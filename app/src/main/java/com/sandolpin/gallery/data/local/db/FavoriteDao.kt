package com.sandolpin.gallery.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    /** お気に入りのIDの一覧。変更があるたびに、新しい一覧が流れる。 */
    @Query("SELECT mediaId FROM favorites")
    fun observeIds(): Flow<List<Long>>

    /** お気に入りを全部(ID と 追加日時)。書き出しで使う。 */
    @Query("SELECT * FROM favorites")
    suspend fun getAll(): List<FavoriteEntity>

    /** すでに入っているIDは、そのまま(入れ直さない)。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<FavoriteEntity>)

    /** SQLiteの変数の上限(999)があるため、呼ぶ側で、数百件ずつに分けること。 */
    @Query("DELETE FROM favorites WHERE mediaId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
