package com.example.nikontransfer

object GPhoto2Bridge {
    @Volatile private var libReady = false

    /** assets 解包版本号：每次更换 assets 里的 .so 时 +1，触发重新解包 */
    private const val EXTRACT_VERSION = "2"

    init { System.loadLibrary("gphoto2_jni") }

    /** 首次调用：把 assets/gphoto2 里的 camlib/iolib 解包到 filesDir，并设置路径 */
    fun setup(context: android.content.Context) {
        if (libReady) return
        val base = java.io.File(context.filesDir, "gphoto2")
        val marker = java.io.File(context.filesDir, "gphoto2.exver")
        val markerStale = try {
            !marker.exists() || marker.readText().trim() != EXTRACT_VERSION
        } catch (e: Exception) { true }
        if (markerStale) {
            // 版本变化：清空旧解包（曾出现过 0 字节/旧版本残留导致 dlopen 失败）
            base.deleteRecursively()
            marker.delete()
        }
        listOf("camlibs", "iolibs").forEach { sub ->
            val dir = java.io.File(base, sub).apply { mkdirs() }
            context.assets.list("gphoto2/$sub")?.forEach { so ->
                val f = java.io.File(dir, so)
                // 不能只判断 exists：解包中断留下的 0 字节/截断文件会被永久沿用，
                // 导致 libltdl dlopen 报 file not found。空文件必须重新解包。
                if (!f.exists() || f.length() == 0L) {
                    val tmp = java.io.File(dir, "$so.tmp")
                    context.assets.open("gphoto2/$sub/$so").use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (f.exists()) f.delete()
                    if (!tmp.renameTo(f)) tmp.delete()
                }
                f.setReadable(true, false)
            }
        }
        nativeSetupLibPaths(
            java.io.File(base, "camlibs").absolutePath,
            java.io.File(base, "iolibs").absolutePath
        )
        marker.writeText(EXTRACT_VERSION)
        libReady = true
    }

    external fun nativeSetupLibPaths(camlibs: String, iolibs: String)
    external fun nativeConnect(path: String): Int          // "ptpip:192.168.1.66"
    external fun nativeListFiles(folder: String): String?  // "a.JPG|b.JPG|"
    external fun nativeListFolders(folder: String): String? // 子文件夹 "DCIM|xxx|"
    external fun nativeDownloadFile(folder: String, name: String): ByteArray?
    external fun nativeWaitEvent(timeoutMs: Int): Int   // 应答相机 Probe / 消费事件
    external fun nativePairingProbe(ip: String): Int    // Nikon 配对探针（复刻 Z传 握手）
    external fun nativeExit()

    // ---- 原生传输路径（配对模式，nativePairingProbe 成功后自动建立常驻会话）----
    external fun nativeListNative(): String?            // "句柄:YYYYMMDD-HHMMSS:JPG|..."
    external fun nativeDownloadNative(handle: Int): ByteArray?
    external fun nativePreviewNative(handle: Int, offset: Int, maxLen: Int): ByteArray?   // 对象局部数据（含内嵌缩略图）
    external fun nativeEventPollNative(): Int           // 消费 0x941c 事件，维持会话健康
    external fun nativeTransferClose()
    external fun nativeProbeCameraInfo(ip: String): String?   // INIT 握手探测相机信息 "型号|序列号"
}