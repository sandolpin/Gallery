package com.sandolpin.gallery.data.local.mediastore

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.sandolpin.gallery.data.model.GeoPoint
import com.sandolpin.gallery.data.model.MediaDetails
import com.sandolpin.gallery.data.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 詳細情報ダイアログ用に、1枚分の追加情報(保存場所・位置情報・地名)を読み込む。
 * 一覧では読み込まない(1万枚規模で重くなるため)。ダイアログを開いたときだけ呼ぶ。
 */
@Singleton
class MediaDetailsLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 保存場所と位置情報(緯度経度)を読む。地名は、通信が必要なため [placeName] で別に取る。 */
    suspend fun load(item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
        MediaDetails(
            folderPath = queryFolderPath(item),
            location = readLocation(item),
        )
    }

    /** 緯度経度から、「群馬県高崎市付近」のような地名を作る。取得できなければ null。 */
    suspend fun placeName(point: GeoPoint): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            val geocoder = Geocoder(context, Locale.JAPAN)
            val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(
                        point.latitude, point.longitude, 1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                cont.resume(null)
                            }
                        },
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()
            }
            address?.let(::formatPlace)
        } catch (e: Exception) {
            Log.w(TAG, "地名の取得に失敗", e)
            null
        }
    }

    /** 「群馬県」+「高崎市」+「付近」。都道府県・市区町村のどちらも取れなければ null。 */
    private fun formatPlace(address: Address): String? {
        val area = address.adminArea
        val city = address.locality ?: address.subAdminArea
        val name = listOfNotNull(area, city).joinToString("")
        return if (name.isBlank()) null else "${name}付近"
    }

    // ---- 保存場所 ----

    /** 「内部/DCIM/Camera」「SDカード/Pictures」のような形にする。 */
    private fun queryFolderPath(item: MediaItem): String {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.VOLUME_NAME,
                )
                context.contentResolver.query(item.uri, projection, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val relative = c.getString(0).orEmpty().trimEnd('/')
                        val volume = volumeLabel(c.getString(1))
                        return if (relative.isEmpty()) volume else "$volume/$relative"
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                context.contentResolver.query(item.uri, projection, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val parent = File(c.getString(0).orEmpty()).parent.orEmpty()
                        @Suppress("DEPRECATION")
                        val root = Environment.getExternalStorageDirectory().absolutePath
                        return if (parent.startsWith(root)) "内部" + parent.removePrefix(root) else parent
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "保存場所の取得に失敗", e)
        }
        return item.folderName
    }

    private fun volumeLabel(volumeName: String?): String =
        if (volumeName == null || volumeName == MediaStore.VOLUME_EXTERNAL_PRIMARY) "内部" else "SDカード"

    // ---- 位置情報 ----

    private fun readLocation(item: MediaItem): GeoPoint? = try {
        if (item.isVideo) readVideoLocation(item.uri) else readPhotoLocation(item.uri)
    } catch (e: Exception) {
        Log.w(TAG, "位置情報の読み取りに失敗", e)
        null
    }

    /**
     * Android 10以降は、位置情報が、通常の読み取りでは隠される。
     * 「元のファイルを要求する」URIで読む(ACCESS_MEDIA_LOCATION の許可が必要)。
     * 許可がなくて失敗したら、通常のURIで読み直す。
     */
    private fun candidateUris(uri: Uri): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(MediaStore.setRequireOriginal(uri), uri)
        } else {
            listOf(uri)
        }

    private fun readPhotoLocation(uri: Uri): GeoPoint? {
        for (candidate in candidateUris(uri)) {
            try {
                context.contentResolver.openInputStream(candidate)?.use { stream ->
                    ExifInterface(stream).latLong?.let { return GeoPoint(it[0], it[1]) }
                }
            } catch (e: Exception) {
                // 許可がない場合など。次の候補で読み直す
            }
        }
        return null
    }

    /** 動画の位置情報は、「+35.6762+139.6503/」のような文字(ISO 6709)で入っている。 */
    private fun readVideoLocation(uri: Uri): GeoPoint? {
        val retriever = MediaMetadataRetriever()
        try {
            for (candidate in candidateUris(uri)) {
                try {
                    retriever.setDataSource(context, candidate)
                    val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                    parseIso6709(raw)?.let { return it }
                } catch (e: Exception) {
                    // 許可がない場合など。次の候補で読み直す
                }
            }
        } finally {
            retriever.release()
        }
        return null
    }

    private fun parseIso6709(raw: String?): GeoPoint? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)").find(raw) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return GeoPoint(lat, lon)
    }

    private companion object {
        const val TAG = "MediaDetailsLoader"
    }
}