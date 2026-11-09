package cn.levanll.inventory.ui

import cn.levanll.inventory.i18n.I18n

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.channels.Channel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.drawscope.DrawScope

/** 数量步进器：按钮热区 48dp，按单位步进 */
@Composable
fun QtyStepper(
    qty: Double,
    unit: String,
    min: Double,
    max: Double,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val step = cn.levanll.inventory.domain.Constants.stepOfUnit(unit)
    val canMinus = qty - step >= min
    val canPlus = qty + step <= max

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StepButton("－", canMinus, MaterialTheme.colorScheme.onSurface) {
            if (canMinus) onChange(cn.levanll.inventory.domain.round2(qty - step))
        }
        Text(
            text = fmtQty(qty),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        StepButton("＋", canPlus, MaterialTheme.colorScheme.primary) {
            if (canPlus) onChange(cn.levanll.inventory.domain.round2(qty + step))
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, tint: Color, onClick: () -> Unit) {
    // 用 Box + clickable 而不是 TextButton：TextButton 自带最小尺寸与内边距，
    // 套进固定尺寸的 Box 会把实际热区压到很小，导致点击不灵敏。
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 22.sp,
            color = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun fmtQty(v: Double): String {
    return if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString()
    else (Math.round(v * 100.0) / 100.0).toString()
}

/** 区块标题 */
@Composable
fun SectionLabel(text: String, required: Boolean = false) {
    Row(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (required) {
            Text(" *", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** 空状态 */
@Composable
fun EmptyState(title: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onAction) {
            Text(
                action,
                fontSize = 15.sp,
                color = Color.White,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            )
        }
    }
}

/** 确认对话框 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium) },
        text = { Text(message, fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(I18n.s("取消", "Cancel")) }
        }
    )
}

/**
 * 列表滚动指示条，可拖动。
 *
 * 性能设计（这是本文件最需要注意的地方）：
 *   1. 滑块尺寸与位置**只在 draw 阶段计算**（drawBehind）。
 *      若在组合阶段读 listState.layoutInfo，每滚动一帧都会触发重组，
 *      这是最初卡顿的主因。
 *   2. 拖动用 rememberDraggableState，回调里只做整数运算并 trySend，
 *      不 launch 协程；由唯一一个 LaunchedEffect 消费目标索引，
 *      避免高频拖动时协程堆积（这是「拉动延迟明显」的原因）。
 *   3. 拖动起点做快照（baseIndex），位移用累加值。
 *      若边拖边读 firstVisibleItemIndex，会与自身滚动形成正反馈而抖动。
 *   4. Channel 用 CONFLATED：拖动快于列表响应时只保留最新目标，不落后。
 *
 * 视觉设计：
 *   - 平时 4dp 半透明，不干扰阅读
 *   - 按下变粗到 7dp 并加深，明确「抓得住」
 *   - 触摸热区 24dp，4dp 的线不可能点得准
 */
@Composable
fun ScrollIndicator(
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    minThumbHeight: Dp = 32.dp
) {
    val density = LocalDensity.current
    val minThumbPx = with(density) { minThumbHeight.toPx() }
    val barColor = MaterialTheme.colorScheme.onSurfaceVariant

    var dragging by remember { mutableStateOf(false) }
    // 拖动起点快照与累加位移
    var baseIndex by remember { mutableFloatStateOf(0f) }
    var accumPx by remember { mutableFloatStateOf(0f) }
    var trackPx by remember { mutableFloatStateOf(0f) }
    var thumbStartPx by remember { mutableFloatStateOf(0f) }

    // 唯一消费者：把目标索引推给列表。scrollToItem 本身是瞬时的，配 CONFLATED 足够跟手
    val requests = remember { Channel<Int>(Channel.CONFLATED) }
    LaunchedEffect(listState) {
        for (target in requests) {
            listState.scrollToItem(target)
        }
    }

    val thumbWidth by animateDpAsState(
        targetValue = if (dragging) 7.dp else 4.dp,
        animationSpec = tween(120),
        label = "thumbWidth"
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (dragging) 0.92f else if (listState.isScrollInProgress) 0.78f else 0.40f,
        animationSpec = tween(180),
        label = "thumbAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .onSizeChanged { trackPx = it.height.toFloat() }

            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    if (trackPx <= 0f) return@rememberDraggableState
                    val info = listState.layoutInfo
                    val total = info.totalItemsCount
                    val visible = info.visibleItemsInfo.size
                    if (total <= visible) return@rememberDraggableState
                    val maxIndex = (total - visible).coerceAtLeast(1)

                    // 用「滑块行程」而不是条目索引做基准：
                    // 手指位移直接映射成滑块位置，滑块走完 → 列表到底。
                    // 之前的写法拿 baseIndex 累加会产生量纲错配，永远到不了 maxIndex。
                    val thumb = maxOf(minThumbPx, trackPx * visible / total.toFloat())
                    val usablePx = (trackPx - thumb).coerceAtLeast(1f)

                    accumPx += delta
                    // accumPx 是相对拖动起点的滑块位移；thumbStartPx 是起点时滑块已走的距离
                    val thumbPos = (thumbStartPx + accumPx).coerceIn(0f, usablePx)
                    val frac = if (usablePx > 0f) thumbPos / usablePx else 0f
                    val target = (frac * maxIndex).toInt().coerceIn(0, maxIndex)
                    requests.trySend(target)
                },
                onDragStarted = {
                    dragging = true
                    accumPx = 0f
                    // 记录起点时滑块已走到的像素位置，拖动从此处继续
                    val info = listState.layoutInfo
                    val total = info.totalItemsCount
                    val visible = info.visibleItemsInfo.size
                    if (total > visible && trackPx > 0f) {
                        val thumb = maxOf(minThumbPx, trackPx * visible / total.toFloat())
                        val usablePx = (trackPx - thumb).coerceAtLeast(1f)
                        val maxIndex = (total - visible).toFloat().coerceAtLeast(1f)
                        thumbStartPx = usablePx * (listState.firstVisibleItemIndex / maxIndex).coerceIn(0f, 1f)
                    } else {
                        thumbStartPx = 0f
                    }
                },
                onDragStopped = { dragging = false }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 3.dp)
                .drawBehind {
                    val info = listState.layoutInfo
                    val total = info.totalItemsCount
                    val visible = info.visibleItemsInfo.size
                    if (total <= visible || visible == 0 || trackPx <= 0f) return@drawBehind

                    val thumbPx = maxOf(minThumbPx, trackPx * visible / total.toFloat())
                    val usablePx = (trackPx - thumbPx).coerceAtLeast(0f)

                    // 像素级进度：只用 index 会在条目间跳动，加上首个可见项的偏移就平滑了
                    val first = info.visibleItemsInfo.firstOrNull()
                    val pos = if (first != null && first.size > 0) {
                        first.index + (-first.offset).toFloat() / first.size
                    } else 0f
                    val maxIndex = (total - visible).toFloat().coerceAtLeast(1f)
                    val progress = (pos / maxIndex).coerceIn(0f, 1f)

                    val offsetY = usablePx * progress
                    val w = thumbWidth.toPx()
                    val canvasW = this.size.width
                    drawRoundRect(
                        color = barColor.copy(alpha = thumbAlpha),
                        topLeft = Offset(canvasW - w, offsetY),
                        size = Size(w, thumbPx),
                        cornerRadius = CornerRadius(w / 2f, w / 2f)
                    )
                }
        )
    }
}