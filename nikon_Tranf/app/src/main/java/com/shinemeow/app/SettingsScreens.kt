package com.shinemeow.app

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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


    @OptIn(ExperimentalLayoutApi::class)
    @Composable
internal fun MainActivity.SettingsScreen(
        prefs: android.content.SharedPreferences,
        onBack: () -> Unit,
        onKeepScreenOnChanged: (Boolean) -> Unit,
        onPickDir: () -> Unit
    ) {
        // 三个开关初值一次读完 SharedPreferences（原为三个独立 remember 各读一次）
        val initial = remember {
            Triple(
                prefs.getBoolean("set_keep_on", false),
                prefs.getBoolean("set_auto_connect", false),
                prefs.getBoolean("set_auto_preview", true)
            )
        }
        var keepOn by remember { mutableStateOf(initial.first) }
        var autoConnect by remember { mutableStateOf(initial.second) }
        var autoPreview by remember { mutableStateOf(initial.third) }
        var showCacheLimitDialog by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        // 缓存大小统计 = listFiles() + 逐个 length() 的目录遍历 —— 必须在 IO 线程。
        // 原实现在 LaunchedEffect 里直接调（主线程），撞在 320ms 滑入动画的首帧上
        // → 打开设置页偶现卡顿。改为后台算完再回来写状态。
        LaunchedEffect(Unit) {
            val bytes = withContext(Dispatchers.IO) { vm.computeThumbCacheBytes() }
            vm.thumbCacheBytes = bytes
            vm.refreshDownloadStats()   // 已下载照片统计（JPG/RAW 分类，内部自带线程）
        }

        // 顶部与主页顶栏同款公式（主页注释"顶栏高度只改这里"）：不算状态栏 inset、
        // 顶部固定 40dp —— 原 safeDrawingPadding+16dp 会比主页顶栏低状态栏差值几个 dp
        Column(
            Modifier.fillMaxSize()
                // 与主页/下载队列同款顶栏公式：safeDrawing.Top 自适应状态栏 + topBarGapDp 余量（设置·外观可调）
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = UiSpec.PAGE_PAD, end = UiSpec.PAGE_PAD, bottom = UiSpec.PAGE_PAD)
                .padding(top = vm.topBarGapDp.value.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 返回按钮：34dp 圆角色块容器，与顶栏按钮同款
                Box(
                    Modifier
                        .size(UiSpec.TOPBAR_BTN)
                        .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(UiSpec.ICON_STD),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(UiSpec.TOPBAR_BTN_GAP))
                Text("设置", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))
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
                        "connected" -> UiSpec.accent(vm.darkModeOn)
                        "connecting" -> UiSpec.amber(vm.darkModeOn)
                        else -> UiSpec.error(vm.darkModeOn)
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
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("$model ($serial)", style = MaterialTheme.typography.bodyLarge)
                            Text(ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (connected && ip == connectedIp.value)
                            Text("已连接 ✓", color = UiSpec.accent(vm.darkModeOn), style = MaterialTheme.typography.labelSmall)
                    }
                }
                SettingsDivider()
                SettingSwitch(
                    title = "热点未开提示",
                    subtitle = "未连接且手机热点未开启时，在连接页显示跳转热点设置的提示",
                    checked = vm.hotspotHintOn.value,
                    onChange = { vm.setHotspotHint(it) }
                )
                SettingsDivider()
                SettingSwitch(
                    title = "启动时自动连接相机",
                    subtitle = "启动后自动扫描并连接相机，未检测到网络时跳过",
                    checked = autoConnect,
                    onChange = {
                        autoConnect = it
                        prefs.edit().putBoolean("set_auto_connect", it).apply()
                    }
                )
                // 优先通道选择器：随自动连接开关展开/收起（淡入向下展开 / 淡出收起）
                AnimatedVisibility(
                    visible = autoConnect,
                    enter = fadeIn(tween(UiSpec.STANDARD)) + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut(tween(UiSpec.QUICK)) + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    Column {
                        SettingsDivider()
                        AutoConnChannelPicker()
                    }
                }
            }
            SettingsSection(title = "传输", icon = { SwapPressIcon(MaterialTheme.colorScheme.primary, Modifier.size(16.dp)) }) {
                SettingSwitch(
                    title = "合并 RAW+JPG 展示",
                    subtitle = "同一时间拍摄的 JPG 与 NEF 合并为一格，预览页/批量下载可分别选格式",
                    checked = vm.mergePairs.value,
                    onChange = { vm.setMergePairs(it) }
                )
                SettingsDivider()
                // 短/长按反转：网格点选交互习惯因人而异（多选为主 vs 预览为主）
                SettingSwitch(
                    title = "短按选中、长按预览",
                    subtitle = "反转网格的短按/长按：开 = 短按切换选中（进入多选），长按进预览；关 = 默认短按预览、长按选中",
                    checked = vm.swapPressOn.value,
                    onChange = { vm.setSwapPress(it) }
                )
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
                SettingsDivider()
                /* [SCROLLBAR-SEALED] 快速滚动条封存：设置入口与阈值选择器一并停用，重启时随标记恢复
                // 快速滚动条：开关承担"不显示"，展开后只选显示阈值（样式与分块加载一致）
                SettingSwitch(
                    title = "快速滚动条",
                    subtitle = "照片多时显示右侧快速滚动条，可拖动快速定位",
                    checked = vm.scrollbarThreshold.value >= 0,
                    onChange = { vm.setScrollbarThreshold(if (it) 50 else -1) }
                )
                AnimatedVisibility(
                    visible = vm.scrollbarThreshold.value >= 0,
                    enter = fadeIn(tween(UiSpec.STANDARD)) + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut(tween(UiSpec.QUICK)) + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    Column {
                        SettingsDivider()
                        ScrollbarThresholdPicker()
                    }
                }
                [/SCROLLBAR-SEALED] */
                SettingsDivider()
                SettingSwitch(
                    title = "分块加载照片",
                    subtitle = "照片很多时先显示一部分，滚动接近已加载末尾时自动加载下一块，直至全部加载",
                    checked = vm.chunkedLoad.value,
                    onChange = { vm.setChunkedLoad(it) }
                )
                // 分块大小选择器：随分块开关展开/收起
                AnimatedVisibility(
                    visible = vm.chunkedLoad.value,
                    enter = fadeIn(tween(UiSpec.STANDARD)) + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut(tween(UiSpec.QUICK)) + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    Column {
                        SettingsDivider()
                        ChunkSizePicker()
                    }
                }
                SettingsDivider()
                SettingSwitch(
                    title = "USB 整文件传输",
                    subtitle = "开启后 USB 下载一次读完整文件（更快）；关闭按 1MB 分段读取。整包失败自动回退分段",
                    checked = vm.usbWholeTransfer.value,
                    onChange = { vm.setUsbWholeTransfer(it) }
                )
            }
            // 拍摄（任务 5/6）：遥控拍摄 + 实时取景的行为开关
            SettingsSection(title = "拍摄", icon = { RemoteIcon(MaterialTheme.colorScheme.primary, Modifier.size(16.dp)) }) {
                SettingSwitch(
                    title = "拍摄后自动刷新照片列表",
                    subtitle = "遥控拍摄存卡后自动重新枚举（照片很多时可关，稍后手动刷新）",
                    checked = vm.captureRefreshOn.value,
                    onChange = { vm.setCaptureRefresh(it) }
                )
                SettingsDivider()
                SettingSwitch(
                    title = "实时取景省电模式",
                    subtitle = "取景帧率降为约 5fps，延长相机与手机续航",
                    checked = vm.lvEcoOn.value,
                    onChange = { vm.setLvEco(it) }
                )
                SettingsDivider()
                SettingSwitch(
                    title = "拍摄结果气泡",
                    subtitle = "拍摄/录像成功时弹底部气泡提示（失败提示不受影响）",
                    checked = vm.captureBubbleOn.value,
                    onChange = { vm.setCaptureBubble(it) }
                )
            }
            SettingsSection(title = "外观", icon = { SectionIcon("外观") }) {
                SettingSwitch(
                    title = "深色模式",
                    subtitle = "界面切换为深色配色（默认浅色，开关即时生效）",
                    checked = vm.darkModeOn,
                    onChange = { vm.setDarkMode(it) }
                )
                SettingsDivider()
                // 顶栏到状态栏余量：三页共用，按机型自由调整（本页即实时预览）
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("顶栏上边距", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "顶栏到状态栏的距离（0 = 紧贴状态栏，三页生效）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ActionPill("−") { vm.setTopBarGap(vm.topBarGapDp.value - 2) }
                    Text(
                        "${vm.topBarGapDp.value}dp",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    ActionPill("+") { vm.setTopBarGap(vm.topBarGapDp.value + 2) }
                }
                SettingsDivider()
                // 配色主题：切即写 UiSpec.currentTheme，取色处自动重组 → 全 App 实时生效。
                // 色卡内三枚色点 = 该主题的主色 / USB 通道色 / JPG 徽章色（按当前深浅模式取）。
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("配色主题", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "切换后立即生效，返回主页即可看到照片页/连接卡的实际配色",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (k in ThemeKey.entries) {
                            ThemeChip(k, selected = vm.themeKey.value == k) { vm.setTheme(k) }
                        }
                    }
                }
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
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("存储目录", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            vm.dirDisplay.value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ActionPill("更改目录", onPickDir)
                    if (vm.customDirUri.value != null) {
                        ActionPill("恢复默认") { vm.setCustomDir(null) }
                    }
                }
                SettingsDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("已下载照片", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        val st = vm.dlPhotoStats.value
                        when {
                            st == null -> Text(
                                "统计中…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            st.totalCount == 0 -> Text(
                                "暂无已下载照片",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            else -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    DlTypeBadge("JPG", UiSpec.JPG_TEAL)
                                    Text(
                                        "${st.jpgCount} 张 · ${humanSize(st.jpgBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    DlTypeBadge("RAW", UiSpec.NEF_PURPLE)
                                    Text(
                                        "${st.rawCount} 张 · ${humanSize(st.rawBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "合计 ${st.totalCount} 张 · ${humanSize(st.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    ActionPill("刷新") { vm.refreshDownloadStats() }
                }
                SettingsDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("缩略图缓存", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "当前 ${humanSize(vm.thumbCacheBytes)} · 上限 ${vm.thumbCacheLimitMb}MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ActionPill("上限") { showCacheLimitDialog = true }
                    ActionPill("清空") { vm.clearThumbCache() }
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
            SettingsSection(title = "性能/诊断", icon = { SectionIcon("性能") }) {
                // 2Hz 轮询 PerfTrace（它刻意不持有任何 Compose 状态 —— 否则"采样"
                // 本身会引发每帧重组，把要测的东西测没了）
                var tick by remember { mutableStateOf(0) }
                LaunchedEffect(Unit) { while (true) { delay(500); tick++ } }
                val s = remember(tick) { PerfTrace.snapshot() }

                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        "帧率 ${"%.1f".format(s.fps)} fps · 掉帧 ${"%.1f".format(s.jankPct)}%",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "P50 ${"%.1f".format(s.p50Ms)}ms · P95 ${"%.1f".format(s.p95Ms)}ms · " +
                            "最长 ${"%.1f".format(s.maxMs)}ms · 样本 ${s.windowFrames}/累计 ${s.totalFrames}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionPill("重置采样") { PerfTrace.reset(); tick++ }
                    }
                }
                SettingsDivider()
                // 派生路径重算次数：优化前后对比的核心指标
                val recompute = remember(tick) {
                    listOf("filterPhotos#重算", "filterPairs#重算", "sections#重算", "pairSections#重算")
                        .map { it.removeSuffix("#重算") to PerfTrace.countOf("$it") }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        "派生路径重算次数",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        recompute.joinToString(" · ") { (k, v) -> "$k $v" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "滑动/勾选时这些数字几乎不动才算正常；每帧都涨 = 派生缓存失效（旧实现就是这样）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SettingsDivider()
                var verbose by remember { mutableStateOf(PerfTrace.verbose) }
                SettingSwitch(
                    title = "逐次明细日志",
                    subtitle = "打开后每次枚举/每张缩略图都打 Log（tag: Perf）。定位单步问题时开，平时关着 —— 它本身会拖慢主线程",
                    checked = verbose,
                    onChange = { verbose = it; PerfTrace.verbose = it }
                )
                SettingsDivider()
                SettingSwitch(
                    title = "演示数据模式",
                    subtitle = "不连相机也能测 UI 流畅度：886 张假照片 + 零解码成本缩略图。含删除演练（约 10 张预置保护，可验证成功气泡/混合结果框）与假存储容量；相机连着时没有 adb 通道（USB 口被占 / Wi-Fi 与热点互斥），只能这样测",
                    checked = vm.demoMode.value,
                    onChange = { vm.setDemoMode(it) }
                )
                AnimatedVisibility(
                    visible = vm.demoMode.value,
                    enter = fadeIn(tween(UiSpec.STANDARD)) + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut(tween(UiSpec.QUICK)) + shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    Column {
                        SettingsDivider()
                        DemoDownloadPanel()
                    }
                }
            }
            SettingsSection(title = "关于", icon = { SectionIcon("关于") }) {
                Text(
                    "尼康 Z 系列 Wi-Fi 传图\n" +
                        "通过 PTP/IP 协议与相机直连传输\n" +
                        "注：配对模式下相机不提供原始文件名，文件按拍摄时间命名",
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
internal fun MainActivity.SettingsSection(
        title: String,
        icon: @Composable () -> Unit,
        content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            ) {
                // 分组图标：28dp 圆角色块容器（主色 12% 底），与顶栏/连接卡同风格
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = UiSpec.PILL_TINT)),
                    contentAlignment = Alignment.Center
                ) { icon() }
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(content = content)
            }
        }
        Spacer(Modifier.height(18.dp))
    }

    /** 卡片内行分隔线（左右内缩） */
    @Composable
internal fun MainActivity.SettingsDivider() {
        HorizontalDivider(
            thickness = 0.8.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
            modifier = Modifier.padding(horizontal = 14.dp)
        )
    }

    /** 分组小图标（16dp 自绘，与主界面漏斗/WiFi/下载同风格） */
    @Composable
internal fun MainActivity.SectionIcon(kind: String) {
        val color = MaterialTheme.colorScheme.primary
        Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            when (kind) {
                "连接" -> {
                    val style = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
                    val cx = w / 2f
                    val cy = h * 0.74f
                    listOf(0.27f, 0.47f).forEach { r ->
                        drawArc(
                            color,
                            -135f, 90f, false,
                            topLeft = Offset(cx - w * r, cy - h * r),
                            size = Size(w * r * 2f, h * r * 2f),
                            style = style
                        )
                    }
                    drawCircle(color, radius = w * 0.10f, center = Offset(cx, cy))
                }
                "外观" -> {
                    // 月牙：外圆减去右上偏移圆（Path 布尔差集，真轮廓填充）。
                    // 旧实现用一枚表面色圆盖在主圆上"裁"月牙——卡片底色与
                    // surface 有出入时遮挡圆轮廓会露馅；差集结果与背景无关。
                    val outer = Path().apply {
                        addOval(Rect(w * 0.03f, h * 0.10f, w * 0.87f, h * 0.94f))
                    }
                    val inner = Path().apply {
                        addOval(Rect(w * 0.30f, h * 0.02f, w * 1.02f, h * 0.74f))
                    }
                    drawPath(Path.combine(PathOperation.Difference, outer, inner), color)
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
                "性能" -> {
                    // 三根柱状条（自绘）：帧时间分布
                    val style = Stroke(width = w * 0.14f, cap = StrokeCap.Round)
                    listOf(0.38f to 0.55f, 0.62f to 0.85f, 0.86f to 0.35f).forEach { (x, top) ->
                        drawLine(
                            color,
                            Offset(w * x, h * 0.92f),
                            Offset(w * x, h * (0.92f - top)),
                            strokeWidth = style.width,
                            cap = StrokeCap.Round
                        )
                    }
                    drawLine(
                        color,
                        Offset(w * 0.1f, h * 0.92f),
                        Offset(w * 0.95f, h * 0.92f),
                        strokeWidth = w * 0.08f,
                        cap = StrokeCap.Round
                    )
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
internal fun MainActivity.SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
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

    /* [SCROLLBAR-SEALED] 快速滚动条封存：阈值选择器一并停用，重启时随标记恢复
    /** 快速滚动条显示阈值选择器（30/50/100/200；"不显示"由上方开关承担，写入 set_scrollbar_threshold） */
    @Composable
internal fun MainActivity.ScrollbarThresholdPicker() {
        val t = vm.scrollbarThreshold.value
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("显示阈值", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "照片数量达到该值后才显示快速滚动条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelChip("30", selected = t == 30) { vm.setScrollbarThreshold(30) }
                ChannelChip("50", selected = t == 50) { vm.setScrollbarThreshold(50) }
                ChannelChip("100", selected = t == 100) { vm.setScrollbarThreshold(100) }
                ChannelChip("200", selected = t == 200) { vm.setScrollbarThreshold(200) }
            }
        }
    }
    [/SCROLLBAR-SEALED] */

    /** 分块大小选择器（50/100/200/500，写入 set_chunk_size；仅分块加载开启时显示） */
    @Composable
internal fun MainActivity.ChunkSizePicker() {
        val n = vm.chunkSize.value
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("每块加载量", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "每次追加到网格的数量（合并模式按合并格计）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelChip("50", selected = n == 50) { vm.setChunkSize(50) }
                ChannelChip("100", selected = n == 100) { vm.setChunkSize(100) }
                ChannelChip("200", selected = n == 200) { vm.setChunkSize(200) }
                ChannelChip("500", selected = n == 500) { vm.setChunkSize(500) }
            }
        }
    }

    /** 自动连接优先通道选择器（USB / Wi-Fi 二选一，写入 set_auto_conn_channel） */
    @Composable
internal fun MainActivity.AutoConnChannelPicker() {
        // 读可观察的 VM 状态：点击后选中态立即刷新（读 prefs 不会触发重组，chip 会"点不动"）
        val channel = vm.autoConnChannel.value
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("自动连接优先通道", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("启动时自动连接的尝试顺序，手动选择连接方式不受影响", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelChip("USB", selected = channel == "usb") {
                    vm.setAutoConnChannel("usb")
                }
                ChannelChip("Wi-Fi", selected = channel == "wifi") {
                    vm.setAutoConnChannel("wifi")
                }
            }
        }
    }

    /** 演示模式的假下载面板：复刻两种真实回调粒度，用来压下载列表 */
    @Composable
internal fun MainActivity.DemoDownloadPanel() {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                "假下载任务",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "回调粒度：64KB 复刻 USB 整包模式的高频回调（50MB ≈ 800 次），1MB 复刻分段模式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelChip("64KB", selected = vm.demoStepKb.value == 64) { vm.demoStepKb.value = 64 }
                ChannelChip("1MB", selected = vm.demoStepKb.value == 1024) { vm.demoStepKb.value = 1024 }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill("开始 20 个") { vm.startDemoDownloads(20) }
                if (vm.demoDownloadRunning.value) ActionPill("停止") { vm.stopDemoDownloads() }
            }
        }
    }

    /** 存储统计的类型小徽章：JPG 青 / RAW 紫（与队列行/合并开关的类型徽章同色系） */
    @Composable
    internal fun MainActivity.DlTypeBadge(text: String, color: Color) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color,
            modifier = Modifier.padding(end = 4.dp)
        ) {
            Text(
                text,
                Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                color = Color.White,
                style = TextStyle(fontSize = UiSpec.BADGE_FONT, fontWeight = FontWeight.Bold, lineHeight = 9.sp)
            )
        }
    }

    /** 设置页动作胶囊：可点击文字统一包裹（描边胶囊，与 Wi-Fi 卡"扫描相机"同风格） */
    @Composable
internal fun MainActivity.ActionPill(text: String, onClick: () -> Unit) {
        Box(
            Modifier
                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = UiSpec.OUTLINE_ALPHA),
                    RoundedCornerShape(UiSpec.ROUND_SMALL)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = UiSpec.ACTION_HPAD, vertical = UiSpec.ACTION_VPAD),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
    }

    /** 配色主题色卡：三枚色点（主色 / USB 通道 / JPG 徽章）+ 主题名；
     *  选中态 = 主色描边 + 主色淡底，未选中 = 中性描边。 */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    internal fun MainActivity.ThemeChip(key: ThemeKey, selected: Boolean, onClick: () -> Unit) {
        val t = UI_THEMES[key] ?: return
        val dark = vm.darkModeOn
        val main = if (dark) t.accentD else t.accentL
        val usb = if (dark) t.usbD else t.usbL
        Box(
            Modifier
                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                .background(if (selected) main.copy(alpha = 0.14f) else Color.Transparent)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) main else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(UiSpec.ROUND_SMALL)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ThemeColorDot(main)
                    ThemeColorDot(usb)
                    ThemeColorDot(t.jpg)
                }
                Text(
                    key.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) main else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    @Composable
    private fun ThemeColorDot(color: Color) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
    }

    @Composable
internal fun MainActivity.ChannelChip(text: String, selected: Boolean, onClick: () -> Unit) {
        Box(
            Modifier
                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                .then(
                    if (selected) Modifier.background(
                        UiSpec.accentDeep(vm.darkModeOn)
                    )
                    // 未选中：透明底 + 中性描边，保证在卡片底色上有清晰胶囊轮廓
                    else Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(UiSpec.ROUND_SMALL)
                    )
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }