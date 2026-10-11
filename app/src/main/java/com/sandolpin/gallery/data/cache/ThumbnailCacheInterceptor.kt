package com.sandolpin.gallery.data.cache

import coil.intercept.Interceptor
import coil.memory.MemoryCache
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.Dimension
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import android.graphics.drawable.BitmapDrawable

/**
 * 画像を読み込むときに、サムネイルのキャッシュ([ThumbnailCache])を使うための、Coilの割り込み処理。
 *
 * 動き方:
 * 1. 画面のメモリに、すでにあれば、何もしない(Coilに任せる)。
 * 2. ディスクに、サムネイルがあれば、元の写真の代わりに、それを読む(速い)。
 * 3. なければ、元の写真から、小さなサイズ([ThumbnailCache.ThumbnailPx])で読み込んで表示し、
 *    その画像を、裏でディスクに保存する(次からは、2.になる)。
 *
 * 対象は、リクエストに [ThumbnailCache.KeyParameter] が付いていて、表示する大きさが小さい([ThumbnailCache.MaxRequestPx] 以下)ものだけ。
 * それ以外(ビューアーの大きな画像など)は、いつもどおり、元の写真から読み込む。
 */
class ThumbnailCacheInterceptor(
    private val cache: ThumbnailCache,
    private val memoryCache: MemoryCache,
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val key = request.parameters.value(ThumbnailCache.KeyParameter) as? String
            ?: return chain.proceed(request)

        // 大きく表示する画像には、使わない(サムネイルでは、ぼやけてしまう)
        if (!isSmall(chain.size)) return chain.proceed(request)

        // 1. 画面のメモリにあるなら、そのまま(ディスクを調べない)
        val memoryKey = request.memoryCacheKey
        // (request.memoryCacheKey は、文字ではなく、MemoryCache.Key という型なので、そのまま渡す)
        if (memoryKey != null && memoryCache[memoryKey] != null) {
            return chain.proceed(request)
        }

        // 2. ディスクに、サムネイルがあるなら、それを読む
        val cached = cache.get(key)
        if (cached != null) {
            val result = chain.proceed(request.newBuilder().data(cached).build())
            if (result is SuccessResult) return result
            // 読めなかったとき(壊れたファイルなど)は、元の写真から読む。次の保存で、上書きされる
        }

        // 3. 元の写真から、サムネイルの大きさで読み込む
        val thumbnailRequest = request.newBuilder()
            .scale(Scale.FIT)
            .precision(Precision.EXACT)
            .build()
        val result = chain.withSize(Size(ThumbnailCache.ThumbnailPx, ThumbnailCache.ThumbnailPx))
            .proceed(thumbnailRequest)
        if (result is SuccessResult) {
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) cache.putInBackground(key, bitmap)
        }
        return result
    }

    /** 表示する大きさが、サムネイル向きか(ピクセルで決まっていて、小さいか)。 */
    private fun isSmall(size: Size): Boolean {
        val width = (size.width as? Dimension.Pixels)?.px
        val height = (size.height as? Dimension.Pixels)?.px
        // 大きさが決まっていない(元の大きさのまま)ものは、対象にしない
        if (width == null && height == null) return false
        return maxOf(width ?: 0, height ?: 0) <= ThumbnailCache.MaxRequestPx
    }
}
