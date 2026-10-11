package com.sandolpin.gallery.data.repository

import com.sandolpin.gallery.data.local.db.FavoriteDao
import com.sandolpin.gallery.data.local.db.FavoriteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** お気に入り(このアプリの中だけで管理)。写真のID(MediaItem.id)で覚える。 */
@Singleton
class FavoriteRepository @Inject constructor(
    private val dao: FavoriteDao,
) {
    /** お気に入りの写真のID。変更があるたびに、新しい集合が流れる。 */
    val favoriteIds: Flow<Set<Long>> = dao.observeIds().map { it.toSet() }

    /** お気に入りを全部(ID と 追加日時)。書き出しで使う。 */
    suspend fun getAll(): List<FavoriteEntity> = dao.getAll()

    /** [ids] を、お気に入りに入れる([favorite] が true)/ 外す(false)。 */
    suspend fun setFavorite(ids: Collection<Long>, favorite: Boolean) {
        if (ids.isEmpty()) return
        if (favorite) {
            val now = System.currentTimeMillis()
            // SQLiteの変数の上限を超えないよう、500件ずつに分けて入れる
            ids.chunked(500).forEach { chunk ->
                dao.insertAll(chunk.map { FavoriteEntity(mediaId = it, addedAtMillis = now) })
            }
        } else {
            // SQLiteの変数の上限(999)を超えないよう、500件ずつに分けて消す
            ids.chunked(500).forEach { dao.deleteByIds(it) }
        }
    }

    /**
     * 写真のIDが変わったとき(移動でコピー+削除にしたとき)に、お気に入りを、新しいIDに付け替える。
     * [mapping] は、古いID → 新しいID。お気に入りでないものは、何もしない。
     */
    suspend fun replaceIds(mapping: Map<Long, Long>) {
        if (mapping.isEmpty()) return
        val current = dao.observeIds().first().toSet()
        val targets = mapping.filterKeys { it in current }
        if (targets.isEmpty()) return
        dao.insertAll(targets.values.map { FavoriteEntity(mediaId = it, addedAtMillis = System.currentTimeMillis()) })
        targets.keys.toList().chunked(500).forEach { dao.deleteByIds(it) }
    }
}
