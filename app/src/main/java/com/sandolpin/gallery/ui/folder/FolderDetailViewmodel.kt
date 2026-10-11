package com.sandolpin.gallery.ui.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandolpin.gallery.data.analysis.AnalysisRepository
import com.sandolpin.gallery.data.analysis.PhotoCategories
import com.sandolpin.gallery.data.analysis.PhotoCategory
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.model.RenameResult
import com.sandolpin.gallery.data.repository.FavoriteRepository
import com.sandolpin.gallery.data.repository.MediaRepository
import com.sandolpin.gallery.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** フォルダ内の並び順。設定への保存は、段階7でまとめて実装する。 */
enum class FolderMediaSort(val label: String) {
    NewestFirst("新しい順"),
    OldestFirst("古い順"),
    NameAsc("名前順"),
}

/** 表示する種類の絞り込み。 */
enum class MediaFilter(val label: String) {
    All("すべて"),
    PhotosOnly("写真のみ"),
    VideosOnly("動画のみ"),
}

val ColumnOptions = listOf(2, 3, 4, 5)

/**
 * 「お気に入り」を、フォルダ内画面で開くための、特別なフォルダID。
 * フォルダのID(MediaStoreのBUCKET_ID)には、まず現れない値にしている。
 * (写真のカテゴリの一覧も、これに続く特別なIDで開く。[PhotoCategories.folderIdOf])
 */
const val FavoritesFolderId = Long.MIN_VALUE

/** 画面の見た目に関する選択(並び順・絞り込み・列数・検索・選択モード)。 */
private data class Options(
    val sort: FolderMediaSort = FolderMediaSort.NewestFirst,
    val filter: MediaFilter = MediaFilter.All,
    val columns: Int = 3,
    val query: String = "",
    val isSearching: Boolean = false,
    /** 選択モード中かどうか。最後の1枚の選択を外しても、×を押すまでモードは続く。 */
    val isSelecting: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
)

data class FolderDetailUiState(
    val folderName: String = "",
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val sort: FolderMediaSort = FolderMediaSort.NewestFirst,
    val filter: MediaFilter = MediaFilter.All,
    val columns: Int = 3,
    val query: String = "",
    val isSearching: Boolean = false,
    val isSelecting: Boolean = false,
    /** 選択中の写真のID。いま一覧に表示されているものだけを含む。 */
    val selectedIds: Set<Long> = emptySet(),
)

@HiltViewModel
class FolderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MediaRepository,
    private val favoriteRepository: FavoriteRepository,
    private val analysisRepository: AnalysisRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val folderId: Long = checkNotNull(savedStateHandle["folderId"])

    /** カテゴリの一覧として開かれたときの、そのカテゴリ。ふつうのフォルダ・お気に入りなら null。 */
    private val category: PhotoCategory? = PhotoCategories.fromFolderId(folderId)

    /** null = まだ読み込み中 */
    private val source = MutableStateFlow<List<MediaItem>?>(null)
    private val options = MutableStateFlow(Options())

    val uiState: StateFlow<FolderDetailUiState> = combine(source, options) { items, opt ->
        if (items == null) {
            FolderDetailUiState(
                sort = opt.sort, filter = opt.filter, columns = opt.columns,
                query = opt.query, isSearching = opt.isSearching,
                isSelecting = opt.isSelecting,
            )
        } else {
            val shown = apply(items, opt)
            // 絞り込み・検索で見えなくなった写真は、選択の数に含めない
            val shownIds = shown.mapTo(HashSet()) { it.id }
            FolderDetailUiState(
                folderName = when {
                    folderId == FavoritesFolderId -> "お気に入り"
                    category != null -> category.displayName
                    else -> items.firstOrNull()?.folderName.orEmpty()
                },
                items = shown,
                isLoading = false,
                sort = opt.sort, filter = opt.filter, columns = opt.columns,
                query = opt.query, isSearching = opt.isSearching,
                isSelecting = opt.isSelecting,
                selectedIds = opt.selectedIds.filterTo(HashSet()) { it in shownIds },
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderDetailUiState())

    /** 「3件をゴミ箱へ移動しました」などの、一度だけ表示するメッセージ。 */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** ゴミ箱が使える端末か(使えないときは、削除ダイアログの文言と動きが変わる)。 */
    val isTrashSupported: Boolean get() = repository.isTrashSupported

    /** お気に入りの写真のID。選択モードの︙メニュー(追加/外す)の表示に使う。 */
    val favoriteIds: StateFlow<Set<Long>> = favoriteRepository.favoriteIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * 最初の読み込みと、削除・ゴミ箱への移動があったときの読み直し。
     * 「お気に入り」の場合は、お気に入りの追加・解除でも読み直す。
     * カテゴリの場合は、ラベルのしきい値(設定)を変えたときも読み直す。
     */
    init {
        viewModelScope.launch {
            when {
                folderId == FavoritesFolderId -> {
                    combine(repository.version, favoriteRepository.favoriteIds) { _, ids -> ids }
                        .collect { ids ->
                            source.value = repository.getAllMedia().filter { it.id in ids }
                        }
                }
                category != null -> {
                    val percent = settingsRepository.settings
                        .map { it.labelConfidencePercent }
                        .distinctUntilChanged()
                    combine(repository.version, percent) { _, p -> p }
                        .collect { p ->
                            // そのカテゴリのラベルが付いた写真(写真のみ。端末から消えた写真は、一覧にないので外れる)
                            val hits = analysisRepository.findByLabels(category.labels, p / 100f)
                            source.value = repository.getAllMedia()
                                .filter { !it.isVideo && it.id in hits }
                        }
                }
                else -> {
                    repository.version.collect {
                        source.value = repository.getMediaInFolder(folderId)
                    }
                }
            }
        }
    }

    fun onSortSelected(sort: FolderMediaSort) = options.update { it.copy(sort = sort) }
    fun onFilterSelected(filter: MediaFilter) = options.update { it.copy(filter = filter) }
    fun onColumnsSelected(columns: Int) = options.update { it.copy(columns = columns) }
    fun onQueryChange(query: String) = options.update { it.copy(query = query) }
    fun onSearchStart() = options.update { it.copy(isSearching = true) }
    fun onSearchClose() = options.update { it.copy(isSearching = false, query = "") }

    // ---- 選択モード ----

    /** 長押し: 選択モードを始めて、その写真を選ぶ。 */
    fun onLongPress(mediaId: Long) = options.update {
        it.copy(isSelecting = true, selectedIds = it.selectedIds + mediaId)
    }

    /** 選択モード中のタップ: 選ぶ/外す。 */
    fun onToggleSelect(mediaId: Long) = options.update {
        val next = if (mediaId in it.selectedIds) it.selectedIds - mediaId else it.selectedIds + mediaId
        it.copy(selectedIds = next)
    }

    /** 「すべて選択」。すでに全部選んでいたら、全部外す(モードは続ける)。 */
    fun onSelectAllToggle() {
        val shownIds = uiState.value.items.mapTo(HashSet()) { it.id }
        options.update {
            val allSelected = shownIds.isNotEmpty() && shownIds.all { id -> id in it.selectedIds }
            it.copy(selectedIds = if (allSelected) emptySet() else it.selectedIds + shownIds)
        }
    }

    /**
     * 選んだ写真を削除する。[permanent] が false ならゴミ箱へ移動、true ならそのまま削除。
     * ゴミ箱が使えない端末では、常にそのまま削除になる。終わったら、選択モードも終える。
     */
    fun deleteSelected(permanent: Boolean) {
        val state = uiState.value
        val targets = state.items.filter { it.id in state.selectedIds }
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

    /**
     * 選んだ写真を、お気に入りに追加する。すでに全部がお気に入りなら、全部外す。
     * 終わったら、選択モードも終える。
     */
    fun toggleFavoriteSelected() {
        val state = uiState.value
        val ids = state.items.filter { it.id in state.selectedIds }.map { it.id }
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
    fun onSelectionClose() = options.update { it.copy(isSelecting = false, selectedIds = emptySet()) }

    /** 絞り込み → 検索 → 並び替え、の順に適用する。 */
    private fun apply(items: List<MediaItem>, opt: Options): List<MediaItem> {
        val filtered = items.filter {
            when (opt.filter) {
                MediaFilter.All -> true
                MediaFilter.PhotosOnly -> !it.isVideo
                MediaFilter.VideosOnly -> it.isVideo
            }
        }
        val searched = if (opt.query.isBlank()) {
            filtered
        } else {
            filtered.filter { it.displayName.contains(opt.query.trim(), ignoreCase = true) }
        }
        return when (opt.sort) {
            FolderMediaSort.NewestFirst -> searched.sortedByDescending { it.dateTakenMillis }
            FolderMediaSort.OldestFirst -> searched.sortedBy { it.dateTakenMillis }
            FolderMediaSort.NameAsc -> searched.sortedBy { it.displayName.lowercase() }
        }
    }
}
