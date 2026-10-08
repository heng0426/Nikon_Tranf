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


    @Composable
internal fun MainActivity.SettingsScreen(
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
                // 返回按钮：34dp 圆角色块容器，与顶栏按钮同款
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(10.dp))
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
                SettingsDivider()
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
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
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
        val surface = MaterialTheme.colorScheme.surface
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

    /** 设置页动作胶囊：可点击文字统一包裹（描边胶囊，与 Wi-Fi 卡"扫描相机"同风格） */
    @Composable
internal fun MainActivity.ActionPill(text: String, onClick: () -> Unit) {
        Box(
            Modifier
                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    RoundedCornerShape(UiSpec.ROUND_SMALL)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
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