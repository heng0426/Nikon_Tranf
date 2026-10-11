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
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.drawBehind
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


    /** 下载管理页：队列条目（状态/进度/重试/取消）+ 清空已完成 */
    @Composable
internal fun MainActivity.DownloadsScreen(onBack: () -> Unit) {
        // 队列统计：VM 侧一次遍历得出（只订阅 status，低频：每张一次）。
        // 原来是页面顶层 2×any + 4×count + firstOrNull 共 7 次全队列遍历，
        // 且其中 firstOrNull 还读了 got —— 一个字节变化就会让整页重算。
        val st = vm.queueStats
        val listState = rememberLazyListState()
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                }
                Text("下载队列", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (st.hasActive) {
                    TextButton(onClick = { vm.cancelAllDownloads() }) { Text("全部取消") }
                }
                if (st.hasFinished) {
                    TextButton(onClick = { vm.clearFinished() }) { Text("清空已完成") }
                }
            }
            // 总览卡片：状态计数胶囊 + 总进度收进同一张卡（进度 = 已完成数 + 当前任务
            // 字节比例，除以未完成任务总数；不按字节累加——排队任务开始前 total=0，
            // 按字节累加会让分母失真）。denom=0（全部取消/失败）时不显示。
            if (vm.downloadQueue.isNotEmpty() && st.denom > 0 && !vm.perfHideOverallBar.value) {
                OverallCard(st, listState)
            }
            if (vm.downloadQueue.isEmpty()) {
                Text(
                    "暂无下载任务\n\n选择照片下载后，任务将显示在这里",
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            LazyColumn(Modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.downloadQueue, key = { it.handle }) { item ->
                    QueueRow(item)
                }
            }
        }
    }

    /** 总览卡片。
     *  第一排恒定三胶囊：已完成 / 总数量 / 排队中（"下载中"不再占位 —— 总数量-已完成-排队中
     *  的差就是正在下载的那条，串行 worker 至多一条）。
     *  第二排按阶段切换：下载中 → [滑窗速度][剩 mm:ss][百分比]；批次完成 → [平均速度][总时长]；
     *  仅排队（还没开跑）→ 无第二排。
     *  进度口径：字节权重 Σ已收/Σ总字节 —— 入队后台预取 0x9421 大小，未知条目按已知均值估计，
     *  整批全未知时退回按张数。点击卡片滚动到正在下载（或第一个排队）的行。
     *  got（高频）读取全部关进 OverallFill / PercentPill 两个窄作用域，整卡其余部分只在
     *  状态变化（低频）时重组；速度/ETA 由 VM 500ms 采样线程写入，本卡只读结果。 */
    @Composable
internal fun MainActivity.OverallCard(st: MainViewModel.QueueStats, listState: LazyListState) {
        val scope = rememberCoroutineScope()
        val green = UiSpec.accent(vm.darkModeOn)
        Surface(
            shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
            // primary 着色底：与队列行的 surfaceVariant 灰卡区分（这是汇总卡）
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            modifier = Modifier.clickable {
                val runningIdx = st.runningItem?.let { cur ->
                    vm.downloadQueue.indexOfFirst { q -> q === cur }.takeIf { ix -> ix >= 0 }
                }
                val idx = runningIdx
                    ?: vm.downloadQueue.indexOfFirst { it.status.value == QStatus.QUEUED }.takeIf { ix -> ix >= 0 }
                if (idx != null) scope.launch { listState.animateScrollToItem(idx) }
            }
        ) {
            Box(Modifier.fillMaxWidth()) {
                OverallFill(st, Modifier.matchParentSize())
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 第一排（恒定）：已完成 / 总数量 / 排队中 —— 绿 / amber / primary 三色同族
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("已完成 ${st.done}", bg = green.copy(alpha = 0.14f), fg = green, rolling = true)
                        val amber = UiSpec.amber(vm.darkModeOn)
                        Pill("总数量 ${st.denom}", bg = amber.copy(alpha = 0.14f), fg = amber, rolling = true)
                        Pill("排队中 ${st.queued}", bg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), fg = MaterialTheme.colorScheme.primary, rolling = true)
                    }
                    // 第二排（按阶段切换）
                    when {
                        st.runningItem != null -> BatchPaceRow(st)
                        !st.hasActive && st.hasFinished && st.msDone > 0L -> BatchDoneRow(st)
                    }
                }
            }
        }
    }

    /** 卡底绿色进度填充：字节权重比例（整批未知大小时退回按张数）。
     *  got 变化只重算这一个窄作用域；drawBehind 只走绘制阶段，不参与测量/布局。
     *  modifier 由调用处传 matchParentSize（BoxScope 接收者扩展，跨不出父 Box 作用域）。 */
    @Composable
    private fun MainActivity.OverallFill(st: MainViewModel.QueueStats, modifier: Modifier) {
        val frac by remember(st) {
            derivedStateOf {
                val cur = st.runningItem
                val num = st.byteDone + (cur?.got?.value ?: 0L)
                when {
                    st.byteDenom > 0 -> (num.toDouble() / st.byteDenom).toFloat().coerceIn(0f, 1f)
                    st.denom > 0 -> {
                        val curFrac = if (cur != null && cur.total.value > 0)
                            (cur.got.value.toFloat() / cur.total.value).coerceIn(0f, 1f) else 0f
                        ((st.done + curFrac) / st.denom).coerceIn(0f, 1f)
                    }
                    else -> 0f
                }
            }
        }
        val anim by animateFloatAsState(frac, animationSpec = vm.progressSpec(), label = "queueOverall")
        val green = UiSpec.accent(vm.darkModeOn)
        Box(
            modifier.drawBehind {
                drawRect(color = green, alpha = 0.20f, size = Size(size.width * anim, size.height))
            }
        )
    }

    /** 第二排（下载中）：[滑窗速度] [剩 mm:ss] [百分比]。速度/ETA 读 VM 500ms 采样结果，
     *  变化只重组本行；百分比跟 got 走（250ms），单独收进 PercentPill。 */
    @Composable
    private fun MainActivity.BatchPaceRow(st: MainViewModel.QueueStats) {
        val green = UiSpec.accent(vm.darkModeOn)
        val speed = vm.batchSpeedBps.value
        val eta = vm.batchEtaMs.value
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(if (speed > 0) "${humanSize(speed)}/s" else "…", bg = green.copy(alpha = 0.14f), fg = green, mono = true)
            Pill(if (eta > 0) "剩 ${fmtDur(eta)}" else "剩 …", bg = green.copy(alpha = 0.14f), fg = green, mono = true)
            PercentPill(st)
        }
    }

    /** 第二排（完成后）：队列口径 —— 队列内全部已完成照片一起算：
     *  平均 = Σ已完成字节 / Σ纯下载用时（Σ DONE.elapsedMs，批次间空闲与排队等待不计）；
     *  总时长 = Σ纯下载用时。清空已完成后自然归零，下一批重新累计。 */
    @Composable
    private fun MainActivity.BatchDoneRow(st: MainViewModel.QueueStats) {
        val green = UiSpec.accent(vm.darkModeOn)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("平均 ${humanSize(st.queueAvgBps)}/s", bg = green.copy(alpha = 0.14f), fg = green, mono = true)
            Pill("总时长 ${fmtDur(st.msDone)}", bg = green.copy(alpha = 0.14f), fg = green, mono = true)
        }
    }

    /** 百分比胶囊：字节权重（整批未知大小时退回按张数）。got 变化只重组这一个胶囊。 */
    @Composable
    private fun MainActivity.PercentPill(st: MainViewModel.QueueStats) {
        val pct by remember(st) {
            derivedStateOf {
                val cur = st.runningItem
                val num = st.byteDone + (cur?.got?.value ?: 0L)
                val v = when {
                    st.byteDenom > 0 -> num * 100.0 / st.byteDenom
                    st.denom > 0 -> {
                        val curFrac = if (cur != null && cur.total.value > 0)
                            cur.got.value.toDouble() / cur.total.value else 0.0
                        (st.done + curFrac) * 100.0 / st.denom
                    }
                    else -> 0.0
                }
                v.roundToInt()
            }
        }
        val green = UiSpec.accent(vm.darkModeOn)
        Pill("$pct%", bg = green.copy(alpha = 0.14f), fg = green, mono = true)
    }

    /** 毫秒 → m:ss（满 1 小时 h:mm:ss） */
    private fun fmtDur(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)
        else String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
    }

    /** 总览卡内的文字胶囊：ROUND_SMALL + 着色底，与全局胶囊风格一致。
     *  rolling = true 时数字变化走 RollingText 滚动动画（仅用于低频计数）；
     *  mono = true 时加 tnum 等宽数字（高频数值防抖动，如总进度 250ms 一次）。 */
    @Composable
    private fun MainActivity.Pill(
        text: String,
        bg: Color,
        fg: Color,
        rolling: Boolean = false,
        mono: Boolean = false
    ) {
        Surface(shape = RoundedCornerShape(UiSpec.ROUND_SMALL), color = bg) {
            if (rolling) {
                RollingText(
                    text,
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = fg
                )
            } else {
                Text(
                    text,
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = fg,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = if (mono) "tnum" else null
                    )
                )
            }
        }
    }

    /** 队列行缩略图：handle → photoRows 既有预览位图（网格加载过即命中），无位图时兜底加载 */
    @Composable
internal fun MainActivity.QueueThumb(handle: Int) {
        val row = vm.rowForHandle(handle)   // O(1)：原 firstOrNull 是 O(n)，886 行 × 20 个队列行
        LaunchedEffect(handle) { row?.let { vm.ensureThumb(it) } }
        val bmp = row?.preview?.value
        Box(
            Modifier
                .size(UiSpec.QUEUE_THUMB)
                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                DownloadIcon(MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
 internal fun MainActivity.QueueRow(item: QueueItem) {
        val st = item.status.value
        // 进度填充直接铺在卡片背景上：下载中按比例推进（浅绿），完成后整卡铺满（绿加深），
        // 与未下载行一眼区分；排队/失败/取消不铺色。
        // fillFrac 经 animateFloatAsState 线性插值（500ms）——分块更新（USB bulk 轮/Wi-Fi 1MB 块）
        // 之间平滑推进，进度条不再一顿一顿；首次组合直接取目标值，滑回屏幕不重播动画
        val fillFracTarget: Float
        val fillAlpha: Float
        when {
            st == QStatus.RUNNING && item.total.value > 0 -> {
                fillFracTarget = (item.got.value.toFloat() / item.total.value).coerceIn(0f, 1f)
                fillAlpha = 0.20f
            }
            st == QStatus.DONE -> {
                fillFracTarget = 1f
                fillAlpha = 0.30f
            }
            else -> {
                fillFracTarget = 0f
                fillAlpha = 0f
            }
        }
        val fillFrac by animateFloatAsState(
            targetValue = fillFracTarget,
            animationSpec = vm.progressSpec(),
            label = "queueRowFill"
        )
        Surface(
            shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Box(Modifier.fillMaxWidth()) {
                if (fillAlpha > 0f && !vm.perfHideRowFill.value) {
                    val green = UiSpec.accent(vm.darkModeOn)
                    /* 填充层用 drawBehind 画，不再用一个"宽度随进度变化"的 Box。
                     * 原因（实测）：宽度参与布局 → 正在下载的那一行每帧重新测量，
                     * 中位帧从 8.6ms 涨到 15.1ms。drawBehind 只走绘制阶段，
                     * 不改变任何测量/布局，动画再密也只是一次重绘。
                     * 之前还踩过 BoxWithConstraints —— 那个会额外发起一次子合成。 */
                    Box(
                        Modifier
                            .matchParentSize()
                            .drawBehind {
                                drawRect(
                                    color = green,
                                    alpha = fillAlpha,
                                    size = Size(size.width * fillFrac, size.height)
                                )
                            }
                    )
                }
                Row(
                    // 高度锁定 60dp（原实际高度）：下载中（有取消按钮）与已完成（无按钮）
                    // 卡片总高一致，状态切换不跳变；各元素在行内垂直居中
                    Modifier.fillMaxWidth().height(UiSpec.QUEUE_ROW_H).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!vm.perfHideRowThumb.value) QueueThumb(item.handle)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.type,
                                Modifier
                                    .background(badgeColor(item.type), RoundedCornerShape(UiSpec.ROUND_SMALL))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        // 状态行固定 20dp 高：下载中胶囊/完成后胶囊/纯文字高度一致，卡片不因状态切换变高
                        Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            when (st) {
                                QStatus.QUEUED -> Text(
                                    "排队中",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                QStatus.RUNNING -> {
                                    // 胶囊显示进度/速度；tnum 等宽数字——数值每帧变化时文字不抖动
                                    val green = UiSpec.accent(vm.darkModeOn)
                                    Surface(
                                        shape = RoundedCornerShape(UiSpec.ROUND_SMALL),
                                        color = green.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            "${humanSize(item.got.value)} / ${humanSize(item.total.value)} · ${item.speed.value}",
                                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFeatureSettings = "tnum"
                                            )
                                        )
                                    }
                                }
                                QStatus.DONE -> {
                                    // 已完成胶囊 + 下载耗时胶囊（精确到 0.1s，tnum 等宽不抖动）
                                    val green = UiSpec.accent(vm.darkModeOn)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(UiSpec.ROUND_SMALL),
                                            color = green.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                "已完成 ✓",
                                                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                color = green,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        if (item.elapsedMs > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(UiSpec.ROUND_SMALL),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    "%.1fs".format(item.elapsedMs / 1000.0),
                                                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFeatureSettings = "tnum"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                QStatus.FAILED -> Text(
                                    "失败",
                                    color = UiSpec.error(vm.darkModeOn),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                QStatus.CANCELED -> Text(
                                    "已取消",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    when (st) {
                        QStatus.QUEUED, QStatus.RUNNING -> IconButton(
                            onClick = { vm.cancelDownload(item) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "取消",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        QStatus.FAILED -> TextButton(onClick = { vm.retryDownload(item) }) { Text("重试") }
                        else -> {}
                    }
                }
            }
        }
    }

    /** 预览页下载槽位：按队列状态渲染（排队/按钮内进度/重试/已下载），与队列单一真相源联动。
     *  未下载=实心主色按钮；下载中=进度直接填充在按钮内（点按取消）；
     *  已下载=绿色描边空心按钮（与实心未下载态一眼区分），点按弹重新下载确认窗。 */
    @Composable
internal fun MainActivity.DownloadStateSlot(
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
            // 已下载：实心绿胶囊 + ✓（未下载=主色实心胶囊），颜色与内容双重区分，实心底在照片背景上清晰
            downloaded || (qItem != null && st == QStatus.DONE) -> {
                var confirmRedownload by remember { mutableStateOf(false) }
                val green = UiSpec.accentDeep(vm.darkModeOn)
                Button(
                    onClick = { confirmRedownload = true },
                    modifier = modifier,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = green,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        Icons.Filled.Check, contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("已下载")
                }
                if (confirmRedownload) {
                    AlertDialog(
                        onDismissRequest = { confirmRedownload = false },
                        title = { Text("已下载") },
                        text = { Text("该照片已下载，可打开相册查看，或重新下载。") },
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
            qItem != null && st == QStatus.RUNNING -> {
                // 进度条直接长在按钮上：胶囊外形不变，浅绿填充随进度推进（与下载队列同款配色），点按取消。
                // 填充 500ms 线性插值 + 实时速度（与队列行同款节流）：分块更新间平滑推进不顿挫
                val fracTarget = if (qItem.total.value > 0)
                    (qItem.got.value.toFloat() / qItem.total.value).coerceIn(0f, 1f) else 0f
                val frac by animateFloatAsState(
                    targetValue = fracTarget,
                    animationSpec = tween(500, easing = LinearEasing),
                    label = "previewFill"
                )
                val green = UiSpec.accent(vm.darkModeOn)
                Box(
                    modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { vm.cancelDownload(qItem) }
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(frac)
                            .background(green.copy(alpha = 0.20f))
                    )
                    Row(
                        Modifier.matchParentSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            // tnum 等宽数字：数值刷新时文字宽度稳定不抖动；速度与队列行同源
                            "${humanSize(qItem.got.value)} / ${humanSize(qItem.total.value)}" +
                                (qItem.speed.value.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                            color = green,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            maxLines = 1
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Close, contentDescription = "取消下载",
                            tint = green, modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            qItem != null && st == QStatus.QUEUED -> {
                // 排队中：与下载中同款 40dp 胶囊（旧实现是无高度的文字盒，Row 顶对齐下视觉偏上）
                Box(
                    modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text("排队中…", color = UiSpec.GREY_TEXT, style = MaterialTheme.typography.labelMedium)
                }
            }
            qItem != null && st == QStatus.FAILED -> Button(
                onClick = { vm.retryDownload(qItem) }, modifier = modifier
            ) { Text("下载失败") }
            qItem != null && st == QStatus.CANCELED -> Button(
                onClick = { vm.retryDownload(qItem) }, modifier = modifier
            ) { Text("重新下载") }
            else -> Button(onClick = onDownload, enabled = enabled, modifier = modifier) { Text(label) }
        }
    }