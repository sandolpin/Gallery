package com.sandolpin.gallery.data.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * 設定と、JSON(書き出しファイルの中身)の、相互の変換。
 * 読み込むときは、項目がなかったり、値が正しくなかったりしたら、その項目だけ、初期値にする(ほかの項目は、読み込む)。
 */

/** 設定を、JSONにする。キーは、設定の項目名。選択式の値は、名前(例: "System")で書く。 */
internal fun AppSettings.toJson(): JSONObject = JSONObject().apply {
    put("themeMode", themeMode.name)
    put("dynamicColor", dynamicColor)
    put("deleteMethod", deleteMethod.name)
    put("confirmDelete", confirmDelete)
    put("useTrash", useTrash)
    put("showTrashInFolders", showTrashInFolders)
    put("pullToRefresh", pullToRefresh)
    put("hideSystemUiInViewer", hideSystemUiInViewer)
    put("edgeTapNavigation", edgeTapNavigation)
    put("deepZoom", deepZoom)
    put("rotationGesture", rotationGesture)
    put("highQualityDisplay", highQualityDisplay)
    put("viewerBottomActions", JSONArray(viewerBottomActions.map { it.name }))
    put("showStorageStats", showStorageStats)
    put("homeSections", JSONArray(homeSections.map { it.name }))
    put("allPhotosLayout", allPhotosLayout.name)
    put("allPhotosColumns", allPhotosColumns)
    put("pinnedColumns", pinnedColumns)
    put("folderColumns", folderColumns)
    put("folderNamePosition", folderNamePosition.name)
    put("scrollDirection", scrollDirection.name)
    put("showFolderCount", showFolderCount)
    put("showFileNameInPhotos", showFileNameInPhotos)
    put("cacheLimit", cacheLimit.name)
    put("fuzzySearchEnabled", fuzzySearchEnabled)
    put("textRecognitionEnabled", textRecognitionEnabled)
    put("labelConfidencePercent", labelConfidencePercent)
}

/** JSONから、設定を作る。読めない項目は、[defaults] の値を使う。 */
internal fun appSettingsFromJson(json: JSONObject, defaults: AppSettings = AppSettings()): AppSettings =
    AppSettings(
        themeMode = json.enumOr("themeMode", defaults.themeMode),
        dynamicColor = json.booleanOr("dynamicColor", defaults.dynamicColor),
        deleteMethod = json.enumOr("deleteMethod", defaults.deleteMethod),
        confirmDelete = json.booleanOr("confirmDelete", defaults.confirmDelete),
        useTrash = json.booleanOr("useTrash", defaults.useTrash),
        showTrashInFolders = json.booleanOr("showTrashInFolders", defaults.showTrashInFolders),
        pullToRefresh = json.booleanOr("pullToRefresh", defaults.pullToRefresh),
        hideSystemUiInViewer = json.booleanOr("hideSystemUiInViewer", defaults.hideSystemUiInViewer),
        edgeTapNavigation = json.booleanOr("edgeTapNavigation", defaults.edgeTapNavigation),
        deepZoom = json.booleanOr("deepZoom", defaults.deepZoom),
        rotationGesture = json.booleanOr("rotationGesture", defaults.rotationGesture),
        highQualityDisplay = json.booleanOr("highQualityDisplay", defaults.highQualityDisplay),
        viewerBottomActions = json.enumSetOr("viewerBottomActions", defaults.viewerBottomActions)
            .sortedBy { it.ordinal }
            .take(MaxViewerBottomActions)
            .toSet(),
        showStorageStats = json.booleanOr("showStorageStats", defaults.showStorageStats),
        homeSections = json.enumSetOr("homeSections", defaults.homeSections),
        allPhotosLayout = json.enumOr("allPhotosLayout", defaults.allPhotosLayout),
        allPhotosColumns = json.intOr("allPhotosColumns", defaults.allPhotosColumns).coerceIn(2, 5),
        pinnedColumns = json.intOr("pinnedColumns", defaults.pinnedColumns).coerceIn(1, 5),
        folderColumns = json.intOr("folderColumns", defaults.folderColumns).coerceIn(1, 5),
        folderNamePosition = json.enumOr("folderNamePosition", defaults.folderNamePosition),
        scrollDirection = json.enumOr("scrollDirection", defaults.scrollDirection),
        showFolderCount = json.booleanOr("showFolderCount", defaults.showFolderCount),
        showFileNameInPhotos = json.booleanOr("showFileNameInPhotos", defaults.showFileNameInPhotos),
        cacheLimit = json.enumOr("cacheLimit", defaults.cacheLimit),
        fuzzySearchEnabled = json.booleanOr("fuzzySearchEnabled", defaults.fuzzySearchEnabled),
        textRecognitionEnabled = json.booleanOr("textRecognitionEnabled", defaults.textRecognitionEnabled),
        labelConfidencePercent = json.intOr("labelConfidencePercent", defaults.labelConfidencePercent)
            .coerceIn(50, 90),
    )

private fun JSONObject.booleanOr(key: String, default: Boolean): Boolean =
    if (has(key) && !isNull(key)) optBoolean(key, default) else default

private fun JSONObject.intOr(key: String, default: Int): Int =
    if (has(key) && !isNull(key)) optInt(key, default) else default

private inline fun <reified E : Enum<E>> JSONObject.enumOr(key: String, default: E): E {
    val name = if (has(key) && !isNull(key)) optString(key) else return default
    return enumValues<E>().firstOrNull { it.name == name } ?: default
}

/** 名前の配列から、enumの集合を作る。配列がない場合は、[default]。名前が見つからないものは、無視する。 */
private inline fun <reified E : Enum<E>> JSONObject.enumSetOr(key: String, default: Set<E>): Set<E> {
    val array = optJSONArray(key) ?: return default
    val values = enumValues<E>()
    return (0 until array.length())
        .mapNotNull { index -> values.firstOrNull { it.name == array.optString(index) } }
        .toSet()
}
