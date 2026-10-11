package com.sandolpin.gallery.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

// ダイアログの色(画像に合わせた固定色)
private val DeleteRed = Color(0xFFFF6E6E)
private val CancelGray = Color(0xFFBDBDBD)
private val IconCircleGray = Color(0xFFBDBDBD)
private val IconDark = Color(0xFF212121)
private val LongPressTrack = Color(0xFFF0F0F0)

/** 長押しで完全削除するまでに、押し続ける時間(ミリ秒)。 */
private const val LongPressDurationMs = 1000

private val ButtonHeight = 48.dp

/**
 * 削除の確認ダイアログ。
 * ゴミ箱へ移動する設定([toTrash] が true)のとき:
 * - 「OK」: ゴミ箱へ移動
 * - 「長押ししてゴミ箱に移動せず削除」: 押し続けると、カプセル内の赤い部分が右から減っていき、
 *   なくなったところで、ゴミ箱を通さず完全に削除する。途中で指を離すと、赤が元に戻る。
 *
 * [onConfirm] の引数 permanent は、true なら完全に削除、false ならゴミ箱へ。
 */
@Composable
fun DeleteConfirmDialog(
    count: Int,
    toTrash: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (permanent: Boolean) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "写真・動画を削除",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                // 大きなゴミ箱アイコン(灰色の丸の中)
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(IconCircleGray),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = IconDark,
                        modifier = Modifier.size(48.dp),
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = if (toTrash) {
                        "${count}項目をゴミ箱へ移動します。"
                    } else {
                        "${count}項目を完全に削除します。元に戻せません。"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                if (toTrash) {
                    // ゴミ箱へ移動: 「キャンセル」と「OK」、その下に、長押しで完全削除
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PillButton(
                            text = "キャンセル",
                            color = CancelGray,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                        PillButton(
                            text = "OK",
                            color = DeleteRed,
                            onClick = { onConfirm(false) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LongPressDeleteButton(
                        label = "長押ししてゴミ箱に移動せず削除",
                        onCompleted = { onConfirm(true) },
                    )
                } else {
                    // そのまま削除: 「OK」はなく、長押しでだけ削除できる(うっかり消さないための確認)
                    PillButton(
                        text = "キャンセル",
                        color = CancelGray,
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    LongPressDeleteButton(
                        label = "長押しして削除",
                        onCompleted = { onConfirm(true) },
                    )
                }
            }
        }
    }
}

/** 色つきの、丸いカプセル型ボタン(白い太字)。 */
@Composable
private fun PillButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(ButtonHeight),
        shape = CircleShape,
        color = color,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * 長押しで完全削除するボタン。
 * 押している間、カプセルの赤い部分が、右から左へ減っていく。
 * 全部なくなったら [onCompleted]。途中で離したら、赤が元の長さに戻る。
 */
@Composable
private fun LongPressDeleteButton(label: String, onCompleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // 赤い部分の残り(1.0 = 全部赤、0.0 = 赤がなくなった)
    val remaining = remember { Animatable(1f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .clip(CircleShape)
            .background(LongPressTrack)
            // 赤い部分は、再構成せずに、描画だけで更新する(なめらかに動かすため)
            .drawBehind {
                val width = size.width * remaining.value
                if (width > 0f) {
                    drawRoundRect(
                        color = DeleteRed,
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // 押している間、赤を減らしていく。最後まで減ったら、削除を実行する
                        val job = scope.launch {
                            remaining.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(LongPressDurationMs, easing = LinearEasing),
                            )
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCompleted()
                        }
                        tryAwaitRelease()
                        // 最後まで行く前に離したら、中断して、赤を元に戻す
                        if (job.isActive) {
                            job.cancel()
                            scope.launch { remaining.animateTo(1f, tween(200)) }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}