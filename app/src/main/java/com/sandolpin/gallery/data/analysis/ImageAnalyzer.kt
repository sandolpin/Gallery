package com.sandolpin.gallery.data.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.sandolpin.gallery.data.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 写真の分析についての、決まった値。 */
object AnalysisConfig {
    /**
     * 分析の仕組みの版。ラベルの検出の結果が変わるときに上げる。
     * 上げると、分析済みの写真も、また分析される。
     * (1 = 土台だけ / 2 = カテゴリ検出(ラベル)を追加)
     * 文字認識は、版ではなく、「文字認識をした記録があるか」で、済んだかどうかを判断する。
     */
    const val CurrentVersion = 2

    /** 1回の実行で、分析に使う時間の上限(ミリ秒)。WorkManagerの1回の実行は、10分が上限のため、少し短くする。 */
    const val BudgetMillis = 9 * 60 * 1000L

    /** ラベル検出のために読み込む、画像の大きさ(長いほうの辺、px)。ML Kitは、224px以上あれば十分に動く。 */
    const val LabelImagePx = 320

    /** 文字認識のために読み込む、画像の大きさ(長いほうの辺、px)。小さい文字を読むため、ラベル検出より大きくする。 */
    const val TextImagePx = 1280

    /**
     * ラベルとして保存する、信頼度の下限。ML Kitの標準(0.5)と同じ。
     * 設定の「しきい値」は、これ以上の範囲で選ぶ(保存は広めにしておき、使うときに絞るので、しきい値を変えても、分析し直さない)。
     */
    const val MinStoredConfidence = 0.5f

    /** 1枚の写真に、保存するラベルの最大数(信頼度の高い順)。 */
    const val MaxLabelsPerImage = 10

    /** 1枚の写真から、保存する文字の最大の長さ(文字数)。 */
    const val MaxTextLength = 5000
}

/** 写真から検出したラベル1つ分。[label] は、ML Kitが返す英語の名前。 */
data class DetectedLabel(
    val label: String,
    val confidence: Float,
)

/**
 * 写真1枚の分析結果。
 * [labels] は、ラベルを検出したときだけ入る(null は、検出していない)。
 * [text] は、文字認識をしたときだけ入る(null は、していない。文字がなければ、空の文字)。
 */
data class AnalysisOutcome(
    val labels: List<DetectedLabel>?,
    val text: String?,
)

/**
 * 写真1枚を分析する。
 * - カテゴリ検出(ML Kit の Image Labeling)
 * - 文字認識(ML Kit の Text Recognition。日本語モデル。日本語も英数字も読める)
 * どちらも、モデルはアプリに内蔵。端末の中だけで動く。
 */
@Singleton
class ImageAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** ラベル検出の本体。最初に使うときに作り、そのあとは使い回す。 */
    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(AnalysisConfig.MinStoredConfidence)
                .build(),
        )
    }

    /** 文字認識の本体(日本語モデル)。最初に使うときに作り、そのあとは使い回す。 */
    private val textRecognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    /**
     * [item] を分析する。[needLabels] が true ならラベル検出を、[needText] が true なら文字認識を行う。
     * 画像の読み込みは、1回だけ(両方行うときは、文字認識向けの大きい画像を読み込み、ラベル検出には、縮小して使う)。
     *
     * - 画像を読み込めなかったとき(壊れたファイルなど)は、「ラベルなし・文字なし」として返す(呼ぶ側は、分析済みとして記録する)。
     * - ML Kit自体が失敗したときは、例外を、そのまま投げる(失敗を「ラベルなし」として記録しないため)。
     */
    suspend fun analyze(item: MediaItem, needLabels: Boolean, needText: Boolean): AnalysisOutcome {
        val emptyOutcome = AnalysisOutcome(
            labels = if (needLabels) emptyList() else null,
            text = if (needText) "" else null,
        )
        if (!needLabels && !needText) return emptyOutcome

        val loadPx = if (needText) AnalysisConfig.TextImagePx else AnalysisConfig.LabelImagePx
        val bitmap = loadBitmap(item.uri, loadPx)
        if (bitmap == null) {
            Log.w(TAG, "画像を読み込めませんでした: ${item.uri}")
            return emptyOutcome
        }
        try {
            val rotation = readRotationDegrees(item.uri)

            val labels = if (needLabels) detectLabels(bitmap, rotation) else null
            val text = if (needText) recognizeText(bitmap, rotation) else null
            return AnalysisOutcome(labels = labels, text = text)
        } finally {
            bitmap.recycle()
        }
    }

    /** ラベルを検出する(信頼度の高い順)。大きい画像のときは、縮小したコピーを使う。 */
    private suspend fun detectLabels(bitmap: Bitmap, rotation: Int): List<DetectedLabel> {
        val longSide = maxOf(bitmap.width, bitmap.height)
        val small = if (longSide > AnalysisConfig.LabelImagePx * 2) {
            val scale = AnalysisConfig.LabelImagePx.toFloat() / longSide
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        try {
            return labeler.process(InputImage.fromBitmap(small, rotation)).await()
                .sortedByDescending { it.confidence }
                .take(AnalysisConfig.MaxLabelsPerImage)
                .map { DetectedLabel(label = it.text, confidence = it.confidence) }
        } finally {
            if (small !== bitmap) small.recycle()
        }
    }

    /**
     * 写真の中の文字を読み取る。検索しやすいよう、全角・半角をそろえ(NFKC)、改行は空白にして返す。
     * 文字がなければ、空の文字。
     */
    private suspend fun recognizeText(bitmap: Bitmap, rotation: Int): String {
        val result = textRecognizer.process(InputImage.fromBitmap(bitmap, rotation)).await()
        return Normalizer.normalize(result.text, Normalizer.Form.NFKC)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(AnalysisConfig.MaxTextLength)
    }

    /**
     * [uri] の画像を、長いほうの辺が [maxPx] 前後になるよう、縮小して読み込む。読めなければ null。
     * 縮小は、読み込みのときに行う(元の大きさのまま読み込まない)ので、メモリを使いすぎない。
     */
    suspend fun loadBitmap(uri: Uri, maxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            // まず、大きさだけを調べる
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            // 長い辺が maxPx 以上になる範囲で、いちばん大きく縮める(2のべき乗)
            var sample = 1
            val longSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (longSide / (sample * 2) >= maxPx) sample *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            Log.w(TAG, "画像の読み込みに失敗: $uri", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "メモリが足りず、画像を読み込めませんでした: $uri", e)
            null
        }
    }

    /**
     * 写真の向き(EXIF)から、「何度回すと、正しい向きになるか」を返す(0・90・180・270)。
     * BitmapFactoryは、向きを反映しないので、ML Kitに、別に伝える。読めなければ 0。
     */
    private suspend fun readRotationDegrees(uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90,
                    ExifInterface.ORIENTATION_TRANSPOSE,
                    -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270,
                    ExifInterface.ORIENTATION_TRANSVERSE,
                    -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /** Google の Task(ML Kit が返す、結果の受け取り口)を、コルーチンで待てるようにする。 */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }

    private companion object {
        const val TAG = "ImageAnalyzer"
    }
}
