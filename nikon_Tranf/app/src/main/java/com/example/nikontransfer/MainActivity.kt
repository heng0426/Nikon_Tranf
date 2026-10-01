package com.example.nikontransfer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.nikontransfer.ui.theme.NikonTransferTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    // ---- 转发到 ViewModel（Activity 重建时状态由 VM 保留）----
    private val connected get() = vm.connected
    private val connecting get() = vm.connecting
    private val selectMode get() = vm.selectMode
    private val connPhase get() = vm.connPhase
    private val connText get() = vm.connText
    private val connDetail get() = vm.connDetail
    private val connectedIp get() = vm.connectedIp
    private val showConnDetail get() = vm.showConnDetail
    private val scanResults get() = vm.scanResults
    private val scanning get() = vm.scanning
    private val scanText get() = vm.scanText
    private val photoRows get() = vm.photoRows
    private val downloadProgress get() = vm.downloadProgress
    private var uiLog: String
        get() = vm.uiLog
        set(value) { vm.uiLog = value }

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
            else vm.uiLog = "缺少本地网络权限，无法连接相机"
        }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun ensureLocalNetworkPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < 37) { action(); return }
        val perm = Manifest.permission.ACCESS_LOCAL_NETWORK
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingAction = action
            localNetPermissionLauncher.launch(perm)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyKeepScreenOn(
            getSharedPreferences("cfg", Context.MODE_PRIVATE).getBoolean("set_keep_on", false)
        )
        // FGS 通知可见性（Android 13+ 运行时权限；不授权服务照常运行）
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            NikonTransferTheme {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    /** 设置项：传输时屏幕常亮 */
    private fun applyKeepScreenOn(on: Boolean) {
        if (on) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        // 回前台体检：后台期间会话可能已被相机/系统掐掉（VM 状态跨重建存活，体检保证真实）
        vm.resumeHealthCheck()
    }

    override fun onDestroy() {
        // 仅真正退出时清理；MIUI 销毁重建 Activity 时 VM（和相机会话）继续存活
        if (isFinishing) vm.shutdown()
        super.onDestroy()
    }

    @Composable
    private fun MainScreen() {
        val prefs = LocalContext.current.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val scope = rememberCoroutineScope()
        var showSettings by remember { mutableStateOf(false) }
        var previewIndex by remember { mutableStateOf(-1) }

        // 返回键：全屏预览 → 关预览；设置页 → 回主页
        BackHandler(enabled = previewIndex >= 0) { previewIndex = -1 }
        BackHandler(enabled = showSettings) { showSettings = false }

        if (showSettings) {
            SettingsScreen(
                prefs = prefs,
                onBack = { showSettings = false },
                onKeepScreenOnChanged = { applyKeepScreenOn(it) }
            )
            return
        }

        val selMode = selectMode.value
        val selCount = photoRows.count { it.selected.value }

        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 顶部栏：浏览模式（占位logo+选择+齿轮）/ 多选模式（关闭+已选N+全选）
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!selMode) {
                        // 占位 logo（正式 logo 后续开发中提供）：圆角方块 + 镜头圆环
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFF00695C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(17.dp)
                                    .border(2.5.dp, Color.White, CircleShape)
                            )
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "选择",
                            Modifier
                                .clickable { selectMode.value = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    } else {
                        IconButton(onClick = {
                            selectMode.value = false
                            photoRows.forEach { it.selected.value = false }
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "退出选择")
                        }
                        Text(
                            "已选 ${selCount} 张",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { photoRows.forEach { it.selected.value = true } }) {
                            Text("全选")
                        }
                    }
                }
                // 连接状态条：未连接=点击就地连接；连接中=分步进度；已连接=点击显示详情
                val phase = connPhase.value
                Surface(
                    Modifier.fillMaxWidth().clickable(enabled = phase != "connecting") {
                        when (phase) {
                            "disconnected" -> ensureLocalNetworkPermission {
                                scope.launch { withContext(Dispatchers.IO) { vm.connectionFlow() } }
                            }
                            "connected" -> {
                                showConnDetail.value = !showConnDetail.value
                                if (showConnDetail.value) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            Thread.sleep(3000)
                                            showConnDetail.value = false
                                        }
                                    }
                                }
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    color = when (phase) {
                        "connected" -> Color(0xFFE0F2F1)
                        "connecting" -> Color(0xFFFFF8E1)
                        else -> Color(0xFFFFEBEE)
                    }
                ) {
                    Text(
                        connText.value,
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (phase) {
                            "connected" -> Color(0xFF00695C)
                            "connecting" -> Color(0xFF8D6E00)
                            else -> Color(0xFFB71C1C)
                        }
                    )
                }
                Text(uiLog, style = MaterialTheme.typography.bodySmall)
                if (downloadProgress.value.isNotEmpty()) {
                    Text(
                        downloadProgress.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (selMode) {
                    Button(
                        onClick = { scope.launch { withContext(Dispatchers.IO) { vm.downloadSelected() } } },
                        enabled = selCount > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (selCount > 0) "下载所选($selCount)" else "下载所选") }
                }
                if (connPhase.value != "connected" && photoRows.isEmpty()) {
                    Text(
                        "连接相机后即可浏览与下载照片\n（相机菜单 → 连接至 PC (Wi-Fi) → 建立连接）",
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF999999),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                // 照片网格：3 列，从新到旧
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(photoRows, key = { it.handle }) { row ->
                        GridCell(
                            row = row,
                            selectMode = selMode,
                            onTap = {
                                if (selMode) {
                                    row.selected.value = !row.selected.value
                                } else {
                                    previewIndex = photoRows.indexOf(row)
                                }
                            },
                            onLongPress = {
                                if (!selMode) {
                                    selectMode.value = true   // 长按直接进入多选
                                    row.selected.value = true // 并选中该张
                                } else {
                                    row.selected.value = !row.selected.value
                                }
                            }
                        )
                    }
                }
            }
            // 全屏预览（横向滑动翻页）
            if (previewIndex >= 0 && photoRows.isNotEmpty()) {
                PreviewPager(
                    initialIndex = previewIndex,
                    rows = photoRows.toList(),
                    onClose = { previewIndex = -1 },
                    onDownload = { row ->
                        scope.launch { withContext(Dispatchers.IO) { vm.downloadOne(row) } }
                    }
                )
            }
        }
    }

    /** 网格格子：预览图 + 左上格式角标 + 右下已下载标记 + 多选选中态。
     *  点按=浏览模式进全屏预览/多选模式切换选中；长按=进入多选并选中该张。 */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun GridCell(
        row: PhotoRow,
        selectMode: Boolean,
        onTap: () -> Unit,
        onLongPress: () -> Unit
    ) {
        val haptic = LocalHapticFeedback.current
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = row.preview.value
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = row.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFFE0E0E0)))
                Text(row.type, color = Color(0xFF9E9E9E), style = MaterialTheme.typography.labelSmall)
            }
            // 左上格式角标
            Text(
                row.type,
                Modifier
                    .align(Alignment.TopStart)
                    .background(badgeColor(row.type))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
            // 右下已下载标记
            if (row.downloaded.value) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color(0xCC00695C), CircleShape)
                ) {
                    Text(
                        "✓",
                        Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            // 多选选中态：绿色边框 + 中央勾
            if (selectMode && row.selected.value) {
                Box(Modifier.fillMaxSize().background(Color(0x3300695C)))
                Box(Modifier.fillMaxSize().border(3.dp, Color(0xFF00695C)))
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已选择",
                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                    tint = Color.White
                )
            }
        }
    }

    /** 全屏预览：大图 + 横向滑动翻页 + 单张下载 + 加入选择 */
    @Composable
    private fun PreviewPager(
        initialIndex: Int,
        rows: List<PhotoRow>,
        onClose: () -> Unit,
        onDownload: (PhotoRow) -> Unit
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex) { rows.size }
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                    }
                    Text(
                        "${pagerState.currentPage + 1} / ${rows.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.width(48.dp))
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { page ->
                    val row = rows[page]
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val bmp = row.preview.value
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = row.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    row.type,
                                    color = Color(0xFF9E9E9E),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "缩略图加载中…",
                                    color = Color(0xFF777777),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                // 底部信息 + 操作
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    val row = rows[pagerState.currentPage]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.type,
                            Modifier
                                .background(badgeColor(row.type))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            prettyStamp(row.stamp),
                            color = Color(0xFFAAAAAA),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.weight(1f))
                        if (row.downloaded.value)
                            Text(
                                "已下载 ✓",
                                color = Color(0xFF4DB6AC),
                                style = MaterialTheme.typography.labelSmall
                            )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onDownload(row) },
                            enabled = !row.downloaded.value,
                            modifier = Modifier.weight(1f)
                        ) { Text(if (row.downloaded.value) "已下载" else "下载此照片") }
                        OutlinedButton(
                            onClick = { row.selected.value = !row.selected.value },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (row.selected.value) "已加入选择" else "加入选择",
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsScreen(
        prefs: android.content.SharedPreferences,
        onBack: () -> Unit,
        onKeepScreenOnChanged: (Boolean) -> Unit
    ) {
        var jpgOnly by remember { mutableStateOf(prefs.getBoolean("set_jpg_only", false)) }
        var keepOn by remember { mutableStateOf(prefs.getBoolean("set_keep_on", false)) }
        var autoPreview by remember { mutableStateOf(prefs.getBoolean("set_auto_preview", true)) }
        val scope = rememberCoroutineScope()

        Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("设置", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text("连接", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            val (cm, cs) = if (connDetail.value.isNotEmpty()) vm.splitInfo(connDetail.value) else ("Nikon" to "?")
            Text(
                if (connPhase.value == "connected") "已连接：$cm ($cs) · ${connectedIp.value}" else "未连接",
                style = MaterialTheme.typography.bodyMedium,
                color = if (connPhase.value == "connected") Color(0xFF00695C) else Color(0xFFB71C1C)
            )
            Button(
                onClick = {
                    ensureLocalNetworkPermission {
                        scope.launch {
                            withContext(Dispatchers.IO) { vm.scanForCameras() }
                        }
                    }
                },
                enabled = !scanning.value && !connecting
            ) { Text(if (scanning.value) scanText.value else "扫描相机") }
            for ((ip, info) in scanResults) {
                val (model, serial) = vm.splitInfo(info)
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = !connecting) {
                            ensureLocalNetworkPermission {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { vm.connectToCamera(ip, info) }
                                    if (ok) {
                                        kotlinx.coroutines.delay(800)   // 显示 ✓ 已连接，随后自动返回主界面
                                        onBack()
                                    }
                                }
                            }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("$model ($serial)", style = MaterialTheme.typography.bodyLarge)
                        Text(ip, style = MaterialTheme.typography.bodySmall, color = Color(0xFF888888))
                    }
                    if (connected && ip == connectedIp.value)
                        Text("已连接 ✓", color = Color(0xFF00695C), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("传输", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            SettingSwitch(
                title = "批量下载只取 JPG",
                subtitle = "成对照片（RAW+JPG）只下载 JPG，自动跳过 NEF",
                checked = jpgOnly,
                onChange = {
                    jpgOnly = it
                    prefs.edit().putBoolean("set_jpg_only", it).apply()
                }
            )
            SettingSwitch(
                title = "连接后自动加载预览",
                subtitle = "关闭后只列文件名，节省流量与时间",
                checked = autoPreview,
                onChange = {
                    autoPreview = it
                    prefs.edit().putBoolean("set_auto_preview", it).apply()
                }
            )
            SettingSwitch(
                title = "传输时保持屏幕常亮",
                subtitle = "大批量下载时防止锁屏中断",
                checked = keepOn,
                onChange = {
                    keepOn = it
                    prefs.edit().putBoolean("set_keep_on", it).apply()
                    onKeepScreenOnChanged(it)
                }
            )
            Spacer(Modifier.height(16.dp))
            Text("关于", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                "尼康 Z 系列 Wi-Fi 传图 · 配对模式原生协议\n" +
                    "协议：PTP/IP + Nikon 私有指令（0x941c/0x9421/0x9431/0x9434/0x952b/0x935a）\n" +
                    "注意：配对模式下相机不提供原始文件名，列表名称由拍摄时间+句柄生成",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF888888)
            )
        }
    }

    @Composable
    private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF888888))
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
