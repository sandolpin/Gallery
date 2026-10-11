package com.sandolpin.gallery.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandolpin.gallery.data.analysis.AnalysisProgress
import com.sandolpin.gallery.data.analysis.AnalysisRepository
import com.sandolpin.gallery.data.analysis.AnalysisScheduler
import com.sandolpin.gallery.data.backup.BackupManager
import com.sandolpin.gallery.data.cache.ThumbnailCache
import com.sandolpin.gallery.data.cache.formatCacheSize
import com.sandolpin.gallery.data.repository.SettingsRepository
import com.sandolpin.gallery.data.settings.AllPhotosLayout
import com.sandolpin.gallery.data.settings.AppSettings
import com.sandolpin.gallery.data.settings.CacheLimit
import com.sandolpin.gallery.data.settings.DeleteMethod
import com.sandolpin.gallery.data.settings.FolderNamePosition
import com.sandolpin.gallery.data.settings.HomeSection
import com.sandolpin.gallery.data.settings.ScrollDirection
import com.sandolpin.gallery.data.settings.ThemeMode
import com.sandolpin.gallery.data.settings.ViewerAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 設定画面。表示する値と、変更の保存を担当する。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val backup: BackupManager,
    private val thumbnailCache: ThumbnailCache,
    private val analysisRepository: AnalysisRepository,
    private val analysisScheduler: AnalysisScheduler,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setPullToRefresh(value: Boolean) = save { repository.setPullToRefresh(value) }
    fun setAllPhotosLayout(value: AllPhotosLayout) = save { repository.setAllPhotosLayout(value) }
    fun setAllPhotosColumns(value: Int) = save { repository.setAllPhotosColumns(value) }
    fun setHideSystemUiInViewer(value: Boolean) = save { repository.setHideSystemUiInViewer(value) }
    fun setEdgeTapNavigation(value: Boolean) = save { repository.setEdgeTapNavigation(value) }
    fun setDeepZoom(value: Boolean) = save { repository.setDeepZoom(value) }
    fun setRotationGesture(value: Boolean) = save { repository.setRotationGesture(value) }
    fun setHighQualityDisplay(value: Boolean) = save { repository.setHighQualityDisplay(value) }
    fun setViewerBottomActions(value: Set<ViewerAction>) = save { repository.setViewerBottomActions(value) }
    fun setThemeMode(value: ThemeMode) = save { repository.setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = save { repository.setDynamicColor(value) }
    fun setDeleteMethod(value: DeleteMethod) = save { repository.setDeleteMethod(value) }
    fun setConfirmDelete(value: Boolean) = save { repository.setConfirmDelete(value) }
    fun setUseTrash(value: Boolean) = save { repository.setUseTrash(value) }
    fun setShowTrashInFolders(value: Boolean) = save { repository.setShowTrashInFolders(value) }
    fun setShowStorageStats(value: Boolean) = save { repository.setShowStorageStats(value) }
    fun setHomeSections(value: Set<HomeSection>) = save { repository.setHomeSections(value) }
    fun setPinnedColumns(value: Int) = save { repository.setPinnedColumns(value) }
    fun setFolderColumns(value: Int) = save { repository.setFolderColumns(value) }
    fun setFolderNamePosition(value: FolderNamePosition) = save { repository.setFolderNamePosition(value) }
    fun setScrollDirection(value: ScrollDirection) = save { repository.setScrollDirection(value) }
    fun setShowFolderCount(value: Boolean) = save { repository.setShowFolderCount(value) }
    fun setShowFileNameInPhotos(value: Boolean) = save { repository.setShowFileNameInPhotos(value) }

    // ---- あいまい検索(写真の分析) ----

    /**
     * ON/OFFの保存だけを行う。裏の分析を動かす/止めるのは、設定の変更を見ている
     * GalleryApplication が行う(設定の読み込みで変わったときも、同じように動く)。
     */
    fun setFuzzySearchEnabled(value: Boolean) = save { repository.setFuzzySearchEnabled(value) }

    /** 写真の中の文字の認識を、ON/OFFする。ONにすると、次の分析で、分析済みの写真も、文字だけ読み取る。 */
    fun setTextRecognitionEnabled(value: Boolean) = save { repository.setTextRecognitionEnabled(value) }

    /** ラベルの信頼度のしきい値(%)を変える。 */
    fun setLabelConfidencePercent(value: Int) = save { repository.setLabelConfidencePercent(value) }

    private val analysisTotal = MutableStateFlow(0)

    /** ラベルが付いた写真の数。しきい値を変えると、数え直される。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val labeledCount = repository.settings
        .map { it.labelConfidencePercent }
        .distinctUntilChanged()
        .flatMapLatest { percent -> analysisRepository.labeledCount(percent / 100f) }

    /** 分析の進み具合(分析済みの枚数・対象の枚数・状態)。 */
    val analysisProgress: StateFlow<AnalysisProgress> = combine(
        analysisRepository.analyzedCount,
        analysisTotal,
        analysisScheduler.status,
        labeledCount,
        analysisRepository.recognizedCount,
    ) { analyzed, total, status, labeled, recognized ->
        AnalysisProgress(analyzed, total, status, labeled, recognized)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisProgress())

    /** 分析の対象の枚数を、数え直す(設定画面を開いたときなどに呼ぶ)。 */
    fun refreshAnalysisTotal() {
        viewModelScope.launch { analysisTotal.value = analysisRepository.countTargets() }
    }

    /** 「今すぐ分析」。 */
    fun startAnalysisNow() = analysisScheduler.startNow()

    // ---- キャッシュ ----

    private val _cacheSizeBytes = MutableStateFlow(0L)

    /** いま保存されているサムネイルの、合計の大きさ(バイト)。 */
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    /** 保存されているサムネイルの大きさを、数え直す(設定画面を開いたときなどに呼ぶ)。 */
    fun refreshCacheSize() {
        viewModelScope.launch { _cacheSizeBytes.value = thumbnailCache.sizeBytes() }
    }

    /** 保持容量を変える。小さくしたときは、超えた分を、すぐに(古いものから)消す。 */
    fun setCacheLimit(value: CacheLimit) {
        viewModelScope.launch {
            repository.setCacheLimit(value)
            thumbnailCache.applyLimit(value.bytes)
            _cacheSizeBytes.value = thumbnailCache.sizeBytes()
        }
    }

    /** サムネイルのキャッシュを、すべて消す。 */
    fun clearCache() = report {
        val freed = thumbnailCache.clear()
        _cacheSizeBytes.value = thumbnailCache.sizeBytes()
        "キャッシュを削除しました(${formatCacheSize(freed)})"
    }

    // ---- 書き出し・読み込み ----

    /** 「設定を書き出しました」などの、一度だけ表示するメッセージ。 */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun exportSettings(uri: Uri) = report { backup.exportSettings(uri) }
    fun importSettings(uri: Uri) = report { backup.importSettings(uri) }
    fun exportFavorites(uri: Uri) = report { backup.exportFavorites(uri) }
    fun importFavorites(uri: Uri) = report { backup.importFavorites(uri) }

    private fun report(block: suspend () -> String) {
        viewModelScope.launch { _messages.emit(block()) }
    }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
