package com.sandolpin.gallery.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 下部ナビゲーションの4タブ。
 * 「すべて」のラベルは画面上では「すべて」、内部ルート名は all とする。
 */
enum class GalleryDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home(route = "home", label = "ホーム", icon = Icons.Default.Home),
    All(route = "all", label = "すべて", icon = Icons.Default.Photo),
    Folder(route = "folder", label = "フォルダ", icon = Icons.Default.Folder),
    Settings(route = "settings", label = "設定", icon = Icons.Default.Settings),
}
