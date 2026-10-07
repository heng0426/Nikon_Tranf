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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
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

    internal val vm: MainViewModel by viewModels()

    // ---- 转发到 ViewModel（Activity 重建时状态由 VM 保留）----
    internal val connected get() = vm.connected
    internal val connecting get() = vm.connecting
    internal val connPhase get() = vm.connPhase
    internal val connText get() = vm.connText
    internal val connDetail get() = vm.connDetail
    internal val connectedIp get() = vm.connectedIp
    internal val showConnDetail get() = vm.showConnDetail
    internal val scanResults get() = vm.scanResults
    internal val scanning get() = vm.scanning
    internal val scanText get() = vm.scanText
    internal val photoRows get() = vm.photoRows
    internal val downloadProgress get() = vm.downloadProgress
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

    internal fun ensureLocalNetworkPermission(action: () -> Unit) {
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
        // 请求最高刷新率：HyperOS「智能刷新率」把无帧率声明的应用归入 normal=60Hz 档，
        // 照片网格滑动会被锁 60fps（实测 framestats：janky≈0 但帧距被 vsync 节拍钉在 ~20ms）。
        // 显式投票同分辨率的最高刷新模式（120Hz），浏览照片属高频交互，值得这点功耗。
        display?.mode?.let { cur ->
            display?.supportedModes
                ?.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                ?.maxByOrNull { it.refreshRate }
                ?.takeIf { it.refreshRate > cur.refreshRate }
                ?.let { best ->
                    window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
                }
        }
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
        // 合并模式高亮键 = PairRow.key（唯一）。不能用 stamp：连拍同秒两格会同时命中，
        // 返回指示脉冲同时跳动。单格式模式用 handle，天然唯一。
        var highlightPairKey by remember { mutableStateOf<String?>(null) }
        // 脉冲只播一次的闸门（跨格子组合销毁存活）：预览返回时武装，命中格子播完即消费——
        // 否则格子滑出屏被 LazyGrid 销毁、滑回重建时 LaunchedEffect 首跑会重播动画
        var highlightPulseArmed by remember { mutableStateOf(false) }
        val mergeGridState = rememberLazyGridState()
        val fileGridState = rememberLazyGridState()
        // 滑动感知暂停：网格滚动时通知缩略图 loader 挂起重活（停止后自动继续）
        LaunchedEffect(mergeGridState, fileGridState) {
            snapshotFlow { mergeGridState.isScrollInProgress || fileGridState.isScrollInProgress }
                .collect { vm.gridScrolling = it }
        }

        // 打开 App 自动连接（设置项，默认关）：仅未连接/未连接中时触发；
        // 优先通道 = 设置项 set_auto_conn_channel（usb 默认 / wifi），由总连接流程分发
        LaunchedEffect(Unit) {
            if (prefs.getBoolean("set_auto_connect", false) &&
                !connected && !connecting && connPhase.value == "disconnected"
            ) {
                val channel = prefs.getString("set_auto_conn_channel", "usb") ?: "usb"
                vm.pendingChannel.value = if (channel == "wifi") "wifi" else null
                ensureLocalNetworkPermission {
                    scope.launch { withContext(Dispatchers.IO) { vm.connectionFlow() } }
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

        // 返回键优先级（Compose 中先注册者优先级最低）：多选退出 → 预览关闭 → 下载页/设置页回主页。
        // 多选时点进预览，按返回先关预览回多选（多选 handler 必须最先注册，否则会抢走预览的返回键）
        BackHandler(enabled = selCount > 0 && pairPreviewIndex < 0 && previewIndex < 0) {
            vm.clearSelection()
        }
        BackHandler(enabled = pairPreviewIndex >= 0) { pairPreviewIndex = -1 }
        BackHandler(enabled = previewIndex >= 0) { previewIndex = -1 }
        BackHandler(enabled = showDownloads) { showDownloads = false }
        BackHandler(enabled = showSettings) { showSettings = false }

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
                // 顶部栏：双卡连接界面（无照片）只保留设置齿轮；有图片列表时恢复筛选/连接/下载
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // 设置齿轮按钮（方框圆角，同款 34dp / RoundedCornerShape(UiSpec.ROUND_SMALL)）
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showSettings = true },
                        contentAlignment = Alignment.Center
                    ) {
                        GearIcon(MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (photoRows.isNotEmpty()) {
                        // 筛选按钮（logo 右侧，同款圆角外框）：有筛选生效时漏斗变色
                        Box(
                            Modifier
                                .padding(start = 10.dp)
                                .size(34.dp)
                                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showFilter = true },
                            contentAlignment = Alignment.Center
                        ) {
                            FunnelIcon(if (vm.filterActive) Color(0xFF00695C) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // 连接指示（常驻，筛选旁，同款圆角外框）：已连接=显示通道图标(点看详情)；连接中=禁点；断开=点击重连
                        val red = phase == "disconnected"
                        val amber = phase == "connecting"
                        val isUsbConn = vm.connChannel.value == "usb"
                        Box(
                            Modifier
                                .padding(start = 10.dp)
                                .graphicsLayer { translationX = connShake.value * 6.dp.toPx() }
                                .size(34.dp)
                                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(enabled = !amber) {
                                    if (red) {
                                        // 重试连接：走当前偏好通道（USB 优先默认）
                                        ensureLocalNetworkPermission {
                                            scope.launch { withContext(Dispatchers.IO) { vm.connectionFlow() } }
                                        }
                                    } else toggleConnDetail()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val iconColor by animateColorAsState(
                                when {
                                    red -> UiSpec.error(vm.darkModeOn)
                                    amber -> UiSpec.amber(vm.darkModeOn)
                                    isUsbConn -> UiSpec.usbBlue(vm.darkModeOn)
                                    vm.darkModeOn -> Color(0xFF4DB6AC)
                                    else -> Color(0xFF00695C)
                                },
                                animationSpec = tween(UiSpec.STANDARD),
                                label = "connIconColor"
                            )
                            if (isUsbConn) {
                                UsbIcon(iconColor, Modifier.size(20.dp))
                            } else {
                                WifiIcon(iconColor, Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // 下载队列按钮：无任务=下载图标；有任务=数字直接替换图标（青绿底白字，99+ 封顶）
                        TopBarDownloadButton(onClick = { showDownloads = true })
                    }
                }
                // 详情浮层 + 状态条收纳进内层 Column（无 spacedBy：卡片移除时不会带走间距导致下方跳动）
                Column {
                // 连接详情浮层：点击 Wi-Fi 图标后显示 3 秒（展开+淡入 / 收起+淡出）
                AnimatedVisibility(
                    visible = showConnDetail.value && phase == "connected",
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(UiSpec.STANDARD)),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(UiSpec.QUICK))
                ) {
                    val (cm, cs) = if (connDetail.value.isNotEmpty()) vm.splitInfo(connDetail.value)
                    else ("Nikon" to "?")
                    val isUsb = vm.connChannel.value == "usb"
                    Surface(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = if (vm.darkModeOn)
                            (if (isUsb) Color(0xFF101838) else Color(0xFF10312D))
                        else
                            (if (isUsb) Color(0xFFE3F2FD) else Color(0xFFE0F2F1))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "已连接：$cm ($cs) · ${if (isUsb) "USB 直连" else connectedIp.value}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (vm.darkModeOn)
                                        (if (isUsb) Color(0xFF64B5F6) else Color(0xFF4DB6AC))
                                    else
                                        (if (isUsb) Color(0xFF1565C0) else Color(0xFF00695C))
                                )
                            }
                            TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { vm.disconnect() } } }) {
                                Text(
                                    "断开",
                                    color = UiSpec.error(vm.darkModeOn)
                                )
                            }
                        }
                    }
                }
                // 连接状态：无照片（冷启动/连接中/连接成功枚举中/断开回退）= 全屏双卡；
                // 有照片（断开/连接中）= 顶部紧凑选择条内动画
                if (photoRows.isEmpty()) {
                    // 全屏双卡选择（枚举中双卡保持显示，进度在下方信息卡片）
                    val isUsbActive = phase == "connecting" &&
                        (vm.pendingChannel.value == "usb" || vm.connChannel.value == "usb")
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(48.dp))
                        Text(
                            when {
                                phase == "connecting" -> "正在连接相机…"
                                phase == "connected" -> "正在读取照片列表…"
                                else -> "选择连接方式"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        // USB 卡（蓝色系）
                        UsbChannelCard(
                            onClick = {
                                scope.launch { withContext(Dispatchers.IO) { vm.startConnect("usb") } }
                            },
                            active = isUsbActive,
                            dimmed = phase == "connecting" && !isUsbActive
                        )
                        Spacer(Modifier.height(12.dp))
                        // Wi-Fi 卡（绿色系）
                        WifiChannelCard(
                            onClick = {
                                ensureLocalNetworkPermission {
                                    scope.launch { withContext(Dispatchers.IO) { vm.startConnect("wifi") } }
                                }
                            },
                            onScanClick = {
                                ensureLocalNetworkPermission {
                                    scope.launch { withContext(Dispatchers.IO) { vm.requestWifiScan() } }
                                }
                            },
                            onWifiCancel = { vm.cancelWifiConnect() },
                            active = phase == "connecting" && !isUsbActive,
                            dimmed = phase == "connecting" && isUsbActive,
                            hotspotOn = vm.isHotspotOn()
                        )
                        // 连接中=探测/扫描进度；连接成功=正在读取列表；断开=失败原因/取消提示/失联信息
                        ConnInfoCard(
                            text = when {
                                phase == "connecting" -> connText.value
                                phase == "connected" -> "正在读取照片列表…"
                                else -> vm.connFailMsg.value
                            },
                            loading = phase != "disconnected"
                        )
                        if (phase != "connecting" && vm.scanResults.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "发现 ${vm.scanResults.size} 台相机，请选择：",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Column {
                                vm.scanResults.forEach { (ip, info) ->
                                    val (m, s) = vm.splitInfo(info)
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) { vm.connectToCamera(ip, info) }
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("$m ($s)", style = MaterialTheme.typography.bodyMedium)
                                            Text(ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text("连接", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(48.dp))
                    }
                } else if (photoRows.isNotEmpty()) {
                    // 有照片：顶部紧凑重连条——断开时弹出（淡入+向下展开），连接成功后淡出收起。
                    // 点选通道 → 该卡动画展开成整条（带进度），另一张动画收起；失败 → 双卡动画恢复。
                    AnimatedVisibility(
                        visible = phase != "connected",
                        enter = fadeIn(tween(UiSpec.STANDARD)) + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut(tween(UiSpec.QUICK)) + shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        Column {
                            val connectingNow = phase == "connecting"
                    val usbFull = connectingNow &&
                        (vm.pendingChannel.value == "usb" || vm.connChannel.value == "usb")
                    val wifiFull = connectingNow && !usbFull
                    val expandSpec = tween<Float>(UiSpec.EMPHASIS)
                    val usbW by animateFloatAsState(
                        when { usbFull -> 2.4f; connectingNow -> 0f; else -> 1f },
                        animationSpec = expandSpec, label = "usbW"
                    )
                    val wifiW by animateFloatAsState(
                        when { wifiFull -> 2.4f; connectingNow -> 0f; else -> 1f },
                        animationSpec = expandSpec, label = "wifiW"
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier.weight(usbW.coerceAtLeast(0.001f))
                                .graphicsLayer { alpha = usbW.coerceIn(0f, 1f) }
                                .clipToBounds()
                                .padding(end = (8f * wifiW.coerceIn(0f, 1f)).dp)
                        ) {
                            UsbChannelCard(
                                compact = true,
                                onClick = {
                                    scope.launch { withContext(Dispatchers.IO) { vm.startConnect("usb") } }
                                },
                                active = usbFull,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Box(
                            Modifier.weight(wifiW.coerceAtLeast(0.001f))
                                .graphicsLayer { alpha = wifiW.coerceIn(0f, 1f) }
                                .clipToBounds()
                        ) {
                            WifiChannelCard(
                                compact = true,
                                onClick = {
                                    ensureLocalNetworkPermission {
                                        scope.launch { withContext(Dispatchers.IO) { vm.startConnect("wifi") } }
                                    }
                                },
                                onScanClick = {
                                    ensureLocalNetworkPermission {
                                        scope.launch { withContext(Dispatchers.IO) { vm.requestWifiScan() } }
                                    }
                                },
                                onWifiCancel = { vm.cancelWifiConnect() },
                                hotspotOn = vm.isHotspotOn(),
                                active = wifiFull,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    // 连接中=探测/扫描进度（带转圈）；断开=失败原因/取消提示/失联信息
                    ConnInfoCard(
                        text = if (phase == "connecting") connText.value else vm.connFailMsg.value,
                        loading = phase == "connecting"
                    )
                    if (vm.scanResults.isNotEmpty()) {
                        Column(Modifier.padding(top = 4.dp)) {
                            vm.scanResults.forEach { (ip, info) ->
                                val (m, s) = vm.splitInfo(info)
                                Row(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            scope.launch { withContext(Dispatchers.IO) { vm.connectToCamera(ip, info) } }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("$m ($s)", style = MaterialTheme.typography.bodyMedium)
                                        Text(ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("连接", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                        }
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
                    thumbUnselectedColor = UiSpec.accent(vm.darkModeOn),
                    thumbSelectedColor = UiSpec.thumbSel(vm.darkModeOn),
                    hideDelayMillis = 600
                )
                // 拖动滚动条期间强制常显：scrollBy 事件间隙 isScrollInProgress 抖动会导致渐隐
                var sbDragging by remember { mutableStateOf(false) }
                val sbActiveSettings = if (sbDragging) sbSettings.copy(alwaysShowScrollbar = true) else sbSettings
                // 分块加载：滚动接近已加载区域末尾（24 项 ≈ 8 行）时自动追加下一块；
                // 设置里关闭分块时 loadMoreChunks 内部直接忽略。仅活动网格的状态被读取。
                LaunchedEffect(mergeOn) {
                    val state = if (mergeOn) mergeGridState else fileGridState
                    snapshotFlow {
                        val info = state.layoutInfo
                        (info.visibleItemsInfo.maxOfOrNull { it.index } ?: -1) to info.totalItemsCount
                    }.collect { (last, total) ->
                        if (total > 0 && last >= total - 24) vm.loadMoreChunks()
                    }
                }
                // 照片网格：按日期分节（节头占满一行），组内从新到旧；合并模式一格=一对
                if (mergeOn) {
                    // 置顶日期胶囊：固定槽位显示当前分组（随滚动更新，不与照片重叠）+ 总张数胶囊
                    // derivedStateOf：滑动中每帧的 layoutInfo 变化仅在跨分组时才输出新值，
                    // 避免 MainScreen 每帧重组（那会让所有可见格子跟着重建 → 滑动卡顿）
                    val pillSec by remember(vm.pairSections, vm.collapsedDates.value) {
                        derivedStateOf {
                            val firstIdx = mergeGridState.layoutInfo.visibleItemsInfo.minOfOrNull { it.index }
                                ?: return@derivedStateOf null
                            var acc = 0
                            var key: String? = null
                            for (sec in vm.pairSections) {
                                // 折叠节只渲染节头 1 个 item，映射需同步（否则置顶胶囊日期错位）
                                val cnt = 1 + if (sec.dateKey in vm.collapsedDates.value) 0 else sec.rows.size
                                if (firstIdx >= acc && firstIdx <= acc + cnt - 1) { key = sec.dateKey; break }
                                acc += cnt
                            }
                            vm.pairSections.firstOrNull { it.dateKey == key }
                        }
                    }
                    val sec = pillSec   // 委托属性无法 smart cast，先固化局部值
                    PinnedPillSlot(
                        dateText = sec?.let { vm.dateLabel(it.dateKey, it.rows.size) },
                        countText = if (vm.chunkedLoad.value) "已加载 ${pairs.size}/${vm.fullVisibleCount()} 张"
                        else "共 ${pairs.size} 张"
                    )
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
                            val dayCollapsed = sec.dateKey in vm.collapsedDates.value
                            item(key = "phdr_${sec.dateKey}", span = { GridItemSpan(maxLineSpan) }, contentType = "hdr") {
                                DateHeaderPill(
                                    dateKey = sec.dateKey,
                                    totalCount = sec.rows.size,
                                    collapsed = dayCollapsed,
                                    selectedInDay = sec.rows.count { it.key in vm.pairSelection.value },
                                    onToggle = { vm.toggleDateCollapsed(sec.dateKey) }
                                )
                            }
                            if (!dayCollapsed) {
                                items(sec.rows, key = { it.key }, contentType = { "photo" }) { pair ->
                                    PairCell(
                                        pair = pair,
                                        highlight = pair.key == highlightPairKey,
                                        pulseArmed = highlightPulseArmed,
                                        onPulsePlayed = { highlightPulseArmed = false },
                                        onTap = {
                                            highlightHandle = null
                                            highlightPairKey = null
                                            pairPreviewIndex = pairs.indexOf(pair)
                                        },
                                        onLongPress = { vm.togglePair(pair.key) }
                                    )
                                }
                            }
                        }
                    }
                    // 快速滚动条覆盖层（合并/文件共用）：阈值判断 + 显示层 + 拖动层
                    GridScrollbarOverlay(
                        vm = vm,
                        state = mergeGridState,
                        sections = vm.pairSections.map { it.dateKey to it.rows.size },
                        collapsedDates = vm.collapsedDates.value,
                        settings = sbActiveSettings,
                        onDragging = { sbDragging = it }
                    )
                    }
                } else {
                    // 置顶日期胶囊：固定槽位显示当前分组（随滚动更新，不与照片重叠）+ 总张数胶囊
                    // derivedStateOf：滑动中每帧的 layoutInfo 变化仅在跨分组时才输出新值，
                    // 避免 MainScreen 每帧重组（那会让所有可见格子跟着重建 → 滑动卡顿）
                    val pillSec by remember(vm.visibleSections, vm.collapsedDates.value) {
                        derivedStateOf {
                            val firstIdx = fileGridState.layoutInfo.visibleItemsInfo.minOfOrNull { it.index }
                                ?: return@derivedStateOf null
                            var acc = 0
                            var key: String? = null
                            for (sec in vm.visibleSections) {
                                // 折叠节只渲染节头 1 个 item，映射需同步（否则置顶胶囊日期错位）
                                val cnt = 1 + if (sec.dateKey in vm.collapsedDates.value) 0 else sec.rows.size
                                if (firstIdx >= acc && firstIdx <= acc + cnt - 1) { key = sec.dateKey; break }
                                acc += cnt
                            }
                            vm.visibleSections.firstOrNull { it.dateKey == key }
                        }
                    }
                    val sec = pillSec   // 委托属性无法 smart cast，先固化局部值
                    PinnedPillSlot(
                        dateText = sec?.let { vm.dateLabel(it.dateKey, it.rows.size) },
                        countText = if (vm.chunkedLoad.value) "已加载 ${visible.size}/${vm.fullVisibleCount()} 张"
                        else "共 ${visible.size} 张"
                    )
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
                        val dayCollapsed = sec.dateKey in vm.collapsedDates.value
                        item(key = "hdr_${sec.dateKey}", span = { GridItemSpan(maxLineSpan) }, contentType = "hdr") {
                            DateHeaderPill(
                                dateKey = sec.dateKey,
                                totalCount = sec.rows.size,
                                collapsed = dayCollapsed,
                                selectedInDay = sec.rows.count { it.selected.value },
                                onToggle = { vm.toggleDateCollapsed(sec.dateKey) }
                            )
                        }
                        if (!dayCollapsed) {
                            items(sec.rows, key = { it.handle }, contentType = { "photo" }) { row ->
                                GridCell(
                                    row = row,
                                    highlight = row.handle == highlightHandle,
                                    pulseArmed = highlightPulseArmed,
                                    onPulsePlayed = { highlightPulseArmed = false },
                                    onTap = {
                                        highlightHandle = null
                                        highlightPairKey = null
                                        previewIndex = visible.indexOf(row)
                                    },
                                    onLongPress = { row.selected.value = !row.selected.value }
                                )
                            }
                        }
                    }
                }
                    // 快速滚动条覆盖层（合并/文件共用）：阈值判断 + 显示层 + 拖动层
                    GridScrollbarOverlay(
                        vm = vm,
                        state = fileGridState,
                        sections = vm.visibleSections.map { it.dateKey to it.rows.size },
                        collapsedDates = vm.collapsedDates.value,
                        settings = sbActiveSettings,
                        onDragging = { sbDragging = it }
                    )
                    }
                } // else: 文件模式网格结束
                // 多选操作条：有选中才从底部弹出（全选 + 格式勾选(合并) + 下载所选）
                AnimatedVisibility(
                    visible = selCount > 0,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(UiSpec.STANDARD)),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(UiSpec.QUICK))
                ) {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        // 分格式计数（已应用"跳过已下载"过滤；格式勾掉时其标签变灰）
                        val skip = vm.skipDownloadedOn
                        val jpgCount: Int
                        val nefCount: Int
                        if (mergeOn) {
                            val selPairs = pairs.filter { it.key in vm.pairSelection.value }
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
                                    else if (mergeOn) vm.pairSelection.value = pairs.map { it.key }.toSet()
                                    else visible.forEach { it.selected.value = true }
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = allSel, onCheckedChange = null)
                                Text("全选", style = MaterialTheme.typography.bodyMedium)
                            }
                            // 选当天：勾上=选中"当前日期"（顶部置顶胶囊所示分节）的全部可见照片；
                            // 再点一次=取消该天。折叠天照片同样参与（只藏不见语义）。
                            val curDayKey by remember(mergeOn) {
                                derivedStateOf {
                                    val state = if (mergeOn) mergeGridState else fileGridState
                                    val firstIdx = state.layoutInfo.visibleItemsInfo.minOfOrNull { it.index }
                                        ?: return@derivedStateOf null
                                    // 统一投影为 (dateKey, 行数)：PairSection 与 DateSection 是两个类
                                    val sections: List<Pair<String, Int>> =
                                        if (mergeOn) vm.pairSections.map { it.dateKey to it.rows.size }
                                        else vm.visibleSections.map { it.dateKey to it.rows.size }
                                    var acc = 0
                                    var key: String? = null
                                    for ((d, n) in sections) {
                                        // 折叠节只渲染节头 1 个 item，索引映射需同步
                                        val cnt = 1 + if (d in vm.collapsedDates.value) 0 else n
                                        if (firstIdx >= acc && firstIdx <= acc + cnt - 1) { key = d; break }
                                        acc += cnt
                                    }
                                    key
                                }
                            }
                            val dk = curDayKey   // 委托属性无法 smart cast，先固化局部值
                            if (dk != null) {
                                val dayAllOn = if (mergeOn) {
                                    val rows = pairs.filter { it.stamp.startsWith(dk) }
                                    rows.isNotEmpty() && rows.all { it.key in vm.pairSelection.value }
                                } else {
                                    val rows = visible.filter { it.stamp.startsWith(dk) }
                                    rows.isNotEmpty() && rows.all { it.selected.value }
                                }
                                Row(
                                    Modifier.clickable {
                                        if (mergeOn) {
                                            val keys = pairs.filter { it.stamp.startsWith(dk) }.map { it.key }
                                            val allOn = keys.isNotEmpty() && keys.all { it in vm.pairSelection.value }
                                            vm.pairSelection.value =
                                                if (allOn) vm.pairSelection.value - keys.toSet()
                                                else vm.pairSelection.value + keys.toSet()
                                        } else {
                                            val rows = visible.filter { it.stamp.startsWith(dk) }
                                            val allOn = rows.all { it.selected.value }
                                            rows.forEach { it.selected.value = !allOn }
                                        }
                                    }.padding(start = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = dayAllOn, onCheckedChange = null)
                                    Text("选当天", style = MaterialTheme.typography.bodyMedium)
                                }
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
                        val pairKey = pairs.getOrNull(page)?.key
                        highlightPairKey = pairKey
                        highlightPulseArmed = true
                        // 网格跳到刚预览的对所在位置（已在可视区则不动）
                        if (pairKey != null) {
                            var idx = 0
                            var found = false
                            for (sec in vm.pairSections) {
                                if (found) break
                                idx++   // 日期分组头
                                val pos = sec.rows.indexOfFirst { it.key == pairKey }
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
                        highlightPairKey = null
                        val handle = visible.getOrNull(page)?.handle
                        highlightHandle = handle
                        highlightPulseArmed = true
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
                enter = fadeIn(tween(UiSpec.QUICK)),
                exit = fadeOut(tween(UiSpec.QUICK)),
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
                enter = slideInHorizontally(tween(UiSpec.EMPHASIS)) { -it } + fadeIn(tween(UiSpec.QUICK)),
                exit = slideOutHorizontally(tween(UiSpec.EMPHASIS)) { -it } + fadeOut(tween(UiSpec.QUICK)),
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
                enter = fadeIn(tween(UiSpec.QUICK)),
                exit = fadeOut(tween(UiSpec.QUICK)),
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
                enter = slideInHorizontally(tween(UiSpec.EMPHASIS)) { it } + fadeIn(tween(UiSpec.QUICK)),
                exit = slideOutHorizontally(tween(UiSpec.EMPHASIS)) { it } + fadeOut(tween(UiSpec.QUICK)),
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
    internal fun openInGallery(name: String, type: String) {
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

    /** 顶栏下载队列按钮（独立函数：缩小 MainScreen 巨型方法体，规避 d8 超大方法 dex 优化 bug）。
     *  无任务=下载图标；有任务=数字直接替换图标（同款 34dp 方框圆角，青绿底白字，99+ 封顶）。 */
    @Composable
    private fun TopBarDownloadButton(onClick: () -> Unit) {
        val active = vm.activeDownloadCount
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                .background(
                    if (active > 0)
                        UiSpec.accentDeep(vm.darkModeOn)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (active > 0) {
                RollingText(
                    if (active > 99) "99+" else "$active",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            } else {
                DownloadIcon(MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    /** 连接状态/错误信息卡片：双卡下方统一承载——连接中显示探测/扫描进度（带转圈），
     *  断开时显示失败原因/取消提示/失联信息。出现 = 淡入+向下展开；消失 = 淡出收起。 */
    @Composable
    private fun ConnInfoCard(text: String, loading: Boolean = false) {
        AnimatedVisibility(
            visible = text.isNotEmpty(),
            enter = fadeIn(tween(UiSpec.STANDARD)) + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut(tween(UiSpec.QUICK)) + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(UiSpec.ROUND_SMALL),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 高度锁定 48dp（容两行）：转圈/文字都在其中居中，连接中↔断开切换卡片位置恒定
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (loading) MaterialTheme.colorScheme.onSurfaceVariant
                            else UiSpec.error(vm.darkModeOn),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    /** 数字滚动文本：内容变化时旧值上滑淡出、新值自下滑入（队列计数、顶栏角标等微交互） */
    @Composable
    internal fun RollingText(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        style: TextStyle = MaterialTheme.typography.labelSmall
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (slideInVertically(tween(UiSpec.QUICK)) { it } + fadeIn(tween(UiSpec.QUICK))) togetherWith
                    (slideOutVertically(tween(UiSpec.QUICK)) { -it } + fadeOut(tween(UiSpec.QUICK)))
            },
            modifier = modifier,
            label = "rollingNum"
        ) { t ->
            Text(t, color = color, style = style, maxLines = 1)
        }
    }

}