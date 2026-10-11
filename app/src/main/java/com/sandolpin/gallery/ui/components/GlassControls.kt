package com.sandolpin.gallery.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sandolpin.gallery.data.analysis.PhotoCategories
import com.sandolpin.gallery.ui.settings.LocalAppSettings

/** 画面上部の、半透明ヘッダーの高さ。リストの上の余白(contentPadding)にも使う。 */
val GlassHeaderHeight = 64.dp

/**
 * 検索バーに薄く出す、検索の例文。
 * 「あいまい検索」がONのときは、カテゴリの中からランダムに選んだ言葉(「楽器」で検索 など)。画面を開くたびに変わる。
 * OFFのときは、写真の中身では検索できないので、「ファイル名で検索」。
 */
@Composable
fun rememberSearchHint(): String {
    val fuzzy = LocalAppSettings.current.fuzzySearchEnabled
    val example = remember { PhotoCategories.searchExamples.random() }
    return if (fuzzy) "「$example」で検索" else "ファイル名で検索"
}

/** 半透明の検索バー。タップすると [onClick] を呼ぶ(ここでは入力しない)。 */
@Composable
fun GlassSearchPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String = rememberSearchHint(),
) {
    GlassSurface(
        modifier = modifier.height(48.dp),
        shape = CircleShape,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Default.Search, contentDescription = "検索")
            Spacer(Modifier.width(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 半透明の並び替えボタン。押すと、[options] のメニューが開く。 */
@Composable
fun <T> GlassSortButton(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        var expanded by remember { mutableStateOf(false) }

        GlassSurface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            onClick = { expanded = true },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "並び替え",
                modifier = Modifier.align(Alignment.Center),
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    leadingIcon = {
                        if (option == selected) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 半透明の丸いアイコンボタン(戻る、検索、︙など)。 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
