package com.sandolpin.gallery.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandolpin.gallery.data.analysis.AnalysisRepository
import com.sandolpin.gallery.data.analysis.LabelTranslations
import com.sandolpin.gallery.data.analysis.PhotoCategories
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.repository.MediaRepository
import com.sandolpin.gallery.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

/** 検索結果の並び順。「信頼度順」は、「写真から検出」だけに効く(ほかのセクションは、新しい順になる)。 */
enum class SearchSort(val label: String) {
    Confidence("信頼度が高い順"),
    NewestFirst("新しい順"),
    OldestFirst("古い順"),
    NameAsc("名前順"),
}

/** 「写真から検出」の1枚分。[confidence] は 0.0〜1.0。 */
data class LabelHit(
    val item: MediaItem,
    val confidence: Float,
)

data class SearchUiState(
    /** 検索語(前後の空白を除いたもの)が入力されているか。 */
    val hasQuery: Boolean = false,
    val isLoading: Boolean = true,
    /** 写真の中身(ラベル)が、検索語に合った写真。 */
    val labelResults: List<LabelHit> = emptyList(),
    /** 写真の中の文字が、検索語に合った写真。 */
    val textResults: List<MediaItem> = emptyList(),
    /** ファイル名が一致した写真・動画。 */
    val fileNameResults: List<MediaItem> = emptyList(),
    val sort: SearchSort = SearchSort.Confidence,
    /** 「あいまい検索」がONか(写真の中身・文字の検索ができるか)。 */
    val fuzzyEnabled: Boolean = false,
    /** 分析が終わっていないときの、お知らせ。終わっていれば null。 */
    val analysisNote: String? = null,
) {
    val hasAnyResult: Boolean
        get() = labelResults.isNotEmpty() || textResults.isNotEmpty() || fileNameResults.isNotEmpty()
}

/** 検索用に、ファイル名を整えたもの(毎回の入力で、整え直さないように、読み込み時に1回だけ作る)。 */
private class IndexedMedia(val item: MediaItem, val normalizedName: String)

/** 検索の元になる一覧。[byId] は、ラベル・文字の結果(写真のID)を、写真に直すのに使う。 */
private class SearchIndex(val list: List<IndexedMedia>, val byId: Map<Long, MediaItem>)

/** 検索に関係する設定だけ(ほかの設定が変わっても、検索をやり直さないため)。 */
private data class SearchSettings(
    val fuzzy: Boolean = false,
    val text: Boolean = false,
    val confidencePercent: Int = 60,
)

/** 分析の進み具合。 */
private data class AnalysisCounts(val analyzed: Int, val recognized: Int)

/**
 * 検索画面。次の3つを、まとめて検索する。
 * - 写真から検出: ML Kitのラベル(「あいまい検索」がONのとき)。日本語の言葉は、対応表とカテゴリで、英語のラベルに直して探す。
 * - 写真内の文字: 文字認識の結果(「写真内の文字も検索する」もONのとき)。
 * - ファイル名: ファイル名の部分一致。
 *
 * - 端末内のすべての写真・動画が対象(ゴミ箱の中は含めない。ラベルと文字は、写真だけ)。
 * - 部分一致。大文字・小文字、全角・半角は区別しない。ひらがなとカタカナは区別する。
 * - 入力が止まって0.2秒後に、結果を更新する。
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val analysisRepository: AnalysisRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    /** 入力欄に表示する文字(入力のたびに、すぐ更新される)。 */
    val query: StateFlow<String> = _query.asStateFlow()

    private val sort = MutableStateFlow(SearchSort.Confidence)

    /** null = まだ読み込み中 */
    private val index = MutableStateFlow<SearchIndex?>(null)

    private val searchSettings: Flow<SearchSettings> = settingsRepository.settings
        .map {
            SearchSettings(
                fuzzy = it.fuzzySearchEnabled,
                text = it.textRecognitionEnabled,
                confidencePercent = it.labelConfidencePercent,
            )
        }
        .distinctUntilChanged()

    private val results: Flow<SearchUiState> = combine(
        _query.debounce(DebounceMillis),
        sort,
        index,
        searchSettings,
    ) { query, sortOrder, indexed, settings ->
        val trimmed = query.trim()
        when {
            indexed == null -> SearchUiState(sort = sortOrder, fuzzyEnabled = settings.fuzzy)
            trimmed.isEmpty() -> SearchUiState(isLoading = false, sort = sortOrder, fuzzyEnabled = settings.fuzzy)
            else -> search(trimmed, sortOrder, indexed, settings)
        }
    }

    /** 分析が終わっていないときの、お知らせの文。進み具合が細かく変わるので、1秒に1回だけ更新する。 */
    private val analysisNote: Flow<String?> = combine(
        combine(analysisRepository.analyzedCount, analysisRepository.recognizedCount) { analyzed, recognized ->
            AnalysisCounts(analyzed, recognized)
        }.sample(NoteIntervalMillis),
        searchSettings,
        index,
    ) { counts, settings, indexed ->
        if (indexed == null || !settings.fuzzy) return@combine null
        val total = indexed.list.count { !it.item.isVideo }
        when {
            counts.analyzed < total ->
                "分析の途中のため、一部の写真は、写真の中身で検索できません(${format(counts.analyzed)} / ${format(total)}枚)"
            settings.text && counts.recognized < total ->
                "文字認識の途中のため、一部の写真は、文字で検索できません(${format(counts.recognized)} / ${format(total)}枚)"
            else -> null
        }
    }.distinctUntilChanged()

    val uiState: StateFlow<SearchUiState> = combine(results, analysisNote) { state, note ->
        state.copy(analysisNote = note)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    /** 最初の読み込みと、削除・名前の変更などがあったときの読み直し。 */
    init {
        viewModelScope.launch {
            repository.version.collect {
                val items = repository.getAllMedia()
                index.value = withContext(Dispatchers.Default) {
                    SearchIndex(
                        list = items.map { IndexedMedia(it, normalize(it.displayName)) },
                        byId = items.associateBy { it.id },
                    )
                }
            }
        }
    }

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun onSortSelected(order: SearchSort) {
        sort.value = order
    }

    private suspend fun search(
        trimmed: String,
        sortOrder: SearchSort,
        indexed: SearchIndex,
        settings: SearchSettings,
    ): SearchUiState {
        val needle = normalize(trimmed)

        val fileNames = indexed.list
            .filter { it.normalizedName.contains(needle) }
            .map { it.item }

        // 写真の中身: 対応表の日本語・英語の名前と、カテゴリの名前(「楽器」など)から、英語のラベルを集めて探す
        val labelHits = if (settings.fuzzy) {
            val labels = LinkedHashSet<String>()
            labels += LabelTranslations.labelsMatching(trimmed)
            PhotoCategories.matching(trimmed).forEach { labels += it.labels }
            analysisRepository.findByLabels(labels, settings.confidencePercent / 100f)
                .mapNotNull { (id, confidence) ->
                    // 写真だけ(端末から消えた写真・ゴミ箱の中の写真は、一覧にないので、ここで外れる)
                    indexed.byId[id]?.takeIf { !it.isVideo }?.let { LabelHit(it, confidence) }
                }
        } else {
            emptyList()
        }

        val textHits = if (settings.fuzzy && settings.text) {
            analysisRepository.findByText(trimmed)
                .mapNotNull { id -> indexed.byId[id]?.takeIf { !it.isVideo } }
        } else {
            emptyList()
        }

        return SearchUiState(
            hasQuery = true,
            isLoading = false,
            labelResults = sortedHits(labelHits, sortOrder),
            textResults = sorted(textHits, sortOrder),
            fileNameResults = sorted(fileNames, sortOrder),
            sort = sortOrder,
            fuzzyEnabled = settings.fuzzy,
        )
    }

    private fun sorted(items: List<MediaItem>, order: SearchSort): List<MediaItem> = when (order) {
        // 信頼度のないセクションでは、新しい順にする
        SearchSort.Confidence, SearchSort.NewestFirst -> items.sortedByDescending { it.dateTakenMillis }
        SearchSort.OldestFirst -> items.sortedBy { it.dateTakenMillis }
        SearchSort.NameAsc -> items.sortedBy { it.displayName.lowercase() }
    }

    private fun sortedHits(hits: List<LabelHit>, order: SearchSort): List<LabelHit> = when (order) {
        // 信頼度が同じときは、新しい順
        SearchSort.Confidence -> hits.sortedWith(
            compareByDescending<LabelHit> { it.confidence }.thenByDescending { it.item.dateTakenMillis },
        )
        else -> sorted(hits.map { it.item }, order).let { items ->
            val byId = hits.associateBy { it.item.id }
            items.mapNotNull { byId[it.id] }
        }
    }

    private fun format(value: Int): String = String.format(Locale.US, "%,d", value)

    private companion object {
        const val DebounceMillis = 200L
        const val NoteIntervalMillis = 1000L
    }
}

/**
 * 検索用に、文字を整える。
 * - 全角・半角をそろえる(「ＩＭＧ」→「IMG」、半角カナ→全角カナ)。NFKCという正規化を使う。
 * - 大文字・小文字をそろえる。
 * ひらがなとカタカナは、変えない(区別する)。
 */
internal fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
