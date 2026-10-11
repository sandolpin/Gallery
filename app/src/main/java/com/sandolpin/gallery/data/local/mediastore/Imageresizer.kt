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
import kotlin.math.roundToInt

/**
 * 写真を縮小して保存する(JPEG・PNG・WebPに対応)。
 * - JPEG・WebPは画質90、PNGは劣化なしで保存する。
 * - JPEGは、撮影日時・位置情報・カメラ情報(EXIF)を、新しいファイルに引き継ぐ。
 *   向きの情報は、画像そのものに反映して、「向きの情報」は正常(1)にする。
 * - ファイルの更新日時は、元のまま保つ。
 * 保存したあとは、呼び出し側で、[FileOperations.scan] を呼んで、一覧に反映すること。
 */
@Singleton
class ImageResizer @Inject constructor(
    private val fileOperations: FileOperations,
) {

    /** リサイズに対応している形式か(拡張子で判断する)。 */
    fun isSupported(item: MediaItem): Boolean =
        !item.isVideo && extensionOf(item.displayName) in SupportedExtensions

    /**
     * [item] を、横幅 [targetWidth] px(縦は、縦横比を保つ)に縮小して保存する。
     * @param overwrite true なら、元のファイルを上書き(IDは変わらない)。false なら、「名前_resized.jpg」として、同じフォルダに新しく保存。
     * @return 保存したファイル。失敗したら null。
     */
    suspend fun resize(item: MediaItem, targetWidth: Int, overwrite: Boolean): File? =
        withContext(Dispatchers.IO) {
            val source = fileOperations.queryFilePath(item)?.let(::File)
            if (source == null || !source.exists() || item.width <= 0) return@withContext null

            val originalModified = source.lastModified()
            try {
                val scale = targetWidth.toDouble() / item.width
                resizeFile(source, scale, overwrite)?.also { it.setLastModified(originalModified) }
            } catch (e: Exception) {
                Log.w(TAG, "リサイズに失敗: ${item.uri}", e)
                null
            } catch (e: OutOfMemoryError) {
                // 非常に大きな画像で、メモリが足りないとき。その1件だけを、失敗として数える
                Log.w(TAG, "リサイズに失敗(メモリ不足): ${item.uri}", e)
                null
            }
        }

    private fun resizeFile(source: File, scale: Double, overwrite: Boolean): File? {
        val extension = extensionOf(source.name)
        val isJpeg = extension == "jpg" || extension == "jpeg"

        // JPEGの向き(EXIF)。画像の向きは、ここで、画像そのものに反映する
        val orientation = if (isJpeg) {
            ExifInterface(source.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } else {
            ExifInterface.ORIENTATION_NORMAL
        }
        val swapped = orientation in SwappedOrientations

        // 元の大きさだけを先に調べる(画像は、まだ読み込まない)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        val rawWidth = bounds.outWidth
        val rawHeight = bounds.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) return null

        // 向きを反映したあとの、縮小後の大きさ
        val displayWidth = if (swapped) rawHeight else rawWidth
        val displayHeight = if (swapped) rawWidth else rawHeight
        val finalWidth = (displayWidth * scale).roundToInt().coerceAtLeast(1)
        val finalHeight = (displayHeight * scale).roundToInt().coerceAtLeast(1)
        // 向きを反映する前(撮影したままの向き)での、縮小後の大きさ
        val rawTargetWidth = if (swapped) finalHeight else finalWidth
        val rawTargetHeight = if (swapped) finalWidth else finalHeight

        // 大きな画像は、読み込む段階で、先に小さくする(メモリの節約)。目標より小さくならない範囲で
        var sampleSize = 1
        while (rawWidth / (sampleSize * 2) >= rawTargetWidth && rawHeight / (sampleSize * 2) >= rawTargetHeight) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            source.path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: return null

        val scaled = if (decoded.width == rawTargetWidth && decoded.height == rawTargetHeight) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, rawTargetWidth, rawTargetHeight, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }
        val result = applyOrientation(scaled, orientation)

        // 上書きのときは、同じフォルダの一時ファイルに書いてから、差し替える(途中で失敗しても、元が壊れない)
        val directory = source.parentFile!!
        val target = if (overwrite) {
            File(directory, ".${source.name}.resizing")
        } else {
            fileOperations.uniqueFile(directory, resizedName(source.name))
        }

        return try {
            val (format, quality) = compressSettings(extension)
            val written = FileOutputStream(target).use { result.compress(format, quality, it) }
            if (!written) {
                target.delete()
                return null
            }
            if (isJpeg) copyExif(source, target, finalWidth, finalHeight)

            if (overwrite) {
                if (target.renameTo(source)) {
                    source
                } else {
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
            result.recycle()
        }
    }

    /** EXIFの向き(2〜8)を、画像そのものに反映する。向きが正常(1)なら、そのまま返す。 */
    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (oriented !== bitmap) bitmap.recycle()
        return oriented
    }

    /**
     * 元のJPEGの、撮影日時・位置情報・カメラ情報などを、新しいファイルに写す。
     * Bitmapから保存し直すと、これらが消えてしまうため。向きは、画像に反映済みなので、正常(1)にする。
     */
    private fun copyExif(source: File, target: File, width: Int, height: Int) {
        val from = ExifInterface(source.absolutePath)
        val to = ExifInterface(target.absolutePath)
        for (tag in CopiedExifTags) {
            from.getAttribute(tag)?.let { to.setAttribute(tag, it) }
        }
        to.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        to.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
        to.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
        to.saveAttributes()
    }

    /** 保存の形式と画質。JPEG・WebPは90、PNGは劣化なし(画質の指定は無視される)。 */
    @Suppress("DEPRECATION")
    private fun compressSettings(extension: String): Pair<Bitmap.CompressFormat, Int> = when (extension) {
        "png" -> Bitmap.CompressFormat.PNG to 100
        "webp" -> {
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            format to WebpQuality
        }
        else -> Bitmap.CompressFormat.JPEG to JpegQuality
    }

    /** 「name.jpg」→「name_resized.jpg」。 */
    private fun resizedName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) "${name.substring(0, dot)}_resized${name.substring(dot)}" else "${name}_resized"
    }

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    private companion object {
        const val TAG = "ImageResizer"
        const val JpegQuality = 90
        const val WebpQuality = 90
        val SupportedExtensions = setOf("jpg", "jpeg", "png", "webp")

        /** 縦横が入れ替わる向き(90°・270°回転を含むもの)。 */
        val SwappedOrientations = setOf(
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_ROTATE_270,
        )

        /** 新しいファイルに引き継ぐ、EXIFの項目。 */
        val CopiedExifTags = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_EXPOSURE_PROGRAM,
            ExifInterface.TAG_METERING_MODE,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
        )
    }
}