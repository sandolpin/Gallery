package com.sandolpin.gallery.ui.folder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import coil.compose.AsyncImage
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.settings.ScrollDirection
import com.sandolpin.gallery.ui.components.BarIconButton
import com.sandolpin.gallery.ui.components.DeleteConfirmDialog
import com.sandolpin.gallery.ui.components.GlassHeaderHeight
import com.sandolpin.gallery.ui.components.RefreshBox
import com.sandolpin.gallery.ui.components.RenameDialog
import com.sandolpin.gallery.ui.fileop.FileOperationType
import com.sandolpin.gallery.ui.fileop.LocalFileOperationController
import com.sandolpin.gallery.ui.resize.LocalResizeController
import com.sandolpin.gallery.ui.rotation.LocalRotationController
import com.sandolpin.gallery.ui.settings.LocalAppSettings
import com.sandolpin.gallery.ui.components.GlassIconButton
import com.sandolpin.gallery.ui.components.GlassSortButton
import com.sandolpin.gallery.ui.components.GlassSurface
import com.sandolpin.gallery.ui.components.SelectionBar
import com.sandolpin.gallery.ui.components.SelectionMoreMenu
import com.sandolpin.gallery.ui.components.VideoBadge
import com.sandolpin.gallery.ui.components.rememberMediaImageRequest
import com.sandolpin.gallery.ui.viewer.LocalViewerController
import com.sandolpin.gallery.ui.viewer.mediaSharedSource
import com.sandolpin.gallery.ui.viewer.openWithOtherApp
import com.sandolpin.gallery.ui.viewer.shareMediaList

private val GridHorizontalPadding = 12.dp
private val GridSpacing = 6.dp
private val CellCornerRadius = 12.dp

/**
 * フォルダ内画面。半透明ヘッダー(戻る・フォルダ名・検索・並び替え・︙)と、
 * 正方形のグリッド(ファイル名は黒のグラデーションの上に表示)。
 * 長押しで選択モードになり、ヘッダーが「選択数・すべて選択」に変わる。
 * 選択した写真への操作(削除・共有など)は、段階3の続きで追加する。
 */
@Composable
fun FolderDetailScreen(
    onBack: () -> Unit,
    viewModel: FolderDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    // 選んだ写真が、全部お気に入りなら、メニューは「お気に入りから外す」になる
    val allSelectedFavorite = uiState.selectedIds.isNotEmpty() &&
            uiState.selectedIds.all { it in favoriteIds }
    val viewer = LocalViewerController.current
    val fileOperations = LocalFileOperationController.current
    val rotation = LocalRotationController.current
    val resize = LocalResizeController.current
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    // 設定: 削除方法(ゴミ箱へ移動/そのまま削除)と、確認ダイアログの有無
    val settings = LocalAppSettings.current
    val toTrash = settings.deletesToTrash(viewModel.isTrashSupported)
    // 名前を変更する写真(null なら、ダイアログは出さない)
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }

    // 削除の結果などのメッセージを、トーストで表示する
    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // 検索中に戻る操作をしたら、画面を閉じずに、検索だけを閉じる
    BackHandler(enabled = uiState.isSearching) { viewModel.onSearchClose() }
    // 選択中に戻る操作をしたら、画面を閉じずに、選択モードだけを終わる
    // (あとから登録したものが優先されるため、検索より先に効く)
    BackHandler(enabled = uiState.isSelecting) { viewModel.onSelectionClose() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                Box(Modifier.align(Alignment.Center)) { CircularProgressIndicator() }
            }
            uiState.items.isEmpty() -> {
                Text(
                    text = "該当する写真・動画がありません",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                MediaGrid(
                    items = uiState.items,
                    columns = uiState.columns,
                    // 並び順・絞り込みを変えたら、先頭に戻す
                    resetKey = uiState.sort to uiState.filter,
                    isSelecting = uiState.isSelecting,
                    selectedIds = uiState.selectedIds,
                    onMediaClick = { media ->
                        if (uiState.isSelecting) {
                            // 選択モード中のタップは、選ぶ/外す
                            viewModel.onToggleSelect(media.id)
                        } else {
                            // タップしたら、いま表示している一覧(絞り込み・並び順も含む)で、ビューアーを開く
                            viewer.open(uiState.items, media.id)
                        }
                    },
                    showFileName = settings.showFileNameInPhotos,
                    onMediaLongClick = { media ->
                        // 選択モード中の長押しは、何もしない(タップと同じ扱いにはしない)
                        if (!uiState.isSelecting) viewModel.onLongPress(media.id)
                    },
                )
            }
        }

        // ヘッダーは最後に置いて、グリッドの上に重ねる
        DetailHeader(
            state = uiState,
            onBack = onBack,
            onSearchStart = viewModel::onSearchStart,
            onSearchClose = viewModel::onSearchClose,
            onQueryChange = viewModel::onQueryChange,
            onSortSelected = viewModel::onSortSelected,
            onFilterSelected = viewModel::onFilterSelected,
            onColumnsSelected = viewModel::onColumnsSelected,
            onSelectionClose = viewModel::onSelectionClose,
            onSelectAllToggle = viewModel::onSelectAllToggle,
            allSelectedFavorite = allSelectedFavorite,
            onToggleFavorite = viewModel::toggleFavoriteSelected,
            // 名前の変更・ほかのアプリで開くは、1枚だけ選んでいるときに使える
            // コピー・移動: 選んだ写真を渡して、フォルダ選択を始める。選択モードは、そのまま終える
            onCopy = {
                val selected = uiState.items.filter { it.id in uiState.selectedIds }
                fileOperations.start(FileOperationType.Copy, selected)
                viewModel.onSelectionClose()
            },
            onMove = {
                val selected = uiState.items.filter { it.id in uiState.selectedIds }
                fileOperations.start(FileOperationType.Move, selected)
                viewModel.onSelectionClose()
            },
            // 回転: 選んだ写真を渡して、回転の流れ(選択肢 → 回転 → 保存)を始める
            onRotate = {
                val selected = uiState.items.filter { it.id in uiState.selectedIds }
                rotation.start(selected)
                viewModel.onSelectionClose()
            },
            // リサイズ: 選んだ写真を渡して、リサイズの流れ(設定 → 保存)を始める
            onResize = {
                val selected = uiState.items.filter { it.id in uiState.selectedIds }
                resize.start(selected)
                viewModel.onSelectionClose()
            },
            onRename = { renameTarget = uiState.items.firstOrNull { it.id in uiState.selectedIds } },
            onOpenWith = {
                uiState.items.firstOrNull { it.id in uiState.selectedIds }
                    ?.let { openWithOtherApp(context, it) }
            },
            // 1枚も選んでいないときは、何もしない
            onDelete = {
                if (uiState.selectedIds.isNotEmpty()) {
                    // ゴミ箱へ移動で、確認がオフなら、すぐ実行。「そのまま削除」は、常に確認する
                    if (toTrash && !settings.confirmDelete) viewModel.deleteSelected(permanent = false)
                    else showDeleteDialog = true
                }
            },
            // 選んだ写真・動画を、まとめて共有する(表示中の一覧の並び順で渡す)
            onShare = {
                val selected = uiState.items.filter { it.id in uiState.selectedIds }
                shareMediaList(context, selected)
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        renameTarget?.let { target ->
            RenameDialog(
                item = target,
                onRename = { newName -> viewModel.rename(target, newName) },
                onDismiss = { renameTarget = null },
                onRenamed = {
                    renameTarget = null
                    viewModel.onSelectionClose()
                },
            )
        }

        if (showDeleteDialog) {
            DeleteConfirmDialog(
                count = uiState.selectedIds.size,
                toTrash = toTrash,
                onDismiss = { showDeleteDialog = false },
                onConfirm = { permanent ->
                    showDeleteDialog = false
                    viewModel.deleteSelected(permanent)
                },
            )
        }
    }
}

/**
 * 写真・動画のグリッド(フォルダ内・お気に入り・カテゴリ・ゴミ箱で共通)。
 * 設定の「スクロールの向き」が縦なら、[columns] 列のグリッドを縦にスクロール。
 * 横なら、[columns] 行のグリッドを横にスクロールする(このとき、下に引っ張って更新は使えない)。
 */
@Composable
internal fun MediaGrid(
    items: List<MediaItem>,
    columns: Int,
    resetKey: Any,
    isSelecting: Boolean,
    selectedIds: Set<Long>,
    onMediaClick: (MediaItem) -> Unit,
    onMediaLongClick: (MediaItem) -> Unit,
    showFileName: Boolean = true,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(resetKey) { gridState.scrollToItem(0) }

    if (LocalAppSettings.current.scrollDirection == ScrollDirection.Horizontal) {
        LazyHorizontalGrid(
            state = gridState,
            rows = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = GridHorizontalPadding,
                end = GridHorizontalPadding,
                // ヘッダーの高さ分だけ、上を空ける(ヘッダーは、グリッドの上に重ねて表示する)
                top = GlassHeaderHeight,
                bottom = 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
            verticalArrangement = Arrangement.spacedBy(GridSpacing),
        ) {
            items(items = items, key = { it.id }) { item ->
                MediaCell(
                    item = item,
                    isSelecting = isSelecting,
                    selected = item.id in selectedIds,
                    onClick = { onMediaClick(item) },
                    onLongClick = { onMediaLongClick(item) },
                    showFileName = showFileName,
                    horizontal = true,
                )
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
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = GridHorizontalPadding,
                end = GridHorizontalPadding,
                top = GlassHeaderHeight,
                bottom = 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
            verticalArrangement = Arrangement.spacedBy(GridSpacing),
        ) {
            items(items = items, key = { it.id }) { item ->
                MediaCell(
                    item = item,
                    isSelecting = isSelecting,
                    selected = item.id in selectedIds,
                    onClick = { onMediaClick(item) },
                    onLongClick = { onMediaLongClick(item) },
                    showFileName = showFileName,
                )
            }
        }
    }
}

/**
 * 正方形のサムネイル。下部に黒のグラデーションを敷いて、ファイル名を重ねる。
 * 選択中は、赤い枠と薄い色、右上のチェックで示す。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaCell(
    item: MediaItem,
    isSelecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** false なら、下部のファイル名(と黒のグラデーション)を出さない。 */
    showFileName: Boolean = true,
    /** true なら、横スクロールのグリッド用(行の高さに合わせて、正方形にする)。 */
    horizontal: Boolean = false,
) {
    // 選択の色。変えたいときは、ここだけ直す(例: colorScheme.primary)
    val accent = MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            // サイズ指定より前に置く(shared element transition の目印)
            .mediaSharedSource(item.id)
            .then(
                if (horizontal) {
                    Modifier.fillMaxHeight().aspectRatio(1f)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(1f)
                },
            )
            .clip(RoundedCornerShape(CellCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = rememberMediaImageRequest(item),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        item.durationMillis?.let { duration ->
            VideoBadge(
                durationMillis = duration,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        // ファイル名(背景は、下に向かって濃くなる黒のグラデーション)
        if (showFileName) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        ),
                    )
                    .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 6.dp),
            ) {
                Text(
                    text = item.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 選択中: 薄い色と赤い枠(親が角丸で切り抜いているので、角もそろう)
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.18f))
                    .border(3.dp, accent, RoundedCornerShape(CellCornerRadius)),
            )
        }

        // 選択モード中だけ、右上に丸いチェック欄を表示する
        if (isSelecting) {
            SelectionMark(
                selected = selected,
                accent = accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
    }
}

/** 右上の丸いチェック欄。選択中は色つき+チェック、未選択は半透明の白縁。 */
@Composable
internal fun SelectionMark(
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) accent else Color.Black.copy(alpha = 0.35f))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "選択中",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun DetailHeader(
    state: FolderDetailUiState,
    onBack: () -> Unit,
    onSearchStart: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortSelected: (FolderMediaSort) -> Unit,
    onFilterSelected: (MediaFilter) -> Unit,
    onColumnsSelected: (Int) -> Unit,
    onSelectionClose: () -> Unit,
    onSelectAllToggle: () -> Unit,
    allSelectedFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRotate: () -> Unit,
    onResize: () -> Unit,
    onRename: () -> Unit,
    onOpenWith: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GlassHeaderHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            state.isSelecting -> {
                SelectionBar(
                    selectedCount = state.selectedIds.size,
                    totalCount = state.items.size,
                    onClose = onSelectionClose,
                ) {
                    BarIconButton(Icons.Default.Delete, "削除", onDelete)
                    BarIconButton(Icons.Default.Share, "共有", onShare)
                    SelectionMoreMenu(
                        allSelected = state.items.isNotEmpty() &&
                                state.selectedIds.size == state.items.size,
                        onSelectAllToggle = onSelectAllToggle,
                    ) { dismiss ->
                        DropdownMenuItem(
                            text = { Text(if (allSelectedFavorite) "お気に入りから外す" else "お気に入りに追加") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (allSelectedFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                onToggleFavorite()
                                dismiss()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("回転") },
                            leadingIcon = { Icon(Icons.Default.RotateRight, contentDescription = null) },
                            enabled = state.selectedIds.isNotEmpty(),
                            onClick = {
                                onRotate()
                                dismiss()
                            },
                        )
                        // 名前の変更・ほかのアプリで開くは、1枚だけ選んでいるときだけ押せる
                        DropdownMenuItem(
                            text = { Text("名前の変更") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            enabled = state.selectedIds.size == 1,
                            onClick = {
                                onRename()
                                dismiss()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("コピー") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            enabled = state.selectedIds.isNotEmpty(),
                            onClick = {
                                onCopy()
                                dismiss()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("移動") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            enabled = state.selectedIds.isNotEmpty(),
                            onClick = {
                                onMove()
                                dismiss()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("リサイズ") },
                            leadingIcon = { Icon(Icons.Default.PhotoSizeSelectLarge, contentDescription = null) },
                            enabled = state.selectedIds.isNotEmpty(),
                            onClick = {
                                onResize()
                                dismiss()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("ほかのアプリで開く") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                            },
                            enabled = state.selectedIds.size == 1,
                            onClick = {
                                onOpenWith()
                                dismiss()
                            },
                        )
                    }
                }
            }

            state.isSearching -> {
                // 検索中: 戻るボタンが「検索を閉じる」に変わり、フォルダ名が入力欄に変わる
                GlassIconButton(Icons.Default.Close, "検索を閉じる", onSearchClose)
                SearchField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                )
            }

            else -> {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "戻る", onBack)

                GlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = CircleShape,
                ) {
                    Text(
                        text = state.folderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(horizontal = 16.dp),
                    )
                }

                GlassIconButton(Icons.Default.Search, "検索", onSearchStart)

                GlassSortButton(
                    options = FolderMediaSort.entries,
                    selected = state.sort,
                    labelOf = { it.label },
                    onSelected = onSortSelected,
                )

                MoreMenu(
                    filter = state.filter,
                    columns = state.columns,
                    onFilterSelected = onFilterSelected,
                    onColumnsSelected = onColumnsSelected,
                )
            }
        }
    }
}

/** ヘッダーの中で、その場で入力できる検索欄(ファイル名で絞り込む)。 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    // 検索を始めたら、すぐにキーボードを出す
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    GlassSurface(
        modifier = modifier.height(48.dp),
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Default.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "ファイル名で検索",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
        }
    }
}

/**
 * ︙メニュー: 表示の絞り込み(すべて/写真のみ/動画のみ)と、列数(2〜5)。
 * 設定のスクロールの向きが横のときは、「列数」が「行数」になる。
 */
@Composable
private fun MoreMenu(
    filter: MediaFilter,
    columns: Int,
    onFilterSelected: (MediaFilter) -> Unit,
    onColumnsSelected: (Int) -> Unit,
) {
    val horizontal = LocalAppSettings.current.scrollDirection == ScrollDirection.Horizontal
    val unit = if (horizontal) "行" else "列"

    Box {
        var expanded by remember { mutableStateOf(false) }

        GlassIconButton(Icons.Default.MoreVert, "その他", onClick = { expanded = true })

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuSectionLabel("表示")
            MediaFilter.entries.forEach { option ->
                CheckMenuItem(
                    label = option.label,
                    selected = option == filter,
                    onClick = {
                        onFilterSelected(option)
                        expanded = false
                    },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MenuSectionLabel(if (horizontal) "行数" else "列数")
            ColumnOptions.forEach { option ->
                CheckMenuItem(
                    label = "${option}$unit",
                    selected = option == columns,
                    onClick = {
                        onColumnsSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun CheckMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            if (selected) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        onClick = onClick,
    )
}
