package com.sandolpin.gallery.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashUiState(
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val isSelecting: Boolean = false,
    /** 選択中の写真のID。いま一覧にあるものだけを含む。 */
    val selectedIds: Set<Long> = emptySet(),
)

/** 選択モードの状態。 */
private data class Selection(
    val isSelecting: Boolean = false,
    val ids: Set<Long> = emptySet(),
)

/** ゴミ箱画面。一覧の表示と、選択・復元・完全削除・ゴミ箱を空にする、を担当する。 */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {

    /** null = まだ読み込み中 */
    private val source = MutableStateFlow<List<MediaItem>?>(null)
    private val selection = MutableStateFlow(Selection())

    val uiState: StateFlow<TrashUiState> = combine(source, selection) { items, sel ->
        if (items == null) {
            TrashUiState(isSelecting = sel.isSelecting)
        } else {
            val ids = items.mapTo(HashSet()) { it.id }
            TrashUiState(
                items = items,
                isLoading = false,
                isSelecting = sel.isSelecting,
                selectedIds = sel.ids.filterTo(HashSet()) { it in ids },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** 最初の読み込みと、削除・復元などがあったときの読み直し。 */
    init {
        viewModelScope.launch {
            repository.version.collect {
                source.value = repository.getTrashedMedia()
            }
        }
    }

    // ---- 選択 ----

    /** タップ: 選択モードでなければ始めて選ぶ。選択モード中は、選ぶ/外す。 */
    fun onItemClick(mediaId: Long) = selection.update {
        if (!it.isSelecting) {
            Selection(isSelecting = true, ids = setOf(mediaId))
        } else {
            it.copy(ids = if (mediaId in it.ids) it.ids - mediaId else it.ids + mediaId)
        }
    }

    /** 長押し: 選択モードを始めて、その写真を選ぶ。 */
    fun onLongPress(mediaId: Long) = selection.update {
        it.copy(isSelecting = true, ids = it.ids + mediaId)
    }

    /** 「すべて選択」。すでに全部選んでいたら、全部外す(モードは続ける)。 */
    fun onSelectAllToggle() {
        val shownIds = uiState.value.items.mapTo(HashSet()) { it.id }
        selection.update {
            val allSelected = shownIds.isNotEmpty() && shownIds.all { id -> id in it.ids }
            it.copy(ids = if (allSelected) emptySet() else it.ids + shownIds)
        }
    }

    fun onSelectionClose() = selection.update { Selection() }

    // ---- 操作 ----

    /** 選んだ写真を、ゴミ箱から元に戻す。 */
    fun restoreSelected() {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        onSelectionClose()
        viewModelScope.launch {
            val done = repository.restore(targets)
            _messages.emit(resultMessage(done, targets.size, "復元しました", "復元できませんでした"))
        }
    }

    /** 選んだ写真を、完全に削除する。 */
    fun deleteSelected() {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        onSelectionClose()
        deleteForever(targets)
    }

    /** ゴミ箱の中身を、全部、完全に削除する。 */
    fun emptyTrash() {
        val targets = uiState.value.items
        if (targets.isEmpty()) return
        onSelectionClose()
        deleteForever(targets)
    }

    private fun deleteForever(targets: List<MediaItem>) {
        viewModelScope.launch {
            val done = repository.deletePermanently(targets)
            _messages.emit(resultMessage(done, targets.size, "完全に削除しました", "削除できませんでした"))
        }
    }

    private fun selectedItems(): List<MediaItem> {
        val state = uiState.value
        return state.items.filter { it.id in state.selectedIds }
    }

    private fun resultMessage(done: Int, total: Int, success: String, failure: String): String =
        when {
            done == 0 -> failure
            done < total -> "${total}件中${done}件のみ処理できました"
            else -> "${done}件を$success"
        }
}