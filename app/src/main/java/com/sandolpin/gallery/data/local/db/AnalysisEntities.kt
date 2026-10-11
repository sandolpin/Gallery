package com.sandolpin.gallery.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 「分析が終わった写真」の記録。写真1枚につき1行。
 * この表にある写真は、ラベルの検出が終わっている(結果が0件でも、記録は残る)ので、次からは飛ばす。
 */
@Entity(tableName = "analyzed_media")
data class AnalyzedMediaEntity(
    /** MediaStoreのID(MediaItem.id)。 */
    @PrimaryKey val mediaId: Long,
    /** 分析したときの、画像の「版」(MediaItem.imageVersion)。写真の中身が変わったら、分析し直す。 */
    val version: String,
    /** 分析の仕組みの版([com.sandolpin.gallery.data.analysis.AnalysisConfig.CurrentVersion])。仕組みを新しくしたら、分析し直す。 */
    val analyzerVersion: Int,
    /** 分析した日時(ミリ秒)。 */
    val analyzedAtMillis: Long,
)

/**
 * 写真から検出したラベル(「海」「犬」など)。1枚の写真に、複数ある。
 * [label] は、検出に使うライブラリが返す英語の名前(日本語への変換は、表示や検索のときに行う)。
 * 写真の記録([AnalyzedMediaEntity])を消すと、一緒に消える。
 */
@Entity(
    tableName = "detected_labels",
    foreignKeys = [
        ForeignKey(
            entity = AnalyzedMediaEntity::class,
            parentColumns = ["mediaId"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaId"), Index("label")],
)
data class DetectedLabelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val label: String,
    /** 信頼度(0.0〜1.0)。 */
    val confidence: Float,
)

/**
 * 写真の中から読み取った文字(文字認識の結果)。写真1枚につき1行。
 * 文字認識をした写真には、文字がなくても、行が残る([text] が空)ので、次からは飛ばす。
 * 検索しやすいよう、全角・半角をそろえた(NFKC)文字を保存する。
 * 写真の記録([AnalyzedMediaEntity])を消すと、一緒に消える。
 */
@Entity(
    tableName = "detected_texts",
    foreignKeys = [
        ForeignKey(
            entity = AnalyzedMediaEntity::class,
            parentColumns = ["mediaId"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DetectedTextEntity(
    @PrimaryKey val mediaId: Long,
    val text: String,
)

/** 分析済みの写真の、ID・版・仕組みの版だけ(一覧の取得用)。 */
data class AnalyzedInfo(
    val mediaId: Long,
    val version: String,
    val analyzerVersion: Int,
)

/** ラベルの検索結果: 写真のID と、その写真に付いた(条件に合う)ラベルの中で、いちばん高い信頼度(0.0〜1.0)。 */
data class MediaConfidence(
    val mediaId: Long,
    val confidence: Float,
)
