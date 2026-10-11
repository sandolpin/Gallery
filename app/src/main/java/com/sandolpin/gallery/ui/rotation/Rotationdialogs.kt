package com.sandolpin.gallery.ui.rotation

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.ui.components.rememberMediaImageRequest
import com.sandolpin.gallery.ui.fileop.OperationProgressDialog

// ダイアログの色(画像に合わせた固定色)
private val ActionBlue = Color(0xFF00A6FF)
private val DisabledGray = Color(0xFFBDBDBD)
private val RoundButtonGray = Color(0xFFCCCCCC)
private val IconDark = Color(0xFF212121)

/** 回転ダイアログのプレビュー(正方形)の大きさ。縦長・横長の写真が、回転しても、枠からはみ出さない。 */
private val PreviewSize = 170.dp

/**
 * 回転の画面(GalleryRootに置く)。段階に応じて、次のダイアログを出す。
 * 選択肢(複数枚のとき)→ 回転 → 保存(上書き/コピー)→ 進行状況。結果のメッセージも、ここでトーストにする。
 */
@Composable
fun RotationHost(controller: RotationController) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(controller) {
        controller.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    when (val current = state) {
        is RotationState.ChoosingScope -> RotationScopeDialog(
            count = current.items.size,
            unsupported = current.unsupported,
            onIndividually = { controller.chooseScope(applyToAll = false) },
            onAll = { controller.chooseScope(applyToAll = true) },
            onCancel = controller::cancel,
        )
        is RotationState.Editing -> RotationEditDialog(
            session = current.session,
            onSave = controller::save,
            onSkip = controller::skip,
            onCancel = controller::cancel,
        )
        is RotationState.ChoosingSave -> RotationSaveDialog(
            session = current.session,
            degrees = current.degrees,
            onChoose = controller::chooseSave,
            onCancel = controller::cancel,
        )
        is RotationState.Running -> OperationProgressDialog(
            title = "回転中…",
            done = current.done,
            total = current.total,
            remainingSeconds = current.remainingSeconds,
            onCancel = controller::cancelRunning,
        )
        RotationState.Idle -> Unit
    }
}

/** 回転のダイアログに共通の枠(幅を広めにして、角丸の白いカード)。 */
@Composable
internal fun RotationDialogFrame(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
    }
}

/** 複数枚を選んだとき: 「1枚ずつ回転」か「すべてに同じ角度で回転」かを選ぶ。 */
@Composable
private fun RotationScopeDialog(
    count: Int,
    unsupported: Int,
    onIndividually: () -> Unit,
    onAll: () -> Unit,
    onCancel: () -> Unit,
) {
    RotationDialogFrame(onDismiss = onCancel) {
        Text(
            text = "回転",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "${count}枚の写真を回転します。",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        if (unsupported > 0) {
            Text(
                text = "${unsupported}枚は非対応のため、回転されません。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        BluePillButton(text = "1枚ずつ回転", onClick = onIndividually)
        Spacer(Modifier.height(8.dp))
        BluePillButton(text = "すべてに同じ角度で回転", onClick = onAll)
        Spacer(Modifier.height(10.dp))
        CancelPillButton(onClick = onCancel)
    }
}

/**
 * 回転ダイアログ: 左右の丸ボタンで90°ずつ回し、プレビューと角度(0〜270°)で確認して、「保存」。
 * 角度が0°のときは、「保存」を押せない。
 * 1枚ずつのときは、「3/5」のように何枚目かを出し、「スキップ」で、回転せずに次へ進める。
 */
@Composable
private fun RotationEditDialog(
    session: RotationSession,
    onSave: (degrees: Int) -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    val item = session.current
    val individually = !session.applyToAll && session.items.size > 1

    // 写真が変わったら(1枚ずつで、次の写真へ進んだら)、0°から始める
    var rotation by remember(session.index) { mutableIntStateOf(0) }
    val normalized = ((rotation % 360) + 360) % 360
    val animatedRotation by animateFloatAsState(
        targetValue = rotation.toFloat(),
        animationSpec = tween(durationMillis = 250),
        label = "rotation",
    )

    RotationDialogFrame(onDismiss = onCancel) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "回転",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (individually) {
                Text(
                    text = "${session.index + 1}/${session.items.size}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(Icons.Default.RotateLeft, "左に90°回転") { rotation -= 90 }
            PreviewImage(item = item, rotationDegrees = animatedRotation)
            RoundIconButton(Icons.Default.RotateRight, "右に90°回転") { rotation += 90 }
        }

        Text(
            text = "${normalized}°",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (session.applyToAll) {
            Text(
                text = "${session.items.size}枚をまとめて回転(プレビューは1枚目)",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (session.unsupported > 0 && session.index == 0) {
            Text(
                text = "${session.unsupported}枚は非対応のため、回転されません",
                style = MaterialTheme.typography.labelSmall,
            )
        }

        if (individually) {
            TextButton(onClick = onSkip) { Text("スキップ(この写真は回転しない)") }
        } else {
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CancelPillButton(onClick = onCancel, modifier = Modifier.weight(1f))
            BluePillButton(
                text = "保存",
                icon = Icons.Default.Save,
                enabled = normalized != 0,
                onClick = { onSave(normalized) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 保存ダイアログ: 回転後の向きのプレビューと角度を見せて、「ファイルを上書き」か「コピーを保存」を選ぶ。 */
@Composable
private fun RotationSaveDialog(
    session: RotationSession,
    degrees: Int,
    onChoose: (overwrite: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    RotationDialogFrame(onDismiss = onCancel) {
        Text(
            text = "保存",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        PreviewImage(item = session.current, rotationDegrees = degrees.toFloat())
        Text(
            text = "${degrees}°",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (session.items.size > 1) {
            Text(
                text = "この選択は、残りの写真にも使います",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(12.dp))
        BluePillButton(text = "ファイルを上書き", icon = Icons.Default.Save, onClick = { onChoose(true) })
        Spacer(Modifier.height(8.dp))
        BluePillButton(text = "コピーを保存", icon = Icons.Default.Save, onClick = { onChoose(false) })
        Spacer(Modifier.height(10.dp))
        CancelPillButton(onClick = onCancel)
    }
}

/** 写真のプレビュー(正方形の枠の中に収めて、[rotationDegrees] だけ回す)。 */
@Composable
private fun PreviewImage(item: MediaItem, rotationDegrees: Float) {
    Box(
        modifier = Modifier.size(PreviewSize),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = rememberMediaImageRequest(item),
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotationDegrees },
        )
    }
}

/** 灰色の丸いアイコンボタン(左右の回転)。 */
@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = RoundButtonGray,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = IconDark)
        }
    }
}

/** 青い、塗りつぶしのカプセル型ボタン(白い太字)。[enabled] が false のときは、灰色で押せない。 */
@Composable
internal fun BluePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = CircleShape,
        color = if (enabled) ActionBlue else DisabledGray,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 枠線つきの「✕ キャンセル」ボタン。 */
@Composable
internal fun CancelPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                text = "キャンセル",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}