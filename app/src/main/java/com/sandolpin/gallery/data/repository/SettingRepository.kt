package com.sandolpin.gallery.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.sandolpin.gallery.data.settings.AllPhotosLayout
import com.sandolpin.gallery.data.settings.AppSettings
import com.sandolpin.gallery.data.settings.CacheLimit
import com.sandolpin.gallery.data.settings.DeleteMethod
import com.sandolpin.gallery.data.settings.FolderNamePosition
import com.sandolpin.gallery.data.settings.HomeSection
import com.sandolpin.gallery.data.settings.MaxViewerBottomActions
import com.sandolpin.gallery.data.settings.ScrollDirection
import com.sandolpin.gallery.data.settings.ThemeMode
import com.sandolpin.gallery.data.settings.ViewerAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** アプリの設定を、端末内(DataStore)に保存する。 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val PullToRefresh = booleanPreferencesKey("settings_pull_to_refresh")
        val AllPhotosLayout = stringPreferencesKey("settings_all_photos_layout")
        val AllPhotosColumns = intPreferencesKey("settings_all_photos_columns")
        val HideSystemUiInViewer = booleanPreferencesKey("settings_hide_system_ui_in_viewer")
        val EdgeTapNavigation = booleanPreferencesKey("settings_edge_tap_navigation")
        val DeepZoom = booleanPreferencesKey("settings_deep_zoom")
        val RotationGesture = booleanPreferencesKey("settings_rotation_gesture")
        val HighQualityDisplay = booleanPreferencesKey("settings_high_quality_display")
        val ViewerBottomActions = stringSetPreferencesKey("settings_viewer_bottom_actions")
        val ThemeMode = stringPreferencesKey("settings_theme_mode")
        val DynamicColor = booleanPreferencesKey("settings_dynamic_color")
        val DeleteMethod = stringPreferencesKey("settings_delete_method")
        val ConfirmDelete = booleanPreferencesKey("settings_confirm_delete")
        val UseTrash = booleanPreferencesKey("settings_use_trash")
        val ShowTrashInFolders = booleanPreferencesKey("settings_show_trash_in_folders")
        val ShowStorageStats = booleanPreferencesKey("settings_show_storage_stats")
        val HomeSections = stringSetPreferencesKey("settings_home_sections")
        val PinnedColumns = intPreferencesKey("settings_pinned_columns")
        val FolderColumns = intPreferencesKey("settings_folder_columns")
        val FolderNamePosition = stringPreferencesKey("settings_folder_name_position")
        val ScrollDirection = stringPreferencesKey("settings_scroll_direction")
        val ShowFolderCount = booleanPreferencesKey("settings_show_folder_count")
        val ShowFileNameInPhotos = booleanPreferencesKey("settings_show_file_name_in_photos")
        val CacheLimit = stringPreferencesKey("settings_cache_limit")
        val FuzzySearchEnabled = booleanPreferencesKey("settings_fuzzy_search_enabled")
        val TextRecognitionEnabled = booleanPreferencesKey("settings_text_recognition_enabled")
        val LabelConfidencePercent = intPreferencesKey("settings_label_confidence_percent")
    }

    /** いまの設定。変更があるたびに、新しい値が流れる。保存されていない項目は、初期値になる。 */
    val settings: Flow<AppSettings> = dataStore.data
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                pullToRefresh = prefs[Keys.PullToRefresh] ?: defaults.pullToRefresh,
                allPhotosLayout = prefs.enumOf(Keys.AllPhotosLayout, defaults.allPhotosLayout),
                allPhotosColumns = (prefs[Keys.AllPhotosColumns] ?: defaults.allPhotosColumns).coerceIn(2, 5),
                hideSystemUiInViewer = prefs[Keys.HideSystemUiInViewer] ?: defaults.hideSystemUiInViewer,
                edgeTapNavigation = prefs[Keys.EdgeTapNavigation] ?: defaults.edgeTapNavigation,
                deepZoom = prefs[Keys.DeepZoom] ?: defaults.deepZoom,
                rotationGesture = prefs[Keys.RotationGesture] ?: defaults.rotationGesture,
                highQualityDisplay = prefs[Keys.HighQualityDisplay] ?: defaults.highQualityDisplay,
                viewerBottomActions = prefs[Keys.ViewerBottomActions]
                    ?.mapNotNull { name -> ViewerAction.entries.firstOrNull { it.name == name } }
                    ?.sortedBy { it.ordinal }
                    ?.take(MaxViewerBottomActions)
                    ?.toSet()
                    ?: defaults.viewerBottomActions,
                themeMode = prefs.enumOf(Keys.ThemeMode, defaults.themeMode),
                dynamicColor = prefs[Keys.DynamicColor] ?: defaults.dynamicColor,
                deleteMethod = prefs.enumOf(Keys.DeleteMethod, defaults.deleteMethod),
                confirmDelete = prefs[Keys.ConfirmDelete] ?: defaults.confirmDelete,
                useTrash = prefs[Keys.UseTrash] ?: defaults.useTrash,
                showTrashInFolders = prefs[Keys.ShowTrashInFolders] ?: defaults.showTrashInFolders,
                showStorageStats = prefs[Keys.ShowStorageStats] ?: defaults.showStorageStats,
                homeSections = prefs[Keys.HomeSections]
                    ?.mapNotNull { name -> HomeSection.entries.firstOrNull { it.name == name } }
                    ?.toSet()
                    ?: defaults.homeSections,
                pinnedColumns = (prefs[Keys.PinnedColumns] ?: defaults.pinnedColumns).coerceIn(1, 5),
                folderColumns = (prefs[Keys.FolderColumns] ?: defaults.folderColumns).coerceIn(1, 5),
                folderNamePosition = prefs.enumOf(Keys.FolderNamePosition, defaults.folderNamePosition),
                scrollDirection = prefs.enumOf(Keys.ScrollDirection, defaults.scrollDirection),
                showFolderCount = prefs[Keys.ShowFolderCount] ?: defaults.showFolderCount,
                showFileNameInPhotos = prefs[Keys.ShowFileNameInPhotos] ?: defaults.showFileNameInPhotos,
                cacheLimit = prefs.enumOf(Keys.CacheLimit, defaults.cacheLimit),
                fuzzySearchEnabled = prefs[Keys.FuzzySearchEnabled] ?: defaults.fuzzySearchEnabled,
                textRecognitionEnabled = prefs[Keys.TextRecognitionEnabled] ?: defaults.textRecognitionEnabled,
                labelConfidencePercent = (prefs[Keys.LabelConfidencePercent] ?: defaults.labelConfidencePercent)
                    .coerceIn(50, 90),
            )
        }
        .distinctUntilChanged()

    suspend fun setPullToRefresh(value: Boolean) = edit { it[Keys.PullToRefresh] = value }
    suspend fun setAllPhotosLayout(value: AllPhotosLayout) = edit { it[Keys.AllPhotosLayout] = value.name }
    suspend fun setAllPhotosColumns(value: Int) = edit { it[Keys.AllPhotosColumns] = value.coerceIn(2, 5) }
    suspend fun setHideSystemUiInViewer(value: Boolean) = edit { it[Keys.HideSystemUiInViewer] = value }
    suspend fun setEdgeTapNavigation(value: Boolean) = edit { it[Keys.EdgeTapNavigation] = value }
    suspend fun setDeepZoom(value: Boolean) = edit { it[Keys.DeepZoom] = value }
    suspend fun setRotationGesture(value: Boolean) = edit { it[Keys.RotationGesture] = value }
    suspend fun setHighQualityDisplay(value: Boolean) = edit { it[Keys.HighQualityDisplay] = value }
    suspend fun setViewerBottomActions(value: Set<ViewerAction>) =
        edit { it[Keys.ViewerBottomActions] = value.map { action -> action.name }.toSet() }

    /** いまの設定を、1回だけ読む。 */
    suspend fun current(): AppSettings = settings.first()

    /** すべての設定を、[value] に置き換える(設定の読み込みで使う)。 */
    suspend fun replaceAll(value: AppSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.ThemeMode] = value.themeMode.name
            prefs[Keys.DynamicColor] = value.dynamicColor
            prefs[Keys.DeleteMethod] = value.deleteMethod.name
            prefs[Keys.ConfirmDelete] = value.confirmDelete
            prefs[Keys.UseTrash] = value.useTrash
            prefs[Keys.ShowTrashInFolders] = value.showTrashInFolders
            prefs[Keys.PullToRefresh] = value.pullToRefresh
            prefs[Keys.HideSystemUiInViewer] = value.hideSystemUiInViewer
            prefs[Keys.EdgeTapNavigation] = value.edgeTapNavigation
            prefs[Keys.DeepZoom] = value.deepZoom
            prefs[Keys.RotationGesture] = value.rotationGesture
            prefs[Keys.HighQualityDisplay] = value.highQualityDisplay
            prefs[Keys.ViewerBottomActions] = value.viewerBottomActions.map { it.name }.toSet()
            prefs[Keys.ShowStorageStats] = value.showStorageStats
            prefs[Keys.HomeSections] = value.homeSections.map { it.name }.toSet()
            prefs[Keys.AllPhotosLayout] = value.allPhotosLayout.name
            prefs[Keys.AllPhotosColumns] = value.allPhotosColumns.coerceIn(2, 5)
            prefs[Keys.PinnedColumns] = value.pinnedColumns.coerceIn(1, 5)
            prefs[Keys.FolderColumns] = value.folderColumns.coerceIn(1, 5)
            prefs[Keys.FolderNamePosition] = value.folderNamePosition.name
            prefs[Keys.ScrollDirection] = value.scrollDirection.name
            prefs[Keys.ShowFolderCount] = value.showFolderCount
            prefs[Keys.ShowFileNameInPhotos] = value.showFileNameInPhotos
            prefs[Keys.CacheLimit] = value.cacheLimit.name
            prefs[Keys.FuzzySearchEnabled] = value.fuzzySearchEnabled
            prefs[Keys.TextRecognitionEnabled] = value.textRecognitionEnabled
            prefs[Keys.LabelConfidencePercent] = value.labelConfidencePercent.coerceIn(50, 90)
        }
    }

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.ThemeMode] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DynamicColor] = value }
    suspend fun setDeleteMethod(value: DeleteMethod) = edit { it[Keys.DeleteMethod] = value.name }
    suspend fun setConfirmDelete(value: Boolean) = edit { it[Keys.ConfirmDelete] = value }
    suspend fun setUseTrash(value: Boolean) = edit { it[Keys.UseTrash] = value }
    suspend fun setShowTrashInFolders(value: Boolean) = edit { it[Keys.ShowTrashInFolders] = value }
    suspend fun setShowStorageStats(value: Boolean) = edit { it[Keys.ShowStorageStats] = value }
    suspend fun setHomeSections(value: Set<HomeSection>) =
        edit { it[Keys.HomeSections] = value.map { section -> section.name }.toSet() }
    suspend fun setPinnedColumns(value: Int) = edit { it[Keys.PinnedColumns] = value.coerceIn(1, 5) }
    suspend fun setFolderColumns(value: Int) = edit { it[Keys.FolderColumns] = value.coerceIn(1, 5) }
    suspend fun setFolderNamePosition(value: FolderNamePosition) =
        edit { it[Keys.FolderNamePosition] = value.name }

    suspend fun setScrollDirection(value: ScrollDirection) =
        edit { it[Keys.ScrollDirection] = value.name }
    suspend fun setShowFolderCount(value: Boolean) = edit { it[Keys.ShowFolderCount] = value }
    suspend fun setShowFileNameInPhotos(value: Boolean) = edit { it[Keys.ShowFileNameInPhotos] = value }
    suspend fun setCacheLimit(value: CacheLimit) = edit { it[Keys.CacheLimit] = value.name }
    suspend fun setFuzzySearchEnabled(value: Boolean) = edit { it[Keys.FuzzySearchEnabled] = value }
    suspend fun setTextRecognitionEnabled(value: Boolean) = edit { it[Keys.TextRecognitionEnabled] = value }
    suspend fun setLabelConfidencePercent(value: Int) =
        edit { it[Keys.LabelConfidencePercent] = value.coerceIn(50, 90) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    /** 保存された名前から、enumに戻す。見つからなければ(保存がない・名前が変わった)、初期値。 */
    private inline fun <reified E : Enum<E>> Preferences.enumOf(
        key: Preferences.Key<String>,
        default: E,
    ): E = this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
