package com.sandolpin.gallery.ui.all

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sandolpin.gallery.ui.components.GlassHeaderHeight
import com.sandolpin.gallery.ui.components.GlassSearchPill
import com.sandolpin.gallery.ui.components.GlassSortButton
import com.sandolpin.gallery.ui.components.GlassSurface

/** ヘッダーの高さ。リストの上の余白(contentPadding)にも使う。 */
val AllPhotosHeaderHeight = GlassHeaderHeight

/**
 * 「すべて」画面の上部ヘッダー: 年月、検索バー、並び替えボタン。
 * リストの上に重ねて表示し、各部品は半透明(Liquid Glass風)にして、
 * スクロールした写真・文字がうっすら透けて見えるようにする。
 */
@Composable
fun AllPhotosHeader(
    title: String,
    sortOrder: SortOrder,
    onSortSelected: (SortOrder) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AllPhotosHeaderHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 年月(写真の上に重なっても読めるよう、検索バーと同じ半透明の背景をつける)
        GlassSurface(
            modifier = Modifier.height(48.dp),
            shape = CircleShape,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp),
            )
        }

        GlassSearchPill(
            onClick = onSearchClick,
            modifier = Modifier.weight(1f),
        )

        GlassSortButton(
            options = SortOrder.entries,
            selected = sortOrder,
            labelOf = { it.label },
            onSelected = onSortSelected,
        )
    }
}