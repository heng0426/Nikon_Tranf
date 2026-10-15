package com.shinemeow.app

import android.os.Looper
import android.util.Log
import android.view.Choreographer

/* =====================================================================
 *  PerfTrace — 帧率/卡顿采样 + 关键路径耗时埋点
 *  ---------------------------------------------------------------------
 *  存在的理由：相机连着的时候没有任何 adb 通道 ——
 *    · USB 通道：Type-C 口被相机占着，插不了电脑；
 *    · Wi-Fi 通道：手机连了相机热点，就连不了家里 Wi-Fi，adb tcpip 走不通。
 *  所以只能把尺子做进 App 里：常驻采样，读数在设置页「性能/诊断」分区看。
 *
 *  ▍采样开销
 *    每帧一个 Choreographer.FrameCallback（几十微秒），只保留最近 CAP 帧的间隔。
 *    不持有任何 Compose 状态 —— UI 侧 2Hz 主动轮询，避免"每帧写状态 → 每帧重组"
 *    这种自己把自己测卡的反效果。
 * ===================================================================== */

object PerfTrace {

    /** 用 PerfTrace 而不是 Perf：系统会用 "Perf" 这个 tag 刷 "Connecting to perf service"，
     *  logcat -s Perf 会被淹没。 */
    const val TAG = "PerfTrace"
    private const val CAP = 1000            // 环形窗口：最近 1000 帧

    /** 单帧预算（ns），由 attach(hz) 按实际刷新率设定。
     *  不能写死 16.67ms：这台机（1200x2670）实测跑在 120Hz，8.33ms 一帧，
     *  按 60Hz 预算算会把"丢一帧"判成正常，掉帧率永远 ≈0。
     *  没拿到刷新率时回退 60Hz。 */
    @Volatile private var periodNs = 16_666_667L
    /** 掉帧判定线：超过 1.5 个帧周期 = 至少错过一次 vsync */
    private val jankNs: Long get() = periodNs * 3L / 2L
    val hz: Float get() = 1_000_000_000f / periodNs

    /** 逐次明细日志开关（默认关）：886 张缩略图逐张打日志本身就会拖慢主线程，
     *  而且会把真正想看的信号淹掉。需要单步定位时在诊断页打开。 */
    @Volatile var verbose = false

    private val durations = LongArray(CAP)
    @Volatile private var head = 0
    @Volatile private var filled = 0
    @Volatile private var totalFrames = 0L
    @Volatile private var maxNs = 0L
    @Volatile private var started = false
    private var lastNs = 0L

    /* 命名计数器：count(次数) / totalNs(累计耗时) —— 缩略图、枚举、下载等热路径用 */
    private val counters = java.util.concurrent.ConcurrentHashMap<String, LongArray>()

    private val callback = Choreographer.FrameCallback { onFrame(it) }

    /** 主线程调用一次即开始常驻采样（幂等）。放在 Activity.onCreate。
     *  hz = 当前显示刷新率，决定单帧预算与掉帧判定线。 */
    fun attach(hz: Float = 0f) {
        if (hz > 0f) periodNs = (1_000_000_000f / hz).toLong().coerceIn(4_000_000L, 33_000_000L)
        if (started || Looper.myLooper() != Looper.getMainLooper()) return
        started = true
        Choreographer.getInstance().postFrameCallback(callback)
    }

    private fun onFrame(ns: Long) {
        if (lastNs != 0L) {
            val d = ns - lastNs
            durations[head] = d
            head = (head + 1) % CAP
            if (filled < CAP) filled++
            totalFrames++
            if (d > maxNs) maxNs = d
        }
        lastNs = ns
        Choreographer.getInstance().postFrameCallback(callback)
    }

    /** 清空窗口与计数器（诊断页"重置"用）：采样线程不中断。 */
    fun reset() {
        synchronized(this) {
            head = 0
            filled = 0
            totalFrames = 0L
            maxNs = 0L
            lastNs = 0L
            counters.clear()
        }
    }

    data class Stats(
        val windowFrames: Int,   // 窗口内样本数
        val jankPct: Float,      // 窗口内掉帧比例（%）：超过 1.5 个帧周期
        val p50Ms: Float,
        val p95Ms: Float,
        val maxMs: Float,
        val fps: Float,          // 窗口平均帧率
        val totalFrames: Long,   // 累计总帧数
        val hz: Float,           // 单帧预算对应的刷新率
        val budgetMs: Float      // 单帧预算（ms）
    )

    /** 取当前窗口统计（窗口不足 2 帧时返回全零，避免首帧误导）。 */
    fun snapshot(): Stats {
        val n = filled
        if (n < 2) return Stats(n, 0f, 0f, 0f, 0f, 0f, totalFrames, hz, periodNs / 1_000_000f)
        val copy = LongArray(n)
        synchronized(this) {
            // 环形缓冲按写入顺序摊平：head 指向最旧的一帧
            for (i in 0 until n) copy[i] = durations[(head - n + i + CAP) % CAP]
        }
        copy.sort()
        var jank = 0
        var sum = 0L
        for (d in copy) {
            if (d > jankNs) jank++
            sum += d
        }
        val avgMs = sum / n / 1_000_000.0
        val p50 = copy[(n * 0.50).toInt().coerceAtMost(n - 1)] / 1_000_000f
        val p95 = copy[(n * 0.95).toInt().coerceAtMost(n - 1)] / 1_000_000f
        return Stats(
            windowFrames = n,
            jankPct = jank * 100f / n,
            p50Ms = p50,
            p95Ms = p95,
            maxMs = maxNs / 1_000_000f,
            fps = if (avgMs > 0) (1000f / avgMs.toFloat()) else 0f,
            totalFrames = totalFrames,
            hz = hz,
            budgetMs = periodNs / 1_000_000f
        )
    }

    /* ---------- 关键路径埋点 ---------- */

    /** 记一次耗时（ns）到命名计数器；verbose 时才打 Log。 */
    fun log(name: String, ns: Long) {
        val arr = counters.getOrPut(name) { LongArray(2) }
        synchronized(arr) {
            arr[0]++
            arr[1] += ns
        }
        if (verbose) Log.d(TAG, "$name ${"%.2f".format(ns / 1_000_000.0)}ms")
    }

    /** 只记次数不计时：统计一条派生路径被重算了多少次（优化前后对比的核心指标） */
    fun tick(name: String) {
        val arr = counters.getOrPut(name) { LongArray(2) }
        synchronized(arr) { arr[0]++ }
    }

    inline fun <T> measure(name: String, block: () -> T): T {
        val t0 = System.nanoTime()
        val r = block()
        log(name, System.nanoTime() - t0)
        return r
    }

    /** 命名计数器的平均值（ms）；无样本返回 null。 */
    fun avgMs(name: String): Float? {
        val arr = counters[name] ?: return null
        synchronized(arr) {
            if (arr[0] == 0L) return null
            return arr[1] / arr[0] / 1_000_000f
        }
    }

    fun countOf(name: String): Long = counters[name]?.get(0) ?: 0L
}
