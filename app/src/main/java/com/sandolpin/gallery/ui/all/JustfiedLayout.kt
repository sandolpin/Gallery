package com.sandolpin.gallery.ui.all

import com.sandolpin.gallery.data.model.MediaItem
import java.time.LocalDate

/** 同じ日に撮った写真のまとまり。 */
data class DayGroup(
    val date: LocalDate,
    val items: List<MediaItem>,
)

/** 行の中の写真1枚。表示する幅(dp)を、計算済みで持つ。 */
data class RowPhoto(
    val media: MediaItem,
    val widthDp: Float,
)

/** 「すべて」画面の縦スクロールリストの1項目(日付見出し、または写真の1行)。 */
sealed interface AllListItem {
    /** LazyColumnのキー。重複しないこと。 */
    val key: String

    /** この項目が属する日付。ヘッダーの年月表示に使う。 */
    val date: LocalDate

    data class HeaderItem(override val date: LocalDate) : AllListItem {
        override val key: String get() = "h_$date"
    }

    data class RowItem(
        val photos: List<RowPhoto>,
        val heightDp: Float,
        override val date: LocalDate,
    ) : AllListItem {
        override val key: String get() = "r_${photos.first().media.id}"
    }
}

/** 極端に細長い写真で、行のバランスが崩れないよう、縦横比の範囲を制限する。 */
private const val MIN_ASPECT = 0.5f
private const val MAX_ASPECT = 2.5f

private fun MediaItem.layoutAspect(): Float = aspectRatio.coerceIn(MIN_ASPECT, MAX_ASPECT)

/**
 * 写真の縦横比に合わせて、1行の高さをそろえて並べる(Googleフォトと同じ形式)。
 *
 * 1. 目標の高さ [targetRowHeightDp] で左から順に並べ、合計の幅が
 *    [availableWidthDp] に届いたところで行を区切る。
 * 2. 区切った行は、幅がちょうど [availableWidthDp] になるよう、高さを調整する。
 * 3. 日付が変わるところでは必ず行を区切り、見出しを入れる。
 *    日付の最後の行は、引き伸ばさずに目標の高さのまま左詰めにする。
 *
 * 写真の中身は読み込まず、幅と高さだけで計算するため、1万枚規模でも高速に動く。
 */
fun buildJustifiedItems(
    days: List<DayGroup>,
    availableWidthDp: Float,
    targetRowHeightDp: Float = 120f,
    spacingDp: Float = 2f,
): List<AllListItem> {
    if (availableWidthDp <= 0f) return emptyList()

    val out = ArrayList<AllListItem>()

    for (day in days) {
        out += AllListItem.HeaderItem(day.date)

        var row = ArrayList<MediaItem>()
        var sumAspect = 0f

        fun flush(fillWidth: Boolean) {
            if (row.isEmpty()) return
            val gaps = spacingDp * (row.size - 1)
            val height = if (fillWidth) {
                (availableWidthDp - gaps) / sumAspect
            } else {
                targetRowHeightDp
            }
            out += AllListItem.RowItem(
                photos = row.map { RowPhoto(it, it.layoutAspect() * height) },
                heightDp = height,
                date = day.date,
            )
            row = ArrayList()
            sumAspect = 0f
        }

        for (item in day.items) {
            row += item
            sumAspect += item.layoutAspect()
            val widthAtTarget = sumAspect * targetRowHeightDp + spacingDp * (row.size - 1)
            if (widthAtTarget >= availableWidthDp) {
                flush(fillWidth = true)
            }
        }
        flush(fillWidth = false)
    }

    return out
}

/**
 * すべての写真を、同じ大きさの正方形にして、[columns] 列で並べる(設定「正方形に固定」のとき)。
 * 日付が変わるところでは、必ず行を区切って、見出しを入れる(日付の最後の行は、左詰め)。
 * 戻り値は、[buildJustifiedItems] と同じ形なので、「すべて」画面は、そのまま表示できる。
 */
fun buildSquareItems(
    days: List<DayGroup>,
    availableWidthDp: Float,
    columns: Int,
    spacingDp: Float = 2f,
): List<AllListItem> {
    if (availableWidthDp <= 0f || columns < 1) return emptyList()

    val cellDp = (availableWidthDp - spacingDp * (columns - 1)) / columns
    val out = ArrayList<AllListItem>()

    for (day in days) {
        out += AllListItem.HeaderItem(day.date)
        for (chunk in day.items.chunked(columns)) {
            out += AllListItem.RowItem(
                photos = chunk.map { RowPhoto(it, cellDp) },
                heightDp = cellDp,
                date = day.date,
            )
        }
    }
    return out
}

// ---------------------------------------------------------------------------------------------
// 横スクロール用(「すべて」画面の、スクロールの向きが「横」のとき)
// 縦スクロールの「行」を、「列」に置き換えたもの。写真は、列の中で、上から下へ積む。
// ---------------------------------------------------------------------------------------------

/** 列の中の写真1枚。表示する高さ(dp)を、計算済みで持つ。 */
data class ColumnPhoto(
    val media: MediaItem,
    val heightDp: Float,
)

/**
 * 横スクロールのリストの1項目 = 写真の1列。
 * 日付の見出し(ピルと横線)は、日ごとの先頭の列にピルを、すべての列に横線を、上に付けて作る。
 *
 * @param isFirstOfDay その日の最初の列(日付のピルを付ける)
 * @param isLastOfDay その日の最後の列(横線を、ここで終える)
 */
data class DayColumn(
    val photos: List<ColumnPhoto>,
    val widthDp: Float,
    val date: LocalDate,
    val isFirstOfDay: Boolean = false,
    val isLastOfDay: Boolean = false,
) {
    /** LazyRowのキー。重複しないこと。 */
    val key: String get() = "c_${photos.first().media.id}"
}

/** 日ごとの列のリストに、「最初の列」「最後の列」の印を付ける。 */
private fun markDayEnds(columns: List<DayColumn>): List<DayColumn> =
    columns.mapIndexed { index, column ->
        column.copy(isFirstOfDay = index == 0, isLastOfDay = index == columns.lastIndex)
    }

/**
 * 写真の縦横比に合わせて、1列の幅をそろえて並べる([buildJustifiedItems] の、縦と横を入れ替えたもの)。
 *
 * 1. 目標の幅 [targetColumnWidthDp] で上から順に積み、合計の高さが [availableHeightDp] に届いたところで列を区切る。
 * 2. 区切った列は、高さがちょうど [availableHeightDp] になるよう、幅を調整する。
 * 3. 日付が変わるところでは必ず列を区切る。日付の最後の列は、引き伸ばさずに目標の幅のまま、上詰めにする。
 */
fun buildJustifiedColumns(
    days: List<DayGroup>,
    availableHeightDp: Float,
    targetColumnWidthDp: Float = 200f,
    spacingDp: Float = 2f,
): List<DayColumn> {
    if (availableHeightDp <= 0f) return emptyList()

    val out = ArrayList<DayColumn>()

    for (day in days) {
        val dayColumns = ArrayList<DayColumn>()
        var column = ArrayList<MediaItem>()
        // 写真の「高さ ÷ 幅」の合計(幅が1のときの、積んだ高さ)
        var sumInverse = 0f

        fun flush(fillHeight: Boolean) {
            if (column.isEmpty()) return
            val gaps = spacingDp * (column.size - 1)
            val width = if (fillHeight) {
                (availableHeightDp - gaps) / sumInverse
            } else {
                targetColumnWidthDp
            }
            dayColumns += DayColumn(
                photos = column.map { ColumnPhoto(it, width / it.layoutAspect()) },
                widthDp = width,
                date = day.date,
            )
            column = ArrayList()
            sumInverse = 0f
        }

        for (item in day.items) {
            column += item
            sumInverse += 1f / item.layoutAspect()
            val heightAtTarget = sumInverse * targetColumnWidthDp + spacingDp * (column.size - 1)
            if (heightAtTarget >= availableHeightDp) {
                flush(fillHeight = true)
            }
        }
        flush(fillHeight = false)

        out += markDayEnds(dayColumns)
    }

    return out
}

/**
 * すべての写真を、同じ大きさの正方形にして、[rows] 行で並べる(設定「正方形に固定」のとき)。
 * 日付が変わるところでは、必ず列を区切る(日付の最後の列は、上詰め)。
 */
fun buildSquareColumns(
    days: List<DayGroup>,
    availableHeightDp: Float,
    rows: Int,
    spacingDp: Float = 2f,
): List<DayColumn> {
    if (availableHeightDp <= 0f || rows < 1) return emptyList()

    val cellDp = (availableHeightDp - spacingDp * (rows - 1)) / rows
    val out = ArrayList<DayColumn>()

    for (day in days) {
        val dayColumns = day.items.chunked(rows).map { chunk ->
            DayColumn(
                photos = chunk.map { ColumnPhoto(it, cellDp) },
                widthDp = cellDp,
                date = day.date,
            )
        }
        out += markDayEnds(dayColumns)
    }
    return out
}
