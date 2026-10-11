package com.sandolpin.gallery.ui.all

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.model.RenameResult
import com.sandolpin.gallery.data.repository.FavoriteRepository
import com.sandolpin.gallery.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** 並び順。設定への保存は、段階7でまとめて実装する。 */
enum class SortOrder(val label: String) {
    NewestFirst("新しい順"),
    OldestFirst("古い順"),
}

data class AllPhotosUiState(
    val days: List<DayGroup> = emptyList(),
    val isLoading: Boolean = true,
    val sortOrder: SortOrder = SortOrder.NewestFirst,
    /** 選択モード中かどうか。最後の1枚の選択を外しても、×を押すまでモードは続く。 */
    val isSelecting: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
) {
    val isEmpty: Boolean get() = days.isEmpty()
}

/**
 * 写真を日付ごとにまとめ、並び順を適用するところまでを担当する。
 * 画面の幅に応じた行の計算は、幅が分かる画面側で行う(JustifiedLayout.kt)。
 */
@HiltViewModel
class AllPhotosViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val favoriteRepository: FavoriteRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AllPhotosUiState())
    val uiState: StateFlow<AllPhotosUiState> = _uiState.asStateFlow()

    /** 「3件をゴミ箱へ移動しました」などの、一度だけ表示するメッセージ。 */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** お気に入りの写真のID。選択モードの︙メニュー(追加/外す)の表示に使う。 */
    val favoriteIds: StateFlow<Set<Long>> = favoriteRepository.favoriteIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** ゴミ箱が使える端末か(使えないときは、削除ダイアログの文言と動きが変わる)。 */
    val isTrashSupported: Boolean get() = repository.isTrashSupported

    /** 新しい順にまとめた元データ。古い順は、これを逆にして作る。 */
    private var newestFirstDays: List<DayGroup> = emptyList()

    init {
        observeChanges()
    }

    /** 最初の読み込みと、削除・ゴミ箱への移動があったときの読み直し。 */
    private fun observeChanges() {
        viewModelScope.launch {
            repository.version.collect {
                val items = repository.getAllMedia()
                newestFirstDays = withContext(Dispatchers.Default) { groupByDay(items) }
                // 削除などで、なくなった写真は、選択からも外す
                val existingIds = items.mapTo(HashSet()) { it.id }
                _uiState.update {
                    it.copy(
                        days = applySort(newestFirstDays, it.sortOrder),
                        isLoading = false,
                        selectedIds = it.selectedIds.filterTo(HashSet()) { id -> id in existingIds },
                    )
                }
            }
        }
    }

    // ---- 選択モード ----

    /** 長押し: 選択モードを始めて、その写真を選ぶ。 */
    fun onLongPress(mediaId: Long) = _uiState.update {
        it.copy(isSelecting = true, selectedIds = it.selectedIds + mediaId)
    }

    /** 選択モード中のタップ: 選ぶ/外す。 */
    fun onToggleSelect(mediaId: Long) = _uiState.update {
        val next = if (mediaId in it.selectedIds) it.selectedIds - mediaId else it.selectedIds + mediaId
        it.copy(selectedIds = next)
    }

    /** 「すべて選択」。すでに全部選んでいたら、全部外す(モードは続ける)。 */
    fun onSelectAllToggle() = _uiState.update { state ->
        val allIds = state.days.flatMap { day -> day.items }.mapTo(HashSet()) { it.id }
        val allSelected = allIds.isNotEmpty() && allIds.all { it in state.selectedIds }
        state.copy(selectedIds = if (allSelected) emptySet() else allIds)
    }

    /**
     * 選んだ写真を、お気に入りに追加する。すでに全部がお気に入りなら、全部外す。
     * 終わったら、選択モードも終える。
     */
    fun toggleFavoriteSelected() {
        val state = _uiState.value
        val ids = state.days.flatMap { it.items }.filter { it.id in state.selectedIds }.map { it.id }
        if (ids.isEmpty()) return
        val allFavorite = ids.all { it in favoriteIds.value }
        onSelectionClose()
        viewModelScope.launch {
            favoriteRepository.setFavorite(ids, !allFavorite)
            _messages.emit(
                if (allFavorite) "${ids.size}件をお気に入りから外しました"
                else "${ids.size}件をお気に入りに追加しました",
            )
        }
    }

    /** 名前の変更。[newDisplayName] は、拡張子を含む名前。 */
    suspend fun rename(item: MediaItem, newDisplayName: String): RenameResult =
        repository.rename(item, newDisplayName)

    /** 選択モードを終わる(選択も全部外す)。 */
    fun onSelectionClose() = _uiState.update { it.copy(isSelecting = false, selectedIds = emptySet()) }

    /**
     * 選んだ写真を削除する。[permanent] が false ならゴミ箱へ移動、true ならそのまま削除。
     * ゴミ箱が使えない端末では、常にそのまま削除になる。終わったら、選択モードも終える。
     */
    fun deleteSelected(permanent: Boolean) {
        val state = _uiState.value
        val targets = state.days.flatMap { it.items }.filter { it.id in state.selectedIds }
        if (targets.isEmpty()) return
        onSelectionClose()

        viewModelScope.launch {
            val toTrash = !permanent && repository.isTrashSupported
            val done = if (toTrash) repository.trash(targets) else repository.deletePermanently(targets)
            val message = when {
                done == 0 -> if (toTrash) "ゴミ箱へ移動できませんでした" else "削除できませんでした"
                done < targets.size -> "${targets.size}件中${done}件のみ処理できました"
                toTrash -> "${done}件をゴミ箱へ移動しました"
                else -> "${done}件を削除しました"
            }
            _messages.emit(message)
        }
    }

    fun onSortSelected(order: SortOrder) {
        if (order == _uiState.value.sortOrder) return
        viewModelScope.launch {
            val days = withContext(Dispatchers.Default) { applySort(newestFirstDays, order) }
            _uiState.update { it.copy(days = days, sortOrder = order) }
        }
    }

    /** 古い順は、日付の順序と、同じ日の中の写真の順序の、両方を逆にする。 */
    private fun applySort(newestFirst: List<DayGroup>, order: SortOrder): List<DayGroup> =
        when (order) {
            SortOrder.NewestFirst -> newestFirst
            SortOrder.OldestFirst -> newestFirst.asReversed().map {
                it.copy(items = it.items.asReversed())
            }
        }

    /** 撮影日(端末のタイムゾーン)ごとにまとめる。入力の並び順は保たれる。 */
    private fun groupByDay(items: List<MediaItem>): List<DayGroup> {
        val zone = ZoneId.systemDefault()
        return items
            .groupBy { Instant.ofEpochMilli(it.dateTakenMillis).atZone(zone).toLocalDate() }
            .map { (date: LocalDate, list) -> DayGroup(date, list) }
    }
}