package com.sandolpin.gallery.ui.trash

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sandolpin.gallery.ui.components.BarIconButton
import com.sandolpin.gallery.ui.components.GlassHeaderHeight
import com.sandolpin.gallery.ui.components.GlassIconButton
import com.sandolpin.gallery.ui.components.GlassSurface
import com.sandolpin.gallery.ui.components.SelectionBar
import com.sandolpin.gallery.ui.components.SelectionMoreMenu
import com.sandolpin.gallery.ui.folder.MediaGrid

/** ゴミ箱の列数。設定画面(段階7)で変えられるようにする想定。 */
private const val TrashColumns = 3

/** 確認ダイアログの種類。 */
private enum class TrashConfirm { DeleteSelected, EmptyTrash }

/**
 * ゴミ箱画面。フォルダ内画面とほぼ同じ見た目のグリッドで、
 * 写真をタップ(または長押し)して選ぶと、「復元」「完全に削除」ができる。
 */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<TrashConfirm?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // 選択中に戻る操作をしたら、画面を閉じずに、選択モードだけを終わる
    BackHandler(enabled = uiState.isSelecting) { viewModel.onSelectionClose() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                Box(Modifier.align(Alignment.Center)) { CircularProgressIndicator() }
            }
            uiState.items.isEmpty() -> {
                Text(
                    text = "ゴミ箱は空です",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                MediaGrid(
                    items = uiState.items,
                    columns = TrashColumns,
                    resetKey = Unit,
                    isSelecting = uiState.isSelecting,
                    selectedIds = uiState.selectedIds,
                    onMediaClick = { viewModel.onItemClick(it.id) },
                    onMediaLongClick = { if (!uiState.isSelecting) viewModel.onLongPress(it.id) },
                )
            }
        }

        // ヘッダーは最後に置いて、グリッドの上に重ねる
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(GlassHeaderHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.isSelecting) {
                SelectionBar(
                    selectedCount = uiState.selectedIds.size,
                    totalCount = uiState.items.size,
                    onClose = viewModel::onSelectionClose,
                ) {
                    BarIconButton(Icons.Default.Restore, "復元") {
                        if (uiState.selectedIds.isNotEmpty()) viewModel.restoreSelected()
                    }
                    BarIconButton(Icons.Default.DeleteForever, "完全に削除") {
                        if (uiState.selectedIds.isNotEmpty()) confirm = TrashConfirm.DeleteSelected
                    }
                    SelectionMoreMenu(
                        allSelected = uiState.items.isNotEmpty() &&
                                uiState.selectedIds.size == uiState.items.size,
                        onSelectAllToggle = viewModel::onSelectAllToggle,
                    )
                }
            } else {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "戻る", onBack)

                GlassSurface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = CircleShape,
                ) {
                    Text(
                        text = if (uiState.isLoading) "ゴミ箱" else "ゴミ箱 (${uiState.items.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(horizontal = 16.dp),
                    )
                }

                TrashMoreMenu(
                    canEmpty = uiState.items.isNotEmpty(),
                    onEmptyTrash = { confirm = TrashConfirm.EmptyTrash },
                )
            }
        }

        when (confirm) {
            TrashConfirm.DeleteSelected -> ConfirmDialog(
                title = "完全に削除",
                message = "${uiState.selectedIds.size}項目を完全に削除します。元に戻せません。",
                onDismiss = { confirm = null },
                onConfirm = {
                    confirm = null
                    viewModel.deleteSelected()
                },
            )
            TrashConfirm.EmptyTrash -> ConfirmDialog(
                title = "ゴミ箱を空にする",
                message = "${uiState.items.size}項目をすべて完全に削除します。元に戻せません。",
                onDismiss = { confirm = null },
                onConfirm = {
                    confirm = null
                    viewModel.emptyTrash()
                },
            )
            null -> Unit
        }
    }
}

/** ヘッダーの︙メニュー: ゴミ箱を空にする。 */
@Composable
private fun TrashMoreMenu(
    canEmpty: Boolean,
    onEmptyTrash: () -> Unit,
) {
    Box {
        var expanded by remember { mutableStateOf(false) }

        GlassIconButton(Icons.Default.MoreVert, "その他", onClick = { expanded = true })

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("ゴミ箱を空にする") },
                leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                enabled = canEmpty,
                onClick = {
                    expanded = false
                    onEmptyTrash()
                },
            )
        }
    }
}

/** 取り消せない操作の前に出す、シンプルな確認ダイアログ。 */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("削除", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}