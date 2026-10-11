package com.sandolpin.gallery.data

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache
import com.sandolpin.gallery.data.analysis.AnalysisScheduler
import com.sandolpin.gallery.data.cache.ThumbnailCache
import com.sandolpin.gallery.data.cache.ThumbnailCacheInterceptor
import com.sandolpin.gallery.data.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GalleryApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    /** サムネイルのディスクキャッシュ(設定の「キャッシュの保持容量」に従う)。 */
    @Inject
    lateinit var thumbnailCache: ThumbnailCache

    /** 裏で動く処理(写真の分析)の中で、HiltでつないだクラスをWorkerに渡すための工場。 */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var analysisScheduler: AnalysisScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 設定の「あいまい検索」に合わせて、裏の分析を、動かす/止める(起動時と、設定が変わったとき)
        appScope.launch {
            settingsRepository.settings
                .map { it.fuzzySearchEnabled }
                .distinctUntilChanged()
                .collect { enabled -> analysisScheduler.apply(enabled) }
        }
    }

    /** WorkManager(裏で動く処理)の設定。Hiltの工場を使う。 */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * アプリ全体で使う画像読み込みの設定。
     * - 動画のサムネイル(先頭付近の1コマ)を表示できるよう、動画用のデコーダーを追加する。
     * - 小さく表示する写真・動画は、ディスクのサムネイルキャッシュを使う([ThumbnailCacheInterceptor])。
     */
    override fun newImageLoader(): ImageLoader {
        val memoryCache = MemoryCache.Builder(this).build()
        return ImageLoader.Builder(this)
            .memoryCache(memoryCache)
            .components {
                add(VideoFrameDecoder.Factory())
                add(ThumbnailCacheInterceptor(thumbnailCache, memoryCache))
            }
            .crossfade(true)
            .build()
    }
}
