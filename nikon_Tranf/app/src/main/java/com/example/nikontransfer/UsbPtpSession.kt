package com.example.nikontransfer

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * USB 直连相机（PTP over USB bulk）——照搬 Z传 路线的自研实现：
 * UsbManager 发现尼康 → claimInterface → bulkTransfer 读写 PTP 容器。
 * 与 Wi-Fi 侧（GPhoto2 native）互不相干；object id 语义与 gphoto2 的 handle 一致。
 *
 * 上层需要的四个原语：enumerate() / objectSize() / partialRead() / close()。
 * 枚举输出与 nativeListNative() 同格式："句柄:YYYYMMDD-HHMMSS:类型|..."。
 */
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
        private const val OP_GET_OBJECT_HANDLES = 0x1007
        private const val OP_GET_OBJECT_INFO = 0x1008
        private const val OP_GET_OBJECT = 0x1009
        private const val OP_GET_THUMB = 0x100A
        private const val OP_GET_PARTIAL_OBJECT = 0x1014
        private const val OP_GET_PARTIAL_OBJECT64 = 0x95C1
        // 尼康 USB 的部分读取是专有 0x101B（Z传 字典实证）；标准 0x1014 在尼康 USB 上
        // 被解释为 GetDevicePropDesc 之类，参数对不上必返 0x2006
        private const val OP_NIKON_GET_PARTIAL = 0x101B
        // 尼康专有：对象清单表（句柄+时间戳一次拿全，免逐对象 0x1008；PTP-IP/USB 均可用）
        private const val OP_NIKON_OBJ_TABLE = 0x9434
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
                android.content.Intent("com.example.nikontransfer.USB_PERMISSION")
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

        val model = device.productName?.ifBlank { "Nikon" } ?: "Nikon"
        val serial = try { device.serialNumber ?: "" } catch (_: Throwable) { "" }
        modelInfo = if (serial.isBlank()) model else "$model|$serial"

        // OpenSession（Nikon 偶发 SessionAlreadyOpen：先 Close 再开）
        var resp = transaction(OP_OPEN_SESSION, intArrayOf(1))
        if (resp.code == RESP_SESSION_ALREADY_OPEN) {
            transaction(OP_CLOSE_SESSION)
            resp = transaction(OP_OPEN_SESSION, intArrayOf(1))
        }
        if (resp.code != RESP_OK) {
            conn.close()
            throw IllegalStateException("OpenSession 失败: 0x${(resp.code and 0xFFFF).toString(16)}")
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
    fun getObject(handle: Int): ByteArray? = withRecovery {
        val resp = transaction(OP_GET_OBJECT, intArrayOf(handle))
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

    /** GetPartialObject 偏移读：与 nativePreviewNative(handle, offset, maxLen) 等价。
     *  双通道自适应：标准 0x1014（maxBytes u16 陷阱→clamp 32KB）失败自动切换
     *  MTP 扩展 0x95C1 GetPartialObject64（64 位 offset/length），并锁定成功通道。 */
    fun partialRead(handle: Int, offset: Long, maxLen: Int): ByteArray? = withRecovery { partialReadInner(handle, offset, maxLen) }

    private fun partialReadInner(handle: Int, offset: Long, maxLen: Int): ByteArray? {
        // ① 已锁定 0x95C1（MTP 64 位）
        if (partialMode == 2) return partialRead64(handle, offset, maxLen)
        // ② 尼康专有 0x101B GetPartialObject（Z传 字典实证；标准 0x1014 在尼康 USB 上
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
        Log.i("UsbPtp", "会话关闭")
    }

    /* ---------- PTP 协议 ---------- */

    private class ObjInfo(
        val handle: Int,
        val name: String,
        val stamp: String,      // "YYYYMMDD-HHMMSS"
        val size: Long,
        val isAssociation: Boolean
    ) {
        // 第 4 段 = 真实文件名：RAW+JPG 合并配对需要文件编号（同张 JPG/NEF 同名），
        // 秒级时间戳在连拍时会撞车。解析处 split(':', limit = 4) 兼容无文件名的 Wi-Fi 行。
        val row: String get() = "$handle:$stamp:${if (name.endsWith(".NEF", true)) "NEF" else "JPG"}:$name"
    }

    private class Resp(val code: Int, val data: ByteArray, val params: IntArray)

    private fun transaction(opcode: Int, params: IntArray = intArrayOf()): Resp {
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
                while (filled < payload) {
                    val want = minOf(BULK_CHUNK, payload - filled)
                    val buf = ByteArray(want)
                    val r = bulkIn(buf)
                    if (r <= 0) throw IllegalStateException("数据中断 @$filled/$payload")
                    val cp = minOf(r, payload - filled)
                    System.arraycopy(buf, 0, data, filled, cp)
                    filled += cp
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
        bb.short                     // ProtectionStatus
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
        return ObjInfo(handle, fname, stamp, size, isAssociation = false)
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
