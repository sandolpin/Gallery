package com.sandolpin.gallery.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import com.sandolpin.gallery.data.cache.ThumbnailCache
import com.sandolpin.gallery.data.model.MediaItem

/**
 * 写真・動画を表示するための、画像の読み込みリクエスト。
 * 画像の「版」([MediaItem.imageVersion])をキャッシュのキーに含めるので、
 * 回転などで写真の中身が変わると、古い画像を使い回さずに、読み込み直す。
 * 写真を表示する画面(一覧・ホーム・ビューアーなど)では、`model = item.uri` の代わりに、これを使う。
 *
 * 小さく表示するとき(一覧など)は、ディスクのサムネイルキャッシュ([ThumbnailCache])も使う。
 * 大きく表示するとき(ビューアーなど)は、自動的に、使わない。
 *
 * @param sizePx 読み込む大きさ(px)。指定しなければ、表示される大きさに合わせる。
 *   カルーセルのように、表示の大きさが変わり続けるところでは、固定すると、読み込み直しが減る。
 */
@Composable
fun rememberMediaImageRequest(item: MediaItem, sizePx: Int? = null): ImageRequest {
    val context = LocalContext.current
    return remember(item.uri, item.id, item.imageVersion, sizePx) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .memoryCacheKey("${item.uri}#${item.imageVersion}")
            // ディスクのサムネイルの名前。写真のID + 版(中身が変わったら、別の名前になる)
            // 3つめの引数を null にして、メモリキャッシュのキーには、含めない(上で決めたキーを使う)
            .setParameter(ThumbnailCache.KeyParameter, "${item.id}_${item.imageVersion}", null)
            .apply { if (sizePx != null) size(sizePx) }
            .build()
    }
}
