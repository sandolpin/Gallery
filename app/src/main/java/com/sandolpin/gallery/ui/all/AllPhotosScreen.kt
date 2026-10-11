package com.sandolpin.gallery.ui.all

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.settings.AllPhotosLayout
import com.sandolpin.gallery.data.settings.ScrollDirection
import com.sandolpin.gallery.ui.components.BarIconButton
import com.sandolpin.gallery.ui.components.DeleteConfirmDialog
import com.sandolpin.gallery.ui.components.GlassSurface
import com.sandolpin.gallery.ui.components.LazyListFastScrollbar
import com.sandolpin.gallery.ui.components.LazyListHorizontalFastScrollbar
import com.sandolpin.gallery.ui.components.RefreshBox
import com.sandolpin.gallery.ui.components.RenameDialog
import com.sandolpin.gallery.ui.fileop.FileOperationType
import com.sandolpin.gallery.ui.fileop.LocalFileOperationController
import com.sandolpin.gallery.ui.resize.LocalResizeController
import com.sandolpin.gallery.ui.rotation.LocalRotationController
import com.sandolpin.gallery.ui.settings.LocalAppSettings
import com.sandolpin.gallery.ui.components.SelectionBar
import com.sandolpin.gallery.ui.components.SelectionMoreMenu
import com.sandolpin.gallery.ui.components.VideoBadge
import com.sandolpin.gallery.ui.components.rememberMediaImageRequest
import com.sandolpin.gallery.ui.folder.SelectionMark
import com.sandolpin.gallery.ui.viewer.LocalViewerController
import com.sandolpin.gallery.ui.viewer.mediaSharedSource
import com.sandolpin.gallery.ui.viewer.openWithOtherApp
import com.sandolpin.gallery.ui.viewer.shareMediaList
import java.time.LocalDate

// 見た目の調整用の定数(あとから設定画面で変えられるようにする想定)
private val ScreenStartPadding = 8.dp
/** 右端のスクロールバーが写真に重ならないよう、右側は広めに空ける。 */
private val ScreenEndPadding = 28.dp
private const val ItemSpacingDp = 2f
private const val TargetRowHeightDp = 180f
private val PhotoCornerRadius = 12.dp

// 横スクロール用
/** 日付の見出し(ピルと横線)の帯の高さと、写真との間の余白。 */
private val StripHeight = 44.dp
private val StripGap = 6.dp
/** 画面の下端のスクロールバー用に、空けておく高さ。 */
private val HorizontalBottomReserve = 40.dp
/** 日付の最後の列と、次の日の最初の列との間の余白(横線は、ここで途切れる)。 */
private val DayEndGap = 16.dp
/** 日付のピルと、横線の間の余白。 */
private val PillLineGap = 10.dp
/** 日付のピルの右に、最低限残す横線の長さ。 */
private val MinLineLength = 16.dp
/** 横スクロールの、目標の列の幅(「写真によって変化する」のとき)。 */
private const val TargetColumnWidthDp = 200f

/**
 * 「すべて」画面。上部ヘッダー(年月・検索バー・並び替え)、
 * 日付見出し + 縦横比に合わせたレイアウト、右端(横スクロールのときは下端)のスクロールバー。
 * 長押しで選択モードになり、ヘッダーが「選択数・削除・共有・︙」のバーに変わる。
 */
@Composable
fun AllPhotosScreen(
    onSearchClick: () -> Unit,
    viewModel: AllPhotosViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val context = LocalContext.current
    val fileOperations = LocalFileOperationController.current
    val rotation = LocalRotationController.current
    val resize = LocalResizeController.current
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

    // 選択中に戻る操作をしたら、選択モードだけを終わる
    BackHandler(enabled = uiState.isSelecting) { viewModel.onSelectionClose() }

    if (uiState.isLoading || uiState.isEmpty) {
        // 読み込み中・写真なしのときも、ヘッダーは表示する
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.Center)) {
                if (uiState.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text("写真が見つかりませんでした", style = MaterialTheme.typography.bodyLarge)
                }
            }
            AllPhotosHeader(
                title = "すべて",
                sortOrder = uiState.sortOrder,
                onSortSelected = viewModel::onSortSelected,
                onSearchClick = onSearchClick,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    } else {
        JustifiedPhotoList(
            days = uiState.days,
            sortOrder = uiState.sortOrder,
            onSortSelected = viewModel::onSortSelected,
            onSearchClick = onSearchClick,
            isSelecting = uiState.isSelecting,
            selectedIds = uiState.selectedIds,
            onToggleSelect = viewModel::onToggleSelect,
            onLongPress = viewModel::onLongPress,
            onSelectionClose = viewModel::onSelectionClose,
            onSelectAllToggle = viewModel::onSelectAllToggle,
            // 選んだ写真が、全部お気に入りなら、メニューは「お気に入りから外す」になる
            allSelectedFavorite = uiState.selectedIds.isNotEmpty() &&
                    uiState.selectedIds.all { it in favoriteIds },
            onToggleFavorite = viewModel::toggleFavoriteSelected,
            // 名前の変更・ほかのアプリで開くは、1枚だけ選んでいるときに使える
            // コピー・移動: 選んだ写真を渡して、フォルダ選択を始める。選択モードは、そのまま終える
            onCopy = {
                val selected = uiState.days.flatMap { it.items }.filter { it.id in uiState.selectedIds }
                fileOperations.start(FileOperationType.Copy, selected)
                viewModel.onSelectionClose()
            },
            onMove = {
                val selected = uiState.days.flatMap { it.items }.filter { it.id in uiState.selectedIds }
                fileOperations.start(FileOperationType.Move, selected)
                viewModel.onSelectionClose()
            },
            // 回転: 選んだ写真を渡して、回転の流れ(選択肢 → 回転 → 保存)を始める
            onRotate = {
                val selected = uiState.days.flatMap { it.items }.filter { it.id in uiState.selectedIds }
                rotation.start(selected)
                viewModel.onSelectionClose()
            },
            // リサイズ: 選んだ写真を渡して、リサイズの流れ(設定 → 保存)を始める
            onResize = {
                val selected = uiState.days.flatMap { it.items }.filter { it.id in uiState.selectedIds }
                resize.start(selected)
                viewModel.onSelectionClose()
            },
            onRename = {
                renameTarget = uiState.days.flatMap { it.items }.firstOrNull { it.id in uiState.selectedIds }
            },
            onOpenWith = {
                uiState.days.flatMap { it.items }.firstOrNull { it.id in uiState.selectedIds }
                    ?.let { openWithOtherApp(context, it) }
            },
            onDelete = {
                if (uiState.selectedIds.isNotEmpty()) {
                    // ゴミ箱へ移動で、確認がオフなら、すぐ実行。「そのまま削除」は、常に確認する
                    if (toTrash && !settings.confirmDelete) viewModel.deleteSelected(permanent = false)
                    else showDeleteDialog = true
                }
            },
            onShare = {
                // 表示中の並び順で、選んだ写真・動画を共有する
                val selected = uiState.days.flatMap { it.items }.filter { it.id in uiState.selectedIds }
                shareMediaList(context, selected)
            },
        )
    }

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

@Composable
private fun JustifiedPhotoList(
    days: List<DayGroup>,
    sortOrder: SortOrder,
    onSortSelected: (SortOrder) -> Unit,
    onSearchClick: () -> Unit,
    isSelecting: Boolean,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
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
) {
    // 写真をタップしたら、いま表示している一覧(並び順も含む)を渡して、ビューアーを開く
    // 選択モード中のタップは、選ぶ/外す
    val viewer = LocalViewerController.current
    val onMediaClick: (MediaItem) -> Unit = { media ->
        if (isSelecting) {
            onToggleSelect(media.id)
        } else {
            viewer.open(days.flatMap { it.items }, media.id)
        }
    }
    // 長押しで選択モードを始める(選択モード中の長押しは、何もしない)
    val onMediaLongClick: (MediaItem) -> Unit = { media ->
        if (!isSelecting) onLongPress(media.id)
    }

    // ヘッダー(縦・横のどちらのリストでも共通)。リストの上に重ねて表示する。引数は、年月の文字
    val header: @Composable BoxScope.(String) -> Unit = { title ->
        if (isSelecting) {
            // 選択中: ← / 「3/329」 / 削除 / 共有 / ︙(すべて選択)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(AllPhotosHeaderHeight)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                SelectionBar(
                    selectedCount = selectedIds.size,
                    totalCount = days.sumOf { it.items.size },
                    onClose = onSelectionClose,
                ) {
                    BarIconButton(Icons.Default.Delete, "削除", onDelete)
                    BarIconButton(Icons.Default.Share, "共有", onShare)
                    SelectionMoreMenu(
                        allSelected = selectedIds.isNotEmpty() &&
                                selectedIds.size == days.sumOf { it.items.size },
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
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                onRotate()
                                dismiss()
                            },
                        )
                        // 名前の変更・ほかのアプリで開くは、1枚だけ選んでいるときだけ押せる
                        DropdownMenuItem(
                            text = { Text("名前の変更") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            enabled = selectedIds.size == 1,
                            onClick = {
                                onRename()
                                dismiss()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("コピー") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                onCopy()
                                dismiss()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("移動") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                onMove()
                                dismiss()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("リサイズ") },
                            leadingIcon = { Icon(Icons.Default.PhotoSizeSelectLarge, contentDescription = null) },
                            enabled = selectedIds.isNotEmpty(),
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
                            enabled = selectedIds.size == 1,
                            onClick = {
                                onOpenWith()
                                dismiss()
                            },
                        )
                    }
                }
            }
        } else {
            AllPhotosHeader(
                title = title,
                sortOrder = sortOrder,
                onSortSelected = onSortSelected,
                onSearchClick = onSearchClick,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 設定: スクロールの向き(縦/横)
        val horizontal = LocalAppSettings.current.scrollDirection == ScrollDirection.Horizontal

        if (horizontal) {
            HorizontalPhotoList(
                days = days,
                sortOrder = sortOrder,
                maxHeightDp = maxHeight.value,
                isSelecting = isSelecting,
                selectedIds = selectedIds,
                onMediaClick = onMediaClick,
                onMediaLongClick = onMediaLongClick,
                header = header,
            )
        } else {
            VerticalPhotoList(
                days = days,
                sortOrder = sortOrder,
                maxWidthDp = maxWidth.value,
                isSelecting = isSelecting,
                selectedIds = selectedIds,
                onMediaClick = onMediaClick,
                onMediaLongClick = onMediaLongClick,
                header = header,
            )
        }
    }
}

/** 縦スクロール(これまでと同じ)。 */
@Composable
private fun BoxScope.VerticalPhotoList(
    days: List<DayGroup>,
    sortOrder: SortOrder,
    maxWidthDp: Float,
    isSelecting: Boolean,
    selectedIds: Set<Long>,
    onMediaClick: (MediaItem) -> Unit,
    onMediaLongClick: (MediaItem) -> Unit,
    header: @Composable BoxScope.(String) -> Unit,
) {
    val availableWidthDp = maxWidthDp - ScreenStartPadding.value - ScreenEndPadding.value

    // 設定: 表示形式(写真によって変化する/正方形に固定)と、正方形のときの列数
    val settings = LocalAppSettings.current
    val layout = settings.allPhotosLayout
    val columns = settings.allPhotosColumns

    // 画面の幅や、並び順、表示形式が変わったときだけ、行を計算し直す
    val listItems = remember(days, availableWidthDp, layout, columns) {
        when (layout) {
            AllPhotosLayout.Justified -> buildJustifiedItems(
                days = days,
                availableWidthDp = availableWidthDp,
                targetRowHeightDp = TargetRowHeightDp,
                spacingDp = ItemSpacingDp,
            )
            AllPhotosLayout.Square -> buildSquareItems(
                days = days,
                availableWidthDp = availableWidthDp,
                columns = columns,
                spacingDp = ItemSpacingDp,
            )
        }
    }

    val listState = rememberLazyListState()

    // 月が変わる位置(その月の先頭項目の番号)。スクロールバーの「・」に使う
    val monthMarkers = remember(listItems) {
        buildList {
            var previous: Pair<Int, Int>? = null
            listItems.forEachIndexed { index, item ->
                val yearMonth = item.date.year to item.date.monthValue
                if (yearMonth != previous) {
                    add(index)
                    previous = yearMonth
                }
            }
        }
    }

    // 並び順を変えたら、先頭に戻す
    LaunchedEffect(sortOrder) { listState.scrollToItem(0) }

    // 一番上に見えている項目の日付から、「2026年7月」を作る
    val title by remember(listItems) {
        derivedStateOf {
            listItems.getOrNull(listState.firstVisibleItemIndex)?.date
                ?.let { "${it.year}年${it.monthValue}月" }
                ?: ""
        }
    }

    // 下に引っ張って更新。更新の表示は、ヘッダーに隠れないよう、ヘッダーの下に出す
    RefreshBox(
        modifier = Modifier.fillMaxSize(),
        indicatorTopPadding = AllPhotosHeaderHeight,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ScreenStartPadding,
                end = ScreenEndPadding,
                // ヘッダーの高さ分だけ、最初の項目を下げる(スクロールするとヘッダーの下に潜る)
                top = AllPhotosHeaderHeight,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ItemSpacingDp.dp),
        ) {
            items(
                items = listItems,
                key = { it.key },
                contentType = { if (it is AllListItem.HeaderItem) 0 else 1 },
            ) { item ->
                when (item) {
                    is AllListItem.HeaderItem -> DateHeader(item.date)
                    is AllListItem.RowItem -> PhotoRow(
                        row = item,
                        isSelecting = isSelecting,
                        selectedIds = selectedIds,
                        onMediaClick = onMediaClick,
                        onMediaLongClick = onMediaLongClick,
                    )
                }
            }
        }
    }

    // 右端のスクロールバー(ヘッダーの下から、画面の下まで)
    LazyListFastScrollbar(
        state = listState,
        itemCount = listItems.size,
        markerIndices = monthMarkers,
        labelForIndex = { index ->
            listItems.getOrNull(index)?.date?.let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" } ?: ""
        },
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(top = AllPhotosHeaderHeight, bottom = 8.dp, end = 2.dp),
    )

    // ヘッダーは最後に置いて、リストの上に重ねる
    header(title)
}

/**
 * 横スクロール。日ごとに、上に「日付のピル + 右へ伸びる横線」の帯を付け、その下に写真を並べる。
 * 写真は、縦に積んで1列にし、列を右へ並べていく。
 * 引っ張って更新は、横スクロールでは使わない。
 */
@Composable
private fun BoxScope.HorizontalPhotoList(
    days: List<DayGroup>,
    sortOrder: SortOrder,
    maxHeightDp: Float,
    isSelecting: Boolean,
    selectedIds: Set<Long>,
    onMediaClick: (MediaItem) -> Unit,
    onMediaLongClick: (MediaItem) -> Unit,
    header: @Composable BoxScope.(String) -> Unit,
) {
    // 写真を積める高さ = 画面の高さ - ヘッダー - 日付の帯 - 下端のスクロールバー用の余白
    val availableHeightDp = maxHeightDp - AllPhotosHeaderHeight.value -
            StripHeight.value - StripGap.value - HorizontalBottomReserve.value

    // 設定: 表示形式(写真によって変化する/正方形に固定)と、正方形のときの行数
    val settings = LocalAppSettings.current
    val layout = settings.allPhotosLayout
    val rows = settings.allPhotosColumns

    // 画面の高さや、並び順、表示形式が変わったときだけ、列を計算し直す
    val columns = remember(days, availableHeightDp, layout, rows) {
        when (layout) {
            AllPhotosLayout.Justified -> buildJustifiedColumns(
                days = days,
                availableHeightDp = availableHeightDp,
                targetColumnWidthDp = TargetColumnWidthDp,
                spacingDp = ItemSpacingDp,
            )
            AllPhotosLayout.Square -> buildSquareColumns(
                days = days,
                availableHeightDp = availableHeightDp,
                rows = rows,
                spacingDp = ItemSpacingDp,
            )
        }
    }

    val listState = rememberLazyListState()

    // 月が変わる位置(その月の先頭の列の番号)。スクロールバーの「・」に使う
    val monthMarkers = remember(columns) {
        buildList {
            var previous: Pair<Int, Int>? = null
            columns.forEachIndexed { index, column ->
                val yearMonth = column.date.year to column.date.monthValue
                if (yearMonth != previous) {
                    add(index)
                    previous = yearMonth
                }
            }
        }
    }

    // 並び順を変えたら、先頭に戻す
    LaunchedEffect(sortOrder) { listState.scrollToItem(0) }

    // 一番左に見えている列の日付から、「2026年7月」を作る
    val title by remember(columns) {
        derivedStateOf {
            columns.getOrNull(listState.firstVisibleItemIndex)?.date
                ?.let { "${it.year}年${it.monthValue}月" }
                ?: ""
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ScreenStartPadding,
            end = ScreenStartPadding,
            // ヘッダーの高さ分だけ、リストを下げる。下は、スクロールバーの分を空ける
            top = AllPhotosHeaderHeight,
            bottom = HorizontalBottomReserve,
        ),
        // 列の間の余白は、各項目の中に持たせる(日付の横線を、列をまたいで途切れさせないため)
    ) {
        items(
            items = columns,
            key = { it.key },
        ) { column ->
            DayColumnItem(
                column = column,
                isSelecting = isSelecting,
                selectedIds = selectedIds,
                onMediaClick = onMediaClick,
                onMediaLongClick = onMediaLongClick,
            )
        }
    }

    // 下端のスクロールバー
    LazyListHorizontalFastScrollbar(
        state = listState,
        itemCount = columns.size,
        markerIndices = monthMarkers,
        labelForIndex = { index ->
            columns.getOrNull(index)?.date?.let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" } ?: ""
        },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = ScreenStartPadding, end = ScreenStartPadding, bottom = 2.dp),
    )

    // ヘッダーは最後に置いて、リストの上に重ねる
    header(title)
}

private enum class ColumnSlot { Pill, Line, Photos }

/**
 * 横スクロールの1項目 = 写真の1列 + その上の日付の帯。
 *
 * - 日ごとの最初の列には、日付のピルを付ける。
 * - 横線は、ピルの右(最初の列)または列の左端から、列の右端まで引く。日の途中の列は、
 *   次の列との間の余白まで伸ばして、線を途切れさせない。日の最後の列では、列の右端で止める。
 * - ピルが列の幅より広いときは、項目の幅をピルに合わせて広げる(写真は左詰め)。
 */
@Composable
private fun DayColumnItem(
    column: DayColumn,
    isSelecting: Boolean,
    selectedIds: Set<Long>,
    onMediaClick: (MediaItem) -> Unit,
    onMediaLongClick: (MediaItem) -> Unit,
) {
    val trailing = if (column.isLastOfDay) DayEndGap else ItemSpacingDp.dp

    Layout(
        content = {
            if (column.isFirstOfDay) {
                GlassSurface(
                    modifier = Modifier.layoutId(ColumnSlot.Pill),
                    shape = CircleShape,
                ) {
                    Text(
                        text = formatDate(column.date),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.layoutId(ColumnSlot.Line))
            Column(
                modifier = Modifier.layoutId(ColumnSlot.Photos),
                verticalArrangement = Arrangement.spacedBy(ItemSpacingDp.dp),
            ) {
                column.photos.forEach { photo ->
                    PhotoCell(
                        media = photo.media,
                        widthDp = column.widthDp,
                        heightDp = photo.heightDp,
                        isSelecting = isSelecting,
                        selected = photo.media.id in selectedIds,
                        onClick = onMediaClick,
                        onLongClick = onMediaLongClick,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val stripPx = StripHeight.roundToPx()
        val gapPx = StripGap.roundToPx()
        val trailingPx = trailing.roundToPx()
        val photosWidthPx = column.widthDp.dp.roundToPx()
        val pillGapPx = PillLineGap.roundToPx()
        val minLinePx = MinLineLength.roundToPx()

        val pill = measurables.firstOrNull { it.layoutId == ColumnSlot.Pill }
            ?.measure(Constraints(0, Constraints.Infinity, 0, stripPx))
        val photosMeasurable = measurables.first { it.layoutId == ColumnSlot.Photos }
        val lineMeasurable = measurables.first { it.layoutId == ColumnSlot.Line }

        // 項目の中身の幅(余白を除く): 写真の列の幅か、ピル + 横線の最低限の長さの、広いほう
        val pillNeedPx = if (pill != null) pill.width + pillGapPx + minLinePx else 0
        val contentWidthPx = maxOf(photosWidthPx, pillNeedPx)

        val photos = photosMeasurable.measure(
            Constraints(
                minWidth = photosWidthPx,
                maxWidth = photosWidthPx,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            ),
        )

        // 横線の範囲(中身の左端を0とする)
        val lineStartPx = if (pill != null) pill.width + pillGapPx else 0
        val lineEndPx = if (column.isLastOfDay) contentWidthPx else contentWidthPx + trailingPx
        val lineWidthPx = (lineEndPx - lineStartPx).coerceAtLeast(0)
        val line = lineMeasurable.measure(
            Constraints(
                minWidth = lineWidthPx,
                maxWidth = lineWidthPx,
                minHeight = 0,
                maxHeight = stripPx,
            ),
        )

        val totalWidth = contentWidthPx + trailingPx
        val totalHeight = stripPx + gapPx + photos.height

        layout(totalWidth, totalHeight) {
            pill?.placeRelative(0, (stripPx - pill.height) / 2)
            line.placeRelative(lineStartPx, (stripPx - line.height) / 2)
            photos.placeRelative(0, stripPx + gapPx)
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 日付の文字にも、検索バーと同じ半透明の背景をつける
        GlassSurface(shape = CircleShape) {
            Text(
                text = formatDate(date),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PhotoRow(
    row: AllListItem.RowItem,
    isSelecting: Boolean,
    selectedIds: Set<Long>,
    onMediaClick: (MediaItem) -> Unit,
    onMediaLongClick: (MediaItem) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ItemSpacingDp.dp)) {
        row.photos.forEach { photo ->
            PhotoCell(
                media = photo.media,
                widthDp = photo.widthDp,
                heightDp = row.heightDp,
                isSelecting = isSelecting,
                selected = photo.media.id in selectedIds,
                onClick = onMediaClick,
                onLongClick = onMediaLongClick,
            )
        }
    }
}

/** 写真1枚(縦スクロールの行・横スクロールの列で共通)。選択中の枠・チェック欄・動画のバッジも含む。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoCell(
    media: MediaItem,
    widthDp: Float,
    heightDp: Float,
    isSelecting: Boolean,
    selected: Boolean,
    onClick: (MediaItem) -> Unit,
    onLongClick: (MediaItem) -> Unit,
) {
    // 選択の色。変えたいときは、フォルダ内画面(FolderDetailScreen)の accent と合わせて直す
    val accent = MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            // サイズ指定より前に置く(shared element transition の目印)
            .mediaSharedSource(media.id)
            .width(widthDp.dp)
            .height(heightDp.dp)
            .clip(RoundedCornerShape(PhotoCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(
                onClick = { onClick(media) },
                onLongClick = { onLongClick(media) },
            ),
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

        // 選択中: 薄い色と赤い枠(親が角丸で切り抜いているので、角もそろう)
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.18f))
                    .border(3.dp, accent, RoundedCornerShape(PhotoCornerRadius)),
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

/** 今年なら「7月31日」、それ以外の年なら「2025年7月31日」。 */
private fun formatDate(date: LocalDate): String {
    val thisYear = LocalDate.now().year
    return if (date.year == thisYear) {
        "${date.monthValue}月${date.dayOfMonth}日"
    } else {
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
}
