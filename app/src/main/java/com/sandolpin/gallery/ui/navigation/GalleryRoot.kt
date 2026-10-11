package com.sandolpin.gallery.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sandolpin.gallery.ui.fileop.FileOperationController
import com.sandolpin.gallery.ui.fileop.FileOperationHost
import com.sandolpin.gallery.ui.fileop.LocalFileOperationController
import com.sandolpin.gallery.ui.resize.LocalResizeController
import com.sandolpin.gallery.ui.resize.ResizeController
import com.sandolpin.gallery.ui.resize.ResizeHost
import com.sandolpin.gallery.ui.rotation.LocalRotationController
import com.sandolpin.gallery.ui.rotation.RotationController
import com.sandolpin.gallery.ui.rotation.RotationHost
import com.sandolpin.gallery.ui.viewer.LocalSharedTransitionScope
import com.sandolpin.gallery.ui.viewer.LocalViewerController
import com.sandolpin.gallery.ui.viewer.LocalViewerVisibilityScope
import com.sandolpin.gallery.ui.viewer.ViewerController
import com.sandolpin.gallery.ui.viewer.ViewerScreen
import com.sandolpin.gallery.ui.viewer.ViewerState

/**
 * アプリ全体の最上位。通常の画面(GalleryApp)の上に、ビューアーを重ねて表示する。
 * ビューアーは画面遷移の行き先ではなく、重ね表示にしているため、
 * ステータスバー・下部ナビゲーションの余白の影響を受けず、全画面で表示できる。
 *
 * コピー・移動のフォルダ選択と進行状況([FileOperationHost])は、いちばん手前に重ねる。
 *
 * 全体を SharedTransitionLayout で包み、一覧のサムネイルとビューアーの写真の間で、
 * 開く・閉じるときの shared element transition を行う。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryRoot(
    viewerController: ViewerController,
    fileOperationController: FileOperationController,
    rotationController: RotationController,
    resizeController: ResizeController,
) {
    val session by viewerController.session.collectAsState()
    val viewerState = session.state

    // 閉じるアニメーションの間も、直前の内容を表示し続けるため、最後の状態を覚えておく
    var lastViewerState by remember { mutableStateOf<ViewerState?>(null) }
    if (viewerState != null) lastViewerState = viewerState

    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalViewerController provides viewerController,
            LocalFileOperationController provides fileOperationController,
            LocalRotationController provides rotationController,
            LocalResizeController provides resizeController,
            LocalSharedTransitionScope provides this,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                GalleryApp()

                AnimatedVisibility(
                    visible = viewerState != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    // このブロックの this は AnimatedVisibilityScope(ビューアーの表示/非表示の枠)
                    CompositionLocalProvider(LocalViewerVisibilityScope provides this) {
                        lastViewerState?.let { state ->
                            // 開くたびに、ページの位置などの状態を作り直す
                            key(state.sessionId) {
                                ViewerScreen(state = state, onClose = viewerController::close)
                            }
                        }
                    }
                }

                // コピー・移動の、フォルダ選択と進行状況。ビューアーより手前に重ねる
                FileOperationHost(fileOperationController)

                // 回転の、選択肢・回転・保存・進行状況のダイアログ
                RotationHost(rotationController)

                // リサイズの、設定・保存・進行状況のダイアログ
                ResizeHost(resizeController)
            }
        }
    }
}