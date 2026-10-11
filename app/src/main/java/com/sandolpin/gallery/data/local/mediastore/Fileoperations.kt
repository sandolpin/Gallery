package com.sandolpin.gallery.data.local.mediastore

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.sandolpin.gallery.data.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** コピー・移動の行き先。 */
sealed interface CopyDestination {
    /** すでにあるフォルダ([folderId] は、MediaStoreのBUCKET_ID)。 */
    data class ExistingFolder(val folderId: Long) : CopyDestination

    /** 新しく作るフォルダ。内部ストレージの Pictures の直下に作る。 */
    data class NewFolder(val name: String) : CopyDestination
}

/** 1件を移動した結果。 */
sealed interface MoveOutcome {
    /** MediaStoreの書き換えで移動できた。写真のIDは変わらない(お気に入りなどは、そのまま)。 */
    data object InPlace : MoveOutcome

    /** コピーして元を削除した。元の写真のIDは消え、[file] に新しいIDが付く(スキャン後に分かる)。 */
    data class Copied(val file: File, val oldId: Long) : MoveOutcome

    /** コピーはできたが、元のファイルを削除できなかった(コピーしただけの状態)。 */
    data class CopiedOnly(val file: File) : MoveOutcome

    /** 移動できなかった。 */
    data object Failed : MoveOutcome
}

/**
 * 写真・動画のファイルを、直接コピー・移動する。
 * 「すべてのファイルへのアクセス」権限があるので、OSの確認ダイアログは出ない。
 * コピーしたあと(移動では、コピー+削除にしたとき)、[scan] でAndroidに知らせると、ギャラリーの一覧に反映される。
 */
@Singleton
class FileOperations @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 行き先のフォルダ(ディレクトリ)を決める。見つからない・作れないときは null。 */
    suspend fun resolveDirectory(destination: CopyDestination): File? = withContext(Dispatchers.IO) {
        when (destination) {
            is CopyDestination.ExistingFolder -> directoryOfBucket(destination.folderId)
            is CopyDestination.NewFolder -> {
                @Suppress("DEPRECATION")
                val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val directory = File(pictures, destination.name)
                if (directory.isDirectory || directory.mkdirs()) directory else null
            }
        }
    }

    /** フォルダのID(BUCKET_ID)から、そのフォルダの場所を調べる(中の写真1枚の保存先から求める)。 */
    private fun directoryOfBucket(folderId: Long): File? = try {
        @Suppress("DEPRECATION")
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            "${MediaStore.Files.FileColumns.BUCKET_ID} = ?",
            arrayOf(folderId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.let { File(it).parentFile } else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "フォルダの場所を調べられませんでした: $folderId", e)
        null
    }

    /**
     * 1件を [directory] にコピーする。成功したら、コピー先のファイルを返す(失敗したら null)。
     * - 同じ名前のファイルがあれば、「名前 (1).jpg」のように番号を付ける(上書きしない)。
     * - 撮影日時の情報がない写真(スクリーンショットなど)が、「いま撮った」扱いにならないよう、
     *   更新日時も、元のファイルに合わせる。
     */
    suspend fun copyOne(item: MediaItem, directory: File): File? = withContext(Dispatchers.IO) {
        copyFile(item, directory)
    }

    private fun copyFile(item: MediaItem, directory: File): File? {
        var target: File? = null
        return try {
            val source = queryFilePath(item)?.let(::File)
            if (source == null || !source.exists()) return null
            target = uniqueFile(directory, item.displayName)
            source.copyTo(target)
            target.setLastModified(source.lastModified())
            target
        } catch (e: Exception) {
            Log.w(TAG, "コピーに失敗: ${item.uri}", e)
            // 途中まで書いたファイルが残らないようにする
            target?.delete()
            null
        }
    }

    /**
     * 1件を [directory] に移動する。次の順に試す。
     * 1. MediaStoreの保存先の情報を書き換える(IDが変わらず、一瞬で終わる)。
     *    Androidの決まりで、使えない場所(写真を Download の下に置くなど)や、SDカードでは失敗する。
     * 2. 失敗したら、コピーして、元を削除する(IDが変わる)。
     */
    suspend fun moveOne(item: MediaItem, directory: File): MoveOutcome = withContext(Dispatchers.IO) {
        if (tryMoveInPlace(item, directory)) return@withContext MoveOutcome.InPlace

        val copied = copyFile(item, directory) ?: return@withContext MoveOutcome.Failed
        if (deleteOriginal(item)) {
            MoveOutcome.Copied(file = copied, oldId = item.id)
        } else {
            MoveOutcome.CopiedOnly(file = copied)
        }
    }

    /** 同じ条件で、書き換えに失敗した場所(写真/動画 × フォルダ)。何度も試さないよう覚えておく。 */
    private val inPlaceBlocked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun tryMoveInPlace(item: MediaItem, directory: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        val blockedKey = "${item.isVideo}|${directory.path}"
        if (blockedKey in inPlaceBlocked) return false

        @Suppress("DEPRECATION")
        val root = Environment.getExternalStorageDirectory().absolutePath
        // 内部ストレージの中での移動だけが対象(SDカードなど、別の保存先にまたがる移動はできない)
        if (!directory.path.startsWith("$root/")) return false
        val sourcePath = queryFilePath(item) ?: return false
        if (!sourcePath.startsWith("$root/")) return false

        val relativePath = directory.path.removePrefix("$root/") + "/"
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                // 同じ名前があるときは、連番の名前にする
                val uniqueName = uniqueFile(directory, item.displayName).name
                if (uniqueName != item.displayName) put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
            }
            context.contentResolver.update(item.uri, values, null, null) > 0
        } catch (e: IllegalArgumentException) {
            // 「この場所には置けない」という、Androidの決まりによる失敗。同じ条件では、もう試さない
            Log.i(TAG, "MediaStoreの書き換えでは移動できない場所: $relativePath", e)
            inPlaceBlocked += blockedKey
            false
        } catch (e: Exception) {
            Log.w(TAG, "MediaStoreの書き換えに失敗: ${item.uri}", e)
            false
        }
    }

    /** 元のファイルを削除する。成功したら true。 */
    private fun deleteOriginal(item: MediaItem): Boolean {
        val path = queryFilePath(item)
        val deletedRow = try {
            context.contentResolver.delete(item.uri, null, null) > 0
        } catch (e: Exception) {
            Log.w(TAG, "元の削除に失敗: ${item.uri}", e)
            false
        }
        // 登録は消えたが、ファイルが残っている場合に備えて、ファイルも確認する
        val file = path?.let(::File)
        if (file != null && file.exists()) return file.delete()
        return deletedRow || (file != null && !file.exists())
    }

    /**
     * ファイルを、Androidに知らせる。全部の登録が終わるまで待つ。
     * 戻り値は、パス → 登録されたURI(新しい写真のIDは、ここから分かる)。登録できなかったものは、値が null。
     */
    suspend fun scan(paths: List<String>): Map<String, Uri?> {
        if (paths.isEmpty()) return emptyMap()
        val results = ConcurrentHashMap<String, Uri>()
        suspendCancellableCoroutine { continuation ->
            val remaining = AtomicInteger(paths.size)
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { path, uri ->
                if (uri != null) results[path] = uri
                if (remaining.decrementAndGet() == 0 && continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
        return paths.associateWith { results[it] }
    }

    /** [directory] の中で、まだ使われていないファイル名にする(「name.jpg」→「name (1).jpg」→…)。 */
    fun uniqueFile(directory: File, displayName: String): File {
        val first = File(directory, displayName)
        if (!first.exists()) return first

        val dot = displayName.lastIndexOf('.')
        val base = if (dot > 0) displayName.substring(0, dot) else displayName
        val extension = if (dot > 0) displayName.substring(dot) else ""
        var number = 1
        while (true) {
            val candidate = File(directory, "$base ($number)$extension")
            if (!candidate.exists()) return candidate
            number++
        }
    }

    /** MediaStoreに登録されている、ファイルの保存先のパス。 */
    fun queryFilePath(item: MediaItem): String? = try {
        @Suppress("DEPRECATION")
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        context.contentResolver.query(item.uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TAG = "FileOperations"
    }
}