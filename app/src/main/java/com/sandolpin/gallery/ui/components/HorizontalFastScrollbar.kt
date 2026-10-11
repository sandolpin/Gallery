package com.sandolpin.gallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

/**
 * LazyRow(横スクロール)用の、つまんでドラッグできるスクロールバー。画面の下端に置く。
 * 縦スクロール用の [LazyListFastScrollbar] を、縦と横で入れ替えたもの。
 *
 * - 位置は、ピクセルではなく「全体の何番目の項目か」の割合(先頭=0、末尾=1)で決める。
 * - 月が変わる位置に「・」を打つ([markerIndices] = 各月の先頭項目の番号)。近すぎる点は間引く。
 * - ドラッグ中は、つまみの上に吹き出しで日付を表示する([labelForIndex])。
 *
 * 呼び出し側で、横方向の余白と、配置(align)を指定すること。
 * 吹き出しを表示するため、高さは広め([totalHeight])に取るが、つまみ以外の部分は
 * タッチを受け取らず、後ろのリストのスクロールを邪魔しない。
 */
@Composable
fun LazyListHorizontalFastScrollbar(
    state: LazyListState,
    itemCount: Int,
    markerIndices: List<Int>,
    labelForIndex: (Int) -> String,
    modifier: Modifier = Modifier,
    thumbWidth: Dp = 44.dp,
    thumbHeight: Dp = 22.dp,
    touchHeight: Dp = 32.dp,
    totalHeight: Dp = 84.dp,
) {
    val scope = rememberCoroutineScope()
    val thumbWidthPx = with(LocalDensity.current) { thumbWidth.toPx() }

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var bubbleWidthPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var dragIndex by remember { mutableIntStateOf(0) }

    // リストの現在位置(0.0〜1.0)
    val listFraction by remember(state, itemCount) {
        derivedStateOf {
            when {
                itemCount <= 1 -> 0f
                // 一番右までスクロールしたら、つまみも一番右に置く
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

    fun jumpTo(xPx: Float) {
        val range = (trackWidthPx - thumbWidthPx).coerceAtLeast(1f)
        val f = ((xPx - thumbWidthPx / 2f) / range).coerceIn(0f, 1f)
        val target = (f * (itemCount - 1).coerceAtLeast(0)).roundToInt()
        dragFraction = f
        dragIndex = target
        scope.launch { state.scrollToItem(target) }
    }

    val lineColor = MaterialTheme.colorScheme.outline
    val thumbShape = RoundedCornerShape(7.dp)

    Box(
        modifier = modifier
            .height(totalHeight)
            .onSizeChanged { trackWidthPx = it.width.toFloat() },
    ) {
        // 下端: 線・月の点・つまみ(ここだけがタッチを受け取る)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(touchHeight)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            jumpTo(offset.x)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            jumpTo(change.position.x)
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // 横の細い線
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(lineColor),
            )
            // 月ごとの点(つまみがその月の位置に来たとき、つまみの中心と重なる)
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (itemCount > 1) {
                    val range = size.width - thumbWidthPx
                    val minGap = 10.dp.toPx()
                    val radius = 3.dp.toPx()
                    var lastX = -Float.MAX_VALUE
                    markerIndices.forEach { index ->
                        val x = index.toFloat() / (itemCount - 1) * range + thumbWidthPx / 2f
                        if (x - lastX >= minGap) {
                            drawCircle(lineColor, radius, Offset(x, size.height / 2f))
                            lastX = x
                        }
                    }
                }
            }
            // つまみ
            Box(
                modifier = Modifier
                    .offset { IntOffset((fraction * (trackWidthPx - thumbWidthPx)).roundToInt(), 0) }
                    .size(thumbWidth, thumbHeight)
                    .clip(thumbShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.5.dp, lineColor, thumbShape),
            )
        }

        // ドラッグ中だけ、つまみの上に吹き出しで日付を表示する(画面の左右の端からは、はみ出さない)
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    val thumbCenter = fraction * (trackWidthPx - thumbWidthPx) + thumbWidthPx / 2f
                    val x = (thumbCenter - bubbleWidthPx / 2f)
                        .coerceIn(0f, (trackWidthPx - bubbleWidthPx).coerceAtLeast(0f))
                    IntOffset(x.roundToInt(), 0)
                }
                .onSizeChanged { bubbleWidthPx = it.width.toFloat() },
        ) {
            DateBubbleDown(text = labelForIndex(dragIndex))
        }
    }
}

/** つまみの上に出す、下向きのしっぽ付きの吹き出し。 */
@Composable
private fun DateBubbleDown(text: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primaryContainer
    Column(
        modifier = modifier.padding(bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
        // しっぽ(下向きの三角形)
        Canvas(modifier = Modifier.size(width = 16.dp, height = 8.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path, color)
        }
    }
}
