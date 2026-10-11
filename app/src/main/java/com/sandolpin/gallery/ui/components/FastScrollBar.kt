package com.sandolpin.gallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val BubbleHeight = 40.dp

/**
 * LazyColumn用の、つまんでドラッグできるスクロールバー。
 *
 * - 位置は、ピクセルではなく「全体の何番目の項目か」の割合(先頭=0、末尾=1)で決める。
 *   行の高さがまちまちでも、1万枚規模でも軽く動く。
 * - 月が変わる位置に「・」を打つ([markerIndices] = 各月の先頭項目の番号)。
 *   近すぎる点は間引く。
 * - ドラッグ中は、つまみの左に吹き出しで日付を表示する([labelForIndex])。
 *
 * 呼び出し側で、縦方向の余白(fillMaxHeight + padding)と、配置(align)を指定すること。
 * 吹き出しを表示するため、幅は広め([totalWidth])に取るが、つまみ以外の部分は
 * タッチを受け取らず、後ろのリストのスクロールを邪魔しない。
 */
@Composable
fun LazyListFastScrollbar(
    state: LazyListState,
    itemCount: Int,
    markerIndices: List<Int>,
    labelForIndex: (Int) -> String,
    modifier: Modifier = Modifier,
    thumbWidth: Dp = 22.dp,
    thumbHeight: Dp = 44.dp,
    touchWidth: Dp = 32.dp,
    totalWidth: Dp = 240.dp,
) {
    val scope = rememberCoroutineScope()
    val thumbHeightPx = with(LocalDensity.current) { thumbHeight.toPx() }

    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragIndex by remember { mutableIntStateOf(0) }

    // リストの現在位置(0.0〜1.0)
    val listFraction by remember(state, itemCount) {
        derivedStateOf {
            when {
                itemCount <= 1 -> 0f
                // 一番下までスクロールしたら、つまみも一番下に置く
                state.canScrollBackward && !state.canScrollForward -> 1f
                else -> {
                    val first = state.layoutInfo.visibleItemsInfo.firstOrNull()
                    val partial = if (first != null) {
                        state.firstVisibleItemScrollOffset.toFloat() / first.size.coerceAtLeast(1)
                    } else {
                        0f
                    }
                    ((state.firstVisibleItemIndex + partial) / (itemCount - 1)).coerceIn(0f, 1f)
                }
            }
        }
    }
    // ドラッグ中は、指の位置にそのままつまみを追従させる
    val fraction = if (isDragging) dragFraction else listFraction

    fun jumpTo(yPx: Float) {
        val range = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
        val f = ((yPx - thumbHeightPx / 2f) / range).coerceIn(0f, 1f)
        val target = (f * (itemCount - 1).coerceAtLeast(0)).roundToInt()
        dragFraction = f
        dragIndex = target
        scope.launch { state.scrollToItem(target) }
    }

    val lineColor = MaterialTheme.colorScheme.outline
    val thumbShape = RoundedCornerShape(7.dp)

    Box(
        modifier = modifier
            .width(totalWidth)
            .onSizeChanged { trackHeightPx = it.height.toFloat() },
    ) {
        // 右端: 線・月の点・つまみ(ここだけがタッチを受け取る)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(touchWidth)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            jumpTo(offset.y)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            jumpTo(change.position.y)
                        },
                    )
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            // 縦の細い線
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(lineColor),
            )
            // 月ごとの点(つまみがその月の位置に来たとき、つまみの中心と重なる)
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (itemCount > 1) {
                    val range = size.height - thumbHeightPx
                    val minGap = 10.dp.toPx()
                    val radius = 3.dp.toPx()
                    var lastY = -Float.MAX_VALUE
                    markerIndices.forEach { index ->
                        val y = index.toFloat() / (itemCount - 1) * range + thumbHeightPx / 2f
                        if (y - lastY >= minGap) {
                            drawCircle(lineColor, radius, Offset(size.width / 2f, y))
                            lastY = y
                        }
                    }
                }
            }
            // つまみ
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, (fraction * (trackHeightPx - thumbHeightPx)).roundToInt()) }
                    .size(thumbWidth, thumbHeight)
                    .clip(thumbShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.5.dp, lineColor, thumbShape),
            )
        }

        // ドラッグ中だけ、つまみの左に吹き出しで日付を表示する
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        x = -(touchWidth.roundToPx() + 4.dp.roundToPx()),
                        y = (fraction * (trackHeightPx - thumbHeightPx)).roundToInt() +
                                ((thumbHeightPx - BubbleHeight.toPx()) / 2f).roundToInt(),
                    )
                },
        ) {
            DateBubble(text = labelForIndex(dragIndex))
        }
    }
}

/** つまみの左に出す、右向きのしっぽ付きの吹き出し。 */
@Composable
private fun DateBubble(text: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primaryContainer
    Row(
        modifier = modifier.height(BubbleHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = color,
            shadowElevation = 4.dp,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        // しっぽ(右向きの三角形)
        Canvas(modifier = Modifier.size(width = 8.dp, height = 16.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, color)
        }
    }
}