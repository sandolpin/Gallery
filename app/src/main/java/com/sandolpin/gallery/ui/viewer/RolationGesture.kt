package com.sandolpin.gallery.ui.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** この角度(度)より大きくひねるまでは、回さない。ピンチのズームで、少し指がねじれても、回らないようにするため。 */
private const val TwistThresholdDegrees = 10f

/**
 * 2本指のひねりを、見張る(指のイベントは、消費しない。ズームやパンは、そのまま動く)。
 * ひねりが [TwistThresholdDegrees] を超えたら、そのあとの動きを、[onRotate] に、角度の変化(度。時計回りが正)で渡す。
 * 指を離したら、[onEnd] を呼ぶ(ひねって回していたときだけ)。
 *
 * このModifierは、回転する枠の「外側」につけること。外側につければ、指の位置は、回転していない画面の座標になる。
 *
 * @param canStart 最初の指が触れたときに呼ぶ。false なら、この操作の間は、回さない(拡大中は、回さない、など)。
 */
internal fun Modifier.twistRotation(
    enabled: Boolean,
    canStart: () -> Boolean,
    onRotate: (deltaDegrees: Float) -> Unit,
    onEnd: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitEachGesture {
            // 子(ズームする写真)より先に、イベントを見る。消費はしない
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val allowed = canStart()
            var accumulated = 0f
            var rotating = false

            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressedCount = event.changes.count { it.pressed }
                if (allowed && pressedCount >= 2) {
                    val delta = event.calculateRotation()
                    if (rotating) {
                        onRotate(delta)
                    } else {
                        accumulated += delta
                        if (abs(accumulated) >= TwistThresholdDegrees) rotating = true
                    }
                }
            } while (event.changes.any { it.pressed })

            if (rotating) onEnd()
        }
    }
}

/**
 * 写真を [degrees] 度回したときに、回した写真が、表示領域(縦 [viewHeight] × 横 [viewWidth])に収まるように拡大・縮小する倍率。
 * [imageWidth]・[imageHeight] は、写真の縦横比の計算に使う(単位は、比がわかればよい)。
 * 0度(と180度)では、ちょうど 1 になる。
 */
internal fun fitScaleForRotation(
    viewWidth: Float,
    viewHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    degrees: Float,
): Float {
    if (viewWidth <= 0f || viewHeight <= 0f || imageWidth <= 0f || imageHeight <= 0f) return 1f

    // 回す前に、表示領域に収まっている写真の大きさ
    val fit = min(viewWidth / imageWidth, viewHeight / imageHeight)
    val width = imageWidth * fit
    val height = imageHeight * fit

    val radians = degrees * (PI.toFloat() / 180f)
    val c = abs(cos(radians))
    val s = abs(sin(radians))
    // 回した写真を囲む長方形が、表示領域に収まる倍率
    return min(viewWidth / (width * c + height * s), viewHeight / (width * s + height * c))
}

/**
 * 回転(中心は、表示領域の中央)と拡大・縮小([scale])をした枠の中の点(localX, localY)が、
 * 画面では、どこに見えるか(横の位置)。画面の端のタップを、回転した写真でも、画面の左右で判断するために使う。
 */
internal fun toScreenX(
    localX: Float,
    localY: Float,
    viewWidth: Float,
    viewHeight: Float,
    degrees: Float,
    scale: Float,
): Float {
    val cx = viewWidth / 2f
    val cy = viewHeight / 2f
    val radians = degrees * (PI.toFloat() / 180f)
    val dx = localX - cx
    val dy = localY - cy
    // 画面は、下が正のため、時計回りの回転になる
    return cx + scale * (dx * cos(radians) - dy * sin(radians))
}