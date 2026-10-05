package com.example.nikontransfer

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
import android.net.NetworkCapabilities
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlin.math.roundToInt

/** 照片行：预览位图异步填充（原生会话单通道，必须串行取块）
 *  fileNo：真实文件编号（文件名去扩展名，如 "DSC_1234"；USB 枚举提供，Wi-Fi 枚举为 null），
 *  是 RAW+JPG 合并配对的精确键 —— 尼康同一张的 JPG/NEF 同名不同扩展名。 */
class PhotoRow(val name: String, val handle: Int, val type: String, val stamp: String,
               val fileNo: String? = null) {
    val preview = mutableStateOf<Bitmap?>(null)
    val selected = mutableStateOf(false)
    val downloaded = mutableStateOf(false)
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
    if (type.equals("JPG", true)) androidx.compose.ui.graphics.Color(0xFF00695C)
    else androidx.compose.ui.graphics.Color(0xFF6A1B9A)

/** "20261001-162351" → "2026-10-01 16:23:51"（相机时钟，可能比手机慢） */
fun prettyStamp(s: String): String =
    if (s.length >= 15 && s[8] == '-')
        "${s.slice(0..3)}-${s.slice(4..5)}-${s.slice(6..7)} ${s.slice(9..10)}:${s.slice(11..12)}:${s.slice(13..14)}"
    else s

/** 下载队列条目状态 */
enum class QStatus { QUEUED, RUNNING, DONE, FAILED, CANCELED }

/** 下载队列条目：一个文件一条；状态被下载管理页与全屏预览页共同读取（单一真相源） */
class QueueItem(val handle: Int, val name: String, val type: String, val stamp: String) {
    val status = mutableStateOf(QStatus.QUEUED)
    val total = mutableStateOf(0L)
    val got = mutableStateOf(0L)
    val speed = mutableStateOf("")
    @Volatile var cancelRequested = false
    /** 实际下载耗时（ms）：DONE 时有效，0 = 未完成/被取消 */
    var elapsedMs: Long = 0
}

fun humanSize(b: Long): String = when {
    b >= 1 shl 20 -> "%.1fMB".format(b / 1048576.0)
    b >= 1 shl 10 -> "%.0fKB".format(b / 1024.0)
    else -> "${b}B"
}

/** 连接与相册的全部状态和逻辑。
 *  放在 ViewModel 里：MIUI 等系统在 App 切后台后可能销毁重建 Activity，
 *  ViewModel 跨重建存活 —— 前台服务维持的相机会话不会被 UI 重置丢掉。 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ACTION_USB_PERMISSION = "com.example.nikontransfer.USB_PERMISSION"


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
    /** 双卡选择的通道：usb / wifi / null（null = 未点击，走自动连接偏好） */
    val pendingChannel = mutableStateOf<String?>(null)
    /** 最近一次连接失败的原因（显示在双卡下方） */
    val connFailMsg = mutableStateOf("")

    /* ---------- USB 直连通道（PTP over bulk，照搬 Z传 路线）---------- */
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
                        connText.value = "USB 权限被拒 · 插拔数据线重试"
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
            connText.value = "USB 相机已插入 · 请授权"
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
        connText.value = "USB 已拔出 · 正在回退 Wi-Fi …"
        // 拔线自愈：自动发起现有 Wi-Fi 连接流程
        Thread { connectionFlow() }.start()
    }

    /** USB 连接核心：成功返回 true（connPhase/connText/listFiles 全部就位） */
    private fun tryConnectUsbCore(dev: UsbDevice, auto: Boolean): Boolean {
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
            connText.value = "USB 直连相机…"
            val session = UsbPtpSession(usbManager, dev)
            synchronized(camMutex) { session.openSession() }
            usbSession = session
            connected = true
            connPhase.value = "connected"
            connChannel.value = "usb"
            connDetail.value = session.modelInfo
            connectedIp.value = ""
            connText.value = "枚举照片…"
            uiLog = listFiles()
            val model = session.modelInfo.split("|").first()
            connText.value = "USB 直连 · $model"
            Log.i("UsbPtp", "USB 直连建立: ${session.modelInfo} (auto=$auto)")
            return true
        } catch (t: Throwable) {
            Log.w("UsbPtp", "USB 连接失败: $t")
            usbSession = null
            connected = false
            connPhase.value = "disconnected"
            connText.value = "USB 连接失败: ${t.message}"
            connFailMsg.value = "USB 连接失败 · ${t.message}"
            pendingChannel.value = null
            return false
        } finally {
            connecting = false
        }
    }

    /* ---------- 相册 / 下载 / 预览 ---------- */
    val photoRows = mutableStateListOf<PhotoRow>()
    @Volatile private var previewGen = 0
    @Volatile private var nefDumped = false
    @Volatile private var downloadBusy = false
    val downloadProgress = mutableStateOf("")

    /* ---------- 多选模式 ---------- */
    val selectMode = mutableStateOf(false)

    /* ---------- 照片筛选（会话内有效，不跨启动记忆）---------- */
    val filterFormat = mutableStateOf("全部")          // 全部 / JPG / NEF（单选）
    val filterUntransferred = mutableStateOf(false)   // 状态 chip：只看未传
    val filterDownloaded = mutableStateOf(false)      // 状态 chip：只看已传
    val filterStart = mutableStateOf<String?>(null)   // "YYYYMMDD"，null = 不限
    val filterEnd = mutableStateOf<String?>(todayKey())  // 默认今天（含）

    private fun todayKey(): String {
        val d = java.time.LocalDate.now()
        return "%04d%02d%02d".format(d.year, d.monthValue, d.dayOfMonth)
    }

    /** 是否有偏离默认的筛选条件（决定筛选按钮高亮） */
    val filterActive: Boolean
        get() = filterFormat.value != "全部" || filterUntransferred.value ||
            filterDownloaded.value || filterStart.value != null ||
            filterEnd.value != todayKey()

    fun resetFilter() {
        filterFormat.value = "全部"
        filterUntransferred.value = false
        filterDownloaded.value = false
        filterStart.value = null
        filterEnd.value = todayKey()
    }

    /** 筛选后的可见照片 —— 网格 / 全屏预览翻页 / 多选全选的统一数据源 */
    val visiblePhotos: List<PhotoRow>
        get() {
            val fmt = filterFormat.value
            val onlyUn = filterUntransferred.value
            val onlyDl = filterDownloaded.value
            val st = filterStart.value
            val en = filterEnd.value
            return photoRows.filter { r ->
                if (fmt != "全部" && !r.type.equals(fmt, true)) return@filter false
                val dl = r.downloaded.value
                if (onlyUn && !onlyDl && dl) return@filter false
                if (onlyDl && !onlyUn && !dl) return@filter false
                val d = if (r.stamp.length >= 8) r.stamp.substring(0, 8) else ""
                if (st != null && (d.isEmpty() || d < st)) return@filter false
                if (en != null && (d.isEmpty() || d > en)) return@filter false
                true
            }
        }

    /** 按日期分组的可见照片：日期从新到旧，组内保持原排序 */
    data class DateSection(val dateKey: String, val rows: List<PhotoRow>)

    val visibleSections: List<DateSection>
        get() {
            val order = ArrayList<String>()
            val map = LinkedHashMap<String, MutableList<PhotoRow>>()
            for (r in visiblePhotos) {
                val d = if (r.stamp.length >= 8) r.stamp.substring(0, 8) else "00000000"
                val list = map.getOrPut(d) { mutableListOf() }
                if (list.isEmpty()) order.add(d)
                list.add(r)
            }
            return order.map { DateSection(it, map[it]!!) }
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
     *  zip 一一配对 —— 旧实现 firstOrNull 会把同秒其余照片整格吞掉（丢图根因）。 */
    val pairRowsAll: List<PairRow>
        get() {
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
            return rows.sortedByDescending { it.stamp }
        }

    /** 合并模式的可见对：格式筛选隐藏（①b）；下载状态=补全语义（②a）；日期照旧 */
    val visiblePairs: List<PairRow>
        get() {
            val onlyUn = filterUntransferred.value
            val onlyDl = filterDownloaded.value
            val st = filterStart.value
            val en = filterEnd.value
            return pairRowsAll.filter { p ->
                if (onlyUn && !onlyDl && p.allDownloaded) return@filter false
                if (onlyDl && !onlyUn && !p.allDownloaded) return@filter false
                val d = p.stamp.take(8)
                if (st != null && d < st) return@filter false
                if (en != null && d > en) return@filter false
                true
            }
        }

    data class PairSection(val dateKey: String, val rows: List<PairRow>)

    val pairSections: List<PairSection>
        get() {
            val order = ArrayList<String>()
            val map = LinkedHashMap<String, MutableList<PairRow>>()
            for (p in visiblePairs) {
                val d = p.stamp.take(8)
                val list = map.getOrPut(d) { mutableListOf() }
                if (list.isEmpty()) order.add(d)
                list.add(p)
            }
            return order.map { PairSection(it, map[it]!!) }
        }

    /* ---------- 已下载标记（跨会话持久化，键 = 合成文件名）---------- */
    private val downloadedNames = mutableStateOf<Set<String>>(emptySet())

    /* ---------- 存储位置（Q1=A 默认 Pictures/NikonTransfer/<拍摄日期>/，可 SAF 自定义）---------- */
    val dateFolderOn = mutableStateOf(true)          // 按拍摄日期文件夹保存，默认开（Q3=A）
    val customDirUri = mutableStateOf<String?>(null) // SAF tree URI，null = 默认位置
    val dirDisplay = mutableStateOf("Pictures/NikonTransfer")

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
            dirDisplay.value = "Pictures/NikonTransfer（默认）"
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
                arrayOf("Pictures/NikonTransfer%", "Download/NikonTransfer%"),
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

    init {
        loadDownloaded()
        mergePairs.value = prefs.getBoolean("set_merge_pairs", false)
        dateFolderOn.value = prefs.getBoolean("set_date_folder", true)   // 默认开（Q3=A）
        customDirUri.value = prefs.getString("save_dir_uri", null)
        refreshDirDisplay()
    }

    /* ---------- 日志 ---------- */
    var uiLog by mutableStateOf("就绪")

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

    /* ---------- 扫描 ---------- */

    /** 收集所有 Wi-Fi 接口的 /24 网段前缀（如 "10.19.161"） */
    private fun wifiSubnets(): List<String> {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val out = LinkedHashSet<String>()
        for (net in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val lp = cm.getLinkProperties(net) ?: continue
            for (la in lp.linkAddresses) {
                val a = la.address.address
                if (a is Array<*> || a.size != 4) continue
                if (la.prefixLength == 24)
                    out.add("${a[0].toInt() and 0xFF}.${a[1].toInt() and 0xFF}.${a[2].toInt() and 0xFF}")
            }
        }
        return out.toList()
    }

    /** 并发扫描网段的 15740 端口（PTP/IP），返回开放的 IP 列表 */
    private fun scanSubnet(subnet: String, progress: (Int, Int) -> Unit): List<String> {
        val found = java.util.Collections.synchronizedList(ArrayList<String>())
        val pool = java.util.concurrent.Executors.newFixedThreadPool(16)
        val ips = (1..254).map { "$subnet.$it" }
        val latch = java.util.concurrent.CountDownLatch(ips.size)
        val done = java.util.concurrent.atomic.AtomicInteger()
        for (ip in ips) {
            pool.execute {
                try {
                    val s = java.net.Socket()
                    try {
                        s.connect(java.net.InetSocketAddress(ip, 15740), 300)
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

    /** 设置页「扫描相机」：扫全部网段 + 读取相机名 */
    fun scanForCameras() {
        scanning.value = true
        try {
            GPhoto2Bridge.setup(ctx)
            val candidates = ArrayList<String>()
            for (subnet in wifiSubnets()) {
                scanText.value = "扫描 $subnet.x …"
                candidates.addAll(
                    scanSubnet(subnet) { done, total ->
                        scanText.value = "扫描 $subnet.x … $done/$total"
                    }
                )
            }
            scanResults.clear()
            for (ip in candidates.distinct()) {
                val info = synchronized(camMutex) { GPhoto2Bridge.nativeProbeCameraInfo(ip) } ?: "|"
                scanResults.add(ip to info)
            }
            if (scanResults.isEmpty())
                scanText.value = "未发现相机 · 请确认相机已进入 Wi-Fi 等待态"
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

    /** 双卡入口（IO 线程）：按选择通道直连；未选则 USB 优先。成功后清双卡状态。 */
    fun startConnect(channel: String?): Boolean {
        if (connecting || connected) return false
        connFailMsg.value = ""
        pendingChannel.value = channel
        return when (channel) {
            "usb" -> tryConnectManualUsb()
            "wifi" -> wifiConnectFlow(preferScan = false)
            else -> connectionFlow()
        }
    }

    /** 手动点 USB 卡：已插线→直连；未插线→提示（UI 侧读 connFailMsg） */
    private fun tryConnectManualUsb(): Boolean {
        val dev = UsbPtpSession.findCamera(usbManager)
        if (dev == null) {
            connFailMsg.value = "未检测到尼康相机 · 请用数据线连接相机与手机"
            pendingChannel.value = null
            return false
        }
        if (!UsbPtpSession.hasPermission(usbManager, dev)) {
            UsbPtpSession.requestPermission(ctx, usbManager, dev)
            connFailMsg.value = "已请求 USB 授权 · 请在弹窗中允许"
            pendingChannel.value = null
            return false
        }
        return tryConnectUsbCore(dev, auto = false)
    }

    /** Wi-Fi 连接（IO 线程）：上次 IP → 网段扫描（热点网段优先）→ 命中即连。
     *  preferScan=true 时跳过上次 IP 直接扫描（Wi-Fi 卡内「扫描相机」按钮）。 */
    private fun wifiConnectFlow(preferScan: Boolean): Boolean {
        connecting = true
        connPhase.value = "connecting"
        pendingChannel.value = "wifi"   // 标记当前尝试通道（双卡进度归位用）
        try {
            GPhoto2Bridge.setup(ctx)
            if (!preferScan) {
                // ① 上次相机 IP
                val lastIp = prefs.getString("camera_ip", null)
                if (lastIp != null) {
                    connText.value = "探测上次相机 $lastIp …"
                    val info = synchronized(camMutex) { GPhoto2Bridge.nativeProbeCameraInfo(lastIp) }
                    if (info != null) return finishConnect(lastIp, info)
                    Log.i("GPhoto2", "上次 IP $lastIp 不可达，转为网段扫描")
                }
            }
            // ② 网段扫描：热点网段（AOSP 默认 192.168.43.x）优先，其次路由器网段
            val subnets = wifiSubnets().toMutableList()
            if (!subnets.contains("192.168.43")) subnets.add(0, "192.168.43")
            else { subnets.remove("192.168.43"); subnets.add(0, "192.168.43") }
            if (subnets.isEmpty()) {
                connText.value = "未发现可用 Wi-Fi 子网"
                connPhase.value = "disconnected"
                connFailMsg.value = "未发现可用 Wi-Fi 子网 · 请确认手机已连接 Wi-Fi 或已开热点"
                pendingChannel.value = null
                return false
            }
            var candidates: List<String> = emptyList()
            for (subnet in subnets) {
                connText.value = "扫描网段 $subnet.x …"
                candidates = scanSubnet(subnet) { done, total ->
                    connText.value = "扫描网段 $subnet.x … $done/$total"
                }
                if (candidates.isNotEmpty()) break
            }
            if (candidates.isEmpty()) {
                connText.value = "未发现相机 · 请确认相机已进入 Wi-Fi 等待态"
                connPhase.value = "disconnected"
                connFailMsg.value = "未发现相机 · 请确认相机已进入 Wi-Fi 等待态"
                pendingChannel.value = null
                return false
            }
            val named = candidates.map { ip ->
                ip to (synchronized(camMutex) { GPhoto2Bridge.nativeProbeCameraInfo(ip) } ?: "|")
            }
            if (named.size == 1) {
                val (ip, info) = named[0]
                return finishConnect(ip, info)
            }
            scanResults.clear()
            scanResults.addAll(named)
            connText.value = "发现 ${named.size} 台相机 · 在 Wi-Fi 卡中选择"
            connPhase.value = "disconnected"
            connFailMsg.value = "发现 ${named.size} 台相机 · 请在下方选择"
            pendingChannel.value = null
            return false
        } finally {
            connecting = false
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
        connText.value = "连接 $model ($serial) …"
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
        connText.value = "配对探针 + 传输会话…"
        val ret = synchronized(camMutex) { GPhoto2Bridge.nativePairingProbe(ip) }
        if (ret != 0) {
            connected = false
            ptpPath = ""
            stopKeepAlive()       // 重连失败时清理可能残留的保活
            connText.value = "连接失败: $ret（相机需处于 Wi-Fi 等待态）"
            connFailMsg.value = "连接失败 ($ret) · 请确认相机已进入 Wi-Fi 等待态后重试"
            connPhase.value = "disconnected"
            pendingChannel.value = null
            return false
        }
        connected = true
        ptpPath = "ptpip:$ip"
        connChannel.value = "wifi"
        startEventPolling()
        startKeepAlive()          // 前台服务 + WifiLock：后台不再被冻结断连
        prefs.edit().putString("camera_ip", ip).apply()
        connectedIp.value = ip
        connDetail.value = info
        connText.value = "枚举照片…"
        uiLog = listFiles()
        connText.value = "$model ($serial) · $ip"
        connPhase.value = "connected"
        everConnected = true
        return true
    }

    /**
     * 关键：把进程网络绑定到「相机所在」的网络。
     * - 手机连相机热点（AP 模式）：相机网段是受管 Wi-Fi 网络之一，按子网匹配并绑定。
     * - 相机连手机热点：热点网段不在受管网络列表里，此时【不绑定】——
     *   未绑定套接字经主路由表的直连路由即可到达热点客户端（实测验证）。
     */
    private fun bindToWifiNetwork(cameraIp: String): String {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val camBytes = try {
            java.net.InetAddress.getByName(cameraIp).address
        } catch (e: Exception) {
            Log.i("GPhoto2", "IP 解析失败($cameraIp)，未绑定网络")
            return "IP 解析失败，未绑定网络"
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
        // 相机在手机热点网段（不受管网络）：不绑定，走主路由直连
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

    fun listFiles(): String = synchronized(camMutex) {
        if (!connected) return "未连接相机，请先连接成功后再列目录"
        // USB 全量枚举（逐对象 GetObjectInfo）需数秒：立即给出加载提示，替代旧日志（如"就绪"）
        uiLog = "正在读取照片列表…"
        // 配对模式原生列目录："句柄:YYYYMMDD-HHMMSS:类型|..."
        val failMsg = "列目录失败（传输会话可能已断开，请重新连接）"
        val res: String = if (usbSession != null) {
            val s = usbSession!!
            val r = s.enumerate()
            when {
                r == null -> return "USB 枚举失败 · ${s.lastDiag}"
                r.isEmpty() -> return "USB 枚举为空 · ${s.lastDiag}"
                else -> r
            }
        } else {
            GPhoto2Bridge.nativeListNative() ?: return failMsg
        }
        val rows = res.split('|').filter { it.isNotBlank() }
        if (rows.isEmpty()) return "相机里没有待传输的照片"
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
        }.sortedByDescending { it.stamp }              // 从新到旧
        syncDownloadedNames()                          // 本地删除过的照片不再标记已下载
        parsed.forEach { it.downloaded.value = it.name in downloadedNames.value }
        photoRows.clear()
        photoRows.addAll(parsed)
        val autoPreview = prefs.getBoolean("set_auto_preview", true)
        if (autoPreview) {
            startPreviewLoading(parsed)                // 异步逐个取内嵌缩略图
            return "共 ${photoRows.size} 个文件"
        }
        return "共 ${photoRows.size} 个文件"
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
                "Pictures/NikonTransfer/$dateSub" else "Pictures/NikonTransfer"
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
                        "Download/NikonTransfer/$dateSub" else "Download/NikonTransfer"
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

    /* ---------- 下载队列（单一真相源：单张/批量全量入队，串行 worker 消费）---------- */
    val downloadQueue = mutableStateListOf<QueueItem>()
    private val queueLock = Object()
    @Volatile private var workerRunning = false

    /** 活跃任务数（顶栏队列按钮角标） */
    val activeDownloadCount: Int
        get() = downloadQueue.count { it.status.value in setOf(QStatus.QUEUED, QStatus.RUNNING) }

    /** 入队（去重：同一文件已在排队/下载中则忽略） */
    fun enqueueDownload(row: PhotoRow) {
        synchronized(queueLock) {
            if (downloadQueue.any {
                    it.handle == row.handle &&
                        it.status.value in setOf(QStatus.QUEUED, QStatus.RUNNING)
                }) return
            // 重新下载：清掉同句柄旧的已完成/已取消条目，避免槽位读到陈旧状态
            downloadQueue.removeAll {
                it.handle == row.handle && it.status.value in setOf(QStatus.DONE, QStatus.CANCELED)
            }
            downloadQueue.add(QueueItem(row.handle, row.name, row.type, row.stamp))
        }
        ensureWorker()
    }

    /** 失败/取消后的重试：复用条目，状态复位重新排队 */
    fun retryDownload(item: QueueItem) {
        item.cancelRequested = false
        item.got.value = 0
        synchronized(queueLock) {
            if (item.status.value !in setOf(QStatus.QUEUED, QStatus.RUNNING)) {
                item.status.value = QStatus.QUEUED
                if (downloadQueue.none { it === item }) downloadQueue.add(item)
            }
        }
        ensureWorker()
    }

    /** 取消：排队中 = 直接移除；下载中 = 置取消标志（分块循环丢弃已下字节） */
    fun cancelDownload(item: QueueItem) {
        when (item.status.value) {
            QStatus.QUEUED -> synchronized(queueLock) { downloadQueue.remove(item) }
            QStatus.RUNNING -> item.cancelRequested = true
            else -> {}
        }
    }

    /** 清空已完成/已取消条目（失败项保留以便重试） */
    fun clearFinished() {
        synchronized(queueLock) {
            downloadQueue.removeAll { it.status.value in setOf(QStatus.DONE, QStatus.CANCELED) }
        }
    }

    /** 一键取消所有未完成任务：排队中直接移除，下载中置取消标志（当前分块后停止） */
    fun cancelAllDownloads() {
        synchronized(queueLock) {
            downloadQueue.removeAll { it.status.value == QStatus.QUEUED }
        }
        downloadQueue.forEach { if (it.status.value == QStatus.RUNNING) it.cancelRequested = true }
    }

    /* ---------- 队列完成提醒（震动 + 通知；设置可关，仅批量生效）---------- */
    var notifyDoneOn: Boolean by mutableStateOf(prefs.getBoolean("set_notify_done", true))
    fun setNotifyDone(v: Boolean) {
        notifyDoneOn = v
        prefs.edit().putBoolean("set_notify_done", v).apply()
    }

    private var batchMode = false            // 本次批次是否来自批量下载
    private var batchOk = 0
    private var batchFail = 0
    private var batchActive = false

    private fun vibrateOnce() {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26)
            v.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(200)
    }

    private fun ensureWorker() {
        synchronized(queueLock) {
            if (workerRunning) return
            workerRunning = true
        }
        batchOk = 0
        batchFail = 0
        batchActive = true
        Thread {
            while (true) {
                val item = synchronized(queueLock) {
                    downloadQueue.firstOrNull { it.status.value == QStatus.QUEUED }
                } ?: break
                runQueueItem(item)
                when (item.status.value) {
                    QStatus.DONE -> batchOk++
                    QStatus.FAILED -> batchFail++
                    else -> {}
                }
            }
            if (batchActive && batchMode && (batchOk > 0 || batchFail > 0)) {
                if (notifyDoneOn) {
                    vibrateOnce()
                    CameraKeepAliveService.notifyDone(ctx, "下载完成：成功 $batchOk · 失败 $batchFail")
                }
                batchActive = false
            }
            synchronized(queueLock) { workerRunning = false }
            CameraKeepAliveService.clearProgress(ctx)
        }.apply {
            isDaemon = true
            name = "download-worker"
            start()
        }
    }

    /** 串行消费一个队列条目：取总大小（0x9421）→ 0x9431 按 128KB 分块拉取拼装 → 保存。
     *  全程持有 camMutex（事件轮询与文件传输不能在同一条 PTP 流上交错）。
     *  每块更新进度（UI 250ms 节流）；通知栏 1s 节流。 */
    private fun runQueueItem(item: QueueItem) = synchronized(camMutex) {
        if (!connected) { item.status.value = QStatus.FAILED; return }
        item.status.value = QStatus.RUNNING
        item.cancelRequested = false
        item.got.value = 0
        val startMs = System.currentTimeMillis()   // 下载耗时统计（DONE 时写入 elapsedMs）
        // USB 通道：优先偏移读分块（0x101B，带进度）；首块失败回退整对象一次取回
        val usb = usbSession
        if (usb != null) {
            val total = sizeOf(item.handle)
            if (total <= 0) { item.status.value = QStatus.FAILED; return }
            item.total.value = total
            val buf = ByteArray(total.toInt())
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
                item.got.value = got
            }
            if (fallbackWhole) {
                val whole = usb.getObject(item.handle)
                if (whole == null) { item.status.value = QStatus.FAILED; return }
                System.arraycopy(whole, 0, buf, 0, minOf(whole.size, buf.size))
            }
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
            val want = minOf(0x20000L, total - got).toInt()
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
            item.got.value = got
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
        synchronized(queueLock) {
            fresh.forEach { downloadQueue.add(QueueItem(it.handle, it.name, it.type, it.stamp)) }
        }
        photoRows.forEach { it.selected.value = false }   // 进度由队列页接管
        pairSelection.value = emptySet()
        ensureWorker()
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

    /** 快速滚动条显示阈值：照片数达到该值才显示右侧滚动条；-1 = 不显示。
     *  计数口径 = 未折叠的照片数（折叠天不参与，UI 侧按 collapsedDates 计算）。 */
    val scrollbarThreshold = mutableStateOf(prefs.getInt("set_scrollbar_threshold", 50))
    fun setScrollbarThreshold(v: Int) {
        scrollbarThreshold.value = v
        prefs.edit().putInt("set_scrollbar_threshold", v).apply()
    }

    var thumbCacheBytes: Long by mutableStateOf(0L)
    // lazy 一次性创建：原 getter 每次访问都 mkdirs()，主线程反复系统调用造成滑动 IO 风暴
    private val thumbDir: java.io.File by lazy {
        java.io.File(ctx.cacheDir, "thumbs").apply { mkdirs() }
    }
    private fun thumbFile(key: String) =
        java.io.File(thumbDir, "$key.jpg")

    fun refreshThumbCacheSize() {
        thumbCacheBytes = thumbDir.listFiles()?.sumOf { it.length() } ?: 0L
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
            if (evicted) thumbRowByKey.remove(key)?.preview?.value = null
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
        if (row.onScreen || !row.enteredOnce) row.preview.value = bmp
    }

    /* ---------- 磁盘读回串行队列：启动后首滑时内存 LRU 是冷的，滑动经过的每个
     *  新格子都会触发读磁盘。原实现"每格子开一个新线程"= 每秒 10~30 个线程创建
     *  + 并发 IO/解码争抢——首滑卡顿的元凶（多滑几次变流畅 = 格子进了内存缓存）。
     *  改为常驻单线程串行处理 + in-flight 去重 + 磁盘 miss 负缓存。 ---------- */
    private val thumbIoQueue = java.util.concurrent.ConcurrentLinkedQueue<PhotoRow>()
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
                    val row = thumbIoQueue.poll() ?: break
                    try {
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
        thumbIoQueue.add(row)
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
            // 照搬 Z传：BACKGROUND 优先级把线程放进 background cgroup → 调度到小核，
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

    /* ---------- 高清预览（复刻 Z传：结构解析 + 0x9431 定向读嵌入大图） ----------
     *  抓包实证（capture-hires.pcap，2026-10-02）：
     *  JPG：头 128KB 内 APP2 MPF 元数据列出各内嵌图（size+offset），取最大条目
     *       一次读出即完整 JPEG（实测 0.7~0.9MB，offset+len 恰为文件尾）。
     *  NEF：补读 0x20000..0x80000（TIFF IFD0 的 SubIFD 链所在），解析压缩=7
     *       （JPEG）SubIFD 的 StripOffsets/ByteCounts，定向读出全尺寸嵌入预览
     *       （实测 0.67~0.83MB），RAW 数据一个字节不传。
     *  Z传 自身无缓存、翻页重拉 —— 这里加 LRU 缓存与 ±1 页预取，翻回秒显。 */

    /** 内存 LRU：最近 6 张高清位图（解码采样至最长边 ≤2048px，单张 ~6MB） */
    private val hiresCache = object : android.util.LruCache<Int, Bitmap>(6) {
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
     *  取长度最小者 = Z传 实际行为（669KB 小预览而非 1.34MB 大预览）。 */
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
        uiLog = "相机连接已断开（$reason）"
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
