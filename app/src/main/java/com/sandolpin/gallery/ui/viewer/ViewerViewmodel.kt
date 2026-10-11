package com.sandolpin.gallery.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandolpin.gallery.data.local.mediastore.MediaDetailsLoader
import com.sandolpin.gallery.data.model.GeoPoint
import com.sandolpin.gallery.data.model.MediaDetails
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.model.RenameResult
import com.sandolpin.gallery.data.repository.FavoriteRepository
import com.sandolpin.gallery.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ビューアーでの、ファイル操作(削除など)と、詳細情報の読み込みを担当する。
 * ビューアーは、画面遷移の外に重ねて表示しているため、Activityに1つだけ作られる。
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val detailsLoader: MediaDetailsLoader,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {

    /** お気に入りの写真のID。下部の☆/★の表示に使う。 */
    val favoriteIds: StateFlow<Set<Long>> = favoriteRepository.favoriteIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** お気に入りに入れる/外す(いまの状態の逆にする)。 */
    fun toggleFavorite(mediaId: Long) {
        viewModelScope.launch {
            val isFavorite = mediaId in favoriteIds.value
            favoriteRepository.setFavorite(listOf(mediaId), !isFavorite)
        }
    }

    val isTrashSupported: Boolean get() = repository.isTrashSupported

    /**
     * 1件を削除する。[permanent] が true ならゴミ箱を通さず削除。
     * ゴミ箱が使えない端末では、常にそのまま削除する。成功したら true。
     */
    suspend fun delete(item: MediaItem, permanent: Boolean): Boolean {
        val toTrash = !permanent && repository.isTrashSupported
        val done = if (toTrash) repository.trash(listOf(item)) else repository.deletePermanently(listOf(item))
        return done > 0
    }

    /** 名前の変更。[newDisplayName] は、拡張子を含む名前。 */
    suspend fun rename(item: MediaItem, newDisplayName: String): RenameResult =
        repository.rename(item, newDisplayName)

    /** 詳細情報ダイアログ用: 保存場所と位置情報(緯度経度)を読む。 */
    suspend fun loadDetails(item: MediaItem): MediaDetails = detailsLoader.load(item)

    /** 詳細情報ダイアログ用: 緯度経度から地名を取る(通信が必要。取れなければ null)。 */
    suspend fun loadPlaceName(point: GeoPoint): String? = detailsLoader.placeName(point)
}