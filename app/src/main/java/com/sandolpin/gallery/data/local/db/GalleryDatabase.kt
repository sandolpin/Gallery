package com.sandolpin.gallery.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * アプリのデータベース。お気に入りと、写真の分析結果(分析済みの記録・検出したラベル・読み取った文字)。
 * 表を足したり変えたりするときは、version を上げて、[GalleryMigrations] に、移行の手順を足すこと
 * (足し忘れると、起動時に落ちる。お気に入りを消さないために、作り直しはしない)。
 */
@Database(
    entities = [
        FavoriteEntity::class,
        AnalyzedMediaEntity::class,
        DetectedLabelEntity::class,
        DetectedTextEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun analysisDao(): AnalysisDao
}
