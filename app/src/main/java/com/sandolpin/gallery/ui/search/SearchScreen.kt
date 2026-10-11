package com.sandolpin.gallery.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.ui.components.GlassHeaderHeight
import com.sandolpin.gallery.ui.components.GlassSortButton
import com.sandolpin.gallery.ui.components.GlassSurface
import com.sandolpin.gallery.ui.components.rememberSearchHint
import com.sandolpin.gallery.ui.folder.MediaCell
import com.sandolpin.gallery.ui.viewer.LocalViewerController
import kotlin.math.roundToInt

private val GridHorizontalPadding = 16.dp
private val GridSpacing = 12.dp

/**
 * 検索画面。上部に検索バーと並び替えボタン、その下に、結果を見出しつきのセクションで並べる。
 * 上から「写真から検出」(信頼度のバーつき)、「写真内の文字」、「ファイル名」。結果がないセクションは出さない。
 * 結果をタップすると、そのセクションの結果全体を、ビューアーで開く。
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()
    val viewer = LocalViewerController.current
    val focusManager = LocalFocusManager.current

    // 結果をタップしたら、キーボードを閉じてから、ビューアーを開く
    val openViewer: (List<MediaItem>, MediaItem) -> Unit = { list, media ->
        focusManager.clearFocus()
        viewer.open(list, media.id)
    }

    val gridState = rememberLazyGridState()
    // 検索語や並び順が変わったら、先頭に戻す
    LaunchedEffect(uiState.sort, query) { gridState.scrollToItem(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                Box(Modifier.align(Alignment.Center)) { CircularProgressIndicator() }
            }
            !uiState.hasQuery -> {
                MessageWithNote(
                    message = if (uiState.fuzzyEnabled) {
                        "写真の中身(「楽器」「食べ物」など)・写真内の文字・ファイル名で検索できます"
                    } else {
                        "ファイル名で検索できます"
                    },
                    note = uiState.analysisNote,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            !uiState.hasAnyResult -> {
                MessageWithNote(
                    message = "「${query.trim()}」に一致する写真・動画がありません",
                    note = uiState.analysisNote,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
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
                    // 分析が終わっていないときのお知らせ
                    uiState.analysisNote?.let { note ->
                        item(key = "note", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // 写真から検出(信頼度のバーつき)
                    if (uiState.labelResults.isNotEmpty()) {
                        item(key = "header_label", span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(title = "写真から検出")
                        }
                        items(items = uiState.labelResults, key = { "l_${it.item.id}" }) { hit ->
                            LabelHitCell(
                                hit = hit,
                                onClick = { openViewer(uiState.labelResults.map { it.item }, hit.item) },
                            )
                        }
                    }

                    // 写真内の文字
                    if (uiState.textResults.isNotEmpty()) {
                        item(key = "header_text", span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(title = "写真内の文字")
                        }
                        items(items = uiState.textResults, key = { "t_${it.id}" }) { media ->
                            MediaCell(
                                item = media,
                                isSelecting = false,
                                selected = false,
                                onClick = { openViewer(uiState.textResults, media) },
                                onLongClick = { },
                            )
                        }
                    }

                    // ファイル名
                    if (uiState.fileNameResults.isNotEmpty()) {
                        item(key = "header_file_name", span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(title = "ファイル名")
                        }
                        items(items = uiState.fileNameResults, key = { "f_${it.id}" }) { media ->
                            MediaCell(
                                item = media,
                                isSelecting = false,
                                selected = false,
                                onClick = { openViewer(uiState.fileNameResults, media) },
                                onLongClick = { },
                            )
                        }
                    }
                }
            }
        }

        // ヘッダー(検索バーと並び替え)は最後に置いて、結果の上に重ねる
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(GlassHeaderHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchField(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier.weight(1f),
            )
            GlassSortButton(
                options = SearchSort.entries,
                selected = uiState.sort,
                labelOf = { it.label },
                onSelected = viewModel::onSortSelected,
            )
        }
    }
}

/** 画面の中央に出す案内。下に、分析のお知らせ(あれば)を、小さく添える。 */
@Composable
private fun MessageWithNote(message: String, note: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 見出し + 横線(ホームの見出しと同じ形)。 */
@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

/** 「写真から検出」の1枚。サムネイルの下に、「信頼度 87%」と、バーを出す。 */
@Composable
private fun LabelHitCell(hit: LabelHit, onClick: () -> Unit) {
    val confidence = hit.confidence.coerceIn(0f, 1f)
    Column {
        MediaCell(
            item = hit.item,
            isSelecting = false,
            selected = false,
            onClick = onClick,
            onLongClick = { },
        )
        Row(
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "信頼度",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(confidence * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        LinearProgressIndicator(
            progress = { confidence },
            modifier = Modifier
                .padding(top = 3.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/**
 * 検索バー(半透明)。虫眼鏡と入力欄。文字が入っているときだけ、右に「×」(全部消す)を出す。
 * 画面が開いたら、すぐキーボードを出す。
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val hint = rememberSearchHint()
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    GlassSurface(
        modifier = modifier.height(48.dp),
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Default.Search, contentDescription = "検索")
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // 検索ボタンを押したら、キーボードを閉じる(結果は、入力中から出ている)
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "入力を消す",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
