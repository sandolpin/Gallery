package com.sandolpin.gallery.data.analysis

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * 裏で動く、写真の分析(WorkManagerが呼ぶ)。
 * 1回の実行で、最大9分ほど分析する。残りがあって、「今すぐ分析」から始まった実行なら、続きを頼む。
 * (自動の分析は、15分ごとに、また動く)
 * 分析している間は、進行状況の通知を出す。
 */
@HiltWorker
class AnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: AnalysisRepository,
    private val analyzer: ImageAnalyzer,
    private val scheduler: AnalysisScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manual = inputData.getBoolean(KeyManual, false)
        val notifier = AnalysisNotifier(applicationContext)

        // 通知を、フォアグラウンドで出す。裏からは始められないとき(Android 12以降)は、ふつうの通知にする
        try {
            setForeground(notifier.foregroundInfo(done = 0, total = 0))
        } catch (e: Exception) {
            Log.d(TAG, "フォアグラウンドにできないため、ふつうの通知を使います: ${e.javaClass.simpleName}")
            notifier.update(done = 0, total = 0)
        }

        return try {
            // 通知の更新は、細かすぎると、Androidに制限される。1秒に1回までにする
            var lastNotifiedAt = 0L
            val result = repository.analyzePending(analyzer, onProgress = { done, total ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastNotifiedAt >= NotifyIntervalMillis || done >= total) {
                    lastNotifiedAt = now
                    notifier.update(done, total)
                }
            })
            Log.d(TAG, "分析: ${result.processed}枚を処理、残り${result.remaining}枚(手動=$manual)")
            if (manual && result.remaining > 0) scheduler.continueManual()
            Result.success()
        } catch (e: CancellationException) {
            // 止められた(設定をOFFにした、など)。そのまま、止める
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "分析に失敗", e)
            // 自動の分析は、15分後に、また動く。手動は、もう一度、頼んでもらう
            Result.failure()
        } finally {
            notifier.cancel()
        }
    }

    companion object {
        private const val TAG = "AnalysisWorker"
        private const val NotifyIntervalMillis = 1000L

        /** 入力のキー。true なら、「今すぐ分析」から始まった実行。 */
        const val KeyManual = "manual"
    }
}
