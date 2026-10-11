package com.sandolpin.gallery.ui.fileop

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sandolpin.gallery.data.local.mediastore.CopyDestination
import com.sandolpin.gallery.data.model.MediaFolder
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.ui.components.BarIconButton
import com.sandolpin.gallery.ui.components.validateFileName
import java.util.Locale

private val ScreenPadding = 16.dp
private val FolderCornerRadius = 16.dp

/**
 * コピー・移動の、行き先のフォルダを選ぶ画面(全画面)。
 * 上部は選択モードと同じ赤い角丸バー、その下に、フォルダを3列で並べる(ピン留めが先頭)。
 * 選んでいる写真が、すべて、いまあるフォルダを選んだときは、「同じフォルダです」と出して、何もしない。
 */
@Composable
fun FolderPickerScreen(
    type: FileOperationType,
    items: List<MediaItem>,
    onPick: (CopyDestination) -> Unit,
    onCancel: () -> Unit,
    viewModel: FolderPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showNewFolderDialog by remember { mutableStateOf(false) }

    // 戻る操作は、フォルダ選択をやめる(後ろの画面より先に効く)
    BackHandler(onBack = onCancel)

    // 後ろの画面にタッチが抜けないよう、画面全体を覆う
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            PickerBar(
                title = "${type.label}先を選択",
                count = items.size,
                onBack = onCancel,
                onNewFolder = { showNewFolderDialog = true },
            )

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = uiState.folders, key = { it.id }) { folder ->
                        FolderTile(
                            folder = folder,
                            isPinned = folder.id in uiState.pinnedIds,
                            onClick = {
                                if (items.all { it.folderId == folder.id }) {
                                    Toast.makeText(context, "同じフォルダです", Toast.LENGTH_SHORT).show()
                                } else {
                                    onPick(CopyDestination.ExistingFolder(folder.id))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onCreate = { name ->
                showNewFolderDialog = false
                onPick(CopyDestination.NewFolder(name))
            },
        )
    }
}

/** 上部の赤い角丸バー: ← / 「コピー先を選択」 / 「3件」 / 新しいフォルダ。 */
@Composable
private fun PickerBar(
    title: String,
    count: Int,
    onBack: () -> Unit,
    onNewFolder: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 8.dp)
            .height(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BarIconButton(Icons.AutoMirrored.Filled.ArrowBack, "戻る", onBack)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            Text(
                text = String.format(Locale.US, "%,d件", count),
                style = MaterialTheme.typography.labelLarge,
            )
            BarIconButton(Icons.Default.CreateNewFolder, "新しいフォルダ", onNewFolder)
        }
    }
}

/** フォルダ1件: 正方形のカバー写真(ピン留めには、ピンのマーク)と、その下に「フォルダ名 (枚数)」。 */
@Composable
private fun FolderTile(
    folder: MediaFolder,
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(FolderCornerRadius))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(FolderCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = folder.coverUri,
                contentDescription = folder.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isPinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "ピン留め",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            text = "${folder.name} (${String.format(Locale.US, "%,d", folder.itemCount)})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
        )
    }
}

/** 新しいフォルダの名前を入力するダイアログ。内部ストレージの Pictures の直下に作る。 */
@Composable
private fun NewFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun submit() {
        val trimmed = name.trim()
        val problem = validateFileName(trimmed)
        if (problem != null) error = problem else onCreate(trimmed)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新しいフォルダ") },
        text = {
            Column {
                Text(
                    text = "Pictures フォルダの中に作成します。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    singleLine = true,
                    label = { Text("フォルダ名") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        confirmButton = { TextButton(onClick = { submit() }) { Text("作成") } },
    )
}