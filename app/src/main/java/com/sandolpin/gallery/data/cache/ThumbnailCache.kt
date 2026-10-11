package com.sandolpin.gallery.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.sandolpin.gallery.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 写真・動画のサムネイルを、端末のキャッシュ用の場所(cacheDir/thumbs)に、小さな画像(WebP)として保存しておく。
 * 次に表示するときは、元の写真を読み込み直さず、この小さな画像を読むので、速くなる。
 *
 * - 保存できる合計の大きさ(保持容量)は、設定で決まる。超えたら、「最後に使った日時」が古いものから消す。
 * - 見つけるための名前は、「写真のID + 版」から作る。写真の中身が変わる(回転など)と、版が変わるので、新しく作り直される。
 * - Androidは、端末の容量が足りなくなると、このキャッシュを自動で消すことがある(消えても、作り直されるだけ)。
 */
@Singleton
class ThumbnailCache @Inject constructor(
    @ApplicationContext context: Context,
    settingsRepository: SettingsRepository,
) {
    private val dir = File(context.cacheDir, "thumbs")

    /** 書き込みや、容量の調整を、画面とは別に行うための場所。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 「ファイルの追加」「消す」「合計の計算」が、同時に走らないようにする。 */
    private val mutex = Mutex()

    /** 保持容量(バイト)。設定が変わったら、更新される。 */
    @Volatile
    private var limitBytes: Long = DefaultLimitBytes

    /** 保存されているファイルの合計(バイト)。-1 は、まだ数えていない状態。[mutex] の中だけで触る。 */
    private var totalBytes: Long = -1L

    init {
        // アプリの起動時に、保存してある設定の保持容量を読み込む。変わるたびに、容量を超えた分を消す
        scope.launch {
            settingsRepository.settings
                .map { it.cacheLimit.bytes }
                .distinctUntilChanged()
                .collect { bytes -> applyLimit(bytes) }
        }
    }

    /** 保持容量を [bytes] にして、超えている分を、すぐに消す(古いものから)。 */
    suspend fun applyLimit(bytes: Long) {
        limitBytes = bytes
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureScanned()
                trimLocked()
            }
        }
    }

    /** [key] のサムネイルがあれば、そのファイルを返す(なければ null)。使った日時を、いまにする。 */
    suspend fun get(key: String): File? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (file.isFile && file.length() > 0L) {
            file.setLastModified(System.currentTimeMillis())
            file
        } else {
            null
        }
    }

    /** [bitmap] を、サムネイルとして保存する。画面を待たせないよう、裏で行う。 */
    fun putInBackground(key: String, bitmap: Bitmap) {
        scope.launch { put(key, bitmap) }
    }

    private suspend fun put(key: String, bitmap: Bitmap) {
        // 保持容量が、いちばん小さい設定より小さくなることはないが、念のため、0以下なら保存しない
        if (limitBytes <= 0L) return
        val target = fileFor(key)
        val temp = File(dir, "${target.name}.${System.nanoTime()}.tmp")
        try {
            dir.mkdirs()
            // ハードウェアビットマップは、そのままでは書き出せないことがあるため、コピーしてから書く
            val source = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return
            } else {
                bitmap
            }
            try {
                temp.outputStream().use { out -> source.compress(webpFormat(), Quality, out) }
            } finally {
                if (source !== bitmap) source.recycle()
            }
            mutex.withLock {
                ensureScanned()
                val oldLength = if (target.exists()) target.length() else 0L
                // 書きかけのファイルを読まれないよう、完成してから、名前を付け替える
                if (!temp.renameTo(target)) {
                    temp.delete()
                    return
                }
                totalBytes += target.length() - oldLength
                trimLocked()
            }
        } catch (e: Exception) {
            Log.w(TAG, "サムネイルの保存に失敗", e)
            temp.delete()
        }
    }

    /** すべてのサムネイルを消す。消した合計の大きさ(バイト)を返す。 */
    suspend fun clear(): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            var freed = 0L
            dir.listFiles()?.forEach { file ->
                val length = file.length()
                if (file.delete()) freed += length
            }
            totalBytes = 0L
            freed
        }
    }

    /** いま保存されているサムネイルの、合計の大きさ(バイト)。 */
    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            // OSが勝手に消すこともあるため、毎回、数え直す
            totalBytes = scan()
            totalBytes
        }
    }

    // ---- 内部 ----

    private fun fileFor(key: String): File = File(dir, "${hash(key)}$Extension")

    /** [mutex] の中で呼ぶ。まだ合計を数えていなければ、数える。 */
    private fun ensureScanned() {
        if (totalBytes < 0L) totalBytes = scan()
    }

    /** フォルダの中を数える。書きかけのまま残った一時ファイルは、消す。 */
    private fun scan(): Long {
        var total = 0L
        dir.listFiles()?.forEach { file ->
            when {
                file.name.endsWith(Extension) -> total += file.length()
                file.name.endsWith(TempExtension) -> file.delete()
            }
        }
        return total
    }

    /**
     * [mutex] の中で呼ぶ。保持容量を超えていたら、「最後に使った日時」が古いものから、
     * 保持容量の 90% になるまで消す(少し余裕を持たせて、毎回は消さないようにする)。
     */
    private fun trimLocked() {
        val limit = limitBytes
        if (totalBytes <= limit) return
        val goal = (limit * 0.9).toLong()
        val files = dir.listFiles { file -> file.name.endsWith(Extension) }
            ?.sortedBy { it.lastModified() }
            ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= goal) break
            val length = file.length()
            if (file.delete()) total -= length
        }
        totalBytes = total
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it) }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    companion object {
        private const val TAG = "ThumbnailCache"
        private const val Extension = ".webp"
        private const val TempExtension = ".tmp"
        private const val Quality = 80
        private const val DefaultLimitBytes = 500L * 1024 * 1024

        /** 保存するサムネイルの大きさ(長いほうの辺、px)。 */
        const val ThumbnailPx = 480

        /**
         * この大きさ(px)より大きく表示する画像は、キャッシュを使わない(ぼやけてしまうため)。
         * ビューアーの大きな画像などが、これにあたる。
         */
        const val MaxRequestPx = 600

        /** Coilのリクエストに、サムネイルの名前を渡すための、パラメーターの名前。 */
        const val KeyParameter = "thumbnail_cache_key"
    }
}

/** 大きさ(バイト)を、「12.3 MB」のような、読みやすい文字にする。 */
fun formatCacheSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024L -> String.format(Locale.US, "%d KB", bytes / 1024)
    else -> "$bytes B"
}
