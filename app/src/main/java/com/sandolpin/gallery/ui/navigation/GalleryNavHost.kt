package com.sandolpin.gallery.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sandolpin.gallery.data.analysis.PhotoCategories
import com.sandolpin.gallery.ui.components.GalleryBottomBar
import com.sandolpin.gallery.ui.folder.FavoritesFolderId
import com.sandolpin.gallery.ui.folder.FolderDetailScreen
import com.sandolpin.gallery.ui.folder.FolderScreen
import com.sandolpin.gallery.ui.home.HomeScreen
import com.sandolpin.gallery.ui.all.AllPhotosScreen
import com.sandolpin.gallery.ui.search.SearchScreen
import com.sandolpin.gallery.ui.settings.SettingsScreen
import com.sandolpin.gallery.ui.trash.TrashScreen

private const val ANIM_DURATION_MS = 300
private const val FOLDER_DETAIL_ROUTE = "folder_detail"
private const val TRASH_ROUTE = "trash"

@Composable
fun GalleryApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route
    // タブ以外の画面(検索など)では、直前に開いていたタブを、選択したままにする
    var lastTab by remember { mutableStateOf(GalleryDestination.Home) }
    val currentTab = GalleryDestination.entries.firstOrNull { it.route == currentRoute }
    LaunchedEffect(currentTab) { if (currentTab != null) lastTab = currentTab }

    val currentDestination = when {
        // フォルダ内画面・ゴミ箱画面は、「フォルダ」タブの一部として扱う
        currentRoute?.startsWith(FOLDER_DETAIL_ROUTE) == true -> GalleryDestination.Folder
        currentRoute == TRASH_ROUTE -> GalleryDestination.Folder
        else -> currentTab ?: lastTab
    }

    // タブ切り替え(下部ナビゲーション・ホームの「>」で使う)。
    // バックスタックが積み重ならないようにして、各タブの状態を覚えておく
    val navigateToTab: (GalleryDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        // 画面の中身が、ステータスバー(上)・左右の切り欠きと重ならないようにする。
        // 下側は、GalleryBottomBar自身がナビゲーションバー分の余白を持つ。
        contentWindowInsets = WindowInsets.systemBars
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        bottomBar = {
            GalleryBottomBar(
                currentDestination = currentDestination,
                onDestinationSelected = navigateToTab,
                onSearchClick = { navController.navigate("search") },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = GalleryDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
            // 4タブ間はタブの並び順に応じて左右スライドする
            enterTransition = {
                slideIntoContainer(slideDirection(), tween(ANIM_DURATION_MS))
            },
            exitTransition = {
                slideOutOfContainer(slideDirection(), tween(ANIM_DURATION_MS))
            },
            popEnterTransition = {
                slideIntoContainer(slideDirection(), tween(ANIM_DURATION_MS))
            },
            popExitTransition = {
                slideOutOfContainer(slideDirection(), tween(ANIM_DURATION_MS))
            },
        ) {
            composable(GalleryDestination.Home.route) {
                HomeScreen(
                    onOpenFolder = { folderId ->
                        navController.navigate("$FOLDER_DETAIL_ROUTE/$folderId")
                    },
                    onOpenFolderTab = { navigateToTab(GalleryDestination.Folder) },
                    onOpenFavorites = {
                        navController.navigate("$FOLDER_DETAIL_ROUTE/$FavoritesFolderId")
                    },
                    // カテゴリ(「食べ物」など)の写真の一覧は、フォルダ内画面を、特別なフォルダIDで開いて表示する
                    onOpenCategory = { category ->
                        navController.navigate(
                            "$FOLDER_DETAIL_ROUTE/${PhotoCategories.folderIdOf(category)}",
                        )
                    },
                )
            }
            composable(GalleryDestination.All.route) {
                AllPhotosScreen(onSearchClick = { navController.navigate("search") })
            }
            composable(GalleryDestination.Folder.route) {
                FolderScreen(
                    onSearchClick = { navController.navigate("search") },
                    onFolderClick = { folder ->
                        navController.navigate("$FOLDER_DETAIL_ROUTE/${folder.id}")
                    },
                    onTrashClick = { navController.navigate(TRASH_ROUTE) },
                    // お気に入りは、フォルダ内画面を、特別なフォルダIDで開いて表示する
                    onFavoritesClick = {
                        navController.navigate("$FOLDER_DETAIL_ROUTE/$FavoritesFolderId")
                    },
                )
            }
            composable(GalleryDestination.Settings.route) {
                SettingsScreen(onOpenTrash = { navController.navigate(TRASH_ROUTE) })
            }
            composable(
                route = "$FOLDER_DETAIL_ROUTE/{folderId}",
                arguments = listOf(navArgument("folderId") { type = NavType.LongType }),
            ) {
                FolderDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(TRASH_ROUTE) {
                TrashScreen(onBack = { navController.popBackStack() })
            }
            // 検索画面(ファイル名・写真の中身・写真内の文字)
            composable("search") { SearchScreen() }
            // フォルダ内、ビューアー、ゴミ箱などは、次の段階で追加していく
        }
    }
}

/** 4タブの並び順での位置。タブ以外の画面(検索など)は -1。 */
private fun tabIndex(route: String?): Int =
    GalleryDestination.entries.indexOfFirst { it.route == route }

/**
 * スライドの方向を決める。
 * - 右のタブへ移動 → 左へスライド(新しい画面が右から入ってくる)
 * - 左のタブへ移動 → 右へスライド
 * - タブから検索などの別画面へ → 左へスライド、戻るときは右へスライド
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideDirection():
        AnimatedContentTransitionScope.SlideDirection {
    val from = tabIndex(initialState.destination.route)
    val to = tabIndex(targetState.destination.route)
    return when {
        from == -1 -> AnimatedContentTransitionScope.SlideDirection.Right
        to == -1 -> AnimatedContentTransitionScope.SlideDirection.Left
        to >= from -> AnimatedContentTransitionScope.SlideDirection.Left
        else -> AnimatedContentTransitionScope.SlideDirection.Right
    }
}
