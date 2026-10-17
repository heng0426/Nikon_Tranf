package com.shinemeow.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.os.SystemClock
import android.net.nsd.NsdServiceInfo
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.roundToInt

/** 照片行：预览位图异步填充（原生会话单通道，必须串行取块）
 *  fileNo：真实文件编号（文件名去扩展名，如 "DSC_1234"；USB 枚举提供，Wi-Fi 枚举为 null），
 *  是 RAW+JPG 合并配对的精确键 —— 尼康同一张的 JPG/NEF 同名不同扩展名。 */
class PhotoRow(val name: String, val handle: Int, val type: String, val stamp: String,
               val fileNo: String? = null) {
    val preview = mutableStateOf<Bitmap?>(null)
    val selected = mutableStateOf(false)
    val downloaded = mutableStateOf(false)
    /** 相机端写保护（GetObjectInfo.ProtectionStatus != 0）：机内删除会被相机拒绝。
     *  三个来源：①枚举后后台渐进扫描（事前显示徽标）②删除预检（选中即查）
     *  ③0x2009 失败学习（相机端确认受保护后回写）。 */
    val isProtected = mutableStateOf(false)
    /** 格子是否在屏幕上：由 GridCell 进入/离开组合维护。滑动中读回的位图若格子
     *  已滚出屏幕则只写 LRU 缓存、不写 preview —— 避免滑动中位图填充触发的重组风暴。 */
    @Volatile var onScreen = false
    /** 格子是否曾进入过组合（进入后永不清 false）。区分"首轮加载"（格子尚未组合，
     *  位图可直接写 preview，无重组成本）与"滑动中滚出"（只写 LRU）。 */
    @Volatile var enteredOnce = false
}

/** 合并视图格子：同一拍摄张的 JPG+NEF 成对（或孤儿单格式）。
 *  实例每次派生时重建，选中态存在 VM 的 pairSelection 里（键 = key，非 stamp ——
 *  连拍同一秒会产生多格，stamp 不唯一）。 */
data class PairRow(
    val key: String,
    val stamp: String,
    val jpg: PhotoRow?,
    val nef: PhotoRow?
) {
    val previewBmp: Bitmap? get() = (jpg ?: nef)?.preview?.value
    val hasJpg get() = jpg != null
    val hasNef get() = nef != null
    val jpgDownloaded get() = jpg?.downloaded?.value == true
    val nefDownloaded get() = nef?.downloaded?.value == true
    /** 下载状态筛选的"补全"语义：两个格式都落地才算已下载 */
    val allDownloaded get() = (!hasJpg || jpgDownloaded) && (!hasNef || nefDownloaded)
}

fun badgeColor(type: String) =
    if (type.equals("JPG", true)) UiSpec.JPG_TEAL else UiSpec.NEF_PURPLE

/** "20261001-162351" → "2026-10-01 16:23:51"（相机时钟，可能比手机慢） */
fun prettyStamp(s: String): String =
    if (s.length >= 15 && s[8] == '-')
        "${s.slice(0..3)}-${s.slice(4..5)}-${s.slice(6..7)} ${s.slice(9..10)}:${s.slice(11..12)}:${s.slice(13..14)}"
    else s

/** 下载队列条目状态 / QueueItem / 队列调度：见 DownloadManager.kt */

fun humanSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.1fGB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1fMB".format(b / 1048576.0)
    b >= 1L shl 10 -> "%.0fKB".format(b / 1024.0)
    else -> "${b}B"
}

/** 已下载照片统计（设置·存储分区展示）：JPG 与 RAW 分别计数 + 占用空间 */
data class DlPhotoStats(
    val jpgCount: Int, val jpgBytes: Long,
    val rawCount: Int, val rawBytes: Long
) {
    val totalCount: Int get() = jpgCount + rawCount
    val totalBytes: Long get() = jpgBytes + rawBytes
}

/** 连接与相册的全部状态和逻辑。
 *  放在 ViewModel 里：MIUI 等系统在 App 切后台后可能销毁重建 Activity，
 *  ViewModel 跨重建存活 —— 前台服务维持的相机会话不会被 UI 重置丢掉。 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ACTION_USB_PERMISSION = "com.shinemeow.app.USB_PERMISSION"


    private val ctx: Context get() = getApplication()
    private val prefs get() = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    /* ---------- 连接状态机 ---------- */
    private val camMutex = Object()
    @Volatile var connected = false
    private var ptpPath = ""
    @Volatile var connecting = false
    val connPhase = mutableStateOf("disconnected")   // disconnected / connecting / connected
    val connText = mutableStateOf("未连接 · 点按连接相机")
    val connDetail = mutableStateOf("")              // "型号|序列号"
    val connectedIp = mutableStateOf("")
    val showConnDetail = mutableStateOf(false)
    val scanResults = mutableStateListOf<Pair<String, String>>()  // ip to "型号|序列号"
    val scanning = mutableStateOf(false)
    val scanText = mutableStateOf("")

    /** 当前连接通道（供 UI 决定图标/详情）：usb / wifi / empty（未连接） */
    val connChannel = mutableStateOf("")
    /** 最近一次成功连接的通道（断开后不清除，供 UI 断开时仍显示对应图标）：usb / wifi / empty */
    val lastConnChannel = mutableStateOf("")
    /** 双卡选择的通道：usb / wifi / null（null = 未点击，走自动连接偏好） */
    val pendingChannel = mutableStateOf<String?>(null)
    /** 最近一次连接失败的原因（显示在双卡下方） */
    val connFailMsg = mutableStateOf("")
    /** Wi-Fi 连接取消请求（探测/扫描阶段检查；进入配对后不可打断） */
    @Volatile private var wifiCancelRequested = false

    /* ---------- USB 直连通道（PTP over bulk）---------- */
    @Volatile var usbSession: UsbPtpSession? = null  // 非空 = 当前走 USB
    private val usbPermissionPending = mutableStateOf(false)
    private val usbManager get() = ctx.getSystemService(Context.USB_SERVICE) as UsbManager

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val dev: UsbDevice? = i.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            when (i.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED ->
                    if (dev?.vendorId == UsbPtpSession.VENDOR_NIKON) onUsbAttached(dev)
                UsbManager.ACTION_USB_DEVICE_DETACHED ->
                    if (dev?.vendorId == UsbPtpSession.VENDOR_NIKON) onUsbDetached()
                ACTION_USB_PERMISSION -> {
                    usbPermissionPending.value = false
                    if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && dev != null) {
                        onUsbAttached(dev)
                    } else {
                        connText.value = "USB 权限被拒绝，请重新插拔数据线"
                    }
                }
            }
        }
    }

    private fun registerUsbReceiver() {
        val f = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        // ATTACHED/DETACHED 为系统广播、权限回执发自本 app —— NOT_EXPORTED 即可（13+ 强制）
        ctx.registerReceiver(usbReceiver, f, Context.RECEIVER_NOT_EXPORTED)
    }

    // receiver 字段声明之后才能注册（init 块按源码顺序执行，提前注册会拿到未初始化的 receiver → NPE）
    init { registerUsbReceiver() }

    // 插线拉起场景（device_filter + USB_DEVICE_ATTACHED intent-filter）：相机插线拉起
    // 本 app 时 ATTACHED 广播已经发过、不会重播——启动后主动扫一次已插相机，走与
    // 广播相同的 onUsbAttached 路径（已授权直接连 / 未授权弹权限，连失败落失败 UI）。
    // 延迟 800ms：等 Activity 前台就绪再弹权限框；用户手动开着 app 时此扫描幂等无害。
    init {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!connected && !connecting) {
                UsbPtpSession.findCamera(usbManager)?.let { onUsbAttached(it) }
            }
        }, 800)
    }

    /** 插线拉起入口（MainActivity onCreate/onNewIntent 调用）：launch intent 携带
     *  USB 设备时走与 ATTACHED 广播相同的 onUsbAttached；无设备（普通启动）无操作。
     *  与 800ms 扫描/广播三路并发安全：onUsbAttached 的 connecting 守卫 +
     *  tryConnectUsbCore 的 synchronized 保证只连一次。 */
    fun handleUsbLaunchIntent(intent: Intent?) {
        val dev: UsbDevice? = intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        if (dev?.vendorId == UsbPtpSession.VENDOR_NIKON) onUsbAttached(dev)
    }

    private fun onUsbAttached(dev: UsbDevice) {
        if (connecting) return
        if (usbManager.hasPermission(dev)) {
            Thread { tryConnectUsbCore(dev, auto = true) }.start()
        } else {
            usbPermissionPending.value = true
            val pi = android.app.PendingIntent.getBroadcast(
                ctx, 0,
                Intent(ACTION_USB_PERMISSION).setPackage(ctx.packageName),
                android.app.PendingIntent.FLAG_MUTABLE
            )
            connText.value = "已检测到 USB 相机，请在弹窗中允许访问"
            usbManager.requestPermission(dev, pi)
        }
    }

    private fun onUsbDetached() {
        if (usbSession == null) return
        synchronized(camMutex) { usbSession?.close() }
        usbSession = null
        connected = false
        connChannel.value = ""
        connPhase.value = "disconnected"
        // 已取消"拔线自动回退 Wi-Fi"：拔出仅清理状态，连接方式由用户手动选择
        connText.value = "USB 已断开，请选择连接方式"
        pendingChannel.value = null
    }

    /** USB 连接核心：成功返回 true（connPhase/connText/listFiles 全部就位）。
     *  ⚠️ synchronized(this)：广播 / onNewIntent / 800ms 扫描三路都可能并发进入——
     *  connecting 检查到赋值非原子，不加锁会双线程同时 openDevice 抢同一台相机
     *  （实测表现为连接转圈直到 bulk 超时）。锁对象 = VM 实例，仅此方法使用。 */
    private fun tryConnectUsbCore(dev: UsbDevice, auto: Boolean): Boolean = synchronized(this) {
        if (connecting) return false
        // Wi-Fi 已连接时先干净断开（USB 优先切换）
        if (connected && usbSession == null) {
            stopEventPolling()
            stopKeepAlive()
            synchronized(camMutex) { GPhoto2Bridge.nativeTransferClose() }
            Thread.sleep(1500)
            connected = false
        }
        connecting = true
        connPhase.value = "connecting"
        try {
            connText.value = "正在通过 USB 连接相机…"
            val session = UsbPtpSession(usbManager, dev)
            synchronized(camMutex) { session.openSession() }
            usbSession = session
            connected = true
            connPhase.value = "connected"
            connChannel.value = "usb"
            lastConnChannel.value = "usb"
            connDetail.value = session.modelInfo
            connectedIp.value = ""
            connText.value = "正在读取照片列表…"
            uiLog = listFiles()
            refreshCamStorage()      // 容量展示（连接详情浮层）
            val model = session.modelInfo.split("|").first()
            connText.value = "USB 已连接 · $model"
            Log.i("UsbPtp", "USB 直连建立: ${session.modelInfo} (auto=$auto)")
            return true
        } catch (t: Throwable) {
            Log.w("UsbPtp", "USB 连接失败: $t")
            usbSession = null
            connected = false
            connPhase.value = "disconnected"
            connText.value = "USB 连接失败：${t.message}"
            connFailMsg.value = "USB 连接失败：${t.message}"
            pendingChannel.value = null
            return false
        } finally {
            connecting = false
        }
    }

    /* ---------- 相册 / 下载 / 预览 ---------- */
    val photoRows = mutableStateListOf<PhotoRow>()

    /** handle → 行的索引。下载队列行/预览页按句柄取缩略图时原来是
     *  `photoRows.firstOrNull { it.handle == handle }` —— 20 个队列行 × 886 行
     *  = 每次重组一万多次比较，而且这个查找发生在每个队列行的组合里。 */
    private val rowByHandle = java.util.concurrent.ConcurrentHashMap<Int, PhotoRow>()

    fun rowForHandle(handle: Int): PhotoRow? = rowByHandle[handle]

    /** photoRows 整体替换后调用（枚举 / 演示数据 / 清空） */
    private fun rebuildRowIndex() {
        rowByHandle.clear()
        for (r in photoRows) rowByHandle[r.handle] = r
    }
    @Volatile private var previewGen = 0
    @Volatile private var nefDumped = false
    @Volatile private var downloadBusy = false
    val downloadProgress = mutableStateOf("")

    /* ---------- 自动化性能测量：请求打开某个页面 ----------
     * 页面显隐是 Activity 内的 remember 状态，VM 够不到。用计数器形式下发意图：
     * 计数值变化即触发一次打开（避免"打开→关闭→再打开"被去重）。 */
    val openDownloadsRequest = mutableStateOf(0)
    val openSettingsRequest = mutableStateOf(0)
    fun requestOpenDownloads() { openDownloadsRequest.value++ }
    fun requestOpenSettings() { openSettingsRequest.value++ }

    /* ---------- 多选模式 ---------- */
    val selectMode = mutableStateOf(false)

    /* 文件模式选中计数：与 pairSelection 对等的独立计数器。
     * 旧实现 UI 侧写 `photoRows.count { it.selected.value }` —— 这个表达式订阅了
     * 每一行的 selected，于是"勾一格"会让整个 MainScreen（含两个网格）重组，
     * 再叠加多选栏展开动画每帧改变高度 → 网格每帧 re-measure → 滑动/勾选卡顿。
     * 改由 VM 维护后读取是 O(1)，且订阅点只有一个。 */
    val selectedCount = mutableStateOf(0)

    /** 唯一的选中态写入口：同步维护计数器。UI 不要再直接写 row.selected。 */
    fun setRowSelected(row: PhotoRow, on: Boolean) {
        if (row.selected.value == on) return
        row.selected.value = on
        selectedCount.value = (selectedCount.value + if (on) 1 else -1).coerceAtLeast(0)
    }

    fun toggleRowSelected(row: PhotoRow) = setRowSelected(row, !row.selected.value)

    /** 批量选中/取消（全选、选当天）：逐行走节流入口，计数器自动对齐 */
    fun setSelectedAll(rows: List<PhotoRow>, on: Boolean) {
        for (r in rows) setRowSelected(r, on)
    }

    /* ---------- 照片筛选（会话内有效，不跨启动记忆）---------- */
    val filterFormat = mutableStateOf("全部")          // 全部 / JPG / NEF（单选）
    val filterDl = mutableStateOf("全部")              // 全部 / 未下载 / 已下载（单选，与文件类型同款交互）
    val filterStart = mutableStateOf<String?>(null)   // "YYYYMMDD"，null = 不限
    val filterEnd = mutableStateOf<String?>(todayKey())  // 默认今天（含）

    private fun todayKey(): String {
        val d = java.time.LocalDate.now()
        return "%04d%02d%02d".format(d.year, d.monthValue, d.dayOfMonth)
    }

    /** 是否有偏离默认的筛选条件（决定筛选按钮高亮） */
    val filterActive: Boolean
        get() = filterFormat.value != "全部" || filterDl.value != "全部" ||
            filterStart.value != null || filterEnd.value != todayKey()

    fun resetFilter() {
        filterFormat.value = "全部"
        filterDl.value = "全部"
        filterStart.value = null
        filterEnd.value = todayKey()
    }

    /* ---------- 短/长按反转（网格点选交互）----------
     *  关（默认）= 短按进预览、长按选中（进入多选）；
     *  开 = 反转：短按选中、长按进预览。合并/文件两模式共用，持久化 set_swap_press。 */
    val swapPressOn = mutableStateOf(prefs.getBoolean("set_swap_press", false))
    fun setSwapPress(on: Boolean) {
        swapPressOn.value = on
        prefs.edit().putBoolean("set_swap_press", on).apply()
    }

    /* ---------- 分块加载：照片很多时先显示一部分，滚近已加载末尾自动追加 ----------
     *  关闭 = 全量显示（现状）。合并模式按合并格计块，文件模式按照片计块。
     *  全选/选当天/下载统计等口径一律基于"已加载可见项"，与网格所见一致。 */
    val chunkedLoad = mutableStateOf(prefs.getBoolean("set_chunked_load", false))
    val chunkSize = mutableStateOf(prefs.getInt("set_chunk_size", 100))
    /** 当前已放行的显示条数；Int.MAX_VALUE = 不限 */
    private val displayLimit = mutableStateOf(Int.MAX_VALUE)

    private fun <T> applyChunk(list: List<T>): List<T> =
        if (!chunkedLoad.value || displayLimit.value >= list.size) list else list.take(displayLimit.value)

    fun setChunkedLoad(on: Boolean) {
        chunkedLoad.value = on
        prefs.edit().putBoolean("set_chunked_load", on).apply()
        displayLimit.value = if (on) chunkSize.value else Int.MAX_VALUE
    }

    fun setChunkSize(n: Int) {
        chunkSize.value = n
        prefs.edit().putInt("set_chunk_size", n).apply()
        if (chunkedLoad.value) displayLimit.value = n
    }

    /** 网格滚近已加载末尾时调用：追加一块（已全部加载则忽略） */
    fun loadMoreChunks() {
        if (!chunkedLoad.value) return
        val full = if (mergePairs.value) filteredPairs.value.size else filteredPhotos.value.size
        if (displayLimit.value >= full) return
        displayLimit.value += chunkSize.value
    }

    /** 筛选后的完整可见照片数（分块前），供置顶胶囊显示"已加载 X/Y" */
    fun fullVisibleCount(): Int =
        if (mergePairs.value) filteredPairs.value.size else filteredPhotos.value.size

    /* ---------- 派生结果缓存（批2 核心）----------
     * 原来是四个"每次读都重算"的 getter，且每次返回新的 List 实例 ——
     * 后果不是"多算几次"，而是让下游 MainActivity 里三个 derivedStateOf 完全失效：
     *   derivedStateOf 靠"读到的值是否变化"决定是否输出新值，
     *   源每次都返回新 List → 永远判定变化 → 滑动/勾选时每帧全量 filter + regroup。
     * 改成 derivedStateOf 后：只有真正读到的状态（筛选条件 / 分块上限 / 折叠 /
     * downloaded）变化才重算，且返回同一个 List 实例，下游缓存随即生效。
     * 注意 downloaded 只在"按下载状态筛选"开启时才被读到 —— 默认关闭时，
     * 勾一格多选、或某张下载完成，都不会触发这里重算。 */
    val filteredPhotos: State<List<PhotoRow>> = derivedStateOf { computeFilteredPhotos() }

    /** 筛选后的可见照片（分块前全量）—— 网格 / 全屏预览翻页 / 多选全选的统一数据源 */
    private fun computeFilteredPhotos(): List<PhotoRow> {
        PerfTrace.tick("filterPhotos#重算")
        val fmt = filterFormat.value
        val dl = filterDl.value
        val st = filterStart.value
        val en = filterEnd.value
        return photoRows.filter { r ->
            if (fmt != "全部" && !r.type.equals(fmt, true)) return@filter false
            val isDl = r.downloaded.value
            if (dl == "未下载" && isDl) return@filter false
            if (dl == "已下载" && !isDl) return@filter false
            val d = if (r.stamp.length >= 8) r.stamp.substring(0, 8) else ""
            if (st != null && (d.isEmpty() || d < st)) return@filter false
            if (en != null && (d.isEmpty() || d > en)) return@filter false
            true
        }
    }

    /** 分块加载后的可见照片 */
    val visiblePhotos: List<PhotoRow> by derivedStateOf { applyChunk(filteredPhotos.value) }

    /** 按日期分组的可见照片：日期从新到旧，组内保持原排序 */
    data class DateSection(val dateKey: String, val rows: List<PhotoRow>)

    val visibleSections: List<DateSection> by derivedStateOf {
        PerfTrace.tick("sections#重算")
        val order = ArrayList<String>()
        val map = LinkedHashMap<String, MutableList<PhotoRow>>()
        for (r in visiblePhotos) {
            val d = if (r.stamp.length >= 8) r.stamp.substring(0, 8) else "00000000"
            val list = map.getOrPut(d) { mutableListOf() }
            if (list.isEmpty()) order.add(d)
            list.add(r)
        }
        order.map { DateSection(it, map[it]!!) }
    }

    /** 节头文案："10月1日 周四 · 6 张" */
    fun dateLabel(dateKey: String, count: Int): String {
        if (dateKey.length != 8) return "$dateKey · $count 张"
        return try {
            val ld = java.time.LocalDate.of(
                dateKey.substring(0, 4).toInt(),
                dateKey.substring(4, 6).toInt(),
                dateKey.substring(6, 8).toInt()
            )
            val wd = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[ld.dayOfWeek.value - 1]
            "${ld.monthValue}月${ld.dayOfMonth}日 $wd · $count 张"
        } catch (_: Exception) {
            "$dateKey · $count 张"
        }
    }

    /* ---------- 合并展示（RAW+JPG 同时间戳合成一格，设置开关即时生效）---------- */
    val mergePairs = mutableStateOf(false)

    fun setMergePairs(on: Boolean) {
        mergePairs.value = on
        prefs.edit().putBoolean("set_merge_pairs", on).apply()
        clearSelection()   // 切换视图形态时清空多选，避免跨模式选中态混乱
    }

    fun clearSelection() {
        photoRows.forEach { it.selected.value = false }
        selectedCount.value = 0
        pairSelection.value = emptySet()
    }

    /** 合并模式的多选（键 = 时间戳），与文件模式的多选相互独立 */
    val pairSelection = mutableStateOf<Set<String>>(emptySet())

    /** 折叠的日期（dateKey 集合，会话内有效：切视图/重连保留，退出 App 重置）。
     *  折叠语义 = 只藏不见：网格不渲染该天照片，但全选/统计口径不变（Q4=B）。 */
    val collapsedDates = mutableStateOf<Set<String>>(emptySet())

    fun toggleDateCollapsed(dateKey: String) {
        collapsedDates.value =
            if (dateKey in collapsedDates.value) collapsedDates.value - dateKey
            else collapsedDates.value + dateKey
    }

    fun togglePair(key: String) {
        pairSelection.value =
            if (key in pairSelection.value) pairSelection.value - key
            else pairSelection.value + key
    }

    /** 多选批量下载的格式勾选框（合并模式；默认只勾 JPG） */
    val batchFmtJpg = mutableStateOf(true)
    val batchFmtNef = mutableStateOf(false)

    /** 全部照片按拍摄张配对（孤儿单格式也成一项），从新到旧。
     *  配对键优先用真实文件编号（尼康同一张的 JPG/NEF 同名不同扩展名，USB 枚举提供）；
     *  无文件名（Wi-Fi 枚举）退回时间戳。同键同格式多张（连拍同一秒）按句柄升序
     *  zip 一一配对 —— 旧实现 firstOrNull 会把同秒其余照片整格吞掉（丢图根因）。
     *  结果缓存：配对只依赖 photoRows 内容（仅在 listFiles 重建后失效）。此前每次
     *  读取都重新分组+排序（O(n log n) 大量分配），滑动中任何读到它的重组都会踩到。 */
    @Volatile private var pairRowsCache: List<PairRow>? = null
    /** 配对缓存版本号：仅在重建时自增。
     *  旧实现只写 @Volatile 字段 —— derivedStateOf 读不到它，所以重连/重新枚举后
     *  配对结果变了，下游 derivedStateOf 也不会失效（网格继续显示旧配对）。
     *  下游只要读一次 pairRowsAll（内部读 version）就建立了正确依赖。 */
    private val pairRowsVersion = mutableStateOf(0)

    val pairRowsAll: List<PairRow>
        get() {
            pairRowsVersion.value          // 建立依赖
            return pairRowsCache ?: buildPairRows().also { pairRowsCache = it }
        }

    /** 重建配对缓存：枚举线程上调用（首次读取不落在主线程） */
    fun refreshPairRows() {
        pairRowsCache = buildPairRows()
        pairRowsVersion.value++
    }

    /* ---------- 同秒排序 ----------
     * stamp 只有秒级精度，sortedByDescending 是稳定排序：同一秒并列时完全保留
     * 枚举输出顺序 —— USB 快路径 = BFS 文件夹序（JPG 目录整段在前、NEF 在后，
     * 各自旧→新），Wi-Fi = C 侧 qsort 句柄升序（旧→新）。表现为同秒连拍内部
     * 顺序与整体"从新到旧"相反、同张 JPG/NEF 被文件夹分组拆开。 */
    /** 同秒并列时的拍摄序键：fileNo 数字部分（"DSC_1234"→1234）优先；
     *  USB 快路径（0x9434 表只回 3 段）与 Wi-Fi 枚举都拿不到文件名，
     *  兜底句柄低 16 位槽位号（尼康句柄 = 目录<<16 | 槽位，同目录内随拍摄递增，
     *  JPG/NEF 同一张的槽位号一致 → 跨类型可比）。值越大 = 拍摄越晚。 */
    private fun PhotoRow.captureSeq(): Int =
        fileNo?.takeLastWhile { c -> c.isDigit() }?.toIntOrNull() ?: (handle and 0xffff)

    /** 配对行的同秒拍摄序：取代表行（JPG 优先，缺则为 NEF）按同规则计算 */
    private fun PairRow.captureSeq(): Int =
        (jpg ?: nef)?.captureSeq() ?: -1

    private fun buildPairRows(): List<PairRow> {
        val byKey = LinkedHashMap<String, MutableList<PhotoRow>>()
        for (r in photoRows) {
            val key = r.fileNo ?: "T${r.stamp}"
            byKey.getOrPut(key) { mutableListOf() }.add(r)
        }
        val rows = ArrayList<PairRow>()
        for ((key, list) in byKey) {
            val jpgs = list.filter { it.type.equals("JPG", true) }.sortedBy { it.handle }
            val nefs = list.filter { it.type.equals("NEF", true) }.sortedBy { it.handle }
            for (i in 0 until maxOf(jpgs.size, nefs.size)) {
                val jpg = jpgs.getOrNull(i)
                val nef = nefs.getOrNull(i)
                val stamp = (jpg ?: nef)!!.stamp
                rows.add(PairRow("$key#$i", stamp, jpg, nef))
            }
        }
        // 同秒并列按拍摄序打破（从新到旧）：不依赖 photoRows 恰好已排好序，
        // 显式声明，防止上游排序变化悄悄破坏配对组间的先后
        return rows.sortedWith(
            compareByDescending<PairRow> { it.stamp }
                .thenByDescending { it.captureSeq() }
        )
    }

    /** 合并模式的可见对（分块前全量）：格式筛选隐藏（①b）；下载状态=补全语义（②a）；日期照旧
     *  同 filteredPhotos：派生缓存，避免每次读都全表 filter + 每次返回新实例。 */
    val filteredPairs: State<List<PairRow>> = derivedStateOf { computeFilteredPairs() }

    private fun computeFilteredPairs(): List<PairRow> {
        PerfTrace.tick("filterPairs#重算")
        val dl = filterDl.value
        val st = filterStart.value
        val en = filterEnd.value
        return pairRowsAll.filter { p ->
            if (dl == "未下载" && p.allDownloaded) return@filter false
            if (dl == "已下载" && !p.allDownloaded) return@filter false
            val d = p.stamp.take(8)
            if (st != null && d < st) return@filter false
            if (en != null && d > en) return@filter false
            true
        }
    }

    /** 分块加载后的可见对 */
    val visiblePairs: List<PairRow> by derivedStateOf { applyChunk(filteredPairs.value) }

    data class PairSection(val dateKey: String, val rows: List<PairRow>)

    val pairSections: List<PairSection> by derivedStateOf {
        PerfTrace.tick("pairSections#重算")
        val order = ArrayList<String>()
        val map = LinkedHashMap<String, MutableList<PairRow>>()
        for (p in visiblePairs) {
            val d = p.stamp.take(8)
            val list = map.getOrPut(d) { mutableListOf() }
            if (list.isEmpty()) order.add(d)
            list.add(p)
        }
        order.map { PairSection(it, map[it]!!) }
    }

    /* ---------- 已下载标记（跨会话持久化，键 = 合成文件名）---------- */
    private val downloadedNames = mutableStateOf<Set<String>>(emptySet())

    /* ---------- 存储位置（Q1=A 默认 Pictures/ShineMeow/<拍摄日期>/，可 SAF 自定义）---------- */
    val dateFolderOn = mutableStateOf(true)          // 按拍摄日期文件夹保存，默认开（Q3=A）
    val customDirUri = mutableStateOf<String?>(null) // SAF tree URI，null = 默认位置
    val dirDisplay = mutableStateOf("Pictures/ShineMeow")

    fun setDateFolder(on: Boolean) {
        dateFolderOn.value = on
        prefs.edit().putBoolean("set_date_folder", on).apply()
    }

    fun setCustomDir(uri: String?) {
        customDirUri.value = uri
        if (uri == null) prefs.edit().remove("save_dir_uri").apply()
        else prefs.edit().putString("save_dir_uri", uri).apply()
        refreshDirDisplay()
    }

    /** 目录行的友好显示：默认 = 相对路径；自定义 = 目录名（SAF 查询 DISPLAY_NAME） */
    fun refreshDirDisplay() {
        val u = customDirUri.value
        if (u == null) {
            dirDisplay.value = "Pictures/ShineMeow（默认）"
            return
        }
        try {
            val treeUri = android.net.Uri.parse(u)
            val doc = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            )
            ctx.contentResolver.query(
                doc, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    dirDisplay.value = c.getString(0) ?: u
                    return
                }
            }
            dirDisplay.value = u
        } catch (t: Throwable) {
            dirDisplay.value = u
        }
    }

    private fun loadDownloaded() {
        downloadedNames.value =
            prefs.getStringSet("downloaded_names", emptySet()) ?: emptySet()
        syncDownloadedNames()   // 本地文件可能已被用户删除，先与磁盘对齐
    }

    private fun markDownloaded(name: String) {
        val s = downloadedNames.value + name
        downloadedNames.value = s
        prefs.edit().putStringSet("downloaded_names", s).apply()
    }

    /** 扫描本地实际存在的文件名：MediaStore 默认位置 + SAF 自定义目录（含一级日期子夹） */
    private fun queryLocalFileNames(): Set<String> {
        val out = HashSet<String>()
        try {
            val col = MediaStore.Files.getContentUri("external_primary")
            ctx.contentResolver.query(
                col,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("Pictures/ShineMeow%", "Download/ShineMeow%"),
                null
            )?.use { c ->
                while (c.moveToNext()) c.getString(0)?.let { out.add(it) }
            }
        } catch (t: Throwable) {
            Log.w("GPhoto2", "MediaStore 已下载扫描失败", t)
        }
        customDirUri.value?.let { uriStr ->
            try {
                val treeUri = android.net.Uri.parse(uriStr)
                val rootId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val subDirs = mutableListOf<String>()
                ctx.contentResolver.query(
                    android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId),
                    arrayOf(
                        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null, null, null
                )?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getString(2) == android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                            subDirs.add(c.getString(0))
                        else c.getString(1)?.let { out.add(it) }
                    }
                }
                for (docId in subDirs) {
                    ctx.contentResolver.query(
                        android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId),
                        arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        null, null, null
                    )?.use { c ->
                        while (c.moveToNext()) c.getString(0)?.let { out.add(it) }
                    }
                }
            } catch (t: Throwable) {
                Log.w("GPhoto2", "SAF 已下载扫描失败", t)
            }
        }
        return out
    }

    /** 已下载状态与本地文件对齐：磁盘上已不存在的条目从 downloaded_names 移除 */
    private fun syncDownloadedNames() {
        val before = downloadedNames.value
        if (before.isEmpty()) return
        val disk = queryLocalFileNames()
        val valid = before.filterTo(HashSet()) { it in disk }
        if (valid.size != before.size) {
            downloadedNames.value = valid
            prefs.edit().putStringSet("downloaded_names", valid).apply()
            Log.i("GPhoto2", "已下载同步：${before.size} → ${valid.size}（本地已删除的条目已清除）")
        }
    }

    /* ---------- 存储统计：已下载照片（设置·存储分区展示）---------- */

    val dlPhotoStats = mutableStateOf<DlPhotoStats?>(null)

    /** 后台重算已下载照片统计（进入设置页时与手动「刷新」触发） */
    fun refreshDownloadStats() {
        Thread {
            try {
                dlPhotoStats.value = computeDownloadStats()
            } catch (t: Throwable) {
                Log.w("GPhoto2", "存储统计失败", t)
            }
        }.start()
    }

    /** 统计数据源与保存逻辑对齐（两处同时统计——换过目录的旧文件也真实占用空间）：
     *  ① 默认位置（MediaStore）：Pictures/ShineMeow% + NEF 回退 Download/ShineMeow%；
     *  ② 自定义目录（SAF tree）：根 + 日期子目录（写入最多两级，遍历同深防失控）。
     *  按扩展名分类：jpg/jpeg → JPG，nef/nrw → RAW（尼康 RAW 家族），其余忽略。 */
    private fun computeDownloadStats(): DlPhotoStats {
        var jpgN = 0
        var jpgB = 0L
        var rawN = 0
        var rawB = 0L
        fun add(name: String?, size: Long) {
            val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return
            when (ext) {
                "jpg", "jpeg" -> { jpgN++; jpgB += size }
                "nef", "nrw" -> { rawN++; rawB += size }
            }
        }
        try {
            val col = MediaStore.Files.getContentUri("external_primary")
            ctx.contentResolver.query(
                col,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("Pictures/ShineMeow%", "Download/ShineMeow%"),
                null
            )?.use { c ->
                while (c.moveToNext())
                    add(c.getString(0), if (c.isNull(1)) 0L else c.getLong(1))
            }
        } catch (t: Throwable) {
            Log.w("GPhoto2", "存储统计 MediaStore 查询失败", t)
        }
        customDirUri.value?.let { uriStr ->
            try {
                val treeUri = android.net.Uri.parse(uriStr)
                fun walk(docId: String, depth: Int) {
                    val dirs = mutableListOf<String>()
                    ctx.contentResolver.query(
                        android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId),
                        arrayOf(
                            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                            android.provider.DocumentsContract.Document.COLUMN_SIZE
                        ),
                        null, null, null
                    )?.use { c ->
                        while (c.moveToNext()) {
                            if (c.getString(2) == android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
                                c.getString(0)?.let { dirs.add(it) }
                            else
                                add(c.getString(1), if (c.isNull(3)) 0L else c.getLong(3))
                        }
                    }
                    if (depth < 2) for (d in dirs) walk(d, depth + 1)
                }
                walk(android.provider.DocumentsContract.getTreeDocumentId(treeUri), 0)
            } catch (t: Throwable) {
                Log.w("GPhoto2", "存储统计 SAF 遍历失败", t)
            }
        }
        return DlPhotoStats(jpgN, jpgB, rawN, rawB)
    }

    /* ---------- 高清预览开关（默认关，持久化；Q3 定案）---------- */
    var hiresOn: Boolean by mutableStateOf(prefs.getBoolean("set_hires_preview", false))

    fun setHiresPreview(v: Boolean) {
        hiresOn = v
        prefs.edit().putBoolean("set_hires_preview", v).apply()
    }

    /* ---------- 下载过滤（跳过已下载；仅批量下载所选，预览页重下不受影响）---------- */
    var skipDownloadedOn: Boolean by mutableStateOf(prefs.getBoolean("set_skip_downloaded", true))

    fun setSkipDownloaded(v: Boolean) {
        skipDownloadedOn = v
        prefs.edit().putBoolean("set_skip_downloaded", v).apply()
    }

    /* ---------- 连接状态机：本会话是否连接成功过（决定断开时用红图标还是状态条）---------- */
    var everConnected: Boolean by mutableStateOf(false)

    /* ---------- 外观：深色模式（默认关 = 浅色）---------- */
    var darkModeOn: Boolean by mutableStateOf(prefs.getBoolean("set_dark_mode", false))

    fun setDarkMode(v: Boolean) {
        darkModeOn = v
        prefs.edit().putBoolean("set_dark_mode", v).apply()
    }

    /* ---------- 外观：顶栏到状态栏余量（dp）：主页/下载队列/设置三页共用，
       0 = 紧贴状态栏，按机型显示状态自由调整（默认取 UiSpec.TOPBAR_GAP）---------- */
    val topBarGapDp = mutableStateOf(prefs.getInt("set_topbar_gap", UiSpec.TOPBAR_GAP.value.toInt()))

    fun setTopBarGap(v: Int) {
        topBarGapDp.value = v.coerceIn(0, 32)
        prefs.edit().putInt("set_topbar_gap", topBarGapDp.value).apply()
    }

    /* ---------- 外观：配色主题（设置·外观切换 → 写 UiSpec.currentTheme，
       取色处自动重组，实时生效；持久化到 set_ui_theme）---------- */
    val themeKey = mutableStateOf(
        runCatching {
            ThemeKey.valueOf(prefs.getString("set_ui_theme", ThemeKey.DEFAULT.name) ?: ThemeKey.DEFAULT.name)
        }.getOrDefault(ThemeKey.DEFAULT)
    )

    fun setTheme(k: ThemeKey) {
        themeKey.value = k
        UiSpec.currentTheme = k
        prefs.edit().putString("set_ui_theme", k.name).apply()
        Log.i("GPhoto2", "外观: 配色主题切换为 ${k.label}")
    }

    // 放在 themeKey 声明之后：init 块按源码顺序执行，启动时恢复上次选择的配色主题
    init { UiSpec.currentTheme = themeKey.value }

    init {
        loadDownloaded()
        mergePairs.value = prefs.getBoolean("set_merge_pairs", false)
        dateFolderOn.value = prefs.getBoolean("set_date_folder", true)   // 默认开（Q3=A）
        customDirUri.value = prefs.getString("save_dir_uri", null)
        refreshDirDisplay()
    }

    /* ---------- 日志 ---------- */
    var uiLog by mutableStateOf("")

    /* ---------- 事件轮询 ---------- */
    @Volatile private var pollerRunning = false
    private var eventThread: Thread? = null

    /* ---------- 后台保活 ---------- */
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    fun splitInfo(info: String): Pair<String, String> {
        val p = info.split('|')
        return (p.getOrNull(0)?.ifEmpty { "Nikon" } ?: "Nikon") to (p.getOrNull(1)?.ifEmpty { "?" } ?: "?")
    }

    /** 手机热点是否开启（本 App 的主拓扑 = 相机连手机热点，热点是前提）。
     *  getWifiApState 是隐藏 API，走反射取；WIFI_AP_STATE_ENABLED = 13。
     *  检测失败（机型差异/权限）一律视为开启，绝不误拦连接。 */
    fun isHotspotOn(): Boolean {
        return try {
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val state = wm.javaClass.getMethod("getWifiApState").invoke(wm) as Int
            state == 13
        } catch (t: Throwable) {
            Log.w("GPhoto2", "热点状态检测失败（按开启处理）: ${t.message}")
            true
        }
    }

    /** 热点状态的可观察缓存：初始化时求值一次（isHotspotOn 是 fun 无顺序问题），onResume 时刷新（从热点设置页返回即生效） */
    var hotspotOnState by mutableStateOf(isHotspotOn())
        private set

    fun refreshHotspot() { hotspotOnState = isHotspotOn() }

    /** 热点未开提示开关（设置项，默认开）：未连接且热点未开时，连接页 Wi-Fi 卡下方显示跳转提示 */
    val hotspotHintOn = mutableStateOf(prefs.getBoolean("set_hotspot_hint", true))
    fun setHotspotHint(v: Boolean) {
        hotspotHintOn.value = v
        prefs.edit().putBoolean("set_hotspot_hint", v).apply()
    }

    /** USB 整文件传输开关（默认开=单事务 0x1009 整包读取更快，进度按 bulk 轮回调）：
     *  关闭后下载走 1MB 分段 0x101B（兼容优先）。两种模式失败均自动回退另一路径。 */
    val usbWholeTransfer = mutableStateOf(prefs.getBoolean("set_usb_whole", true))
    fun setUsbWholeTransfer(v: Boolean) {
        usbWholeTransfer.value = v
        prefs.edit().putBoolean("set_usb_whole", v).apply()
    }

    /* ---------- 扫描 ---------- */

    /** Wi-Fi 侧 /24 网段及其「出接口方式」：
     *  ① 受管 Wi-Fi 网络（手机连 Wi-Fi/相机热点）：带 Network 对象，扫描 socket
     *     由 network.socketFactory 创建（逐 socket 精确绑定，不污染进程默认网络）；
     *  ② 本机热点接口（MIUI 开热点不建 NetworkAgent，热点不是受管网络）：带本机
     *     热点地址，socket 绑定该源地址即可从热点接口出包。
     *  小米 11 实测：热点网段的目标命中规则链 fwmark 0x0/0x10000 → 热点直连
     *  路由表（表 97 = 192.168.18.0/24 dev wlan1），无需任何进程级绑定。 */
    private data class WifiRoute(val prefix: String, val network: Network?, val localAddr: java.net.InetAddress?)

    private fun wifiRoutes(): List<WifiRoute> {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val out = LinkedHashMap<String, WifiRoute>()
        for (net in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val lp = cm.getLinkProperties(net) ?: continue
            for (la in lp.linkAddresses) {
                val a = la.address.address
                if (a is Array<*> || a.size != 4) continue
                if (la.prefixLength == 24) {
                    val p = "${a[0].toInt() and 0xFF}.${a[1].toInt() and 0xFF}.${a[2].toInt() and 0xFF}"
                    if (!out.containsKey(p)) out[p] = WifiRoute(p, net, null)
                }
            }
        }
        // 接口枚举兜底（热点非受管网络时的唯一来源）；受管已覆盖的网段跳过
        for ((p, addr) in interfaceWifiAddrs())
            if (!out.containsKey(p)) out[p] = WifiRoute(p, null, addr)
        return out.values.toList()
    }

    /** 网卡侧 Wi-Fi 接口的 IPv4（/24 前缀 → 本机地址）。
     *  接口典型名：wlan0（Wi-Fi 客户端）/ swlan0、ap0、softap0（热点）；
     *  蜂窝（rmnet/ccmni）与回环排除。枚举失败返回空表（不阻断扫描）。 */
    private fun interfaceWifiAddrs(): List<Pair<String, java.net.InetAddress>> {
        val out = LinkedHashMap<String, java.net.InetAddress>()
        try {
            for (nif in java.net.NetworkInterface.getNetworkInterfaces()) {
                if (nif == null || !nif.isUp || nif.isLoopback) continue
                val name = nif.name.lowercase()
                val wifiSide = name.startsWith("wlan") || name.startsWith("swlan") ||
                    name.startsWith("softap") || name.startsWith("ap")
                if (!wifiSide) continue
                for (ia in nif.inetAddresses) {
                    val a = (ia as? java.net.Inet4Address)?.address ?: continue
                    if (a.size != 4) continue
                    val prefix = "${a[0].toInt() and 0xFF}.${a[1].toInt() and 0xFF}.${a[2].toInt() and 0xFF}"
                    if (!out.containsKey(prefix)) out[prefix] = ia
                }
            }
        } catch (t: Throwable) {
            Log.w("GPhoto2", "网卡网段枚举失败: ${t.message}")
        }
        return out.map { it.key to it.value }
    }

    /** 热点网段 = 本机 Wi-Fi 侧接口网段中「不在受管网络列表」的部分。
     *  MIUI 等机型开热点时只挂接口（wlan1/swlan0）不建 NetworkAgent，热点不是
     *  受管网络——cm.allNetworks 拿不到 Network 对象。发往热点网段的连接
     *  【无需绑定】：未标记 socket 命中规则链 fwmark 0x0/0x10000 → 热点直连
     *  路由表（小米 11 实测表 97）。
     *  返回 (网段前缀, 本机热点地址)，null = 无热点或热点即受管网络（手机连相机热点场景）。 */
    private fun hotspotSubnet(): Pair<String, java.net.InetAddress>? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val managed = HashSet<String>()
        for (net in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val lp = cm.getLinkProperties(net) ?: continue
            for (la in lp.linkAddresses) {
                val a = la.address.address
                if (a is Array<*> || a.size != 4) continue
                managed.add("${a[0].toInt() and 0xFF}.${a[1].toInt() and 0xFF}.${a[2].toInt() and 0xFF}")
            }
        }
        return interfaceWifiAddrs().firstOrNull { it.first !in managed }
    }

    /** mDNS 服务发现找相机（主路径）：相机在 Wi-Fi 上广播
     *  _ptp._tcp / _nikon._tcp 服务，系统 NsdManager（netd mdnsd）在【所有】
     *  接口上做多播收听，不受 App 进程默认网络/路由绑定影响——一次发现直接
     *  拿到相机 IP，免去 /24 全网段扫描（16~20s）。返回 PTP/IP 端口(15740)
     *  在监听的相机 IP 列表。 */
    private fun mdnsDiscoverCameras(timeoutMs: Long = 8000L): List<String> {
        val nsd = try {
            ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager
        } catch (_: Throwable) { null }
        if (nsd == null) {
            Log.i("GPhoto2", "发现: NsdManager 不可用，走网段扫描")
            return emptyList()
        }
        val found = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
        val listeners = ArrayList<NsdManager.DiscoveryListener>()
        for (svc in arrayOf("_ptp._tcp.", "_nikon._tcp.")) {
            val l = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String?) {
                    Log.i("GPhoto2", "发现: mDNS 开始 $type")
                }
                override fun onDiscoveryStopped(type: String?) {}
                override fun onStartDiscoveryFailed(type: String?, err: Int) {
                    Log.w("GPhoto2", "发现: mDNS 启动失败($err) $type")
                }
                override fun onStopDiscoveryFailed(type: String?, err: Int) {}
                override fun onServiceLost(si: NsdServiceInfo?) {}
                override fun onServiceFound(si: NsdServiceInfo?) {
                    si ?: return
                    Log.i("GPhoto2", "发现: mDNS 找到 ${si.serviceName} @ ${si.serviceType}")
                    try {
                        nsd.resolveService(si, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo?, err: Int) {
                                Log.w("GPhoto2", "发现: resolve 失败($err) ${info?.serviceName}")
                            }
                            override fun onServiceResolved(info: NsdServiceInfo?) {
                                val ip = info?.host?.hostAddress ?: return
                                if (info.port != 15740) {
                                    Log.i("GPhoto2", "发现: ${info.serviceName} → $ip:${info.port} 非 PTP/IP 端口，跳过")
                                    return
                                }
                                if (found.add(ip))
                                    Log.i("GPhoto2", "发现: mDNS 命中相机 $ip（${info.serviceName}）")
                            }
                        })
                    } catch (t: Throwable) {
                        Log.w("GPhoto2", "发现: resolve 异常 ${t.message}")
                    }
                }
            }
            listeners.add(l)
            try {
                nsd.discoverServices(svc, NsdManager.PROTOCOL_DNS_SD, l)
            } catch (t: Throwable) {
                Log.w("GPhoto2", "发现: mDNS 启动异常($svc) ${t.message}")
            }
        }
        // 睡眠窗口改为分段轮询（原 Thread.sleep(8000) 硬睡满：点取消后要等 8s 才退出，
        // 且命中相机后也傻等满窗）：
        //  · 每 200ms 查一次 wifiCancelRequested → 点取消亚秒级退出
        //  · 提前命中相机（found 非空）→ 多留 600ms 收尾 resolve 回调即返回，
        //    正常路径也不再等满 8s
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        var earlyExitAt = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (wifiCancelRequested) break
            if (found.isNotEmpty()) {
                if (earlyExitAt == 0L) earlyExitAt = SystemClock.elapsedRealtime() + 600
                if (SystemClock.elapsedRealtime() >= earlyExitAt) break
            }
            val remain = deadline - SystemClock.elapsedRealtime()
            try { Thread.sleep(minOf(200L, remain.coerceAtLeast(1L))) } catch (_: InterruptedException) {}
        }
        for (l in listeners) try { nsd.stopServiceDiscovery(l) } catch (_: Throwable) {}
        Thread.sleep(300)   // 给最后的 resolve 回调留收尾时间
        val out = found.toList()
        Log.i("GPhoto2", "发现: mDNS 结果 $out (cancel=$wifiCancelRequested)")
        return out
    }

    /** 相机 IP 所在网段的进程绑定（供 native 探测/会话用——native socket 无法
     *  逐 socket 绑 Network）：
     *  - 受管 Wi-Fi 网段（手机连相机热点 AP 模式）：bindProcessToNetwork 该网络，
     *    默认网络是移动数据时 native socket 不绑定到不了相机；
     *  - 本机热点网段（相机连手机热点）：【不绑定】——未标记 socket 命中规则链
     *    fwmark 0x0/0x10000 → 热点直连路由表（小米 11 实测表 97）；
     *    此前的热点 netId 手工绑定（反射/Parcel 伪造）全属多余且有害（会打上
     *    explicit 标记反而错过直连规则）。 */
    private fun bindProcessForCameraSubnet(cameraIp: String) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val camBytes = try {
            java.net.InetAddress.getByName(cameraIp).address
        } catch (_: Exception) { return }
        if (camBytes.size != 4) return
        val camPrefix = "${camBytes[0].toInt() and 0xFF}.${camBytes[1].toInt() and 0xFF}.${camBytes[2].toInt() and 0xFF}"
        for (r in wifiRoutes()) {
            if (r.prefix != camPrefix) continue
            val net = r.network ?: return   // 热点网段：直连，无需绑定
            if (cm.bindProcessToNetwork(net))
                Log.i("GPhoto2", "网络策略: 相机 $cameraIp 在受管网段 $camPrefix.x，进程绑定该网络")
            else
                Log.w("GPhoto2", "网络策略: 相机 $cameraIp 受管网段绑定失败，走默认网络")
            return
        }
    }

    /** 并发扫描网段的 15740 端口（PTP/IP），返回开放的 IP 列表。
     *  abort 非空时逐任务检查，命中即跳过剩余目标提前收尾（Wi-Fi 取消用）。
     *  出接口选择（不做进程级绑定）：
     *  - 受管网络网段：socket 由该 Network 的 socketFactory 创建（逐 socket 绑定）；
     *  - 热点网段：socket 绑定本机热点接口源地址，内核按源地址匹配直连路由出包。 */
    private fun scanSubnet(
        route: WifiRoute,
        abort: (() -> Boolean)? = null,
        progress: (Int, Int) -> Unit
    ): List<String> {
        val found = java.util.Collections.synchronizedList(ArrayList<String>())
        val pool = java.util.concurrent.Executors.newFixedThreadPool(16)
        val ips = (1..254).map { "${route.prefix}.$it" }
        val latch = java.util.concurrent.CountDownLatch(ips.size)
        val done = java.util.concurrent.atomic.AtomicInteger()
        for (ip in ips) {
            pool.execute {
                try {
                    if (abort?.invoke() == true) return@execute   // 取消：跳过本目标
                    val s = route.network?.socketFactory?.createSocket() ?: java.net.Socket()
                    try {
                        route.localAddr?.let {
                            try { s.bind(java.net.InetSocketAddress(it, 0)) } catch (_: Throwable) {}
                        }
                        // 热点 Wi-Fi 高 RTT（实测 ping 52~235ms，SoftAp 排队/省电唤醒更慢），
                        // 300ms 会把唯一在线的相机漏成"无设备"；1200ms 下 16 并发扫 /24
                        // 最坏 ~19s，仍在 40s latch 内
                        s.connect(java.net.InetSocketAddress(ip, 15740), 400)
                        found.add(ip)
                    } finally { s.close() }
                } catch (_: Throwable) {
                } finally {
                    progress(done.incrementAndGet(), ips.size)
                    latch.countDown()
                }
            }
        }
        latch.await(40, java.util.concurrent.TimeUnit.SECONDS)
        pool.shutdownNow()
        return found
    }

    /** 设置页「扫描相机」：mDNS 发现（秒级）优先，无果再网段扫描 + 读取相机名 */
    fun scanForCameras() {
        scanning.value = true
        try {
            GPhoto2Bridge.setup(ctx)
            val candidates = ArrayList<String>()
            scanText.value = "正在通过 mDNS 发现相机…"
            candidates.addAll(mdnsDiscoverCameras())
            val routes = wifiRoutes()
            if (candidates.isEmpty() && routes.isEmpty()) {
                scanText.value = "未发现可用 Wi-Fi 网络，请确认手机已连接 Wi-Fi 或已开启热点"
                return
            }
            if (candidates.isEmpty()) {
                for (r in routes) {
                    scanText.value = "正在扫描 ${r.prefix}.x…"
                    candidates.addAll(
                        scanSubnet(r) { done, total ->
                            scanText.value = "正在扫描 ${r.prefix}.x…（$done/$total）"
                        }
                    )
                }
            }
            scanResults.clear()
            for (ip in candidates.distinct()) {
                bindProcessForCameraSubnet(ip)   // 受管网段先绑定（native 探测用）；热点网段直连
                val info = synchronized(camMutex) { GPhoto2Bridge.nativeProbeCameraInfo(ip) } ?: "|"
                scanResults.add(ip to info)
            }
            if (scanResults.isEmpty())
                scanText.value = "未发现相机，请确认相机已进入 Wi-Fi 连接等待状态"
        } finally {
            scanning.value = false
        }
    }

    /** 自动连接优先通道：usb（默认）/ wifi。仅影响启动时自动连接的尝试顺序，
     *  手动点双卡与 USB 插线自动连接均不受影响。 */
    val autoConnChannel = mutableStateOf(prefs.getString("set_auto_conn_channel", "usb") ?: "usb")
    fun setAutoConnChannel(v: String) {
        autoConnChannel.value = v
        prefs.edit().putString("set_auto_conn_channel", v).apply()
    }

    /* ---------- 连接流程 ---------- */

    /** 当前所连 Wi-Fi 的网关 IP（手机连相机热点时 = 相机自身）；无 Wi-Fi 或无网关返回 null */
    private fun wifiGateway(): String? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (net in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val lp = cm.getLinkProperties(net) ?: continue
            for (r in lp.routes) {
                if (r.isDefaultRoute) {
                    val g = r.gateway ?: continue
                    val a = g.address
                    if (a.size == 4) {
                        return "${a[0].toInt() and 0xFF}.${a[1].toInt() and 0xFF}." +
                            "${a[2].toInt() and 0xFF}.${a[3].toInt() and 0xFF}"
                    }
                }
            }
        }
        return null
    }

    /** 双卡入口（IO 线程）：按选择通道直连；未选则 USB 优先。成功后清双卡状态。
     *  Wi-Fi 进行中点 USB 卡 = 取消 Wi-Fi（探测/扫描阶段立即生效）+ 接力启动 USB。 */
    fun startConnect(channel: String?): Boolean {
        if (connected) return false
        // Wi-Fi 进行中点 USB 卡：请求取消 Wi-Fi（①②③ 阶段生效），其退出后自动启动 USB
        if (connecting && pendingChannel.value == "wifi" && channel == "usb") {
            wifiCancelRequested = true
            pendingChannel.value = "usb"
            connFailMsg.value = "已取消 Wi-Fi，正在尝试 USB 连接"
            connText.value = "已取消 Wi-Fi，USB 连接中…"
            Thread { waitForWifiExitThenUsb() }.start()
            return true
        }
        if (connecting) return false
        connFailMsg.value = ""
        pendingChannel.value = channel
        return when (channel) {
            "usb" -> tryConnectManualUsb()
            "wifi" -> wifiConnectFlow(preferScan = false)
            else -> connectionFlow()
        }
    }

    /** Wi-Fi 取消后的 USB 接力：等 Wi-Fi 线程退出后启动 USB 直连。
     *  不设超时——Wi-Fi 流程内部各调用（probe/扫描）都有自身超时、必然返回；
     *  旧实现 8s 放弃会导致 probe 长阻塞时永远卡在"连接中"。camMutex 保证两条
     *  PTP 通道不并发；Wi-Fi 意外成功（connected）则放弃 USB 接力。
     *  接力终止（无相机/未授权）必须把 connPhase 恢复 disconnected——
     *  否则 UI 卡在 connecting 转圈且无任何线程在跑。 */
    private fun waitForWifiExitThenUsb() {
        while (connecting && !connected) {
            try { Thread.sleep(20) } catch (_: InterruptedException) { return }
        }
        if (connecting || connected) return
        connFailMsg.value = ""
        val dev = UsbPtpSession.findCamera(usbManager)
        if (dev == null) {
            connPhase.value = "disconnected"
            connText.value = "未连接，请选择连接方式"
            connFailMsg.value = "未检测到尼康相机，请用数据线连接相机与手机"
            pendingChannel.value = null
            return
        }
        if (!UsbPtpSession.hasPermission(usbManager, dev)) {
            connPhase.value = "disconnected"
            connText.value = "未连接，请选择连接方式"
            UsbPtpSession.requestPermission(ctx, usbManager, dev)
            connFailMsg.value = "已请求 USB 权限，请在弹窗中允许"
            pendingChannel.value = null
            return
        }
        tryConnectUsbCore(dev, auto = false)
    }

    /** Wi-Fi 卡连接中再点卡片 = 取消：探测/扫描阶段立即生效；进入配对后不可打断 */
    fun cancelWifiConnect() {
        if (connecting && pendingChannel.value == "wifi") {
            wifiCancelRequested = true
            connText.value = "正在取消 Wi-Fi 连接…"
        }
    }

    /** 手动点 USB 卡：已插线→直连；未插线→提示（UI 侧读 connFailMsg） */
    private fun tryConnectManualUsb(): Boolean {
        val dev = UsbPtpSession.findCamera(usbManager)
        if (dev == null) {
            connFailMsg.value = "未检测到尼康相机，请用数据线连接相机与手机"
            pendingChannel.value = null
            return false
        }
        if (!UsbPtpSession.hasPermission(usbManager, dev)) {
            UsbPtpSession.requestPermission(ctx, usbManager, dev)
            connFailMsg.value = "已请求 USB 权限，请在弹窗中允许"
            pendingChannel.value = null
            return false
        }
        return tryConnectUsbCore(dev, auto = false)
    }

    /** Wi-Fi 取消检查（①②③ 阶段调用）：命中即收尾退出。
     *  切换 USB 意图时保持 connecting/pendingChannel，由 USB 接力线程无缝接管。 */
    private fun wifiCancelled(): Boolean {
        if (!wifiCancelRequested) return false
        if (pendingChannel.value == "usb") {
            connText.value = "已取消 Wi-Fi，USB 连接中…"
        } else {
            connText.value = "已取消"
            connPhase.value = "disconnected"
            connFailMsg.value = "已取消 Wi-Fi 连接"
            pendingChannel.value = null
        }
        return true
    }

    /** Wi-Fi 连接（IO 线程）：上次 IP → mDNS 发现 → 网段扫描（兜底）→ 命中即连。
     *  preferScan=true 时跳过上次 IP 直接发现（Wi-Fi 卡内「扫描相机」按钮）。
     *  探测/发现阶段可被取消（点 Wi-Fi 卡 / 点 USB 卡切换）；进入配对后跑完。
     *  网络 出接口（实证方案，全程不做进程级绑定）：受管网段 socket 走
     *  Network.socketFactory，热点网段绑本机源地址——小米 11 实测热点直连路由
     *  在规则链兜底表里，未标记 socket 天然可达；此前把进程绑到受管 Wi-Fi
     *  反而给 socket 打上 explicit 标记、错过热点直连规则（黑洞根因）。 */
    private fun wifiConnectFlow(preferScan: Boolean): Boolean {
        connecting = true
        connPhase.value = "connecting"
        pendingChannel.value = "wifi"   // 标记当前尝试通道（双卡进度归位用）
        try {
            GPhoto2Bridge.setup(ctx)
            if (wifiCancelled()) return false
            if (!preferScan) {
                // ① 上次相机 IP（热点网段直连；受管网段先绑定再探测）
                val lastIp = prefs.getString("camera_ip", null)
                if (lastIp != null) {
                    connText.value = "正在连接上次的相机 $lastIp…"
                    bindProcessForCameraSubnet(lastIp)
                    val info = synchronized(camMutex) { GPhoto2Bridge.nativeProbeCameraInfo(lastIp) }
                    if (wifiCancelled()) return false
                    if (info != null) return finishConnect(lastIp, info)
                    Log.i("GPhoto2", "上次 IP $lastIp 不可达，转为 mDNS/网段发现")
                }
            }
            // ② mDNS 服务发现（主路径，秒级，免 /24 扫描）
            connText.value = "正在通过 mDNS 发现相机…"
            var candidates = mdnsDiscoverCameras()
            if (wifiCancelled()) return false
            // ③ 兜底：网段扫描（受管走网络 socketFactory，热点绑本机源地址）
            val routes = wifiRoutes()
            if (candidates.isEmpty() && routes.isEmpty()) {
                connText.value = "未发现可用 Wi-Fi 网络"
                connPhase.value = "disconnected"
                connFailMsg.value = "未发现可用 Wi-Fi 网络，请确认手机已连接 Wi-Fi 或已开启热点"
                pendingChannel.value = null
                return false
            }
            if (candidates.isEmpty()) {
                for (r in routes) {
                    if (wifiCancelled()) return false
                    connText.value = "正在扫描网络 ${r.prefix}.x…"
                    candidates = scanSubnet(r, abort = { wifiCancelRequested }) { done, total ->
                        if (!wifiCancelRequested)                       // 取消后不再覆盖"已取消/USB 连接中"状态文字
                            connText.value = "正在扫描网络 ${r.prefix}.x…（$done/$total）"
                    }
                    if (wifiCancelled()) return false
                    if (candidates.isNotEmpty()) break
                }
            }
            if (candidates.isEmpty()) {
                connText.value = "未发现相机，请确认相机已进入 Wi-Fi 连接等待状态"
                connPhase.value = "disconnected"
                connFailMsg.value = "未发现相机，请确认相机已进入 Wi-Fi 连接等待状态"
                pendingChannel.value = null
                return false
            }
            // ④ 相机信息探测（逐台；受管网段先绑定；可取消）
            val named = ArrayList<Pair<String, String>>()
            for (ip in candidates) {
                if (wifiCancelled()) return false
                bindProcessForCameraSubnet(ip)
                val info = synchronized(camMutex) { GPhoto2Bridge.nativeProbeCameraInfo(ip) } ?: "|"
                named.add(ip to info)
            }
            if (named.size == 1) {
                val (ip, info) = named[0]
                return finishConnect(ip, info)
            }
            scanResults.clear()
            scanResults.addAll(named)
            connText.value = "发现 ${named.size} 台相机，请在上方选择"
            connPhase.value = "disconnected"
            connFailMsg.value = "发现 ${named.size} 台相机，请在下方选择"
            pendingChannel.value = null
            return false
        } finally {
            connecting = false
            wifiCancelRequested = false
        }
    }

    /** 连接总流程（IO 线程）：USB 直连优先（插线即走 USB）→ 未选通道时按自动连接偏好
     *  决定先试哪条；Wi-Fi 内部 = 上次 IP → 网段扫描（热点网段优先）→ 命中即连。 */
    fun connectionFlow(): Boolean {
        if (connecting || connected) return false
        // 手动点了 Wi-Fi 卡 → 跳过 USB 直抢（用户意图优先）
        if (pendingChannel.value == "wifi") {
            pendingChannel.value = null
            return wifiConnectFlow(preferScan = false)
        }
        connFailMsg.value = ""
        // ⓪ USB 直连优先：插着已授权的尼康相机 → 直接走 USB，跳过整个 Wi-Fi 流程
        UsbPtpSession.findCamera(usbManager)?.let { dev ->
            if (UsbPtpSession.hasPermission(usbManager, dev)) {
                pendingChannel.value = "usb"   // 标记当前尝试通道（双卡进度归位用）
                if (tryConnectUsbCore(dev, auto = false)) return true
            }
        }
        // Wi-Fi 已连接 + 自动连接偏好 USB 但没插线 → 维持现状，不折腾
        if (connected && usbSession == null) return true
        return wifiConnectFlow(preferScan = false)
    }

    /** 连接入口（带防并发守卫，设置页扫描结果点击触发） */
    fun connectToCamera(ip: String, info: String): Boolean {
        if (connecting) { scanText.value = "有连接正在进行…"; return false }
        connecting = true
        connPhase.value = "connecting"
        try {
            return finishConnect(ip, info)
        } finally {
            connecting = false
        }
    }

    /** 与指定相机完成连接（配对探针 + 常驻传输会话）并自动列目录 */
    private fun finishConnect(ip: String, info: String): Boolean {
        val (model, serial) = splitInfo(info)
        connText.value = "正在连接 $model ($serial)…"
        // 已连接则先干净断开旧会话（相机需要时间复位）
        if (usbSession != null) {          // USB 在连时改走 Wi-Fi：先关 USB 会话
            synchronized(camMutex) { usbSession?.close() }
            usbSession = null
            connected = false
        }
        if (connected) {
            stopEventPolling()
            synchronized(camMutex) { GPhoto2Bridge.nativeTransferClose() }
            Thread.sleep(3000)
        }
        bindToWifiNetwork(ip)
        connText.value = "正在建立连接…"
        val ret = synchronized(camMutex) { GPhoto2Bridge.nativePairingProbe(ip) }
        if (ret != 0) {
            connected = false
            ptpPath = ""
            stopKeepAlive()       // 重连失败时清理可能残留的保活
            connText.value = "连接失败（$ret），请确认相机已进入 Wi-Fi 连接等待状态后重试"
            connFailMsg.value = "连接失败（$ret），请确认相机已进入 Wi-Fi 连接等待状态后重试"
            connPhase.value = "disconnected"
            pendingChannel.value = null
            return false
        }
        connected = true
        ptpPath = "ptpip:$ip"
        connChannel.value = "wifi"
        lastConnChannel.value = "wifi"
        startEventPolling()
        startKeepAlive()          // 前台服务 + WifiLock：后台不再被冻结断连
        prefs.edit().putString("camera_ip", ip).apply()
        connectedIp.value = ip
        connDetail.value = info
        connText.value = "正在读取照片列表…"
        uiLog = listFiles()
        refreshCamStorage()          // 容量展示（连接详情浮层）
        connText.value = "$model ($serial) · $ip"
        connPhase.value = "connected"
        everConnected = true
        return true
    }

    /**
     * 连接/传输阶段（native 会话建立前）的网络绑定：
     * - 相机在本机热点网段（相机连手机热点，热点非受管网络）：【不绑定】——
     *   未标记 socket 命中规则链 fwmark 0x0/0x10000 → 热点直连路由表
     *   （小米 11 实测：表 97 = 192.168.18.0/24 dev wlan1）。
     *   此前对热点 netId 的手工绑定（反射/Parcel 伪造）全属多余且有害——
     *   explicit 标记反而错过直连规则、走进默认网络黑洞。
     * - 相机在受管 Wi-Fi 网段（手机连相机热点 AP 模式）：bindProcessToNetwork
     *   该网络——默认网络是移动数据时 native socket 不绑定到不了相机。
     */
    private fun bindToWifiNetwork(cameraIp: String): String {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val camBytes = try {
            java.net.InetAddress.getByName(cameraIp).address
        } catch (e: Exception) {
            Log.i("GPhoto2", "IP 解析失败($cameraIp)，未绑定网络")
            return "IP 解析失败，未绑定网络"
        }
        if (camBytes.size == 4) {
            val camPrefix = "${camBytes[0].toInt() and 0xFF}.${camBytes[1].toInt() and 0xFF}.${camBytes[2].toInt() and 0xFF}"
            val hs = hotspotSubnet()
            if (hs != null && hs.first == camPrefix) {
                val desc = "直连模式（相机在本机热点网段 $camPrefix.x）"
                Log.i("GPhoto2", "网络策略: $desc")
                return desc
            }
        }
        var desc = "未发现受管 Wi-Fi 网络"
        for (net in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val lp = cm.getLinkProperties(net) ?: continue
            for (la in lp.linkAddresses) {
                if (la is LinkAddress && ipInSubnet(la, camBytes)) {
                    if (cm.bindProcessToNetwork(net)) {
                        desc = "绑定到相机所在网络（${la.address.hostName ?: ""}${la.address.hostAddress}）"
                        Log.i("GPhoto2", "网络策略: $desc")
                        return desc
                    }
                }
            }
        }
        // 相机 IP 不在任何 Wi-Fi 网段：不绑定，走默认网络
        Log.i("GPhoto2", "网络策略: $desc，相机 IP=$cameraIp 直连模式")
        return desc
    }

    /** 相机 IP 是否落在 IP/prefix 子网内 */
    private fun ipInSubnet(la: LinkAddress, camBytes: ByteArray): Boolean {
        val addr = la.address.address
        if (addr.size != camBytes.size) return false
        val prefix = la.prefixLength
        val full = prefix / 8
        val rem = prefix % 8
        for (i in 0 until full) if (addr[i] != camBytes[i]) return false
        if (rem > 0) {
            val mask = (0xFF shl (8 - rem))
            if ((addr[full].toInt() and 0xFF and mask) != (camBytes[full].toInt() and 0xFF and mask)) return false
        }
        return true
    }

    /* ---------- 列目录 / 下载 / 预览 ---------- */

    /** 通道分发：对象大小（USB=GetObjectInfo 缓存；Wi-Fi=0x9421 native） */
    private fun sizeOf(handle: Int): Long = synchronized(camMutex) {
        usbSession?.objectSize(handle) ?: GPhoto2Bridge.nativeObjectSizeNative(handle)
    }

    /** 通道分发：偏移读（USB=GetPartialObject；Wi-Fi=native 偏移读） */
    private fun readOff(handle: Int, off: Long, len: Int): ByteArray? = synchronized(camMutex) {
        usbSession?.partialRead(handle, off, len)
            ?: GPhoto2Bridge.nativePreviewNative(handle, off.toInt(), len)
    }

    /** 枚举耗时计入 PerfTrace（"枚举"），明细日志由 verbose 开关控制 */
    fun listFiles(): String = withLvPaused { PerfTrace.measure("枚举") { listFilesInner() } }

    private fun listFilesInner(): String = synchronized(camMutex) {
        if (demoMode.value) return "演示模式：共 ${photoRows.size} 个文件"
        if (!connected) return "未连接相机，请先连接后再读取照片"
        // USB 全量枚举（逐对象 GetObjectInfo）需数秒：立即给出加载提示，替代旧日志（如"就绪"）
        uiLog = "正在读取照片列表…"
        // 配对模式原生列目录："句柄:YYYYMMDD-HHMMSS:类型|..."
        val failMsg = "读取照片列表失败，连接可能已断开，请重新连接"
        val res: String = if (usbSession != null) {
            val s = usbSession!!
            val r = s.enumerate()
            when {
                r == null -> return "读取照片列表失败：${s.lastDiag}"
                r.isEmpty() -> return "相机中未找到照片：${s.lastDiag}"
                else -> r
            }
        } else {
            GPhoto2Bridge.nativeListNative() ?: return failMsg
        }
        val rows = res.split('|').filter { it.isNotBlank() }
        if (rows.isEmpty()) return "相机中没有可传输的照片"
        val parsed = rows.mapNotNull { row ->
            val p = row.split(':', limit = 4)
            if (p.size < 3) return@mapNotNull null
            val handle = p[0].toIntOrNull() ?: return@mapNotNull null
            val stamp = p[1]
            val type = p[2].uppercase()
            // 第 4 段 = 真实文件名（USB 枚举提供；Wi-Fi 枚举无此段）。仅作配对键，
            // name 仍用合成名 —— 保持已下载标记（downloadedNames 键）与既有保存名不变。
            val fileNo = p.getOrNull(3)?.substringBeforeLast('.')
            val name = "IMG_${stamp}_${p[0].takeLast(4).padStart(4, '0')}.${type.lowercase()}"
            PhotoRow(name, handle, type, stamp, fileNo)
        }.sortedWith(
            compareByDescending<PhotoRow> { it.stamp }                  // 从新到旧
                .thenByDescending { it.captureSeq() }                   // 同秒：晚拍的在前（连拍不再内部倒序）
                .thenBy { if (it.type.equals("NEF", true)) 1 else 0 }   // 同张兜底：JPG 在 NEF 前
        )
        syncDownloadedNames()                          // 本地删除过的照片不再标记已下载
        parsed.forEach { it.downloaded.value = it.name in downloadedNames.value }
        // 原子快照提交（与 appendNewObjects 同理：多步结构写须防并发交错产生重复）
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            photoRows.clear()
            photoRows.addAll(parsed)
        }
        rebuildRowIndex()
        dedupePhotoRows()
        refreshPairRows()                 // 枚举线程上预构建配对缓存（滑动主线程零重算）
        startProtectionScan(parsed)       // 后台渐进采集保护状态（网格徽标 + 删除预警）
        displayLimit.value = if (chunkedLoad.value) chunkSize.value else Int.MAX_VALUE   // 重新枚举 → 分块进度重置
        val autoPreview = prefs.getBoolean("set_auto_preview", true)
        if (autoPreview) {
            startPreviewLoading(parsed)                // 异步逐个取内嵌缩略图
            return "共 ${photoRows.size} 个文件"
        }
        return "共 ${photoRows.size} 个文件"
    }

    /* =====================================================================
     *  演示数据模式（性能诊断）
     *  ---------------------------------------------------------------------
     *  相机连着时没有任何 adb 通道：USB 口被相机占着，Wi-Fi 又与相机热点互斥，
     *  连不了家里网络 —— 所以 UI 流畅度只能在离线状态下测。
     *  缩略图刻意用代码生成的渐变位图（解码成本≈0、886 行复用 8 个实例）：
     *  若 mock 下仍然掉帧，就能 100% 锁定是重组问题而不是 IO/解码问题。
     * ===================================================================== */
    val demoMode = mutableStateOf(false)
    /** 假下载回调粒度（KB）：64 = 复刻 USB 整包模式的高频回调（50MB≈800 次）；1024 = 分段模式 */
    val demoStepKb = mutableStateOf(64)
    val demoDownloadRunning = mutableStateOf(false)
    /* 进度条补间时长（ms），0 = snap（不插值）。默认 250 —— 与 got 的 250ms 节流严格
     * 匹配，每段补间恰好走完，进度条匀速线性推进。
     * 历史教训：曾默认 0(snap)。旧实现把动画值读在组合期（`by` 委托），补间期间
     * 每帧重组整行（实测 120Hz / 8.33ms 预算下 p50 16.5ms，任何时长都超支），
     * 只好关掉插值靠节流频率撑观感。进度读取下沉到绘制阶段后（drawBehind 内读
     * State.value，见 RowFillLayer / OverallFill / PreviewRunningButton），动画帧
     * 只剩一次 drawRect，零重组零布局 —— 补间成本近零，线性插值默认打开。
     * 实测（120Hz 真机，同进程四轮交替对照，演示下载 20 张）：tween250 p50=8.2ms /
     * jank 7.6% / fps≈108，snap p50=16.5ms / jank 54~57% / fps≈72 —— snap 下内容
     * 近静态，HyperOS 智能刷新率把屏幕降回 60Hz，帧距翻倍反被计入掉帧；补间让内容
     * 持续有帧，屏幕稳在 120Hz。线性补间在此设备上是双赢：更平滑且测得更"快"。
     * 测具仍可运行时调（广播 cmd=tween；0 回退 snap 做对照）。 */
    val progressTweenMs = mutableStateOf(250)
    /** 测量用：运行时摘掉总进度条（定位 M3 LinearProgressIndicator 自带补间的开销） */
    val perfHideOverallBar = mutableStateOf(false)
    /** 测量用：运行时摘掉队列行的进度填充层 */
    val perfHideRowFill = mutableStateOf(false)
    /** 测量用：运行时摘掉队列行缩略图 */
    val perfHideRowThumb = mutableStateOf(false)

    /** 进度补间的 AnimationSpec：0 = snap。
     *  注意补间时长与更新间隔的关系 —— 500ms 补间配 250ms 更新，动画永远跑不完，
     *  于是每一帧都在重组 + 重新测量；时长小于更新间隔时动画才能停下来。 */
    fun progressSpec(): androidx.compose.animation.core.AnimationSpec<Float> {
        val ms = progressTweenMs.value
        return if (ms <= 0) androidx.compose.animation.core.snap()
        else androidx.compose.animation.core.tween(ms, easing = androidx.compose.animation.core.LinearEasing)
    }
    @Volatile private var demoStop = false

    private val demoBitmaps = ArrayList<Bitmap>()

    /** 8 张共享渐变位图：行数再多内存开销也不变，且零解码 */
    private fun demoBitmap(i: Int): Bitmap {
        if (demoBitmaps.isEmpty()) {
            repeat(8) { k ->
                val b = Bitmap.createBitmap(160, 160, Bitmap.Config.RGB_565)
                val cv = android.graphics.Canvas(b)
                val g = android.graphics.LinearGradient(
                    0f, 0f, 160f, 160f,
                    android.graphics.Color.HSVToColor(floatArrayOf(k * 45f, 0.35f, 0.92f)),
                    android.graphics.Color.HSVToColor(floatArrayOf(k * 45f + 30f, 0.5f, 0.55f)),
                    android.graphics.Shader.TileMode.CLAMP
                )
                cv.drawRect(0f, 0f, 160f, 160f, android.graphics.Paint().apply { shader = g })
                demoBitmaps.add(b)
            }
        }
        return demoBitmaps[i % demoBitmaps.size]
    }

    fun setDemoMode(on: Boolean) {
        demoMode.value = on
        if (on) loadDemoRows(886) else clearDemo()
    }

    private fun loadDemoRows(n: Int) {
        stopDemoDownloads()
        val rows = ArrayList<PhotoRow>(n)
        for (i in 0 until n) {
            val ld = java.time.LocalDate.now().minusDays((i % 30).toLong())
            val hh = 8 + (i / 30) % 12
            val stamp = "%04d%02d%02d-%02d%02d%02d".format(
                ld.year, ld.monthValue, ld.dayOfMonth, hh, (i * 7) % 60, (i * 13) % 60
            )
            val type = if (i % 3 == 0) "NEF" else "JPG"
            val r = PhotoRow(
                "IMG_${stamp}_$i.${type.lowercase()}", 0x900000 + i, type, stamp, "DSC_${1000 + i}"
            )
            r.preview.value = demoBitmap(i)
            // 演示保护：每 89 张预置 1 张受保护（共约 10 张）——网格/预览出保护徽标，
            // 删除预检计入 protectedCount，确认删除时被 0x200D 分支跳过 → 可离线演练
            // 「已删除 N 张；M 张受保护已跳过」的混合结果框与全成功的静默气泡两条路径
            if (i % 89 == 7) r.isProtected.value = true
            rows.add(r)
        }
        rows.sortWith(
            compareByDescending<PhotoRow> { it.stamp }
                .thenByDescending { it.captureSeq() }
                .thenBy { if (it.type.equals("NEF", true)) 1 else 0 }
        )
        photoRows.clear()
        photoRows.addAll(rows)
        rebuildRowIndex()
        refreshPairRows()
        displayLimit.value = if (chunkedLoad.value) chunkSize.value else Int.MAX_VALUE
        connected = true
        connPhase.value = "connected"
        connChannel.value = "usb"
        lastConnChannel.value = "usb"
        connDetail.value = "Nikon Z 6_2 (演示)|DEMO"
        connectedIp.value = "演示模式"
        connText.value = "演示模式 · ${photoRows.size} 张"
        uiLog = "演示模式：${photoRows.size} 张假数据"
        demoRowTotal = photoRows.size
        refreshCamStorage()   // 演示假容量：连接详情浮层立即可见
    }

    private fun clearDemo() {
        stopDemoDownloads()
        photoRows.clear()
        rebuildRowIndex()
        refreshPairRows()
        connected = false
        connPhase.value = "disconnected"
        connChannel.value = ""
        lastConnChannel.value = ""
        connDetail.value = ""
        connectedIp.value = ""
        connText.value = "未连接 · 点按连接相机"
        camStorage.value = null
        deletePlan.value = null
        deleteResult.value = null
        deleteResultTitle.value = null
    }

    /** 假下载：走与真实下载同一条进度节流路径，用来复现/验证下载页的更新压力 */
    fun startDemoDownloads(count: Int) {
        stopDemoDownloads()
        val step = demoStepKb.value * 1024L
        val items = ArrayList<QueueItem>()
        repeat(count) { i ->
            val it = QueueItem(0xD00000 + i, "DEMO_${1000 + i}.JPG", "JPG", "20261001-120000")
            it.total.value = 24L * 1024 * 1024
            items.add(it)
        }
        downloads.enqueueDemo(items)
        ensureSpeedTicker()   // 诊断页假下载同样走批次速度统计
        demoStop = false
        demoDownloadRunning.value = true
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            for (it in items) {
                if (demoStop) { it.status.value = QStatus.CANCELED; continue }
                it.status.value = QStatus.RUNNING
                val fStart = System.currentTimeMillis()   // 队列口径统计要累加每张纯下载用时
                var got = 0L
                val total = it.total.value
                var idx = 0
                val every = paceEvery(total, step)
                while (got < total) {
                    if (demoStop) break
                    got = minOf(got + step, total)
                    it.publishProgress(got, total)
                    if (idx++ % every == 0) paceOnce()   // 见下：按步数均摊，两档总时长一致
                }
                if (demoStop) { it.status.value = QStatus.CANCELED; break }
                it.elapsedMs = System.currentTimeMillis() - fStart
                it.flushProgress(total)
                it.status.value = QStatus.DONE
                Thread.sleep(40)
            }
            demoDownloadRunning.value = false
        }.apply { isDaemon = true; name = "demo-download"; start() }
    }

    /** 只入队不跑：用来把"页面本身的开销"和"下载活动带来的开销"分开测 */
    fun enqueueDemoItems(count: Int) {
        val items = ArrayList<QueueItem>()
        repeat(count) { i ->
            val it = QueueItem(0xD00000 + i, "DEMO_${1000 + i}.JPG", "JPG", "20261001-120000")
            it.total.value = 24L * 1024 * 1024
            items.add(it)
        }
        downloads.enqueueDemo(items)
    }

    /* 假下载的节拍控制。
     * 两个坑都踩过：
     *  ① 每步 Thread.sleep(1)：64KB 档 384 步/文件 ≈ 每秒几百次定时器唤醒，
     *     实测把帧率从 108 压到 54 —— 这是 sleep 空转造出来的假象，真实 USB 是阻塞读。
     *  ② 只给 64KB 档配节拍：1MB 档（24 步）不睡，20 个文件瞬间跑完，
     *     测出来的"1MB 很流畅"其实是空页面在空转。
     * 现在按步数均摊：每个文件固定 12 次 sleep × 100ms ≈ 1.2s，与步长无关，
     * 于是"回调频率"成为两档之间唯一的变量 —— 这才是我要测的东西。 */
    private fun paceEvery(total: Long, step: Long): Int =
        maxOf(1, ((total / step.coerceAtLeast(1)) / 12L).toInt())

    private fun paceOnce() {
        try { Thread.sleep(100) } catch (_: InterruptedException) {}
    }

    fun stopDemoDownloads() {
        demoStop = true
        demoDownloadRunning.value = false
        downloads.clearAll()
    }

    /* ---------- 对照实验用：把"后台线程活动"与"Compose 状态写入"拆开测 ----------
     * 实测发现下载页一有活动就稳定掉到 60fps，且与队列长度无关（1 条和 20 条一样），
     * 说明开销是个常量，跟"画多少行"没关系。三组对照定位它到底来自哪里。 */
    @Volatile private var spinStop = false

    /** A：纯空转，完全不碰 Compose 状态 */
    fun spinOnly() {
        spinStop = true
        Thread {
            spinStop = false
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            var i = 0
            while (!spinStop && i < 384 * 20) {
                i++
                if (i % 32 == 0) try { Thread.sleep(100) } catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; name = "spin-only"; start() }
    }

    /** B/C：同样的循环，但每次迭代调用 publishProgress。
     *  live=false → 写在一个没人订阅的孤儿 QueueItem 上（只有快照写入成本）
     *  live=true  → 写在一个已入队、UI 正在订阅的 QueueItem 上（还有重组成本） */
    fun spinWriting(live: Boolean) {
        spinStop = true
        val dummy = QueueItem(0xE00001, "SPIN.JPG", "JPG", "20261001-120000").apply {
            total.value = 24L * 1024 * 1024
        }
        val target = if (live) {
            downloads.enqueueDemo(listOf(dummy)); dummy
        } else dummy
        Thread {
            spinStop = false
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            var got = 0L
            var i = 0
            while (!spinStop && i < 384 * 20) {
                i++
                got += 65536L
                target.publishProgress(got, 24L * 1024 * 1024)
                if (i % 32 == 0) try { Thread.sleep(100) } catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; name = "spin-write"; start() }
    }

    fun stopSpin() { spinStop = true }

    /** 演示模式下点单张下载：假驱动，不碰相机 */
    private fun runDemoItem(item: QueueItem) {
        if (item.total.value <= 0) item.total.value = 24L * 1024 * 1024
        val total = item.total.value
        val step = demoStepKb.value * 1024L
        var got = 0L
        var idx = 0
        val fStart = System.currentTimeMillis()   // 队列口径统计要累加每张纯下载用时
        val every = paceEvery(total, step)
        while (got < total) {
            if (demoStop || item.cancelRequested) {
                item.status.value = QStatus.CANCELED
                return
            }
            got = minOf(got + step, total)
            item.publishProgress(got, total)
            if (idx++ % every == 0) paceOnce()
        }
        item.elapsedMs = System.currentTimeMillis() - fStart
        item.flushProgress(total)
        item.status.value = QStatus.DONE
    }

    /** 保存到系统相册：自定义 SAF 目录优先；否则 MediaStore。
     *  「按拍摄日期文件夹保存」开启时，JPG 与 NEF 同入 <根>/<拍摄日期>/ 子夹（Q2=A）。 */
    private fun savePhoto(name: String, type: String, data: ByteArray, stamp: String): Boolean {
        val mime = if (type.equals("NEF", true)) "image/x-nikon-nef" else "image/jpeg"
        val dateSub = stampDateSub(stamp)
        // 自定义目录（SAF tree URI）
        customDirUri.value?.let { uriStr ->
            return saveToSaf(uriStr, name, mime, data, if (dateFolderOn.value) dateSub else null)
        }
        // 默认 MediaStore 位置
        return try {
            val rel = if (dateFolderOn.value && dateSub != null)
                "Pictures/ShineMeow/$dateSub" else "Pictures/ShineMeow"
            // NEF（raw 图像，Android 10+ 识别为 image media）必须走 Images 集合：
            // Files 集合只允许 Download/Documents 路径， Pictures 会直接被拒（实测异常见日志）。
            if (type.equals("NEF", true) && android.os.Build.VERSION.SDK_INT >= 29) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                    }
                    val uri = ctx.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                    ) ?: return false
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    true
                } catch (t: Throwable) {
                    // 个别系统对 Images+raw 校验严格 → 回退 Files 集合（只能 Download/）
                    Log.w("GPhoto2", "NEF 写 Pictures 失败，回退 Download", t)
                    val dlRel = if (dateFolderOn.value && dateSub != null)
                        "Download/ShineMeow/$dateSub" else "Download/ShineMeow"
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, dlRel)
                    }
                    val uri = ctx.contentResolver.insert(
                        MediaStore.Files.getContentUri("external_primary"), values
                    ) ?: return false
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    true
                }
            } else {
                val collection = if (type.equals("NEF", true))
                    MediaStore.Files.getContentUri("external_primary")
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    if (android.os.Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
                    }
                }
                val uri = ctx.contentResolver.insert(collection, values) ?: return false
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                true
            }
        } catch (t: Throwable) {
            Log.e("GPhoto2", "savePhoto 失败: $name", t)
            false
        }
    }

    /** "20261001-…" → "2026-10-01"（拍摄日期，取自相机时间戳） */
    private fun stampDateSub(stamp: String): String? {
        if (stamp.length < 8) return null
        return try {
            val ld = java.time.LocalDate.of(
                stamp.substring(0, 4).toInt(),
                stamp.substring(4, 6).toInt(),
                stamp.substring(6, 8).toInt()
            )
            "%04d-%02d-%02d".format(ld.year, ld.monthValue, ld.dayOfMonth)
        } catch (_: Exception) {
            null
        }
    }

    /** SAF 目录写入：treeUri/<dateSub?>/<name>；dateSub 目录不存在则创建 */
    private fun saveToSaf(
        uriStr: String,
        name: String,
        mime: String,
        data: ByteArray,
        dateSub: String?
    ): Boolean {
        return try {
            val treeUri = android.net.Uri.parse(uriStr)
            val rootId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            var parentId = rootId
            if (dateSub != null) {
                parentId = findOrCreateSafDir(treeUri, rootId, dateSub) ?: return false
            }
            val parentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
            val newDoc = android.provider.DocumentsContract.createDocument(
                ctx.contentResolver, parentUri, mime, name
            ) ?: return false
            ctx.contentResolver.openOutputStream(newDoc)?.use { it.write(data) }
            true
        } catch (t: Throwable) {
            Log.e("GPhoto2", "saveToSaf 失败: $name", t)
            false
        }
    }

    /** 在 SAF 目录树里找名为 dirName 的子目录，找不到就创建，返回其 documentId */
    private fun findOrCreateSafDir(
        treeUri: android.net.Uri,
        rootId: String,
        dirName: String
    ): String? {
        return try {
            val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
            ctx.contentResolver.query(
                children,
                arrayOf(
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(2) == android.provider.DocumentsContract.Document.MIME_TYPE_DIR &&
                        c.getString(1) == dirName
                    ) return c.getString(0)
                }
            }
            val parentUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
            val docUri = android.provider.DocumentsContract.createDocument(
                ctx.contentResolver, parentUri,
                android.provider.DocumentsContract.Document.MIME_TYPE_DIR, dirName
            )
            docUri?.let { android.provider.DocumentsContract.getDocumentId(it) }
        } catch (t: Throwable) {
            Log.e("GPhoto2", "findOrCreateSafDir 失败", t)
            null
        }
    }

    /* ---------- 下载队列（状态与调度在 DownloadManager；传输 IO 在本类，注入回调）---------- */
    val downloads = DownloadManager(
        runItem = { item -> runQueueItem(item) },
        onWorkerExit = { ok, fail -> onQueueWorkerExit(ok, fail) }
    )
    val downloadQueue get() = downloads.queue
    val activeDownloadCount get() = downloads.activeCount

    /* 队列统计：一次遍历算出全部计数，取代下载页顶层的
     * 2×any + 4×count + firstOrNull（7 次全队列遍历，且每次都订阅所有条目的 status）。
     * 只订阅 status/total/expected（低频），**不读 got** —— got 是高频状态（节流后 250ms
     * 一次），读它会把统计拖进每次进度更新，进而让整个下载页重组。
     * got 相关读取单独放在下载页 OverallCard 的窄作用域（OverallFill/PercentPill）里。 */
    data class QueueStats(
        val running: Int, val queued: Int, val done: Int,
        val failed: Int, val canceled: Int,
        val runningItem: QueueItem?,     // 正在下载的条目（串行 worker，至多一条）
        val byteDone: Long,              // Σ DONE.total：已完成字节
        val byteQueuedKnown: Long,       // Σ QUEUED 已知大小（预取 expected / 演示预置 total）
        val queuedUnknown: Int,          // 大小未知的排队条目数（分母按均值估计）
        val byteRunningTotal: Long,      // RUNNING 条目 total 之和（开始下载时才写入）
        val msDone: Long                 // Σ DONE.elapsedMs：队列内已完成照片的纯下载用时
    ) {
        val hasActive get() = running + queued > 0
        val hasFinished get() = done + canceled > 0
        val denom get() = done + running + queued
        /** 已知大小条目的平均字节（估计未知排队条目用）；整批全未知时 0 */
        val avgKnownBytes: Long
            get() {
                val cnt = done + running + queued - queuedUnknown
                return if (cnt > 0) (byteDone + byteRunningTotal + byteQueuedKnown) / cnt else 0L
            }
        /** 字节权重分母：已完成 + 下载中 + 排队（已知/均值估计）。全未知时 0 → UI 退回按张数 */
        val byteDenom: Long
            get() = byteDone + byteRunningTotal + byteQueuedKnown + queuedUnknown * avgKnownBytes
        /** 队列口径平均速度：全部已完成照片一起算 —— Σ字节 / Σ纯下载用时。
         *  批次间空闲与排队等待不计入用时，跨批次自然累计；清空已完成即归零重来。 */
        val queueAvgBps: Long get() = if (msDone > 0) byteDone * 1000 / msDone else 0L
    }

    private fun scanQueueStats(): QueueStats {
        var r = 0; var q = 0; var d = 0; var f = 0; var c = 0
        var run: QueueItem? = null
        var bDone = 0L; var bQueued = 0L; var qUnknown = 0; var bRun = 0L; var ms = 0L
        for (it in downloadQueue) when (it.status.value) {
            QStatus.RUNNING -> { r++; if (run == null) run = it; bRun += it.total.value }
            QStatus.QUEUED -> {
                q++
                val sz = if (it.expectedBytes.value > 0) it.expectedBytes.value else it.total.value
                if (sz > 0) bQueued += sz else qUnknown++
            }
            QStatus.DONE -> { d++; bDone += it.total.value; ms += it.elapsedMs }
            QStatus.FAILED -> f++
            QStatus.CANCELED -> c++
        }
        return QueueStats(r, q, d, f, c, run, bDone, bQueued, qUnknown, bRun, ms)
    }

    val queueStats: QueueStats by derivedStateOf { scanQueueStats() }

    /* ---------- 批次速度 / 剩余时间（总览卡第二排·下载中）---------- */
    val batchSpeedBps = mutableStateOf(0L)   // 滑窗瞬时速度（字节/秒）
    val batchEtaMs = mutableStateOf(0L)      // 按滑窗速度推算的剩余毫秒
    // 平均速度/总用时不在这里维护 —— 它们是队列口径的纯派生值（QueueStats.queueAvgBps / msDone）
    private var speedTicker: Thread? = null

    /** 500ms 采样线程（常驻）：下载中时以 8s 滑窗算瞬时速度与 ETA，空闲时清零。
     *  常驻不退出 —— 旧版"批次结束即退出"有竞态：收尾窗口（≤500ms）内再次入队，
     *  ensureSpeedTicker 误判线程存活而不再启动，速度从此冻结成旧值。 */
    private fun ensureSpeedTicker() {
        if (speedTicker?.isAlive == true) return
        speedTicker = Thread {
            val window = ArrayDeque<Pair<Long, Long>>()   // (tMs, 队列累计字节)
            while (true) {
                val st = scanQueueStats()
                val now = System.currentTimeMillis()
                val cur = st.runningItem
                if (cur != null) {
                    val cum = st.byteDone + cur.got.value
                    window.addLast(now to cum)
                    while (window.size > 2 && now - window.first().first > 8000) window.removeFirst()
                    val (t0, b0) = window.first()
                    val dt = now - t0
                    if (dt >= 1500 && cum > b0) {
                        val bps = (cum - b0) * 1000 / dt
                        batchSpeedBps.value = bps
                        val remain = st.byteDenom - st.byteDone - cur.got.value
                        batchEtaMs.value = if (bps > 0 && remain > 0) remain * 1000 / bps else 0L
                    }
                } else if (!st.hasActive) {
                    // 条目交接空档（有排队、暂无下载中）保留最后一次速度/ETA，避免闪"…"
                    if (batchSpeedBps.value != 0L) batchSpeedBps.value = 0L
                    if (batchEtaMs.value != 0L) batchEtaMs.value = 0L
                }
                try { Thread.sleep(500) } catch (_: InterruptedException) { break }
            }
        }.apply { isDaemon = true; name = "batch-speed"; start() }
    }

    @Volatile private var prefetchInFlight = false

    /** 入队后预取文件大小（0x9421，走 camMutex 串行）：字节权重进度分母的前提。
     *  查询失败（-1）的条目保持 expectedBytes=0，分母按已知均值估计；
     *  真实值在该条目开始下载时由 runQueueItem 写入 total 自然替换。 */
    private fun prefetchSizes() {
        if (demoMode.value || prefetchInFlight) return
        prefetchInFlight = true
        Thread {
            try {
                for (it in downloadQueue.toList()) {
                    if (it.status.value != QStatus.QUEUED) continue
                    if (it.expectedBytes.value > 0 || it.total.value > 0) continue
                    val s = sizeOf(it.handle)
                    if (s > 0) it.expectedBytes.value = s
                }
            } finally { prefetchInFlight = false }
        }.apply { isDaemon = true; name = "size-prefetch"; start() }
    }

    /** 入队（单张：全屏预览页与网格触发统一走队列；单张不触发完成提醒） */
    fun enqueueDownload(row: PhotoRow) {
        downloads.enqueue(row)
        prefetchSizes()
    }

    fun retryDownload(item: QueueItem) {
        downloads.retry(item)
        prefetchSizes()   // 复位重排的条目可能还没预取到大小
    }

    fun cancelDownload(item: QueueItem) = downloads.cancel(item)

    /** 划走队列条目：未下载/下载中 → 取消下载，已完成/已取消/失败 → 移出队列（见 DownloadManager.dismiss） */
    fun dismissDownload(item: QueueItem) = downloads.dismiss(item)

    fun clearFinished() = downloads.clearFinished()

    fun cancelAllDownloads() = downloads.cancelAll()

    /* ---------- 队列完成提醒（震动 + 通知；设置可关，仅批量生效）---------- */
    var notifyDoneOn: Boolean by mutableStateOf(prefs.getBoolean("set_notify_done", true))
    fun setNotifyDone(v: Boolean) {
        notifyDoneOn = v
        prefs.edit().putBoolean("set_notify_done", v).apply()
    }

    private var batchMode = false            // 本次批次是否来自批量下载

    private fun vibrateOnce() {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26)
            v.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(200)
    }

    /** worker 收尾：批量模式弹震动+通知（设置可关）；无论单张/批量都清理通知栏进度 */
    private fun onQueueWorkerExit(ok: Int, fail: Int) {
        if (batchMode && (ok > 0 || fail > 0) && notifyDoneOn) {
            vibrateOnce()
            CameraKeepAliveService.notifyDone(ctx, "下载完成：成功 $ok 张，失败 $fail 张")
        }
        CameraKeepAliveService.clearProgress(ctx)
    }

    /** 串行消费一个队列条目：取总大小（0x9421）→ 0x9431 按 128KB 分块拉取拼装 → 保存。
     *  全程持有 camMutex（事件轮询与文件传输不能在同一条 PTP 流上交错）。
     *  每块更新进度（UI 250ms 节流）；通知栏 1s 节流。 */
    private fun runQueueItem(item: QueueItem) = synchronized(camMutex) {
        if (!connected) { item.status.value = QStatus.FAILED; return }
        item.status.value = QStatus.RUNNING
        item.cancelRequested = false
        item.resetProgress()
        ensureSpeedTicker()   // 批次速度/ETA 采样（幂等；全部终态后自退出）
        if (demoMode.value) { runDemoItem(item); return }
        val startMs = System.currentTimeMillis()   // 下载耗时统计（DONE 时写入 elapsedMs）
        // USB 通道：开关开 → 单事务整包（0x1009，进度按 bulk 轮回调）失败回退分段；
        // 默认 → 偏移读分块（0x101B，1MB 粒度带进度）；分段首块失败回退整对象一次取回
        val usb = usbSession
        if (usb != null) {
            val total = sizeOf(item.handle)
            if (total <= 0) { item.status.value = QStatus.FAILED; return }
            item.total.value = total
            val buf = ByteArray(total.toInt())
            // 实时速度节流器（250ms，与 Wi-Fi 分支同款）：整包回调与分段循环共用
            var lastUi = 0L
            var lastGot = 0L
            var lastT = System.currentTimeMillis()
            fun updateSpeed(nowGot: Long) {
                val now = System.currentTimeMillis()
                if (now - lastUi >= 250) {
                    val dt = (now - lastT).coerceAtLeast(1)
                    item.speed.value = humanSize((nowGot - lastGot).coerceAtLeast(0) * 1000 / dt) + "/s"
                    lastUi = now; lastGot = nowGot; lastT = now
                }
            }
            if (usbWholeTransfer.value) {
                if (item.cancelRequested) { item.status.value = QStatus.CANCELED; return }
                if (!connected) { item.status.value = QStatus.FAILED; return }
                val whole = usb.getObject(item.handle) { n ->
                    val g = minOf(n, total)
                    // 节流：整包模式每 64KB 回调一次（50MB ≈ 800 次），直写会让下载页整页重组
                    item.publishProgress(g, total)
                    updateSpeed(g)
                }
                if (whole != null) {
                    System.arraycopy(whole, 0, buf, 0, minOf(whole.size, buf.size))
                    item.flushProgress(total)      // 终态前补一次 100%，避免停在 97% 直接跳完成
                    if (savePhoto(item.name, item.type, buf, item.stamp)) {
                        item.elapsedMs = System.currentTimeMillis() - startMs
                        item.status.value = QStatus.DONE
                        photoRows.firstOrNull { it.handle == item.handle }?.downloaded?.value = true
                        markDownloaded(item.name)
                    } else {
                        item.status.value = QStatus.FAILED
                    }
                    return
                }
                item.resetProgress() // 整包失败 → 回退分段路径；节流器基准同步归零
                lastGot = 0; lastT = System.currentTimeMillis(); lastUi = 0
            }
            var got = 0L
            var fallbackWhole = false
            while (got < total) {
                if (item.cancelRequested) { item.status.value = QStatus.CANCELED; return }
                if (!connected) { item.status.value = QStatus.FAILED; return }
                val want = minOf(0x100000L, total - got).toInt()   // 1MB 粒度（partialRead 内部按 partialLimit 自适应）
                val chunk = readOff(item.handle, got, want)
                if (chunk == null) { fallbackWhole = true; break }
                val len = minOf(chunk.size.toLong(), total - got).toInt()
                System.arraycopy(chunk, 0, buf, got.toInt(), len)
                got += len
                item.publishProgress(got, total)
                updateSpeed(got)
            }
            if (fallbackWhole) {
                val whole = usb.getObject(item.handle)
                if (whole == null) { item.status.value = QStatus.FAILED; return }
                System.arraycopy(whole, 0, buf, 0, minOf(whole.size, buf.size))
            }
            item.flushProgress(total)
            if (savePhoto(item.name, item.type, buf, item.stamp)) {
                item.elapsedMs = System.currentTimeMillis() - startMs
                item.status.value = QStatus.DONE
                photoRows.firstOrNull { it.handle == item.handle }?.downloaded?.value = true
                markDownloaded(item.name)
            } else {
                item.status.value = QStatus.FAILED
            }
            return
        }
        val total = sizeOf(item.handle)
        if (total <= 0) { item.status.value = QStatus.FAILED; return }
        item.total.value = total
        val buf = ByteArray(total.toInt())
        var got = 0L
        var retries = 0
        var lastUi = 0L
        var lastNotif = 0L
        var lastGot = 0L
        var lastT = System.currentTimeMillis()
        while (got < total) {
            if (item.cancelRequested) { item.status.value = QStatus.CANCELED; return }
            if (!connected) { item.status.value = QStatus.FAILED; return }
            // 末块请求长度须精确到文件尾：尼康对 offset+len 超界的 GetPartialObject 直接报错
            // （gphoto2 native 会自动截断，USB 侧必须自己做）
            // 1MB 块（原 128KB）：0x9431 单次上限 1MB（C 层校验），高清预览 0.7~0.9MB
            // 单次成功实证 —— 块越大 PTP-IP 往返越少，下载吞吐显著提升
            val want = minOf(0x100000L, total - got).toInt()
            val chunk = readOff(item.handle, got, want)
            if (chunk == null) {
                retries++
                if (retries >= 3) { item.status.value = QStatus.FAILED; return }
                Thread.sleep(300)
                continue
            }
            retries = 0
            val len = minOf(chunk.size.toLong(), total - got).toInt()
            System.arraycopy(chunk, 0, buf, got.toInt(), len)
            got += len
            item.publishProgress(got, total)
            val now = System.currentTimeMillis()
            if (now - lastUi >= 250) {
                val dt = (now - lastT).coerceAtLeast(1)
                item.speed.value = humanSize((got - lastGot) * 1000 / dt) + "/s"
                lastUi = now; lastGot = got; lastT = now
            }
            if (now - lastNotif >= 1000) {
                CameraKeepAliveService.notifyProgress(
                    ctx, "${item.name} ${humanSize(got)}/${humanSize(total)}"
                )
                lastNotif = now
            }
        }
        item.flushProgress(total)
        if (savePhoto(item.name, item.type, buf, item.stamp)) {
            item.elapsedMs = System.currentTimeMillis() - startMs
            item.status.value = QStatus.DONE
            photoRows.firstOrNull { it.handle == item.handle }?.downloaded?.value = true
            markDownloaded(item.name)
        } else {
            item.status.value = QStatus.FAILED
        }
    }

    /** 单张下载 → 入队（全屏预览页与网格触发统一走队列；单张不触发完成提醒） */
    fun downloadOne(row: PhotoRow) {
        batchMode = false
        enqueueDownload(row)
    }

    /** 批量下载勾选的照片 → 逐条入队（worker 串行消费）。
     *  合并模式：按格式勾选框（JPG/NEF）展开；skipDownloaded 开启时排除已下载文件。 */
    fun downloadSelected() {
        batchMode = true
        val skip = skipDownloadedOn
        val rows = mutableListOf<PhotoRow>()
        if (mergePairs.value) {
            val wantJpg = batchFmtJpg.value
            val wantNef = batchFmtNef.value
            visiblePairs.filter { it.key in pairSelection.value }.forEach { p ->
                if (wantJpg) p.jpg?.takeUnless { skip && it.downloaded.value }?.let { rows.add(it) }
                if (wantNef) p.nef?.takeUnless { skip && it.downloaded.value }?.let { rows.add(it) }
            }
        } else {
            photoRows.filter { it.selected.value && !(skip && it.downloaded.value) }
                .forEach { rows.add(it) }
        }
        val fresh = rows.filter { r ->
            downloadQueue.none {
                it.handle == r.handle && it.status.value in setOf(QStatus.QUEUED, QStatus.RUNNING)
            }
        }
        if (fresh.isEmpty()) {
            downloadProgress.value =
                if (rows.isEmpty() && skip) "所选均已下载，已跳过"
                else "所选照片已在下载队列中"
            return
        }
        downloads.enqueueAll(fresh.map { QueueItem(it.handle, it.name, it.type, it.stamp) })
        prefetchSizes()       // 字节权重进度：入队即后台预取各文件大小
        clearSelection()   // 进度由队列页接管（走统一出口，选中计数器一并归零）
    }

    private fun decodeScaled(buf: ByteArray, off: Int, len: Int): Bitmap? = try {
        // 两步降采样：先量尺寸算 inSampleSize，再直接解出小图（避免全尺寸中间位图引发 GC 抖动）
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(buf, off, len, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 640) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(
                buf, off, len,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            )
            if (bmp != null) {
                val trimmed = trimBlackBars(bmp)
                if (trimmed.width > 640) {
                    val s = 640f / trimmed.width
                    Bitmap.createScaledBitmap(trimmed, 640, (trimmed.height * s).toInt().coerceAtLeast(1), true)
                } else trimmed
            } else null
        }
    } catch (_: Throwable) { null }

    /** 裁掉位图上下贴边的纯黑条：Z6_2 内嵌缩略图（JPG 的 EXIF 缩略图与 NEF 的
     *  RGB 条带）自带 letterbox 黑边，布局层 Crop 裁不掉（黑边长在位图里）。
     *  保守策略：整行平均亮度 <20 才算黑行，单边最多裁 35%，避免误裁暗色照片。 */
    private fun trimBlackBars(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 16 || h < 16) return src
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        fun rowLum(y: Int): Int {
            var sum = 0L
            var n = 0
            val step = (w / 24).coerceAtLeast(1)
            var x = 0
            while (x < w) {
                val p = px[y * w + x]
                sum += ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)
                n++
                x += step
            }
            return (sum / (n * 3L)).toInt()
        }
        val maxTrim = (h * 0.35f).toInt()
        var top = 0
        while (top < maxTrim && rowLum(top) < 20) top++
        if (top >= h - 16) return src                     // 几乎全黑，别裁
        var bot = 0
        while (bot < maxTrim && rowLum(h - 1 - bot) < 20) bot++
        if (top + bot >= h - 16) return src
        if (top == 0 && bot == 0) return src
        val nh = h - top - bot
        val dst = Bitmap.createBitmap(w, nh, Bitmap.Config.ARGB_8888)
        val part = IntArray(w * nh)
        src.getPixels(part, 0, w, 0, top, w, nh)
        dst.setPixels(part, 0, w, 0, 0, w, nh)
        return dst
    }

    /** 从对象前 128KB 里抠出可解码的完整 JPEG（EXIF/TIFF 内嵌缩略图）。
     *  实测 Z6_2 JPG 首块结构：SOI@0(主图) … SOI@33352(缩略图) … EOI@43956 ——
     *  主图前缀段和缩略图共享同一个 EOI，所以不能"跳到段尾继续扫"，
     *  必须收集全部 SOI→最近EOI 候选段，按段长升序尝试解码：
     *  真缩略图段最短（约 10KB），优先命中；主图截断段解码失败自动跳过。 */
    private fun extractPreviewJpeg(chunk: ByteArray): Bitmap? {
        val n = chunk.size
        val sois = ArrayList<Int>()
        var i = 0
        while (i + 1 < n) {
            if (chunk[i] == 0xFF.toByte() && chunk[i + 1] == 0xD8.toByte()) sois.add(i)
            i++
        }
        data class Cand(val start: Int, val len: Int)
        val cands = ArrayList<Cand>()
        for (s in sois) {
            var j = s + 2
            var eoi = -1
            while (j + 1 < n) {
                if (chunk[j] == 0xFF.toByte() && chunk[j + 1] == 0xD9.toByte()) { eoi = j + 2; break }
                j++
            }
            if (eoi > s) cands.add(Cand(s, eoi - s))
        }
        cands.sortBy { it.len }
        for (c in cands) {
            if (c.len < 512) continue              // 过短的伪配对忽略
            val bmp = decodeScaled(chunk, c.start, c.len)
            if (bmp != null) return bmp
        }
        return null
    }

    /** TIFF/NEF 结构感知：走 IFD 链（IFD0 → next / SubIFD 0x014a），
     *  找内嵌 JPEG 缩略图 tags 0x0201(JPEGInterchangeFormat)+0x0202(长度)。
     *  返回 longArrayOf(偏移, 长度)，找不到返回 null。 */
    private fun tiffThumbRange(chunk: ByteArray): LongArray? {
        if (chunk.size < 16) return null
        val bb = java.nio.ByteBuffer.wrap(chunk).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        if (bb.getShort(0).toInt() != 0x4949) return null          // "II"（Nikon 小端）
        if (bb.getShort(2).toInt() != 42) return null              // TIFF 魔数
        val visited = HashSet<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(bb.getInt(4))
        var offset = -1L
        var length = -1L
        fun value(e: Int, typ: Int, num: Int): Long =
            if (typ == 3 && num == 1) bb.getShort(e + 8).toLong() and 0xFFFF
            else bb.getInt(e + 8).toLong() and 0xFFFFFFFFL
        while (queue.isNotEmpty() && visited.size < 8) {
            val base = queue.removeFirst()
            if (base <= 0 || base + 2 > chunk.size || !visited.add(base)) continue
            val cnt = bb.getShort(base).toInt() and 0xFFFF
            if (base + 2 + cnt * 12 + 4 > chunk.size) continue
            for (i in 0 until cnt) {
                val e = base + 2 + i * 12
                val tag = bb.getShort(e).toInt() and 0xFFFF
                val typ = bb.getShort(e + 2).toInt() and 0xFFFF
                val num = bb.getInt(e + 4)
                when (tag) {
                    0x0201 -> if (num >= 1 && offset < 0) offset = value(e, typ, num)
                    0x0202 -> if (num >= 1 && length < 0) length = value(e, typ, num)
                    0x014a -> when {                               // SubIFD
                        num == 1 -> queue.add(value(e, typ, 1).toInt())
                        num in 2..8 -> {
                            val p = if (typ == 3) e + 8 else bb.getInt(e + 8)
                            if (p > 0 && p + num * 4 <= chunk.size)
                                for (k in 0 until num) queue.add(bb.getInt(p + k * 4))
                        }
                    }
                }
            }
            val nxt = bb.getInt(base + 2 + cnt * 12)
            if (nxt > 0) queue.add(nxt)
        }
        return if (offset > 0 && length > 0) longArrayOf(offset, length) else null
    }

    /** NEF（TIFF）缩略图条带解析：实测 Z6_2 NEF 的 IFD0 直接描述 160×120
     *  未压缩 RGB 条带 —— 0x0100/0x0101=宽高、0x0103=1(未压缩)、0x0106=2(RGB)、
     *  0x0111=StripOffsets、0x0117=StripByteCounts(=w*h*3)。
     *  返回 Triple(宽, 高, [数据偏移, 字节数])，结构不符返回 null。 */
    private fun tiffRgbStrip(chunk: ByteArray): Triple<Int, Int, LongArray>? {
        if (chunk.size < 16) return null
        val bb = java.nio.ByteBuffer.wrap(chunk).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        if (bb.getShort(0).toInt() != 0x4949 || bb.getShort(2).toInt() != 42) return null
        val base = bb.getInt(4)
        if (base <= 0 || base + 2 > chunk.size) return null
        val cnt = bb.getShort(base).toInt() and 0xFFFF
        if (base + 2 + cnt * 12 > chunk.size) return null
        var w = -1; var h = -1; var compression = -1; var photometric = -1
        var off = -1L; var byteCount = -1L
        for (i in 0 until cnt) {
            val e = base + 2 + i * 12
            val tag = bb.getShort(e).toInt() and 0xFFFF
            val typ = bb.getShort(e + 2).toInt() and 0xFFFF
            val num = bb.getInt(e + 4)
            val v: Long = when {
                typ == 3 && num == 1 -> bb.getShort(e + 8).toLong() and 0xFFFF
                typ == 4 && num == 1 -> bb.getInt(e + 8).toLong() and 0xFFFFFFFFL
                else -> -1L
            }
            when (tag) {
                0x0100 -> w = v.toInt()
                0x0101 -> h = v.toInt()
                0x0103 -> compression = v.toInt()
                0x0106 -> photometric = v.toInt()
                0x0111 -> if (num >= 1) off = v
                0x0117 -> if (num >= 1) byteCount = v
            }
        }
        if (w <= 0 || h <= 0 || off < 0 || byteCount <= 0) return null
        if (compression != 1 || photometric != 2) return null   // 只支持未压缩 RGB
        if (byteCount != w.toLong() * h * 3) return null        // 必须是 24bpp packed
        return Triple(w, h, longArrayOf(off, byteCount))
    }

    /** 未压缩 RGB24 字节 → Bitmap */
    private fun rawRgbToBitmap(data: ByteArray, w: Int, h: Int): Bitmap? {
        if (data.size < w * h * 3) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        var p = 0
        for (y in 0 until h) {
            var o = y * w * 3
            for (x in 0 until w) {
                pixels[p++] = 0xFF000000.toInt() or
                    ((data[o].toInt() and 0xFF) shl 16) or
                    ((data[o + 1].toInt() and 0xFF) shl 8) or
                    (data[o + 2].toInt() and 0xFF)
                o += 3
            }
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }

    /** 预览加载管线（原生会话单通道，camMutex 串行取块）：
     *  ① 首 128KB 纯 JPEG 段扫描（JPG 的 EXIF 缩略图必在）
     *  ② TIFF/NEF 解析：EXIF 式 0x0201/0x0202 缩略图，或 IFD0 未压缩 RGB 条带
     *  ③ 盲扩块扫描（最多到 768KB），兜底非标准布局
     *  列表变化（重新连接/列目录）会使旧加载线程失效。 */
    /* ---------- 缩略图磁盘缓存（cache/thumbs，键=时间戳_类型，跨会话稳定）---------- */
    var thumbCacheLimitMb: Int by mutableStateOf(prefs.getInt("set_thumb_cache_mb", 200))
    fun setThumbCacheMb(v: Int) {
        thumbCacheLimitMb = v
        prefs.edit().putInt("set_thumb_cache_mb", v).apply()
    }

    /* [SCROLLBAR-SEALED] 快速滚动条封存：阈值状态与持久化一并停用（set_scrollbar_threshold 键保留，重启不丢旧值）
    /** 快速滚动条显示阈值：照片数达到该值才显示右侧滚动条；-1 = 不显示。
     *  计数口径 = 未折叠的照片数（折叠天不参与，UI 侧按 collapsedDates 计算）。 */
    val scrollbarThreshold = mutableStateOf(prefs.getInt("set_scrollbar_threshold", 50))
    fun setScrollbarThreshold(v: Int) {
        scrollbarThreshold.value = v
        prefs.edit().putInt("set_scrollbar_threshold", v).apply()
    }
    [/SCROLLBAR-SEALED] */

    var thumbCacheBytes: Long by mutableStateOf(0L)
    // lazy 一次性创建：原 getter 每次访问都 mkdirs()，主线程反复系统调用造成滑动 IO 风暴
    private val thumbDir: java.io.File by lazy {
        java.io.File(ctx.cacheDir, "thumbs").apply { mkdirs() }
    }
    private fun thumbFile(key: String) =
        java.io.File(thumbDir, "$key.jpg")

    /** 目录遍历 + 逐个 length() —— 只能在后台线程调用。
     *  设置页首帧曾在主线程跑它（LaunchedEffect 直接调 refreshThumbCacheSize），
     *  与 320ms 滑入动画撞在一起 → 打开设置页偶现卡顿。 */
    fun computeThumbCacheBytes(): Long = thumbDir.listFiles()?.sumOf { it.length() } ?: 0L

    fun refreshThumbCacheSize() {
        thumbCacheBytes = computeThumbCacheBytes()
    }

    /** 清空缩略图缓存（设置页手动入口） */
    fun clearThumbCache() {
        thumbDir.listFiles()?.forEach { it.delete() }
        thumbNoCache.clear()
        thumbMem.evictAll()
        refreshThumbCacheSize()
    }

    /** 软上限：超过 thumbCacheMb 按最后修改时间从旧到新清理 */
    private fun enforceThumbCacheLimit() {
        val max = thumbCacheLimitMb.toLong() * 1024 * 1024
        val files = thumbDir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= max) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= max) break
            total -= f.length()
            f.delete()
        }
    }

    /* ---------- 缩略图内存 LRU：防全列表位图常驻内存引发 GC 滑动卡顿。
     *  预算 = 堆上限/8（≤64MB）；逐出时把对应行 preview 置 null（UI 回退占位），
     *  行滑回可视区由 ensureThumb 从磁盘缓存自动恢复。 ---------- */
    private val thumbRowByKey = java.util.concurrent.ConcurrentHashMap<String, PhotoRow>()
    /** 主线程无锁读路径：ensureThumb 的内存命中走这里（LruCache.get 是 synchronized，
     *  loader/读回线程高频 put 时主线程 get 会被锁住 → 滑动帧被拉长）。 */
    private val thumbFast = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    private val thumbMem = object : android.util.LruCache<String, Bitmap>(1) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        override fun entryRemoved(evicted: Boolean, key: String, old: Bitmap?, new: Bitmap?) {
            thumbFast.remove(key)
            // 逐出时行若仍在屏上则不清 preview——列表从新到旧加载时，在屏格子恰是
            // 最早放入 LRU 的一批，刷新触发全量重载必然把它们逐出 → 屏上格子
            // 集体变白（划出划回才恢复的元凶）。位图由行 preview 继续持有，出屏
            // 后的下一次逐出（onScreen=false）才真正释放。
            if (evicted) {
                val row = thumbRowByKey.remove(key)
                if (row != null && !row.onScreen) row.preview.value = null
            }
        }
    }

    init {
        thumbMem.resize(
            (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtMost(64 * 1024 * 1024)
        )
    }

    /** 缩略图缓存键：优先真实文件编号（USB 枚举，连拍同秒多张互不冲突）；
     *  Wi-Fi 枚举无文件名，退回句柄（相机对象 ID，跨会话稳定）。
     *  旧的 "时间戳_类型" 键在连拍同秒时会让多张照片共用缓存 → 缩略图串图。 */
    private fun thumbKey(row: PhotoRow) =
        "${row.fileNo ?: "h${row.handle}"}_${row.type.uppercase()}"

    /** 加载完成统一入口：写内存 LRU；格子在屏上、或尚未组合过（首轮加载，重连后
     *  loader 跑在网格重组之前）时直接写 preview 触发显示 —— 旧实现只认 onScreen，
     *  首轮位图全被压进 LRU，屏上格子留白、要滑出滑回才显示。
     *  滑动中滚出屏幕的只进 LRU，格子再进屏幕时 ensureThumb 缓存命中秒显。 */
    private fun putThumb(row: PhotoRow, bmp: Bitmap) {
        val key = thumbKey(row)
        thumbRowByKey[key] = row
        thumbNoCache.remove(key)
        thumbMem.put(key, bmp)
        thumbFast[key] = bmp
        PerfTrace.tick("thumb#加载")
        if (row.onScreen || !row.enteredOnce) row.preview.value = bmp
    }

    /* ---------- 磁盘读回串行队列：启动后首滑时内存 LRU 是冷的，滑动经过的每个
     *  新格子都会触发读磁盘。原实现"每格子开一个新线程"= 每秒 10~30 个线程创建
     *  + 并发 IO/解码争抢——首滑卡顿的元凶（多滑几次变流畅 = 格子进了内存缓存）。
     *  改为常驻单线程串行处理 + in-flight 去重 + 磁盘 miss 负缓存。 ---------- */
    /** LIFO 队列：最近进入可视区的格子优先读回（快速滑动时用户看的是当前位置，
     *  而不是 3 秒前滑过的那批）。旧的 FIFO 无界队列会把滑过的旧请求全部排完，
     *  新位置的缩略图要等几百次读盘才轮到 —— 表现为"越滑越空白"。 */
    private val thumbIoQueue = ConcurrentLinkedDeque<PhotoRow>()
    private val thumbInFlight = java.util.Collections.newSetFromMap(
        java.util.Collections.synchronizedMap(java.util.IdentityHashMap<PhotoRow, Boolean>())
    )
    /** 磁盘 miss 负缓存：key → 记录时刻。带 TTL —— 永不过期会把清缓存/重连后的
     *  读回路径永久挡死（磁盘刚被清空时打的标记，之后 ensureThumb 一律直接 return）。 */
    private val thumbNoCache = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var thumbIoRunning = false

    private fun pumpThumbIo() {
        if (thumbIoRunning) return
        synchronized(this) {
            if (thumbIoRunning) return
            thumbIoRunning = true
        }
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            try {
                while (true) {
                    val row = thumbIoQueue.pollLast() ?: break
                    try {
                        // 出屏取消：排队期间格子已滚出可视区（且不是首轮加载）→ 直接丢弃。
                        // 与 LIFO 配套：快速滑动时被滑过的请求不再占用读盘/解码。
                        if (row.enteredOnce && !row.onScreen) continue
                        // 排队期间可能已被 loader 填充，再查一次避免重复 IO
                        if (row.preview.value == null && !thumbFast.containsKey(thumbKey(row))) {
                            val bmp = loadThumbFromCache(thumbKey(row))
                            if (bmp != null) putThumb(row, bmp)
                            else thumbNoCache[thumbKey(row)] = System.currentTimeMillis()
                        }
                    } catch (_: Throwable) {
                    } finally {
                        thumbInFlight.remove(row)
                    }
                }
            } finally {
                synchronized(this) { thumbIoRunning = false }
                if (thumbIoQueue.isNotEmpty()) pumpThumbIo()   // 收尾空档又进新请求 → 续泵
            }
        }.apply {
            isDaemon = true
            name = "thumb-io"
            start()
        }
    }

    /** 格子进入可视区的兜底：内存命中走无锁 thumbFast（主线程不与后台 put 抢 LruCache 锁）；
     *  磁盘缓存经串行队列读回（主线程零文件 IO、零线程创建） */
    fun ensureThumb(row: PhotoRow) {
        if (row.preview.value != null) return
        val key = thumbKey(row)
        thumbFast[key]?.let { row.preview.value = it; return }
        val now = System.currentTimeMillis()
        thumbNoCache[key]?.let { marked ->
            if (now - marked < 15_000L) return     // 磁盘确认无缓存（loader 会补），不反复空读
            thumbNoCache.remove(key)               // 标记过期放行：清缓存/重连后自愈
        }
        if (!thumbInFlight.add(row)) return      // 已在队列，去重
        thumbIoQueue.addLast(row)
        pumpThumbIo()
    }

    private fun loadThumbFromCache(key: String): Bitmap? = try {
        val f = thumbFile(key)
        if (f.exists()) BitmapFactory.decodeFile(
            f.absolutePath,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        ) else null
    } catch (_: Throwable) {
        null
    }

    private fun saveThumbToCache(key: String, bmp: Bitmap) {
        try {
            java.io.File(thumbDir, "tmp_${System.currentTimeMillis()}.jpg").let { tmp ->
                tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                tmp.renameTo(thumbFile(key)) || tmp.delete()
            }
            // 节流：listFiles 全目录扫描较贵，连续写盘时每 25 张刷一次（设置页的显示值允许滞后）
            if (++thumbCacheWrites % 25 == 0) {
                refreshThumbCacheSize()
                enforceThumbCacheLimit()
            }
        } catch (_: Throwable) {
        }
    }
    @Volatile private var thumbCacheWrites = 0

    private fun startPreviewLoading(rows: List<PhotoRow>) {
        val myGen = ++previewGen
        val CH = 0x10000
        Thread {
            // BACKGROUND 优先级把线程放进 background cgroup → 调度到小核，
            // 解码/写盘不与 UI 线程争抢大核（Thread.priority 只调 nice 不换核）
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            for (row in rows) {
                if (!connected || previewGen != myGen) return@Thread
                // 滑动感知暂停：网格滚动进行时挂起重活（Glide 式策略），停止后自动继续
                while (gridScrolling && connected && previewGen == myGen) {
                    try { Thread.sleep(50) } catch (_: InterruptedException) { return@Thread }
                }
                // 磁盘缓存命中：直接显示，跳过网络拉取
                val cached = loadThumbFromCache(thumbKey(row))
                if (cached != null) {
                    putThumb(row, cached)
                    continue
                }
                var bmp: Bitmap? = null
                val usb = usbSession
                val first = if (usb != null) null else synchronized(camMutex) {
                    GPhoto2Bridge.nativePreviewNative(row.handle, 0, 2 * CH)
                }
                if (usb != null) {
                    // USB：无偏移读能力 → GetThumb 直接取相机内嵌缩略图
                    val t = synchronized(camMutex) { usb.getThumb(row.handle) }
                    if (t != null) {
                        bmp = BitmapFactory.decodeByteArray(t, 0, t.size)?.let { trimBlackBars(it) }
                        if (bmp == null) Log.w("UsbPtp", "thumb: ${row.name} 解码失败 ${t.size}B")
                    } else {
                        Log.w("UsbPtp", "thumb: ${row.name} GetThumb 失败")
                    }
                } else if (first != null) {
                    bmp = extractPreviewJpeg(first)
                    if (bmp == null) {
                        val range = tiffThumbRange(first)
                        Log.i("GPhoto2", "preview: ${row.name} TIFF解析=${range?.joinToString("/") ?: "null"}")
                        if (range != null) {
                            val off = range[0].toInt()
                            val len = range[1].toInt()
                            // 大小不限：小缩略图整取；大预览（如全尺寸 JPEG）先取头部 256KB 再扫内嵌小图
                            val take = if (off + len <= first.size) {
                                bmp = decodeScaled(first, off, len); null
                            } else if (len <= 2 * 1024 * 1024) {
                                longArrayOf(off.toLong(), len.toLong())
                            } else {
                                longArrayOf(off.toLong(), 256L * 1024)
                            }
                            if (bmp == null && take != null) {
                                Log.i("GPhoto2", "preview: NEF 缩略图在 ${take[0]}+${take[1]}，扩块取")
                                val aligned = (((take[1] + CH - 1) / CH * CH).toInt()).coerceAtMost(1024 * 1024)
                                val extra = synchronized(camMutex) {
                                    GPhoto2Bridge.nativePreviewNative(row.handle, take[0].toInt(), aligned)
                                }
                                if (extra != null) {
                                    bmp = extractPreviewJpeg(extra) ?: decodeScaled(extra, 0, extra.size)
                                }
                            }
                        }
                    }
                    if (bmp == null) {
                        val strip = tiffRgbStrip(first)
                        if (strip != null) {
                            val (w, h, range) = strip
                            Log.i("GPhoto2", "preview: ${row.name} NEF RGB 条带 ${w}x${h} @${range[0]}(${range[1]}B)")
                            val aligned = ((range[1] + CH - 1) / CH * CH).toInt()
                            val data = if (range[0] + range[1] <= first.size)
                                first.copyOfRange(range[0].toInt(), range[0].toInt() + range[1].toInt())
                            else synchronized(camMutex) {
                                GPhoto2Bridge.nativePreviewNative(row.handle, range[0].toInt(), aligned)
                            }
                            if (data != null) bmp = rawRgbToBitmap(data, w, h)?.let { trimBlackBars(it) }
                        }
                    }
                    if (bmp == null) {
                        // 诊断转储：只存第一个失败的 NEF 首 128KB，供离线分析
                        if (row.type.equals("NEF", true) && !nefDumped) {
                            nefDumped = true
                            try {
                                java.io.File(ctx.filesDir, "debug_nef.bin").writeBytes(first)
                                Log.i("GPhoto2", "preview: 已转储 NEF 首 128KB 供分析")
                            } catch (_: Throwable) {}
                        }
                        for (seg in 2..4) {                        // 256K/512K/768K 盲扫
                            if (!connected || previewGen != myGen) return@Thread
                            val more = synchronized(camMutex) {
                                GPhoto2Bridge.nativePreviewNative(row.handle, seg * 2 * CH, 2 * CH)
                            }
                            if (more == null) break
                            bmp = extractPreviewJpeg(more)
                            if (bmp != null) { Log.i("GPhoto2", "preview: ${row.name} 在 ${seg * 256}KB 处命中"); break }
                        }
                    }
                }
                if (bmp != null) {
                    putThumb(row, bmp)
                    saveThumbToCache(thumbKey(row), bmp)
                }
            }
            }.apply {
                isDaemon = true
                name = "preview-loader"
                start()
            }
    }

    /* ---------- 高清预览（结构解析 + 0x9431 定向读嵌入大图） ----------
     *  抓包实证（capture-hires.pcap，2026-10-02）：
     *  JPG：头 128KB 内 APP2 MPF 元数据列出各内嵌图（size+offset），取最大条目
     *       一次读出即完整 JPEG（实测 0.7~0.9MB，offset+len 恰为文件尾）。
     *  NEF：补读 0x20000..0x80000（TIFF IFD0 的 SubIFD 链所在），解析压缩=7
     *       （JPEG）SubIFD 的 StripOffsets/ByteCounts，定向读出全尺寸嵌入预览
     *       （实测 0.67~0.83MB），RAW 数据一个字节不传。
     *  这里加 LRU 缓存与 ±1 页预取，翻回秒显。 */

    /** 内存 LRU：高清位图（解码采样至最长边 ≤2048px）。
     *  ⚠️ 单张实际内存 = 2048×1536×4 ≈ **12.6MB**（ARGB_8888 解码后，不是文件大小）——
     *  旧预算 maxMemory/16（12~32MB）只装得下 1~2 张：每 put 一张就 trim 逐出旧的，
     *  **往回翻（访问最早、最久未 get 的页）必 miss** → 重新解码 50MB NEF 期间
     *  显示缩略图（糊）+ EXIF 表保留不清 → "EXIF 出来了但预览是糊"的直接来源。
     *  Android 8+ 位图像素数据在 native heap，Java 堆压力低，预算给足：
     *  64~160MB 可稳定持有 5~12 张，预览 ±1 预取窗口 + 往回翻 2~3 页全部命中。 */
    private val hiresCache = object : android.util.LruCache<Int, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 4).toInt().coerceIn(64 * 1024 * 1024, 160 * 1024 * 1024)
    ) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
        override fun entryRemoved(evicted: Boolean, key: Int?, old: Bitmap?, new: Bitmap?) {
            if (evicted) hiresTick.value++
        }
    }
    private val hiresTick = mutableStateOf(0)

    /** 加载进度：handle → (-1=结构解析期不定长；0..1=定向读取)。完成/失败即移除 */
    val hiresProgress = mutableStateMapOf<Int, Float>()
    /** 加载失败的句柄：UI 显示小字，翻回该页自动重试 */
    val hiresFailed = mutableStateMapOf<Int, Boolean>()
    /** EXIF 摘要：handle → "F2.8 · 1/500s · ISO400 · 70mm"（高清加载时解析） */
    val exifLines = mutableStateMapOf<Int, List<String>>()

    /** 网格滚动进行中（MainScreen 同步写入）：缩略图 loader 循环据此挂起重活 */
    @Volatile
    var gridScrolling = false

    private enum class HiresKind { JPG, NEF }
    private data class HiresTask(val handle: Int, val kind: HiresKind)

    /** 供 UI 读取：内部读一次 tick 建立重组依赖（缓存变化 → 重取位图） */
    fun hiresBitmap(handle: Int): Bitmap? {
        hiresTick.value
        return hiresCache.get(handle)
    }

    @Volatile private var hiresWanted: Set<Int> = emptySet()
    private val hiresQueued: MutableSet<Int> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())
    private val hiresQueue = java.util.concurrent.LinkedBlockingQueue<HiresTask>()
    @Volatile private var hiresWorkerUp = false

    /** 进入/翻动预览页时调用：targets[0] 为当前页，其余为相邻预取。
     *  重设 wanted 窗口 —— 窗口外的任务被跳过/中途丢弃（省流量，不占相机）。 */
    fun requestHires(targets: List<PhotoRow>) {
        if (!hiresOn || targets.isEmpty()) return
        val wanted = HashSet<Int>()
        val fresh = ArrayList<HiresTask>()
        for (r in targets) {
            wanted.add(r.handle)
            if (hiresCache.get(r.handle) == null && hiresQueued.add(r.handle)) {
                fresh.add(
                    HiresTask(r.handle, if (r.type.equals("JPG", true)) HiresKind.JPG else HiresKind.NEF)
                )
            }
        }
        hiresWanted = wanted
        hiresQueue.addAll(fresh)
        ensureHiresWorker()
    }

    /** 退出预览页：停止一切加载（含中途丢弃），清进度与失败标记。
     *  exifLines 不清——退出再进时位图缓存命中不重新解析，保留才能持续显示。 */
    fun clearHires() {
        hiresWanted = emptySet()
        hiresProgress.clear()
        hiresFailed.clear()
    }

    /** EXIF 参数解析（高清数据源自主文件头）：返回 [F, 快门, ISO, 焦距, (镜头)] 胶囊文本。
     *  getAttribute 返回有理数字符串（如 "28/10"），必须用 getAttributeDouble/Int 解析；
     *  Nikon 常写旧 ISO 标签 0x8827（IsoSpeedRatings）而非 0x8830，两者都查。 */
    private fun parseExifParts(jpeg: ByteArray): List<String>? = try {
        val ex = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(jpeg))
        fun d(tag: String) = ex.getAttributeDouble(tag, 0.0).takeIf { it > 0.0 }
        fun i(tag: String) = ex.getAttributeInt(tag, 0).takeIf { it > 0 }
        val f = d(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER)
        val shutter = d(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME)
        val iso = i(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
            ?: i(androidx.exifinterface.media.ExifInterface.TAG_ISO_SPEED_RATINGS)
        val fl = d(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH)
        val lens = ex.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_LENS_MODEL)
            ?.trim()?.takeIf { it.isNotEmpty() }
        val parts = buildList {
            f?.let { add("F${"%.1f".format(it)}") }
            shutter?.let { add(if (it >= 1) "${"%.1f".format(it)}s" else "1/${(1 / it).roundToInt()}s") }
            iso?.let { add("ISO$it") }
            fl?.let { add("${fl.roundToInt()}mm") }
        }
        if (parts.isEmpty()) null else {
            val out = parts.toMutableList()
            lens?.let(out::add)
            out
        }
    } catch (_: Throwable) {
        null
    }

    private fun ensureHiresWorker() {
        if (hiresWorkerUp) return
        hiresWorkerUp = true
        Thread {
            try {
                while (true) {
                    val t = hiresQueue.poll() ?: break
                    if (t.handle !in hiresWanted) { hiresQueued.remove(t.handle); continue }
                    loadHires(t)
                }
            } finally {
                hiresWorkerUp = false
                if (hiresQueue.isNotEmpty()) ensureHiresWorker()   // 退出瞬间来新任务的兜底
            }
        }.apply {
            isDaemon = true
            name = "hires-loader"
            start()
        }
    }

    private fun loadHires(task: HiresTask) {
        val h = task.handle
        try {
            if (!connected) throw IllegalStateException("未连接")
            hiresProgress[h] = -1f
            // ---------- USB 通道：优先偏移读（0x101B，与 Wi-Fi 管线同构）；失败回退整对象 ----------
            val usb = usbSession
            if (usb != null) {
                try {
                    val fileSize = sizeOf(h)
                    val (range, exifSource) = when (task.kind) {
                        HiresKind.JPG -> {
                            val head = readOff(h, 0, 0x20000)
                                ?: throw IllegalStateException("读 JPG 头失败")
                            mpfLargestRange(head, fileSize)?.let { it to head }
                                ?: throw IllegalStateException("MPF 无可用大图")
                        }
                        HiresKind.NEF -> {
                            val head = readOff(h, 0, 0x20000)
                                ?: throw IllegalStateException("读 NEF 头失败")
                            val mid = readOff(h, 0x20000, 0x60000)
                                ?: throw IllegalStateException("读 NEF 中段失败")
                            val joined = ByteArray(head.size + mid.size).also {
                                System.arraycopy(head, 0, it, 0, head.size)
                                System.arraycopy(mid, 0, it, head.size, mid.size)
                            }
                            nefPreviewRange(head, mid, fileSize)?.let { it to joined }
                                ?: throw IllegalStateException("TIFF 无嵌入预览")
                        }
                    }
                    if (h !in hiresWanted) throw IllegalStateException("已翻页")
                    val jpeg = readHiresChunks(h, range[0], range[1])
                    val bmp = decodeHires(jpeg) ?: throw IllegalStateException("解码失败")
                    hiresCache.put(h, bmp)
                    hiresTick.value++
                    hiresFailed.remove(h)
                    parseExifParts(exifSource)?.let {
                        exifLines[h] = it
                        Log.i("UsbPtp", "exif: handle=$h ${it.joinToString(" · ")}")
                    }
                    Log.i("UsbPtp", "hires(0x101B偏移读): ${task.kind} $h ✓")
                    return
                } catch (t: Throwable) {
                    Log.w("UsbPtp", "偏移读管线失败，回退整对象: $t")
                    hiresProgress[h] = -1f
                }
                // 回退：整对象下载 + 内存切片（慢但可用）
                val full = synchronized(camMutex) { usb.getObject(h) }
                    ?: throw IllegalStateException("整对象读取失败")
                if (h !in hiresWanted) throw IllegalStateException("已翻页")
                val exifSource = full.copyOf(minOf(full.size, 0x80000))
                val bmp = if (task.kind == HiresKind.JPG) {
                    decodeHires(full) ?: throw IllegalStateException("JPEG 解码失败")
                } else {
                    val probeLen = minOf(full.size, 0x80000)
                    val probe = full.copyOf(probeLen)
                    val mid = if (probeLen > 0x20000) probe.copyOfRange(0x20000, probeLen) else ByteArray(0)
                    val r = nefPreviewRange(probe.copyOf(minOf(probeLen, 0x20000)), mid, full.size.toLong())
                        ?: throw IllegalStateException("NEF 无嵌入预览")
                    val off = r[0].toInt(); val len = r[1].toInt()
                    decodeHires(full.copyOfRange(off, (off + len).coerceAtMost(full.size)))
                        ?: throw IllegalStateException("NEF 预览解码失败")
                }
                hiresCache.put(h, bmp)
                hiresTick.value++
                hiresFailed.remove(h)
                parseExifParts(exifSource)?.let { exifLines[h] = it }
                Log.i("UsbPtp", "hires(整对象): ${task.kind} $h ✓ (${full.size}B)")
                return
            }
            // ---------- Wi-Fi 通道：偏移读（gphoto2 native 自动截断越界块） ----------
            val fileSize = sizeOf(h)
            // EXIF 数据源：拍摄参数在主文件头部（大图/嵌入预览的 EXIF 只有尺寸）——
            // JPG=主 JPEG 头部（APP1 完整 EXIF）；NEF=TIFF 头部拼接（ExifInterface 支持 RAW）
            val (range, exifSource) = when (task.kind) {
                HiresKind.JPG -> {
                    val head = readOff(h, 0, 0x20000)
                        ?: throw IllegalStateException("读 JPG 头失败")
                    mpfLargestRange(head, fileSize)?.let { it to head }
                        ?: throw IllegalStateException("MPF 无可用大图 (fileSize=$fileSize)")
                }
                HiresKind.NEF -> {
                    val head = readOff(h, 0, 0x20000)
                        ?: throw IllegalStateException("读 NEF 头失败")
                    val mid = readOff(h, 0x20000, 0x60000)
                        ?: throw IllegalStateException("读 NEF 中段失败")
                    val joined = ByteArray(head.size + mid.size).also {
                        System.arraycopy(head, 0, it, 0, head.size)
                        System.arraycopy(mid, 0, it, head.size, mid.size)
                    }
                    nefPreviewRange(head, mid, fileSize)?.let { it to joined }
                        ?: throw IllegalStateException("TIFF 无嵌入预览 (fileSize=$fileSize)")
                }
            }
            if (h !in hiresWanted) throw IllegalStateException("已翻页")
            val jpeg = readHiresChunks(h, range[0], range[1])
            val full = trimJpegTail(jpeg)
            val bmp = decodeHires(full) ?: throw IllegalStateException("JPEG 解码失败")
            hiresCache.put(h, bmp)
            hiresTick.value++
            hiresFailed.remove(h)
            parseExifParts(exifSource)?.let {
                exifLines[h] = it
                Log.i("GPhoto2", "exif: handle=$h ${it.joinToString(" · ")}")
            }
            Log.i("GPhoto2", "hires: ${task.kind} handle=$h @${range[0]}+${range[1]} ✓")
        } catch (t: Throwable) {
            if (h in hiresWanted && connected) {
                hiresFailed[h] = true
                Log.w("GPhoto2", "hires: handle=$h 失败: ${t.message}")
            }
        } finally {
            hiresProgress.remove(h)
            hiresQueued.remove(h)
        }
    }

    /** 分块读取（单块 ≤0xF0000，JNI 上限 1MB）；中途翻页立即中止 */
    private fun readHiresChunks(handle: Int, off: Long, len: Long): ByteArray {
        if (off <= 0 || off > Int.MAX_VALUE - len || len <= 0 || len > 32L * 1024 * 1024)
            throw IllegalStateException("读参数异常 off=$off len=$len")
        val out = ByteArray(len.toInt())
        val cap = 0xF0000
        var done = 0L
        while (done < len) {
            if (handle !in hiresWanted) throw IllegalStateException("已翻页")
            val n = minOf(cap.toLong(), len - done).toInt()
            val b = readOff(handle, off + done, n)
            ?: throw IllegalStateException("读块失败 @${off + done}")
            if (b.isEmpty()) throw IllegalStateException("读块为空 @${off + done}")
            System.arraycopy(b, 0, out, done.toInt(), minOf(n, b.size))
            done += b.size
            hiresProgress[handle] = done.toFloat() / len
        }
        return out
    }

    /** 裁掉 JPEG 前导垃圾与尾部余量，保证 SOI..EOI 完整 */
    private fun trimJpegTail(b: ByteArray): ByteArray {
        var s = 0
        while (s + 1 < b.size && !(b[s] == 0xFF.toByte() && b[s + 1] == 0xD8.toByte())) s++
        if (s + 1 >= b.size) return b
        var e = b.size
        var i = b.size - 2
        while (i >= s) {
            if (b[i] == 0xFF.toByte() && b[i + 1] == 0xD9.toByte()) { e = i + 2; break }
            i--
        }
        return if (s == 0 && e == b.size) b else b.copyOfRange(s, e)
    }

    /** 高清解码：采样至最长边 ≤2048px（6048px 原图取 1/4 ≈ 6MB 位图，LRU 6 张可控） */
    private fun decodeHires(jpeg: ByteArray): Bitmap? = try {
        val bo = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bo)
        if (bo.outWidth <= 0 || bo.outHeight <= 0) null
        else {
            var s = 1
            while (maxOf(bo.outWidth, bo.outHeight) / s > 2048) s *= 2
            val o = BitmapFactory.Options().apply { inSampleSize = s }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, o)
        }
    } catch (_: Throwable) { null }

    private fun findBytes(data: ByteArray, pat: ByteArray): Int? {
        outer@ for (i in 0..data.size - pat.size) {
            for (j in pat.indices) if (data[i + j] != pat[j]) continue@outer
            return i
        }
        return null
    }

    /** JPG：从头 128KB 找 APP2 MPF（"MPF\0"+TIFF 头），取最大内嵌图条目。
     *  返回 longArrayOf(绝对偏移, 长度)；MPEntry 端序不确定，两种都试，
     *  须通过文件大小越界校验才算数。 */
    private fun mpfLargestRange(head: ByteArray, fileSize: Long): LongArray? {
        val mpf = findBytes(head, byteArrayOf(0x4D, 0x50, 0x46, 0x00)) ?: return null   // "MPF\0"
        var t = -1
        var i = mpf + 4
        while (i + 4 <= head.size && i < mpf + 24) {      // TIFF 头紧随其后（允许少量填充）
            val m = ((head[i].toInt() and 0xFF) shl 8) or (head[i + 1].toInt() and 0xFF)
            val ii = m == 0x4949 && head[i + 2] == 0x2A.toByte() && head[i + 3].toInt() == 0
            val mm = m == 0x4D4D && head[i + 2].toInt() == 0 && head[i + 3] == 0x2A.toByte()
            if (ii || mm) { t = i; break }
            i++
        }
        if (t < 0) return null
        val big = (head[t].toInt() and 0xFF) == 0x4D
        return mpfEntries(head, t, big, fileSize) ?: mpfEntries(head, t, !big, fileSize)
    }

    /** MPEntry 布局：属性 2B | 大小 4B | 数据偏移 4B（相对 TIFF 头） | 依赖 2×2B */
    private fun mpfEntries(head: ByteArray, t: Int, big: Boolean, fileSize: Long): LongArray? {
        fun u16(o: Int): Int = if (big)
            ((head[o].toInt() and 0xFF) shl 8) or (head[o + 1].toInt() and 0xFF)
        else (head[o].toInt() and 0xFF) or ((head[o + 1].toInt() and 0xFF) shl 8)
        fun u32(o: Int): Long = if (big)
            ((head[o].toLong() and 0xFF) shl 24) or ((head[o + 1].toLong() and 0xFF) shl 16) or
                ((head[o + 2].toLong() and 0xFF) shl 8) or (head[o + 3].toLong() and 0xFF)
        else (head[o].toLong() and 0xFF) or ((head[o + 1].toLong() and 0xFF) shl 8) or
            ((head[o + 2].toLong() and 0xFF) shl 16) or ((head[o + 3].toLong() and 0xFF) shl 24)
        if (t + 8 > head.size) return null
        val ifd0 = t + u32(t + 4).toInt()
        if (ifd0 <= 0 || ifd0 + 2 > head.size) return null
        val cnt = u16(ifd0)
        if (cnt <= 0 || cnt > 512) return null
        var nImages = 0
        var entryPos = -1
        for (k in 0 until cnt) {
            val e = ifd0 + 2 + k * 12
            if (e + 12 > head.size) return null
            when (u16(e)) {
                0xB001 -> nImages = u32(e + 8).toInt()
                0xB002 -> entryPos = t + u32(e + 8).toInt()
            }
        }
        if (entryPos <= 0 || nImages <= 0 || nImages > 16) return null
        var bestSize = 0L
        var bestOff = 0L
        for (k in 0 until nImages) {
            val p = entryPos + k * 16
            if (p + 16 > head.size) break
            // 实测布局（抓包逐字节闭合）：attr 4B | 大小 4B@+4 | 偏移 4B@+8（相对 TIFF 头）| 依赖 4B
            val sz = u32(p + 4)
            val off = u32(p + 8)
            if (off <= 0 || sz <= 0) continue               // 偏移 0 = 主图自身
            if (sz < 65536) continue                        // 过小 = 缩略图级，不参与
            val abs = t + off
            // fileSize 异常（0x9421 失败返回 -1）时退化为宽松上限，靠读后解码兜底
            val sizeOk = if (fileSize > 0) abs + sz <= fileSize else sz <= 24L * 1024 * 1024
            if (!sizeOk) continue                           // 越界 = 端序/结构不符
            if (sz > bestSize) { bestSize = sz; bestOff = abs }
        }
        return if (bestSize > 0) longArrayOf(bestOff, bestSize) else null
    }

    /** NEF：head[0,0x20000)+mid[0x20000,0x80000) 拼接 → TIFF IFD 链（IFD0 + 0x014a SubIFD），
     *  收集所有带 0x0201(JPEGInterchangeFormat)/0x0202(长度) 的 IFD（内嵌 JPEG 预览；
     *  实测压缩值=6 而非 7，RAW IFD(34713) 不带 0x0201 天然排除），
     *  取长度最小者（669KB 小预览而非 1.34MB 大预览）。 */
    private fun nefPreviewRange(head: ByteArray, mid: ByteArray, fileSize: Long): LongArray? {
        if (head.size < 16 || mid.isEmpty()) return null
        val full = ByteArray(head.size + mid.size)
        System.arraycopy(head, 0, full, 0, head.size)
        System.arraycopy(mid, 0, full, head.size, mid.size)
        val bb = java.nio.ByteBuffer.wrap(full)
        val magic = ((full[0].toInt() and 0xFF) shl 8) or (full[1].toInt() and 0xFF)
        if (magic != 0x4949 && magic != 0x4D4D) return null
        bb.order(if (magic == 0x4D4D) java.nio.ByteOrder.BIG_ENDIAN else java.nio.ByteOrder.LITTLE_ENDIAN)
        if (bb.getShort(2).toInt() != 42) return null
        var best = LongArray(0)
        var bestLen = Long.MAX_VALUE
        val visited = HashSet<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(bb.getInt(4))
        var steps = 0
        while (queue.isNotEmpty() && visited.size < 12 && steps++ < 24) {
            val base = queue.removeFirst()
            if (base <= 0 || base + 2 > full.size || !visited.add(base)) continue
            val cnt = bb.getShort(base).toInt() and 0xFFFF
            if (cnt <= 0 || base + 2 + cnt * 12 + 4 > full.size) continue
            var jOff = -1L
            var jLen = -1L
            for (k in 0 until cnt) {
                val e = base + 2 + k * 12
                val tag = bb.getShort(e).toInt() and 0xFFFF
                val typ = bb.getShort(e + 2).toInt() and 0xFFFF
                val num = bb.getInt(e + 4)
                val v: Long = when {
                    typ == 3 && num == 1 -> bb.getShort(e + 8).toLong() and 0xFFFF
                    typ == 4 && num == 1 -> bb.getInt(e + 8).toLong() and 0xFFFFFFFFL
                    else -> -1L
                }
                when (tag) {
                    0x0201 -> jOff = v
                    0x0202 -> jLen = v
                    0x014a -> when {
                        num == 1 -> { if (v > 0) queue.add(v.toInt()) }
                        num in 2..8 -> {
                            val p = bb.getInt(e + 8)
                            if (p > 0 && p + num * 4 <= full.size)
                                for (q in 0 until num) {
                                    val sv = bb.getInt(p + q * 4).toLong() and 0xFFFFFFFFL
                                    if (sv > 0) queue.add(sv.toInt())
                                }
                        }
                    }
                }
            }
            if (jOff > 0 && jLen >= 65536 && jLen < bestLen &&
                (fileSize <= 0 || jOff + jLen <= fileSize)
            ) { best = longArrayOf(jOff, jLen); bestLen = jLen }
            val nxt = bb.getInt(base + 2 + cnt * 12)
            if (nxt > 0) queue.add(nxt)
        }
        return if (best.size == 2) best else null
    }

    /** 手动断开当前连接（USB 关会话 + Wi-Fi 关传输会话 + 清通道状态），供详情浮层「断开」按钮使用。
     *  断开后保持照片网格（usbSession=null / connected=false），UI 回到双卡/选择条。 */
    fun disconnect() {
        if (usbSession != null) {
            synchronized(camMutex) { runCatching { usbSession?.close() } }
            usbSession = null
        }
        stopEventPolling()
        stopKeepAlive()
        if (connected || usbSession != null) {
            synchronized(camMutex) {
                GPhoto2Bridge.nativeTransferClose()
                runCatching { GPhoto2Bridge.nativeExit() }
            }
        }
        connected = false
        connPhase.value = "disconnected"
        connChannel.value = ""
        connDetail.value = ""
        connectedIp.value = ""
        connText.value = "已断开 · 选择连接方式"
        showConnDetail.value = false
        pendingChannel.value = null
        Log.i("GPhoto2", "手动断开连接")
        camStorage.value = null
        deletePlan.value = null
        deleteResult.value = null
        deleteResultTitle.value = null
        paramsReadOnly.value = false
        paramHint.value = null
        paramsPanelOpen.value = false
        lvThreadRunning = false
        lvActive.value = false
        lvFrame.value = null
        lvSupported.value = null
        lvText.value = ""
        movieRecording.value = false
        remoteOpen.value = false
    }

    /* ---------- 机内删除（0x100B DeleteObject）+ 存储容量（0x1005）----------
     * ZENCHE 五端均无机内删除，对标落地任务 7。双通道分发：
     *   USB   → UsbPtpSession.deleteObject / storageInfo / objectProtected
     *   Wi-Fi → nativeDeleteNative / nativeStorageInfoNative / nativeObjectProtectedNative
     * 流程：requestDelete 队列冲突前置拦截 + 写保护预检（IO 线程）→ deletePlan
     * 弹确认框 → confirmDelete 逐句柄执行 → deleteResult 单次汇总对话框。
     * 删除成功只移除本地枚举行并重建配对缓存；不碰已下载标记与本地文件。 */

    /** 存储容量（连接详情浮层展示；连接成功后刷新，删除成功后刷新） */
    val camStorage = mutableStateOf<CamStorageInfo?>(null)

    /** 删除确认计划：预检完成后由 UI 弹出确认框（total=张数，protectedCount=受保护将跳过） */
    data class DeletePlan(val total: Int, val protectedCount: Int, val rows: List<PhotoRow>)
    val deletePlan = mutableStateOf<DeletePlan?>(null)

    /** 删除结果汇总（单次 AlertDialog 展示；null=不显示）。
     *  全部删除成功时保持 null（静默不弹窗），仅受保护跳过/失败/中断才提示。 */
    val deleteResult = mutableStateOf<String?>(null)
    /** 结果框标题（与 deleteResult 成对出现）："无法删除"/"删除失败"/"删除中断"/"删除未完成" */
    val deleteResultTitle = mutableStateOf<String?>(null)
    /** 删除成功气泡事件（一次性）：Pair(序列 id, 成功张数)。UI 消费后置 null。
     *  id 递增保证「连续删 1 张再删 1 张」也能各自触发气泡（值相同 key 不同）。 */
    val deleteOkEvent = mutableStateOf<Pair<Int, Int>?>(null)
    private var deleteOkSeq = 0
    val deleteBusy = mutableStateOf(false)

    fun dismissDeleteResult() { deleteResult.value = null; deleteResultTitle.value = null }
    fun consumeDeleteOk() { deleteOkEvent.value = null }
    fun cancelDeletePlan() { deletePlan.value = null }

    /* =====================================================================
     *  拍照控制（任务 5；grilling A/B/A/A 确认 2026-10-17）
     *  - 快门：USB/Wi-Fi 同走 0x100E InitiateCapture（照片进存储卡，非 SDRAM
     *    ——拍完网格刷新即见新照片，与"传输工具"心智一致）。写卡完成检测：
     *    USB 轮询 0x90c7 GetEvent 等 0x4002 ObjectAdded（通道不支持则固定延时）。
     *  - 参数：0x1015 读 / 0x1016 写（ZENCHE 实测口径）；ISO 0x500f(u16) /
     *    光圈 0x5007(u16=f×100) / 快门回退链 {0xd100(u32=(num<<16)|den),
     *    0x500d(u32=sec×10000)}。Wi-Fi 会话可用性未知（0x1008 曾被拒）——
     *    首次读取即探针，全失败则 UI 降级隐藏参数面板。
     * ===================================================================== */
    val shutterBusy = mutableStateOf(false)
    /** 拍摄结果气泡（一次性）：Pair(序列 id, 消息)；UI 消费后置 null */
    val captureEvent = mutableStateOf<Pair<Int, String>?>(null)
    private var captureSeq = 0
    fun consumeCapture() { captureEvent.value = null }

    /* ---------- 参数面板 ---------- */
    val paramsPanelOpen = mutableStateOf(false)
    /** null=未探测；true=通道可用；false=通道不支持参数读写（UI 隐藏参数条） */
    val paramsSupported = mutableStateOf<Boolean?>(null)
    /** Wi-Fi 会话写参数被拒（0x201F AccessDenied，Z6 II 实测）→ 参数只读，步进禁用 */
    val paramsReadOnly = mutableStateOf(false)
    /** 面板底部提示行（步进失败原因/只读说明；null=不显示） */
    val paramHint = mutableStateOf<String?>(null)
    val paramIso = mutableStateOf<Int?>(null)        // 原生 ISO（64..102400）
    val paramAperture = mutableStateOf<Int?>(null)   // f×100（400 = f/4.0）
    /** 快门秒值（由 0xd100 或 0x500d 换算），null=未读到 */
    val paramShutter = mutableStateOf<Double?>(null)
    @Volatile private var shutterProp = 0xD100       // 最近命中的快门属性（写优先用它）

    private fun readPropRaw(prop: Int): ByteArray? =
        usbSession?.getDeviceProp(prop) ?: GPhoto2Bridge.nativeGetPropNative(prop)

    private fun writePropRaw(prop: Int, value: ByteArray): Int =
        usbSession?.setDeviceProp(prop, value) ?: GPhoto2Bridge.nativeSetPropNative(prop, value)

    private fun le16(d: ByteArray) = (d[0].toInt() and 0xFF) or ((d[1].toInt() and 0xFF) shl 8)
    private fun le32(d: ByteArray) = (d[0].toLong() and 0xFF) or ((d[1].toLong() and 0xFF) shl 8) or
        ((d[2].toLong() and 0xFF) shl 16) or ((d[3].toLong() and 0xFF) shl 24)
    private fun le16b(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    private fun le32b(v: Long) = byteArrayOf(
        (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
    )

    /** 读快门（ZENCHE 照片链 {0xd100, 0x500d}），返回 秒值 to 命中属性。
     *  加合理性校验：0xd100 解析出 sec ∉ (0,60] 视为编码误读（Z6 II 实测出现过
     *  num=0 / 编码方向存疑），自动落到 0x500d；原始值全量打日志供取证。 */
    private fun readShutterSec(): Pair<Double, Int>? {
        readPropRaw(0xD100)?.let { if (it.size >= 4) {
            val v = le32(it)
            val num = (v ushr 16) and 0xFFFF; val den = v and 0xFFFF
            Log.i("GPhoto2", "shutter 0xd100 raw=0x%08x num=%d den=%d".format(v, num, den))
            if (den != 0L && num != 0L) {
                val sec = num.toDouble() / den
                if (sec in 0.0001..60.0) return sec to 0xD100
            }
        } }
        readPropRaw(0x500D)?.let { if (it.size >= 4) {
            val v = le32(it)
            Log.i("GPhoto2", "shutter 0x500d raw=0x%08x (%d)".format(v, v))
            if (v in 1..600000) return (v / 10000.0) to 0x500D
        } }
        return null
    }

    /** 后台刷新三参数（连接后/面板展开时调用；单属性失败不阻断其余） */
    fun refreshCamParams() {
        if (demoMode.value) { paramsSupported.value = false; return }
        if (!connected) return
        Thread {
            var iso: Int? = null; var ap: Int? = null; var sh: Double? = null
            try {
                synchronized(camMutex) {
                    readPropRaw(0x500F)?.let { if (it.size >= 2) iso = le16(it) }
                    readPropRaw(0x5007)?.let { if (it.size >= 2) ap = le16(it) }
                    readShutterSec()?.let { sh = it.first; shutterProp = it.second }
                }
            } catch (t: Throwable) {
                Log.w("GPhoto2", "参数读取异常: ${t.message}")
            }
            val any = iso != null || ap != null || sh != null
            mainHandler.post {
                paramsSupported.value = any
                iso?.let { paramIso.value = it }
                ap?.let { paramAperture.value = it }
                sh?.let { paramShutter.value = it }
            }
            Log.i("GPhoto2", "参数读取: iso=$iso aperture=$ap shutter=$sh any=$any")
        }.start()
    }

    fun stepCamParam(which: Int, dir: Int) {
        if (demoMode.value || !connected || shutterBusy.value || paramsReadOnly.value) return
        Thread {
            try {
                synchronized(camMutex) {
                    when (which) {
                        0 -> stepIso(dir)
                        // 光圈方向取反：UI「+」= 光圈开大 = f 值变小（数值越大光圈越小）
                        1 -> stepAperture(-dir)
                        2 -> stepShutter(dir)
                    }
                }
            } catch (t: Throwable) {
                Log.w("GPhoto2", "参数步进异常: ${t.message}")
            }
        }.start()
    }

    private fun stepIso(dir: Int) {
        val cur = readPropRaw(0x500F)?.let { if (it.size >= 2) le16(it) else null } ?: return
        val i = ISO_LADDER.indexOf(cur).let { idx -> if (idx < 0) ISO_LADDER.indexOfFirst { v -> v >= cur } else idx }
        val next = (i + dir).takeIf { it in ISO_LADDER.indices } ?: return
        writeIsoValue(ISO_LADDER[next])
    }

    private fun stepAperture(dir: Int) {
        val cur = readPropRaw(0x5007)?.let { if (it.size >= 2) le16(it) else null } ?: return
        val i = AP_LADDER.indexOf(cur).let { idx -> if (idx < 0) AP_LADDER.indexOfFirst { v -> v >= cur } else idx }
        val next = (i + dir).takeIf { it in AP_LADDER.indices } ?: return
        writeApertureValue(AP_LADDER[next])
    }

    private fun stepShutter(dir: Int) {
        val cur = readShutterSec()?.first ?: return
        val next = SHUTTER_LADDER[(nearestShutterIdx(cur) + dir).takeIf { it in SHUTTER_LADDER.indices } ?: return]
        writeShutterStep(next)
    }

    /** 快门秒值 → 阶梯最近挡索引（相对误差最小；相机值不在阶梯上时也能落到正确方向） */
    private fun nearestShutterIdx(sec: Double): Int {
        var idx = 0; var best = Double.MAX_VALUE
        SHUTTER_LADDER.forEachIndexed { k, p ->
            val d = Math.abs(p.sec - sec) / Math.max(sec, 1e-9)
            if (d < best) { best = d; idx = k }
        }
        return idx
    }

    /* ---------- 挡位写值核心（步进与滚轮直接写共用）：写 + 读回 + 主线程更新 ---------- */

    private fun writeIsoValue(v: Int) {
        val rc = writePropRaw(0x500F, le16b(v))
        if (rc == 0x2001) {
            Thread.sleep(120)                       // 相机内部生效延迟
            val back = readPropRaw(0x500F)?.let { if (it.size >= 2) le16(it) else null } ?: v
            mainHandler.post { paramIso.value = back; paramHint.value = null }
            Log.i("GPhoto2", "ISO → 目标 $v 实际 $back")
        } else stepFailHint("ISO", rc)
    }

    private fun writeApertureValue(v: Int) {
        val rc = writePropRaw(0x5007, le16b(v))
        if (rc == 0x2001) {
            Thread.sleep(120)
            val back = readPropRaw(0x5007)?.let { if (it.size >= 2) le16(it) else null } ?: v
            mainHandler.post { paramAperture.value = back; paramHint.value = null }
            Log.i("GPhoto2", "光圈 → 目标 f/${v / 100.0} 实际 f/${back / 100.0}")
        } else stepFailHint("光圈", rc)
    }

    private fun writeShutterStep(next: ShutterStep) {
        // 写：d100 = (num<<16)|den（小数挡分母 10，与相机上报风格一致）；
        // 非 OK 且非传输失败 → 回退 0x500d = sec×10000（1.3″ 档 d100 编码被拒时兜底）
        val secMs = Math.round(next.sec * 10000.0)
        var rc = writePropRaw(0xD100, le32b(((next.num.toLong() and 0xFFFF) shl 16) or (next.den.toLong() and 0xFFFF)))
        var used = 0xD100
        if (rc != 0x2001 && rc != -1 && rc != -2) {
            rc = writePropRaw(0x500D, le32b(secMs.toLong()))
            used = 0x500D
        }
        if (rc == 0x2001) {
            Thread.sleep(150)
            val back = readShutterSec()            // 读回相机实际值（编码方向取证以它为准）
            val shown = back?.first ?: next.sec
            if (back != null) shutterProp = back.second
            mainHandler.post { paramShutter.value = shown; paramHint.value = null }
            Log.i("GPhoto2", "快门 → 目标 ${next.label} 实际 ${shown}s (写 0x%04x)".format(used))
        } else stepFailHint("快门", rc)
    }

    /* ---------- 滚轮直接写（参数条/滚轮弹窗用）----------
     * UI 序（index 0 = 最亮端，上滑=更亮=index 减）：
     *   ISO = 阶梯降序、快门 = 秒升序、光圈 = f 值升序（f 小=开大=亮）。
     * 光圈 ± 步进的 dir 取反（#30）只影响 stepCamParam；滚轮按曝光序直接定挡。 */

    /** 滚轮候选值标签（UI 序） */
    fun paramWheelLabels(which: Int): List<String> = when (which) {
        0 -> ISO_LADDER.reversed().map { it.toString() }
        1 -> AP_LADDER.map { if (it % 100 == 0) "f/${it / 100}" else "f/${it / 100.0}" }
        else -> SHUTTER_LADDER.reversed().map { it.label }
    }

    /** 当前值在 UI 序中的索引；-1 = 未知（相机值不在阶梯上） */
    fun paramWheelIndex(which: Int): Int = when (which) {
        0 -> paramIso.value?.let { v -> ISO_LADDER.indexOf(v) }?.takeIf { it >= 0 }?.let { ISO_LADDER.size - 1 - it } ?: -1
        1 -> paramAperture.value?.let { v -> AP_LADDER.indexOf(v).takeIf { it >= 0 } } ?: -1
        else -> paramShutter.value?.let { nearestShutterIdx(it) }?.let { SHUTTER_LADDER.size - 1 - it } ?: -1
    }

    /** 滚轮选定后直接写目标挡（松手/确认时调用） */
    fun applyParamWheel(which: Int, uiIdx: Int) {
        if (demoMode.value || !connected || paramsReadOnly.value) return
        if (uiIdx !in paramWheelLabels(which).indices) return
        Thread {
            try {
                synchronized(camMutex) {
                    when (which) {
                        0 -> ISO_LADDER.getOrNull(ISO_LADDER.size - 1 - uiIdx)?.let { writeIsoValue(it) }
                        1 -> AP_LADDER.getOrNull(uiIdx)?.let { writeApertureValue(it) }
                        else -> SHUTTER_LADDER.getOrNull(SHUTTER_LADDER.size - 1 - uiIdx)?.let { writeShutterStep(it) }
                    }
                }
            } catch (t: Throwable) {
                Log.w("GPhoto2", "滚轮写值异常: ${t.message}")
            }
        }.start()
    }

    /** 步进失败 → 面板底部提示（0x201F 额外置只读态，禁用全部步进） */
    private fun stepFailHint(name: String, rc: Int) {
        Log.w("GPhoto2", "$name 设置失败 rc=0x${(rc and 0xFFFF).toString(16)}")
        val hint = when {
            rc == 0x201F -> {
                paramsReadOnly.value = true
                "Wi-Fi 连接下相机拒绝写参数（AccessDenied），请用 USB 连接调节"
            }
            rc == 0x2019 -> "相机正忙，请稍后再试"
            isPropCompatError(rc) -> "当前拍摄模式不允许调$name（请检查模式转盘/镜头光圈环）"
            else -> "$name 设置失败 (0x${(rc and 0xFFFF).toString(16)})"
        }
        mainHandler.post { paramHint.value = hint }
    }

    /** 属性不兼容类错误（ZENCHE 同款集合）：快门回退链才允许换下一个属性 */
    private fun isPropCompatError(rc: Int) = rc in intArrayOf(0x2005, 0x200A, 0x200F, 0x201C, 0x201D)

    /** 秒值 → 机身原标法（最近档匹配；相机值不在阶梯上时退回通用格式化） */
    fun shutterLabel(sec: Double): String {
        var best: ShutterStep? = null; var bd = Double.MAX_VALUE
        for (s in SHUTTER_LADDER) {
            val d = Math.abs(s.sec - sec) / sec
            if (d < bd) { bd = d; best = s }
        }
        if (best != null && bd < 0.05) return best.label
        return if (sec >= 1.0) "%.1f″".format(sec) else "1/${Math.round(1.0 / sec)}"
    }

    /* ---------- 快门触发 ---------- */
    /** 拍摄模式：true=自动对焦后拍摄（0x100E，实测相机先 AF 合焦后释放）；
     *  false=直接拍摄（0x9207 [0xFFFFFFFF,0]，不对焦立即拍，照片仍进卡）。 */
    val shootAf = mutableStateOf(true)

    /** 拍摄后写卡等待 + 列表刷新专用单线程（连拍多张排队执行，不阻塞快门） */
    private val refreshExec = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun capturePhoto() {
        if (shutterBusy.value) return
        if (demoMode.value) {
            captureEvent.value = Pair(++captureSeq, "演示模式：快门已模拟")
            return
        }
        if (!connected) return
        shutterBusy.value = true
        val af = shootAf.value
        Thread {
            var ok = false
            var msg: String
            var code = 0
            fun fire(): Int = if (af) {
                usbSession?.initiateCapture() ?: GPhoto2Bridge.nativeCaptureNative()
            } else {
                usbSession?.captureDirect() ?: GPhoto2Bridge.nativeCapture2Native(-1, 0)
            }
            try {
                code = synchronized(camMutex) { fire() }
                // 取景中失败：暂停拉帧重试一次（部分固件取景态拒拍；ZENCHE 同款拆流思路的轻量版）
                if (code != 0x2001 && lvThreadRunning) {
                    lvPause = true
                    try {
                        Thread.sleep(300)
                        code = synchronized(camMutex) { fire() }
                    } finally { lvPause = false }
                    Log.i("GPhoto2", "取景态拒拍 → 暂停取景重试: code=0x${(code and 0xFFFF).toString(16)}")
                }
                if (code == 0x2001) {
                    ok = true
                    msg = if (captureRefreshOn.value) "拍摄成功 · 已存入存储卡，正在刷新列表…"
                          else "拍摄成功 · 已存入存储卡"
                } else {
                    msg = when {
                        code == 0x2019 -> "相机正忙，请稍后再拍"
                        code == 0x2005 -> "当前模式不支持遥控快门"
                        // 0x2002=GeneralError：AF 模式下实测为对焦未合焦被相机拒绝
                        code == 0x2002 && af -> "自动对焦未合焦，拍摄取消（可切直拍模式）"
                        code == 0x201D -> "直拍指令被相机拒绝 (0x201D)"
                        code == -1 || code == -2 -> "拍摄失败：连接已中断"
                        else -> "拍摄失败 (0x${(code and 0xFFFF).toString(16)})"
                    }
                }
            } catch (t: Throwable) {
                msg = "拍摄失败：${t.message ?: "传输异常"}"
            }
            mainHandler.post { shutterBusy.value = false }   // 快门指令返回即解锁：连拍不等写卡/刷新
            if (!ok || captureBubbleOn.value) {
                val ev = Pair(++captureSeq, msg)
                mainHandler.post { captureEvent.value = ev }
            }
            Log.i("GPhoto2", "遥控拍摄: ok=$ok code=0x${(code and 0xFFFF).toString(16)}")
            if (ok) {
                // 后台单线程排队：先试增量（只拉新对象），失败/通道不可用再回退全量枚举。
                // Wi-Fi 事件由原生缓冲统一汇总（0x941c 保活与 0x90c7 谁消费都入缓冲），无需挂起保活。
                refreshExec.execute {
                    try {
                        // Wi-Fi 事件的 ObjectAdded 在拍进卡模式下多为 SDRAM 临时 id
                        // （0x1014 查无此对象），不可作差集依据 → 统一走 0x9434 全表差集：
                        // 表由相机在写卡完成时维护，差集即真新照片，天然等写完。
                        val handles: List<Int>? = if (usbSession != null) waitForCaptureHandles() else wifiDiffRefresh()
                        Log.i("GPhoto2", "拍后刷新: handles=${handles?.size ?: "null(通道不可用)"}")
                        if (handles.isNullOrEmpty() || !appendNewObjects(handles)) {
                            Log.i("GPhoto2", "增量不可用 → 回退全量枚举")
                            Thread.sleep(if (usbSession != null) 4000 else 3000)
                            if (captureRefreshOn.value) {
                                try { listFiles() } catch (t: Throwable) {
                                    Log.w("GPhoto2", "拍摄后刷新失败: ${t.message}")
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }
        }.start()
    }

    /** 轮询 GetEvent 收集新对象句柄（0x4002 ObjectAdded / 0xC101 尼康 SDRAM）。
     *  ⚠️ 同一对象可能同时发 0x4002 与 0xC101 两条事件 → LinkedHashSet 去重
     *  （曾因重复句柄进列表触发 LazyGrid key 重复闪退）。命中后留 1.2s 静默窗
     *  收集连拍余量；空列表 = 超时或通道不支持（调用方回退全量）。 */
    private fun waitForCaptureHandles(): List<Int> {
        val deadline = System.currentTimeMillis() + 15_000
        var lastHit = 0L
        val got = LinkedHashSet<Int>()
        while (System.currentTimeMillis() < deadline) {
            if (lastHit > 0 && System.currentTimeMillis() - lastHit > 1200) break
            val evs = synchronized(camMutex) { usbSession?.getEvents() } ?: return got.toList()
            var hit = false
            for ((c, p) in evs) if (c == 0x4002 || c == 0xC101) { got.add(p); hit = true }
            if (hit) { lastHit = System.currentTimeMillis(); continue }
            Thread.sleep(400)
        }
        return got.toList()
    }

    /** Wi-Fi 拍后差集刷新：0x9434 全表 ∖ rowByHandle = 新照片句柄（写卡完成才入表）。
     *  返回 null = 表拉取失败（回退全量）；空列表 = 窗口内没等到新照片（回退全量兜底）。 */
    private fun wifiDiffRefresh(): List<Int>? {
        try { Thread.sleep(1500) } catch (_: InterruptedException) { return null }   // 写卡起步
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline && connected) {
            val table = synchronized(camMutex) { GPhoto2Bridge.nativeStampTableNative() }
                ?: return null
            val fresh = ArrayList<Int>()
            for (tok in table.split('|')) {
                if (tok.isBlank()) continue
                val h = tok.substringBefore(':').trim().toIntOrNull() ?: continue
                if (!rowByHandle.containsKey(h)) fresh.add(h)
            }
            if (fresh.isNotEmpty()) return fresh
            try { Thread.sleep(1200) } catch (_: InterruptedException) { return null }
        }
        return emptyList()
    }

    /** 拍摄后增量刷新：ObjectAdded 句柄 → 单对象信息行 → 追加进列表（不全量枚举）。
     *  行格式与 listFilesInner 解析同源（"h:stamp:type[:fname]"）；新行内存重排序，
     *  与全量枚举的排序规则一致。返回 false = 拉取失败（调用方回退全量）。 */
    private fun appendNewObjects(handles: List<Int>): Boolean = synchronized(camMutex) {
        val lines = ArrayList<String>(handles.size)
        val seen = HashSet<Int>()                              // 局部去重：handles 本身可能含重复事件
        for (h in handles) {
            if (!seen.add(h)) continue
            if (rowByHandle.containsKey(h)) continue           // 重复事件/上次已入列
            val line = usbSession?.objectInfoLine(h) ?: GPhoto2Bridge.nativeObjectInfoNative(h)
            if (line == null) { Log.w("GPhoto2", "增量 info($h) 失败 → 回退全量"); return false }
            lines.add(line)
        }
        if (lines.isEmpty()) return true
        val parsed = lines.mapNotNull { row ->
            val p = row.split(':', limit = 4)
            if (p.size < 3) return@mapNotNull null
            val handle = p[0].toIntOrNull() ?: return@mapNotNull null
            if (rowByHandle.containsKey(handle)) return@mapNotNull null   // 双保险去重
            val stamp = p[1]
            val type = p[2].uppercase()
            val fileNo = p.getOrNull(3)?.substringBeforeLast('.')
            val name = "IMG_${stamp}_${p[0].takeLast(4).padStart(4, '0')}.${type.lowercase()}"
            PhotoRow(name, handle, type, stamp, fileNo)
        }
        if (parsed.isEmpty()) return true
        // 原子快照提交：addAll+sort 是多步结构写，与主线程并发修改交错时
        // SnapshotStateList 可能出现重复元素（LazyGrid key 重复闪退的根源）
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            photoRows.addAll(parsed)
            photoRows.sortWith(
                compareByDescending<PhotoRow> { it.stamp }
                    .thenByDescending { it.captureSeq() }
                    .thenBy { if (it.type.equals("NEF", true)) 1 else 0 }
            )
        }
        dedupePhotoRows()
        rebuildRowIndex()
        refreshPairRows()
        startPreviewLoading(parsed)
        startProtectionScan(parsed)
        if (chunkedLoad.value) displayLimit.value += parsed.size
        Log.i("GPhoto2", "增量刷新 +${parsed.size}（总 ${photoRows.size}）")
        true
    }

    /** 防御性全表句柄去重：任何写入路径的并发交错都可能在 photoRows 留下重复
     *  句柄 → LazyGrid key 重复直接闪退。发现重复时保留首条、剔除其余并落日志。 */
    private fun dedupePhotoRows() {
        val dedup = photoRows.distinctBy { it.handle }
        if (dedup.size == photoRows.size) return
        val dupKeys = photoRows.groupBy { it.handle }.filter { it.value.size > 1 }.keys
        Log.w("GPhoto2", "photoRows 重复句柄 ${photoRows.size - dedup.size} 条（$dupKeys）→ 去重")
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            photoRows.clear()
            photoRows.addAll(dedup)
        }
    }

    private val ISO_LADDER = intArrayOf(
        64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000,
        2500, 3200, 4000, 5000, 6400, 8000, 10000, 12800, 16000, 20000, 25600, 32000,
        40000, 51200, 64000, 102400
    )
    private val AP_LADDER = intArrayOf(   // f×100，1/3 档（f/1.2 … f/32）
        120, 140, 160, 180, 200, 220, 250, 280, 320, 350, 400, 450, 500, 560, 630, 710,
        800, 900, 1000, 1100, 1300, 1400, 1600, 1800, 2000, 2200, 2500, 2800, 3200
    )
    /** 快门阶梯：Z6 II 机身实测挡位（2026-10-17 用户逐档确认）。
     *  长曝段无 3/3.2s（4″→2.5″ 直达）；亚秒段全为 1/x 标法（1/1.3、1/1.6、
     *  1/2.5、1/3），非 0.8/0.6/0.4/0.3。label = 机身原标法。
     *  ⚠️ 0xd100 编码实证：相机把 1.6″ 报告为 0x0010000a=16/10——**小数挡
     *  分母固定 10**（1.3″=13/10、2.5″=25/10、1/1.3=10/13），不约分。 */
    private class ShutterStep(val num: Int, val den: Int, val label: String) {
        val sec: Double get() = num.toDouble() / den
    }
    private val SHUTTER_LADDER = listOf(
        ShutterStep(30, 1, "30″"), ShutterStep(25, 1, "25″"), ShutterStep(20, 1, "20″"),
        ShutterStep(15, 1, "15″"), ShutterStep(13, 1, "13″"), ShutterStep(10, 1, "10″"),
        ShutterStep(8, 1, "8″"), ShutterStep(6, 1, "6″"), ShutterStep(5, 1, "5″"),
        ShutterStep(4, 1, "4″"), ShutterStep(25, 10, "2.5″"), ShutterStep(2, 1, "2″"),
        ShutterStep(16, 10, "1.6″"), ShutterStep(13, 10, "1.3″"), ShutterStep(1, 1, "1″"),
        ShutterStep(10, 13, "1/1.3"), ShutterStep(10, 16, "1/1.6"), ShutterStep(1, 2, "1/2"),
        ShutterStep(10, 25, "1/2.5"), ShutterStep(1, 3, "1/3"), ShutterStep(1, 4, "1/4"),
        ShutterStep(1, 5, "1/5"), ShutterStep(1, 6, "1/6"), ShutterStep(1, 8, "1/8"),
        ShutterStep(1, 10, "1/10"), ShutterStep(1, 13, "1/13"), ShutterStep(1, 15, "1/15"),
        ShutterStep(1, 20, "1/20"), ShutterStep(1, 25, "1/25"), ShutterStep(1, 30, "1/30"),
        ShutterStep(1, 40, "1/40"), ShutterStep(1, 50, "1/50"), ShutterStep(1, 60, "1/60"),
        ShutterStep(1, 80, "1/80"), ShutterStep(1, 100, "1/100"), ShutterStep(1, 125, "1/125"),
        ShutterStep(1, 160, "1/160"), ShutterStep(1, 200, "1/200"), ShutterStep(1, 250, "1/250"),
        ShutterStep(1, 320, "1/320"), ShutterStep(1, 400, "1/400"), ShutterStep(1, 500, "1/500"),
        ShutterStep(1, 640, "1/640"), ShutterStep(1, 800, "1/800"), ShutterStep(1, 1000, "1/1000"),
        ShutterStep(1, 1250, "1/1250"), ShutterStep(1, 1600, "1/1600"), ShutterStep(1, 2000, "1/2000"),
        ShutterStep(1, 2500, "1/2500"), ShutterStep(1, 3200, "1/3200"), ShutterStep(1, 4000, "1/4000"),
        ShutterStep(1, 5000, "1/5000"), ShutterStep(1, 6400, "1/6400"), ShutterStep(1, 8000, "1/8000")
    )

    /* =====================================================================
     *  实时取景 / 遥控拍摄（任务 6；grilling A/A/A + B门移出，2026-10-17）
     *  取景流：0x9201 开启 → 循环 0x9203 拉帧（只保留最新帧）→ 0x9202 关闭。
     *  Wi-Fi 0x9203 可用性未实证 → 首启探针，被拒则遥控页降级提示。
     *  独占操作经 withLvPaused 暂停拉帧（相机保持取景态，ZENCHE 同款护栏；
     *  camMutex 保证事务级互斥，暂停只为抢带宽）。录像 0x920a/b 文件留卡。
     * ===================================================================== */
    val remoteOpen = mutableStateOf(false)
    val lvSupported = mutableStateOf<Boolean?>(null)   // null=探测中；false=通道不支持（页内降级提示）
    val lvActive = mutableStateOf(false)
    val lvFrame = mutableStateOf<android.graphics.Bitmap?>(null)
    val lvText = mutableStateOf("")
    val movieRecording = mutableStateOf(false)
    val focusStep = mutableStateOf(2)                  // 对焦步长档 1/2/3 → 128/512/1024
    @Volatile private var lvPause = false
    @Volatile private var lvThreadRunning = false
    private var lvThread: Thread? = null

    /* ---------- 拍摄设置（设置页「拍摄」分区） ---------- */
    val captureRefreshOn = mutableStateOf(prefs.getBoolean("set_capture_refresh", true))
    val lvEcoOn = mutableStateOf(prefs.getBoolean("set_lv_eco", false))
    val captureBubbleOn = mutableStateOf(prefs.getBoolean("set_capture_bubble", true))
    fun setCaptureRefresh(on: Boolean) { captureRefreshOn.value = on; prefs.edit().putBoolean("set_capture_refresh", on).apply() }
    fun setLvEco(on: Boolean) { lvEcoOn.value = on; prefs.edit().putBoolean("set_lv_eco", on).apply() }
    fun setCaptureBubble(on: Boolean) { captureBubbleOn.value = on; prefs.edit().putBoolean("set_capture_bubble", on).apply() }

    /* ---------- 悬浮快门球（体验优化：拖动吸边/模式胶囊/隐藏开关/首次提示） ---------- */
    val shutterBallHidden = mutableStateOf(prefs.getBoolean("set_shutter_ball_hide", false))
    fun setShutterBallHidden(on: Boolean) {
        shutterBallHidden.value = on
        prefs.edit().putBoolean("set_shutter_ball_hide", on).apply()
    }
    /** 首次长按提示：显示一次后记住 */
    val ballHintShown = mutableStateOf(prefs.getBoolean("set_ball_hint_shown", false))
    fun markBallHintShown() { ballHintShown.value = true; prefs.edit().putBoolean("set_ball_hint_shown", true).apply() }
    /** 球位置持久化：距最近边的比例（0~1，边内坐标）+ 所在侧 */
    var ballSide = prefs.getInt("ball_side", 1)                        // 0=左 1=右
    var ballYFrac = prefs.getFloat("ball_yfrac", 0.72f)                 // 球中心 y / 可用高度
    fun saveBallPos(side: Int, yFrac: Float) {
        ballSide = side; ballYFrac = yFrac
        prefs.edit().putInt("ball_side", side).putFloat("ball_yfrac", yFrac).apply()
    }

    /** 独占操作期间暂停取景拉帧（相机保持取景态；try/finally 保证恢复） */
    private fun <T> withLvPaused(block: () -> T): T {
        val was = lvThreadRunning
        if (was) lvPause = true
        try { return block() } finally { if (was) lvPause = false }
    }

    fun openRemote() {
        remoteOpen.value = true
        if (connected) ensureLiveView()
    }

    fun closeRemote() {
        remoteOpen.value = false
        lvThreadRunning = false    // 循环自行退出并 EndLiveView
        lvThread = null
    }

    private fun ensureLiveView() {
        if (demoMode.value) {
            lvSupported.value = false
            lvText.value = "演示模式不支持实时取景"
            return
        }
        if (lvThreadRunning) return
        lvThreadRunning = true
        val th = Thread {
            var opened = false
            try {
                synchronized(camMutex) {
                    opened = (usbSession?.startLiveView()
                        ?: GPhoto2Bridge.nativeStartLiveViewNative()) == 0x2001
                }
            } catch (t: Throwable) { Log.w("GPhoto2", "StartLiveView 异常: ${t.message}") }
            mainHandler.post {
                lvSupported.value = opened
                lvActive.value = opened
                lvText.value = if (opened) "正在连接取景…" else "当前连接通道不支持实时取景"
            }
            Log.i("GPhoto2", "StartLiveView opened=$opened")
            if (!opened) { lvThreadRunning = false; return@Thread }
            mainHandler.post { lvFrame.value = null }
            var failStreak = 0
            var decFailStreak = 0
            while (lvThreadRunning && connected) {
                if (lvPause) { try { Thread.sleep(150) } catch (_: InterruptedException) {}; continue }
                val jpeg = try {
                    synchronized(camMutex) {
                        usbSession?.getLiveViewFrame() ?: wifiLvFrame()
                    }
                } catch (t: Throwable) { null }
                if (jpeg != null) {
                    failStreak = 0
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                    if (bmp != null) {
                        decFailStreak = 0
                        mainHandler.post { if (lvThreadRunning) lvFrame.value = bmp }
                    } else {
                        // 截出了 JPEG 但解码失败：数据半截/格式异常，计入连败防止无限刷
                        if (++decFailStreak >= 40) { Log.w("GPhoto2", "LV 解码连败 40 帧退出"); break }
                        if (decFailStreak % 10 == 0) Log.w("GPhoto2", "LV 解码失败 jpegLen=${jpeg.size}")
                        try { Thread.sleep(200) } catch (_: InterruptedException) {}
                    }
                    if (lvEcoOn.value) try { Thread.sleep(100) } catch (_: InterruptedException) {}
                } else {
                    if (++failStreak > 40) break   // 连续丢帧 ~10s：通道异常，退出收尾
                    try { Thread.sleep(200) } catch (_: InterruptedException) {}
                }
            }
            try {
                synchronized(camMutex) {
                    usbSession?.endLiveView() ?: GPhoto2Bridge.nativeEndLiveViewNative()
                }
            } catch (_: Throwable) {}
            lvThreadRunning = false
            mainHandler.post { lvActive.value = false; lvFrame.value = null }
        }
        lvThread = th
        th.start()
    }

    /** Wi-Fi 取景帧：nativeLiveViewFrameNative 返回原始字节，须先截 JPEG（FFD8…FFD9）。
     *  USB 路径已在 UsbPtpSession.getLiveViewFrame 内截取；此前 Wi-Fi 漏了这一步，
     *  原始数据（可能带封装头/尾）直接解码恒败 → 黑屏只有转圈占位。
     *  截取失败时打前 16 字节 hex 供下一轮定位数据格式。 */
    private fun wifiLvFrame(): ByteArray? {
        val raw = GPhoto2Bridge.nativeLiveViewFrameNative() ?: return null
        val jpeg = extractJpegFrame(raw)
        if (jpeg == null) {
            val head = raw.take(16).joinToString("") { "%02x".format(it) }
            Log.w("GPhoto2", "Wi-Fi LV 帧截取失败 len=${raw.size} head=$head")
        }
        return jpeg
    }

    /** JPEG 流截取（与 UsbPtpSession.extractJpeg 同款）：向前找首个 FFD8（SOI），
     *  从尾向前找最后一个 FFD9（EOI，避免误中缩略图内嵌标记）。 */
    private fun extractJpegFrame(d: ByteArray): ByteArray? {
        var start = -1
        val n = d.size
        for (i in 0 until n - 1) {
            if (d[i] == 0xFF.toByte() && d[i + 1] == 0xD8.toByte()) { start = i; break }
        }
        if (start < 0) return null
        for (i in n - 2 downTo start + 1) {
            if (d[i] == 0xFF.toByte() && d[i + 1] == 0xD9.toByte()) {
                return d.copyOfRange(start, i + 2)
            }
        }
        return null
    }

    /** 单次 AF（0x90c1，仅取景态） */
    fun autoFocusDrive() {
        if (!lvActive.value || demoMode.value || !connected) return
        Thread {
            val rc = synchronized(camMutex) {
                usbSession?.autoFocusDrive() ?: GPhoto2Bridge.nativeAfDriveNative()
            }
            Log.i("GPhoto2", "AF rc=0x${(rc and 0xFFFF).toString(16)}")
            if (rc != 0x2001) mainHandler.post {
                lvText.value = if (rc == 0x2019) "相机正忙，AF 稍后再试" else "AF 失败 (0x${(rc and 0xFFFF).toString(16)})"
            }
        }.start()
    }

    /** 手动对焦驱动（0x9204）：dir 1=近 / 2=远，步长档 focusStep 1..3 */
    fun stepFocus(dir: Int) {
        if (!lvActive.value || demoMode.value || !connected) return
        Thread {
            val drive = intArrayOf(128, 512, 1024)[focusStep.value.coerceIn(1, 3) - 1]
            val rc = synchronized(camMutex) {
                usbSession?.manualFocusDrive(dir, focusStep.value)
                    ?: GPhoto2Bridge.nativeFocusDriveNative(dir, drive)
            }
            Log.i("GPhoto2", "MF dir=$dir drive=$drive rc=0x${(rc and 0xFFFF).toString(16)}")
        }.start()
    }

    /** 录像开关（0x920a/b）。文件保存在相机卡内，App 不落盘。
     *  VIDEO-SEALED（2026-10-17 用户要求暂撤：协议链路可用，UI 入口已撤，
     *  加回时恢复 MainActivity 底条 VIDEO-SEALED 块即可）。 */
    fun toggleMovie() {
        if (demoMode.value || !connected) return
        Thread {
            synchronized(camMutex) {
                if (!movieRecording.value) {
                    val rc = usbSession?.startMovie() ?: GPhoto2Bridge.nativeStartMovieNative()
                    if (rc == 0x2001) mainHandler.post {
                        movieRecording.value = true
                        if (captureBubbleOn.value) captureEvent.value = Pair(++captureSeq, "已开始录像 · 影像保存在相机卡内")
                    } else mainHandler.post { lvText.value = "录像启动失败 (0x${(rc and 0xFFFF).toString(16)})" }
                } else {
                    val rc = usbSession?.endMovie() ?: GPhoto2Bridge.nativeEndMovieNative()
                    if (rc == 0x2001) mainHandler.post {
                        movieRecording.value = false
                        if (captureBubbleOn.value) captureEvent.value = Pair(++captureSeq, "录像已结束 · 影像保存在相机卡内")
                    } else mainHandler.post { lvText.value = "录像结束失败 (0x${(rc and 0xFFFF).toString(16)})" }
                }
            }
        }.start()
    }

    /** 后台刷新存储容量（双通道分发；失败静默留空，不影响主流程）
     *  演示模式：假容量（64GB 卡），剩余 = 基数 43GB + 已删张数 × 24MB（单张口径与假下载一致），
     *  删除成功后 confirmDelete 的 refreshCamStorage 会同步回升，可离线验证容量展示。 */
    private var demoRowTotal = 0
    fun refreshCamStorage() {
        if (demoMode.value) {
            val per = 24L * 1024 * 1024
            val free = 43L * 1024 * 1024 * 1024 +
                (demoRowTotal - photoRows.size).coerceAtLeast(0) * per
            camStorage.value = CamStorageInfo(
                64L * 1024 * 1024 * 1024, free, free / per, "演示"
            )
            return
        }
        if (!connected) { camStorage.value = null; return }
        Thread {
            val info = try {
                synchronized(camMutex) {
                    usbSession?.storageInfo()
                        ?: GPhoto2Bridge.nativeStorageInfoNative()?.let { parseCamStorage(it) }
                }
            } catch (t: Throwable) {
                Log.w("GPhoto2", "存储容量读取失败", t)
                null
            }
            camStorage.value = info
        }.start()
    }

    private fun parseCamStorage(s: String): CamStorageInfo? {
        val p = s.split('|')
        if (p.size < 3) return null
        return CamStorageInfo(
            p[0].toLongOrNull() ?: return null,
            p[1].toLongOrNull() ?: return null,
            p[2].toLongOrNull() ?: -1L,
            p.getOrNull(3)?.trim() ?: ""
        )
    }

    /* ---------- 保护状态采集（GetObjectInfo.ProtectionStatus）----------
     * 枚举快路径（0x9434 表 + 句柄位段）不含保护位；删除预检按需查询只覆盖选中项。
     * 为让网格/预览能"事前"显示保护徽标、删除前给出预警，枚举完成后启用后台
     * 渐进扫描：新→旧逐句柄查询，命中即回写 row.isProtected（主线程）。
     * 查询连续失败（通道不支持 0x1008 / 管道损坏）达阈值即静默放弃 —— 保护知识
     * 另有删除预检 + 0x2009 学习两条兜底路径，不影响主流程。
     * 代价：886 句柄 × 一次 0x1008 小事务，与缩略图加载共享 camMutex 交替执行。 */
    private val protScanGen = java.util.concurrent.atomic.AtomicInteger(0)

    /** 保护扫描状态（UI 可见诊断）：idle 未开始 / running 扫描中 / done 完成 / unsupported 通道不支持 */
    val protScanState = mutableStateOf("idle")
    /** 受保护张数（随渐进扫描增长）；derivedStateOf 保证网格徽标与计数同步刷新 */
    val protectedCount: State<Int> = derivedStateOf { photoRows.count { it.isProtected.value } }

    fun startProtectionScan(rows: List<PhotoRow>) {
        if (demoMode.value || !connected) return
        if (usbSession == null) {
            // Wi-Fi 会话 0x1008 已实测被拒（§6.4）：扫描必以 6 次失败告终，纯浪费；
            // 且曾在此路径触发 native 空指针崩溃 → 直接跳过，保护知识走删除预检 + 0x2009 学习兜底。
            protScanState.value = "unsupported"
            return
        }
        val gen = protScanGen.incrementAndGet()          // 重新枚举/断开 → 旧扫描自行退出
        protScanState.value = "running"
        val ch = if (usbSession != null) "usb" else "wifi"
        Log.i("GPhoto2", "保护扫描开始：${rows.size} 张（通道=$ch）")
        Thread {
            var failStreak = 0
            var hit = 0
            var miss = 0
            for (r in rows) {
                if (gen != protScanGen.get() || !connected) {
                    protScanState.value = "idle"        // 断开/重枚举：不留"扫描中"僵尸态
                    return@Thread
                }
                val p = try {
                    synchronized(camMutex) {
                        usbSession?.objectProtected(r.handle)
                            ?: GPhoto2Bridge.nativeObjectProtectedNative(r.handle)
                                .let { c -> if (c < 0) null else c == 1 }
                    }
                } catch (t: Throwable) { null }
                if (p == null) {
                    miss++
                    if (++failStreak >= 6) {
                        // 通道不配合（0x1008 不支持/管道坏）→ 如实标记，别卡在"扫描中"
                        protScanState.value = "unsupported"
                        Log.w("GPhoto2", "保护扫描放弃：连续 6 次查询失败（通道=$ch，多为 0x1008 不支持）")
                        return@Thread
                    }
                    continue
                }
                failStreak = 0
                if (p) {
                    hit++
                    mainHandler.post {
                        if (gen == protScanGen.get()) r.isProtected.value = true
                    }
                }
            }
            if (gen == protScanGen.get()) protScanState.value = if (miss > 0) "unsupported" else "done"
            Log.i("GPhoto2", "保护扫描结束：命中=$hit 查询失败=$miss 状态=${protScanState.value}")
        }.start()
    }

    /** 选中项收集（与 downloadSelected 同口径：合并模式按选中对展开 JPG/NEF 两张） */
    private fun collectSelectedRows(): List<PhotoRow> {
        val rows = mutableListOf<PhotoRow>()
        if (mergePairs.value) {
            visiblePairs.filter { it.key in pairSelection.value }.forEach { p ->
                p.jpg?.let { rows.add(it) }
                p.nef?.let { rows.add(it) }
            }
        } else {
            photoRows.filter { it.selected.value }.forEach { rows.add(it) }
        }
        return rows
    }

    /** 删除所选 → 预检（IO 线程）→ deletePlan 弹确认框。
     *  rows=null 取当前多选；非 null = 预览页单张删除入口。
     *  队列冲突：有选中正在下载/排队即整体拦截（删了正在读的句柄会让该次下载半路失败），
     *  提示用户先取消下载——不静默删一部分，避免"选了 10 张只删 6 张"的困惑。
     *  演示模式同样走全流程（预检→确认框→逐张执行），仅执行结果为模拟值。 */
    fun requestDelete(rows: List<PhotoRow>? = null) {
        if (!connected) { uiLog = "未连接相机，无法删除"; return }
        if (deleteBusy.value || deletePlan.value != null) return
        val picked = rows ?: collectSelectedRows()
        if (picked.isEmpty()) { uiLog = "未选中任何照片"; return }
        val busy = downloadQueue
            .filter { it.status.value == QStatus.QUEUED || it.status.value == QStatus.RUNNING }
            .mapTo(HashSet()) { it.handle }
        val conflict = picked.count { it.handle in busy }
        if (conflict > 0) {
            uiLog = "所选照片中有 $conflict 张正在下载，请先取消或等待完成后再删除"
            return
        }
        deleteBusy.value = true
        Thread {
            // 写保护预检：①先看已知状态（后台扫描确认过 / 上次删除被 0x200D 拒过）
            // ②未知才问相机（0x1008 在 Wi-Fi 传输会话可能不支持 → 失败不计入，
            //   交由相机端 0x200D 拒绝码兜底，并在删除失败时回写徽标）
            // 演示模式不问相机（无通道）：只用预置的 isProtected 已知状态
            var prot = 0
            for (r in picked) {
                if (r.isProtected.value) { prot++; continue }
                val p = if (demoMode.value) null else try {
                    synchronized(camMutex) {
                        usbSession?.objectProtected(r.handle)
                            ?: GPhoto2Bridge.nativeObjectProtectedNative(r.handle)
                                .let { c -> if (c < 0) null else c == 1 }
                    }
                } catch (t: Throwable) { null }
                if (p == true) {
                    prot++
                    mainHandler.post { r.isProtected.value = true }   // 预检确认：回写徽标
                }
            }
            deleteBusy.value = false
            deletePlan.value = DeletePlan(picked.size, prot, picked)
        }.start()
    }

    /** 预览页单张删除入口 */
    fun requestDeleteOne(row: PhotoRow) = requestDelete(listOf(row))

    /** 确认框「删除」→ IO 线程逐句柄执行（串行 camMutex，与下载互斥）。
     *  0x2001=已删除；0x200D=受保护/只读（跳过，Z6 II 实测拒绝码）；
     *  0x2009=句柄无效（同按跳过计）；其他=失败（记响应码）。
     *  演示模式：无相机通道，未受保护的行一律模拟 0x2001 成功（受保护行在
     *  上方已被 isProtected 跳过），覆盖"全成功气泡 / 混合结果框"两条 UI 路径。 */
    fun confirmDelete() {
        val plan = deletePlan.value ?: return
        deletePlan.value = null
        deleteBusy.value = true
        Thread {
            var ok = 0; var prot = 0; var fail = 0
            val codes = StringBuilder()
            for (r in plan.rows) {
                if (!connected) break
                // 已知受保护（扫描确认 / 上次 0x200D 拒过）：不发命令，直接跳过——
                // 发了必被拒，还会让整批多等一次往返
                if (r.isProtected.value) { prot++; continue }
                val code = if (demoMode.value) 0x2001 else try {
                    synchronized(camMutex) {
                        usbSession?.deleteObject(r.handle)
                            ?: GPhoto2Bridge.nativeDeleteNative(r.handle)
                    }
                } catch (t: Throwable) { -2 }
                when {
                    code == 0x2001 -> { ok++; removePhotoRow(r.handle) }
                    // 0x200D = PTP_RC_ReadOnlyObject：相机端「保护/只读」（Z6 II 实测拒绝码）。
                    // 0x2009 = Invalid ObjectHandle 也按受保护计（句柄失效/跨会话旧句柄）。
                    code == 0x200D || code == 0x2009 -> {
                        prot++
                        // 从相机端确认受保护：回写徽标，下次删除前有预警
                        mainHandler.post { r.isProtected.value = true }
                    }
                    else -> {
                        fail++
                        if (codes.length < 100)
                            codes.append("0x").append((code and 0xFFFF).toString(16)).append(' ')
                    }
                }
            }
            deleteBusy.value = false
            val interrupted = ok + prot + fail < plan.rows.size
            if (prot == 0 && fail == 0 && !interrupted) {
                // 全部删除成功：不弹结果框，改由 UI 弹底部小气泡（删除成功 N 张）后自动消失
                deleteResult.value = null
                deleteResultTitle.value = null
                deleteOkEvent.value = Pair(++deleteOkSeq, ok)
                Log.i("GPhoto2", "机内删除完成: ok=$ok / 共${plan.rows.size}（成功静默，气泡提示）")
            } else {
                deleteResultTitle.value = when {
                    ok == 0 && fail == 0 && !interrupted -> "无法删除"      // 全是受保护
                    ok == 0 && fail == 0 -> "删除中断"
                    fail > 0 && ok == 0 -> "删除失败"
                    else -> "删除未完成"
                }
                deleteResult.value = buildString {
                    if (ok > 0) append("已删除 ").append(ok).append(" 张")
                    if (prot > 0) {
                        if (ok > 0) append("；")
                        append(prot).append(" 张受保护已跳过（需在机身上取消「保护」后才能删除）")
                    }
                    if (fail > 0) {
                        if (ok > 0 || prot > 0) append("；")
                        append(fail).append(" 张失败（").append(codes.toString().trim()).append("）")
                    }
                    if (interrupted) append("；连接已中断，剩余未处理")
                }
                Log.i("GPhoto2", "机内删除完成: ok=$ok prot=$prot fail=$fail / 共${plan.rows.size}（弹结果框）")
            }
            if (ok > 0) refreshCamStorage()
        }.start()
    }

    /** 删除成功后本地行移除 + 选中态同步 + 配对缓存重建（已下载标记与本地文件不动）。
     *  ⚠️ 必须整体 post 主线程：photoRows 是 SnapshotStateList，IO 线程 removeAll 与
     *  主线程 derivedStateOf{computeFilteredPhotos()} 的迭代并发 = ConcurrentModificationException
     *  （实测堆栈 StateListIterator.validateModification ← computeFilteredPhotos，选 4 张删到第 2/3 张即崩，
     *  且崩溃会中断 confirmDelete 循环导致"删不全"）。计数 ok++ 留在 IO 线程，仅状态变更回主线程。 */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun removePhotoRow(handle: Int) {
        mainHandler.post {
            val row = photoRows.firstOrNull { it.handle == handle } ?: return@post
            if (row.selected.value) selectedCount.value = (selectedCount.value - 1).coerceAtLeast(0)
            photoRows.removeAll { it.handle == handle }
            rowByHandle.remove(handle)
            refreshPairRows()
            val alive = pairRowsAll.mapTo(HashSet()) { it.key }
            if (pairSelection.value.any { it !in alive }) {
                pairSelection.value = pairSelection.value.filterTo(LinkedHashSet()) { it in alive }
            }
        }
    }

    /** Wi-Fi 卡内「扫描相机」：直接走网段扫描（跳过上次 IP） */
    fun requestWifiScan() {
        Thread { wifiConnectFlow(preferScan = true) }.start()
    }

    /** 双卡失败原因清除（用户重新点卡时调用） */
    fun clearConnFailure() {
        connFailMsg.value = ""
        scanResults.clear()
    }

    /* ---------- 事件轮询 + 失联检测 ---------- */

    /** 事件轮询：周期消费相机事件（原生会话 0x941c GetEventEx），维持会话健康。
     *  连续 3 次失败 = 会话已死（后台被冻结/相机关闭），如实标记断开。
     *  心跳日志用于诊断后台存活情况。 */
    private fun startEventPolling() {
        stopEventPolling()
        pollerRunning = true
        eventThread = Thread {
            var failStreak = 0
            var beats = 0
            val t0 = System.currentTimeMillis()
            while (pollerRunning && connected) {
                val ok = try {
                    synchronized(camMutex) { GPhoto2Bridge.nativeEventPollNative() == 0 }
                } catch (t: Throwable) { false }
                beats++
                if (!ok) {
                    failStreak++
                    Log.w("GPhoto2", "poll 失败 #$failStreak (t=+${(System.currentTimeMillis() - t0) / 1000}s)")
                    if (failStreak >= 3 && connected) {
                        markSessionLost("心跳连续失败 $failStreak 次")
                        break
                    }
                } else {
                    failStreak = 0
                    if (beats % 4 == 0)
                        Log.i("GPhoto2", "poll ok #${beats / 4} (t=+${(System.currentTimeMillis() - t0) / 1000}s)")
                }
                try { Thread.sleep(850) } catch (e: InterruptedException) { break }
            }
        }.apply {
            isDaemon = true
            name = "gphoto2-event"
            start()
        }
    }

    private fun stopEventPolling() {
        pollerRunning = false
        val t = eventThread
        eventThread = null
        t?.join(1500)
    }

    /** 会话失联（心跳失败/回前台体检失败）：如实更新状态，不假装还连着 */
    private fun markSessionLost(reason: String) {
        if (!connected) return
        connected = false
        pollerRunning = false
        ptpPath = ""
        connPhase.value = "disconnected"
        connText.value = "连接已断开 · 点按重连"
        connFailMsg.value = "相机连接已断开（$reason）"
        uiLog = "相机连接已断开（$reason）"
        camStorage.value = null
        deletePlan.value = null      // 删除中失联：撤下确认框，confirmDelete 循环自行中断
        paramsReadOnly.value = false
        paramHint.value = null
        lvThreadRunning = false
        lvActive.value = false
        lvFrame.value = null
        lvSupported.value = null
        movieRecording.value = false
        remoteOpen.value = false
        Log.i("GPhoto2", "markSessionLost: $reason")
        stopKeepAlive()
    }

    /** 回前台体检：后台期间会话可能已被相机/系统掐掉 */
    fun resumeHealthCheck() {
        if (!connected || connecting) return
        if (usbSession != null) return   // USB 会话健康由 IO 错误与拔线广播处理，无 Wi-Fi 心跳
        Thread {
            val ok = try {
                synchronized(camMutex) { GPhoto2Bridge.nativeEventPollNative() == 0 }
            } catch (t: Throwable) { false }
            if (!ok && connected) markSessionLost("回前台体检失败")
        }.start()
    }

    /* ---------- 后台保活（前台服务 + WifiLock）---------- */

    private fun startKeepAlive() {
        try {
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            if (wifiLock == null)
                wifiLock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "nikon-cam")
                    .apply { setReferenceCounted(false); acquire() }
            Log.i("GPhoto2", "WifiLock isHeld=${wifiLock?.isHeld}")
        } catch (t: Throwable) {
            Log.i("GPhoto2", "WifiLock 失败: $t")
        }
        CameraKeepAliveService.start(ctx)
        requestBatteryExemption()   // 首次连接弹一次系统确认框
    }

    private fun stopKeepAlive() {
        try { wifiLock?.let { if (it.isHeld) it.release() } } catch (_: Throwable) {}
        wifiLock = null
        CameraKeepAliveService.stop(ctx)
    }

    /** 电池优化豁免：MIUI/原生系统会限制后台网络，豁免后前台服务才能真正保活 */
    private fun requestBatteryExemption() {
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                ctx.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:${ctx.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (t: Throwable) {
            Log.i("GPhoto2", "电池豁免请求失败: $t")
        }
    }

    /** App 真正退出（isFinishing）时整体清理 */
    fun shutdown() {
        stopEventPolling()
        stopKeepAlive()
        usbSession?.let { s -> synchronized(camMutex) { runCatching { s.close() } } }
        usbSession = null
        GPhoto2Bridge.nativeTransferClose()
        if (ptpPath.isNotEmpty()) GPhoto2Bridge.nativeExit()
    }
}
