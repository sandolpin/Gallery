package com.sandolpin.gallery.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sandolpin.gallery.data.analysis.PhotoCategory
import com.sandolpin.gallery.data.model.MediaFolder
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.settings.HomeSection
import com.sandolpin.gallery.ui.components.RefreshBox
import com.sandolpin.gallery.ui.components.VideoBadge
import com.sandolpin.gallery.ui.components.rememberMediaImageRequest
import com.sandolpin.gallery.ui.settings.LocalAppSettings
import com.sandolpin.gallery.ui.viewer.LocalViewerController
import java.time.LocalTime
import java.util.Locale

private val ScreenPadding = 16.dp
private val TileSize = 96.dp
private val TileCornerRadius = 20.dp

/**
 * ホーム画面。挨拶、統計カード、ピン留め、お気に入り(1枚以上あるときだけ)を、縦に並べる。
 * その下に、カルーセル2種(いろいろな写真・昔に撮った写真)を並べる。
 *
 * @param onOpenFolder ピン留めしたフォルダをタップしたとき(フォルダIDを渡す)
 * @param onOpenFolderTab 「ピン留め」の「>」をタップしたとき(フォルダ画面へ移動)
 * @param onOpenFavorites 「お気に入り」の「>」をタップしたとき(お気に入り一覧へ移動)
 * @param onOpenCategory 「いろいろな写真」の「>」・カテゴリ名をタップしたとき(そのカテゴリの写真の一覧へ移動)
 */
@Composable
fun HomeScreen(
    onOpenFolder: (Long) -> Unit,
    onOpenFolderTab: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenCategory: (PhotoCategory) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val viewer = LocalViewerController.current
    // 設定: ホーム画面に表示する項目と、統計カードの表示
    val settings = LocalAppSettings.current
    val greeting = remember { greetingFor(LocalTime.now().hour) }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // 下に引っ張って更新(設定でOFFにできる)。更新すると、「いろいろな写真」のカテゴリが入れ替わる
    RefreshBox(
        modifier = Modifier.fillMaxSize(),
        onRefresh = viewModel::rerollPickup,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "greeting") {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }

            uiState.stats?.takeIf { settings.showStorageStats }?.let { stats ->
                item(key = "stats") {
                    StatsCard(
                        stats = stats,
                        modifier = Modifier.padding(horizontal = ScreenPadding),
                    )
                }
            }

            if (HomeSection.Pinned in settings.homeSections) item(key = "pinned") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "ピン留め", onMoreClick = onOpenFolderTab)
                    if (uiState.pinned.isEmpty()) {
                        Text(
                            text = "フォルダ画面でフォルダを長押しすると、ここにピン留めできます",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = ScreenPadding),
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(items = uiState.pinned, key = { it.id }) { folder ->
                                PinnedFolderTile(folder = folder, onClick = { onOpenFolder(folder.id) })
                            }
                        }
                    }
                }
            }

            // お気に入りは、1枚以上あるときだけ表示する
            if (HomeSection.Favorites in settings.homeSections && uiState.favoritePreview.isNotEmpty()) {
                item(key = "favorites") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(title = "お気に入り", onMoreClick = onOpenFavorites)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(items = uiState.favoritePreview, key = { it.id }) { media ->
                                PhotoTile(
                                    media = media,
                                    // お気に入り全体を、ページ送りで見られるようにする
                                    onClick = { viewer.open(uiState.favorites, media.id) },
                                )
                            }
                        }
                    }
                }
            }

            // いろいろな写真(カテゴリをランダムに1つ選ぶ。カテゴリ分けできないときは、写真全体からランダム)
            if (HomeSection.Pickup in settings.homeSections && uiState.pickup.isNotEmpty()) {
                item(key = "pickup") {
                    val category = uiState.pickupCategory
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            title = "いろいろな写真を見る",
                            // カテゴリがあるときだけ、「>」で、そのカテゴリの写真の一覧を開ける
                            onMoreClick = category?.let { { onOpenCategory(it) } },
                        )
                        PhotoCarousel(
                            entries = uiState.pickup,
                            // このカルーセルの写真全部を、ページ送りで見られるようにする
                            onClick = { media -> viewer.open(uiState.pickup.map { it.media }, media.id) },
                            // カテゴリ名(大きい写真の下のラベル)を押しても、同じ一覧を開ける
                            onLabelClick = category?.let { { onOpenCategory(it) } },
                        )
                    }
                }
            }

            // 昔に撮った写真(該当する写真がない日は、欄ごと表示しない)
            if (HomeSection.Memories in settings.homeSections && uiState.memories.isNotEmpty()) {
                item(key = "memories") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(title = "昔に撮った写真を見る")
                        PhotoCarousel(
                            entries = uiState.memories,
                            onClick = { media -> viewer.open(uiState.memories.map { it.media }, media.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 5〜10時は「おはようございます」、11〜17時は「こんにちは」、それ以外は「こんばんは」。 */
private fun greetingFor(hour: Int): String = when (hour) {
    in 5..10 -> "おはようございます"
    in 11..17 -> "こんにちは"
    else -> "こんばんは"
}

/** 統計カード: 写真・動画の合計数、ストレージ使用状況、残量目安。 */
@Composable
private fun StatsCard(stats: HomeStats, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            LabelWithIcon(Icons.Default.Image, "端末内にある写真・動画の数")
            Text(
                text = "${formatNumber(stats.totalCount)}枚",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelWithIcon(Icons.Default.Storage, "ストレージ使用状況", modifier = Modifier.weight(1f))
                Text(
                    text = "${stats.usedPercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { stats.usedPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(10.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Text(
                text = "残量目安",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LabelWithIcon(Icons.Default.Image, ":${formatNumber(stats.remainingPhotos)}枚")
                LabelWithIcon(Icons.Default.Movie, ":${formatNumber(stats.remainingVideoMinutes)}分")
            }
        }
    }
}

@Composable
private fun LabelWithIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

/** 見出し + 横線。[onMoreClick] があれば、右端に「>」を出す。 */
@Composable
private fun SectionHeader(
    title: String,
    onMoreClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(modifier = Modifier.weight(1f))
        if (onMoreClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "$title を開く",
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMoreClick),
            )
        }
    }
}

/** ピン留めしたフォルダ: 正方形のカバー写真と、その下に「フォルダ名 (枚数)」。 */
@Composable
private fun PinnedFolderTile(folder: MediaFolder, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(TileSize)
            .clip(RoundedCornerShape(TileCornerRadius))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(TileSize)
                .clip(RoundedCornerShape(TileCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = folder.coverUri,
                contentDescription = folder.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = "${folder.name} (${formatNumber(folder.itemCount)})",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
        )
    }
}

/** お気に入りの写真1枚(正方形)。動画には、左上に再生時間を重ねる。 */
@Composable
private fun PhotoTile(media: MediaItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(TileSize)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(TileCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = rememberMediaImageRequest(media),
            contentDescription = media.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        media.durationMillis?.let { duration ->
            VideoBadge(
                durationMillis = duration,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

/** 3桁ごとにカンマを入れる(10,897)。 */
private fun formatNumber(value: Int): String = String.format(Locale.US, "%,d", value)
