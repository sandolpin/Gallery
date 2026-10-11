package com.sandolpin.gallery.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * データベースの、バージョン間の移行の手順。
 * 既存の表(お気に入りなど)には、触らず、新しい表を足すだけにする。
 * ここのSQLは、Roomが作る表と、一致させる必要がある(ずれると、起動時に落ちる)。
 */
object GalleryMigrations {

    /** 1 → 2: 写真の分析結果の表を足す(分析済みの記録・検出したラベル)。 */
    val Migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `analyzed_media` (" +
                    "`mediaId` INTEGER NOT NULL, " +
                    "`version` TEXT NOT NULL, " +
                    "`analyzerVersion` INTEGER NOT NULL, " +
                    "`analyzedAtMillis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`mediaId`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `detected_labels` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`mediaId` INTEGER NOT NULL, " +
                    "`label` TEXT NOT NULL, " +
                    "`confidence` REAL NOT NULL, " +
                    "FOREIGN KEY(`mediaId`) REFERENCES `analyzed_media`(`mediaId`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_detected_labels_mediaId` ON `detected_labels` (`mediaId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_detected_labels_label` ON `detected_labels` (`label`)")
        }
    }

    /** 2 → 3: 写真から読み取った文字の表を足す(文字認識の結果)。 */
    val Migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `detected_texts` (" +
                    "`mediaId` INTEGER NOT NULL, " +
                    "`text` TEXT NOT NULL, " +
                    "PRIMARY KEY(`mediaId`), " +
                    "FOREIGN KEY(`mediaId`) REFERENCES `analyzed_media`(`mediaId`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
        }
    }
}
