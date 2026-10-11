package com.sandolpin.gallery.ui.viewer

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sandolpin.gallery.data.model.GeoPoint
import com.sandolpin.gallery.data.model.MediaDetails
import com.sandolpin.gallery.data.model.MediaItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val IconCircleGray = Color(0xFFBDBDBD)
private val IconDark = Color(0xFF212121)

/**
 * 詳細情報ダイアログ(ファイル名・撮影日時・解像度・サイズ・位置情報・場所)。
 * - 各行をタップすると、その値をコピーする。
 * - 位置情報の行は、タップで「地名」と「緯度経度」を切り替え、長押しで、表示中の内容をコピーする。
 *   位置情報がない写真・動画では、この行を表示しない。
 *
 * 保存場所・位置情報は [loadDetails]、地名は [loadPlaceName](通信が必要)で、開いたあとに読み込む。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaDetailsDialog(
    item: MediaItem,
    loadDetails: suspend (MediaItem) -> MediaDetails,
    loadPlaceName: suspend (GeoPoint) -> String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var details by remember(item.id) { mutableStateOf<MediaDetails?>(null) }
    var placeName by remember(item.id) { mutableStateOf<String?>(null) }
    var placeLoaded by remember(item.id) { mutableStateOf(false) }
    var showCoordinates by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        val loaded = loadDetails(item)
        details = loaded
        loaded.location?.let { point ->
            placeName = loadPlaceName(point)
        }
        placeLoaded = true
    }

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        // Android 13以降は、OSが「コピーしました」を出すため、それより前の版だけ表示する
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "詳細情報",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "タップでコピー",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 項目が多い・画面が小さいときは、中だけスクロールする
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val fileName = item.displayName
                    DetailRow(Icons.Default.Image, "ファイル名", fileName, onClick = { copy(fileName) })

                    val date = formatDate(item.dateTakenMillis)
                    DetailRow(Icons.Default.CalendarMonth, "撮影日時", date, onClick = { copy(date) })

                    val resolution = formatResolution(item)
                    DetailRow(Icons.Default.AspectRatio, "解像度", resolution, onClick = { copy(resolution) })

                    val size = formatFileSize(item.sizeBytes)
                    DetailRow(Icons.Default.FileCopy, "ファイルサイズ", size, onClick = { copy(size) })

                    // 位置情報: ある場合だけ表示する
                    val point = details?.location
                    if (point != null) {
                        val coordinates = formatCoordinates(point)
                        val canToggle = placeName != null
                        val showingCoordinates = showCoordinates || (placeLoaded && placeName == null)
                        val text = when {
                            showingCoordinates -> coordinates
                            placeName != null -> placeName!!
                            else -> "地名を取得中…"
                        }
                        val hint = when {
                            !canToggle -> null
                            showingCoordinates -> "タップで地名を表示"
                            else -> "タップで緯度経度を表示"
                        }
                        DetailRow(
                            icon = Icons.Default.LocationOn,
                            label = "位置情報",
                            value = text,
                            hint = hint,
                            onClick = { if (canToggle) showCoordinates = !showCoordinates },
                            onLongClick = { if (placeLoaded) copy(text) },
                        )
                    }

                    DetailRow(
                        icon = Icons.Default.Folder,
                        label = "ファイルの場所",
                        value = details?.folderPath ?: "…",
                        onClick = { details?.folderPath?.let(::copy) },
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 閉じるボタン(枠線つきのカプセル)
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "閉じる",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/** 1行分: 灰色の丸アイコン、小さなラベル、太字の値。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    /** 値の右に、小さく添える操作の説明(例: タップで緯度経度を表示) */
    hint: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(IconCircleGray),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = IconDark)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (hint != null) {
                Text(text = hint, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ---- 表示用の整形 ----

private val DateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

/** 「2026/09/28 16:47」(端末のタイムゾーン)。 */
private fun formatDate(millis: Long): String =
    DateFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** 写真は「4096 × 2304 (11.9MP)」、動画は「1920 × 1080」。 */
private fun formatResolution(item: MediaItem): String {
    val base = "${item.width} × ${item.height}"
    if (item.isVideo) return base
    val megaPixels = item.width.toLong() * item.height / 1_000_000.0
    return "$base (${String.format(Locale.US, "%.1f", megaPixels)}MP)"
}

/** 「812KB」「4.09MB」「1.23GB」。 */
private fun formatFileSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2fGB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.2fMB", bytes / mb)
        else -> String.format(Locale.US, "%.0fKB", bytes / kb)
    }
}

/** 「36.321900, 139.003300」。 */
private fun formatCoordinates(point: GeoPoint): String =
    String.format(Locale.US, "%.6f, %.6f", point.latitude, point.longitude)