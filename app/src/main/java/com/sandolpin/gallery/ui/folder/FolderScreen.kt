package com.sandolpin.gallery.ui.folder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sandolpin.gallery.data.model.MediaFolder
import com.sandolpin.gallery.ui.components.GlassHeaderHeight
import com.sandolpin.gallery.ui.components.GlassSearchPill
import com.sandolpin.gallery.data.settings.FolderNamePosition
import com.sandolpin.gallery.data.settings.ScrollDirection
import com.sandolpin.gallery.ui.components.GlassSortButton
import com.sandolpin.gallery.ui.components.RefreshBox
import com.sandolpin.gallery.ui.settings.LocalAppSettings
import java.util.Locale

/**
 * グリッドの分割数。1〜5のどの列数でも割り切れる数(60)にしておくと、
 * 列数の違うピン留め(2列)とその他(3列)を、1つのグリッドで並べられる。
 * 列数は、設定画面(1〜5)から変えられる。
 * (横スクロールのときは、「列」が「行」になる。分け方は同じ)
 */
private const val GridSpan = 60

private val ScreenHorizontalPadding = 16.dp
private val ItemSpacing = 12.dp
private val PinnedCornerRadius = 24.dp
private val FolderCornerRadius = 16.dp

/** 横スクロールのとき、フォルダ名を画像の外に出す場合に、画像の下に空ける高さ(2〜3行ぶん)。 */
private val HorizontalLabelReserve = 56.dp

/** 横スクロールのとき、お気に入り・ゴミ箱の入口の幅。 */
private val HorizontalEntryWidth = 104.dp

/**
 * フォルダ一覧画面。上部ヘッダー(検索バー・並び替え)、ピン留め欄、その他のフォルダ。
 * 設定のスクロールの向きが横のときは、グリッドを横にスクロールする(ピン留め・フォルダの「列数」は、「行数」になる)。
 */
@Composable
fun FolderScreen(
    onSearchClick: () -> Unit,
    onFolderClick: (MediaFolder) -> Unit,
    onTrashClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = LocalAppSettings.current

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                Box(Modifier.align(Alignment.Center)) { CircularProgressIndicator() }
            }
            uiState.isEmpty -> {
                Text(
                    text = "フォルダが見つかりませんでした",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                FolderGrid(
                    pinned = uiState.pinned,
                    others = uiState.others,
                    onFolderClick = onFolderClick,
                    onTogglePin = viewModel::onTogglePin,
                    // ゴミ箱の入口は、一番下に表示する(設定で、ゴミ箱を使う・フォルダ画面に表示するがONのときだけ)
                    showTrash = viewModel.isTrashSupported && settings.useTrash && settings.showTrashInFolders,
                    trashCount = uiState.trashCount,
                    onTrashClick = onTrashClick,
                    favoriteCount = uiState.favoriteCount,
                    onFavoritesClick = onFavoritesClick,
                    pinnedColumns = settings.pinnedColumns,
                    folderColumns = settings.folderColumns,
                    namePosition = settings.folderNamePosition,
                    showCount = settings.showFolderCount,
                    horizontal = settings.scrollDirection == ScrollDirection.Horizontal,
                )
            }
        }

        // ヘッダーは最後に置いて、リストの上に重ねる
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(GlassHeaderHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassSearchPill(
                onClick = onSearchClick,
                modifier = Modifier.weight(1f),
            )
            GlassSortButton(
                options = FolderSortOrder.entries,
                selected = uiState.sortOrder,
                labelOf = { it.label },
                onSelected = viewModel::onSortSelected,
            )
        }
    }
}

@Composable
private fun FolderGrid(
    pinned: List<MediaFolder>,
    others: List<MediaFolder>,
    onFolderClick: (MediaFolder) -> Unit,
    onTogglePin: (MediaFolder, Boolean) -> Unit,
    showTrash: Boolean,
    trashCount: Int,
    onTrashClick: () -> Unit,
    favoriteCount: Int,
    onFavoritesClick: () -> Unit,
    pinnedColumns: Int,
    folderColumns: Int,
    namePosition: FolderNamePosition,
    showCount: Boolean,
    horizontal: Boolean,
) {
    val favoriteLabel = "お気に入り (${String.format(Locale.US, "%,d", favoriteCount)})"
    val trashLabel = "ゴミ箱 (${String.format(Locale.US, "%,d", trashCount)})"

    if (horizontal) {
        // 横スクロール(下に引っ張って更新は、使えない)
        LazyHorizontalGrid(
            rows = GridCells.Fixed(GridSpan),
            modifier = Modifier.fillMaxSize(),
            // 縦の間隔は、縦スクロールのときと同じく、各カードの上下の余白(間隔の半分ずつ)で作る
            contentPadding = PaddingValues(
                start = ScreenHorizontalPadding,
                end = ScreenHorizontalPadding,
                top = GlassHeaderHeight - ItemSpacing / 2,
                bottom = 16.dp - ItemSpacing / 2,
            ),
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // お気に入りは、一番左に固定で表示する(1枚もなくても表示する)
            item(key = "favorites", span = { GridItemSpan(maxLineSpan) }) {
                FolderEntryTile(icon = Icons.Default.Star, label = favoriteLabel, onClick = onFavoritesClick)
            }

            if (pinned.isNotEmpty()) {
                item(key = "pinned_header", span = { GridItemSpan(maxLineSpan) }) {
                    HorizontalPinnedHeader()
                }
                items(
                    items = pinned,
                    key = { "p_${it.id}" },
                    span = { GridItemSpan(GridSpan / pinnedColumns) },
                ) { folder ->
                    HorizontalFolderCard(
                        folder = folder,
                        isPinned = true,
                        cornerRadius = PinnedCornerRadius,
                        onClick = { onFolderClick(folder) },
                        onTogglePin = { onTogglePin(folder, false) },
                        namePosition = namePosition,
                        showCount = showCount,
                    )
                }
                item(key = "pinned_divider", span = { GridItemSpan(maxLineSpan) }) {
                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = ItemSpacing / 2 + 4.dp),
                    )
                }
            }

            items(
                items = others,
                key = { "f_${it.id}" },
                span = { GridItemSpan(GridSpan / folderColumns) },
            ) { folder ->
                HorizontalFolderCard(
                    folder = folder,
                    isPinned = false,
                    cornerRadius = FolderCornerRadius,
                    onClick = { onFolderClick(folder) },
                    onTogglePin = { onTogglePin(folder, true) },
                    namePosition = namePosition,
                    showCount = showCount,
                )
            }

            if (showTrash) {
                item(key = "trash", span = { GridItemSpan(maxLineSpan) }) {
                    FolderEntryTile(icon = Icons.Default.Delete, label = trashLabel, onClick = onTrashClick)
                }
            }
        }
        return
    }

    // 下に引っ張って更新。更新の表示は、ヘッダーに隠れないよう、ヘッダーの下に出す
    RefreshBox(
        modifier = Modifier.fillMaxSize(),
        indicatorTopPadding = GlassHeaderHeight,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GridSpan),
            modifier = Modifier.fillMaxSize(),
            // 横の間隔は、グリッドの設定ではなく、各カードの左右の余白(間隔の半分ずつ)で作る。
            // 60分割のグリッドに横の間隔を指定すると、60マスの「各マスの間」に入ってしまい、
            // カードの幅が列数ごとにずれるため。その分、画面の端の余白を間隔の半分だけ減らす。
            contentPadding = PaddingValues(
                start = ScreenHorizontalPadding - ItemSpacing / 2,
                end = ScreenHorizontalPadding - ItemSpacing / 2,
                top = GlassHeaderHeight,
                bottom = 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            // お気に入りは、一番上に固定で表示する(1枚もなくても表示する)
            item(key = "favorites", span = { GridItemSpan(maxLineSpan) }) {
                FolderEntryRow(
                    icon = Icons.Default.Star,
                    label = favoriteLabel,
                    onClick = onFavoritesClick,
                )
            }

            if (pinned.isNotEmpty()) {
                item(key = "pinned_header", span = { GridItemSpan(maxLineSpan) }) {
                    PinnedHeader()
                }
                items(
                    items = pinned,
                    key = { "p_${it.id}" },
                    span = { GridItemSpan(GridSpan / pinnedColumns) },
                ) { folder ->
                    FolderCard(
                        folder = folder,
                        isPinned = true,
                        cornerRadius = PinnedCornerRadius,
                        onClick = { onFolderClick(folder) },
                        onTogglePin = { onTogglePin(folder, false) },
                        namePosition = namePosition,
                        showCount = showCount,
                    )
                }
                item(key = "pinned_divider", span = { GridItemSpan(maxLineSpan) }) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = ItemSpacing / 2, vertical = 4.dp),
                    )
                }
            }

            items(
                items = others,
                key = { "f_${it.id}" },
                span = { GridItemSpan(GridSpan / folderColumns) },
            ) { folder ->
                FolderCard(
                    folder = folder,
                    isPinned = false,
                    cornerRadius = FolderCornerRadius,
                    onClick = { onFolderClick(folder) },
                    onTogglePin = { onTogglePin(folder, true) },
                    namePosition = namePosition,
                    showCount = showCount,
                )
            }

            if (showTrash) {
                item(key = "trash", span = { GridItemSpan(maxLineSpan) }) {
                    FolderEntryRow(
                        icon = Icons.Default.Delete,
                        label = trashLabel,
                        onClick = onTrashClick,
                    )
                }
            }
        }
    }
}

/** 一覧の一番上(お気に入り)・一番下(ゴミ箱)に置く、特別な入口の行。 */
@Composable
private fun FolderEntryRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ItemSpacing / 2, vertical = 4.dp),
        shape = RoundedCornerShape(FolderCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 横スクロールのときの、お気に入り・ゴミ箱の入口。縦に長いカードで、アイコンと文字を縦に並べる。 */
@Composable
private fun FolderEntryTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxHeight()
            .width(HorizontalEntryWidth)
            .padding(vertical = ItemSpacing / 2),
        shape = RoundedCornerShape(FolderCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PinnedHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ItemSpacing / 2, end = ItemSpacing / 2, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PushPin,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "ピン留め",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

/** 横スクロールのときの「ピン留め」の見出し。ピンのアイコンと、下に伸びる縦線。 */
@Composable
private fun HorizontalPinnedHeader() {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .padding(vertical = ItemSpacing / 2 + 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.PushPin,
            contentDescription = "ピン留め",
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(8.dp))
        VerticalDivider(modifier = Modifier.weight(1f))
    }
}

/** フォルダ1件。タップで開く、長押しでメニュー(ピン留めする/外す)。縦スクロール用。 */
@Composable
private fun FolderCard(
    folder: MediaFolder,
    isPinned: Boolean,
    cornerRadius: Dp,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    namePosition: FolderNamePosition,
    showCount: Boolean,
) {
    FolderCardBody(
        folder = folder,
        isPinned = isPinned,
        cornerRadius = cornerRadius,
        onClick = onClick,
        onTogglePin = onTogglePin,
        namePosition = namePosition,
        showCount = showCount,
        modifier = Modifier.padding(horizontal = ItemSpacing / 2),
        coverModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    )
}

/**
 * フォルダ1件。横スクロール用。
 * 行の高さから、画像(正方形)の大きさを決める(フォルダ名を画像の外に出すときは、その分の高さを空ける)。
 */
@Composable
private fun HorizontalFolderCard(
    folder: MediaFolder,
    isPinned: Boolean,
    cornerRadius: Dp,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    namePosition: FolderNamePosition,
    showCount: Boolean,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = ItemSpacing / 2),
    ) {
        val reserve = if (namePosition == FolderNamePosition.Outside) HorizontalLabelReserve else 0.dp
        val cover = (maxHeight - reserve).coerceAtLeast(48.dp)
        FolderCardBody(
            folder = folder,
            isPinned = isPinned,
            cornerRadius = cornerRadius,
            onClick = onClick,
            onTogglePin = onTogglePin,
            namePosition = namePosition,
            showCount = showCount,
            modifier = Modifier.width(cover),
            coverModifier = Modifier.size(cover),
        )
    }
}

/** フォルダ1件の中身(縦・横で共通)。[coverModifier] で、画像の大きさを決める。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCardBody(
    folder: MediaFolder,
    isPinned: Boolean,
    cornerRadius: Dp,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    namePosition: FolderNamePosition,
    showCount: Boolean,
    modifier: Modifier,
    coverModifier: Modifier,
) {
    // 「フォルダ名 (枚数)」。枚数は、設定でON/OFFできる
    val label = if (showCount) {
        "${folder.name} (${String.format(Locale.US, "%,d", folder.itemCount)})"
    } else {
        folder.name
    }
    val labelStyle = if (isPinned) {
        MaterialTheme.typography.titleSmall
    } else {
        MaterialTheme.typography.labelLarge
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { menuExpanded = true },
        ),
    ) {
        Box(
            modifier = coverModifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = folder.coverUri,
                contentDescription = folder.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // フォルダ名を画像の中に表示する設定のとき: 下に黒のグラデーションを敷いて、白い文字で重ねる
            if (namePosition == FolderNamePosition.Inside) {
                Text(
                    text = label,
                    style = labelStyle,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                            ),
                        )
                        .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 6.dp),
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(if (isPinned) "ピン留めを外す" else "ピン留めする") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.PushPin, contentDescription = null)
                    },
                    onClick = {
                        onTogglePin()
                        menuExpanded = false
                    },
                )
            }
        }

        // フォルダ名(枚数)を、画像の外に表示する設定のとき
        if (namePosition == FolderNamePosition.Outside) {
            Text(
                text = label,
                style = labelStyle,
                fontWeight = FontWeight.Bold,
                // 入りきらない場合は、複数行で表示する(長すぎる場合のみ、3行目で省略)
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}
