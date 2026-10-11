package com.sandolpin.gallery.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandolpin.gallery.data.model.MediaFolder
import com.sandolpin.gallery.data.repository.FavoriteRepository
import com.sandolpin.gallery.data.repository.MediaRepository
import com.sandolpin.gallery.data.repository.PinnedFolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** フォルダの並び順。設定への保存は、段階7でまとめて実装する。 */
enum class FolderSortOrder(val label: String) {
    CountDesc("枚数が多い順"),
    NameAsc("名前順"),
    RecentDesc("最近更新した順"),
}

data class FolderUiState(
    val pinned: List<MediaFolder> = emptyList(),
    val others: List<MediaFolder> = emptyList(),
    val isLoading: Boolean = true,
    val sortOrder: FolderSortOrder = FolderSortOrder.CountDesc,
    /** ゴミ箱の中の件数。フォルダ画面の一番下の「ゴミ箱(N)」に使う。 */
    val trashCount: Int = 0,
    /** お気に入りの件数。フォルダ画面の一番上の「お気に入り(N)」に使う。 */
    val favoriteCount: Int = 0,
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty()
}

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val pinnedRepository: PinnedFolderRepository,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {

    /** null = まだ読み込み中 */
    private val allFolders = MutableStateFlow<List<MediaFolder>?>(null)
    private val sortOrder = MutableStateFlow(FolderSortOrder.CountDesc)
    private val trashCount = MutableStateFlow(0)
    private val favoriteCount = MutableStateFlow(0)

    /** ゴミ箱が使える端末か(使えない端末では、入口を表示しない)。 */
    val isTrashSupported: Boolean get() = mediaRepository.isTrashSupported

    val uiState: StateFlow<FolderUiState> = combine(
        allFolders,
        pinnedRepository.pinnedIds,
        sortOrder,
        trashCount,
        favoriteCount,
    ) { folders, pinnedIds, order, trash, favorites ->
        if (folders == null) {
            FolderUiState(sortOrder = order, trashCount = trash, favoriteCount = favorites)
        } else {
            val sorted = sort(folders, order)
            FolderUiState(
                pinned = sorted.filter { it.id in pinnedIds },
                others = sorted.filter { it.id !in pinnedIds },
                isLoading = false,
                sortOrder = order,
                trashCount = trash,
                favoriteCount = favorites,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderUiState())

    /** 最初の読み込みと、削除・ゴミ箱への移動があったときの読み直し。 */
    init {
        viewModelScope.launch {
            mediaRepository.version.collect {
                allFolders.value = mediaRepository.getFolders()
                trashCount.value = mediaRepository.getTrashedMedia().size
            }
        }
    }

    /**
     * お気に入りの件数。お気に入りの追加・解除と、削除があったときに数え直す。
     * (お気に入りに入れたあとで削除した写真は、数に含めない)
     */
    init {
        viewModelScope.launch {
            combine(mediaRepository.version, favoriteRepository.favoriteIds) { _, ids -> ids }
                .collect { ids ->
                    favoriteCount.value = if (ids.isEmpty()) {
                        0
                    } else {
                        mediaRepository.getAllMedia().count { it.id in ids }
                    }
                }
        }
    }

    fun onSortSelected(order: FolderSortOrder) {
        sortOrder.value = order
    }

    fun onTogglePin(folder: MediaFolder, pinned: Boolean) {
        viewModelScope.launch { pinnedRepository.setPinned(folder.id, pinned) }
    }

    private fun sort(folders: List<MediaFolder>, order: FolderSortOrder): List<MediaFolder> =
        when (order) {
            FolderSortOrder.CountDesc -> folders.sortedByDescending { it.itemCount }
            FolderSortOrder.NameAsc -> folders.sortedBy { it.name.lowercase() }
            FolderSortOrder.RecentDesc -> folders.sortedByDescending { it.latestDateMillis }
        }
}