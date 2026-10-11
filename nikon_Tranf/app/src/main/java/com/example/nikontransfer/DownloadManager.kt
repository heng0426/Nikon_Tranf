package com.example.nikontransfer

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/** 下载队列条目状态 */
enum class QStatus { QUEUED, RUNNING, DONE, FAILED, CANCELED }

/** 下载队列条目：一个文件一条；状态被下载管理页与全屏预览页共同读取（单一真相源） */
class QueueItem(val handle: Int, val name: String, val type: String, val stamp: String) {
    val status = mutableStateOf(QStatus.QUEUED)
    val total = mutableStateOf(0L)
    val got = mutableStateOf(0L)
    /** 入队后后台预取的文件大小（0x9421）；0 = 未知，总进度分母按已知均值估计 */
    val expectedBytes = mutableStateOf(0L)
    val speed = mutableStateOf("")
    @Volatile var cancelRequested = false
    /** 实际下载耗时（ms）：DONE 时有效，0 = 未完成/被取消 */
    var elapsedMs: Long = 0

    /* ---------- 进度写入节流 ----------
     * USB 整包模式是"每 64KB bulk 回调一次"（50MB NEF ≈ 800 次），分段/Wi-Fi 是
     * 每 1MB 一次。got 是可观察状态，每次写入都会推进快照并唤醒观察者 —— 下载页顶层
     * 的多个 count/any/firstOrNull 会被全部重算，整页重组 = 下载页卡顿的主因。
     * 节流放在写入侧（而不是 UI 读取侧）：读取侧节流省不掉快照写入本身的开销。
     * 完成时强制 flush，否则进度条会停在 97% 然后直接跳"完成"。 */
    @Volatile private var lastProgressMs = 0L

    fun publishProgress(nowGot: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (nowGot >= total || now - lastProgressMs >= UiSpec.PROGRESS_THROTTLE_MS) {
            got.value = nowGot
            lastProgressMs = now
        }
    }

    /** 终态前强制写入一次真实进度（完成/失败/取消） */
    fun flushProgress(nowGot: Long) {
        got.value = nowGot
        lastProgressMs = System.currentTimeMillis()
    }

    fun resetProgress() {
        got.value = 0
        lastProgressMs = 0L
    }
}

/** 下载队列：状态与串行调度的单一真相源（单张/批量全量入队，串行 worker 消费）。
 *  传输 IO 不在这里 —— runItem 由 MainViewModel 注入（持 camMutex 走 PTP 分块读取+保存），
 *  worker 收尾回调 onWorkerExit(ok, fail) 供 VM 做批量完成提醒与通知栏清理。 */
class DownloadManager(
    private val runItem: (QueueItem) -> Unit,
    private val onWorkerExit: (ok: Int, fail: Int) -> Unit
) {
    val queue = mutableStateListOf<QueueItem>()
    private val lock = Object()
    @Volatile private var workerRunning = false

    /** 活跃任务数（顶栏队列按钮角标） */
    val activeCount: Int
        get() = queue.count { it.status.value in setOf(QStatus.QUEUED, QStatus.RUNNING) }

    /** 入队（去重：同一文件已在排队/下载中则忽略；重新下载清掉同句柄旧完成/取消条目） */
    fun enqueue(row: PhotoRow) {
        synchronized(lock) {
            if (queue.any {
                    it.handle == row.handle &&
                        it.status.value in setOf(QStatus.QUEUED, QStatus.RUNNING)
                }) return
            queue.removeAll {
                it.handle == row.handle && it.status.value in setOf(QStatus.DONE, QStatus.CANCELED)
            }
            queue.add(QueueItem(row.handle, row.name, row.type, row.stamp))
        }
        ensureWorker()
    }

    /** 批量入队（调用方已按句柄去重；锁内追加与单条入队互斥） */
    fun enqueueAll(items: List<QueueItem>) {
        synchronized(lock) { items.forEach { queue.add(it) } }
        ensureWorker()
    }

    /** 演示数据入队：只入队、不起 worker（假下载由 PerfTrace 诊断线程自己驱动进度） */
    fun enqueueDemo(items: List<QueueItem>) {
        synchronized(lock) { queue.addAll(items) }
    }

    /** 清空调度队列（演示模式退出 / 重置用；不触碰 cancelRequested） */
    fun clearAll() {
        synchronized(lock) { queue.clear() }
    }

    /** 失败/取消后的重试：复用条目，状态复位重新排队 */
    fun retry(item: QueueItem) {
        item.cancelRequested = false
        item.got.value = 0
        synchronized(lock) {
            if (item.status.value !in setOf(QStatus.QUEUED, QStatus.RUNNING)) {
                item.status.value = QStatus.QUEUED
                if (queue.none { it === item }) queue.add(item)
            }
        }
        ensureWorker()
    }

    /** 取消：排队中 = 直接移除；下载中 = 置取消标志（当前分块写完后停止） */
    fun cancel(item: QueueItem) {
        when (item.status.value) {
            QStatus.QUEUED -> synchronized(lock) { queue.remove(item) }
            QStatus.RUNNING -> item.cancelRequested = true
            else -> {}
        }
    }

    /** 划走条目（滑动删除）：未下载 → 移出队列；下载中 → 置取消标志（当前分块写完即停）
     *  并立刻移出列表 —— worker 收尾对已移除条目写终态无副作用（对象不再被任何 UI 引用，
     *  queueStats/总览卡都只遍历列表）；已完成/已取消/失败 → 直接移除。 */
    fun dismiss(item: QueueItem) {
        if (item.status.value == QStatus.RUNNING) item.cancelRequested = true
        synchronized(lock) { queue.remove(item) }
    }

    /** 清空已完成/已取消条目（失败项保留以便重试） */
    fun clearFinished() {
        synchronized(lock) {
            queue.removeAll { it.status.value in setOf(QStatus.DONE, QStatus.CANCELED) }
        }
    }

    /** 一键取消所有未完成任务：排队中直接移除，下载中置取消标志 */
    fun cancelAll() {
        synchronized(lock) { queue.removeAll { it.status.value == QStatus.QUEUED } }
        queue.forEach { if (it.status.value == QStatus.RUNNING) it.cancelRequested = true }
    }

    /** 串行 worker：逐条取 QUEUED 交给 runItem；队列清空后回调收尾并复位 */
    fun ensureWorker() {
        synchronized(lock) {
            if (workerRunning) return
            workerRunning = true
        }
        var ok = 0
        var fail = 0
        Thread {
            while (true) {
                val item = synchronized(lock) {
                    queue.firstOrNull { it.status.value == QStatus.QUEUED }
                } ?: break
                runItem(item)
                when (item.status.value) {
                    QStatus.DONE -> ok++
                    QStatus.FAILED -> fail++
                    else -> {}
                }
            }
            onWorkerExit(ok, fail)
            synchronized(lock) { workerRunning = false }
        }.apply {
            isDaemon = true
            name = "download-worker"
            start()
        }
    }
}
