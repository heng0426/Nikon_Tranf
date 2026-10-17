package com.shinemeow.app

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.SystemClock
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * USB 直连相机（PTP over USB bulk）——自研实现（实现路线经同类 App 抓包实证）：
 * UsbManager 发现尼康 → claimInterface → bulkTransfer 读写 PTP 容器。
 * 与 Wi-Fi 侧（GPhoto2 native）互不相干；object id 语义与 gphoto2 的 handle 一致。
 *
 * 上层需要的四个原语：enumerate() / objectSize() / partialRead() / close()。
 * 枚举输出与 nativeListNative() 同格式："句柄:YYYYMMDD-HHMMSS:类型|..."。
 */
/**
 * 存储容量信息（0x1005 GetStorageInfo）：连接详情浮层展示，删除前可判断卡剩余空间。
 * label 部分固件回空串；freeImages 为 -1 表示相机未报（不明）。
 */
data class CamStorageInfo(
    val capacity: Long,
    val free: Long,
    val freeImages: Long,
    val label: String
)

class UsbPtpSession(
    private val usbManager: UsbManager,
    private val device: UsbDevice
) : java.io.Closeable {

    private var connection: UsbDeviceConnection? = null
    private var usbIf: UsbInterface? = null
    private var epIn: UsbEndpoint? = null       // bulk IN（数据/响应）
    private var epOut: UsbEndpoint? = null      // bulk OUT（命令）
    private var txid = 0
    private val objectSizes = HashMap<Int, Long>()
    /** 相机自报支持的操作码（GetDeviceInfo.OperationsSupported） */
    private val supportedOps = HashSet<Int>()
    /** 对象写保护缓存（GetObjectInfo.ProtectionStatus != 0 = 受保护，删除会被相机拒绝） */
    private val objectProtected = HashMap<Int, Boolean>()
    /** 偏移读通道：1=尼康 0x101B，2=MTP 0x95C1 (GetPartialObject64) */
    private var partialMode = 0
    /** 0x101B 单次请求字节数：满块成功翻倍升档（1MB 封顶），0x2006 减半并封顶 */
    private var partialLimit = 0x8000
    private var partialLimitCapped = false

    lateinit var modelInfo: String              // "型号|序列号"（对齐 splitInfo 解析）
        private set
    var open: Boolean = false
        private set
    /** 最近一次枚举的诊断信息（失败/为空时 UI 直接展示，用户可念给助手） */
    var lastDiag: String = ""
        private set
    private val lastDiagLock = Object()

    companion object {
        const val VENDOR_NIKON = 0x04B0
        private const val BULK_CHUNK = 0x10000  // 单次 bulkTransfer 64KB（失败自动降 16KB）

        // PTP 标准操作码
        private const val OP_GET_DEVICE_INFO = 0x1001
        private const val OP_OPEN_SESSION = 0x1002
        private const val OP_CLOSE_SESSION = 0x1003
        private const val OP_GET_STORAGE_IDS = 0x1004
        private const val OP_GET_STORAGE_INFO = 0x1005
        private const val OP_GET_OBJECT_HANDLES = 0x1007
        private const val OP_GET_OBJECT_INFO = 0x1008
        private const val OP_GET_OBJECT = 0x1009
        private const val OP_GET_THUMB = 0x100A
        private const val OP_DELETE_OBJECT = 0x100B
        private const val OP_INITIATE_CAPTURE = 0x100E    // 标准 InitiateCapture：拍进存储卡（非 SDRAM）
        private const val OP_GET_PARTIAL_OBJECT = 0x1014  // ⚠️ 本项目实测当 GetPartialObject 用；ZENCHE 口径为 GetDevicePropDesc——属性族以 0x1015/0x1016 为准
        private const val OP_GET_DEVICE_PROP_VALUE = 0x1015
        private const val OP_SET_DEVICE_PROP_VALUE = 0x1016
        private const val OP_GET_PARTIAL_OBJECT64 = 0x95C1
        // 尼康 USB 的部分读取是专有 0x101B（操作码字典实证）；标准 0x1014 在尼康 USB 上
        // 被解释为 GetDevicePropDesc 之类，参数对不上必返 0x2006
        private const val OP_NIKON_GET_PARTIAL = 0x101B
        // 尼康专有：对象清单表（句柄+时间戳一次拿全，免逐对象 0x1008；PTP-IP/USB 均可用）
        private const val OP_NIKON_OBJ_TABLE = 0x9434
        private const val OP_GET_EVENT = 0x90C7           // 尼康：取事件（ObjectAdded 等）
        // 尼康文件句柄位段：hi16 高 8 位 0x2a=JPG、0x0a=NEF，低 8 位是文件夹段
        // （Z7=0x1c，新款固件跨文件夹 0x1c/0x1d——硬编码低字节会丢整段照片）
        private const val HI_GROUP_JPG = 0x2a00
        private const val HI_GROUP_NEF = 0x0a00
        private const val PARTIAL_LIMIT_MAX = 0x100000   // 单次部分读取上限 1MB

        private const val RESP_OK = 0x2001
        private const val RESP_SESSION_ALREADY_OPEN = 0x201E
        // 单次 bulk 超时：GetThumb/大对象在相机读卡+生成时可能明显 >8s
        private const val TIMEOUT = 15000

        private const val FORMAT_ASSOCIATION = 0x3001

        /** 找第一台尼康 PTP 相机（vendor 0x04b0 且含 bulk 接口） */
        fun findCamera(usbManager: UsbManager): UsbDevice? =
            usbManager.deviceList.values.firstOrNull {
                it.vendorId == VENDOR_NIKON
            }

        fun hasPermission(usbManager: UsbManager, device: UsbDevice): Boolean =
            usbManager.hasPermission(device)

        /** 请求 USB 设备权限（自定义广播回执由 MainViewModel 的 receiver 处理） */
        fun requestPermission(ctx: android.content.Context, usbManager: UsbManager, device: UsbDevice) {
            val pi = android.app.PendingIntent.getBroadcast(
                ctx, 0,
                android.content.Intent("com.shinemeow.app.USB_PERMISSION")
                    .setPackage(ctx.packageName),
                android.app.PendingIntent.FLAG_MUTABLE
            )
            usbManager.requestPermission(device, pi)
        }
    }

    /** 打开设备并建立 PTP 会话。失败抛异常，由调用方提示。 */
    fun openSession() {
        val conn = usbManager.openDevice(device)
            ?: throw IllegalStateException("openDevice 失败")
        val iface = (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .firstOrNull { it.endpointCount >= 2 }
            ?: throw IllegalStateException("未找到 PTP 接口").also { conn.close() }

        val eps = (0 until iface.endpointCount).map { iface.getEndpoint(it) }
        val out = eps.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
            ?: throw IllegalStateException("无 bulk OUT 端点").also { conn.close() }
        val din = eps.firstOrNull { it.direction == UsbConstants.USB_DIR_IN && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
            ?: throw IllegalStateException("无 bulk IN 端点").also { conn.close() }

        connection = conn
        usbIf = iface
        epOut = out
        epIn = din
        conn.claimInterface(iface, true)

        // 机型显示名：PID 档案命中 → 档案名（比 productName "NIKON DSC Z 6_2" 干净）；
        // 未收录尼康 → 「未知尼康 (0xXXXX)」标注（宽容连接，见 NikonRegistry）
        val model = NikonRegistry.modelLabel(device.productId, device.productName)
        val serial = try { device.serialNumber ?: "" } catch (_: Throwable) { "" }
        modelInfo = if (serial.isBlank()) model else "$model|$serial"

        // OpenSession 三段自愈（解决"杀进程重开一直转圈"）：
        //  ① 直接开（正常路径）
        //  ② SessionAlreadyOpen → Close 再开（尼康偶发）
        //  ③ 仍失败/传输异常 → **USB 级 Reset（0x66 class request，强制相机清
        //     会话与端点状态）** → 600ms 后再开。典型场景 = 上个进程被划掉时没有
        //     CloseSession，相机端会话残留、bulk 端点不响应（ZENCHE
        //     openSessionWithRecovery 的同款手法，PtpCamera §2.3.3）。
        fun openAttempt(): Resp? = try {
            transaction(OP_OPEN_SESSION, intArrayOf(1))
        } catch (t: Throwable) {
            Log.w("UsbPtp", "OpenSession 传输异常: $t")
            null
        }
        var resp: Resp? = openAttempt()
        if (resp?.code == RESP_SESSION_ALREADY_OPEN) {
            transaction(OP_CLOSE_SESSION)
            resp = openAttempt()
        }
        if (resp?.code != RESP_OK) {
            Log.w("UsbPtp", "OpenSession 未就绪(${resp?.code?.let { "0x${(it and 0xFFFF).toString(16)}" } ?: "传输异常"})——USB 级 Reset 后重试")
            usbReset(conn)
            SystemClock.sleep(600)
            resp = openAttempt()
        }
        if (resp == null || resp.code != RESP_OK) {
            conn.close()
            throw IllegalStateException(
                "OpenSession 失败: " + (resp?.let { "0x${(it.code and 0xFFFF).toString(16)}" } ?: "传输超时")
            )
        }
        // GetDeviceInfo：解析相机支持的操作码表（决定偏移读走哪条通道）
        try {
            val di = transaction(OP_GET_DEVICE_INFO)
            if (di.code == RESP_OK && di.data.size >= 2) {
                val bb = ByteBuffer.wrap(di.data).order(ByteOrder.LITTLE_ENDIAN)
                val cnt = bb.short.toInt() and 0xFFFF
                repeat(cnt) {
                    if (bb.position() + 2 <= di.data.size) {
                        supportedOps.add(bb.short.toInt() and 0xFFFF)
                    }
                }
                Log.i("UsbPtp", "支持 0x1014=${supportedOps.contains(0x1014)} 0x95C1=${supportedOps.contains(0x95C1)} (共${supportedOps.size}个操作)")
            }
        } catch (t: Throwable) {
            Log.w("UsbPtp", "GetDeviceInfo 失败: $t")
        }
        open = true
        Log.i("UsbPtp", "会话建立: $modelInfo")
    }

    /** PTP class 级设备复位（USB control transfer requestType=0x21 / request=0x66）：
     *  强制相机清会话与端点状态。场景 = 上个进程被划掉未 CloseSession、bulk 端点卡死。
     *  失败仅记日志（Reset 本身不保证成功，后续 OpenSession 重试会给出最终结论）。 */
    private fun usbReset(conn: UsbDeviceConnection) {
        try {
            val r = conn.controlTransfer(0x21, 0x66, 0, 0, null, 0, 500)
            Log.i("UsbPtp", "USB Reset(0x66)=$r")
        } catch (t: Throwable) {
            Log.w("UsbPtp", "USB Reset 失败: $t")
        }
    }

    /* ---------- 上层原语 ---------- */

    /** 全量枚举（快路径优先）：
     *  快：0x9434 表一次拿全量句柄+时间戳，配合句柄位段判断类型——免逐对象
     *      GetObjectInfo（886 张从十几秒降到 ~1s，实证 PTPIP/USB 同款专有接口）。
     *  慢：0x9434 不可用时回退逐对象 GetObjectInfo（旧逻辑，正确性兜底）。
     *  输出与 nativeListNative 同格式 "句柄:YYYYMMDD-HHMMSS:类型|..."；
     *  失败/为空时 lastDiag 携带完整诊断。 */
    fun enumerate(): String? {
        val diag = StringBuilder()
        try {
            // ① GetStorageIDs：相机暴露了几个存储
            val stor = transaction(OP_GET_STORAGE_IDS)
            val storIds = parseUint32Array(stor.data) ?: intArrayOf()
            diag.append("存储=[${storIds.joinToString(",") { it.toString() }}](code=0x${(stor.code and 0xFFFF).toString(16)}) ")
            // ② 0x9434 时间戳表：[长度][数量][句柄+0+时间+日期]×N（每条 16 字节）
            val stampTable = HashMap<Int, String>()
            for (sid in storIds) {
                val r = transaction(OP_NIKON_OBJ_TABLE, intArrayOf(sid, 0, 0))
                if (r.code != RESP_OK || r.data.size < 8) continue
                val bb = ByteBuffer.wrap(r.data).order(ByteOrder.LITTLE_ENDIAN)
                bb.int                                           // 长度
                val cnt = bb.int
                repeat(minOf(cnt, 4096)) {
                    if (bb.position() + 16 > r.data.size) return@repeat
                    val h = bb.int; bb.int                       // 句柄 + 保留字段
                    val t1 = bb.int; val t2 = bb.int
                    val sec = (t1 ushr 8) and 0xff; val min = (t1 ushr 16) and 0xff; val hour = (t1 ushr 24) and 0xff
                    val day = t2 and 0xff; val mon = (t2 ushr 8) and 0xff; val year = (t2 ushr 16) and 0xffff
                    if (year in 2000..2100 && mon in 1..12 && day in 1..31) {
                        stampTable[h] = String.format(
                            java.util.Locale.US, "%04d%02d%02d-%02d%02d%02d",
                            year, mon, day, hour, min, sec
                        )
                    }
                }
            }
            diag.append("0x9434表=${stampTable.size} ")
            // ③ 根层句柄 + 位段分类（JPG/NEF=文件，其余=文件夹/标记）
            val root = transaction(OP_GET_OBJECT_HANDLES, intArrayOf(-1, 0, 0))
            val rootIds = parseUint32Array(root.data) ?: intArrayOf()
            diag.append("根层=${rootIds.size}(code=0x${(root.code and 0xFFFF).toString(16)}) ")
            if (root.code != RESP_OK) {
                lastDiag = diag.toString()
                return null
            }
            val seen = HashSet<Int>()
            val files = ArrayList<Pair<Int, Boolean>>()          // handle → isJpg
            val assoc = ArrayList<Int>()
            fun classify(h: Int) {
                if (!seen.add(h)) return
                val hi = (h ushr 16) and 0xffff
                when (hi and 0xff00) {
                    HI_GROUP_JPG -> files.add(h to true)
                    HI_GROUP_NEF -> files.add(h to false)
                    else -> assoc.add(h)                          // 标记句柄子层为空，无害
                }
            }
            rootIds.forEach { classify(it) }
            // ④ 文件夹下钻（BFS ≤3 层；部分固件 parent=0 会回跨层对象，seen 兜重复）
            var scan = 0
            var layerEnd = assoc.size
            var depth = 0
            while (scan < assoc.size && depth < 3) {
                val parent = assoc[scan++]
                val r = transaction(OP_GET_OBJECT_HANDLES, intArrayOf(-1, 0, parent))
                if (r.code == RESP_OK) parseUint32Array(r.data)?.forEach { classify(it) }
                if (scan == layerEnd) { layerEnd = assoc.size; depth++ }
            }
            diag.append("文件=${files.size} ")
            // ⑤ 快路径：表+位段直接出列表
            if (stampTable.isNotEmpty() && files.isNotEmpty()) {
                lastDiag = diag.toString()
                return files.joinToString("|") { (h, isJpg) ->
                    val type = if (isJpg) "JPG" else "NEF"
                    "$h:${stampTable[h] ?: "00000000-000000"}:$type"
                }
            }
            // ⑥ 慢路径回退：逐对象 GetObjectInfo（0x9434 不支持时；尼康部分固件的
            //    parent=0 会返回跨层级对象，递归后按句柄去重防 LazyGrid 崩溃）
            val objs = mutableListOf<ObjInfo>()
            val seen2 = HashSet<Int>()
            var infoFail = 0
            fun walk2(parent: Int) {
                val resp = transaction(OP_GET_OBJECT_HANDLES, intArrayOf(-1, 0, parent))
                if (resp.code != RESP_OK) {
                    diag.append("子层$parent code=0x${(resp.code and 0xFFFF).toString(16)} ")
                    return
                }
                val ids = parseUint32Array(resp.data) ?: return
                for (id in ids) {
                    if (!seen2.add(id)) continue
                    val info = fetchObjectInfo(id)
                    if (info == null) {
                        infoFail++
                        continue
                    }
                    if (info.isAssociation) walk2(id) else objs += info
                }
            }
            for (rid in rootIds) {
                if (!seen2.add(rid)) continue
                val info = fetchObjectInfo(rid)
                if (info == null) {
                    infoFail++
                    continue
                }
                if (info.isAssociation) walk2(rid) else objs += info
            }
            diag.append("info失败=$infoFail 去重后=${objs.size} ")
            if (infoFailDetail.isNotEmpty()) diag.append("首例: $infoFailDetail ")
            if (objs.isEmpty()) {
                lastDiag = diag.toString()
                return ""
            }
            lastDiag = diag.toString()
            return objs.joinToString("|") { it.row }
        } catch (t: Throwable) {
            lastDiag = "${diag}异常=${t.message}"
            Log.w("UsbPtp", "enumerate 异常: $t")
            return null
        }
    }

    fun objectSize(handle: Int): Long = objectSizes[handle] ?: run {
        try {
            val info = fetchObjectInfo(handle)
            info?.size ?: -1L
        } catch (_: Throwable) {
            -1L
        }
    }

    /** 带管道自愈的操作包装：一次传输异常（超时/错位）会让 USB 管道残留数据而死掉
     *  （后续全部 w=-1），此时重建会话（closeDevice→openDevice→OpenSession）并重试一次。 */
    private val recoveryLock = Object()

    private fun <T> withRecovery(op: () -> T?): T? {
        val first = try { op() } catch (t: IllegalStateException) {
            Log.w("UsbPtp", "操作异常（将尝试重建会话）: ${t.message}")
            null
        }
        if (first != null) return first
        if (!open) return null
        synchronized(recoveryLock) {
            // 双检：别的线程可能刚重建完并恢复了管道
            if (!open) return null
            val second = try { op() } catch (t: IllegalStateException) { null }
            if (second != null) return second
            return try {
                close()                    // 旧管道丢弃
                openSession()              // 重建（txid 重置、端点重取）
                op()
            } catch (t: Throwable) {
                Log.w("UsbPtp", "重建后仍失败: $t")
                null
            }
        }
    }

    /** 整对象下载（0x1009）：Z6 II USB 无任何部分读取能力（0x1014/0x95C1 均不支持），
     *  下载/高清预览统一走整对象。数据阶段预分配零拷贝（50MB NEF 峰值仅 1×）。 */
    fun getObject(handle: Int): ByteArray? = getObject(handle) {}

    /** 整对象一次事务读取（0x1009 GetObject）：onProgress 在每个 bulk 轮后回调已填字节数，
     *  供整文件传输模式显示真实进度（单 PTP 事务，比 1MB 分段的 0x101B 往返更快）。 */
    fun getObject(handle: Int, onProgress: (Long) -> Unit): ByteArray? = withRecovery {
        val resp = transaction(OP_GET_OBJECT, intArrayOf(handle), onProgress)
        if (resp.code != RESP_OK) {
            val msg = "整读($handle)=0x${(resp.code and 0xFFFF).toString(16)}"
            Log.w("UsbPtp", msg)
            synchronized(lastDiagLock) { lastDiag = (msg + " | " + lastDiag).take(300) }
            null
        } else resp.data
    }

    /** 相机内嵌缩略图（0x100A GetThumb）：返回 JPEG 字节 */
    fun getThumb(handle: Int): ByteArray? = withRecovery {
        val resp = transaction(OP_GET_THUMB, intArrayOf(handle))
        if (resp.code != RESP_OK) {
            Log.w("UsbPtp", "GetThumb($handle)=0x${(resp.code and 0xFFFF).toString(16)}")
            null
        } else resp.data
    }

    /** 机内删除（0x100B DeleteObject，参数 = 对象句柄；ZENCHE 未实现，本机增量功能）。
     *  返回 PTP 响应码：0x2001=已删除；0x200D=对象只读/受保护（相机端二次拒绝，
     *  Z6 II 实测）；0x2009=句柄无效（跨会话旧句柄）；其他见 PTP 响应码表；
     *  -1=传输失败（withRecovery 已尝试重建会话）。
     *  上层据此分别计入「已删除 / 受保护跳过 / 失败」三态。 */
    fun deleteObject(handle: Int): Int = withRecovery {
        val resp = transaction(OP_DELETE_OBJECT, intArrayOf(handle))
        if (resp.code != RESP_OK) {
            Log.w("UsbPtp", "DeleteObject($handle)=0x${(resp.code and 0xFFFF).toString(16)}")
        }
        resp.code
    } ?: -1

    /** 标准快门（0x100E InitiateCapture，参数 [0,0]）：照片进存储卡。
     *  任务 5 决策：USB 不走 ZENCHE 的 0x9207 SDRAM 路径（拍进卡与"传输工具"
     *  心智一致——网格刷新即见新照片，可立即下载/删除，省掉 SDRAM 下载恢复链）。
     *  返回 PTP 响应码：0x2001=已触发；其他=相机拒绝（如 0x2005 不支持/
     *  0x2019 DeviceBusy）；-1=传输失败。 */
    fun initiateCapture(): Int = try {
        val resp = transaction(OP_INITIATE_CAPTURE, intArrayOf(0, 0))
        if (resp.code != RESP_OK) {
            Log.w("UsbPtp", "InitiateCapture=0x${(resp.code and 0xFFFF).toString(16)}")
        }
        resp.code
    } catch (t: Throwable) {
        Log.w("UsbPtp", "InitiateCapture 传输失败: ${t.message}")
        -1
    }

    /** 取事件（0x90c7 GetEvent，尼康扩展；ZENCHE USB 同款）：
     *  dataset = u16 count + N×(u16 code + u32 param)。
     *  拍摄后轮询 0x4002 ObjectAdded 判断照片写卡完成。
     *  null = 通道拒绝/传输失败（上层退化为固定延时）。 */
    fun getEvents(): List<Pair<Int, Int>>? = try {
        val r = transaction(OP_GET_EVENT)
        if (r.code != RESP_OK || r.data.size < 2) {
            Log.w("UsbPtp", "GetEvent=0x${(r.code and 0xFFFF).toString(16)}")
            null
        } else {
            val bb = java.nio.ByteBuffer.wrap(r.data).order(ByteOrder.LITTLE_ENDIAN)
            val n = bb.short.toInt() and 0xFFFF
            val out = ArrayList<Pair<Int, Int>>(n)
            repeat(n) {
                if (bb.remaining() < 6) return@repeat
                val code = bb.short.toInt() and 0xFFFF
                val param = bb.int
                out.add(code to param)
            }
            out
        }
    } catch (t: Throwable) {
        Log.w("UsbPtp", "GetEvent 传输失败: ${t.message}")
        null
    }

    /** 读设备属性（0x1015 GetDevicePropValue，ZENCHE 实测口径）。
     *  返回原始 LE 字节（u16 属性 2 字节 / u32 属性 4 字节）；null=查询失败。 */
    fun getDeviceProp(prop: Int): ByteArray? = withRecovery {
        val r = transaction(OP_GET_DEVICE_PROP_VALUE, intArrayOf(prop))
        if (r.code != RESP_OK) {
            Log.w("UsbPtp", "GetDeviceProp(0x%04x)=0x%04x".format(prop, r.code and 0xFFFF))
            null
        } else r.data
    }

    /** 写设备属性（0x1016 SetDevicePropValue，data-out 相位；ZENCHE 实测口径）。
     *  返回 PTP 响应码；-1=传输失败。 */
    fun setDeviceProp(prop: Int, value: ByteArray): Int = try {
        val r = transactionDataOut(OP_SET_DEVICE_PROP_VALUE, intArrayOf(prop), value)
        if (r.code != RESP_OK) {
            Log.w("UsbPtp", "SetDeviceProp(0x%04x)=0x%04x".format(prop, r.code and 0xFFFF))
        }
        r.code
    } catch (t: Throwable) {
        Log.w("UsbPtp", "SetDeviceProp 传输失败: ${t.message}")
        -1
    }

    /** 存储容量（0x1004 GetStorageIDs → 0x1005 GetStorageInfo）。
     *  ZENCHE 的连接详情无容量展示；导入后让用户删前可判断卡剩余空间。
     *  ⚠️ Z6 II 双卡槽：0x1004 返回 2 个 sid（0x00010000=槽1/0x00020000=槽2），
     *  **空槽查询 0x1005 返回 0x2013 StoreNotAvailable**（实测）——必须遍历全部 sid，
     *  聚合所有可用存储（空槽自动跳过）；全部不可用才返回 null。
     *  dataset 布局（PTP 标准）：StorageType u16@0 / FilesystemType u16@2 /
     *  AccessCapability u16@4 / MaxCapacity u64@6 / FreeSpaceInBytes u64@14 /
     *  FreeSpaceInImages u32@22 / StorageDescription PSTR@26。 */
    fun storageInfo(): CamStorageInfo? = withRecovery {
        val stor = transaction(OP_GET_STORAGE_IDS)
        val ids = parseUint32Array(stor.data)
        if (stor.code != RESP_OK || ids == null || ids.isEmpty()) {
            Log.w("UsbPtp", "GetStorageIDs 失败 code=0x${(stor.code and 0xFFFF).toString(16)}")
            return@withRecovery null
        }
        Log.i("UsbPtp", "StorageIDs: " + ids.joinToString(" ") { "0x%08x".format(it) })
        var cap = 0L; var free = 0L; var img = 0L; var label = ""; var hit = 0
        for (sid in ids) {
            val r = transaction(OP_GET_STORAGE_INFO, intArrayOf(sid))
            if (r.code != RESP_OK || r.data.size < 26) {
                Log.w(
                    "UsbPtp",
                    "GetStorageInfo(sid=0x%08x) 失败 code=0x%04x dlen=%d".format(
                        sid, r.code and 0xFFFF, r.data.size
                    )
                )
                continue
            }
            val si = parseStorageInfo(r.data) ?: continue
            cap += si.capacity; free += si.free
            img += si.freeImages.coerceAtLeast(0)
            if (label.isEmpty()) label = si.label
            hit++
            Log.i("UsbPtp", "GetStorageInfo(sid=0x%08x) ok cap=%d free=%d".format(sid, si.capacity, si.free))
        }
        if (hit == 0) return@withRecovery null
        CamStorageInfo(cap, free, img, label)
    }

    /** 对象写保护预检（GetObjectInfo.ProtectionStatus）：删除前弹窗标注受保护张数。
     *  null = 查询失败（不阻塞删除流程，交由相机端拒绝码兜底）。 */
    fun objectProtected(handle: Int): Boolean? = withRecovery {
        objectProtected[handle]?.let { return@withRecovery it }
        val info = fetchObjectInfo(handle) ?: return@withRecovery null
        objectProtected[handle] = info.protected
        info.protected
    }

    private fun parseStorageInfo(d: ByteArray): CamStorageInfo? {
        if (d.size < 26) return null
        val bb = java.nio.ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(6)
        val capacity = bb.long
        val free = bb.long
        val freeImages = bb.int.toLong() and 0xFFFFFFFFL
        val label = pstr(bb) ?: ""
        return CamStorageInfo(capacity, free, freeImages, label)
    }

    /** GetPartialObject 偏移读：与 nativePreviewNative(handle, offset, maxLen) 等价。
     *  双通道自适应：标准 0x1014（maxBytes u16 陷阱→clamp 32KB）失败自动切换
     *  MTP 扩展 0x95C1 GetPartialObject64（64 位 offset/length），并锁定成功通道。 */
    fun partialRead(handle: Int, offset: Long, maxLen: Int): ByteArray? = withRecovery { partialReadInner(handle, offset, maxLen) }

    private fun partialReadInner(handle: Int, offset: Long, maxLen: Int): ByteArray? {
        // ① 已锁定 0x95C1（MTP 64 位）
        if (partialMode == 2) return partialRead64(handle, offset, maxLen)
        // ② 尼康专有 0x101B GetPartialObject（操作码字典实证；标准 0x1014 在尼康 USB 上
        //    被 0x2006 拒绝——它被固件解释为别的操作）。
        //    单次块大小自适应：满块成功翻倍升档（1MB 封顶）减少事务次数；0x2006 降档。
        var n = minOf(maxLen, partialLimit)
        var attempts = 0
        while (true) {
            attempts++
            val resp = try {
                transaction(OP_NIKON_GET_PARTIAL, intArrayOf(handle, offset.toInt(), n))
            } catch (t: Throwable) {
                Log.w("UsbPtp", "0x101B 异常: $t")
                null
            }
            if (resp != null && resp.code == RESP_OK) {
                partialMode = 1
                if (n >= partialLimit && !partialLimitCapped && partialLimit < PARTIAL_LIMIT_MAX) {
                    partialLimit = minOf(PARTIAL_LIMIT_MAX, partialLimit * 2)   // 升档试探
                }
                return resp.data
            }
            val code = resp?.code ?: -1
            if (code == 0x2006 && attempts <= 4 && partialLimit > 0x4000) {
                // 参数无效 → 单次上限减半（16KB 封底），并封顶不再升档
                partialLimit = maxOf(0x4000, partialLimit / 2)
                partialLimitCapped = true
                n = minOf(maxLen, partialLimit)
                continue
            }
            Log.w("UsbPtp", "0x101B 失败 code=0x${(code and 0xFFFF).toString(16)} n=$n")
            break
        }
        // ③ MTP 扩展 GetPartialObject64
        val r64 = partialRead64(handle, offset, maxLen)
        if (r64 != null) {
            partialMode = 2
            return r64
        }
        // ④ 终极兜底：整对象下载 + 内存截取（仅 0x101B/0x95C1 均失败时；慢但可用）
        val full = getObject(handle) ?: return null
        val start = offset.toInt().coerceIn(0, full.size)
        val end = minOf(offset.toInt() + maxLen, full.size)
        if (start >= end) return ByteArray(0)
        return full.copyOfRange(start, end)
    }

    /** MTP 扩展：GetPartialObject64（0x95C1），params = [handle, offLo, offHi, lenLo, lenHi] */
    private fun partialRead64(handle: Int, offset: Long, maxLen: Int): ByteArray? = try {
        val resp = transaction(
            OP_GET_PARTIAL_OBJECT64,
            intArrayOf(handle, offset.toInt(), (offset ushr 32).toInt(), maxLen, 0)
        )
        if (resp.code != RESP_OK) {
            val msg = "偏移读64($handle,$offset,$maxLen)=0x${(resp.code and 0xFFFF).toString(16)}"
            Log.w("UsbPtp", msg)
            synchronized(lastDiagLock) { lastDiag = (msg + " | " + lastDiag).take(300) }
            null
        } else resp.data
    } catch (t: Throwable) {
        synchronized(lastDiagLock) { lastDiag = ("偏移读64异常=${t.message} | " + lastDiag).take(300) }
        null
    }

    override fun close() {
        if (!open) return
        open = false
        try { transaction(OP_CLOSE_SESSION) } catch (_: Throwable) {}
        try { connection?.close() } catch (_: Throwable) {}
        connection = null
        objectSizes.clear()
        objectProtected.clear()
        Log.i("UsbPtp", "会话关闭")
    }

    /* ---------- PTP 协议 ---------- */

    private class ObjInfo(
        val handle: Int,
        val name: String,
        val stamp: String,      // "YYYYMMDD-HHMMSS"
        val size: Long,
        val isAssociation: Boolean,
        val protected: Boolean = false
    ) {
        // 第 4 段 = 真实文件名：RAW+JPG 合并配对需要文件编号（同张 JPG/NEF 同名），
        // 秒级时间戳在连拍时会撞车。解析处 split(':', limit = 4) 兼容无文件名的 Wi-Fi 行。
        val row: String get() = "$handle:$stamp:${if (name.endsWith(".NEF", true)) "NEF" else "JPG"}:$name"
    }

    private class Resp(val code: Int, val data: ByteArray, val params: IntArray)

    private fun transaction(opcode: Int, params: IntArray = intArrayOf(), onProgress: (Long) -> Unit = {}): Resp {
        val conn = connection ?: throw IllegalStateException("会话未打开")
        txid++
        // ① Command 容器 → bulk OUT
        val cmdLen = 12 + params.size * 4
        val cmd = ByteBuffer.allocate(cmdLen).order(ByteOrder.LITTLE_ENDIAN)
        cmd.putInt(cmdLen).putShort(1.toShort()).putShort(opcode.toShort()).putInt(txid)
        for (p in params) cmd.putInt(p)
        var w = conn.bulkTransfer(epOut, cmd.array(), cmdLen, TIMEOUT)
        if (w != cmdLen) throw IllegalStateException("命令写入失败 w=$w")

        // ② Data 阶段：先读一包判断是否有数据容器（type=2），否则直接是 Response（type=3）
        val head = ByteArray(0x0400)
        var n = bulkIn(head)
        // 数据长度恰为 512 整数倍时设备会先发 ZLP 标记数据结束 —— 吸收之
        var zlp = 0
        while (n == 0 && zlp++ < 3) n = bulkIn(head)
        if (n < 12) throw IllegalStateException("响应过短 n=$n")
        val hb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        val total = hb.int
        val type = hb.short
        var data = ByteArray(0)
        when (type.toInt()) {
            2 -> {
                // 数据容器：预分配精确大小，分块直填（零多余拷贝，50MB 对象峰值仅 1×）
                val payload = total - 12
                if (payload < 0) throw IllegalStateException("容器长度异常 total=$total")
                val data = ByteArray(payload)
                var filled = minOf(n - 12, payload)
                System.arraycopy(head, 12, data, 0, filled)
                if (payload > 0) onProgress(filled.toLong())
                while (filled < payload) {
                    val want = minOf(BULK_CHUNK, payload - filled)
                    val buf = ByteArray(want)
                    val r = bulkIn(buf)
                    if (r <= 0) throw IllegalStateException("数据中断 @$filled/$payload")
                    val cp = minOf(r, payload - filled)
                    System.arraycopy(buf, 0, data, filled, cp)
                    filled += cp
                    onProgress(filled.toLong())
                }
                // ③ Response（数据恰为 512 倍数时设备先发 ZLP → 吸收；读到非响应容器=错位）
                n = bulkIn(head)
                zlp = 0
                while (n == 0 && zlp++ < 3) n = bulkIn(head)
                if (n < 12) throw IllegalStateException("响应缺失 n=$n")
                val rb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
                rb.int; val rt = rb.short
                if (rt.toInt() != 3) throw IllegalStateException("响应错位 type=$rt")
                val code = rb.short.toInt() and 0xFFFF
                rb.position(12)                 // 跳过 len/type/code/txid
                return Resp(code, data, readParams(rb, n))
            }
            3 -> {
                val code = hb.short.toInt() and 0xFFFF
                val pb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
                pb.position(12)                 // 跳过 len/type/code/txid
                return Resp(code, data, readParams(pb, n))
            }
            else -> throw IllegalStateException("异常容器 type=$type")
        }
    }

    /** data-out 事务（cmd → Data 容器 → Response），0x1016 SetDevicePropValue 专用：
     *  与 transaction 相同的容器格式；值很小（2~4 字节），单容器发完直接读响应。 */
    private fun transactionDataOut(opcode: Int, params: IntArray, payload: ByteArray): Resp {
        val conn = connection ?: throw IllegalStateException("会话未打开")
        txid++
        // ① Command 容器 → bulk OUT
        val cmdLen = 12 + params.size * 4
        val cmd = ByteBuffer.allocate(cmdLen).order(ByteOrder.LITTLE_ENDIAN)
        cmd.putInt(cmdLen).putShort(1.toShort()).putShort(opcode.toShort()).putInt(txid)
        for (p in params) cmd.putInt(p)
        val w = conn.bulkTransfer(epOut, cmd.array(), cmdLen, TIMEOUT)
        if (w != cmdLen) throw IllegalStateException("命令写入失败 w=$w")
        // ② Data 容器（type=2）→ bulk OUT
        val dLen = 12 + payload.size
        val dat = ByteBuffer.allocate(dLen).order(ByteOrder.LITTLE_ENDIAN)
        dat.putInt(dLen).putShort(2.toShort()).putShort(opcode.toShort()).putInt(txid)
        dat.put(payload)
        val w2 = conn.bulkTransfer(epOut, dat.array(), dLen, TIMEOUT)
        if (w2 != dLen) throw IllegalStateException("数据写入失败 w=$w2")
        // ③ Response 容器
        val head = ByteArray(0x0400)
        var n = bulkIn(head)
        var zlp = 0
        while (n == 0 && zlp++ < 3) n = bulkIn(head)
        if (n < 12) throw IllegalStateException("响应缺失 n=$n")
        val rb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        rb.int; val rt = rb.short
        if (rt.toInt() != 3) throw IllegalStateException("响应错位 type=$rt")
        val code = rb.short.toInt() and 0xFFFF
        rb.position(12)
        return Resp(code, ByteArray(0), readParams(rb, n))
    }

    private fun readParams(bb: ByteBuffer, recLen: Int): IntArray {
        val pCount = (recLen - 12) / 4
        return IntArray(pCount.coerceAtLeast(0)) { bb.int }
    }

    /** bulk IN 读取；64KB 失败降 16KB 重试一次（个别机型大 transfer 不支持） */
    private fun bulkIn(buf: ByteArray): Int {
        val conn = connection ?: throw IllegalStateException("会话未打开")
        var r = conn.bulkTransfer(epIn, buf, buf.size, TIMEOUT)
        if (r < 0 && buf.size > 16384) {
            r = conn.bulkTransfer(epIn, buf, 16384, TIMEOUT)
        }
        return r
    }

    /* ---------- 枚举 ---------- */

    private fun walk(parent: Int, out: MutableList<ObjInfo>) {
        val resp = transaction(OP_GET_OBJECT_HANDLES, intArrayOf(-1, 0, parent))
        if (resp.code != RESP_OK) return
        val ids = parseUint32Array(resp.data) ?: return
        for (id in ids) {
            val info = fetchObjectInfo(id) ?: continue
            if (info.isAssociation) walk(id, out) else out += info
        }
    }

    private fun fetchObjectInfo(handle: Int): ObjInfo? {
        val resp = try {
            transaction(OP_GET_OBJECT_INFO, intArrayOf(handle))
        } catch (t: Throwable) {
            if (infoFailDetail.isEmpty()) infoFailDetail = "info($handle)异常=${t.message} "
            return null
        }
        if (resp.code != RESP_OK) {
            if (infoFailDetail.isEmpty()) infoFailDetail = "info($handle)code=0x${(resp.code and 0xFFFF).toString(16)} "
            return null
        }
        val d = resp.data
        if (d.size < 52) {
            if (infoFailDetail.isEmpty()) infoFailDetail = "info($handle)数据过短=${d.size}B "
            return null
        }
        val bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        bb.int                       // StorageID
        val format = bb.short.toInt() and 0xFFFF
        val protection = bb.short.toInt() and 0xFFFF   // ProtectionStatus：0=无保护，非0=受保护
        val size = bb.int.toLong() and 0xFFFFFFFFL
        repeat(7) { bb.int }         // Thumb 尺寸×3 + 图像尺寸×2 + 位深 + Parent
        bb.short                     // AssociationType
        bb.int                       // AssociationDesc
        bb.int                       // SequenceNumber
        // 标准位置（52）读文件名；尼康各固件的头部偶有 ±字节差异，失败时在
        // 44..64 窗口内自适应搜索"像文件名"的 PSTR（.JPG/.NEF 结尾或文件夹名）
        var name = pstr(bb)
        var captured = ""
        var usedOff = 52
        val looksLikeFile = { s: String ->
            s.isNotEmpty() && (s.endsWith(".JPG", true) || s.endsWith(".NEF", true) ||
                (format == FORMAT_ASSOCIATION && s.all { it.isLetterOrDigit() || it == '_' || it == ' ' }))
        }
        if (name == null || !looksLikeFile(name)) {
            var found = false
            for (p in 44..minOf(80, d.size - 2)) {
                val pb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
                pb.position(p)
                val cand = pstr(pb) ?: continue
                if (!looksLikeFile(cand)) continue
                name = cand
                usedOff = p
                captured = pstr(pb) ?: ""
                found = true
                break
            }
            if (!found) {
                if (infoFailDetail.isEmpty()) {
                    val hex = d.take(64).joinToString("") { "%02x".format(it) }
                    infoFailDetail = "info($handle)未找到文件名 size=${d.size}B hex=$hex "
                }
                return null
            }
        } else {
            captured = pstr(bb) ?: ""
        }
        if (infoFailDetail.isEmpty()) infoFailDetail = "首对象name=$name 偏移=$usedOff "
        val fname = name ?: return null
        if (format == FORMAT_ASSOCIATION) {
            return ObjInfo(handle, fname, "", size, isAssociation = true)
        }
        // "20241004T121530" → "20241004-121530"
        val stamp = captured.takeIf { it.length >= 15 && it[8] == 'T' }
            ?.replaceRange(8, 9, "-") ?: ""
        objectSizes[handle] = size
        return ObjInfo(handle, fname, stamp, size, isAssociation = false, protected = protection != 0)
    }
    private var infoFailDetail = ""

    /** PTP 字符串：u1 字符数 N（含 NUL）+ N×2 字节 UTF-16LE */
    private fun pstr(bb: ByteBuffer): String? = try {
        val n = bb.get().toInt() and 0xFF
        if (n <= 0) "" else {
            val bytes = ByteArray(n * 2)
            bb.get(bytes)
            String(bytes, Charsets.UTF_16LE).trimEnd('\u0000')
        }
    } catch (_: Throwable) {
        null
    }

    private fun parseUint32Array(d: ByteArray): IntArray? {
        if (d.size < 4) return null
        val bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
        val cnt = bb.int
        if (cnt < 0 || d.size < 4 + cnt * 4) return null
        return IntArray(cnt) { bb.int }
    }
}
