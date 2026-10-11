package com.sandolpin.gallery.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * お気に入りの写真・動画1件分。MediaStoreのID(MediaItem.id)で覚える。
 * このアプリの中だけで管理する(端末全体のお気に入りは使わない)。
 */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaId: Long,
    /** お気に入りに入れた日時(ミリ秒)。 */
    val addedAtMillis: Long,
)