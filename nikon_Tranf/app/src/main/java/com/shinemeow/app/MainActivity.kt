package com.shinemeow.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toIntSize
import androidx.core.content.ContextCompat
import com.shinemeow.app.ui.theme.ShineMeowTheme
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
/* [SCROLLBAR-SEALED] 快速滚动条封存：重启时随标记恢复
import my.nanihadesuka.compose.InternalLazyVerticalGridScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings
[/SCROLLBAR-SEALED] */
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    internal val vm: MainViewModel by viewModels()

    /** 主界面内容的离屏录制层（MainScreen 组合期赋值）：设置/下载/筛选/弹窗等
     *  玻璃面用它回绘主内容的模糊像（毛玻璃底材）。弹窗在独立窗口，也取同一层。 */
    internal var glassSource: androidx.compose.ui.graphics.layer.GraphicsLayer? = null
    /** 全屏预览页的录制层（预览在主 Column 外、不在 glassSource 里）；
     *  预览打开时弹窗玻璃底材要先画 glassSource 再叠本层，才能透出预览正后方内容 */
    internal var glassSourcePreview: androidx.compose.ui.graphics.layer.GraphicsLayer? = null

    /** 主界面根 Box 的布局坐标：玻璃面用 localPositionOf 换算含 graphicsLayer 位移的
     *  屏幕位置（AnimatedVisibility 滑入是位移动画，positionInRoot 不够）。 */
    internal var glassRootCoords: androidx.compose.ui.layout.LayoutCoordinates? = null

    /** 主窗口 decorView：判断玻璃面是否在主窗口内（弹窗/底部弹层是独立窗口） */
    internal var glassMainDecor: android.view.View? = null

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

    /** Activity 已在栈顶时 am start 走的是 onNewIntent，不是 onCreate ——
     *  只处理 onCreate 会让 `--ez demo true` 静默失效（实测表现为演示数据没加载，
     *  测出来"很流畅"其实是空网格在空转）。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("demo", false)) vm.setDemoMode(true)
        // 插线拉起（singleTask 复用实例）：intent 携带 USB 设备时走与 ATTACHED 广播
        // 相同的连接路径（VM 内 connecting/connected 守卫保证幂等，双触发只连一次）
        vm.handleUsbLaunchIntent(intent)
    }

    /* ---------- 自动化性能测量入口（仅 debuggable 包注册，release 零影响）----------
     * 存在的理由：手动进设置页开演示模式、再手滑，采样窗口不可控、结果不可复现，
     * 前后对比就没有意义。有了它就能用 adb 跑同一套动作：
     *   adb shell am start -n com.shinemeow.app/.MainActivity --ez demo true
     *   adb shell am broadcast -a com.shinemeow.app.PERF --es cmd reset
     *   adb shell am broadcast -a com.shinemeow.app.PERF --es cmd select --ei n 200
     *   adb shell am broadcast -a com.shinemeow.app.PERF --es cmd download --ei n 20
     *   adb shell am broadcast -a com.shinemeow.app.PERF --es cmd dump --es tag scroll
     * 读数走 logcat -s Perf。 */
    private val perfReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getStringExtra("cmd")) {
                "reset" -> { PerfTrace.reset(); Log.i(PerfTrace.TAG, "PERF reset") }
                "select" -> {
                    val n = i.getIntExtra("n", 100)
                    vm.setSelectedAll(vm.visiblePhotos.take(n), true)
                    Log.i(PerfTrace.TAG, "PERF select $n")
                }
                "clear" -> vm.clearSelection()
                "openDownloads" -> vm.requestOpenDownloads()
                "openSettings" -> vm.requestOpenSettings()
                "download" -> vm.startDemoDownloads(i.getIntExtra("n", 20))
                "enqueue" -> vm.enqueueDemoItems(i.getIntExtra("n", 20))
                "step" -> vm.demoStepKb.value = i.getIntExtra("n", 64)
                "tween" -> vm.progressTweenMs.value = i.getIntExtra("n", 500)
                // 三组对照：纯空转 / 写状态但无人订阅 / 写状态且 UI 订阅
                "spin" -> vm.spinOnly()
                "write" -> vm.spinWriting(false)
                "writelive" -> vm.spinWriting(true)
                "stopdl" -> vm.stopDemoDownloads()
                "stopSpin" -> vm.stopSpin()
                "hidebar" -> vm.perfHideOverallBar.value = !vm.perfHideOverallBar.value
                "hidefill" -> vm.perfHideRowFill.value = !vm.perfHideRowFill.value
                "hidethumb" -> vm.perfHideRowThumb.value = !vm.perfHideRowThumb.value
                "throttle" -> UiSpec.PROGRESS_THROTTLE_MS = i.getIntExtra("n", 250).toLong()
                "dump" -> dumpPerf(i.getStringExtra("tag") ?: "dump")
            }
        }
    }

    /** 一次性把帧率统计与派生路径重算次数打成一行，便于脚本抓取 */
    private fun dumpPerf(tag: String) {
        val s = PerfTrace.snapshot()
        val names = listOf(
            "filterPhotos#重算", "filterPairs#重算", "sections#重算",
            "pairSections#重算", "枚举", "thumb#加载"
        )
        val counters = names.joinToString(" | ") { n ->
            val c = PerfTrace.countOf(n)
            val a = PerfTrace.avgMs(n)
            when {
                c == 0L -> "$n=-"
                a != null && a > 0f -> "$n=${c}次/均${"%.2f".format(a)}ms"
                else -> "$n=${c}次"
            }
        }
        // rows/visible 是"网格真的渲染了"的校验位：Choreographer 即使完全空闲也按
        // 刷新率回调，fps≈120 不能证明界面在画东西。
        Log.i(
            PerfTrace.TAG,
            "PERF[$tag] fps=${"%.1f".format(s.fps)} jank=${"%.1f".format(s.jankPct)}% " +
                "p50=${"%.1f".format(s.p50Ms)} p95=${"%.1f".format(s.p95Ms)} " +
                "max=${"%.1f".format(s.maxMs)} frames=${s.windowFrames}/${s.totalFrames} " +
                "budget=${"%.1f".format(s.budgetMs)}ms(${s.hz.toInt()}Hz) " +
                "rows=${vm.photoRows.size} visible=${vm.visiblePhotos.size} sel=${vm.selectedCount.value} " +
                "q=${vm.downloadQueue.size} :: $counters"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        glassMainDecor = window.decorView
        // 自动化入口：adb shell am start ... --ez demo true 直接进演示模式
        if (intent?.getBooleanExtra("demo", false) == true) vm.setDemoMode(true)
        // 冷启动插线拉起：intent 携带 USB 设备时直接走连接路径（与 onNewIntent 同款幂等），
        // 不依赖 VM init 的 800ms 扫描兜底
        vm.handleUsbLaunchIntent(intent)
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
        // 帧率采样常驻启动（幂等）：相机连着时没有 adb 通道，只能自己采样。
        // 传入实际刷新率 —— 这台机跑 120Hz，按 60Hz 预算算掉帧率会永远 ≈0。
        val hz = if (Build.VERSION.SDK_INT >= 30) (display?.refreshRate ?: 60f) else 60f
        PerfTrace.attach(hz)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val f = IntentFilter("com.shinemeow.app.PERF")
            // 必须 EXPORTED：adb shell 的 uid 是 2000，NOT_EXPORTED 会把它挡在外面
            // （只放通同 UID / system）。这是 debug 包专属入口，无敏感数据。
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(perfReceiver, f, Context.RECEIVER_EXPORTED)
            else registerReceiver(perfReceiver, f)
        }
        setContent {
            ShineMeowTheme(dark = vm.darkModeOn) {
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
        vm.refreshHotspot()   // 从热点设置页返回时立即刷新提示卡状态
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
        var showRemote by remember { mutableStateOf(false) }   // 遥控拍摄页（任务 6）
        // 自动化测量入口：VM 下发"打开某页"的意图（计数器变化即触发一次）
        LaunchedEffect(vm.openSettingsRequest.value) {
            if (vm.openSettingsRequest.value > 0) showSettings = true
        }
        LaunchedEffect(vm.openDownloadsRequest.value) {
            if (vm.openDownloadsRequest.value > 0) showDownloads = true
        }
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
        // 文件模式选中数改读 VM 计数器：旧写法 `photoRows.count { it.selected.value }`
        // 订阅了每一行的 selected —— 勾一格会让整个 MainScreen（含两个网格）重组，
        // 再叠加多选栏展开动画每帧改高度 → 网格每帧 re-measure → 勾选/滑动卡顿。
        val selCount = if (mergeOn) pairSelCount else vm.selectedCount.value

        // 返回键优先级（Compose 中先注册者优先级最低）：多选退出 → 预览关闭 → 下载页/设置页回主页。
        // 多选时点进预览，按返回先关预览回多选（多选 handler 必须最先注册，否则会抢走预览的返回键）
        BackHandler(enabled = selCount > 0 && pairPreviewIndex < 0 && previewIndex < 0) {
            vm.clearSelection()
        }
        BackHandler(enabled = pairPreviewIndex >= 0) { pairPreviewIndex = -1 }
        BackHandler(enabled = previewIndex >= 0) { previewIndex = -1 }
        BackHandler(enabled = showDownloads) { showDownloads = false }
        BackHandler(enabled = showRemote) { vm.closeRemote(); showRemote = false }
        BackHandler(enabled = showSettings) { showSettings = false }

        // 机内删除后预览停留：rows 快照缩短 → Pager 当前页不变、自然落到下一张
        // （用户要求的行为）；删到末尾越界时由 Pager 内 LaunchedEffect(rows.size) 负责关闭

        // 毛玻璃：仅全屏预览打开时主界面内容整体模糊（API31+，低版本自动退化为半透明黑）。
        // 设置/下载/筛选/弹窗的玻璃面不走全局模糊 —— 背景保持清晰，只在玻璃面自身
        // 范围内回绘主内容的模糊像（见 glassBackdropModifier，与删除气泡同款手法）
        val previewing = (mergeOn && pairPreviewIndex >= 0) || (!mergeOn && previewIndex >= 0)

        // 网格跟随预览（预览 onFollow 回调调用）：目标 item 完整可见则不动；
        // 可见但不完整 → animateScrollBy 补差动画对齐（下方露出不足贴到底、
        // 顶部露出不足顶到顶）；完全不可见 → **分段平滑滚动**直到目标进入视口
        // 再精确对齐——animateScrollToItem 对不可见目标内部是先瞬移再对齐
        // （"向上整格跳没有动画"的来源），animateScrollBy 才是真正的滚动动画。
        fun followGrid(state: LazyGridState, idx: Int) {
            scope.launch {
                var guard = 0
                while (guard++ < 30) {   // 安全闸：最多滚 30 段（≈20+ 屏），防异常死循环
                    val li = state.layoutInfo
                    val item = li.visibleItemsInfo.firstOrNull { it.index == idx }
                    if (item != null) {
                        val h = item.size.height
                        if (item.offset.y >= li.viewportStartOffset &&
                            item.offset.y + h <= li.viewportEndOffset
                        ) break   // 完整可见：到位
                        // 不完整：最小距离对齐（底部露出不足 → 向下补差；顶部 → 向上补差）
                        val delta = if (item.offset.y + h > li.viewportEndOffset)
                            (item.offset.y + h - li.viewportEndOffset).toFloat()
                        else (item.offset.y - li.viewportStartOffset).toFloat()
                        state.animateScrollBy(delta, tween(UiSpec.FOLLOW_SCROLL))
                        break
                    }
                    // 完全不可见：每次只滚**一行**（行高 + 行间距）——跟随手感 =
                    // 翻一张网格跟一行；连翻数张时逐行追赶，绝不滚过头
                    val last = li.visibleItemsInfo.lastOrNull()?.index ?: break
                    val down = idx > last
                    val stride = (li.visibleItemsInfo.firstOrNull { it.contentType == "photo" }?.size?.height
                        ?: (li.viewportEndOffset - li.viewportStartOffset) / 3) + li.mainAxisItemSpacing
                    state.animateScrollBy(
                        if (down) stride.toFloat() else -stride.toFloat(),
                        tween(UiSpec.FOLLOW_SCROLL)
                    )
                }
            }
        }
        // 删除成功气泡的毛玻璃底材：主内容每帧录进离屏层（contentLayer，不带模糊，
        // 正常显示走它），气泡内再把它平移回绘进 glassLayer 并施加 24dp 模糊 ——
        // 模糊只作用于气泡区域，不能设在 contentLayer 上（否则整屏跟着糊）。
        // 网格/全屏预览都在这个 Column 里 → 气泡背后无论是什么都能截到（API31+；
        // 低版本 renderEffect 不生效，气泡自动退化为纯半透明底）
        val contentLayer = rememberGraphicsLayer()
        glassSource = contentLayer
        // 预览页录制层：预览打开时玻璃面（弹窗等）底材在主内容之上再叠预览层，
        // 透出的才是弹窗正后方的预览内容；关闭时置空，玻璃底材回落到纯主内容
        val previewLayer = rememberGraphicsLayer()
        glassSourcePreview = if (previewing) previewLayer else null
        val glassLayer = rememberGraphicsLayer()
        if (Build.VERSION.SDK_INT >= 31) {
            glassLayer.renderEffect = BlurEffect(UiSpec.GLASS_BLUR_SOFT, UiSpec.GLASS_BLUR_SOFT, TileMode.Clamp)
        }
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { glassRootCoords = it }
        ) {
            Column(
                Modifier.fillMaxSize()
                    .drawWithContent {
                        contentLayer.record { this@drawWithContent.drawContent() }   // 录制本帧内容（不重复上屏）
                        drawLayer(contentLayer)                // 正常显示走离屏层（无模糊）
                    }
                    .graphicsLayer {
                        if (previewing && Build.VERSION.SDK_INT >= 31) {
                            renderEffect = android.graphics.RenderEffect
                                .createBlurEffect(UiSpec.GLASS_BLUR_SOFT, UiSpec.GLASS_BLUR_SOFT, android.graphics.Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        } else {
                            renderEffect = null
                        }
                    }
                    // ★ 顶栏垂直位置：safeDrawing 含状态栏实际高度（机型自适应，
                    //    刘海/挖孔一并覆盖），topBarGapDp 只是「状态栏到顶栏」的余量
                    //    （设置·外观可调，默认 UiSpec.TOPBAR_GAP）
                    //    ——数字越大越往下，越小越往上，0 = 紧贴状态栏
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = UiSpec.PAGE_PAD, end = UiSpec.PAGE_PAD, bottom = UiSpec.PAGE_PAD)
                    .padding(top = vm.topBarGapDp.value.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val phase = connPhase.value
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
                            .size(UiSpec.TOPBAR_BTN)
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
                                .padding(start = UiSpec.TOPBAR_BTN_GAP)
                                .size(UiSpec.TOPBAR_BTN)
                                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showFilter = true },
                            contentAlignment = Alignment.Center
                        ) {
                            FunnelIcon(if (vm.filterActive) UiSpec.accent(vm.darkModeOn) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // 连接指示（常驻，筛选旁，同款圆角外框）：
                        // 已连接=当前通道图标（USB 蓝 / Wi-Fi 青）；连接中=琥珀色；断开=断开前通道图标红色
                        // 已连接时可点击 → 弹出连接详情浮层（型号/序列号/通道 + 断开；无重连入口），3 秒自动收起
                        val red = phase == "disconnected"
                        val amber = phase == "connecting"
                        val isUsbConn = vm.lastConnChannel.value == "usb"
                        Box(
                            Modifier
                                .padding(start = UiSpec.TOPBAR_BTN_GAP)
                                .graphicsLayer { translationX = connShake.value * 6.dp.toPx() }
                                .size(UiSpec.TOPBAR_BTN)
                                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .then(
                                    if (phase == "connected") Modifier.clickable {
                                        vm.showConnDetail.value = !vm.showConnDetail.value
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val iconColor by animateColorAsState(
                                when {
                                    red -> UiSpec.error(vm.darkModeOn)
                                    amber -> UiSpec.amber(vm.darkModeOn)
                                    isUsbConn -> UiSpec.usbBlue(vm.darkModeOn)
                                    else -> UiSpec.accent(vm.darkModeOn)
                                },
                                animationSpec = tween(UiSpec.STANDARD),
                                label = "connIconColor"
                            )
                            if (isUsbConn) {
                                UsbIcon(iconColor, Modifier.size(UiSpec.ICON_STD))
                            } else {
                                WifiIcon(iconColor, Modifier.size(UiSpec.ICON_STD))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // 短/长按反转快捷开关（JPG/NEF 合并按钮旁，D 方案文字徽章）：
                        // 与设置页"短按选中、长按预览"同一开关（setSwapPress 持久化）。
                        // 开 = 短按选中/长按预览（accent 绿），关 = 默认短按预览/长按选中（灰）
                        val swapIconColor by animateColorAsState(
                            if (vm.swapPressOn.value) UiSpec.accent(vm.darkModeOn)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(UiSpec.STANDARD),
                            label = "swapPressIconColor"
                        )
                        Box(
                            Modifier
                                .padding(start = UiSpec.TOPBAR_BTN_GAP)
                                .size(UiSpec.TOPBAR_BTN)
                                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { vm.setSwapPress(!vm.swapPressOn.value) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "短⇆长",
                                color = swapIconColor,
                                fontSize = UiSpec.BADGE_FONT,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        // 合并 RAW/JPG 快捷开关（下载队列左侧）：两枚类型小徽章
                        // （JPG 青 / NEF 紫 = 队列行类型徽章同色，白字缩写）——
                        // 合并态徽章斜向贴合叠成一摞，文件态上下完全分开，动画过渡；
                        // 与设置页"合并展示"同一开关（setMergePairs 持久化 + 清空多选）
                        // 合并态：徽章斜向贴合叠成一摞（±3dp/2dp 重叠）；
                        // 文件态：上下两枚完全分离（±7dp 垂直，水平归零不重叠）
                        val offX by animateDpAsState(
                            if (mergeOn) 3.dp else 0.dp,
                            tween(UiSpec.STANDARD), label = "mergeBadgeX"
                        )
                        val offY by animateDpAsState(
                            if (mergeOn) 2.dp else 7.dp,
                            tween(UiSpec.STANDARD), label = "mergeBadgeY"
                        )
                        Box(
                            Modifier
                                .padding(start = UiSpec.TOPBAR_BTN_GAP)
                                .size(UiSpec.TOPBAR_BTN)
                                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { vm.setMergePairs(!mergeOn) },
                            contentAlignment = Alignment.Center
                        ) {
                            TypeBadge("NEF", badgeColor("NEF"), Modifier.offset(x = offX, y = -offY))
                            TypeBadge("JPG", badgeColor("JPG"), Modifier.offset(x = -offX, y = offY))
                        }
                        Spacer(Modifier.width(UiSpec.TOPBAR_BTN_GAP))
                        // 遥控拍摄入口（任务 6）：相机图标，进入全屏遥控页（取景+快门+对焦+录像）
                        Box(
                            Modifier
                                .size(UiSpec.TOPBAR_BTN)
                                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    vm.openRemote()
                                    showRemote = true
                                },
                            contentAlignment = Alignment.Center
                        ) { RemoteIcon(MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.width(UiSpec.TOPBAR_BTN_GAP))
                        // 下载队列按钮：无任务=下载图标；有任务=数字直接替换图标（青绿底白字，99+ 封顶）
                        TopBarDownloadButton(onClick = { showDownloads = true })
                    }
                }
                // 详情浮层 + 状态条收纳进内层 Column（无 spacedBy：卡片移除时不会带走间距导致下方跳动）
                Column {
                // 详情浮层 3 秒自动收起（再次点击图标可提前关闭；离开组合自动取消）
                LaunchedEffect(showConnDetail.value) {
                    if (showConnDetail.value) {
                        kotlinx.coroutines.delay(3000)
                        vm.showConnDetail.value = false
                    }
                }
                // 连接详情浮层：点击顶栏连接图标后显示 3 秒（展开+淡入 / 收起+淡出）
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
                        color = UiSpec.connChipBg(vm.darkModeOn, isUsb)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "已连接：$cm ($cs) · ${if (isUsb) "USB" else connectedIp.value}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UiSpec.connChipText(vm.darkModeOn, isUsb)
                                )
                                // 存储容量（0x1005 GetStorageInfo；相机不报/读取失败时隐藏该行）
                                val st = vm.camStorage.value
                                if (st != null) {
                                    Text(
                                        "存储：剩余 ${humanSize(st.free)} / ${humanSize(st.capacity)}" +
                                            if (st.freeImages >= 0) "（约 ${st.freeImages} 张）" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = UiSpec.connChipText(vm.darkModeOn, isUsb)
                                            .copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                // 受保护张数已上移到置顶胶囊槽（小锁+数字，0 张不显示），
                                // 浮层只保留连接信息与容量两行
                            }
                            // 与全局操作按钮同风格（描边胶囊 ActionPill），不再是裸 TextButton
                            ActionPill("断开") {
                                scope.launch { withContext(Dispatchers.IO) { vm.disconnect() } }
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
                            hotspotOn = vm.hotspotOnState
                        )
                        // 热点未开提示卡：仅未连接时展开（USB 连接中不干扰），点按跳系统热点设置
                        HotspotHintCard(
                            visible = vm.hotspotHintOn.value && !vm.hotspotOnState && phase == "disconnected",
                            topGap = 12.dp,
                            onClick = { openHotspotSettings() }
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
                                hotspotOn = vm.hotspotOnState,
                                active = wifiFull,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    // 热点未开提示卡（同全屏双卡）：仅断开时展开
                    HotspotHintCard(
                        visible = vm.hotspotHintOn.value && !vm.hotspotOnState && phase == "disconnected",
                        topGap = 8.dp,
                        onClick = { openHotspotSettings() }
                    )
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
                            "没有符合条件的照片",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ActionPill("清除筛选") { vm.resetFilter() }
                    }
                }
                /* [SCROLLBAR-SEALED] 快速滚动条封存：设置与拖动状态一并停用，重启时随标记恢复
                // 快速滚动条（LazyColumnScrollbar 库）：>50 张启用；滚动时自动显现。
                // 拖动映射自管：库的映射假设每行等高，日期头/照片行高悬殊导致交界跳动——
                // 这里禁用库手势（Disabled），另叠像素级 scrollBy 拖动层，增量滚动无跳变。
                val sbSettings = ScrollbarSettings(
                    selectionMode = ScrollbarSelectionMode.Disabled,
                    thumbThickness = UiSpec.SCROLLBAR_THICKNESS,
                    scrollbarPadding = 3.dp,
                    thumbMinLength = 0.05f,
                    thumbUnselectedColor = UiSpec.accent(vm.darkModeOn),
                    thumbSelectedColor = UiSpec.thumbSel(vm.darkModeOn),
                    hideDelayMillis = 600
                )
                // 拖动滚动条期间强制常显：scrollBy 事件间隙 isScrollInProgress 抖动会导致渐隐
                var sbDragging by remember { mutableStateOf(false) }
                val sbActiveSettings = if (sbDragging) sbSettings.copy(alwaysShowScrollbar = true) else sbSettings
                [/SCROLLBAR-SEALED] */
                // 分块加载：滚动接近已加载区域末尾（24 项 ≈ 8 行）时自动追加下一块；
                // 设置里关闭分块时 loadMoreChunks 内部直接忽略。仅活动网格的状态被读取。
                LaunchedEffect(mergeOn) {
                    val state = if (mergeOn) mergeGridState else fileGridState
                    snapshotFlow {
                        val info = state.layoutInfo
                        (info.visibleItemsInfo.maxOfOrNull { it.index } ?: -1) to info.totalItemsCount
                    }.collect { (last, total) ->
                        if (total > 0 && last >= total - UiSpec.CHUNK_TRIGGER) vm.loadMoreChunks()
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
                        else "共 ${pairs.size} 张",
                        protCount = vm.protectedCount.value,
                        collapsed = sec != null && sec.dateKey in vm.collapsedDates.value,
                        onToggle = if (sec != null) ({
                            val wasCollapsed = sec.dateKey in vm.collapsedDates.value
                            vm.toggleDateCollapsed(sec.dateKey)
                            // 收起 → 下个日期节头对齐到置顶槽正下方（置顶气泡随滚动同步为该日期）；
                            // 展开 → 回到本节节头；收起末节回落本节头
                            val target = hdrIndexAfterToggle(
                                vm.pairSections.map { it.dateKey to it.rows.size },
                                vm.collapsedDates.value, sec.dateKey, !wasCollapsed
                            )
                            scope.launch { mergeGridState.animateScrollToItem(target) }
                        }) else null
                    )
                    // 免疫无障碍扫描：GKD/记账类服务监听 CONTENT_CHANGED 且全树遍历坐标，
                    // 网格几百节点 × 每帧滚动 = 主线程每帧 6-17ms 语义计算（实测 trace 热点）。
                    // 清空网格子树语义后对外只剩 1 个空节点，服务扫描成本归零（工具类 app 可接受）。
                    // 同款 ROUND_LARGE 圆角裁剪：网格区与底部浮层多选卡片圆角一致，
                    // 消除卡片旁网格的直角突兀感
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(UiSpec.ROUND_LARGE)).clearAndSetSemantics { }) {
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
                    /* [SCROLLBAR-SEALED] 快速滚动条封存：合并模式挂载点，重启时随标记恢复
                    // 快速滚动条覆盖层（合并/文件共用）：阈值判断 + 显示层 + 拖动层
                    GridScrollbarOverlay(
                        vm = vm,
                        state = mergeGridState,
                        sections = vm.pairSections.map { it.dateKey to it.rows.size },
                        collapsedDates = vm.collapsedDates.value,
                        settings = sbActiveSettings,
                        onDragging = { sbDragging = it }
                    )
                    [/SCROLLBAR-SEALED] */
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
                        else "共 ${visible.size} 张",
                        protCount = vm.protectedCount.value,
                        collapsed = sec != null && sec.dateKey in vm.collapsedDates.value,
                        onToggle = if (sec != null) ({
                            val wasCollapsed = sec.dateKey in vm.collapsedDates.value
                            vm.toggleDateCollapsed(sec.dateKey)
                            val target = hdrIndexAfterToggle(
                                vm.visibleSections.map { it.dateKey to it.rows.size },
                                vm.collapsedDates.value, sec.dateKey, !wasCollapsed
                            )
                            scope.launch { fileGridState.animateScrollToItem(target) }
                        }) else null
                    )
                    // 免疫无障碍扫描（同合并模式）：语义子树清空，服务扫描成本归零
                    // 同款 ROUND_LARGE 圆角裁剪：网格区与底部浮层多选卡片圆角一致，
                    // 消除卡片旁网格的直角突兀感
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(UiSpec.ROUND_LARGE)).clearAndSetSemantics { }) {
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
                                    onLongPress = { vm.toggleRowSelected(row) }
                                )
                            }
                        }
                    }
                }
                    /* [SCROLLBAR-SEALED] 快速滚动条封存：文件模式挂载点，重启时随标记恢复
                    // 快速滚动条覆盖层（合并/文件共用）：阈值判断 + 显示层 + 拖动层
                    GridScrollbarOverlay(
                        vm = vm,
                        state = fileGridState,
                        sections = vm.visibleSections.map { it.dateKey to it.rows.size },
                        collapsedDates = vm.collapsedDates.value,
                        settings = sbActiveSettings,
                        onDragging = { sbDragging = it }
                    )
                    [/SCROLLBAR-SEALED] */
                    }
                } // else: 文件模式网格结束
            }
            // 多选操作条：浮层玻璃卡片（有选中弹出/无选中消失，交互逻辑不变），
            // 效果与其他弹窗一致（40dp 模糊+同款着色/描边），底材=网格主内容；
            // 预览打开时收起（避免遮挡预览底部照片信息），退出预览自动弹回
            AnimatedVisibility(
                visible = selCount > 0 && !previewing,
                // slide+fade：expandVertically 会裁剪内容（阴影在边缘外被裁掉，
                // 动画结束瞬间阴影才出现，非常割裂）；slide 位移不裁剪，阴影全程跟随
                enter = slideInVertically(tween(UiSpec.STANDARD)) { it } + fadeIn(tween(UiSpec.STANDARD)),
                exit = slideOutVertically(tween(UiSpec.QUICK)) { it } + fadeOut(tween(UiSpec.QUICK)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 左右/底部比网格区（主 Column 的 PAGE_PAD 内缩）外扩
                    // SEL_CARD_OVERHANG：与网格边缘完全对齐显得过于"焊死"，
                    // 微微外扩更有浮层感；量在 UiSpec 统一调
                    .padding(
                        start = UiSpec.PAGE_PAD - UiSpec.SEL_CARD_OVERHANG,
                        end = UiSpec.PAGE_PAD - UiSpec.SEL_CARD_OVERHANG
                    )
                    .navigationBarsPadding()
                    .padding(bottom = UiSpec.PAGE_PAD - UiSpec.SEL_CARD_OVERHANG)
            ) {
                Surface(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            glassBackdropModifier(
                                shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
                                tint = UiSpec.glassTint(vm.darkModeOn),
                                border = UiSpec.glassBorder(vm.darkModeOn),
                                blur = UiSpec.GLASS_BLUR
                            )
                        ),
                    shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
                    color = Color.Transparent
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
                                    else vm.setSelectedAll(visible, true)
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
                                            vm.setSelectedAll(rows, !allOn)
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
                        ) { Text(if (allDownloaded) "所选照片均已下载" else "下载所选（$total）") }
                        // 机内删除（0x100B，ZENCHE 五端均无）：破坏性操作，
                        // VM 内预检（队列冲突/写保护）后弹确认框，不在这里直接删
                        OutlinedButton(
                            onClick = { scope.launch { withContext(Dispatchers.IO) { vm.requestDelete() } } },
                            enabled = selCount > 0 && !vm.deleteBusy.value,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = UiSpec.error(vm.darkModeOn)
                            )
                        ) { Text(if (vm.deleteBusy.value) "删除预检中…" else "删除所选（$selCount）") }
                        }
                    }
                }
            // 全屏预览（横向滑动翻页，只在筛选结果内翻；合并模式翻合并对）
            // 包录制 Box：预览内容录进 previewLayer 供弹窗玻璃底材回绘——
            // 预览在主 Column 外、不在 contentLayer 里，不录则玻璃透出的是网格
            if (mergeOn && pairPreviewIndex >= 0 && pairs.isNotEmpty()) {
                Box(Modifier.fillMaxSize().drawWithContent {
                    previewLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(previewLayer)   // 正常显示走录制层（无模糊）
                }) {
                PairPager(
                    initialIndex = pairPreviewIndex,
                    rows = pairs.toList(),
                    // 网格跟随预览：预览翻到哪，网格跟到哪（最小距离 + 动画）
                    onFollow = { pairKey ->
                        var idx = 0
                        var found = false
                        for (sec in vm.pairSections) {
                            if (found) break
                            idx++   // 日期分组头
                            val pos = sec.rows.indexOfFirst { it.key == pairKey }
                            if (pos >= 0) { idx += pos; found = true } else idx += sec.rows.size
                        }
                        if (found) followGrid(mergeGridState, idx)
                    },
                    onClose = { page ->
                        highlightHandle = null
                        highlightPairKey = pairs.getOrNull(page)?.key
                        highlightPulseArmed = true
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
                    },
                    onDelete = { pair ->
                        // 合并对删除 = 该张 JPG+NEF 一并删（确认框标注总张数）
                        val targets = listOfNotNull(pair.jpg, pair.nef)
                        if (targets.isNotEmpty()) {
                            scope.launch { withContext(Dispatchers.IO) { vm.requestDelete(targets) } }
                        }
                    }
                )
                }
            } else if (!mergeOn && previewIndex >= 0 && visible.isNotEmpty()) {
                Box(Modifier.fillMaxSize().drawWithContent {
                    previewLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(previewLayer)
                }) {
                PreviewPager(
                    initialIndex = previewIndex,
                    rows = visible.toList(),
                    // 网格跟随预览：预览翻到哪，网格跟到哪（最小距离 + 动画）
                    onFollow = { handle ->
                        var idx = 0
                        var found = false
                        for (sec in vm.visibleSections) {
                            if (found) break
                            idx++   // 日期分组头
                            val pos = sec.rows.indexOfFirst { it.handle == handle }
                            if (pos >= 0) { idx += pos; found = true } else idx += sec.rows.size
                        }
                        if (found) followGrid(fileGridState, idx)
                    },
                    onClose = { page ->
                        highlightPairKey = null
                        highlightHandle = visible.getOrNull(page)?.handle
                        highlightPulseArmed = true
                        previewIndex = -1
                    },
                    onDownload = { row ->
                        scope.launch { withContext(Dispatchers.IO) { vm.downloadOne(row) } }
                    },
                    onDelete = { row ->
                        scope.launch { withContext(Dispatchers.IO) { vm.requestDeleteOne(row) } }
                    }
                )
                }
            }
            // 筛选卡片（底部弹出，实时生效）
            if (showFilter) FilterSheet(onDismiss = { showFilter = false })
            // 机内删除确认框（VM 预检完成后弹出）：
            //   全部受保护 → 阻断式告知（无删除按钮，引导去机身取消保护）；
            //   部分受保护 → 正常确认 + 标注将被跳过的张数；
            //   无保护     → 正常确认。
            vm.deletePlan.value?.let { plan ->
                val allProt = plan.protectedCount > 0 && plan.protectedCount >= plan.total
                AlertDialog(
                    // 全屏 edge-to-edge 弹窗窗口：内容坐标与主窗口对齐，毛玻璃底材可精确回绘
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    ),
                    modifier = glassBackdropModifier(
                        shape = MaterialTheme.shapes.extraLarge,
                        tint = UiSpec.glassTint(vm.darkModeOn),
                        border = UiSpec.glassBorder(vm.darkModeOn),
                        blur = UiSpec.GLASS_BLUR,
                        shadow = UiSpec.GLASS_SHADOW
                    ),
                    containerColor = Color.Transparent,
                    onDismissRequest = { vm.cancelDeletePlan() },
                    title = { Text(if (allProt) "无法删除" else "删除相机中的 ${plan.total} 张照片？") },
                    text = {
                        Text(
                            buildString {
                                if (allProt) {
                                    append("所选 ").append(plan.total)
                                        .append(" 张照片在相机中设了保护，相机拒绝删除。\n")
                                    append("请先在相机上取消保护（播放/菜单里的「保护」），再回本 App 删除。")
                                } else {
                                    append("照片将从相机存储卡中永久删除，此操作不可恢复。\n")
                                    append("已下载到手机的副本不受影响。")
                                    if (plan.protectedCount > 0) {
                                        append("\n\n其中 ").append(plan.protectedCount)
                                            .append(" 张在相机中设了保护，将被跳过。")
                                    }
                                }
                            }
                        )
                    },
                    confirmButton = {
                        if (allProt) {
                            TextButton(onClick = { vm.cancelDeletePlan() }) { Text("好") }
                        } else {
                            TextButton(onClick = {
                                scope.launch { withContext(Dispatchers.IO) { vm.confirmDelete() } }
                            }) { Text("删除", color = UiSpec.error(vm.darkModeOn)) }
                        }
                    },
                    dismissButton = if (allProt) null else {
                        { TextButton(onClick = { vm.cancelDeletePlan() }) { Text("取消") } }
                    }
                )
            }
            // 机内删除结果汇总（仅受保护/失败/中断时弹出；全部成功静默不弹）
            vm.deleteResult.value?.let { msg ->
                AlertDialog(
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    ),
                    modifier = glassBackdropModifier(
                        shape = MaterialTheme.shapes.extraLarge,
                        tint = UiSpec.glassTint(vm.darkModeOn),
                        border = UiSpec.glassBorder(vm.darkModeOn),
                        blur = UiSpec.GLASS_BLUR,
                        shadow = UiSpec.GLASS_SHADOW
                    ),
                    containerColor = Color.Transparent,
                    onDismissRequest = { vm.dismissDeleteResult() },
                    title = { Text(vm.deleteResultTitle.value ?: "删除结果") },
                    text = { Text(msg) },
                    confirmButton = {
                        TextButton(onClick = { vm.dismissDeleteResult() }) { Text("好") }
                    }
                )
            }
            // 热点未开启提醒（本 App 主拓扑 = 相机连手机热点）
            if (hotspotHint) {
                AlertDialog(
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    ),
                    modifier = glassBackdropModifier(
                        shape = MaterialTheme.shapes.extraLarge,
                        tint = UiSpec.glassTint(vm.darkModeOn),
                        border = UiSpec.glassBorder(vm.darkModeOn),
                        blur = UiSpec.GLASS_BLUR,
                        shadow = UiSpec.GLASS_SHADOW
                    ),
                    containerColor = Color.Transparent,
                    onDismissRequest = { hotspotHint = false },
                    title = { Text("手机热点未开启") },
                    text = {
                        Text(
                            "传输照片需要相机连接到手机热点。\n\n" +
                                "请先打开手机热点，然后在相机菜单中执行「连接至 PC（Wi-Fi）」并选择本热点，完成后返回点击「仍然连接」。\n\n" +
                                "如果相机已通过其他方式组网（如相机热点），可点击「仍然连接」继续。"
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
            // 设置：从左侧滑出的卡片式玻璃面板（蒙层点击关闭，主界面保持清晰，
            // 仅面板自身范围内透出主内容的模糊像）
            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(tween(UiSpec.QUICK)),
                exit = fadeOut(tween(UiSpec.QUICK)),
                modifier = Modifier.matchParentSize()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(UiSpec.MASK)
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
                            .then(glassBackdropModifier())
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .fillMaxWidth(0.88f),
                        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                        // 毛玻璃面板：半透明 surface 让模糊后的主界面透出
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                        ),
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
            // 下载队列：从右侧滑出的卡片玻璃面板（样式同设置侧板）
            AnimatedVisibility(
                visible = showDownloads,
                enter = fadeIn(tween(UiSpec.QUICK)),
                exit = fadeOut(tween(UiSpec.QUICK)),
                modifier = Modifier.matchParentSize()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(UiSpec.MASK)
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
                            .then(glassBackdropModifier())
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .fillMaxWidth(0.88f),
                        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                        ),
                        shadowElevation = 8.dp
                    ) {
                        DownloadsScreen(onBack = { showDownloads = false })
                    }
                }
            }
            // 删除成功气泡：底部深色小条（✓ 删除成功 N 张），滑入 1.6s 后滑出，替代成功结果弹窗。
            // 声明在根 Box 末尾 → z 序高于全屏预览，预览页单张删除同样可见。
            val okEv = vm.deleteOkEvent.value
            val okShow = remember { mutableStateOf(false) }
            // 气泡在根坐标系的布局位置（滑入/滑出用 graphicsLayer 位移，不影响布局位置）
            var bubblePos by remember { mutableStateOf(Offset.Zero) }
            LaunchedEffect(okEv?.first) {
                if (okEv != null) {
                    okShow.value = true
                    delay(1600)
                    okShow.value = false
                    vm.consumeDeleteOk()
                }
            }
            AnimatedVisibility(
                visible = okShow.value,
                enter = slideInVertically(tween(UiSpec.STANDARD)) { it } + fadeIn(tween(UiSpec.QUICK)),
                exit = slideOutVertically(tween(UiSpec.QUICK)) { it } + fadeOut(tween(UiSpec.QUICK)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp)
            ) {
                okEv?.let { ev ->
                    // 毛玻璃配色随明暗模式：深色压黑提亮边、浅色罩白压深边；
                    // 模糊底材 = bubbleGlass（主内容实时模糊回绘），API<31 自动退化为纯半透明
                    val dark = vm.darkModeOn
                    val glassTint = UiSpec.bubbleTint(dark)
                    val glassBorder = UiSpec.glassBorder(dark)
                    val fg = if (dark) Color.White else Color(0xFF1C1C1E)
                    Row(
                        Modifier
                            .shadow(8.dp, RoundedCornerShape(UiSpec.ROUND_LARGE))
                            .clip(RoundedCornerShape(UiSpec.ROUND_LARGE))
                            .onGloballyPositioned { bubblePos = it.positionInRoot() }
                            .drawBehind {
                                // 毛玻璃底材：主内容层按气泡位置反向平移录进 glassLayer
                                //（renderEffect 模糊挂在该层上），再回绘 —— 模糊仅限气泡区域
                                // record 画布不自动裁剪：clipRect 显式裁到气泡范围防溢出
                                glassLayer.record {
                                    clipRect(0f, 0f, size.width, size.height) {
                                        translate(-bubblePos.x, -bubblePos.y) {
                                            drawLayer(contentLayer)
                                        }
                                    }
                                }
                                drawLayer(glassLayer)
                            }
                            .background(glassTint)
                            .border(1.dp, glassBorder, RoundedCornerShape(UiSpec.ROUND_LARGE))
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = UiSpec.accent(dark),
                            modifier = Modifier.size(UiSpec.ICON_STD)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "删除成功 ${ev.second} 张",
                            color = fg,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            /* ---------- 遥控拍摄页（任务 6）：全屏取景 + 控制条 ---------- */
            if (showRemote) {
                RemoteScreen(vm = vm, onBack = { vm.closeRemote(); showRemote = false })
            }

            /* ---------- 拍照控制（任务 5）：悬浮快门球 + 长按参数面板 ---------- */
            if (vm.connPhase.value == "connected" && selCount == 0 && !showDownloads && !showSettings && !showRemote) {
                if (vm.paramsPanelOpen.value) {
                    // 全屏点击捕获层：点空白收起参数面板
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { vm.paramsPanelOpen.value = false }
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 16.dp, bottom = 108.dp)
                    ) { ShutterParamPanel(vm) }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 18.dp, bottom = 26.dp)
                ) { ShutterBall(vm) }
            }

            // 拍摄结果气泡：样式与删除成功气泡一致（纯文本），1.8s 自动消失
            val capEv = vm.captureEvent.value
            val capShow = remember { mutableStateOf(false) }
            var capPos by remember { mutableStateOf(Offset.Zero) }
            LaunchedEffect(capEv?.first) {
                if (capEv != null) {
                    capShow.value = true
                    delay(1800)
                    capShow.value = false
                    vm.consumeCapture()
                }
            }
            AnimatedVisibility(
                visible = capShow.value,
                enter = slideInVertically(tween(UiSpec.STANDARD)) { it } + fadeIn(tween(UiSpec.QUICK)),
                exit = slideOutVertically(tween(UiSpec.QUICK)) { it } + fadeOut(tween(UiSpec.QUICK)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp)
            ) {
                capEv?.let { ev ->
                    val dark = vm.darkModeOn
                    val capTint = UiSpec.bubbleTint(dark)
                    val capBorder = UiSpec.glassBorder(dark)
                    val capFg = if (dark) Color.White else Color(0xFF1C1C1E)
                    Row(
                        Modifier
                            .shadow(8.dp, RoundedCornerShape(UiSpec.ROUND_LARGE))
                            .clip(RoundedCornerShape(UiSpec.ROUND_LARGE))
                            .onGloballyPositioned { capPos = it.positionInRoot() }
                            .drawBehind {
                                glassLayer.record {
                                    clipRect(0f, 0f, size.width, size.height) {
                                        translate(-capPos.x, -capPos.y) {
                                            drawLayer(contentLayer)
                                        }
                                    }
                                }
                                drawLayer(glassLayer)
                            }
                            .background(capTint)
                            .border(1.dp, capBorder, RoundedCornerShape(UiSpec.ROUND_LARGE))
                            .padding(horizontal = 18.dp, vertical = 11.dp)
                    ) {
                        Text(ev.second, color = capFg, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    /** 悬浮快门球（任务 5）：连接态右下角常驻。点按 = 0x100E 快门（照片进卡后自动刷新
     *  网格）；长按 = 展开参数面板（ISO/光圈/快门步进）。拍摄中转圈。 */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ShutterBall(vm: MainViewModel) {
        Box(
            Modifier
                .size(UiSpec.SHUTTER_BALL)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(UiSpec.accent(vm.darkModeOn))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { vm.capturePhoto() },
                    onLongClick = {
                        vm.paramsPanelOpen.value = true
                        vm.refreshCamParams()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (vm.shutterBusy.value) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(UiSpec.ICON_STD)
                )
            } else {
                Canvas(Modifier.size(34.dp)) {
                    val r = size.minDimension / 2f
                    drawCircle(Color.White, radius = r * 0.88f, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.White, radius = r * 0.32f)
                }
            }
        }
    }

    /** 拍摄参数面板：三行步进（ISO/光圈/快门）；面板打开期间每 2s 轮询刷新
     *  （相机侧转盘变化实时同步，无需关开面板）；通道不支持降级为提示文案。 */
    @Composable
    private fun ShutterParamPanel(vm: MainViewModel) {
        val dark = vm.darkModeOn
        LaunchedEffect(Unit) {
            while (true) {
                vm.refreshCamParams()
                delay(2000)
            }
        }
        Surface(
            Modifier
                .width(UiSpec.PARAMS_PANEL_W)
                .then(
                    glassBackdropModifier(
                        shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
                        tint = UiSpec.glassTint(dark),
                        border = UiSpec.glassBorder(dark),
                        blur = UiSpec.GLASS_BLUR,
                        shadow = UiSpec.GLASS_SHADOW
                    )
                ),
            shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
            color = Color.Transparent
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "拍摄参数",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (dark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.55f)
                )
                when (vm.paramsSupported.value) {
                    null -> Text(
                        "参数读取中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dark) Color.White else Color(0xFF1C1C1E),
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                    false -> Text(
                        "当前连接通道不支持参数读写",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dark) Color.White else Color(0xFF1C1C1E),
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                    else -> {
                        val pfg = if (dark) Color.White else Color(0xFF1C1C1E)
                        val ro = vm.paramsReadOnly.value
                        ShutterParamRow(
                            "ISO",
                            vm.paramIso.value?.toString() ?: "—",
                            pfg, !ro,
                            { vm.stepCamParam(0, -1) }, { vm.stepCamParam(0, 1) }
                        )
                        ShutterParamRow(
                            "光圈",
                            vm.paramAperture.value?.let {
                                if (it % 100 == 0) "f/${it / 100}" else "f/${it / 100.0}"
                            } ?: "—",
                            pfg, !ro,
                            { vm.stepCamParam(1, -1) }, { vm.stepCamParam(1, 1) }
                        )
                        ShutterParamRow(
                            "快门",
                            vm.paramShutter.value?.let { vm.shutterLabel(it) } ?: "—",
                            pfg, !ro,
                            { vm.stepCamParam(2, -1) }, { vm.stepCamParam(2, 1) }
                        )
                        // 提示行：步进失败原因 / Wi-Fi 只读说明
                        val hint = vm.paramHint.value
                            ?: if (ro) "Wi-Fi 连接下相机参数只读，请用 USB 连接调节" else null
                        if (hint != null) {
                            Text(
                                hint,
                                style = MaterialTheme.typography.labelSmall,
                                color = UiSpec.error(dark),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    /** 参数行：标签 + − 值 +（enabled=false 时按钮灰置） */
    @Composable
    private fun ShutterParamRow(
        label: String, value: String, fg: Color, enabled: Boolean,
        onMinus: () -> Unit, onPlus: () -> Unit
    ) {
        Row(
            Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                Modifier.weight(0.8f),
                style = MaterialTheme.typography.bodyMedium,
                color = fg.copy(alpha = if (enabled) 1f else 0.55f)
            )
            ShutterStepBtn("−", enabled, onMinus)
            Text(
                value,
                Modifier.weight(1.4f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                color = fg.copy(alpha = if (enabled) 1f else 0.55f)
            )
            ShutterStepBtn("+", enabled, onPlus)
        }
    }

    @Composable
    private fun ShutterStepBtn(sym: String, enabled: Boolean, onClick: () -> Unit) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.4f)
                )
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                sym,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f)
            )
        }
    }

    /** 遥控拍摄页（任务 6）：全屏取景画面 + 底部控制条（AF / 对焦−+ / 大快门 / 录像）。
     *  通道不支持取景（0x9201 探针被拒）→ 居中降级提示。BackHandler 退出即关取景。 */
    @Composable
    private fun RemoteScreen(vm: MainViewModel, onBack: () -> Unit) {
        val dark = vm.darkModeOn
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize()) {
                // 顶条：关闭 + 状态/错误文案（REC 红色警示）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                    }
                    Text(
                        when {
                            vm.movieRecording.value -> "REC · 录像中（影像保存在相机卡内）"
                            vm.lvActive.value -> "实时取景中"
                            vm.lvSupported.value == false -> "取景不可用"
                            else -> "正在开启取景…"
                        },
                        color = if (vm.movieRecording.value) Color(0xFFFF5252) else Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.width(UiSpec.TOPBAR_BTN))
                }
                // 取景画面
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = vm.lvFrame.value
                    when {
                        vm.lvSupported.value == false -> Text(
                            vm.lvText.value.ifEmpty { "当前连接通道不支持实时取景" },
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 30.dp)
                        )
                        bmp != null -> Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                vm.lvText.value,
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                // 底部控制条：AF / 焦− / 步长 / 焦+ / 大快门 / 录像
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RemoteCircleBtn("AF", UiSpec.REMOTE_BTN, vm.lvActive.value, dark) { vm.autoFocusDrive() }
                    RemoteCircleBtn("−", UiSpec.REMOTE_BTN, vm.lvActive.value, dark) { vm.stepFocus(1) }
                    // 步长档 chip：点击循环 1→2→3（协议驱动量 128/512/1024）
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable { vm.focusStep.value = vm.focusStep.value % 3 + 1 },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${vm.focusStep.value}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    RemoteCircleBtn("+", UiSpec.REMOTE_BTN, vm.lvActive.value, dark) { vm.stepFocus(2) }
                    // 拍摄模式 chip：AF=自动对焦后拍摄(0x100E) / 直拍=不对焦立即拍(0x9207)
                    Box(
                        Modifier
                            .height(30.dp)
                            .widthIn(min = 40.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                if (vm.shootAf.value) Color.White.copy(alpha = 0.16f)
                                else UiSpec.accent(dark)
                            )
                            .clickable { vm.shootAf.value = !vm.shootAf.value },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (vm.shootAf.value) "AF" else "直拍",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                    // 大快门：复用任务 5 拍摄链路（取景态失败自动暂停重试）
                    Box(
                        Modifier
                            .size(UiSpec.REMOTE_SHUTTER)
                            .shadow(10.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                if (vm.shutterBusy.value) UiSpec.accent(dark).copy(alpha = 0.6f)
                                else UiSpec.accent(dark)
                            )
                            .clickable(enabled = !vm.shutterBusy.value) { vm.capturePhoto() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (vm.shutterBusy.value) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(34.dp)
                            )
                        } else {
                            Canvas(Modifier.size(UiSpec.REMOTE_SHUTTER * 0.55f)) {
                                val r = size.minDimension / 2f
                                drawCircle(Color.White, radius = r * 0.88f, style = Stroke(3.dp.toPx()))
                                drawCircle(Color.White, radius = r * 0.32f)
                            }
                        }
                    }
                    /* VIDEO-SEALED（2026-10-17 用户要求暂撤录像功能，协议链路与 VM
                     * 状态保留，加回时恢复此块 + MainViewModel.toggleMovie）：
                    Box(
                        Modifier
                            .size(UiSpec.REMOTE_BTN)
                            .clip(CircleShape)
                            .background(
                                if (vm.movieRecording.value) Color(0xFFE53935) else Color.White.copy(alpha = 0.16f)
                            )
                            .border(
                                2.dp,
                                if (vm.movieRecording.value) Color(0xFFE53935) else Color.White.copy(alpha = 0.7f),
                                CircleShape
                            )
                            .clickable { vm.toggleMovie() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (vm.movieRecording.value) {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .background(Color.White, RoundedCornerShape(3.dp))
                            )
                        } else {
                            Box(Modifier.size(18.dp).background(Color(0xFFE53935), CircleShape))
                        }
                    }
                    VIDEO-SEALED */
                }
            }
        }
    }

    /** 遥控页控制条圆形按钮（AF / 对焦 ±） */
    @Composable
    private fun RemoteCircleBtn(label: String, size: Dp, enabled: Boolean, dark: Boolean, onClick: () -> Unit) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 0.16f else 0.07f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.35f)
            )
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
                .size(UiSpec.TOPBAR_BTN)
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

    /** 类型小徽章（合并开关内）：JPG 青 / NEF 紫 = 队列行类型徽章同色，白字缩写 */
    @Composable
    private fun TypeBadge(text: String, color: Color, modifier: Modifier = Modifier) {
        Surface(shape = RoundedCornerShape(4.dp), color = color, modifier = modifier) {
            Text(
                text,
                Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                color = Color.White,
                style = TextStyle(fontSize = UiSpec.TYPE_BADGE_FONT, fontWeight = FontWeight.Bold, lineHeight = 8.sp)
            )
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
                        Modifier.fillMaxWidth().height(UiSpec.CONN_INFO_H).padding(horizontal = 12.dp),
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

    /** 毛玻璃面修饰（设置/下载侧板、FilterSheet、各弹窗共用）：在本面自身范围内
     *  回绘主内容离屏层的模糊像 —— 背景保持清晰，只有透过玻璃面才是模糊的
     *  （与删除成功气泡同款手法）。挂到玻璃面根 modifier 的最外层；
     *  API<31 无模糊，仅剩调用方自己的半透明底。
     *  ⚠️ record 的画布不会自动裁剪到图层尺寸，必须 clipRect 显式裁到本面范围，
     *  否则主内容会溢出到玻璃面之外（边缘外的模糊竖条/半屏模糊就是这么来的）。
     *  shape：玻璃面有圆角轮廓时传入（如弹窗的 extraLarge）——底材按轮廓裁剪，
     *  否则方形底材会从圆角外露出一角模糊。
     *  tint：毛玻璃着色（在模糊像上叠加一层半透明 surface 色，统一明暗观感）。
     *  位置换算：玻璃面可能被 graphicsLayer 动画（侧板滑入）或放在独立窗口
     *  （弹窗/底部弹层）—— positionInRoot 前者不含 graphicsLayer 位移、后者是
     *  窗口内相对坐标，都不能直接用。统一换算成屏幕坐标：
     *    主窗口内 → 根 Box 的 localPositionOf（含 graphicsLayer）；
     *    独立窗口 → positionInRoot + 本窗口 decorView 的屏幕位置。
     *  并用每帧循环持续重算（ drawBehind 只在读到的状态变化时才重绘）。 */
    internal fun glassBackdropModifier(
        shape: Shape? = null,
        tint: Color? = null,
        border: Color? = null,
        blur: Float = UiSpec.GLASS_BLUR,
        shadow: Dp? = null
    ): Modifier =
        Modifier.composed {
        // ⚠️ 必须 composed：LocalView 要在「modifier 被应用处的组合」里读——
        // AlertDialog 的 modifier 参数在主窗口组合求值，直接读 LocalView 拿到的是
        // 主窗口 view，窗口判断恒走主窗分支 → localPositionOf 跨层级抛 "not part
        // of the same hierarchy" → fallback positionInRoot 只是弹窗窗口内坐标（小
        // 值）→ 玻璃透出 src 顶部（顶栏/置顶栏）而非正后方内容。composed 后在弹窗
        // 组合里求值，LocalView 才是弹窗自己的 view（FilterSheet 在 content lambda
        // 里调用所以一直正确）。主窗口玻璃面应用处即主组合，行为不变。
        val glass = rememberGraphicsLayer()
        if (Build.VERSION.SDK_INT >= 31) {
            glass.renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
        }
        val view = LocalView.current
        val coords = remember { mutableStateOf<LayoutCoordinates?>(null) }
        val pos = remember { mutableStateOf(Offset.Zero) }
        LaunchedEffect(view) {
            while (true) {
                withFrameNanos { }
                val c = coords.value ?: continue
                pos.value = glassPosOnScreen(view, c)
            }
        }
        Modifier
            .then(
                if (shadow != null && shape != null) {
                    Modifier.shadow(shadow, shape, clip = false)
                } else Modifier
            )
            .onGloballyPositioned {
                coords.value = it
                pos.value = glassPosOnScreen(view, it)
            }
            .drawBehind {
                val src = glassSource ?: return@drawBehind
                val outline = shape?.createOutline(size, layoutDirection, this)
                // ⚠️ record 必须显式传 Density：无参 record 走 DrawScope companion 扩展，
                // 会把 drawBehind 的 DrawScope 本身当作 Density 传给 layer——块内一旦
                // 访问 density（shape.createOutline 的 dp→px 换算就会），CanvasDrawScope
                // .getDensity 调自身无限递归 → StackOverflowError（弹窗即闪退的根因）。
                // 旧版块内只有 clipRect/translate/drawLayer（纯 float 参数）从不碰
                // density 才侥幸没炸。显式传干净 Density 后块内环境与 drawBehind 一致。
                glass.record(
                    Density(density, fontScale),
                    layoutDirection,
                    size.toIntSize()
                ) {
                    clipOutline(outline) {
                        translate(-pos.value.x, -pos.value.y) {
                            drawLayer(src)
                            // 预览打开时叠预览录制层（预览在主 Column 外，不叠的话
                            // 玻璃透出的是网格而非弹窗正后方的预览内容）
                            glassSourcePreview?.let { drawLayer(it) }
                        }
                    }
                }
                clipOutline(outline) {
                    // 模糊像：record 录好的离屏层（挂 blur renderEffect），必须画出来——
                    // 镜像栈回退时曾把本行连带删掉，玻璃只剩 tint 薄纱、透出锐利背景
                    drawLayer(glass)
                    // tint 必须与模糊像同受轮廓裁剪：裸 drawRect 是方形，
                    // 会在弹窗四角轮廓外突出着色块，视觉上"圆角变方角"
                    tint?.let { drawRect(it) }
                }
                // 轮廓描边：沿玻璃面边缘一圈，画在裁剪之外（描边本身贴边）
                if (border != null && outline != null) {
                    drawOutline(outline, border, style = Stroke(1.dp.toPx()))
                }
            }
        }

    /** 毛玻璃底材的裁剪：有轮廓（圆角等）按轮廓裁，无则按矩形边界裁 */
    private fun DrawScope.clipOutline(outline: Outline?, block: DrawScope.() -> Unit) {
        if (outline == null) {
            clipRect(0f, 0f, size.width, size.height, block = block)
        } else {
            clipPath(Path().apply { addOutline(outline) }, block = block)
        }
    }

    /** 玻璃面节点在屏幕坐标系中的位置（与 contentLayer 录制坐标系对齐） */
    private fun glassPosOnScreen(view: android.view.View, c: LayoutCoordinates): Offset {
        val root = glassRootCoords
        return if (mainWindowDecor(view) && root != null) {
            try { root.localPositionOf(c, Offset.Zero) } catch (_: Throwable) {
                // 跨窗口层级兜底（理论上 composed 修正后不会走到）
                c.positionInRoot()
            }
        } else {
            // 独立窗口（弹窗/底部弹层）：窗口内相对坐标 + 窗口在屏幕上的位置
            val loc = IntArray(2)
            view.rootView.getLocationOnScreen(loc)
            c.positionInRoot() + Offset(loc[0].toFloat(), loc[1].toFloat())
        }
    }

    /** 该 view 是否属于主窗口（弹窗/底部弹层有自己的 decorView） */
    private fun mainWindowDecor(view: android.view.View): Boolean =
        view.rootView === glassMainDecor

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