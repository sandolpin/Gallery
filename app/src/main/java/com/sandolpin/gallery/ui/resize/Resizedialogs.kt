package com.sandolpin.gallery.ui.resize

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sandolpin.gallery.data.model.ResizeTarget
import com.sandolpin.gallery.data.model.sizeFor
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.ui.fileop.OperationProgressDialog
import com.sandolpin.gallery.ui.rotation.BluePillButton
import com.sandolpin.gallery.ui.rotation.CancelPillButton
import com.sandolpin.gallery.ui.rotation.RotationDialogFrame
import java.util.Locale
import kotlin.math.roundToInt

// ダイアログの色(画像に合わせた固定色)
private val SliderTrackGray = Color(0xFFAAAAAA)
private val SliderThumbGray = Color(0xFF808080)
private val PresetGray = Color(0xFFD9D9D9)
private val PresetSelected = Color(0xFFA5E1FF)

/** スライダーの範囲(%)。拡大はしないので、上限は100%。 */
private const val MinPercent = 10
private const val MaxPercent = 100
private const val DefaultPercent = 50

/** プリセット。長辺(横幅と縦幅の長いほう)の大きさで決める。縦横比は、写真のまま。 */
private data class Preset(val label: String, val longSidePx: Int)

private val Presets = listOf(
    Preset("4K", 3840),
    Preset("2K", 1920), // フルHD
    Preset("HD", 1280),
)

/**
 * リサイズの画面(GalleryRootに置く)。段階に応じて、次のダイアログを出す。
 * 設定 → 保存(上書き/コピー)→ 進行状況。結果のメッセージも、ここでトーストにする。
 */
@Composable
fun ResizeHost(controller: ResizeController) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(controller) {
        controller.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    when (val current = state) {
        is ResizeState.Editing -> ResizeEditDialog(
            items = current.items,
            unsupported = current.unsupported,
            onSave = controller::save,
            onCancel = controller::cancel,
        )
        is ResizeState.ChoosingSave -> ResizeSaveDialog(
            count = current.items.size,
            onChoose = controller::chooseSave,
            onCancel = controller::cancel,
        )
        is ResizeState.Running -> OperationProgressDialog(
            title = "リサイズ中…",
            done = current.done,
            total = current.total,
            remainingSeconds = current.remainingSeconds,
            onCancel = controller::cancelRunning,
        )
        ResizeState.Idle -> Unit
    }
}

/**
 * リサイズダイアログ: スライダー(10〜100%)かプリセット(4K・2K・HD)で、大きさを決める。
 * - プリセットは、写真の縦横比のまま、長辺を 3840・1920・1280px にする。
 *   写真の長辺以上のものは、押せない(拡大になるため)。
 * - 「リサイズ前/後」は、1枚目の写真の大きさ。サイズ(MB)は、面積の比から出した目安。
 * - 複数枚のときは、設定を全部に同じで適用する(設定より小さい写真は、そのまま)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResizeEditDialog(
    items: List<MediaItem>,
    unsupported: Int,
    onSave: (ResizeTarget) -> Unit,
    onCancel: () -> Unit,
) {
    val first = items.first()
    val firstLongSide = maxOf(first.width, first.height)
    // プリセットは、1枚でも、それより長辺が大きい写真があれば、使える
    val largestLongSide = items.maxOf { maxOf(it.width, it.height) }

    var percent by remember { mutableIntStateOf(DefaultPercent) }
    var preset by remember { mutableStateOf<Preset?>(null) }

    val target: ResizeTarget = preset?.let { ResizeTarget.LongSide(it.longSidePx) }
        ?: ResizeTarget.Percent(percent)
    val firstAfter = target.sizeFor(first.width, first.height)
    // 1枚も縮小されない設定では、「保存」を押せない
    val canSave = items.any { target.sizeFor(it.width, it.height) != null }

    RotationDialogFrame(onDismiss = onCancel) {
        Text(
            text = "リサイズ",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        // バーで調整 + 大きな%表示
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "バーで調整",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = percent.toFloat(),
            onValueChange = {
                percent = it.roundToInt()
                // スライダーを動かしたら、プリセットの選択は外す
                preset = null
            },
            valueRange = MinPercent.toFloat()..MaxPercent.toFloat(),
            steps = MaxPercent - MinPercent - 1,
            // 画像のように、平らな灰色のバーと、縦長のつまみにする
            thumb = {
                Spacer(
                    Modifier
                        .size(width = 6.dp, height = 28.dp)
                        .background(SliderThumbGray),
                )
            },
            track = {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(SliderTrackGray),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        // プリセット
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "プリセット",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "※ファイルより解像度が高いものは選択できません",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Presets.forEach { item ->
                PresetButton(
                    label = item.label,
                    selected = preset == item,
                    // 写真の長辺以上のプリセットは、拡大(または変化なし)になるため、選べない
                    enabled = item.longSidePx < largestLongSide,
                    onClick = {
                        preset = item
                        // スライダーも、そのプリセットに近い%へ動かす(1枚目の写真が基準)
                        percent = (item.longSidePx * 100.0 / firstLongSide).roundToInt()
                            .coerceIn(MinPercent, MaxPercent)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // リサイズ前/後(1枚目の写真)
        SizeRow(
            label = "リサイズ前",
            resolution = "${first.width}×${first.height}",
            fileSize = formatBytes(first.sizeBytes),
        )
        Spacer(Modifier.height(6.dp))
        if (firstAfter == null) {
            SizeRow(label = "リサイズ後", resolution = "変更なし", fileSize = "")
        } else {
            val (width, height) = firstAfter
            // 保存するまで、実際のサイズは分からないため、面積の比から出した目安にする
            val estimate = (first.sizeBytes * (width.toDouble() * height) / (first.width.toDouble() * first.height))
                .toLong()
            SizeRow(
                label = "リサイズ後",
                resolution = "${width}×${height}",
                fileSize = "約${formatBytes(estimate)}",
            )
        }

        if (items.size > 1) {
            Text(
                text = "${items.size}枚をまとめてリサイズ(表示は1枚目の写真)",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        if (unsupported > 0) {
            Text(
                text = "${unsupported}枚は非対応のため、リサイズされません",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CancelPillButton(onClick = onCancel, modifier = Modifier.weight(1f))
            BluePillButton(
                text = "保存",
                icon = Icons.Default.Save,
                enabled = canSave,
                onClick = { onSave(target) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 保存ダイアログ: 「ファイルを上書き」か「コピーを保存」を選ぶ(回転と同じ)。 */
@Composable
private fun ResizeSaveDialog(
    count: Int,
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
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (count > 1) "${count}枚を、どのように保存しますか。" else "どのように保存しますか。",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "上書きすると、元の高い解像度には、戻せません。",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        BluePillButton(text = "ファイルを上書き", icon = Icons.Default.Save, onClick = { onChoose(true) })
        Spacer(Modifier.height(8.dp))
        BluePillButton(text = "コピーを保存", icon = Icons.Default.Save, onClick = { onChoose(false) })
        Spacer(Modifier.height(10.dp))
        CancelPillButton(onClick = onCancel)
    }
}

/** プリセットのボタン(灰色のカプセル)。選択中は水色、選べないものは薄い文字。 */
@Composable
private fun PresetButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        shape = CircleShape,
        color = if (selected) PresetSelected else PresetGray,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black.copy(alpha = if (enabled) 1f else 0.3f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 「リサイズ前  3840×2160  4MB」の1行。 */
@Composable
private fun SizeRow(label: String, resolution: String, fileSize: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = resolution,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = fileSize,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 「812KB」「4.1MB」「1.23GB」。 */
private fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2fGB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1fMB", bytes / mb)
        else -> String.format(Locale.US, "%.0fKB", bytes / kb)
    }
}