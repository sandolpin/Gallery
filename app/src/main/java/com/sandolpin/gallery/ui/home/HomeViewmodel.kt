package com.sandolpin.gallery.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandolpin.gallery.data.analysis.AnalysisRepository
import com.sandolpin.gallery.data.analysis.PhotoCategories
import com.sandolpin.gallery.data.analysis.PhotoCategory
import com.sandolpin.gallery.data.local.StorageInfo
import com.sandolpin.gallery.data.local.StorageReader
import com.sandolpin.gallery.data.model.MediaFolder
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.repository.FavoriteRepository
import com.sandolpin.gallery.data.repository.MediaRepository
import com.sandolpin.gallery.data.repository.PinnedFolderRepository
import com.sandolpin.gallery.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.random.Random

/** お気に入りを、ホーム画面の横一列に並べる最大数。 */
private const val FavoritePreviewCount = 20

data class HomeUiState(
    val isLoading: Boolean = true,
    val stats: HomeStats? = null,
    /** ピン留めしたフォルダ。 */
    val pinned: List<MediaFolder> = emptyList(),
    /** ホーム画面に並べる、お気に入りの写真(最大20枚)。 */
    val favoritePreview: List<MediaItem> = emptyList(),
    /** ビューアーに渡す、お気に入り全部(新しい順)。 */
    val favorites: List<MediaItem> = emptyList(),
    /** 「いろいろな写真を見る」のカルーセル。 */
    val pickup: List<CarouselEntry> = emptyList(),
    /** 「いろいろな写真」で選ばれた、カテゴリ(「食べ物」など)。分析がまだなどで、カテゴリ分けできないときは null。 */
    val pickupCategory: PhotoCategory? = null,
    /** 「昔に撮った写真を見る」のカルーセル。該当がなければ空(欄ごと非表示)。 */
    val memories: List<CarouselEntry> = emptyList(),
)

/** カテゴリ分けできないときの、「いろいろな写真」の枚数。 */
private const val PickupCount = 10

/** カテゴリ分けできないときの、「いろいろな写真」のラベル。 */
private const val PickupLabel = "ピックアップ"

/** 「昔に撮った写真」: 今日の前後、何日までを「同じ日」とみなすか。 */
private const val MemoryDayRange = 3L

/** 「昔に撮った写真」: 1年あたりの最大枚数と、全体の最大枚数。 */
private const val MemoryPerYear = 3
private const val MemoryMax = 15

/** 一覧を読み込んだときに、1回だけ計算する部分(お気に入り・ピン留めの変更では計算し直さない)。 */
private data class Base(
    val items: List<MediaItem>,
    val folders: List<MediaFolder>,
    val stats: HomeStats,
    val memories: List<CarouselEntry>,
)

/**
 * 「いろいろな写真」の選び直しの依頼。[seed] が同じなら、同じカテゴリ・同じ写真になる(一覧を読み直しても、変わらない)。
 * [avoid] は、入れ替えるときに、同じカテゴリが続かないよう、後回しにするカテゴリ。
 */
private data class PickupRequest(val seed: Long, val avoid: PhotoCategory?)

/** 「いろいろな写真」に使う設定(ほかの設定が変わっても、選び直さないため)。 */
private data class PickupSettings(val fuzzy: Boolean, val confidencePercent: Int)

private data class PickupResult(
    val category: PhotoCategory? = null,
    val entries: List<CarouselEntry> = emptyList(),
)

/**
 * ホーム画面。写真の一覧を1回だけ読み込んで、統計とフォルダの両方を作る。
 * 削除・ゴミ箱への移動があったときは、読み直す。
 *
 * 「いろいろな写真」は、アプリを開くたびに、カテゴリをランダムに1つ選ぶ。下に引っ張って更新すると、別のカテゴリに入れ替わる。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val analysisRepository: AnalysisRepository,
    settingsRepository: SettingsRepository,
    pinnedRepository: PinnedFolderRepository,
    favoriteRepository: FavoriteRepository,
) : ViewModel() {

    /** null = まだ読み込み中 */
    private val allMedia = MutableStateFlow<List<MediaItem>?>(null)
    private val storage = MutableStateFlow<StorageInfo?>(null)

    private val pickupRequest = MutableStateFlow(PickupRequest(seed = Random.nextLong(), avoid = null))

    private val pickupSettings: Flow<PickupSettings> = settingsRepository.settings
        .map { PickupSettings(fuzzy = it.fuzzySearchEnabled, confidencePercent = it.labelConfidencePercent) }
        .distinctUntilChanged()

    private val base = combine(allMedia, storage) { items, storageInfo ->
        if (items == null || storageInfo == null) {
            null
        } else {
            Base(
                items = items,
                folders = mediaRepository.foldersOf(items),
                stats = computeHomeStats(items, storageInfo),
                memories = buildMemories(items, LocalDate.now(), ZoneId.systemDefault()),
            )
        }
    }

    private val pickupResult: Flow<PickupResult> = combine(
        allMedia,
        pickupRequest,
        pickupSettings,
    ) { items, request, settings ->
        if (items == null) PickupResult() else buildPickup(items, request, settings)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        base,
        pinnedRepository.pinnedIds,
        favoriteRepository.favoriteIds,
        pickupResult,
    ) { base, pinnedIds, favoriteIds, pickup ->
        if (base == null) {
            HomeUiState()
        } else {
            // 一覧は新しい順なので、絞り込むだけで、お気に入りも新しい順になる
            val favorites = if (favoriteIds.isEmpty()) {
                emptyList()
            } else {
                base.items.filter { it.id in favoriteIds }
            }
            HomeUiState(
                isLoading = false,
                stats = base.stats,
                pinned = base.folders.filter { it.id in pinnedIds },
                favoritePreview = favorites.take(FavoritePreviewCount),
                favorites = favorites,
                pickup = pickup.entries,
                pickupCategory = pickup.category,
                memories = base.memories,
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** 最初の読み込みと、削除・ゴミ箱への移動があったときの読み直し。 */
    init {
        viewModelScope.launch {
            mediaRepository.version.collect {
                allMedia.value = mediaRepository.getAllMedia()
                storage.value = StorageReader.read()
            }
        }
    }

    /** 「いろいろな写真」を、別のカテゴリに入れ替える(下に引っ張って更新したとき)。 */
    fun rerollPickup() {
        pickupRequest.value = PickupRequest(
            seed = Random.nextLong(),
            avoid = uiState.value.pickupCategory,
        )
    }

    /**
     * 「いろいろな写真」: 「あいまい検索」がONで、写真が [PhotoCategories.MinPhotosForHome] 枚以上あるカテゴリの中から、
     * 1つをランダムに選んで、その写真を並べる。
     * 選べるカテゴリがないとき(分析がまだ、など)は、写真全体から、ランダムに選ぶ。
     */
    private suspend fun buildPickup(
        items: List<MediaItem>,
        request: PickupRequest,
        settings: PickupSettings,
    ): PickupResult {
        val photos = items.filter { !it.isVideo }
        val random = Random(request.seed)

        if (settings.fuzzy) {
            val byId = photos.associateBy { it.id }
            val shuffled = PhotoCategory.entries.shuffled(random)
            val avoid = request.avoid
            // 入れ替えのときは、いまのカテゴリを、いちばん最後にする
            val order = if (avoid != null) shuffled.filter { it != avoid } + avoid else shuffled
            for (category in order) {
                val hits = analysisRepository.findByLabels(category.labels, settings.confidencePercent / 100f)
                val candidates = hits.keys.mapNotNull { byId[it] }
                if (candidates.size >= PhotoCategories.MinPhotosForHome) {
                    return PickupResult(
                        category = category,
                        entries = candidates.shuffled(random)
                            .take(PhotoCategories.HomePickupCount)
                            .map { CarouselEntry(media = it, label = category.displayName) },
                    )
                }
            }
        }

        return PickupResult(
            category = null,
            entries = photos.shuffled(random)
                .take(PickupCount)
                .map { CarouselEntry(media = it, label = PickupLabel) },
        )
    }
}

private class MemoryCandidate(val yearsAgo: Int, val dayGap: Long, val media: MediaItem)

/**
 * 「昔に撮った写真」: 今日と同じ日付(前後 [MemoryDayRange] 日を含む)の、過去の年の写真。
 * 近い年から並べ、1年あたり [MemoryPerYear] 枚(今日に近い日付を優先)、全体で [MemoryMax] 枚まで。
 * ラベルは、その写真の「N年前」。該当がなければ、空のリスト。
 */
private fun buildMemories(
    items: List<MediaItem>,
    today: LocalDate,
    zone: ZoneId,
): List<CarouselEntry> {
    val candidates = ArrayList<MemoryCandidate>()
    for (item in items) {
        if (item.isVideo) continue
        val date = Instant.ofEpochMilli(item.dateTakenMillis).atZone(zone).toLocalDate()
        val yearsAgo = today.year - date.year
        if (yearsAgo < 1) continue
        // 撮影した年の、今日と同じ月日(2月29日は、その年に無ければ28日)との差
        val gap = abs(ChronoUnit.DAYS.between(date, today.withYear(date.year)))
        if (gap <= MemoryDayRange) candidates += MemoryCandidate(yearsAgo, gap, item)
    }
    return candidates
        .groupBy { it.yearsAgo }
        .toSortedMap()
        .values
        .flatMap { year -> year.sortedBy { it.dayGap }.take(MemoryPerYear) }
        .take(MemoryMax)
        .map { CarouselEntry(media = it.media, label = "${it.yearsAgo}年前") }
}
