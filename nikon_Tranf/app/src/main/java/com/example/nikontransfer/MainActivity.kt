package com.example.nikontransfer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.nikontransfer.ui.theme.NikonTransferTheme
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import my.nanihadesuka.compose.InternalLazyVerticalGridScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    // ---- 转发到 ViewModel（Activity 重建时状态由 VM 保留）----
    private val connected get() = vm.connected
    private val connecting get() = vm.connecting
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
            NikonTransferTheme(dark = vm.darkModeOn) {
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
        var showDownloads by remember { mutableStateOf(false) }
        var previewIndex by remember { mutableStateOf(-1) }
        var pairPreviewIndex by remember { mutableStateOf(-1) }
        // 预览返回高亮：记住刚预览的那张（句柄/时间戳），返回网格时脉冲提示
        var highlightHandle by remember { mutableStateOf<Int?>(null) }
        var highlightStamp by remember { mutableStateOf<String?>(null) }
        val mergeGridState = rememberLazyGridState()
        val fileGridState = rememberLazyGridState()
        // 滑动感知暂停：网格滚动时通知缩略图 loader 挂起重活（停止后自动继续）
        LaunchedEffect(mergeGridState, fileGridState) {
            snapshotFlow { mergeGridState.isScrollInProgress || fileGridState.isScrollInProgress }
                .collect { vm.gridScrolling = it }
        }

        // 返回键：全屏预览 → 关预览；下载页/设置页 → 回主页
        BackHandler(enabled = pairPreviewIndex >= 0) { pairPreviewIndex = -1 }
        BackHandler(enabled = previewIndex >= 0) { previewIndex = -1 }
        BackHandler(enabled = showDownloads) { showDownloads = false }
        BackHandler(enabled = showSettings) { showSettings = false }

        // 打开 App 自动连接（设置项，默认关）：仅未连接且热点已开启时触发；
        // 热点未开静默跳过不打扰；已连接/连接中（含 MIUI 重建后 VM 会话存活）不重复触发
        LaunchedEffect(Unit) {
            if (prefs.getBoolean("set_auto_connect", false) &&
                !connected && !connecting && connPhase.value == "disconnected"
            ) {
                if (vm.isHotspotOn()) {
                    ensureLocalNetworkPermission {
                        scope.launch { withContext(Dispatchers.IO) { vm.connectionFlow() } }
                    }
                } else {
                    Log.i("GPhoto2", "自动连接：热点未开启，跳过")
                }
            }
        }

        // 深浅色切换时同步系统栏图标颜色（深色=白图标，浅色=黑图标）
        LaunchedEffect(vm.darkModeOn) {
            if (vm.darkModeOn) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                )
            } else {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                    ),
                    navigationBarStyle = SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                    )
                )
            }
        }

        val visible = vm.visiblePhotos
        var showFilter by remember { mutableStateOf(false) }
        var hotspotHint by remember { mutableStateOf(false) }
        val mergeOn = vm.mergePairs.value
        val pairs = vm.visiblePairs
        val pairSelCount = vm.pairSelection.value.size
        val selCount = if (mergeOn) pairSelCount else photoRows.count { it.selected.value }

        // 毛玻璃：预览打开时主界面内容实时模糊（API31+，低版本自动退化为半透明黑）
        val previewing = (mergeOn && pairPreviewIndex >= 0) || (!mergeOn && previewIndex >= 0)
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        if (previewing && Build.VERSION.SDK_INT >= 31) {
                            renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(24f, 24f, android.graphics.Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        } else {
                            renderEffect = null
                        }
                    }
                    // ★ 顶栏高度只改这里：top 的数字越大越往下，越小越往上，0 = 紧贴状态栏
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .padding(top = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val phase = connPhase.value
                // ★ 日期胶囊槽位高度：只改这个数字——数字越小，胶囊下方留白越小（建议 24~30）
                val pillSlotHeight = 20.dp
                // 连接详情切换：显示 3 秒后自动收起（重复点击取消旧计时，避免卡片被旧计时器提前收起）
                var detailHideJob by remember { mutableStateOf<Job?>(null) }
                val toggleConnDetail = {
                    detailHideJob?.cancel()
                    showConnDetail.value = !showConnDetail.value
                    if (showConnDetail.value) {
                        detailHideJob = scope.launch {
                            delay(3000)
                            showConnDetail.value = false
                        }
                    }
                }
                // 断开瞬间图标抖动一次提示（仅连接成功过之后断开）
                val connShake = remember { Animatable(0f) }
                LaunchedEffect(phase) {
                    if (phase == "disconnected" && vm.everConnected) {
                        connShake.snapTo(0f)
                        connShake.animateTo(
                            1f,
                            keyframes {
                                durationMillis = 450
                                -1f at 80
                                1f at 160
                                -0.7f at 240
                                0.7f at 320
                                0f at 450
                            }
                        )
                    }
                }
                // 顶部栏：logo + 筛选 + 队列（多选操作全部在底部弹出条，顶栏不再有模式切换）
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // 顶部 logo（用户提供图片，透明底 PNG）：点击进入设置
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = "logo",
                        modifier = Modifier
                            .height(34.dp)
                            .clickable { showSettings = true }
                    )
                    // 筛选按钮（logo 右侧，同款圆角外框）：有筛选生效时漏斗变色
                    Box(
                        Modifier
                            .padding(start = 10.dp)
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showFilter = true },
                        contentAlignment = Alignment.Center
                    ) {
                        FunnelIcon(if (vm.filterActive) Color(0xFF00695C) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // 连接指示（常驻，筛选旁，同款圆角外框）：绿=已连接(点看详情)；黄=连接中(禁点)；红=断开(点击重连)
                    val red = phase == "disconnected"
                    val amber = phase == "connecting"
                    Box(
                        Modifier
                            .padding(start = 10.dp)
                            .graphicsLayer { translationX = connShake.value * 6.dp.toPx() }
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = !amber) {
                                if (red) {
                                    // 重试连接：热点检查 + 权限 + 完整连接流程
                                    if (vm.isHotspotOn()) {
                                        ensureLocalNetworkPermission {
                                            scope.launch {
                                                withContext(Dispatchers.IO) { vm.connectionFlow() }
                                            }
                                        }
                                    } else {
                                        Log.i("GPhoto2", "热点未开启，弹窗提示")
                                        hotspotHint = true
                                    }
                                } else toggleConnDetail()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val iconColor by animateColorAsState(
                            when {
                                red -> if (vm.darkModeOn) Color(0xFFEF9A9A) else Color(0xFFB71C1C)
                                amber -> if (vm.darkModeOn) Color(0xFFFFD54F) else Color(0xFF8D6E00)
                                vm.darkModeOn -> Color(0xFF4DB6AC)
                                else -> Color(0xFF00695C)
                            },
                            animationSpec = tween(200),
                            label = "connIconColor"
                        )
                        WifiIcon(iconColor, Modifier.size(20.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    // 下载队列按钮（筛选/连接同款圆角外框）：下载图标 + 活跃任务数量角标
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showDownloads = true },
                        contentAlignment = Alignment.Center
                    ) {
                        BadgedBox(badge = {
                            if (vm.activeDownloadCount > 0) {
                                Badge { Text("${vm.activeDownloadCount}") }
                            }
                        }) {
                            DownloadIcon(
                                if (vm.activeDownloadCount > 0)
                                    if (vm.darkModeOn) Color(0xFF4DB6AC) else Color(0xFF00695C)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // 详情浮层 + 状态条收纳进内层 Column（无 spacedBy：卡片移除时不会带走间距导致下方跳动）
                Column {
                // 连接详情浮层：点击 Wi-Fi 图标后显示 3 秒（展开+淡入 / 收起+淡出）
                AnimatedVisibility(
                    visible = showConnDetail.value && phase == "connected",
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(220)),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(180))
                ) {
                    val (cm, cs) = if (connDetail.value.isNotEmpty()) vm.splitInfo(connDetail.value)
                    else ("Nikon" to "?")
                    Surface(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = if (vm.darkModeOn) Color(0xFF10312D) else Color(0xFFE0F2F1)
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                "已连接：$cm ($cs)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (vm.darkModeOn) Color(0xFF4DB6AC) else Color(0xFF00695C)
                            )
                            Text(
                                "IP：${connectedIp.value}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // 连接状态条：冷启动未连接=点击就地连接；连接中=分步进度；已连接/断开重连=收起为标题栏图标
                val showConnBar = phase == "connecting" ||
                    (phase == "disconnected" && !vm.everConnected)
                AnimatedVisibility(
                    visible = showConnBar,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(250)),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(200))
                ) {
                Surface(
                    Modifier.fillMaxWidth().clickable(enabled = phase != "connecting") {
                        if (phase == "disconnected") {
                            if (vm.isHotspotOn()) {
                                ensureLocalNetworkPermission {
                                    scope.launch { withContext(Dispatchers.IO) { vm.connectionFlow() } }
                                }
                            } else {
                                Log.i("GPhoto2", "热点未开启，弹窗提示")
                                hotspotHint = true
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    color = when (phase) {
                        "connected" -> if (vm.darkModeOn) Color(0xFF10312D) else Color(0xFFE0F2F1)
                        "connecting" -> if (vm.darkModeOn) Color(0xFF332B12) else Color(0xFFFFF8E1)
                        else -> if (vm.darkModeOn) Color(0xFF38201F) else Color(0xFFFFEBEE)
                    }
                ) {
                    Text(
                        connText.value,
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (phase) {
                            "connected" -> if (vm.darkModeOn) Color(0xFF4DB6AC) else Color(0xFF00695C)
                            "connecting" -> if (vm.darkModeOn) Color(0xFFFFD54F) else Color(0xFF8D6E00)
                            else -> if (vm.darkModeOn) Color(0xFFEF9A9A) else Color(0xFFB71C1C)
                        }
                    )
                }
                }
                }
                // "共 N 个文件"纯文字计数行不显示（总数已在置顶胶囊中）
                if (uiLog.isNotBlank() && !uiLog.contains("个文件")) {
                    Text(uiLog, style = MaterialTheme.typography.bodySmall)
                }
                if (connPhase.value != "connected" && photoRows.isEmpty()) {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            WifiIcon(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(44.dp))
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "连接相机后即可浏览与下载照片",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "相机菜单 → 连接至 PC (Wi-Fi) → 建立连接",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { vm.resetFilter() }) { Text("清除筛选") }
                    }
                }
                // 快速滚动条（LazyColumnScrollbar 库）：>50 张启用；滚动时自动显现。
                // 拖动映射自管：库的映射假设每行等高，日期头/照片行高悬殊导致交界跳动——
                // 这里禁用库手势（Disabled），另叠像素级 scrollBy 拖动层，增量滚动无跳变。
                val sbSettings = ScrollbarSettings(
                    selectionMode = ScrollbarSelectionMode.Disabled,
                    thumbThickness = 9.dp,
                    scrollbarPadding = 3.dp,
                    thumbMinLength = 0.05f,
                    thumbUnselectedColor = if (vm.darkModeOn) Color(0xFF4DB6AC) else Color(0xFF00695C),
                    thumbSelectedColor = if (vm.darkModeOn) Color(0xFF80CBC4) else Color(0xFF00897B),
                    hideDelayMillis = 600
                )
                // 拖动滚动条期间强制常显：scrollBy 事件间隙 isScrollInProgress 抖动会导致渐隐
                var sbDragging by remember { mutableStateOf(false) }
                val sbActiveSettings = if (sbDragging) sbSettings.copy(alwaysShowScrollbar = true) else sbSettings
                // 照片网格：按日期分节（节头占满一行），组内从新到旧；合并模式一格=一对
                if (mergeOn) {
                    // 置顶日期胶囊：固定槽位显示当前分组（随滚动更新，不与照片重叠）+ 总张数胶囊
                    // derivedStateOf：滑动中每帧的 layoutInfo 变化仅在跨分组时才输出新值，
                    // 避免 MainScreen 每帧重组（那会让所有可见格子跟着重建 → 滑动卡顿）
                    val pillSec by remember(vm.pairSections) {
                        derivedStateOf {
                            val firstIdx = mergeGridState.layoutInfo.visibleItemsInfo.minOfOrNull { it.index }
                                ?: return@derivedStateOf null
                            var acc = 0
                            var key: String? = null
                            for (sec in vm.pairSections) {
                                if (firstIdx >= acc && firstIdx <= acc + sec.rows.size) { key = sec.dateKey; break }
                                acc += 1 + sec.rows.size
                            }
                            vm.pairSections.firstOrNull { it.dateKey == key }
                        }
                    }
                    val sec = pillSec   // 委托属性无法 smart cast，先固化局部值
                    Box(Modifier.fillMaxWidth().height(pillSlotHeight), contentAlignment = Alignment.CenterStart) {
                        if (sec != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                                ) {
                                    Text(
                                        vm.dateLabel(sec.dateKey, sec.rows.size),
                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                                ) {
                                    Text(
                                        "共 ${pairs.size} 张",
                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    // 免疫无障碍扫描：GKD/记账类服务监听 CONTENT_CHANGED 且全树遍历坐标，
                    // 网格几百节点 × 每帧滚动 = 主线程每帧 6-17ms 语义计算（实测 trace 热点）。
                    // 清空网格子树语义后对外只剩 1 个空节点，服务扫描成本归零（工具类 app 可接受）。
                    Box(Modifier.fillMaxWidth().weight(1f).clearAndSetSemantics { }) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        state = mergeGridState,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        vm.pairSections.forEach { sec ->
                            item(key = "phdr_${sec.dateKey}", span = { GridItemSpan(maxLineSpan) }, contentType = "hdr") {
                                Row {
                                    Surface(
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            vm.dateLabel(sec.dateKey, sec.rows.size),
                                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            items(sec.rows, key = { it.stamp }, contentType = { "photo" }) { pair ->
                                PairCell(
                                    pair = pair,
                                    highlight = pair.stamp == highlightStamp,
                                    onTap = {
                                        highlightHandle = null
                                        highlightStamp = null
                                        pairPreviewIndex = pairs.indexOf(pair)
                                    },
                                    onLongPress = { vm.togglePair(pair.stamp) }
                                )
                            }
                        }
                    }
                    if (pairs.size > 50) {
                        // 显示层：库滚动条（拖块位置/动画/日期气泡），手势已禁用
                        InternalLazyVerticalGridScrollbar(
                            state = mergeGridState,
                            modifier = Modifier.fillMaxSize(),
                            settings = sbActiveSettings,
                            indicatorContent = { idx, _ ->
                                var acc = 0
                                var d: String? = null
                                for (sec in vm.pairSections) {
                                    if (idx >= acc && idx <= acc + sec.rows.size) {
                                        d = vm.dateLabel(sec.dateKey, sec.rows.size)
                                        break
                                    }
                                    acc += 1 + sec.rows.size
                                }
                                if (d != null) DateBubble(d)
                            }
                        )
                        // 拖动层：右缘窄条，像素级比例滚动（平滑，无交界跳动）
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(24.dp)
                                .pointerInput(mergeGridState) {
                                    detectVerticalDragGestures(
                                        onDragStart = { sbDragging = true },
                                        onDragEnd = { sbDragging = false },
                                        onDragCancel = { sbDragging = false },
                                        onVerticalDrag = { change, dy ->
                                            change.consume()
                                            val info = mergeGridState.layoutInfo
                                            val vis = info.visibleItemsInfo
                                            if (vis.isNotEmpty()) {
                                                val avg = vis.sumOf { it.size.height.toDouble() } / vis.size
                                                val totalPx = avg * info.totalItemsCount / 3.0   // 3 列
                                                if (totalPx > 0) {
                                                    val scale = totalPx / size.height
                                                    scope.launch {
                                                        mergeGridState.scrollBy((dy * scale).toFloat())
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                        )
                    }
                    }
                } else {
                    // 置顶日期胶囊：固定槽位显示当前分组（随滚动更新，不与照片重叠）+ 总张数胶囊
                    // derivedStateOf：滑动中每帧的 layoutInfo 变化仅在跨分组时才输出新值，
                    // 避免 MainScreen 每帧重组（那会让所有可见格子跟着重建 → 滑动卡顿）
                    val pillSec by remember(vm.visibleSections) {
                        derivedStateOf {
                            val firstIdx = fileGridState.layoutInfo.visibleItemsInfo.minOfOrNull { it.index }
                                ?: return@derivedStateOf null
                            var acc = 0
                            var key: String? = null
                            for (sec in vm.visibleSections) {
                                if (firstIdx >= acc && firstIdx <= acc + sec.rows.size) { key = sec.dateKey; break }
                                acc += 1 + sec.rows.size
                            }
                            vm.visibleSections.firstOrNull { it.dateKey == key }
                        }
                    }
                    val sec = pillSec   // 委托属性无法 smart cast，先固化局部值
                    Box(Modifier.fillMaxWidth().height(pillSlotHeight), contentAlignment = Alignment.CenterStart) {
                        if (sec != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                                ) {
                                    Text(
                                        vm.dateLabel(sec.dateKey, sec.rows.size),
                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                                ) {
                                    Text(
                                        "共 ${visible.size} 张",
                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    // 免疫无障碍扫描（同合并模式）：语义子树清空，服务扫描成本归零
                    Box(Modifier.fillMaxWidth().weight(1f).clearAndSetSemantics { }) {
                    LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    state = fileGridState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    vm.visibleSections.forEach { sec ->
                        item(key = "hdr_${sec.dateKey}", span = { GridItemSpan(maxLineSpan) }, contentType = "hdr") {
                            Row {
                                Surface(
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        vm.dateLabel(sec.dateKey, sec.rows.size),
                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        items(sec.rows, key = { it.handle }, contentType = { "photo" }) { row ->
                            GridCell(
                                row = row,
                                highlight = row.handle == highlightHandle,
                                onTap = {
                                    highlightHandle = null
                                    highlightStamp = null
                                    previewIndex = visible.indexOf(row)
                                },
                                onLongPress = { row.selected.value = !row.selected.value }
                            )
                        }
                    }
                }
                    if (visible.size > 50) {
                        // 显示层：库滚动条（拖块位置/动画/日期气泡），手势已禁用
                        InternalLazyVerticalGridScrollbar(
                            state = fileGridState,
                            modifier = Modifier.fillMaxSize(),
                            settings = sbActiveSettings,
                            indicatorContent = { idx, _ ->
                                var acc = 0
                                var d: String? = null
                                for (sec in vm.visibleSections) {
                                    if (idx >= acc && idx <= acc + sec.rows.size) {
                                        d = vm.dateLabel(sec.dateKey, sec.rows.size)
                                        break
                                    }
                                    acc += 1 + sec.rows.size
                                }
                                if (d != null) DateBubble(d)
                            }
                        )
                        // 拖动层：右缘窄条，像素级比例滚动（平滑，无交界跳动）
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(24.dp)
                                .pointerInput(fileGridState) {
                                    detectVerticalDragGestures(
                                        onDragStart = { sbDragging = true },
                                        onDragEnd = { sbDragging = false },
                                        onDragCancel = { sbDragging = false },
                                        onVerticalDrag = { change, dy ->
                                            change.consume()
                                            val info = fileGridState.layoutInfo
                                            val vis = info.visibleItemsInfo
                                            if (vis.isNotEmpty()) {
                                                val avg = vis.sumOf { it.size.height.toDouble() } / vis.size
                                                val totalPx = avg * info.totalItemsCount / 3.0   // 3 列
                                                if (totalPx > 0) {
                                                    val scale = totalPx / size.height
                                                    scope.launch {
                                                        fileGridState.scrollBy((dy * scale).toFloat())
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                        )
                    }
                    }
                } // else: 文件模式网格结束
                // 多选操作条：有选中才从底部弹出（全选 + 格式勾选(合并) + 下载所选）
                AnimatedVisibility(
                    visible = selCount > 0,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(220)),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(180))
                ) {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        // 分格式计数（已应用"跳过已下载"过滤；格式勾掉时其标签变灰）
                        val skip = vm.skipDownloadedOn
                        val jpgCount: Int
                        val nefCount: Int
                        if (mergeOn) {
                            val selPairs = pairs.filter { it.stamp in vm.pairSelection.value }
                            jpgCount = selPairs.count { it.jpg != null && !(skip && it.jpgDownloaded) }
                            nefCount = selPairs.count { it.nef != null && !(skip && it.nefDownloaded) }
                        } else {
                            val selRows = photoRows.filter { it.selected.value }
                            jpgCount = selRows.count { it.type.equals("JPG", true) && !(skip && it.downloaded.value) }
                            nefCount = selRows.count { it.type.equals("NEF", true) && !(skip && it.downloaded.value) }
                        }
                        val rawTotal = jpgCount + nefCount
                        val total = if (mergeOn)
                            (if (vm.batchFmtJpg.value) jpgCount else 0) +
                                (if (vm.batchFmtNef.value) nefCount else 0)
                        else rawTotal
                        val allDownloaded = rawTotal == 0 && selCount > 0 && skip
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 全选：勾上=全选可见项；再点一次=清空（部分选中显示未勾）
                            val totalVisible = if (mergeOn) pairs.size else visible.size
                            val allSel = selCount > 0 && selCount == totalVisible
                            Row(
                                Modifier.clickable {
                                    if (allSel) vm.clearSelection()
                                    else if (mergeOn) vm.pairSelection.value = pairs.map { it.stamp }.toSet()
                                    else visible.forEach { it.selected.value = true }
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = allSel, onCheckedChange = null)
                                Text("全选", style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.weight(1f))
                            if (mergeOn) {
                                Row(
                                    Modifier.clickable { vm.batchFmtJpg.value = !vm.batchFmtJpg.value }
                                        .alpha(if (vm.batchFmtJpg.value) 1f else 0.45f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = vm.batchFmtJpg.value, onCheckedChange = null)
                                    Text("JPG $jpgCount", style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(
                                    Modifier.clickable { vm.batchFmtNef.value = !vm.batchFmtNef.value }
                                        .alpha(if (vm.batchFmtNef.value) 1f else 0.45f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = vm.batchFmtNef.value, onCheckedChange = null)
                                    Text("NEF $nefCount", style = MaterialTheme.typography.bodyMedium)
                                }
                            } else {
                                Text(
                                    "JPG $jpgCount · NEF $nefCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Button(
                            onClick = { scope.launch { withContext(Dispatchers.IO) { vm.downloadSelected() } } },
                            enabled = total > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (allDownloaded) "均已下载" else "下载所选($total)") }
                        }
                    }
                }
            }
            // 全屏预览（横向滑动翻页，只在筛选结果内翻；合并模式翻合并对）
            if (mergeOn && pairPreviewIndex >= 0 && pairs.isNotEmpty()) {
                PairPager(
                    initialIndex = pairPreviewIndex,
                    rows = pairs.toList(),
                    onClose = { page ->
                        highlightHandle = null
                        val stamp = pairs.getOrNull(page)?.stamp
                        highlightStamp = stamp
                        // 网格跳到刚预览的对所在位置（已在可视区则不动）
                        if (stamp != null) {
                            var idx = 0
                            var found = false
                            for (sec in vm.pairSections) {
                                if (found) break
                                idx++   // 日期分组头
                                val pos = sec.rows.indexOfFirst { it.stamp == stamp }
                                if (pos >= 0) { idx += pos; found = true } else idx += sec.rows.size
                            }
                            if (found) {
                                // 只露出边缘也算不可见，必须完整可见才不跳
                                val item = mergeGridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
                                val fullyVisible = item != null &&
                                    item.offset.y >= mergeGridState.layoutInfo.viewportStartOffset &&
                                    item.offset.y + item.size.height <= mergeGridState.layoutInfo.viewportEndOffset
                                if (!fullyVisible) scope.launch { mergeGridState.scrollToItem(idx) }
                            }
                        }
                        pairPreviewIndex = -1
                    },
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
                    onClose = { page ->
                        highlightStamp = null
                        val handle = visible.getOrNull(page)?.handle
                        highlightHandle = handle
                        // 网格跳到刚预览的照片所在位置（已在可视区则不动）
                        if (handle != null) {
                            var idx = 0
                            var found = false
                            for (sec in vm.visibleSections) {
                                if (found) break
                                idx++   // 日期分组头
                                val pos = sec.rows.indexOfFirst { it.handle == handle }
                                if (pos >= 0) { idx += pos; found = true } else idx += sec.rows.size
                            }
                            if (found) {
                                // 只露出边缘也算不可见，必须完整可见才不跳
                                val item = fileGridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
                                val fullyVisible = item != null &&
                                    item.offset.y >= fileGridState.layoutInfo.viewportStartOffset &&
                                    item.offset.y + item.size.height <= fileGridState.layoutInfo.viewportEndOffset
                                if (!fullyVisible) scope.launch { fileGridState.scrollToItem(idx) }
                            }
                        }
                        previewIndex = -1
                    },
                    onDownload = { row ->
                        scope.launch { withContext(Dispatchers.IO) { vm.downloadOne(row) } }
                    }
                )
            }
            // 筛选卡片（底部弹出，实时生效）
            if (showFilter) FilterSheet(onDismiss = { showFilter = false })
            // 热点未开启提醒（本 App 主拓扑 = 相机连手机热点）
            if (hotspotHint) {
                AlertDialog(
                    onDismissRequest = { hotspotHint = false },
                    title = { Text("手机热点未开启") },
                    text = {
                        Text(
                            "相机需要连接到手机热点才能传图。\n\n" +
                                "请打开手机热点，再到相机菜单「连接至 PC (Wi-Fi)」选择本热点，然后点状态条重试。\n\n" +
                                "（若你使用的是相机开热点的另一组网方式，可点「仍然连接」跳过此提醒）"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            hotspotHint = false
                            openHotspotSettings()
                        }) { Text("打开热点设置") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            hotspotHint = false
                            ensureLocalNetworkPermission {
                                scope.launch { withContext(Dispatchers.IO) { vm.connectionFlow() } }
                            }
                        }) { Text("仍然连接") }
                    }
                )
            }
            // 设置：从左侧滑出的卡片式面板（蒙层点击关闭，主界面保留在底下）
            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.matchParentSize()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showSettings = false }
                )
            }
            AnimatedVisibility(
                visible = showSettings,
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it },
                modifier = Modifier.matchParentSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    Surface(
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .fillMaxWidth(0.88f),
                        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                        shadowElevation = 8.dp
                    ) {
                        SettingsScreen(
                            prefs = prefs,
                            onBack = { showSettings = false },
                            onKeepScreenOnChanged = { applyKeepScreenOn(it) },
                            onPickDir = { dirPicker.launch(null) }
                        )
                    }
                }
            }
            // 下载队列：从右侧滑出的卡片面板（样式同设置侧板）
            AnimatedVisibility(
                visible = showDownloads,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.matchParentSize()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showDownloads = false }
                )
            }
            AnimatedVisibility(
                visible = showDownloads,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier.matchParentSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    Surface(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(0.88f),
                        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                        shadowElevation = 8.dp
                    ) {
                        DownloadsScreen(onBack = { showDownloads = false })
                    }
                }
            }
        }
    }

    /** 按文件名在 MediaStore 查找并用系统相册打开 */
    private fun openInGallery(name: String, type: String) {
        try {
            val uri = MediaStore.Files.getContentUri("external_primary")
            contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(name),
                null
            )?.use { c ->
                if (!c.moveToFirst()) {
                    Toast.makeText(this, "本地文件未找到（可能已删除）", Toast.LENGTH_SHORT).show()
                    return
                }
                val viewUri = ContentUris.withAppendedId(uri, c.getLong(0))
                val mime = if (type.equals("NEF", true)) "image/x-nikon-nef" else "image/jpeg"
                startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(viewUri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            }
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** 直达系统热点设置：先试 TetherSettings 页（多数机型可用），失败逐级退到系统面板 */
    private fun openHotspotSettings() {
        try {
            startActivity(
                Intent().setComponent(
                    android.content.ComponentName("com.android.settings", "com.android.settings.TetherSettings")
                )
            )
        } catch (e: Throwable) {
            try {
                startActivity(Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            } catch (e2: Throwable) {
                startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS))
            }
        }
    }

    /** 下载管理页：队列条目（状态/进度/重试/取消）+ 清空已完成 */
    @Composable
    private fun DownloadsScreen(onBack: () -> Unit) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("下载队列", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                val hasFinished = vm.downloadQueue.any {
                    it.status.value in setOf(QStatus.DONE, QStatus.CANCELED)
                }
                if (hasFinished) {
                    TextButton(onClick = { vm.clearFinished() }) { Text("清空已完成") }
                }
            }
            if (vm.downloadQueue.isEmpty()) {
                Text(
                    "暂无下载任务\n\n点按照片或批量下载后，任务会出现在这里",
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.downloadQueue, key = { it.handle }) { item ->
                    QueueRow(item)
                }
            }
        }
    }

    @Composable
    private fun QueueRow(item: QueueItem) {
        val st = item.status.value
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.type,
                    Modifier
                        .background(badgeColor(item.type), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.width(8.dp))
                Text(item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                when (st) {
                    QStatus.QUEUED -> Text("排队中", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    QStatus.RUNNING -> Text(
                        "${humanSize(item.got.value)} / ${humanSize(item.total.value)} · ${item.speed.value}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    QStatus.DONE -> Text("已完成 ✓", color = Color(0xFF00695C), style = MaterialTheme.typography.labelSmall)
                    QStatus.FAILED -> Text("失败", color = if (vm.darkModeOn) Color(0xFFEF9A9A) else Color(0xFFB71C1C), style = MaterialTheme.typography.labelSmall)
                    QStatus.CANCELED -> Text("已取消", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (st == QStatus.RUNNING && item.total.value > 0) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = {
                        (item.got.value.toFloat() / item.total.value).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (st) {
                    QStatus.QUEUED -> TextButton(onClick = { vm.cancelDownload(item) }) { Text("取消") }
                    QStatus.RUNNING -> TextButton(onClick = { vm.cancelDownload(item) }) { Text("取消") }
                    QStatus.FAILED -> TextButton(onClick = { vm.retryDownload(item) }) { Text("重试") }
                    else -> {}
                }
            }
        }
    }

    /** 预览页下载槽位：按队列状态渲染（排队/进度条/重试/已下载），与队列单一真相源联动 */
    @Composable
    private fun DownloadStateSlot(
        downloaded: Boolean,
        qItem: QueueItem?,
        label: String,
        enabled: Boolean = true,
        onDownload: () -> Unit,
        onOpenInGallery: (() -> Unit)? = null,
        modifier: Modifier = Modifier
    ) {
        val st = qItem?.status?.value
        when {
            // 已下载：正常可点按钮（灰禁用态在黑底上几乎不可见），点击弹确认窗重新下载
            downloaded || (qItem != null && st == QStatus.DONE) -> {
                var confirmRedownload by remember { mutableStateOf(false) }
                Button(
                    onClick = { confirmRedownload = true },
                    modifier = modifier,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00695C),
                        contentColor = Color.White
                    )
                ) { Text("已下载") }
                if (confirmRedownload) {
                    AlertDialog(
                        onDismissRequest = { confirmRedownload = false },
                        title = { Text("已下载") },
                        text = { Text("当前图片已下载，可打开系统相册查看，或重新下载。") },
                        confirmButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (onOpenInGallery != null) {
                                    TextButton(onClick = {
                                        confirmRedownload = false
                                        onOpenInGallery.invoke()
                                    }) { Text("打开相册") }
                                }
                                TextButton(onClick = {
                                    confirmRedownload = false
                                    onDownload()
                                }) { Text("重新下载") }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmRedownload = false }) { Text("取消") }
                        }
                    )
                }
            }
            qItem != null && st == QStatus.RUNNING -> Column(modifier) {
                if (qItem.total.value > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (qItem.got.value.toFloat() / qItem.total.value).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    "${humanSize(qItem.got.value)} / ${humanSize(qItem.total.value)} · ${qItem.speed.value}",
                    color = Color(0xFFAAAAAA),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            qItem != null && st == QStatus.QUEUED -> Box(modifier, contentAlignment = Alignment.Center) {
                Text("排队中…", color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodyMedium)
            }
            qItem != null && st == QStatus.FAILED -> Button(
                onClick = { vm.retryDownload(qItem) }, modifier = modifier
            ) { Text("失败 · 重试") }
            qItem != null && st == QStatus.CANCELED -> Button(
                onClick = { vm.retryDownload(qItem) }, modifier = modifier
            ) { Text("重新下载") }
            else -> Button(onClick = onDownload, enabled = enabled, modifier = modifier) { Text(label) }
        }
    }

    /** 合并格：JPG+NEF 一对一格，双格式徽章 + ✓J/✓N 独立下载标记 + 选中态 */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun PairCell(
        pair: PairRow,
        highlight: Boolean,
        onTap: () -> Unit,
        onLongPress: () -> Unit
    ) {
        val haptic = LocalHapticFeedback.current
        val selected = pair.stamp in vm.pairSelection.value
        // 进入可视区兜底：位图被内存 LRU 逐出后，滑回时从磁盘缓存自动恢复。
        // onScreen 标志供 VM 判断"读回时是否触发显示重组"（滑动中滚出屏的只进缓存）。
        DisposableEffect(Unit) {
            (pair.jpg ?: pair.nef)?.onScreen = true
            onDispose { (pair.jpg ?: pair.nef)?.onScreen = false }
        }
        LaunchedEffect(Unit) { (pair.jpg ?: pair.nef)?.let { vm.ensureThumb(it) } }
        // 返回指示：缩放脉冲（小-大-小-大-小，1s，精确归位）+ 持续青色描边标出刚预览的照片
        val pulse = remember { Animatable(0f) }
        LaunchedEffect(highlight) {
            if (highlight) {
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
                pulse.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
                pulse.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
                pulse.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
            } else pulse.snapTo(0f)
        }
        Box(
            Modifier
                .then(
                    // 高亮时才挂独立渲染层（无条件挂会让每个格子常驻一个 layer，滑动多开销）
                    if (highlight) Modifier.graphicsLayer {
                        val s = 1f + 0.06f * pulse.value
                        scaleX = s
                        scaleY = s
                    } else Modifier
                )
                .then(
                    if (highlight) Modifier.border(3.dp, Color(0xFF00695C), RoundedCornerShape(10.dp))
                    else Modifier
                )
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
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                Text("RAW+JPG", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
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
            if (selected) {
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

    /** 预览缩放状态：1x 时手势让位翻页；放大后归图片；松手 ≤1.2 自动吸附回 1x */
    private class PreviewZoomState {
        var scale by mutableStateOf(1f)
        var offset by mutableStateOf(Offset.Zero)
        var container by mutableStateOf(IntSize.Zero)

        fun clampOffset() {
            val maxX = (scale - 1f) * container.width / 2f
            val maxY = (scale - 1f) * container.height / 2f
            offset = Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
        }

        /** 松手吸附回 1x */
        suspend fun settle() {
            val s0 = scale
            val o0 = offset
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(160)) {
                val t = value
                scale = s0 + (1f - s0) * t
                offset = Offset(o0.x * (1f - t), o0.y * (1f - t))
            }
            scale = 1f
            offset = Offset.Zero
        }

        /** 以 tapPoint 为锚点动画缩放到目标倍率（双击定位放大/还原） */
        suspend fun zoomTo(targetScale: Float, tapPoint: Offset) {
            val s0 = scale
            val o0 = offset
            val center = Offset(container.width / 2f, container.height / 2f)
            val targetOffset = (tapPoint - center) * (1f - targetScale / s0) + o0 * (targetScale / s0)
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(220)) {
                val t = value
                scale = s0 + (targetScale - s0) * t
                offset = Offset(
                    o0.x + (targetOffset.x - o0.x) * t,
                    o0.y + (targetOffset.y - o0.y) * t
                )
            }
            scale = targetScale
            clampOffset()
        }
    }

    /** 预览缩放手势：1x 纯滑动不消费（翻页处理）；放大后/捏合时消费（归图片），松手 ≤1.2 吸附回 1x。
     *  单击回调用于沉浸模式切换（任何缩放状态均响应）。 */
    private fun Modifier.previewZoomGestures(
        zoom: PreviewZoomState,
        onTap: () -> Unit = {}
    ): Modifier = this
        .onSizeChanged { zoom.container = it }
        .pointerInput(zoom) {
            coroutineScope {
                launch {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { tapPoint ->
                            launch {
                                if (zoom.scale > 1.01f) zoom.settle() else zoom.zoomTo(2.5f, tapPoint)
                            }
                        }
                    )
                }
                launch {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var consuming = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.any { it.pressed }
                            if (!pressed) break
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            // 放大状态或出现捏合 → 手势归图片；1x 纯滑动不消费（让翻页处理）
                            if (zoom.scale > 1.01f || zoomChange != 1f) consuming = true
                            if (consuming) {
                                val newScale = (zoom.scale * zoomChange).coerceIn(1f, 5f)
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val center = Offset(zoom.container.width / 2f, zoom.container.height / 2f)
                                val ratio = if (zoom.scale > 0f) newScale / zoom.scale else 1f
                                var newOffset = (centroid - center) * (1f - ratio) +
                                    zoom.offset * ratio + panChange
                                val maxX = (newScale - 1f) * zoom.container.width / 2f
                                val maxY = (newScale - 1f) * zoom.container.height / 2f
                                newOffset = Offset(
                                    newOffset.x.coerceIn(-maxX, maxX),
                                    newOffset.y.coerceIn(-maxY, maxY)
                                )
                                zoom.scale = newScale
                                zoom.offset = newOffset
                                event.changes.forEach { it.consume() }
                            }
                        }
                        if (zoom.scale in 1.01f..1.2f) launch { zoom.settle() }
                    }
                }
            }
        }

    /** 合并模式全屏预览：JPG 大图 + 双格式下载按钮 + 加入选择 */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun PairPager(
        initialIndex: Int,
        rows: List<PairRow>,
        onClose: (Int) -> Unit,
        onDownloadJpg: (PairRow) -> Unit,
        onDownloadNef: (PairRow) -> Unit
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex) { rows.size }
        // 高清预览：开关开启时，当前页 ±1 自动预取（合并模式取显示中的那张：JPG 优先）
        LaunchedEffect(pagerState.currentPage, vm.hiresOn) {
            if (vm.hiresOn) {
                val idx = pagerState.currentPage
                val targets = (maxOf(0, idx - 1)..minOf(rows.lastIndex, idx + 1)).mapNotNull { pi ->
                    rows.getOrNull(pi)?.let { p -> p.jpg ?: p.nef }
                }
                vm.requestHires(targets)
            }
        }
        DisposableEffect(Unit) { onDispose { vm.clearHires() } }
        DisposableEffect(Unit) {
            onDispose { onClose(pagerState.currentPage) }
        }
        // 沉浸模式：单击图片切换，翻页保持状态
        var immersive by remember { mutableStateOf(false) }
        Surface(Modifier.fillMaxSize(), color = Color(0x66000000)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                AnimatedVisibility(
                    visible = !immersive,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                // 顶部细进度条：当前页高清加载中（-1=结构解析期不定长，0..1=定向读取）
                val topKey = rows.getOrNull(pagerState.currentPage)?.let { p -> (p.jpg ?: p.nef)?.handle }
                val topProg = topKey?.let { vm.hiresProgress[it] }
                if (vm.hiresOn && topProg != null) {
                    if (topProg < 0f) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = Color(0xFF4DB6AC),
                            trackColor = Color(0x334DB6AC)
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { topProg.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = Color(0xFF4DB6AC),
                            trackColor = Color(0x334DB6AC)
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onClose(pagerState.currentPage) }) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                    }
                    Text(
                        "${pagerState.currentPage + 1} / ${rows.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    // 右上角选择框：加入/移出多选（选中打勾）
                    val curPair = rows.getOrNull(pagerState.currentPage)
                    val selNow = curPair != null && curPair.stamp in vm.pairSelection.value
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selNow) Color(0xFF00695C) else Color.Transparent)
                            .border(
                                2.dp,
                                if (selNow) Color(0xFF00695C) else Color.White,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { curPair?.let { vm.togglePair(it.stamp) } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selNow) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "已选择",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { page ->
                    val pair = rows[page]
                    val zoom = remember { PreviewZoomState() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { zoom.container = it }
                            .previewZoomGestures(zoom, onTap = { immersive = !immersive }),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = (pair.jpg ?: pair.nef)?.handle?.let { vm.hiresBitmap(it) }
                            ?: pair.previewBmp
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = pair.stamp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoom.scale
                                        scaleY = zoom.scale
                                        translationX = zoom.offset.x
                                        translationY = zoom.offset.y
                                    },
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
                // 底部信息 + 双格式下载（沉浸模式隐藏）
                AnimatedVisibility(
                    visible = !immersive,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
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
                    // EXIF 参数胶囊（高清加载时解析；末项=镜头型号）
                    vm.exifLines[(pair.jpg ?: pair.nef)?.handle]?.let { parts ->
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            parts.forEach { p ->
                                Text(
                                    p,
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = Color(0xFFDDDDDD),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    // 高清加载失败提示（静默回退缩略图，翻回该页自动重试）
                    val hiKey = (pair.jpg ?: pair.nef)?.handle
                    if (vm.hiresOn && hiKey != null && vm.hiresFailed.containsKey(hiKey)) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "高清加载失败 · 翻回此页自动重试",
                            color = Color(0xFF777777),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 单格式成对：唯一按钮占满整排；双格式：各占一半
                        if (pair.hasJpg) DownloadStateSlot(
                            downloaded = pair.jpgDownloaded,
                            qItem = pair.jpg?.let { j ->
                                vm.downloadQueue.firstOrNull { it.handle == j.handle }
                            },
                            label = "下载 JPG",
                            onDownload = { onDownloadJpg(pair) },
                            onOpenInGallery = {
                                pair.jpg?.let { p -> openInGallery(p.name, p.type) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (pair.hasNef) DownloadStateSlot(
                            downloaded = pair.nefDownloaded,
                            qItem = pair.nef?.let { n ->
                                vm.downloadQueue.firstOrNull { it.handle == n.handle }
                            },
                            label = "下载 NEF",
                            onDownload = { onDownloadNef(pair) },
                            onOpenInGallery = {
                                pair.nef?.let { n -> openInGallery(n.name, n.type) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                }
            }
        }
    }

    /** 网格格子：预览图 + 左上格式角标 + 右下已下载标记 + 选中态。
     *  点按=进全屏预览；长按=切换选中。 */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun GridCell(
        row: PhotoRow,
        highlight: Boolean,
        onTap: () -> Unit,
        onLongPress: () -> Unit
    ) {
        val haptic = LocalHapticFeedback.current
        // 进入可视区兜底：位图被内存 LRU 逐出后，滑回时从磁盘缓存自动恢复。
        // onScreen 标志供 VM 判断"读回时是否触发显示重组"（滑动中滚出屏的只进缓存）。
        DisposableEffect(Unit) {
            row.onScreen = true
            onDispose { row.onScreen = false }
        }
        LaunchedEffect(Unit) { vm.ensureThumb(row) }
        // 返回指示：缩放脉冲（小-大-小-大-小，1s，精确归位）+ 持续青色描边标出刚预览的照片
        val pulse = remember { Animatable(0f) }
        LaunchedEffect(highlight) {
            if (highlight) {
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
                pulse.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
                pulse.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
                pulse.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
            } else pulse.snapTo(0f)
        }
        Box(
            Modifier
                .then(
                    // 高亮时才挂独立渲染层（无条件挂会让每个格子常驻一个 layer，滑动多开销）
                    if (highlight) Modifier.graphicsLayer {
                        val s = 1f + 0.06f * pulse.value
                        scaleX = s
                        scaleY = s
                    } else Modifier
                )
                .then(
                    if (highlight) Modifier.border(3.dp, Color(0xFF00695C), RoundedCornerShape(10.dp))
                    else Modifier
                )
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
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                Text(row.type, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
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
            // 选中态：绿色边框 + 中央勾
            if (row.selected.value) {
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

    /** Wi-Fi 形状（三条弧 + 圆点），用于连接状态指示（同漏斗图标的自绘风格）；尺寸由调用方决定 */
    @Composable
    private fun WifiIcon(color: Color, modifier: Modifier = Modifier) {
        Canvas(modifier) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.14f
            val cx = w / 2f
            val cy = h * 0.80f
            val style = Stroke(width = stroke, cap = StrokeCap.Round)
            // 三条弧：由内到外，开口向上
            listOf(0.18f, 0.34f, 0.50f).forEach { r ->
                drawArc(
                    color = color,
                    startAngle = -135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(cx - w * r, cy - h * r),
                    size = Size(w * r * 2f, h * r * 2f),
                    style = style
                )
            }
            drawCircle(color, radius = w * 0.09f, center = Offset(cx, cy))
        }
    }

    /** 下载形状（向下箭头 + 底部托盘），用于下载队列指示（同自绘风格） */
    @Composable
    private fun DownloadIcon(color: Color, modifier: Modifier = Modifier) {
        Canvas(modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.13f
            // 箭杆
            drawLine(
                color,
                start = Offset(w * 0.5f, h * 0.12f),
                end = Offset(w * 0.5f, h * 0.55f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            // 箭头（实心三角）
            val arrow = Path().apply {
                moveTo(w * 0.26f, h * 0.48f)
                lineTo(w * 0.74f, h * 0.48f)
                lineTo(w * 0.5f, h * 0.74f)
                close()
            }
            drawPath(arrow, color)
            // 托盘
            drawLine(
                color,
                start = Offset(w * 0.18f, h * 0.88f),
                end = Offset(w * 0.82f, h * 0.88f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }

    /** 快速滚动条拖动/滚动时的日期气泡（LazyColumnScrollbar 的 indicatorContent 回调渲染） */
    @Composable
    private fun DateBubble(text: String) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
            shadowElevation = 4.dp
        ) {
            Text(
                text,
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
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
                    Text("文件类型", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                }
                Text("下载状态", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
                Text("拍摄日期", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                .background(if (selected) Color(0xFF00695C) else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
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
                .background(if (on) Color(0xFF00695C) else MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onToggle(!on) }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
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
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (value != null && value.length == 8)
                            "${value.substring(0, 4)}-${value.substring(4, 6)}-${value.substring(6, 8)}"
                        else "不限",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (value != null) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "清除$label",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onClear() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun PreviewPager(
        initialIndex: Int,
        rows: List<PhotoRow>,
        onClose: (Int) -> Unit,
        onDownload: (PhotoRow) -> Unit
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex) { rows.size }
        // 高清预览：开关开启时，当前页 ±1 自动预取
        LaunchedEffect(pagerState.currentPage, vm.hiresOn) {
            if (vm.hiresOn) {
                val idx = pagerState.currentPage
                val targets = (maxOf(0, idx - 1)..minOf(rows.lastIndex, idx + 1)).mapNotNull { rows.getOrNull(it) }
                vm.requestHires(targets)
            }
        }
        DisposableEffect(Unit) { onDispose { vm.clearHires() } }
        DisposableEffect(Unit) {
            onDispose { onClose(pagerState.currentPage) }
        }
        // 沉浸模式：单击图片切换，翻页保持状态
        var immersive by remember { mutableStateOf(false) }
        Surface(Modifier.fillMaxSize(), color = Color(0x66000000)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                AnimatedVisibility(
                    visible = !immersive,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                // 顶部细进度条：当前页高清加载中（-1=结构解析期不定长，0..1=定向读取）
                val topRow = rows.getOrNull(pagerState.currentPage)
                val topProg = topRow?.let { vm.hiresProgress[it.handle] }
                if (vm.hiresOn && topProg != null) {
                    if (topProg < 0f) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = Color(0xFF4DB6AC),
                            trackColor = Color(0x334DB6AC)
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { topProg.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = Color(0xFF4DB6AC),
                            trackColor = Color(0x334DB6AC)
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onClose(pagerState.currentPage) }) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                    }
                    Text(
                        "${pagerState.currentPage + 1} / ${rows.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    // 右上角选择框：加入/移出多选（选中打勾）
                    val curRow = rows.getOrNull(pagerState.currentPage)
                    val selNow = curRow != null && curRow.selected.value
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selNow) Color(0xFF00695C) else Color.Transparent)
                            .border(
                                2.dp,
                                if (selNow) Color(0xFF00695C) else Color.White,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { curRow?.let { it.selected.value = !it.selected.value } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selNow) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "已选择",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { page ->
                    val row = rows[page]
                    val zoom = remember { PreviewZoomState() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { zoom.container = it }
                            .previewZoomGestures(zoom, onTap = { immersive = !immersive }),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = vm.hiresBitmap(row.handle) ?: row.preview.value
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = row.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoom.scale
                                        scaleY = zoom.scale
                                        translationX = zoom.offset.x
                                        translationY = zoom.offset.y
                                    },
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
                // 底部信息 + 操作（沉浸模式隐藏）
                AnimatedVisibility(
                    visible = !immersive,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
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
                    // EXIF 参数胶囊（高清加载时解析；末项=镜头型号）
                    vm.exifLines[row.handle]?.let { parts ->
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            parts.forEach { p ->
                                Text(
                                    p,
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = Color(0xFFDDDDDD),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    // 高清加载失败提示（静默回退缩略图，翻回该页自动重试）
                    if (vm.hiresOn && vm.hiresFailed.containsKey(row.handle)) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "高清加载失败 · 翻回此页自动重试",
                            color = Color(0xFF777777),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DownloadStateSlot(
                            downloaded = row.downloaded.value,
                            qItem = vm.downloadQueue.firstOrNull { it.handle == row.handle },
                            label = "下载此照片",
                            onDownload = { onDownload(row) },
                            onOpenInGallery = { openInGallery(row.name, row.type) },
                            modifier = Modifier.weight(1f)
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
        var keepOn by remember { mutableStateOf(prefs.getBoolean("set_keep_on", false)) }
        var autoConnect by remember { mutableStateOf(prefs.getBoolean("set_auto_connect", false)) }
        var autoPreview by remember { mutableStateOf(prefs.getBoolean("set_auto_preview", true)) }
        var showCacheLimitDialog by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { vm.refreshThumbCacheSize() }

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("设置", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            ) {
            SettingsSection(title = "连接", icon = { SectionIcon("连接") }) {
                val (cm, cs) = if (connDetail.value.isNotEmpty()) vm.splitInfo(connDetail.value) else ("Nikon" to "?")
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusColor = when (connPhase.value) {
                        "connected" -> if (vm.darkModeOn) Color(0xFF4DB6AC) else Color(0xFF00695C)
                        "connecting" -> if (vm.darkModeOn) Color(0xFFFFD54F) else Color(0xFF8D6E00)
                        else -> if (vm.darkModeOn) Color(0xFFEF9A9A) else Color(0xFFB71C1C)
                    }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (connPhase.value == "connected") "已连接：$cm ($cs) · ${connectedIp.value}"
                        else if (connPhase.value == "connecting") "正在连接相机…"
                        else "未连接，点击下方扫描相机",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor
                    )
                }
                Button(
                    onClick = {
                        ensureLocalNetworkPermission {
                            scope.launch {
                                withContext(Dispatchers.IO) { vm.scanForCameras() }
                            }
                        }
                    },
                    enabled = !scanning.value && !connecting,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
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
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("$model ($serial)", style = MaterialTheme.typography.bodyLarge)
                            Text(ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (connected && ip == connectedIp.value)
                            Text("已连接 ✓", color = Color(0xFF00695C), style = MaterialTheme.typography.labelSmall)
                    }
                }
                SettingsDivider()
                SettingSwitch(
                    title = "打开 App 自动连接相机",
                    subtitle = "启动后若手机热点已开启，自动扫描并连接相机（热点未开则跳过）",
                    checked = autoConnect,
                    onChange = {
                        autoConnect = it
                        prefs.edit().putBoolean("set_auto_connect", it).apply()
                    }
                )
            }
            SettingsSection(title = "传输", icon = { SectionIcon("传输") }) {
                SettingSwitch(
                    title = "合并 RAW+JPG 展示",
                    subtitle = "同一时间拍摄的 JPG 与 NEF 合并为一格，预览页/批量下载可分别选格式",
                    checked = vm.mergePairs.value,
                    onChange = { vm.setMergePairs(it) }
                )
                SettingsDivider()
                SettingSwitch(
                    title = "下载时跳过已下载",
                    subtitle = "批量下载所选时自动排除已下载的文件（预览页重新下载不受影响）",
                    checked = vm.skipDownloadedOn,
                    onChange = { vm.setSkipDownloaded(it) }
                )
                SettingsDivider()
                SettingSwitch(
                    title = "连接后自动加载预览",
                    subtitle = "关闭后只列文件名，节省流量与时间",
                    checked = autoPreview,
                    onChange = {
                        autoPreview = it
                        prefs.edit().putBoolean("set_auto_preview", it).apply()
                    }
                )
                SettingsDivider()
                SettingSwitch(
                    title = "高清预览",
                    subtitle = "预览时自动加载相机内嵌高清图（每张约 0.7~1MB 流量）",
                    checked = vm.hiresOn,
                    onChange = { vm.setHiresPreview(it) }
                )
                SettingsDivider()
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
            }
            SettingsSection(title = "外观", icon = { SectionIcon("外观") }) {
                SettingSwitch(
                    title = "深色模式",
                    subtitle = "界面切换为深色配色（默认浅色，开关即时生效）",
                    checked = vm.darkModeOn,
                    onChange = { vm.setDarkMode(it) }
                )
            }
            SettingsSection(title = "存储", icon = { SectionIcon("存储") }) {
                SettingSwitch(
                    title = "按拍摄日期文件夹保存",
                    subtitle = "JPG 与 NEF 存入同一拍摄日期文件夹（如 2026-10-01），取自相机时间",
                    checked = vm.dateFolderOn.value,
                    onChange = { vm.setDateFolder(it) }
                )
                SettingsDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("存储目录", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            vm.dirDisplay.value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onPickDir) { Text("更改目录") }
                    if (vm.customDirUri.value != null) {
                        TextButton(onClick = { vm.setCustomDir(null) }) { Text("恢复默认") }
                    }
                }
                SettingsDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("缩略图缓存", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "当前 ${humanSize(vm.thumbCacheBytes)} · 上限 ${vm.thumbCacheLimitMb}MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showCacheLimitDialog = true }) { Text("上限") }
                    TextButton(onClick = { vm.clearThumbCache() }) { Text("清空") }
                }
                if (showCacheLimitDialog) {
                    AlertDialog(
                        onDismissRequest = { showCacheLimitDialog = false },
                        title = { Text("缩略图缓存上限") },
                        text = {
                            Column {
                                listOf(100, 200, 500, 1024).forEach { mb ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clickable {
                                                vm.setThumbCacheMb(mb)
                                                showCacheLimitDialog = false
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = vm.thumbCacheLimitMb == mb,
                                            onClick = {
                                                vm.setThumbCacheMb(mb)
                                                showCacheLimitDialog = false
                                            }
                                        )
                                        Text("${mb}MB" + if (mb == 200) "（默认）" else "", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        },
                        confirmButton = {}
                    )
                }
            }
            SettingsSection(title = "关于", icon = { SectionIcon("关于") }) {
                Text(
                    "尼康 Z 系列 Wi-Fi 传图 · 配对模式原生协议\n" +
                        "协议：PTP/IP + Nikon 私有指令（0x941c/0x9421/0x9431/0x9434/0x952b/0x935a）\n" +
                        "注意：配对模式下相机不提供原始文件名，列表名称由拍摄时间+句柄生成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            }
        }
    }

    /** 设置分组：组标题（自绘小图标 + 文字）在外，内容包进圆角卡片 */
    @Composable
    private fun SettingsSection(
        title: String,
        icon: @Composable () -> Unit,
        content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            ) {
                icon()
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(content = content)
            }
        }
        Spacer(Modifier.height(18.dp))
    }

    /** 卡片内行分隔线（左右内缩） */
    @Composable
    private fun SettingsDivider() {
        HorizontalDivider(
            thickness = 0.8.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
            modifier = Modifier.padding(horizontal = 14.dp)
        )
    }

    /** 分组小图标（16dp 自绘，与主界面漏斗/WiFi/下载同风格） */
    @Composable
    private fun SectionIcon(kind: String) {
        val color = MaterialTheme.colorScheme.primary
        val surface = MaterialTheme.colorScheme.surface
        Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            when (kind) {
                "连接" -> {
                    val style = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
                    val cx = w / 2f
                    val cy = h * 0.78f
                    listOf(0.2f, 0.38f).forEach { r ->
                        drawArc(
                            color,
                            -135f, 90f, false,
                            topLeft = Offset(cx - w * r, cy - h * r),
                            size = Size(w * r * 2f, h * r * 2f),
                            style = style
                        )
                    }
                    drawCircle(color, radius = w * 0.08f, center = Offset(cx, cy))
                }
                "传输" -> {
                    val style = Stroke(width = w * 0.13f, cap = StrokeCap.Round)
                    // 右向箭头（上）
                    drawLine(color, Offset(w * 0.08f, h * 0.28f), Offset(w * 0.9f, h * 0.28f), style.width, cap = StrokeCap.Round)
                    val right = Path().apply {
                        moveTo(w * 0.6f, h * 0.1f)
                        lineTo(w * 0.92f, h * 0.28f)
                        lineTo(w * 0.6f, h * 0.46f)
                        close()
                    }
                    drawPath(right, color)
                    // 左向箭头（下）
                    drawLine(color, Offset(w * 0.92f, h * 0.72f), Offset(w * 0.08f, h * 0.72f), style.width, cap = StrokeCap.Round)
                    val left = Path().apply {
                        moveTo(w * 0.4f, h * 0.54f)
                        lineTo(w * 0.08f, h * 0.72f)
                        lineTo(w * 0.4f, h * 0.9f)
                        close()
                    }
                    drawPath(left, color)
                }
                "外观" -> {
                    // 月牙：主圆 + 表面色圆裁出月牙
                    drawCircle(color, radius = w * 0.42f, center = Offset(w * 0.45f, h * 0.52f))
                    drawCircle(surface, radius = w * 0.36f, center = Offset(w * 0.66f, h * 0.38f))
                }
                "存储" -> {
                    val folder = Path().apply {
                        moveTo(w * 0.06f, h * 0.2f)
                        lineTo(w * 0.38f, h * 0.2f)
                        lineTo(w * 0.46f, h * 0.32f)
                        lineTo(w * 0.94f, h * 0.32f)
                        lineTo(w * 0.94f, h * 0.8f)
                        lineTo(w * 0.06f, h * 0.8f)
                        close()
                    }
                    drawPath(folder, color)
                }
                "关于" -> {
                    drawCircle(
                        color,
                        radius = w * 0.44f,
                        center = Offset(w / 2f, h / 2f),
                        style = Stroke(width = w * 0.1f, cap = StrokeCap.Round)
                    )
                    drawCircle(color, radius = w * 0.06f, center = Offset(w / 2f, h * 0.3f))
                    drawLine(
                        color,
                        start = Offset(w / 2f, h * 0.44f),
                        end = Offset(w / 2f, h * 0.7f),
                        strokeWidth = w * 0.11f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }

    @Composable
    private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                // 开启 = 正常字色；关闭 = 灰字（快速扫出哪些功能在用）
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (checked) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
