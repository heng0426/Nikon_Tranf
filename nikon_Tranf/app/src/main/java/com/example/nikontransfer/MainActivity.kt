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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Path
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

    /** 自定义存储目录：SAF 文件夹选择器，授权持久化后写入 VM */
    private val dirPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Throwable) {
            }
            vm.setCustomDir(uri.toString())
        }
    }

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
        var pairPreviewIndex by remember { mutableStateOf(-1) }

        // 返回键：全屏预览 → 关预览；设置页 → 回主页
        BackHandler(enabled = pairPreviewIndex >= 0) { pairPreviewIndex = -1 }
        BackHandler(enabled = previewIndex >= 0) { previewIndex = -1 }
        BackHandler(enabled = showSettings) { showSettings = false }

        if (showSettings) {
            SettingsScreen(
                prefs = prefs,
                onBack = { showSettings = false },
                onKeepScreenOnChanged = { applyKeepScreenOn(it) },
                onPickDir = { dirPicker.launch(null) }
            )
            return
        }

        val selMode = selectMode.value
        val visible = vm.visiblePhotos
        var showFilter by remember { mutableStateOf(false) }
        val mergeOn = vm.mergePairs.value
        val pairs = vm.visiblePairs
        val pairSelCount = vm.pairSelection.value.size
        val selCount = if (mergeOn) pairSelCount else photoRows.count { it.selected.value }

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
                        // 筛选按钮（logo 右侧，同款圆角外框）：有筛选生效时漏斗变色
                        Box(
                            Modifier
                                .padding(start = 10.dp)
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFFF1F3F5))
                                .clickable { showFilter = true },
                            contentAlignment = Alignment.Center
                        ) {
                            FunnelIcon(if (vm.filterActive) Color(0xFF00695C) else Color(0xFF9E9E9E))
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
                            vm.clearSelection()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "退出选择")
                        }
                        Text(
                            "已选 ${selCount} 张",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            if (mergeOn) vm.pairSelection.value = pairs.map { it.stamp }.toSet()
                            else visible.forEach { it.selected.value = true }
                        }) {
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
                    if (mergeOn) {
                        // 合并模式：批量下载的格式勾选（默认只勾 JPG）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "下载格式",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF888888)
                            )
                            LabeledCheckbox("JPG", vm.batchFmtJpg)
                            LabeledCheckbox("NEF", vm.batchFmtNef)
                        }
                    }
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
                val emptyByFilter = if (mergeOn) photoRows.isNotEmpty() && pairs.isEmpty()
                                    else photoRows.isNotEmpty() && visible.isEmpty()
                if (emptyByFilter) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "无符合筛选条件的照片",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF999999)
                        )
                        TextButton(onClick = { vm.resetFilter() }) { Text("清除筛选") }
                    }
                }
                // 照片网格：按日期分节（节头占满一行），组内从新到旧；合并模式一格=一对
                if (mergeOn) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        vm.pairSections.forEach { sec ->
                            item(key = "phdr_${sec.dateKey}", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    vm.dateLabel(sec.dateKey, sec.rows.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFF444444),
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 2.dp)
                                )
                            }
                            items(sec.rows, key = { it.stamp }) { pair ->
                                PairCell(
                                    pair = pair,
                                    selectMode = selMode,
                                    onTap = {
                                        if (selMode) {
                                            vm.togglePair(pair.stamp)
                                        } else {
                                            pairPreviewIndex = pairs.indexOf(pair)
                                        }
                                    },
                                    onLongPress = {
                                        if (!selMode) {
                                            selectMode.value = true
                                            if (pair.stamp !in vm.pairSelection.value) vm.togglePair(pair.stamp)
                                        } else {
                                            vm.togglePair(pair.stamp)
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    vm.visibleSections.forEach { sec ->
                        item(key = "hdr_${sec.dateKey}", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                vm.dateLabel(sec.dateKey, sec.rows.size),
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF444444),
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 2.dp)
                            )
                        }
                        items(sec.rows, key = { it.handle }) { row ->
                            GridCell(
                                row = row,
                                selectMode = selMode,
                                onTap = {
                                    if (selMode) {
                                        row.selected.value = !row.selected.value
                                    } else {
                                        previewIndex = visible.indexOf(row)
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
                } // else: 文件模式网格结束
            }
            // 全屏预览（横向滑动翻页，只在筛选结果内翻；合并模式翻合并对）
            if (mergeOn && pairPreviewIndex >= 0 && pairs.isNotEmpty()) {
                PairPager(
                    initialIndex = pairPreviewIndex,
                    rows = pairs.toList(),
                    onClose = { pairPreviewIndex = -1 },
                    onDownloadJpg = { row ->
                        row.jpg?.let { p ->
                            scope.launch { withContext(Dispatchers.IO) { vm.downloadOne(p) } }
                        }
                    },
                    onDownloadNef = { row ->
                        row.nef?.let { n ->
                            scope.launch { withContext(Dispatchers.IO) { vm.downloadOne(n) } }
                        }
                    }
                )
            } else if (!mergeOn && previewIndex >= 0 && visible.isNotEmpty()) {
                PreviewPager(
                    initialIndex = previewIndex,
                    rows = visible.toList(),
                    onClose = { previewIndex = -1 },
                    onDownload = { row ->
                        scope.launch { withContext(Dispatchers.IO) { vm.downloadOne(row) } }
                    }
                )
            }
            // 筛选卡片（底部弹出，实时生效）
            if (showFilter) FilterSheet(onDismiss = { showFilter = false })
        }
    }

    /** 合并格：JPG+NEF 一对一格，双格式徽章 + ✓J/✓N 独立下载标记 + 选中态 */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun PairCell(
        pair: PairRow,
        selectMode: Boolean,
        onTap: () -> Unit,
        onLongPress: () -> Unit
    ) {
        val haptic = LocalHapticFeedback.current
        val selected = pair.stamp in vm.pairSelection.value
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
            val bmp = pair.previewBmp
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = pair.stamp,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFFE0E0E0)))
                Text("RAW+JPG", color = Color(0xFF9E9E9E), style = MaterialTheme.typography.labelSmall)
            }
            // 左上格式徽章：JPG(青) + NEF(紫) 相邻
            Row(Modifier.align(Alignment.TopStart)) {
                if (pair.hasJpg) MiniBadge("JPG", badgeColor("JPG"))
                if (pair.hasNef) MiniBadge("NEF", badgeColor("NEF"))
            }
            // 右下已下载标记：✓J / ✓N 各自独立点亮
            Row(
                Modifier.align(Alignment.BottomEnd).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (pair.jpgDownloaded) DLBadge("J", Color(0xFF00695C))
                if (pair.nefDownloaded) DLBadge("N", Color(0xFF6A1B9A))
            }
            if (selectMode && selected) {
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

    /** 小徽章（合并格左上角格式标注） */
    @Composable
    private fun MiniBadge(text: String, bg: Color) {
        Text(
            text,
            Modifier
                .background(bg, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }

    /** 已下载小圆标（✓J / ✓N） */
    @Composable
    private fun DLBadge(label: String, bg: Color) {
        Box(
            Modifier
                .background(bg, CircleShape)
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text("✓$label", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }

    /** 合并模式全屏预览：JPG 大图 + 双格式下载按钮 + 加入选择 */
    @Composable
    private fun PairPager(
        initialIndex: Int,
        rows: List<PairRow>,
        onClose: () -> Unit,
        onDownloadJpg: (PairRow) -> Unit,
        onDownloadNef: (PairRow) -> Unit
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
                    val pair = rows[page]
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val bmp = pair.previewBmp
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = pair.stamp,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "RAW+JPG",
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
                // 底部信息 + 双格式下载
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    val pair = rows[pagerState.currentPage]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (pair.hasJpg) MiniBadge("JPG", badgeColor("JPG"))
                        if (pair.hasNef) {
                            Spacer(Modifier.width(4.dp))
                            MiniBadge("NEF", badgeColor("NEF"))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            prettyStamp(pair.stamp),
                            color = Color(0xFFAAAAAA),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.weight(1f))
                        if (pair.jpgDownloaded || pair.nefDownloaded) {
                            val marks = buildString {
                                if (pair.jpgDownloaded) append("✓J ")
                                if (pair.nefDownloaded) append("✓N")
                            }.trim()
                            Text(marks, color = Color(0xFF4DB6AC), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onDownloadJpg(pair) },
                            enabled = pair.hasJpg && !pair.jpgDownloaded,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                when {
                                    !pair.hasJpg -> "无 JPG"
                                    pair.jpgDownloaded -> "JPG 已下载"
                                    else -> "下载 JPG"
                                }
                            )
                        }
                        Button(
                            onClick = { onDownloadNef(pair) },
                            enabled = pair.hasNef && !pair.nefDownloaded,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                when {
                                    !pair.hasNef -> "无 NEF"
                                    pair.nefDownloaded -> "NEF 已下载"
                                    else -> "下载 NEF"
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val pairSelected = pair.stamp in vm.pairSelection.value
                    OutlinedButton(
                        onClick = { vm.togglePair(pair.stamp) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (pairSelected) "已加入选择" else "加入选择",
                            color = Color.White
                        )
                    }
                }
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
                    .background(badgeColor(row.type), RoundedCornerShape(4.dp))
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

    /** 漏斗图标（material-icons-core 无 FilterList，用 Path 手绘避免引 extended 大依赖） */
    @Composable
    private fun FunnelIcon(color: Color, modifier: Modifier = Modifier) {
        Canvas(modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val p = Path().apply {
                moveTo(0f, h * 0.06f)
                lineTo(w, h * 0.06f)
                lineTo(w * 0.60f, h * 0.52f)
                lineTo(w * 0.60f, h)
                lineTo(w * 0.40f, h * 0.84f)
                lineTo(w * 0.40f, h * 0.52f)
                close()
            }
            drawPath(p, color)
        }
    }

    /** 筛选卡片：文件类型(单选) / 状态(未传+已传独立chip) / 拍摄日期(起止)。
     *  实时生效，右上角重置；样式对照用户提供的参考图（去掉连拍与保护）。 */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FilterSheet(onDismiss: () -> Unit) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FunnelIcon(MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "筛选",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.resetFilter() }) { Text("重置") }
                }
                Spacer(Modifier.height(6.dp))
                // 合并模式下每个格子已同时呈现 JPG+NEF，格式筛选行隐藏（①b）
                if (!vm.mergePairs.value) {
                    Text("文件类型", style = MaterialTheme.typography.titleSmall, color = Color(0xFF888888))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterBigButton("全部", vm.filterFormat.value == "全部", Modifier.weight(1f)) {
                            vm.filterFormat.value = "全部"
                        }
                        FilterBigButton("JPG", vm.filterFormat.value == "JPG", Modifier.weight(1f)) {
                            vm.filterFormat.value = "JPG"
                        }
                        FilterBigButton("NEF", vm.filterFormat.value == "NEF", Modifier.weight(1f)) {
                            vm.filterFormat.value = "NEF"
                        }
                    }
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Color(0xFFEEEEEE),
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                }
                Text("下载状态", style = MaterialTheme.typography.titleSmall, color = Color(0xFF888888))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StateChip("未下载", vm.filterUntransferred.value, Modifier.weight(1f)) {
                        vm.filterUntransferred.value = it
                    }
                    StateChip("已下载", vm.filterDownloaded.value, Modifier.weight(1f)) {
                        vm.filterDownloaded.value = it
                    }
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFFEEEEEE),
                    modifier = Modifier.padding(vertical = 14.dp)
                )
                Text("拍摄日期", style = MaterialTheme.typography.titleSmall, color = Color(0xFF888888))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateField(
                        label = "开始",
                        value = vm.filterStart.value,
                        onSet = { vm.filterStart.value = it },
                        onClear = { vm.filterStart.value = null },
                        modifier = Modifier.weight(1f)
                    )
                    DateField(
                        label = "结束",
                        value = vm.filterEnd.value,
                        onSet = { vm.filterEnd.value = it },
                        onClear = { vm.filterEnd.value = null },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    /** 带标签的勾选框（多选下载格式选择） */
    @Composable
    private fun LabeledCheckbox(label: String, state: androidx.compose.runtime.MutableState<Boolean>) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { state.value = !state.value }
        ) {
            Checkbox(checked = state.value, onCheckedChange = { state.value = it })
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }

    /** 文件类型大按钮（选中=主题色实底白字，未选=浅灰底） */
    @Composable
    private fun FilterBigButton(
        text: String,
        selected: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        Box(
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) Color(0xFF00695C) else Color(0xFFF1F3F5))
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (selected) Color.White else Color(0xFF333333),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    /** 状态 chip（未下载/已下载）：独立点亮，与文件类型按钮同宽（一排两个） */
    @Composable
    private fun StateChip(
        text: String,
        on: Boolean,
        modifier: Modifier = Modifier,
        onToggle: (Boolean) -> Unit
    ) {
        Box(
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (on) Color(0xFF00695C) else Color(0xFFF1F3F5))
                .clickable { onToggle(!on) }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (on) Color.White else Color(0xFF333333),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    /** 日期字段：显示标签 + 当前值（"不限"或 yyyy-MM-dd），点按弹日历，有值时带清除 */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun DateField(
        label: String,
        value: String?,
        onSet: (String) -> Unit,
        onClear: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        var open by remember { mutableStateOf(false) }
        Box(
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF1F3F5))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF888888)
                    )
                    Text(
                        if (value != null && value.length == 8)
                            "${value.substring(0, 4)}-${value.substring(4, 6)}-${value.substring(6, 8)}"
                        else "不限",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (value != null) Color(0xFF222222) else Color(0xFF999999)
                    )
                }
                if (value != null) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "清除$label",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onClear() },
                        tint = Color(0xFF999999)
                    )
                }
            }
        }
        if (open) {
            val init = value?.let { keyToMillis(it) }
            val st = rememberDatePickerState(initialSelectedDateMillis = init)
            DatePickerDialog(
                onDismissRequest = { open = false },
                confirmButton = {
                    TextButton(onClick = {
                        st.selectedDateMillis?.let { onSet(millisToKey(it)) }
                        open = false
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { open = false }) { Text("取消") }
                }
            ) {
                DatePicker(state = st)
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
                                .background(badgeColor(row.type), RoundedCornerShape(4.dp))
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
        onKeepScreenOnChanged: (Boolean) -> Unit,
        onPickDir: () -> Unit
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
                title = "合并 RAW+JPG 展示",
                subtitle = "同一时间拍摄的 JPG 与 NEF 合并为一格，预览页/批量下载可分别选格式",
                checked = vm.mergePairs.value,
                onChange = { vm.setMergePairs(it) }
            )
            SettingSwitch(
                title = "批量下载只取 JPG",
                subtitle = "成对照片（RAW+JPG）只下载 JPG，自动跳过 NEF（未开启合并时生效）",
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
            Text("存储", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            SettingSwitch(
                title = "按拍摄日期文件夹保存",
                subtitle = "JPG 与 NEF 存入同一拍摄日期文件夹（如 2026-10-01），取自相机时间",
                checked = vm.dateFolderOn.value,
                onChange = { vm.setDateFolder(it) }
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("存储目录", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        vm.dirDisplay.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF888888)
                    )
                }
                TextButton(onClick = onPickDir) { Text("更改目录") }
                if (vm.customDirUri.value != null) {
                    TextButton(onClick = { vm.setCustomDir(null) }) { Text("恢复默认") }
                }
            }
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

/** DatePicker 毫秒 → "YYYYMMDD"（与照片时间戳前 8 位同构，可直接字符串比较） */
private fun millisToKey(ms: Long): String {
    val d = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return "%04d%02d%02d".format(d.year, d.monthValue, d.dayOfMonth)
}

/** "YYYYMMDD" → DatePicker 毫秒（当天 0 点） */
private fun keyToMillis(key: String): Long? = try {
    java.time.LocalDate.of(
        key.substring(0, 4).toInt(),
        key.substring(4, 6).toInt(),
        key.substring(6, 8).toInt()
    ).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
} catch (_: Exception) {
    null
}
