package com.sandolpin.gallery.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.ui.components.rememberMediaImageRequest

/** カルーセルの高さ。 */
private val CarouselHeight = 140.dp

/** 一番大きい写真の幅の目安。画面の幅の、半分弱になる。 */
private val PreferredItemWidth = 180.dp

private val CarouselCornerRadius = 28.dp

/** 写真を縮小して読み込むときの大きさ(px)。カルーセルで写真の大きさが変わっても、読み込み直さないように固定する。 */
private const val ThumbnailSizePx = 480

/**
 * カルーセル1枚分。
 * @param label 一番大きい状態のときだけ、下部中央に重ねる文字(null なら出さない)
 */
data class CarouselEntry(
    val media: MediaItem,
    val label: String? = null,
)

/**
 * ホーム画面のカルーセル。大・中・小の写真が並び、スクロールすると、大きい写真が移っていく。
 * material3 の HorizontalMultiBrowseCarousel を使う(material3 1.3.0 では試験的なAPI)。
 * 写真をタップしたら、[onClick] を呼ぶ。
 *
 * @param onLabelClick 指定すると、ラベルが押せるようになる(ラベルの右に「›」が付く)。カテゴリの一覧を開くのに使う。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCarousel(
    entries: List<CarouselEntry>,
    onClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    onLabelClick: (() -> Unit)? = null,
) {
    val state = rememberCarouselState { entries.size }

    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = PreferredItemWidth,
        modifier = modifier
            .fillMaxWidth()
            .height(CarouselHeight),
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { index ->
        val entry = entries[index]
        CarouselCard(
            entry = entry,
            preferredWidth = PreferredItemWidth,
            onClick = { onClick(entry.media) },
            onLabelClick = onLabelClick,
        )
    }
}

/**
 * カルーセルの写真1枚。
 * カルーセルは、写真の大きさに合わせて、中身の幅も変えて表示する。
 * そのため、幅が大きいときだけラベルが見えるようにして(小さくなるにつれて薄くする)、
 * 小さい写真で、文字が切れて読めなくなるのを防ぐ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselItemScope.CarouselCard(
    entry: CarouselEntry,
    preferredWidth: Dp,
    onClick: () -> Unit,
    onLabelClick: (() -> Unit)?,
) {
    // 画像の「版」をキャッシュのキーに含める(回転後に、古い画像が残らない)。大きさは固定して、読み込み直しを減らす
    val request = rememberMediaImageRequest(entry.media, ThumbnailSizePx)

    BoxWithConstraints(
        modifier = Modifier
            .height(CarouselHeight)
            .maskClip(RoundedCornerShape(CarouselCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = request,
            contentDescription = entry.media.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        entry.label?.let { label ->
            // 幅が「目安の幅の95%以上」で完全に見え、「60%以下」で見えなくなる
            val visibility = ((maxWidth - preferredWidth * 0.6f) / (preferredWidth * 0.35f))
                .coerceIn(0f, 1f)
            // ラベルが押せるのは、はっきり見えているときだけ(薄いラベルを、押してしまわないように)
            val labelClickable = onLabelClick != null && visibility > 0.6f
            Text(
                text = if (onLabelClick != null) "$label ›" else label,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .alpha(visibility)
                    .then(
                        if (labelClickable) Modifier.clickable(onClick = onLabelClick!!) else Modifier,
                    )
                    // 写真の上でも読めるよう、下に向かって濃くなる黒のグラデーションを敷く
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                        ),
                    )
                    .padding(top = 24.dp, bottom = 10.dp),
            )
        }
    }
}
