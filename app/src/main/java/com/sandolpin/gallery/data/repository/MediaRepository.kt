package com.sandolpin.gallery.data.repository

import android.os.Build
import com.sandolpin.gallery.data.local.mediastore.MediaStoreDataSource
import com.sandolpin.gallery.data.model.MediaFolder
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.model.RenameResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 画面(ViewModel)からは、このRepository経由でのみ写真データにアクセスする。
 * 将来、Room側のキャッシュや、分類結果との結合をここに追加していく想定。
 */
@Singleton
class MediaRepository @Inject constructor(
    private val dataSource: MediaStoreDataSource,
) {
    private val _version = MutableStateFlow(0)

    /**
     * 写真の削除・ゴミ箱への移動・復元があるたびに増える番号。
     * 各画面のViewModelは、これが変わったら一覧を読み直す。
     */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** ゴミ箱(MediaStoreのIS_TRASHED)が使えるか。Android 11(API 30)以上。 */
    val isTrashSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    suspend fun getAllMedia(): List<MediaItem> = dataSource.queryAllMedia()

    suspend fun getFolders(): List<MediaFolder> = dataSource.queryFolders()

    suspend fun getMediaInFolder(folderId: Long): List<MediaItem> =
        dataSource.queryAllMedia().filter { it.folderId == folderId }

    suspend fun getTrashedMedia(): List<MediaItem> = dataSource.queryTrashedMedia()

    /** 読み込み済みの一覧から、フォルダごとの集計を作る(再読み込みしない)。 */
    fun foldersOf(items: List<MediaItem>): List<MediaFolder> = dataSource.buildFolders(items)

    /** ゴミ箱へ移動する。成功した件数を返す。 */
    suspend fun trash(items: List<MediaItem>): Int =
        dataSource.trash(items).also { if (it > 0) notifyChanged() }

    /** ゴミ箱から元に戻す。成功した件数を返す。 */
    suspend fun restore(items: List<MediaItem>): Int =
        dataSource.restore(items).also { if (it > 0) notifyChanged() }

    /** ゴミ箱を通さず、完全に削除する。成功した件数を返す。 */
    suspend fun deletePermanently(items: List<MediaItem>): Int =
        dataSource.deletePermanently(items).also { if (it > 0) notifyChanged() }

    /** ファイル名を変更する([newDisplayName] は、拡張子を含む名前)。成功したら、一覧を読み直させる。 */
    suspend fun rename(item: MediaItem, newDisplayName: String): RenameResult =
        dataSource.rename(item, newDisplayName).also {
            if (it == RenameResult.Success) notifyChanged()
        }

    /**
     * 写真の中身が書き換わった(回転で上書きした)とき。画像の「版」を変えて、各画面に読み直させる。
     * 一覧やビューアーのサムネイルが、古いまま残らないようにするために使う。
     */
    fun markImagesChanged(ids: Collection<Long>) {
        dataSource.markImagesChanged(ids)
        notifyChanged()
    }

    /** ファイル操作(コピー・移動)で、一覧が変わったとき。各画面に、読み直させる。 */
    fun notifyMediaChanged() = notifyChanged()

    private fun notifyChanged() {
        _version.update { it + 1 }
    }
}