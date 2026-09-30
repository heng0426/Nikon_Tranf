object GPhoto2Bridge {
    @Volatile private var libReady = false

    init { System.loadLibrary("gphoto2_jni") }

    /** 首次调用：把 assets/gphoto2 里的 camlib/iolib 解包到 filesDir，并设置路径 */
    fun setup(context: android.content.Context) {
        if (libReady) return
        val base = java.io.File(context.filesDir, "gphoto2")
        listOf("camlibs", "iolibs").forEach { sub ->
            val dir = java.io.File(base, sub).apply { mkdirs() }
            context.assets.list("gphoto2/$sub")?.forEach { so ->
                val f = java.io.File(dir, so)
                if (!f.exists()) {
                    context.assets.open("gphoto2/$sub/$so").use { input ->
                        f.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                f.setReadable(true, false)
            }
        }
        nativeSetupLibPaths(
            java.io.File(base, "camlibs").absolutePath,
            java.io.File(base, "iolibs").absolutePath
        )
        libReady = true
    }

    external fun nativeSetupLibPaths(camlibs: String, iolibs: String)
    external fun nativeConnect(path: String): Int          // "ptpip:192.168.1.66"
    external fun nativeListFiles(folder: String): String?  // "a.JPG|b.JPG|"
    external fun nativeDownloadFile(folder: String, name: String): ByteArray?
    external fun nativeExit()
}