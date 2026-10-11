package com.sandolpin.gallery.data.settings

/** ダークモード。 */
enum class ThemeMode(val label: String) {
    System("自動(端末の設定に合わせる)"),
    Dark("オン"),
    Light("オフ"),
}

/** 削除するときの、標準の方法。 */
enum class DeleteMethod(val label: String) {
    Trash("ゴミ箱へ移動"),
    Permanent("そのまま削除"),
}

/** フォルダ名を表示する場所(フォルダ画面)。 */
enum class FolderNamePosition(val label: String) {
    Outside("画像の外"),
    Inside("画像の中"),
}

/** 写真・フォルダの一覧の、スクロールの向き。 */
enum class ScrollDirection(val label: String) {
    Vertical("縦"),
    Horizontal("横"),
}

/** ホーム画面に表示する欄(挨拶は、常に表示する)。 */
enum class HomeSection(val label: String) {
    Pinned("ピン留め"),
    Favorites("お気に入り"),
    Pickup("いろいろな写真を見る"),
    Memories("昔に撮った写真を見る"),
}

/**
 * ビューアーの下部に出せる操作。ここに並んでいる順番で表示する。
 * 下部に出さなかった操作は、上部の︙メニューに入る。
 */
enum class ViewerAction(val label: String) {
    Favorite("お気に入り"),
    Edit("編集"),
    Delete("削除"),
    Rotate("回転"),
    Resize("リサイズ"),
    Copy("コピー"),
    Move("移動"),
    Details("詳細情報"),
    Rename("名前の変更"),
    OpenWith("ほかのアプリで開く"),
}

/** ビューアーの下部に出せる操作の、最大の数。 */
const val MaxViewerBottomActions = 5

/** 「すべて」画面の表示形式。 */
enum class AllPhotosLayout(val label: String) {
    Justified("写真によって変化する"),
    Square("正方形に固定"),
}

private const val Megabyte = 1024L * 1024L
private const val Gigabyte = 1024L * Megabyte

/**
 * サムネイルのキャッシュを、どれだけまで保存しておくか(保持容量)。
 * 超えたら、古いものから消える。名前(Mb500 など)で保存するので、名前は変えないこと。
 */
enum class CacheLimit(val label: String, val bytes: Long) {
    Mb10("10 MB", 10 * Megabyte),
    Mb50("50 MB", 50 * Megabyte),
    Mb100("100 MB", 100 * Megabyte),
    Mb250("250 MB", 250 * Megabyte),
    Mb500("500 MB", 500 * Megabyte),
    Mb700("700 MB", 700 * Megabyte),
    Gb1("1 GB", 1 * Gigabyte),
    Gb2("2 GB", 2 * Gigabyte),
    Gb5("5 GB", 5 * Gigabyte),
}

/** ラベルの信頼度のしきい値(%)として、選べる値。 */
val LabelConfidenceOptions = listOf(50, 60, 70, 80, 90)

/**
 * アプリの設定。設定画面で変えると、すぐ、ほかの画面に反映される。
 * ここにない設定(あいまい検索、ビューアーの設定など)は、あとの段階で追加する。
 */
data class AppSettings(
    // テーマ
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,

    // ファイル操作・ゴミ箱
    val deleteMethod: DeleteMethod = DeleteMethod.Trash,
    /** 「ゴミ箱へ移動」のときに、確認ダイアログを出すか。「そのまま削除」のときは、設定にかかわらず、常に確認する。 */
    val confirmDelete: Boolean = true,
    val useTrash: Boolean = true,
    val showTrashInFolders: Boolean = true,

    // 全般
    /** 下に引っ張って、写真の一覧を読み込み直す。 */
    val pullToRefresh: Boolean = true,

    // ビューアー
    /** ビューアーで、バーを隠したとき(画面をタップしたとき)に、ステータスバー・ナビゲーションバーも隠す。 */
    val hideSystemUiInViewer: Boolean = false,
    /** 画面の左右の端をタップして、前/次の写真へ切り替える(拡大中は切り替えない)。 */
    val edgeTapNavigation: Boolean = false,
    /** ズームの最大倍率を、標準より大きくする。 */
    val deepZoom: Boolean = false,
    /** 2本指のひねりで、写真を回す(拡大中は回さない)。回したあと、保存できる。 */
    val rotationGesture: Boolean = false,
    /** ON: ズームしたときに、元の解像度の細部まで表示する。OFF: 画面に収まる大きさの画像だけを表示する(軽くなる)。 */
    val highQualityDisplay: Boolean = true,
    /** ビューアーの下部に出す操作(最大 [MaxViewerBottomActions] 個)。 */
    val viewerBottomActions: Set<ViewerAction> = setOf(ViewerAction.Favorite, ViewerAction.Edit, ViewerAction.Delete),

    // ホーム画面
    val showStorageStats: Boolean = true,
    val homeSections: Set<HomeSection> = HomeSection.entries.toSet(),

    // すべての写真画面
    val allPhotosLayout: AllPhotosLayout = AllPhotosLayout.Justified,
    /** 「正方形に固定」のときの列数(2〜5)。 */
    val allPhotosColumns: Int = 4,

    // 写真・フォルダの一覧
    /** 「すべて」・フォルダ一覧・フォルダ内などの、一覧のスクロールの向き。横のときは、列の代わりに「行」で並ぶ。 */
    val scrollDirection: ScrollDirection = ScrollDirection.Vertical,

    // フォルダ画面
    val pinnedColumns: Int = 2,
    val folderColumns: Int = 3,
    val folderNamePosition: FolderNamePosition = FolderNamePosition.Outside,
    val showFolderCount: Boolean = true,
    val showFileNameInPhotos: Boolean = true,

    // その他
    /** サムネイルのキャッシュの、保持容量。 */
    val cacheLimit: CacheLimit = CacheLimit.Mb500,

    // あいまい検索
    /** ON: 写真の中身を、裏で分析する(山・海などで検索できるようにするため)。 */
    val fuzzySearchEnabled: Boolean = false,
    /** ON: 写真の中の文字も、読み取って、検索できるようにする(「あいまい検索」がONのときだけ、動く)。時間がかかる。 */
    val textRecognitionEnabled: Boolean = false,
    /**
     * ラベル(「海」「犬」など)を、検索やホームの分類に使う、信頼度のしきい値(%)。50〜90(10きざみ)。
     * 分析で保存するのは、50%以上のラベル。使うときに、この値で絞るので、変えても、分析し直さない。
     */
    val labelConfidencePercent: Int = 60,
) {
    /**
     * 削除したときに、ゴミ箱へ移動するか(false なら、そのまま削除)。
     * ゴミ箱が使えない端末(Android 10以下)、「ゴミ箱を使う」がOFF、削除方法が「そのまま削除」のどれかなら、そのまま削除になる。
     */
    fun deletesToTrash(trashSupported: Boolean): Boolean =
        trashSupported && useTrash && deleteMethod == DeleteMethod.Trash
}
