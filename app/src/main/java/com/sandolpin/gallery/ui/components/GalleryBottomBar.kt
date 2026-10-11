package com.sandolpin.gallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sandolpin.gallery.ui.navigation.GalleryDestination

/**
 * ホーム画面だけ、右に独立した検索ボタンを表示する。
 * それ以外の画面(すべて・フォルダ・設定)では、本体の4項目だけを表示する。
 */
@Composable
fun GalleryBottomBar(
    currentDestination: GalleryDestination,
    onDestinationSelected: (GalleryDestination) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 本体(丸い角のナビゲーション)
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                GalleryDestination.entries.forEach { destination ->
                    GalleryNavItem(
                        destination = destination,
                        selected = destination == currentDestination,
                        onClick = { onDestinationSelected(destination) },
                    )
                }
            }
        }

        // ホーム画面だけ表示する、独立した検索ボタン
        AnimatedVisibility(visible = currentDestination == GalleryDestination.Home) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                onClick = onSearchClick,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "検索")
                }
            }
        }
    }
}

@Composable
private fun GalleryNavItem(
    destination: GalleryDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Icon(imageVector = destination.icon, contentDescription = destination.label)
        if (selected) {
            Text(text = destination.label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
