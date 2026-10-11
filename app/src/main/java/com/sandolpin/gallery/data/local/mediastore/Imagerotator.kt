package com.sandolpin.gallery.data.local.mediastore

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.sandolpin.gallery.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 写真を回転して保存する。
 * - JPEG: 画像データには触れず、向きの情報(EXIF)だけを書き換える(劣化なし・一瞬)。
 * - PNG・WebP: 画像を読み込み、回転して、同じ形式で保存し直す。
 *   (PNGは劣化なし。WebPは、対応する端末(Android 11以上)では劣化なしの形式で保存する)
 * - それ以外(HEIC・GIF・動画など)は、対応しない。
 * どの場合も、ファイルの更新日時は、元のまま保つ(撮影日時の情報がない写真が「いま撮った」扱いにならないように)。
 * 保存したあとは、呼び出し側で、[FileOperations.scan] を呼んで、一覧に反映すること。
 */
@Singleton
class ImageRotator @Inject constructor(
    private val fileOperations: FileOperations,
) {

    /** 回転に対応している形式か(拡張子で判断する)。 */
    fun isSupported(item: MediaItem): Boolean =
        !item.isVideo && extensionOf(item.displayName) in SupportedExtensions

    /**
     * [item] を、時計回りに [degrees] 度(90・180・270)回転して保存する。
     * @param overwrite true なら、元のファイルを上書き(IDは変わらない)。false なら、「名前_rotated.jpg」として、同じフォルダに新しく保存。
     * @return 保存したファイル。失敗したら null。
     */
    suspend fun rotate(item: MediaItem, degrees: Int, overwrite: Boolean): File? =
        withContext(Dispatchers.IO) {
            val source = fileOperations.queryFilePath(item)?.let(::File)
            if (source == null || !source.exists()) return@withContext null

            val originalModified = source.lastModified()
            try {
                val result = when (extensionOf(source.name)) {
                    "jpg", "jpeg" -> rotateJpeg(source, degrees, overwrite)
                    "png" -> rotateBitmapFile(source, degrees, overwrite, Bitmap.CompressFormat.PNG)
                    "webp" -> rotateBitmapFile(source, degrees, overwrite, webpFormat())
                    else -> null
                }
                result?.also { it.setLastModified(originalModified) }
            } catch (e: Exception) {
                Log.w(TAG, "回転に失敗: ${item.uri}", e)
                null
            } catch (e: OutOfMemoryError) {
                // 非常に大きな画像で、メモリが足りないとき。その1件だけを、失敗として数える
                Log.w(TAG, "回転に失敗(メモリ不足): ${item.uri}", e)
                null
            }
        }

    /** JPEG: 向きの情報だけを書き換える。コピーのときは、ファイルをコピーしてから、コピーのほうを書き換える。 */
    private fun rotateJpeg(source: File, degrees: Int, overwrite: Boolean): File {
        val target = if (overwrite) {
            source
        } else {
            fileOperations.uniqueFile(source.parentFile!!, rotatedName(source.name)).also { source.copyTo(it) }
        }
        try {
            val exif = ExifInterface(target.absolutePath)
            exif.rotate(degrees)
            exif.saveAttributes()
        } catch (e: Exception) {
            if (!overwrite) target.delete()
            throw e
        }
        return target
    }

    /** PNG・WebP: 画像を読み込み、回転して、保存し直す。 */
    private fun rotateBitmapFile(
        source: File,
        degrees: Int,
        overwrite: Boolean,
        format: Bitmap.CompressFormat,
    ): File? {
        val original = BitmapFactory.decodeFile(source.path) ?: return null
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        // 回転後の画像が別のものなら、元の画像は、先に解放する(メモリを節約する)
        if (rotated !== original) original.recycle()

        // 上書きのときは、同じフォルダの一時ファイルに書いてから、差し替える(途中で失敗しても、元が壊れない)
        val directory = source.parentFile!!
        val target = if (overwrite) {
            File(directory, ".${source.name}.rotating")
        } else {
            fileOperations.uniqueFile(directory, rotatedName(source.name))
        }

        return try {
            val written = FileOutputStream(target).use { rotated.compress(format, 100, it) }
            if (!written) {
                target.delete()
                null
            } else if (overwrite) {
                if (target.renameTo(source)) source else {
                    target.delete()
                    null
                }
            } else {
                target
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            rotated.recycle()
        }
    }

    /** 「name.jpg」→「name_rotated.jpg」。 */
    private fun rotatedName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) "${name.substring(0, dot)}_rotated${name.substring(dot)}" else "${name}_rotated"
    }

    /** WebPは、Android 11以上では「劣化なし」で、それより前は、品質100の通常のWebPで保存する。 */
    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSLESS
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    private companion object {
        const val TAG = "ImageRotator"
        val SupportedExtensions = setOf("jpg", "jpeg", "png", "webp")
    }
}