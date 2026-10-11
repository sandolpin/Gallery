@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.sandolpin.gallery.ui.viewer

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/** 画面全体を包む、shared element transition の枠(GalleryRootで提供する)。 */
internal val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** ビューアーの、表示/非表示のアニメーションの枠(GalleryRootで提供する)。 */
internal val LocalViewerVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

private fun sharedKey(mediaId: Long) = "media_$mediaId"

/**
 * 一覧のサムネイル側(出発点)。
 * 何千枚もあるサムネイルの、すべてに目印をつけると重くなるため、
 * 「いまビューアーで表示している写真」のサムネイルにだけつける。
 * ビューアーが開いている間は、このサムネイルを非表示にして、ビューアーの写真と入れ替える。
 *
 * 注意: サイズ指定(width / height / fillMaxWidth など)より前に置くこと。
 * 角丸などの clip は、このモディファイアの「後ろ」に置くこと(置いてあれば、そのままでよい)。
 */
@Composable
fun Modifier.mediaSharedSource(mediaId: Long): Modifier {
    val scope = LocalSharedTransitionScope.current ?: return this
    val session by LocalViewerController.current.session.collectAsState()
    if (session.sharedId != mediaId) return this

    return with(scope) {
        // 引数名は、Composeのバージョンで違う(1.7.0は state、新しい版は sharedContentState)ため、
        // 最初の2つは名前なし(位置指定)で渡す
        this@mediaSharedSource.sharedElementWithCallerManagedVisibility(
            rememberSharedContentState(key = sharedKey(mediaId)),
            session.state == null,
        )
    }
}

/**
 * ビューアーの写真側(到着点)。表示中のページ([enabled] が true)にだけつける。
 * sharedElement は、動いている間、中身を、そのときの枠の大きさに合わせてレイアウトし直す。
 * そのため、サムネイル(正方形)とビューアー(画面いっぱい)で縦横比が違っても、
 * 写真が引き伸ばされない。
 */
@Composable
fun Modifier.mediaSharedTarget(mediaId: Long, enabled: Boolean): Modifier {
    if (!enabled) return this
    val scope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = LocalViewerVisibilityScope.current ?: return this

    return with(scope) {
        // 引数名は、Composeのバージョンで違うため、最初の2つは名前なし(位置指定)で渡す
        this@mediaSharedTarget.sharedElement(
            rememberSharedContentState(key = sharedKey(mediaId)),
            visibilityScope,
        )
    }
}