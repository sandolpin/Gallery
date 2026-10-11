package com.sandolpin.gallery.ui.fileop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandolpin.gallery.data.model.MediaFolder
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

data class FolderPickerUiState(
    /** ピン留めしたフォルダが先頭、そのあとは、枚数の多い順。 */
    val folders: List<MediaFolder> = emptyList(),
    val pinnedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
)

/** コピー・移動の、行き先のフォルダ選択画面用。フォルダの一覧と、ピン留めの状態を持つ。 */
@HiltViewModel
class FolderPickerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    pinnedRepository: PinnedFolderRepository,
) : ViewModel() {

    /** null = まだ読み込み中 */
    private val allFolders = MutableStateFlow<List<MediaFolder>?>(null)

    val uiState: StateFlow<FolderPickerUiState> = combine(
        allFolders,
        pinnedRepository.pinnedIds,
    ) { folders, pinnedIds ->
        if (folders == null) {
            FolderPickerUiState()
        } else {
            FolderPickerUiState(
                folders = folders.sortedByDescending { it.id in pinnedIds },
                pinnedIds = pinnedIds,
                isLoading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderPickerUiState())

    /** 最初の読み込みと、コピー・削除などで一覧が変わったときの読み直し。 */
    init {
        viewModelScope.launch {
            mediaRepository.version.collect {
                allFolders.value = mediaRepository.getFolders()
            }
        }
    }
}