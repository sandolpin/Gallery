package com.sandolpin.gallery.ui.fileop

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// 進行状況バーの色(画像に合わせた固定色)
private val ProgressBlue = Color(0xFFA5E1FF)
private val ProgressTrack = Color(0xFFDDDDDD)

/** 「1/50」の、はじめの1件が終わる前でも、バーが少し見えるようにする最小の長さ(割合)。 */
private const val MinVisibleFraction = 0.04f

/**
 * ファイル操作の画面(GalleryRootに置く)。
 * フォルダを選んでいる間は、全画面のフォルダ選択を、実行中は、進行状況ダイアログを表示する。
 * 結果のメッセージも、ここでトーストにする。
 */
@Composable
fun FileOperationHost(controller: FileOperationController) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(controller) {
        controller.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    when (val current = state) {
        is FileOperationState.Picking -> FolderPickerScreen(
            type = current.type,
            items = current.items,
            onPick = controller::execute,
            onCancel = controller::cancelPicking,
        )
        is FileOperationState.Running -> OperationProgressDialog(
            title = current.type.progressTitle,
            done = current.done,
            total = current.total,
            remainingSeconds = current.remainingSeconds,
            onCancel = controller::cancelRunning,
        )
        FileOperationState.Idle -> Unit
    }
}

/**
 * 進行状況ダイアログ: 「移動中…」「1/50 あと7秒」、バー、「✕ キャンセル」。
 * コピー・移動・回転で共通に使う。
 * 外側のタップや戻る操作では閉じない(「キャンセル」ボタンだけで中止する)。
 */
@Composable
internal fun OperationProgressDialog(
    title: String,
    done: Int,
    total: Int,
    remainingSeconds: Int?,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "$done/$total ${remainingText(remainingSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                ProgressBar(fraction = done.toFloat() / total.coerceAtLeast(1))
                Spacer(Modifier.height(28.dp))

                // キャンセルボタン(枠線つきのカプセル)
                Surface(
                    onClick = onCancel,
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "キャンセル",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/** 「あと7秒」「あと2分」。1件終わるまでは、「計算中…」。 */
private fun remainingText(seconds: Int?): String = when {
    seconds == null -> "計算中…"
    seconds >= 60 -> "あと${(seconds + 59) / 60}分"
    else -> "あと${seconds.coerceAtLeast(1)}秒"
}

/** 水色の進んだ部分と、灰色の残りの部分を、少し隙間を空けて並べたバー。 */
@Composable
private fun ProgressBar(fraction: Float) {
    val shown = fraction.coerceIn(MinVisibleFraction, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(shown)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(ProgressBlue),
        )
        if (shown < 1f) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .weight(1f - shown)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(ProgressTrack),
            )
        }
    }
}