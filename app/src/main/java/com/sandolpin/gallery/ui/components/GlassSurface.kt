package com.sandolpin.gallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object GlassDefaults {
    /** 背景の不透明度。小さいほど、下の文字・写真が透けて見える。 */
    const val Alpha = 0.7f
}

/**
 * Liquid Glass風の半透明の面。下にスクロールしている文字・写真が、うっすら透けて見える。
 * (背景のぼかしは行わない。ぼかしにはライブラリが必要なため、当面は半透明+影のみ)
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    alpha: Float = GlassDefaults.Alpha,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val base = modifier
        .shadow(
            elevation = 6.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.15f),
            spotColor = Color.Black.copy(alpha = 0.25f),
        )
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha))

    Box(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        content = content,
    )
}