package com.sandolpin.gallery.data.local

import android.os.Environment
import android.os.StatFs

/** 端末の内部ストレージの、全体の容量と空き容量(バイト)。 */
data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
) {
    /** 使用率(0.0〜1.0)。容量が分からないときは 0。 */
    val usedFraction: Float
        get() = if (totalBytes <= 0L) 0f else ((totalBytes - freeBytes).toFloat() / totalBytes).coerceIn(0f, 1f)
}

/** 内部ストレージの容量を読む。SDカードは含めない。 */
object StorageReader {
    fun read(): StorageInfo = try {
        @Suppress("DEPRECATION")
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        StorageInfo(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
    } catch (e: Exception) {
        StorageInfo(totalBytes = 0L, freeBytes = 0L)
    }
}