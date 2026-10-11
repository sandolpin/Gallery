package com.sandolpin.gallery.ui.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.sandolpin.gallery.data.model.MediaItem

private fun mimeTypeOf(context: Context, item: MediaItem): String =
    context.contentResolver.getType(item.uri) ?: if (item.isVideo) "video/*" else "image/*"

/** 共有(他のアプリへ送る)。 */
fun shareMedia(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeTypeOf(context, item)
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/** 編集(他の編集アプリを呼び出す)。 */
fun editMedia(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_EDIT).apply {
        setDataAndType(item.uri, mimeTypeOf(context, item))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "編集できるアプリが見つかりません", Toast.LENGTH_SHORT).show()
    }
}

/** 一度に共有できる最大件数。あまり多いと、Androidのデータ受け渡しの上限を超えるため。 */
const val MaxShareCount = 500

/**
 * 複数の写真・動画を、まとめて共有する(選択モードの共有ボタン用)。
 * 1件なら通常の共有、2件以上なら複数共有にする。
 * 写真と動画が混ざっているときは、種類を「*」にする。
*/
fun shareMediaList(context: Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    if (items.size == 1) {
        shareMedia(context, items.first())
        return
    }
    if (items.size > MaxShareCount) {
        Toast.makeText(context, "一度に共有できるのは${MaxShareCount}件までです", Toast.LENGTH_SHORT).show()
        return
    }
    val type = when {
        items.all { !it.isVideo } -> "image/*"
        items.all { it.isVideo } -> "video/*"
        else -> "*/*"
    }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        this.type = type
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/** ほかのアプリで開く(ブラウザや、ほかのギャラリー・画像ビューアーなどを選べる)。 */
fun openWithOtherApp(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.uri, mimeTypeOf(context, item))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, null))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "開けるアプリが見つかりません", Toast.LENGTH_SHORT).show()
    }
}