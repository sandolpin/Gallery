package com.sandolpin.gallery.data.model

import android.net.Uri

/**
 * 写真・動画1件分の情報。
 * MediaStoreから取得できる範囲の情報のみを持つ(EXIFの詳細などは
 * ビューアーで別途読み込む)。
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val folderId: Long,
    val folderName: String,
    val width: Int,
    val height: Int,
    val dateTakenMillis: Long,
    val sizeBytes: Long,
    val durationMillis: Long?, // nullなら写真、値があれば動画
    /**
     * 画像の「版」を表す文字。写真の中身が変わる(回転など)と、値も変わる。
     * 画像の読み込みライブラリ(Coil)が、同じURIの古い画像を使い回さないよう、キャッシュのキーに含める。
     */
    val imageVersion: String = "",
) {
    val isVideo: Boolean get() = durationMillis != null

    /** 縦横比(幅÷高さ)。「すべて」画面のレイアウト計算に使う。 */
    val aspectRatio: Float
        get() = if (height == 0) 1f else width.toFloat() / height.toFloat()
}

/** フォルダ(端末内のディレクトリ)1件分の情報。 */
data class MediaFolder(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val coverUri: Uri?, // 一覧表示用のサムネイル(最新の1枚)
    val latestDateMillis: Long, // 最も新しい写真の日時(「最近更新した順」に使う)
)