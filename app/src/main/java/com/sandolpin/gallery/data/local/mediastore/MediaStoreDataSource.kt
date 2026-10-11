package com.sandolpin.gallery.data.local.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import com.sandolpin.gallery.data.model.MediaFolder
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.model.RenameResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaStoreから写真・動画の一覧を取得する。
 * 画像本体は読み込まず、メタデータ(幅・高さ・撮影日時など)だけを取得するため、
 * 1万枚規模でも比較的高速に動作する。
 */
@Singleton
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 端末内のすべての写真・動画を、撮影日時の新しい順で取得する(ゴミ箱の中身は含まない)。 */
    suspend fun queryAllMedia(): List<MediaItem> = queryMedia(trashedOnly = false)

    /**
     * 写真の中身をアプリが書き換えた(回転など)ときの印。写真のID → 印を付けた時刻。
     * MediaStoreの値(世代・サイズ)が変わらなくても、確実に、画像を読み込み直させるために使う。
     * (アプリを再起動すると、画像のキャッシュも消えるため、覚えておく必要はない)
     */
    private val changeMarks = ConcurrentHashMap<Long, Long>()

    /** 写真の中身が変わったことを、印として覚える。次に一覧を読み込むと、「版」が変わる。 */
    fun markImagesChanged(ids: Collection<Long>) {
        val now = System.currentTimeMillis()
        ids.forEach { changeMarks[it] = now }
    }

    /** ゴミ箱の中の写真・動画を取得する。Android 11(API 30)未満には、ゴミ箱がないため空になる。 */
    suspend fun queryTrashedMedia(): List<MediaItem> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) queryMedia(trashedOnly = true) else emptyList()

    private suspend fun queryMedia(trashedOnly: Boolean): List<MediaItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri("external")

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 向き(回転角度)。縦向きで撮った写真の縦横を正しく扱うために取得する
            arrayOf(MediaStore.MediaColumns.ORIENTATION)
        } else {
            emptyArray<String>()
        } + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 最後に変更された世代。写真の中身が変わったことを、画像の「版」に反映するために取得する
            arrayOf(MediaStore.MediaColumns.GENERATION_MODIFIED)
        } else {
            emptyArray<String>()
        }

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        // 撮影日時が空のもの(スクリーンショットなど)は更新日時で代用するため、
        // 並び替えはクエリではなく、取得後にKotlin側で行う

        val result = mutableListOf<MediaItem>()

        // ゴミ箱の中身は、通常の取得では含まれない。trashedOnly のときだけ「ゴミ箱の中だけ」を指定する
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            if (trashedOnly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            }
        }

        context.contentResolver.query(
            collection, projection, queryArgs, null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
            val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
            val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            // API 28以下では存在しないため、無い場合は -1 になる
            val orientationCol = cursor.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
            // API 29以下では存在しないため、無い場合は -1 になる
            val generationCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.MediaColumns.GENERATION_MODIFIED)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val mediaType = cursor.getInt(typeCol)
                val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO

                val uri = ContentUris.withAppendedId(
                    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id,
                )

                // スクリーンショットなど撮影日時がない場合は更新日時で代用する
                val dateTaken = cursor.getLong(dateTakenCol).takeIf { it > 0 }
                    ?: (cursor.getLong(dateModifiedCol) * 1000L)

                // 90度・270度回転している場合は、縦横を入れ替える
                val rotation = if (orientationCol >= 0) cursor.getInt(orientationCol) else 0
                val rawWidth = cursor.getInt(widthCol)
                val rawHeight = cursor.getInt(heightCol)
                val swap = rotation == 90 || rotation == 270

                val sizeBytes = cursor.getLong(sizeCol)
                val generation = if (generationCol >= 0) cursor.getLong(generationCol) else 0L
                // アプリ内で「回転した」と印を付けた写真は、その時刻も「版」に含める
                val changeMark = changeMarks[id]?.let { "_r$it" }.orEmpty()

                result += MediaItem(
                    id = id,
                    uri = uri,
                    displayName = cursor.getString(nameCol) ?: "",
                    folderId = cursor.getLong(bucketIdCol),
                    folderName = cursor.getString(bucketNameCol) ?: "",
                    width = if (swap) rawHeight else rawWidth,
                    height = if (swap) rawWidth else rawHeight,
                    dateTakenMillis = dateTaken,
                    sizeBytes = sizeBytes,
                    durationMillis = if (isVideo) cursor.getLong(durationCol) else null,
                    imageVersion = "${generation}_${sizeBytes}$changeMark",
                )
            }
        }

        result.sortByDescending { it.dateTakenMillis }
        result
    }

    /** フォルダごとに集計する。件数の多い順に並べる。 */
    suspend fun queryFolders(): List<MediaFolder> = withContext(Dispatchers.IO) {
        buildFolders(queryAllMedia())
    }

    /**
     * 読み込み済みの一覧から、フォルダごとの集計を作る(件数の多い順)。
     * ホーム画面のように、一覧を1回だけ読み込んで、統計とフォルダの両方に使いたいときに使う。
     */
    fun buildFolders(all: List<MediaItem>): List<MediaFolder> =
        all.groupBy { it.folderId }
            .map { (folderId, items) ->
                val latest = items.maxByOrNull { it.dateTakenMillis }
                MediaFolder(
                    id = folderId,
                    name = items.first().folderName,
                    itemCount = items.size,
                    coverUri = latest?.uri,
                    latestDateMillis = latest?.dateTakenMillis ?: 0L,
                )
            }
            .sortedByDescending { it.itemCount }

    // ---- 書き出し・読み込み(バックアップ)用 ----

    /**
     * 写真のID → ファイルの保存先のパス。見つからなかったIDは、結果に入らない。
     * SQLiteの変数の上限(999)があるため、500件ずつに分けて調べる。
     */
    @Suppress("DEPRECATION")
    suspend fun queryPaths(ids: Collection<Long>): Map<Long, String> = withContext(Dispatchers.IO) {
        val result = HashMap<Long, String>()
        if (ids.isEmpty()) return@withContext result
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
        )
        ids.toList().chunked(QueryChunkSize).forEach { chunk ->
            try {
                val placeholders = chunk.joinToString(",") { "?" }
                val selection = "${MediaStore.Files.FileColumns._ID} IN ($placeholders)"
                val args = chunk.map { it.toString() }.toTypedArray()
                context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                    while (c.moveToNext()) {
                        val path = c.getString(dataCol) ?: continue
                        result[c.getLong(idCol)] = path
                    }
                }
            } catch (e: Exception) {
                // 1回分の失敗で、残りを止めない
                Log.w(TAG, "パスの取得に失敗", e)
            }
        }
        result
    }

    /**
     * ファイルの保存先のパス → 写真のID。見つからなかったパスは、結果に入らない。
     * 写真・動画だけを対象にする。500件ずつに分けて調べる。
     */
    @Suppress("DEPRECATION")
    suspend fun queryIdsByPaths(paths: Collection<String>): Map<String, Long> = withContext(Dispatchers.IO) {
        val result = HashMap<String, Long>()
        if (paths.isEmpty()) return@withContext result
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
        )
        paths.distinct().chunked(QueryChunkSize).forEach { chunk ->
            try {
                val placeholders = chunk.joinToString(",") { "?" }
                val selection = "${MediaStore.Files.FileColumns.DATA} IN ($placeholders) AND " +
                        "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR " +
                        "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
                context.contentResolver.query(collection, projection, selection, chunk.toTypedArray(), null)?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                    while (c.moveToNext()) {
                        val path = c.getString(dataCol) ?: continue
                        result[path] = c.getLong(idCol)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "IDの取得に失敗", e)
            }
        }
        result
    }

    /**
     * フォルダ([directory])の、フォルダID(BUCKET_ID)。そのフォルダ直下に、写真・動画が1つもなければ null。
     * (サブフォルダの中の写真は、数えない)
     */
    @Suppress("DEPRECATION")
    suspend fun queryFolderIdByDirectory(directory: File): Long? = withContext(Dispatchers.IO) {
        try {
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Files.FileColumns.BUCKET_ID)
            val base = escapeLike(directory.absolutePath.trimEnd('/'))
            // 「base/〜」にあてはまり、さらに「base/〜/〜」にはあてはまらない = 直下のファイルだけ
            val selection = "${MediaStore.Files.FileColumns.DATA} LIKE ? ESCAPE '\\' AND " +
                    "${MediaStore.Files.FileColumns.DATA} NOT LIKE ? ESCAPE '\\' AND " +
                    "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR " +
                    "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            val args = arrayOf("$base/%", "$base/%/%")
            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "フォルダIDの取得に失敗: $directory", e)
            null
        }
    }

    /** LIKEで特別な意味を持つ文字(\ % _)を、普通の文字として扱うよう、目印(\)を付ける。 */
    private fun escapeLike(text: String): String =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /**
     * ゴミ箱へ移動する(Android 11以上)。成功した件数を返す。
     * 「すべてのファイルへのアクセス」権限があれば、OSの確認ダイアログは出ない想定。
     */
    suspend fun trash(items: List<MediaItem>): Int = setTrashed(items, trashed = true)

    /** ゴミ箱から元に戻す。成功した件数を返す。 */
    suspend fun restore(items: List<MediaItem>): Int = setTrashed(items, trashed = false)

    private suspend fun setTrashed(items: List<MediaItem>, trashed: Boolean): Int =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext 0
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_TRASHED, if (trashed) 1 else 0)
            }
            var success = 0
            items.forEach { item ->
                try {
                    if (context.contentResolver.update(item.uri, values, null, null) > 0) success++
                } catch (e: Exception) {
                    // 権限がない・ファイルがない場合など。1件の失敗で、残りを止めない
                    Log.w(TAG, "ゴミ箱の操作に失敗: ${item.uri}", e)
                }
            }
            success
        }

    /** ゴミ箱を通さず、完全に削除する。成功した件数を返す。 */
    suspend fun deletePermanently(items: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        var success = 0
        items.forEach { item ->
            try {
                if (context.contentResolver.delete(item.uri, null, null) > 0) success++
            } catch (e: Exception) {
                Log.w(TAG, "削除に失敗: ${item.uri}", e)
            }
        }
        success
    }

    /**
     * ファイル名を変更する([newDisplayName] は、拡張子を含む名前)。
     * MediaStoreの名前を書き換えて、Androidに、ファイル名の変更も任せる。
     * こうすると、写真のIDが変わらないため、お気に入りなども、そのまま残る。
     */
    suspend fun rename(item: MediaItem, newDisplayName: String): RenameResult =
        withContext(Dispatchers.IO) {
            try {
                // 同じフォルダに、同じ名前のファイルがないか、先に確認する
                val path = queryFilePath(item)
                if (path != null) {
                    val target = File(File(path).parentFile, newDisplayName)
                    if (target.exists()) return@withContext RenameResult.AlreadyExists
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
                }
                if (context.contentResolver.update(item.uri, values, null, null) > 0) {
                    RenameResult.Success
                } else {
                    RenameResult.Failed
                }
            } catch (e: Exception) {
                Log.w(TAG, "名前の変更に失敗: ${item.uri}", e)
                RenameResult.Failed
            }
        }

    /** ファイルの保存先のパス。取得できなければ null。 */
    private fun queryFilePath(item: MediaItem): String? = try {
        @Suppress("DEPRECATION")
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        context.contentResolver.query(item.uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TAG = "MediaStoreDataSource"

        /** SQLiteの変数の上限(999)を超えないよう、1回に調べる件数。 */
        const val QueryChunkSize = 500
    }
}
