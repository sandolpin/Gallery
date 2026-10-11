package com.sandolpin.gallery.data.analysis

import android.os.SystemClock
import com.sandolpin.gallery.data.local.db.AnalysisDao
import com.sandolpin.gallery.data.local.db.AnalyzedMediaEntity
import com.sandolpin.gallery.data.local.db.DetectedLabelEntity
import com.sandolpin.gallery.data.local.db.DetectedTextEntity
import com.sandolpin.gallery.data.local.mediastore.MediaStoreDataSource
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.repository.SettingsRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/** 分析の状態(設定画面の表示用)。 */
enum class AnalysisStatus {
    /** 動いていない(自動の分析は、充電中に、動き出す)。 */
    Idle,

    /** 「今すぐ分析」を頼んだ。動き出すのを待っている。 */
    Waiting,

    /** 分析している。 */
    Running,
}

/** 分析の進み具合(設定画面の表示用)。[analyzed] / [total] 枚。 */
data class AnalysisProgress(
    val analyzed: Int = 0,
    val total: Int = 0,
    val status: AnalysisStatus = AnalysisStatus.Idle,
    /** ラベルが付いた写真の枚数(設定のしきい値以上のラベルが、1つ以上ある写真)。 */
    val labeled: Int = 0,
    /** 文字認識が終わった写真の枚数(文字がなかった写真も含む)。 */
    val recognized: Int = 0,
)

/** 1回の実行の結果。[processed] 枚を分析して、まだ [remaining] 枚が、残っている。 */
data class AnalysisBatchResult(
    val processed: Int,
    val remaining: Int,
)

/** これから分析する写真1枚と、何をするか。 */
private data class PendingItem(
    val item: MediaItem,
    val needLabels: Boolean,
    val needText: Boolean,
    /** 写真の中身が変わったので、古い文字を消す。 */
    val clearText: Boolean,
)

/**
 * 写真の分析の、進め方と記録。
 * 「まだ分析していない写真」を探して、新しい順に、1枚ずつ分析して、結果をデータベースに保存する。
 * - ラベルの検出: 常に行う。
 * - 文字認識: 設定の「写真内の文字も検索する」がONのときだけ行う。ONにしたときは、分析済みの写真も、文字認識だけ追加で行う。
 */
@Singleton
class AnalysisRepository @Inject constructor(
    private val dao: AnalysisDao,
    private val dataSource: MediaStoreDataSource,
    private val settingsRepository: SettingsRepository,
) {
    /** 同時に、2つの分析が動かないようにする(自動と「今すぐ」が重なったとき)。 */
    private val runLock = Mutex()

    /** 分析済みの写真の数(いまの仕組みで分析したもの)。変わるたびに、新しい数が流れる。 */
    val analyzedCount: Flow<Int> = dao.observeAnalyzedCount(AnalysisConfig.CurrentVersion)

    /** 文字認識が終わった写真の数。変わるたびに、新しい数が流れる。 */
    val recognizedCount: Flow<Int> = dao.observeTextCount()

    /** しきい値([minConfidence]: 0.0〜1.0)以上のラベルが付いた写真の数。変わるたびに、新しい数が流れる。 */
    fun labeledCount(minConfidence: Float): Flow<Int> = dao.observeLabeledCount(minConfidence)

    /**
     * ラベル([labels]: ML Kitの英語のラベル)が付いた写真を探す。設定のしきい値以上の信頼度のものだけ。
     * 返すのは、写真のID → いちばん高い信頼度(0.0〜1.0)。
     */
    suspend fun findByLabels(labels: Collection<String>, minConfidence: Float): Map<Long, Float> {
        if (labels.isEmpty()) return emptyMap()
        // SQLiteの変数の上限(999)に収まるよう、分けて探す
        val result = HashMap<Long, Float>()
        labels.distinct().chunked(500).forEach { chunk ->
            dao.findByLabels(chunk, minConfidence).forEach { hit ->
                val old = result[hit.mediaId]
                if (old == null || hit.confidence > old) result[hit.mediaId] = hit.confidence
            }
        }
        return result
    }

    /**
     * 写真の中の文字に、[query] が含まれる写真のID。部分一致。全角・半角、英字の大文字小文字は区別しない。
     * 文字認識をしていない写真は、ヒットしない。
     */
    suspend fun findByText(query: String): Set<Long> {
        val needle = Normalizer.normalize(query, Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), " ")
            .trim()
        if (needle.isEmpty()) return emptySet()
        // LIKE の特別な文字(% _ \)は、ただの文字として扱う
        val escaped = needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return dao.findByText("%$escaped%").toHashSet()
    }

    /** 分析の対象になる写真の数(動画は含まない。ゴミ箱の中も含まない)。 */
    suspend fun countTargets(): Int = dataSource.queryAllMedia().count { !it.isVideo }

    /** 分析の結果を、すべて消す(次の実行で、また、最初から分析される)。 */
    suspend fun clearAll() = dao.clearAll()

    /**
     * まだ分析していない写真を、新しい順に分析する。[budgetMillis] を超えたら、そこで止める(続きは、次の実行)。
     * ほかの分析がすでに動いているときは、何もせずに、すぐ返る(残りは 0 とする)。
     *
     * @param onProgress 進み具合を知らせる。(全体で、分析が済んだ枚数, 対象の全体の枚数)。通知の表示に使う。
     */
    suspend fun analyzePending(
        analyzer: ImageAnalyzer,
        budgetMillis: Long = AnalysisConfig.BudgetMillis,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): AnalysisBatchResult {
        if (!runLock.tryLock()) return AnalysisBatchResult(processed = 0, remaining = 0)
        try {
            val startedAt = SystemClock.elapsedRealtime()
            val textEnabled = settingsRepository.current().textRecognitionEnabled
            val targets = dataSource.queryAllMedia().filter { !it.isVideo }
            val done = dao.getAnalyzedInfos().associateBy { it.mediaId }
            val textDone = dao.getTextDoneIds().toHashSet()

            // 端末から消えた写真の記録は、消す(ラベルと文字も、一緒に消える)
            val existing = targets.mapTo(HashSet()) { it.id }
            done.keys.filter { it !in existing }.chunked(500).forEach { dao.deleteAnalyzed(it) }

            // 何かすることがある写真だけ(新しい順のまま)
            val pending = targets.mapNotNull { item ->
                val info = done[item.id]
                val imageChanged = info != null && info.version != item.imageVersion
                val needLabels = info == null ||
                    info.analyzerVersion < AnalysisConfig.CurrentVersion ||
                    imageChanged
                // 文字認識は、ONのときだけ。まだ読み取っていない写真と、中身が変わった写真が対象
                val needText = textEnabled && (needLabels || item.id !in textDone)
                if (needLabels || needText) {
                    PendingItem(item, needLabels, needText, clearText = imageChanged)
                } else {
                    null
                }
            }

            val alreadyDone = targets.size - pending.size
            onProgress(alreadyDone, targets.size)

            var processed = 0
            for (entry in pending) {
                // 取り消されたら(設定をOFFにした、など)、ここで止まる
                currentCoroutineContext().ensureActive()
                if (SystemClock.elapsedRealtime() - startedAt > budgetMillis) break

                val item = entry.item
                val outcome = analyzer.analyze(item, entry.needLabels, entry.needText)
                dao.saveResult(
                    mediaId = item.id,
                    analyzed = if (entry.needLabels) {
                        AnalyzedMediaEntity(
                            mediaId = item.id,
                            version = item.imageVersion,
                            analyzerVersion = AnalysisConfig.CurrentVersion,
                            analyzedAtMillis = System.currentTimeMillis(),
                        )
                    } else {
                        null
                    },
                    labels = outcome.labels?.map {
                        DetectedLabelEntity(mediaId = item.id, label = it.label, confidence = it.confidence)
                    },
                    text = outcome.text?.let { DetectedTextEntity(mediaId = item.id, text = it) },
                    clearText = entry.clearText,
                )
                processed++
                onProgress(alreadyDone + processed, targets.size)
            }
            return AnalysisBatchResult(processed = processed, remaining = pending.size - processed)
        } finally {
            runLock.unlock()
        }
    }
}
