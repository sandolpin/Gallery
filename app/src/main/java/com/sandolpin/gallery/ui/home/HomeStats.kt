package com.sandolpin.gallery.ui.home

import com.sandolpin.gallery.data.local.StorageInfo
import com.sandolpin.gallery.data.model.MediaItem

/**
 * ホーム画面の統計カードに出す数字。
 * @param totalCount 端末内の写真・動画の合計数
 * @param usedPercent ストレージの使用率(0〜100)
 * @param remainingPhotos 空き容量を全部使って撮れる、写真の枚数の目安(10枚単位)
 * @param remainingVideoMinutes 空き容量を全部使って撮れる、動画の時間(分)の目安
 */
data class HomeStats(
    val totalCount: Int,
    val usedPercent: Int,
    val remainingPhotos: Int,
    val remainingVideoMinutes: Int,
)

// ライブラリに写真・動画がまだないときに使う、標準のサイズ
private const val DefaultPhotoBytes = 3L * 1024 * 1024
private const val DefaultVideoBytesPerMinute = 100L * 1024 * 1024

/** 動画の合計時間が、これより短いと、1分あたりのサイズが不安定になるため、標準値を使う。 */
private const val MinVideoMillisForAverage = 60_000L

/**
 * 統計を計算する。
 * - 写真の目安: 空き容量 ÷ 写真1枚の平均サイズ
 * - 動画の目安: 空き容量 ÷ 動画1分あたりの平均サイズ
 * 写真と動画は、どちらも「空き容量の全部を使った場合」の数字(合計ではない)。
 */
fun computeHomeStats(items: List<MediaItem>, storage: StorageInfo): HomeStats {
    var photoCount = 0
    var photoBytes = 0L
    var videoBytes = 0L
    var videoMillis = 0L
    for (item in items) {
        if (item.isVideo) {
            videoBytes += item.sizeBytes
            videoMillis += item.durationMillis ?: 0L
        } else {
            photoCount++
            photoBytes += item.sizeBytes
        }
    }

    val averagePhotoBytes = (if (photoCount > 0) photoBytes / photoCount else 0L)
        .takeIf { it > 0L } ?: DefaultPhotoBytes

    val videoBytesPerMinute = if (videoMillis >= MinVideoMillisForAverage && videoBytes > 0L) {
        (videoBytes.toDouble() / (videoMillis / 60_000.0)).toLong()
    } else {
        DefaultVideoBytesPerMinute
    }.coerceAtLeast(1L)

    val free = storage.freeBytes.coerceAtLeast(0L)
    return HomeStats(
        totalCount = items.size,
        usedPercent = (storage.usedFraction * 100).toInt(),
        remainingPhotos = ((free / averagePhotoBytes) / 10 * 10).toInt(),
        remainingVideoMinutes = (free / videoBytesPerMinute).toInt(),
    )
}