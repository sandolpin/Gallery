package com.sandolpin.gallery.ui.viewer

import androidx.compose.runtime.staticCompositionLocalOf
import com.sandolpin.gallery.data.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ビューアーに表示する写真の一覧と、最初に表示する位置。
 * 「すべて」「フォルダ内」など、開いた元の一覧を、そのままビューアーに渡す。
 * (1万枚の一覧は、画面遷移の引数では渡せないため、アプリ内の共有の置き場に入れる)
 */
data class ViewerState(
    val items: List<MediaItem>,
    val startIndex: Int,
    /** 開くたびに変わる番号。ビューアーの状態を、毎回、作り直すために使う。 */
    val sessionId: Long,
)

/**
 * ビューアーの開閉の状態。
 * 開く・閉じると、shared element transition の目印をつける写真を、1つの状態にまとめて、
 * 一覧側とビューアー側が、同じタイミングで切り替わるようにしている。
 *
 * @param state 開いているときは中身があり、閉じているときは null。
 * @param sharedId 目印をつける写真のID(いまビューアーで表示している写真)。
 *   閉じるアニメーションの間も必要なため、閉じても残す。
 */
data class ViewerSession(
    val state: ViewerState? = null,
    val sharedId: Long? = null,
)

@Singleton
class ViewerController @Inject constructor() {
    private val _session = MutableStateFlow(ViewerSession())
    val session: StateFlow<ViewerSession> = _session.asStateFlow()

    /** [items] の中の、[startId] の写真を最初に表示して、ビューアーを開く。 */
    fun open(items: List<MediaItem>, startId: Long) {
        if (items.isEmpty()) return
        val index = items.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        _session.value = ViewerSession(
            state = ViewerState(items, index, System.nanoTime()),
            sharedId = items[index].id,
        )
    }

    fun close() {
        _session.update { it.copy(state = null) }
    }

    /** ビューアーで、別の写真に移ったとき。目印をつける写真を切り替える。 */
    fun onPageChanged(mediaId: Long) {
        _session.update { if (it.state == null) it else it.copy(sharedId = mediaId) }
    }
}

/** 画面のどこからでも、ビューアーを開けるようにするための受け渡し。 */
val LocalViewerController = staticCompositionLocalOf<ViewerController> {
    error("ViewerController が提供されていません")
}