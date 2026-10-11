package com.sandolpin.gallery.data.analysis

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 写真の分析を、いつ動かすかを決める(WorkManagerに頼む)。
 *
 * - 自動: 15分ごとに、「充電中」で「バッテリー残量が十分」なときだけ、動く(1回で、最大9分ほど分析する)。
 * - 手動(今すぐ分析): 条件なしで、すぐ動く。1回で終わらなければ、続きを、自動で頼む。
 * 設定の「あいまい検索」をOFFにしたら、どちらも止める。
 */
@Singleton
class AnalysisScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** 設定([enabled])に合わせて、自動の分析を、動かす/止める。起動時と、設定が変わったときに呼ぶ。 */
    fun apply(enabled: Boolean) {
        if (enabled) schedulePeriodic() else cancelAll()
    }

    /** 「今すぐ分析」。すでに頼んである(動いている・待っている)ときは、何もしない。 */
    fun startNow() {
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(workDataOf(AnalysisWorker.KeyManual to true))
            .build()
        workManager.enqueueUniqueWork(ManualName, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * 「今すぐ分析」の続きを頼む。分析の最中(AnalysisWorkerの中)から呼ぶ。
     * 動いている分析が終わってから、続きが動く。
     */
    fun continueManual() {
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(workDataOf(AnalysisWorker.KeyManual to true))
            .build()
        workManager.enqueueUniqueWork(ManualName, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** 自動・手動の分析を、すべて止める。 */
    fun cancelAll() {
        workManager.cancelUniqueWork(PeriodicName)
        workManager.cancelUniqueWork(ManualName)
    }

    /**
     * 分析の状態。変わるたびに、新しい状態が流れる。
     * (getter にしているのは、このクラスを作った時点で WorkManager を準備してしまわないため。
     * 準備は、アプリの起動処理(Hiltの注入)が終わってから、行う必要がある)
     */
    val status: Flow<AnalysisStatus>
        get() = combine(
            workManager.getWorkInfosForUniqueWorkFlow(ManualName),
            workManager.getWorkInfosForUniqueWorkFlow(PeriodicName),
        ) { manual, periodic ->
        when {
            (manual + periodic).any { it.state == WorkInfo.State.RUNNING } -> AnalysisStatus.Running
            manual.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } -> AnalysisStatus.Waiting
            else -> AnalysisStatus.Idle
        }
    }.distinctUntilChanged()

    private fun schedulePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<AnalysisWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        // KEEP: すでに予約してあれば、そのまま(起動のたびに、予約し直さない)
        workManager.enqueueUniquePeriodicWork(PeriodicName, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val PeriodicName = "gallery_analysis_periodic"
        const val ManualName = "gallery_analysis_manual"
    }
}
