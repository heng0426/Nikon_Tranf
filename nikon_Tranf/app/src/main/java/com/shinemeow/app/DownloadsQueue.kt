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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.luminance
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


    /** 下载管理页：队列条目（状态/进度/重试/取消）+ 清空已完成 */
    @Composable
internal fun MainActivity.DownloadsScreen(onBack: () -> Unit) {
        // 队列统计：VM 侧一次遍历得出（只订阅 status，低频：每张一次）。
        // 原来是页面顶层 2×any + 4×count + firstOrNull 共 7 次全队列遍历，
        // 且其中 firstOrNull 还读了 got —— 一个字节变化就会让整页重算。
        val st = vm.queueStats
        val listState = rememberLazyListState()
        Column(
            // 顶部与主页顶栏同款公式（见主页"顶栏垂直位置"注释）：safeDrawing.Top
            // 自适应状态栏实际高度 + topBarGapDp 余量（设置·外观可调），返回按钮行与主页顶栏按钮同一水平线
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .padding(top = vm.topBarGapDp.value.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 返回按钮：与设置页同款 34dp 圆角色块容器（原 IconButton 48dp 触控目标
                // 会让图标左缘缩进 12dp，与下方卡片不对齐）；Box 左缘 = 16dp 页边距 = 卡片左缘
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
                Text("下载队列", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                // 顶栏动作区：与设置页动作胶囊同风格（描边 ROUND_SMALL 胶囊），
                // 替换原 M3 默认 TextButton（与整体卡片风格不搭）
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(UiSpec.TOPBAR_BTN_GAP)) {
                    if (st.hasActive) {
                        // 全部取消：两段式确认 —— 首点进入待确认态（转 error 红描边 +
                        // errorContainer 填充），再点执行；3 秒未再点自动还原。
                        // 不弹对话框打断流程，只拦手滑（几百张重建队列代价高）。
                        var confirmCancelAll by remember { mutableStateOf(false) }
                        LaunchedEffect(confirmCancelAll) {
                            if (confirmCancelAll) {
                                delay(3000)
                                confirmCancelAll = false
                            }
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(UiSpec.ROUND_SMALL))
                                .background(
                                    if (confirmCancelAll) MaterialTheme.colorScheme.errorContainer
                                    else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (confirmCancelAll) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary.copy(alpha = UiSpec.OUTLINE_ALPHA),
                                    RoundedCornerShape(UiSpec.ROUND_SMALL)
                                )
                                .clickable {
                                    if (confirmCancelAll) {
                                        vm.cancelAllDownloads()
                                        confirmCancelAll = false
                                    } else {
                                        confirmCancelAll = true
                                    }
                                }
                                .padding(horizontal = UiSpec.ACTION_HPAD, vertical = UiSpec.ACTION_VPAD),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (confirmCancelAll) "确认取消？" else "全部取消",
                                color = if (confirmCancelAll) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    if (st.hasFinished) {
                        ActionPill("清空已完成") { vm.clearFinished() }
                    }
                }
            }
            // 总览统计条（总计）：恒显示（空队列 = 0/0/0），去卡片化纯展示。
            // 空队列时第二排两个分支（速率排/完成排）都不命中 → 只有一排计数数字。
            // perfHideOverallBar 仅性能测具隐藏用。
            if (!vm.perfHideOverallBar.value) {
                OverallCard(st)
            }
            LazyColumn(Modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.downloadQueue, key = { it.handle }) { item ->
                    DismissibleQueueRow(item, Modifier.animateItem())
                }
            }
        }
    }

    /** 总览统计条（去卡片化）：无底色无描边，与队列行（灰卡+缩略图）结构区分。
     *  第一排恒定三组计数：已完成 / 总数量 / 排队中 —— 灰标签 + 着色粗体数字（无胶囊底）；
     *  第二排按阶段切换：下载中 → [速度 · 剩 mm:ss · 百分比]，完成 → [平均 · 总时长]（绿色 mono 小字）；
     *  底部 3dp 圆角细进度条（字节权重口径同原总览卡填充）。纯展示，无点击。
     *  got（高频）读取全部关进 OverallFill / PercentText 两个窄作用域，其余部分只在
     *  状态变化（低频）时重组；速度/ETA 由 VM 500ms 采样线程写入，这里只读结果。 */
    @Composable
internal fun MainActivity.OverallCard(st: MainViewModel.QueueStats) {
        val green = UiSpec.accent(vm.darkModeOn)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // 第一排（恒定）：已完成 / 总数量 / 排队中 —— 绿 / amber / primary
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountStat("已完成", st.done, green)
                CountStat("总数量", st.denom, UiSpec.amber(vm.darkModeOn))
                CountStat("排队中", st.queued, MaterialTheme.colorScheme.primary)
            }
            // 第二排（按阶段切换）。runningItem 在两条目交接瞬间会短暂为 null
            // （worker 取下一条的空档，演示模式固定 40ms），此时只要还有排队
            // （且本批已开跑）就保持速率排 —— 否则每张完成时第二排消失一帧再出现
            when {
                st.runningItem != null || (st.queued > 0 && st.done > 0) -> BatchPaceRow(st)
                !st.hasActive && st.hasFinished && st.msDone > 0L -> BatchDoneRow(st)
            }
            // 底部细进度条：灰轨道 + 绿色填充（OverallFill 只画 frac 宽度）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                OverallFill(st, Modifier.fillMaxSize())
            }
        }
    }

    /** 统计数字：灰色小标签 + 着色粗体数字（无胶囊底，数字变化走 RollingText 滚动） */
    @Composable
    private fun MainActivity.CountStat(label: String, value: Int, tint: Color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(5.dp))
            RollingText(
                "$value",
                color = tint,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            )
        }
    }

    /** 卡底细进度条填充：字节权重比例（整批未知大小时退回按张数）。
     *  got 变化只重算这一个窄作用域；drawBehind 只走绘制阶段，不参与测量/布局。
     *  放在 3dp 高的圆角轨道 Box 内，只画 frac 宽度的填充矩形。 */
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
        // 动画值不 `by` 读在组合期：drawBehind 内读 .value 把读取推迟到绘制阶段，
        // 补间期间每帧只重绘这一个矩形，零重组（与 RowFillLayer 同款模式）
        val animState = animateFloatAsState(frac, animationSpec = vm.progressSpec(), label = "queueOverall")
        val green = UiSpec.accent(vm.darkModeOn)
        Box(
            modifier.drawBehind {
                drawRect(color = green, alpha = UiSpec.DONE_FILL_ALPHA, size = Size(size.width * animState.value, size.height))
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PaceText(if (speed > 0) "${humanSize(speed)}/s" else "…", green)
            PaceText(if (eta > 0) "剩 ${fmtDur(eta)}" else "剩 …", green)
            PercentText(st, green)
        }
    }

    /** 第二排（完成后）：队列口径 —— 队列内全部已完成照片一起算：
     *  平均 = Σ已完成字节 / Σ纯下载用时（Σ DONE.elapsedMs，批次间空闲与排队等待不计）；
     *  总时长 = Σ纯下载用时。清空已完成后自然归零，下一批重新累计。 */
    @Composable
    private fun MainActivity.BatchDoneRow(st: MainViewModel.QueueStats) {
        val green = UiSpec.accent(vm.darkModeOn)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PaceText("平均 ${humanSize(st.queueAvgBps)}/s", green)
            PaceText("总时长 ${fmtDur(st.msDone)}", green)
        }
    }

    /** 速率/时长小字：着色 mono（tnum 高频数值防抖动），无胶囊底 */
    @Composable
    private fun MainActivity.PaceText(text: String, tint: Color) {
        Text(
            text,
            color = tint,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
        )
    }

    /** 百分比文字：字节权重（整批未知大小时退回按张数）。got 变化只重组这一个文本。 */
    @Composable
    private fun MainActivity.PercentText(st: MainViewModel.QueueStats, tint: Color) {
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
        PaceText("$pct%", tint)
    }

    /** 毫秒 → m:ss（满 1 小时 h:mm:ss） */
    private fun fmtDur(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)
        else String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
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
        // 整行只读 status（低频：每张状态切换一次才重组）—— got/total/speed（250ms 高频）
        // 的读取全部下沉到 RowFillLayer / RunningPill 两个窄作用域，进度更新不再碰整行
        val st = item.status.value
        Surface(
            shape = RoundedCornerShape(UiSpec.ROUND_LARGE),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Box(Modifier.fillMaxWidth()) {
                // 进度填充铺在卡片背景上：下载中按比例推进（浅绿），完成后整卡铺满（绿加深）；
                // 排队/失败/取消不铺色
                if ((st == QStatus.RUNNING || st == QStatus.DONE) && !vm.perfHideRowFill.value) {
                    RowFillLayer(item, Modifier.matchParentSize())
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
                                QStatus.RUNNING -> RunningPill(item)
                                QStatus.DONE -> {
                                    // 已完成胶囊 + 下载耗时胶囊（精确到 0.1s，tnum 等宽不抖动）
                                    val green = UiSpec.accent(vm.darkModeOn)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(UiSpec.ROUND_SMALL),
                                            color = green.copy(alpha = UiSpec.PILL_TINT)
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
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = UiSpec.PILL_TINT)
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
                                modifier = Modifier.size(UiSpec.ICON_SM)
                            )
                        }
                        QStatus.FAILED -> ActionPill("重试") { vm.retryDownload(item) }
                        else -> {}
                    }
                }
            }
        }
    }

    /** 行进度填充层（窄作用域）：got/total 的读取、目标计算与补间动画全部关在这里。
     *  两个关键点：
     *  1. drawBehind 画矩形而非"宽度随进度"的 Box —— 填充不参与测量/布局
     *     （实测宽度参与布局会让正在下载的行每帧 re-measure，p50 8.6→15.1ms）；
     *  2. animateFloatAsState 的返回值【不用 `by` 在组合期读】，而是在 drawBehind 内
     *     读 .value —— 状态读取被推迟到绘制阶段，动画引擎每帧只调度一次重绘，
     *     零重组零布局。旧实现读在组合期 → 补间期间每帧重组整行（p50 16.5ms），
     *     这就是当年默认 snap 的根因；读取下沉后，线性补间的成本 ≈ 每帧一次 drawRect。
     *  modifier 由调用处传 matchParentSize（BoxScope 接收者扩展，跨不出父 Box 作用域）。 */
    @Composable
    private fun MainActivity.RowFillLayer(item: QueueItem, modifier: Modifier) {
        val st = item.status.value
        // frac 目标：只在 got/total/status 变化时重算（250ms 一次或状态切换），
        // 重算也只重组这个小 Box，不碰 QueueRow 的其余部分
        val fracTarget by remember(item) {
            derivedStateOf {
                when (item.status.value) {
                    QStatus.DONE -> 1f
                    QStatus.RUNNING -> {
                        val t = item.total.value
                        if (t > 0) (item.got.value.toFloat() / t).coerceIn(0f, 1f) else 0f
                    }
                    else -> 0f
                }
            }
        }
        // 返回 State 而非 `by` 委托：动画运行期间组合不参与，只有 drawBehind 在读
        val fillAnim = animateFloatAsState(
            targetValue = fracTarget,
            animationSpec = vm.progressSpec(),
            label = "rowFill"
        )
        val green = UiSpec.accent(vm.darkModeOn)
        val fillAlpha = if (st == QStatus.DONE) 0.30f else 0.20f
        Box(
            modifier.drawBehind {
                drawRect(
                    color = green,
                    alpha = fillAlpha,
                    size = Size(size.width * fillAnim.value, size.height)
                )
            }
        )
    }

    /** 下载中状态胶囊（窄作用域）：got/total/speed（250ms 高频）只在这里读，
     *  进度更新每 250ms 只重组这一个胶囊，行内其余元素完全不动；
     *  tnum 等宽数字 —— 数值刷新时文字宽度稳定不抖动。 */
    @Composable
    private fun MainActivity.RunningPill(item: QueueItem) {
        val green = UiSpec.accent(vm.darkModeOn)
        Surface(
            shape = RoundedCornerShape(UiSpec.ROUND_SMALL),
            color = green.copy(alpha = UiSpec.PILL_TINT)
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

    /** 可划走的队列行：左右滑动越过阈值松手即离开队列。
     *  未下载 → 移出队列；下载中 → 请求取消（当前分块写完即停）并移出；
     *  已完成/已取消/失败 → 直接移除。红色背景 + 删除图标跟随滑动方向露出。
     *  移除走 DownloadManager.dismiss（锁内 remove），worker 收尾对已移除条目写终态无副作用。 */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
internal fun MainActivity.DismissibleQueueRow(item: QueueItem, modifier: Modifier = Modifier) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { v ->
                if (v == SwipeToDismissBoxValue.Settled) false
                else { vm.dismissDownload(item); true }
            }
        )
        SwipeToDismissBox(
            state = dismissState,
            modifier = modifier,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
            backgroundContent = {
                // 露出的背景：errorContainer 红底 + 删除图标，图标贴随滑动来向的一侧
                val fromStart = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(UiSpec.ROUND_LARGE))
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "移出队列",
                        modifier = Modifier.padding(horizontal = 24.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        ) {
            // QueueRow 卡片是半透明底（surfaceVariant 45%），不垫实底会把底层的
            // 红色背景/删除图标透出来；垫与页面同色的不透明底 + 同款圆角，
            // 红底只在行被划开时露出。垫底随行一起滑动，视觉与改动前完全一致。
            Box(
                Modifier
                    .clip(RoundedCornerShape(UiSpec.ROUND_LARGE))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                QueueRow(item)
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
                        // 深档主色在深色模式下偏亮（如橘猫橙 #E8873A），白字对比不足 → 按亮度取前景
                        contentColor = if (green.luminance() > 0.35f) Color(0xFF1B1B1B) else Color.White
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
                        // 全屏 edge-to-edge 弹窗窗口：内容坐标与主窗口对齐，毛玻璃底材可精确回绘
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        ),
                        modifier = glassBackdropModifier(
                            shape = MaterialTheme.shapes.extraLarge,
                            tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
                        ),
                        containerColor = Color.Transparent,
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
            qItem != null && st == QStatus.RUNNING -> PreviewRunningButton(qItem, modifier)
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

    /** 预览页"下载中"按钮（窄作用域）：got/total/speed（250ms 高频）与补间动画关在这里。
     *  与队列行 RowFillLayer 同款双优化：填充用 drawBehind 画（不参与测量/布局）+
     *  绘制阶段读动画值（零重组）。旧实现 fillMaxWidth(frac) 宽度参与布局、组合期
     *  读动画值 → 补间期间每帧重组 + 重测量；写死 500ms 补间也统一为 vm.progressSpec()。 */
    @Composable
    private fun MainActivity.PreviewRunningButton(qItem: QueueItem, modifier: Modifier) {
        val fracTarget by remember(qItem) {
            derivedStateOf {
                val t = qItem.total.value
                if (t > 0) (qItem.got.value.toFloat() / t).coerceIn(0f, 1f) else 0f
            }
        }
        val fillAnim = animateFloatAsState(
            targetValue = fracTarget,
            animationSpec = vm.progressSpec(),
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
                Modifier.matchParentSize().drawBehind {
                    drawRect(color = green, alpha = UiSpec.DONE_FILL_ALPHA, size = Size(size.width * fillAnim.value, size.height))
                }
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