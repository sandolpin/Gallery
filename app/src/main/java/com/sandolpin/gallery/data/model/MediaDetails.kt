package com.sandolpin.gallery.data.model

/** 緯度・経度。 */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * 詳細情報ダイアログに出す、一覧の情報(MediaItem)だけでは分からない項目。
 * @param folderPath 保存場所。「内部/DCIM/Camera」のような形。
 * @param location 撮影場所。位置情報がない写真・動画は null。
 */
data class MediaDetails(
    val folderPath: String,
    val location: GeoPoint?,
)