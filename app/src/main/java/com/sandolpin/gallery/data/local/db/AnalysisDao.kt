package com.sandolpin.gallery.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 写真の分析結果(分析済みの記録・検出したラベル・読み取った文字)の読み書き。 */
@Dao
abstract class AnalysisDao {

    /** 分析済みの写真の、ID・版・仕組みの版。「まだ分析していない写真」を探すのに使う。 */
    @Query("SELECT mediaId, version, analyzerVersion FROM analyzed_media")
    abstract suspend fun getAnalyzedInfos(): List<AnalyzedInfo>

    /** 文字認識が終わった写真のID(文字がなかった写真も含む)。 */
    @Query("SELECT mediaId FROM detected_texts")
    abstract suspend fun getTextDoneIds(): List<Long>

    /** [minVersion] 以上の仕組みで分析済みの写真の数。変わるたびに、新しい数が流れる(進行状況の表示用)。 */
    @Query("SELECT COUNT(*) FROM analyzed_media WHERE analyzerVersion >= :minVersion")
    abstract fun observeAnalyzedCount(minVersion: Int): Flow<Int>

    /** 文字認識が終わった写真の数。変わるたびに、新しい数が流れる。 */
    @Query("SELECT COUNT(*) FROM detected_texts")
    abstract fun observeTextCount(): Flow<Int>

    /** 信頼度が [minConfidence] 以上のラベルが、1つ以上付いた写真の数。変わるたびに、新しい数が流れる。 */
    @Query("SELECT COUNT(DISTINCT mediaId) FROM detected_labels WHERE confidence >= :minConfidence")
    abstract fun observeLabeledCount(minConfidence: Float): Flow<Int>

    /**
     * [labels](英語のラベル。大文字小文字は区別しない)のどれかが、信頼度 [minConfidence] 以上で付いた写真を探す。
     * 写真ごとに、いちばん高い信頼度を返す。
     */
    @Query(
        "SELECT mediaId, MAX(confidence) AS confidence FROM detected_labels " +
            "WHERE label COLLATE NOCASE IN (:labels) AND confidence >= :minConfidence GROUP BY mediaId",
    )
    abstract suspend fun findByLabels(labels: List<String>, minConfidence: Float): List<MediaConfidence>

    /**
     * 読み取った文字に、[pattern] が含まれる写真のID。[pattern] は、LIKE用(前後に % を付けたもの。\\ でエスケープ)。
     * 英字の大文字小文字は区別しない。
     */
    @Query("SELECT mediaId FROM detected_texts WHERE text LIKE :pattern ESCAPE '\\'")
    abstract suspend fun findByText(pattern: String): List<Long>

    /**
     * 記録を入れる。すでにあれば、更新する。
     * (置き換え(REPLACE)にしないのは、置き換えると、ひもづいたラベルと文字が、一緒に消えてしまうため)
     */
    @Upsert
    abstract suspend fun upsertAnalyzed(entity: AnalyzedMediaEntity)

    @Insert
    abstract suspend fun insertLabels(labels: List<DetectedLabelEntity>)

    @Query("DELETE FROM detected_labels WHERE mediaId = :mediaId")
    abstract suspend fun deleteLabels(mediaId: Long)

    @Upsert
    abstract suspend fun upsertText(entity: DetectedTextEntity)

    @Query("DELETE FROM detected_texts WHERE mediaId = :mediaId")
    abstract suspend fun deleteText(mediaId: Long)

    /** SQLiteの変数の上限(999)があるため、呼ぶ側で、数百件ずつに分けること。 */
    @Query("DELETE FROM analyzed_media WHERE mediaId IN (:ids)")
    abstract suspend fun deleteAnalyzed(ids: List<Long>)

    /** 分析の結果を、すべて消す(ラベルも、文字も、一緒に消える)。 */
    @Query("DELETE FROM analyzed_media")
    abstract suspend fun clearAll()

    /**
     * 1枚分の分析結果を、まとめて保存する。途中で失敗しても、中途半端に残らない。
     * - [analyzed] と [labels]: ラベルを検出し直したときだけ、指定する(null なら、そのまま)。
     * - [text]: 文字認識をしたときだけ、指定する(null なら、そのまま)。
     * - [clearText]: 写真の中身が変わったので、古い文字を消す(新しい [text] があれば、そのあとで入れる)。
     */
    @Transaction
    open suspend fun saveResult(
        mediaId: Long,
        analyzed: AnalyzedMediaEntity?,
        labels: List<DetectedLabelEntity>?,
        text: DetectedTextEntity?,
        clearText: Boolean,
    ) {
        if (analyzed != null) upsertAnalyzed(analyzed)
        if (labels != null) {
            deleteLabels(mediaId)
            if (labels.isNotEmpty()) insertLabels(labels)
        }
        if (clearText) deleteText(mediaId)
        if (text != null) upsertText(text)
    }
}
