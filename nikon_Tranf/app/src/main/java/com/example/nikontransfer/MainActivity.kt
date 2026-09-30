package com.example.nikontransfer

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.nikontransfer.ui.theme.NikonTransferTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var ptpPath = ""           // ptpip:IP
    private var folder = ""            // /store_00010001/DCIM/100XXXXX
    private val files = mutableListOf<String>()

    /** Android 17 (API 37) 的 ACCESS_LOCAL_NETWORK 是运行时权限，先申请再连接 */
    private val localNetPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            lastPermissionGranted = granted
        }
    private var lastPermissionGranted = false
    private var pendingAction: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pendingAction?.invoke()
            else statusText = "缺少本地网络权限，无法连接相机"
        }

    // 简单的状态显示（非 Compose state，仅用于权限回调里更新提示）
    private var statusText: String = ""
        set(value) { field = value; uiLog = value }
    private var uiLog by mutableStateOf("就绪")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NikonTransferTheme {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    @Composable
    private fun MainScreen() {
        var ip by remember { mutableStateOf("192.168.1.66") }
        var log by remember { mutableStateOf(uiLog) }
        val scope = rememberCoroutineScope()

        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = ip, onValueChange = { ip = it },
                label = { Text("相机 IP") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        ensureLocalNetworkPermission {
                            scope.launch {
                                log = withContext(Dispatchers.IO) { connect(ip) }
                                log += "\n" + withContext(Dispatchers.IO) { listFiles() }
                            }
                        }
                    }
                }) { Text("连接并列目录") }
                Button(onClick = {
                    scope.launch {
                        log = withContext(Dispatchers.IO) { downloadFirst() }
                    }
                }) { Text("下载第一张") }
            }
            Text(log, style = MaterialTheme.typography.bodySmall)
            LazyColumn {
                items(files) { f ->
                    Text(f, Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }

    /* ---------- 权限 ---------- */

    private fun ensureLocalNetworkPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < 37) { action(); return }
        val perm = Manifest.permission.ACCESS_LOCAL_NETWORK
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(perm)
        }
    }

    /* ---------- 网络 ---------- */

    /** 关键：把进程网络绑定到相机所在的 Wi-Fi（无外网的网络），否则连接会被切到移动数据 */
    private fun bindToWifiNetwork(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = cm.allNetworks.firstOrNull { net ->
            cm.getNetworkCapabilities(net)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        return wifi?.let { cm.bindProcessToNetwork(it) } ?: false
    }

    /* ---------- 相机操作（全部在 IO 线程） ---------- */

    private fun connect(ip: String): String {
        GPhoto2Bridge.setup(applicationContext)
        bindToWifiNetwork()
        ptpPath = "ptpip:$ip"
        val ret = GPhoto2Bridge.nativeConnect(ptpPath)
        return if (ret == 0) "连接成功 ($ptpPath)" else "连接失败: $ret (检查 IP/相机 Wi-Fi 状态/bindToWifiNetwork)"
    }

    private fun listFiles(): String {
        if (ptpPath.isEmpty()) return "请先连接"
        val dcim = "/store_00010001/DCIM"
        for (sub in listOf("100NZ510", "100NIKON", "100NCZ9", "")) {
            val f = if (sub.isEmpty()) dcim else "$dcim/$sub"
            val res = GPhoto2Bridge.nativeListFiles(f)
            if (!res.isNullOrEmpty()) {
                folder = f
                files.clear()
                files.addAll(res.split('|').filter { it.isNotBlank() })
                return "目录 $f 共 ${files.size} 个文件"
            }
        }
        return "未找到文件（子目录名可能不同，看 logcat GPhoto2 排查）"
    }

    private fun downloadFirst(): String {
        val name = files.firstOrNull { it.endsWith(".JPG", true) || it.endsWith(".NEF", true) }
            ?: return "列表为空，请先「连接并列目录」"
        val data = GPhoto2Bridge.nativeDownloadFile(folder, name) ?: return "下载失败"
        // 写入系统相册（JPEG）；NEF 建议 M3 存 App 专属目录
        val mime = if (name.endsWith(".NEF", true)) "image/x-nikon-nef" else "image/jpeg"
        val collection = if (name.endsWith(".NEF", true))
            MediaStore.Files.getContentUri("external_primary")
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
        }
        val uri = contentResolver.insert(collection, values) ?: return "MediaStore 插入失败"
        contentResolver.openOutputStream(uri)?.use { it.write(data) }
        return "已保存: $name (${data.size} bytes)"
    }

    override fun onDestroy() {
        if (ptpPath.isNotEmpty()) GPhoto2Bridge.nativeExit()
        super.onDestroy()
    }
}
