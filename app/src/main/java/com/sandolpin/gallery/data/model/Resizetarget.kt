package com.sandolpin.gallery.data.model

import kotlin.math.roundToInt

/** リサイズの目標。 */
sealed interface ResizeTarget {
    /** 横幅・縦幅を、それぞれ [percent] %にする(10〜100)。 */
    data class Percent(val percent: Int) : ResizeTarget

    /** 長辺(横幅と縦幅の、長いほう)を [pixels] pxにする。縦横比は保つ(4K=3840、フルHD=1920、HD=1280)。 */
    data class LongSide(val pixels: Int) : ResizeTarget
}

/**
 * 元の大きさ([width]×[height])を、この目標で縮小したときの大きさ(横, 縦)。
 * 縦横比は、元のまま保つ。拡大になる・変化がないときは null(その写真は、リサイズしない)。
 */
fun ResizeTarget.sizeFor(width: Int, height: Int): Pair<Int, Int>? {
    if (width <= 0 || height <= 0) return null
    val scale = when (this) {
        is ResizeTarget.Percent -> percent / 100.0
        is ResizeTarget.LongSide -> pixels.toDouble() / maxOf(width, height)
    }
    if (scale >= 1.0) return null

    val newWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val newHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return if (newWidth >= width && newHeight >= height) null else newWidth to newHeight
}