package com.sandolpin.gallery.data.backup

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.sandolpin.gallery.data.local.mediastore.CopyDestination
import com.sandolpin.gallery.data.local.mediastore.FileOperations
import com.sandolpin.gallery.data.local.mediastore.MediaStoreDataSource
import com.sandolpin.gallery.data.repository.FavoriteRepository
import com.sandolpin.gallery.data.repository.PinnedFolderRepository
import com.sandolpin.gallery.data.repository.SettingsRepository
import com.sandolpin.gallery.data.settings.appSettingsFromJson
import com.sandolpin.gallery.data.settings.toJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 設定とお気に入りの、書き出し(ファイルに保存)・読み込み。形式は、JSON(中を見て、直接編集もできる)。
 * ファイルは、Androidの標準のファイル選択で、ユーザーが選んだ場所(Uri)に、読み書きする。
 *
 * - フォルダと写真は、IDではなく、「ファイルの場所」で書き出す(IDは、端末や再インストールで変わるため)。
 *   内部ストレージの中は「DCIM/Camera」のような相対パス、SDカードなどは、絶対パスで書く。
 * - 各関数は、画面に出す、結果のメッセージを返す(失敗したときも、メッセージを返す)。
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val pinnedRepository: PinnedFolderRepository,
    private val favoriteRepository: FavoriteRepository,
    private val dataSource: MediaStoreDataSource,
    private val fileOperations: FileOperations,
) {

    // ---- 設定 ----

    /** 設定(ピン留めしたフォルダの場所を含む)を、[uri] に書き出す。 */
    suspend fun exportSettings(uri: Uri): String = try {
        val settings = settingsRepository.current()
        val pinnedIds = pinnedRepository.pinnedIds.first()
        // ピン留めは、フォルダの場所で書く。場所が分からないフォルダは、除く
        val pinnedPaths = pinnedIds.mapNotNull { id ->
            fileOperations.resolveDirectory(CopyDestination.ExistingFolder(id))
                ?.let { toStoredPath(it.absolutePath) }
        }

        val json = JSONObject()
            .put("type", TypeSettings)
            .put("version", FormatVersion)
            .put("settings", settings.toJson())
            .put("pinnedFolders", JSONArray(pinnedPaths))
        writeText(uri, json.toString(2))
        "設定を書き出しました(ピン留めしたフォルダ ${pinnedPaths.size}件を含む)"
    } catch (e: Exception) {
        Log.w(TAG, "設定の書き出しに失敗", e)
        "設定を書き出せませんでした"
    }

    /**
     * [uri] のファイルから、設定を読み込んで、いまの設定を置き換える。
     * ピン留めしたフォルダは、場所から、いまの端末のフォルダを探して、見つかったものだけを復元する。
     * フォルダが見つからなかったり、復元できなかったりしても、ほかの設定は、読み込む。
     */
    suspend fun importSettings(uri: Uri): String {
        val root = readJson(uri, TypeSettings) ?: return "設定ファイルを読み込めませんでした(このアプリの設定ファイルではありません)"
        val settingsJson = root.optJSONObject("settings") ?: return "設定ファイルに、設定が見つかりませんでした"

        return try {
            settingsRepository.replaceAll(appSettingsFromJson(settingsJson))
            val pinNote = restorePinnedFolders(root.optJSONArray("pinnedFolders"))
            "設定を読み込みました$pinNote"
        } catch (e: Exception) {
            Log.w(TAG, "設定の読み込みに失敗", e)
            "設定を読み込めませんでした"
        }
    }

    /**
     * ピン留めを、ファイルの場所から復元する。結果の説明(メッセージの末尾に付ける文字)を返す。
     * 1つも見つからなかったときは、いまのピン留めを、そのまま残す。
     */
    private suspend fun restorePinnedFolders(paths: JSONArray?): String {
        if (paths == null || paths.length() == 0) return ""
        return try {
            val ids = mutableSetOf<Long>()
            var notFound = 0
            for (index in 0 until paths.length()) {
                val directory = File(toAbsolutePath(paths.optString(index)))
                val folderId = dataSource.queryFolderIdByDirectory(directory)
                if (folderId != null) {
                    ids.add(folderId)
                } else {
                    notFound += 1
                }
            }
            if (ids.isNotEmpty()) pinnedRepository.setPinnedIds(ids)
            when {
                ids.isEmpty() -> "(ピン留めしたフォルダは、見つからなかったため、復元しませんでした)"
                notFound > 0 -> "(ピン留め ${ids.size}件を復元、${notFound}件は、フォルダが見つかりませんでした)"
                else -> "(ピン留め ${ids.size}件を復元)"
            }
        } catch (e: Exception) {
            Log.w(TAG, "ピン留めの復元に失敗", e)
            "(ピン留めは、復元できませんでした)"
        }
    }

    // ---- お気に入り ----

    /** お気に入りを、ファイルの場所で、[uri] に書き出す。 */
    suspend fun exportFavorites(uri: Uri): String = try {
        val all = favoriteRepository.getAll()
        val paths = dataSource.queryPaths(all.map { it.mediaId })

        val array = JSONArray()
        for (favorite in all) {
            val path = paths[favorite.mediaId] ?: continue
            array.put(
                JSONObject()
                    .put("path", toStoredPath(path))
                    .put("addedAt", favorite.addedAtMillis),
            )
        }
        val json = JSONObject()
            .put("type", TypeFavorites)
            .put("version", FormatVersion)
            .put("favorites", array)
        writeText(uri, json.toString(2))

        val skipped = all.size - array.length()
        if (skipped > 0) {
            "お気に入り ${array.length()}件を書き出しました(${skipped}件は、写真が見つからなかったため、除きました)"
        } else {
            "お気に入り ${array.length()}件を書き出しました"
        }
    } catch (e: Exception) {
        Log.w(TAG, "お気に入りの書き出しに失敗", e)
        "お気に入りを書き出せませんでした"
    }

    /**
     * [uri] のファイルから、お気に入りを読み込んで、**追加**する(いまのお気に入りは、残る)。
     * ファイルの場所から、いまの端末の写真を探す。見つからなかった写真は、件数を知らせる。
     */
    suspend fun importFavorites(uri: Uri): String {
        val root = readJson(uri, TypeFavorites) ?: return "お気に入りのファイルを読み込めませんでした(このアプリのファイルではありません)"
        val array = root.optJSONArray("favorites") ?: return "ファイルに、お気に入りが見つかりませんでした"

        return try {
            val paths = (0 until array.length())
                .mapNotNull { index -> array.optJSONObject(index)?.optString("path")?.takeIf { it.isNotEmpty() } }
                .map(::toAbsolutePath)
            val found = dataSource.queryIdsByPaths(paths)
            favoriteRepository.setFavorite(found.values, favorite = true)

            val notFound = paths.size - found.size
            if (notFound > 0) {
                "お気に入り ${found.size}件を追加しました(${notFound}件は、写真が見つかりませんでした)"
            } else {
                "お気に入り ${found.size}件を追加しました"
            }
        } catch (e: Exception) {
            Log.w(TAG, "お気に入りの読み込みに失敗", e)
            "お気に入りを読み込めませんでした"
        }
    }

    // ---- 共通 ----

    /** 内部ストレージの中は、相対パス(DCIM/Camera)。それ以外(SDカードなど)は、絶対パスのまま。 */
    private fun toStoredPath(absolute: String): String =
        if (absolute.startsWith("$primaryRoot/")) absolute.removePrefix("$primaryRoot/") else absolute

    private fun toAbsolutePath(stored: String): String =
        if (stored.startsWith("/")) stored else "$primaryRoot/$stored"

    @Suppress("DEPRECATION")
    private val primaryRoot: String = Environment.getExternalStorageDirectory().absolutePath

    private suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("書き込み先を開けませんでした")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** ファイルを読んで、JSONにする。形式が違う・種類([expectedType])が違うときは、null。 */
    private suspend fun readJson(uri: Uri, expectedType: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext null
            JSONObject(text).takeIf { it.optString("type") == expectedType }
        } catch (e: Exception) {
            Log.w(TAG, "ファイルを読み込めませんでした: $uri", e)
            null
        }
    }

    private companion object {
        const val TAG = "BackupManager"
        const val TypeSettings = "gallery_settings"
        const val TypeFavorites = "gallery_favorites"
        const val FormatVersion = 1
    }
}
