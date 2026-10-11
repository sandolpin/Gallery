package com.sandolpin.gallery.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.model.RenameResult
import kotlinx.coroutines.launch

/** ファイル名に使えない文字。 */
private const val InvalidChars = "/\\:*?\"<>|"

/** 名前(拡張子を除く)が使えるかを調べる。問題があれば、そのメッセージ。問題なければ null。 */
internal fun validateFileName(name: String): String? = when {
    name.isBlank() -> "名前を入力してください"
    name.any { it in InvalidChars } -> "使えない文字があります( / \\ : * ? \" < > | )"
    name.startsWith(".") -> "先頭に「.」は使えません"
    name.toByteArray().size > 200 -> "名前が長すぎます"
    else -> null
}

/**
 * 名前の変更ダイアログ。拡張子(.jpg など)は変えられず、入力欄の右に固定で表示する。
 * 最初は、名前の全体が選ばれた状態で、キーボードが出る。
 *
 * @param onRename 実際の変更処理。引数は、拡張子を含む新しい名前。
 * @param onRenamed 変更できたとき(引数は、拡張子を含む新しい名前)。ダイアログは、呼び出し側で閉じる。
 */
@Composable
fun RenameDialog(
    item: MediaItem,
    onRename: suspend (newDisplayName: String) -> RenameResult,
    onDismiss: () -> Unit,
    onRenamed: (newDisplayName: String) -> Unit,
) {
    val dot = item.displayName.lastIndexOf('.')
    val extension = if (dot > 0) item.displayName.substring(dot) else ""
    val baseName = if (dot > 0) item.displayName.substring(0, dot) else item.displayName

    var text by remember(item.id) {
        mutableStateOf(TextFieldValue(baseName, TextRange(0, baseName.length)))
    }
    var errorMessage by remember(item.id) { mutableStateOf<String?>(null) }
    var isBusy by remember(item.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun submit() {
        if (isBusy) return
        val newBase = text.text.trim()
        val problem = validateFileName(newBase)
        if (problem != null) {
            errorMessage = problem
            return
        }
        val newName = newBase + extension
        if (newName == item.displayName) {
            // 変わっていないので、何もせず閉じる
            onDismiss()
            return
        }
        isBusy = true
        scope.launch {
            val result = onRename(newName)
            isBusy = false
            when (result) {
                RenameResult.Success -> onRenamed(newName)
                RenameResult.AlreadyExists -> errorMessage = "同じ名前のファイルがすでにあります"
                RenameResult.Failed -> errorMessage = "名前を変更できませんでした"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("名前の変更") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    errorMessage = null
                },
                singleLine = true,
                suffix = { if (extension.isNotEmpty()) Text(extension) },
                isError = errorMessage != null,
                supportingText = { errorMessage?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("キャンセル") }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !isBusy) { Text("変更") }
        },
    )
}