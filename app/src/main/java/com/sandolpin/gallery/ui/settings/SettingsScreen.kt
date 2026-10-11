package com.sandolpin.gallery.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.sandolpin.gallery.data.analysis.AnalysisProgress
import com.sandolpin.gallery.data.analysis.AnalysisStatus
import com.sandolpin.gallery.data.cache.formatCacheSize
import com.sandolpin.gallery.data.settings.AllPhotosLayout
import com.sandolpin.gallery.data.settings.CacheLimit
import com.sandolpin.gallery.data.settings.DeleteMethod
import com.sandolpin.gallery.data.settings.FolderNamePosition
import com.sandolpin.gallery.data.settings.HomeSection
import com.sandolpin.gallery.data.settings.LabelConfidenceOptions
import com.sandolpin.gallery.data.settings.MaxViewerBottomActions
import com.sandolpin.gallery.data.settings.ScrollDirection
import com.sandolpin.gallery.data.settings.ThemeMode
import com.sandolpin.gallery.data.settings.ViewerAction

/** 「ゴミ箱を使う」は、Android 11(API 30)以上で使える。 */
private val TrashSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

/** 「デバイスの色を使う」は、Android 12(API 31)以上で使える。 */
private val DynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** まだ動かない項目に付ける、補足の文字。 */
private const val ComingSoon = "準備中"

/**
 * 設定画面(Material 3)。上部は、スクロールすると小さくなる LargeTopAppBar。
 * カテゴリごとに、見出しと項目(ListItem)を並べる。
 * - ON/OFF は Switch、3択以上はラジオボタンのダイアログ、複数選択はチェックボックスのダイアログ、
 *   列数(1〜5)は SegmentedButton。
 * - まだ動かない項目は、グレーで「準備中」と表示する。
 * 変更は、すぐ保存されて、ほかの画面に反映される。
 *
 * @param onOpenTrash 「ゴミ箱を開く」を押したとき
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenTrash: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsState()
    val analysis by viewModel.analysisProgress.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // ダイアログで選ぶ項目は、どれを開いているかを覚えておく
    var themeDialog by remember { mutableStateOf(false) }
    var deleteMethodDialog by remember { mutableStateOf(false) }
    var homeSectionsDialog by remember { mutableStateOf(false) }
    var folderNameDialog by remember { mutableStateOf(false) }
    var scrollDirectionDialog by remember { mutableStateOf(false) }
    var allPhotosLayoutDialog by remember { mutableStateOf(false) }
    var viewerActionsDialog by remember { mutableStateOf(false) }
    var cacheLimitDialog by remember { mutableStateOf(false) }
    var clearCacheDialog by remember { mutableStateOf(false) }
    var labelConfidenceDialog by remember { mutableStateOf(false) }

    // ---- 設定・お気に入りの書き出し/読み込み(ファイルの選択は、OSの画面を使う) ----
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    // 設定画面を開いたときに、キャッシュの大きさを数える
    LaunchedEffect(viewModel) { viewModel.refreshCacheSize() }
    // 分析の対象の枚数は、開いたときと、分析の状態が変わったときに、数え直す
    LaunchedEffect(viewModel, analysis.status) { viewModel.refreshAnalysisTotal() }
    // 分析中の通知を出すための許可(Android 13以降)。分析をONにしたときに、たずねる
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 許可されなくても、分析は動く(通知が出ないだけ) */ }
    val requestNotificationPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val dateText = remember {
        java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
    }
    // 設定の読み込みは、確認のあとで実行するので、選んだファイルを一時的に覚えておく
    var pendingSettingsUri by remember { mutableStateOf<Uri?>(null) }

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportSettings) }
    val importSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingSettingsUri = uri }
    val exportFavoritesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportFavorites) }
    val importFavoritesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFavorites) }
    // 書き出したファイルは、端末によって MIME タイプが違って見えることがあるので、広めに許可する
    val openTypes = arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("設定") },
                scrollBehavior = scrollBehavior,
                // 上の余白(ステータスバー)は、外側の画面が、すでに空けている
                windowInsets = WindowInsets(0),
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = innerPadding,
        ) {
            // ---- テーマ ----
            category("テーマ")
            item {
                ChoiceItem(
                    title = "ダークモード",
                    summary = settings.themeMode.label,
                    onClick = { themeDialog = true },
                )
            }
            item {
                SwitchItem(
                    title = "デバイスの色を使う",
                    summary = if (DynamicColorSupported) "壁紙に合わせた色にします" else "Android 12以降で使えます",
                    checked = settings.dynamicColor && DynamicColorSupported,
                    enabled = DynamicColorSupported,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }

            // ---- 全般(あとの段階で動かす項目) ----
            category("全般")
            item {
                SwitchItem(
                    title = "あいまい検索",
                    summary = "山、海などで検索できるよう、写真の中身を、端末の中だけで分析します。" +
                        "充電中に、裏で自動で進めます(検索への反映は、あとの段階です)",
                    checked = settings.fuzzySearchEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setFuzzySearchEnabled(enabled)
                        if (enabled) requestNotificationPermission()
                    },
                )
            }
            item {
                SwitchItem(
                    title = "写真内の文字も検索する",
                    summary = "看板やメモなど、写真の中の文字を読み取ります。時間がかかります(1万枚で1〜1.5時間ほど)。" +
                        "オンにすると、分析済みの写真も、文字だけ追加で読み取ります",
                    checked = settings.textRecognitionEnabled,
                    enabled = settings.fuzzySearchEnabled,
                    onCheckedChange = viewModel::setTextRecognitionEnabled,
                )
            }
            item {
                ChoiceItem(
                    title = "今すぐ分析する",
                    summary = analysisSummary(
                        analysis, settings.fuzzySearchEnabled, settings.textRecognitionEnabled,
                    ),
                    enabled = settings.fuzzySearchEnabled && analysis.status == AnalysisStatus.Idle,
                    onClick = viewModel::startAnalysisNow,
                )
            }
            item {
                ChoiceItem(
                    title = "ラベルの信頼度のしきい値",
                    summary = "${settings.labelConfidencePercent}%以上のラベルだけを使います(大きいほど、外れが減り、見つかる写真も減ります。変えても、分析し直しません)",
                    enabled = settings.fuzzySearchEnabled,
                    onClick = { labelConfidenceDialog = true },
                )
            }
            item {
                SwitchItem(
                    title = "下に引っ張って更新",
                    summary = "ホーム・すべて・フォルダの画面で、一覧を読み込み直します",
                    checked = settings.pullToRefresh,
                    onCheckedChange = viewModel::setPullToRefresh,
                )
            }
            item {
                ChoiceItem(
                    title = "写真/フォルダ画面スクロールの向き",
                    summary = if (settings.scrollDirection == ScrollDirection.Horizontal) {
                        "横(「すべて」・フォルダ一覧・フォルダ内。横のときは、下に引っ張って更新は使えません)"
                    } else {
                        "縦(「すべて」・フォルダ一覧・フォルダ内)"
                    },
                    onClick = { scrollDirectionDialog = true },
                )
            }
            item {
                SwitchItem(
                    title = "全画面表示時にシステムUIを隠す",
                    summary = "ビューアーでバーを隠したとき、ステータスバーなども隠します",
                    checked = settings.hideSystemUiInViewer,
                    onCheckedChange = viewModel::setHideSystemUiInViewer,
                )
            }
            item {
                SwitchItem(
                    title = "画面端を押してメディアを切り替える",
                    summary = "ビューアーで、左右の端をタップすると、前/次へ(拡大中は切り替えません)",
                    checked = settings.edgeTapNavigation,
                    onCheckedChange = viewModel::setEdgeTapNavigation,
                )
            }

            // ---- ビューアー(あとの段階で動かす項目) ----
            category("ビューアー")
            item {
                SwitchItem(
                    title = "深いズームを許可",
                    summary = "ズームの最大倍率を、標準より大きくします",
                    checked = settings.deepZoom,
                    onCheckedChange = viewModel::setDeepZoom,
                )
            }
            item {
                SwitchItem(
                    title = "ジェスチャーで画像を回転",
                    summary = "2本指のひねりで、写真を回します(拡大中は回りません)。回したあと、保存できます",
                    checked = settings.rotationGesture,
                    onCheckedChange = viewModel::setRotationGesture,
                )
            }
            item {
                SwitchItem(
                    title = "画像表示時に高画質処理を施す",
                    summary = "オン: 拡大したとき、元の解像度の細部まで表示します。オフ: 画面に収まる大きさの画像だけを表示します(軽くなります)",
                    checked = settings.highQualityDisplay,
                    onCheckedChange = viewModel::setHighQualityDisplay,
                )
            }
            item {
                ChoiceItem(
                    title = "画面下部にアクションを表示",
                    summary = viewerActionsSummary(settings.viewerBottomActions),
                    onClick = { viewerActionsDialog = true },
                )
            }

            // ---- ファイル操作 ----
            category("ファイル操作")
            item {
                val trashAvailable = TrashSupported && settings.useTrash
                ChoiceItem(
                    title = "削除方法",
                    summary = if (trashAvailable) {
                        settings.deleteMethod.label
                    } else {
                        "そのまま削除(ゴミ箱を使わない設定のため)"
                    },
                    enabled = trashAvailable,
                    onClick = { deleteMethodDialog = true },
                )
            }
            item {
                SwitchItem(
                    title = "削除時にダイアログを表示",
                    summary = "「そのまま削除」のときは、安全のため、常に確認します",
                    checked = settings.confirmDelete,
                    onCheckedChange = viewModel::setConfirmDelete,
                )
            }

            // ---- ゴミ箱 ----
            category("ゴミ箱")
            item {
                SwitchItem(
                    title = "ゴミ箱を使う",
                    summary = if (TrashSupported) {
                        "オフにすると、削除は常に、そのまま削除になります"
                    } else {
                        "Android 11以降で使えます"
                    },
                    checked = settings.useTrash && TrashSupported,
                    enabled = TrashSupported,
                    onCheckedChange = viewModel::setUseTrash,
                )
            }
            item {
                SwitchItem(
                    title = "フォルダ画面にゴミ箱を表示",
                    summary = "フォルダ画面の一番下に表示します",
                    checked = settings.showTrashInFolders,
                    enabled = TrashSupported && settings.useTrash,
                    onCheckedChange = viewModel::setShowTrashInFolders,
                )
            }
            item {
                LinkItem(
                    title = "ゴミ箱を開く",
                    enabled = TrashSupported && settings.useTrash,
                    onClick = onOpenTrash,
                )
            }

            // ---- ホーム画面 ----
            category("ホーム画面")
            item {
                ChoiceItem(
                    title = "表示する項目",
                    summary = homeSectionsSummary(settings.homeSections),
                    onClick = { homeSectionsDialog = true },
                )
            }
            item {
                SwitchItem(
                    title = "ストレージ/ファイルの数を表示",
                    summary = "写真・動画の数と、ストレージの使用状況を表示します",
                    checked = settings.showStorageStats,
                    onCheckedChange = viewModel::setShowStorageStats,
                )
            }

            // ---- すべての写真画面(あとの段階で動かす項目) ----
            category("すべての写真画面")
            item {
                ChoiceItem(
                    title = "表示形式",
                    summary = settings.allPhotosLayout.label,
                    onClick = { allPhotosLayoutDialog = true },
                )
            }
            item {
                ColumnsItem(
                    title = "正方形の列数",
                    value = settings.allPhotosColumns,
                    onChange = viewModel::setAllPhotosColumns,
                    range = 2..5,
                    // 「正方形に固定」のときだけ、列数を選べる
                    enabled = settings.allPhotosLayout == AllPhotosLayout.Square,
                )
            }

            // ---- フォルダ画面 ----
            category("フォルダ画面")
            item {
                ColumnsItem(
                    title = "ピン留めの列",
                    value = settings.pinnedColumns,
                    onChange = viewModel::setPinnedColumns,
                )
            }
            item {
                ColumnsItem(
                    title = "フォルダの列",
                    value = settings.folderColumns,
                    onChange = viewModel::setFolderColumns,
                )
            }
            item {
                ChoiceItem(
                    title = "フォルダ名表示の場所",
                    summary = settings.folderNamePosition.label,
                    onClick = { folderNameDialog = true },
                )
            }
            item {
                SwitchItem(
                    title = "フォルダに写真の枚数を表示",
                    checked = settings.showFolderCount,
                    onCheckedChange = viewModel::setShowFolderCount,
                )
            }
            item {
                SwitchItem(
                    title = "写真内にファイル名を表示",
                    summary = "フォルダ内画面のサムネイルに表示します",
                    checked = settings.showFileNameInPhotos,
                    onCheckedChange = viewModel::setShowFileNameInPhotos,
                )
            }

            // ---- その他 ----
            category("その他")
            item {
                ChoiceItem(
                    title = "設定を書き出す",
                    summary = "いまの設定とピン留めフォルダを、ファイルに保存します",
                    onClick = { exportSettingsLauncher.launch("gallery_settings_$dateText.json") },
                )
            }
            item {
                ChoiceItem(
                    title = "設定を読み込む",
                    summary = "書き出したファイルから、設定を復元します",
                    onClick = { importSettingsLauncher.launch(openTypes) },
                )
            }
            item {
                ChoiceItem(
                    title = "お気に入りを書き出す",
                    summary = "お気に入りの写真の一覧を、ファイルに保存します",
                    onClick = { exportFavoritesLauncher.launch("gallery_favorites_$dateText.json") },
                )
            }
            item {
                ChoiceItem(
                    title = "お気に入りを読み込む",
                    summary = "書き出したファイルのお気に入りを、いまのお気に入りに追加します",
                    onClick = { importFavoritesLauncher.launch(openTypes) },
                )
            }
            item {
                ChoiceItem(
                    title = "キャッシュを削除する",
                    summary = "一覧用の小さな画像(サムネイル)を削除します。現在 ${formatCacheSize(cacheSizeBytes)}",
                    onClick = { clearCacheDialog = true },
                )
            }
            item {
                ChoiceItem(
                    title = "キャッシュの保持容量",
                    summary = "${settings.cacheLimit.label}(超えたら、古いものから削除します)",
                    onClick = { cacheLimitDialog = true },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // ---- 選択のダイアログ ----
    pendingSettingsUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingSettingsUri = null },
            title = { Text("設定を読み込みますか?") },
            text = { Text("いまの設定は、ファイルの内容で置き換わります。ピン留めフォルダも、ファイルの内容に合わせて変わります。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingSettingsUri = null
                    viewModel.importSettings(uri)
                }) { Text("読み込む") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSettingsUri = null }) { Text("キャンセル") }
            },
        )
    }
    if (clearCacheDialog) {
        AlertDialog(
            onDismissRequest = { clearCacheDialog = false },
            title = { Text("キャッシュを削除しますか?") },
            text = {
                Text("一覧用の小さな画像(サムネイル)を、すべて削除します。写真や動画そのものは、消えません。次に表示するときに、作り直されます(少し時間がかかります)。")
            },
            confirmButton = {
                TextButton(onClick = {
                    clearCacheDialog = false
                    viewModel.clearCache()
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { clearCacheDialog = false }) { Text("キャンセル") }
            },
        )
    }
    if (labelConfidenceDialog) {
        SingleChoiceDialog(
            title = "ラベルの信頼度のしきい値",
            options = LabelConfidenceOptions,
            labelOf = { "$it%" },
            selected = settings.labelConfidencePercent,
            onSelect = viewModel::setLabelConfidencePercent,
            onDismiss = { labelConfidenceDialog = false },
        )
    }
    if (cacheLimitDialog) {
        SingleChoiceDialog(
            title = "キャッシュの保持容量",
            options = CacheLimit.entries,
            labelOf = { it.label },
            selected = settings.cacheLimit,
            onSelect = viewModel::setCacheLimit,
            onDismiss = { cacheLimitDialog = false },
        )
    }
    if (themeDialog) {
        SingleChoiceDialog(
            title = "ダークモード",
            options = ThemeMode.entries,
            labelOf = { it.label },
            selected = settings.themeMode,
            onSelect = viewModel::setThemeMode,
            onDismiss = { themeDialog = false },
        )
    }
    if (deleteMethodDialog) {
        SingleChoiceDialog(
            title = "削除方法",
            options = DeleteMethod.entries,
            labelOf = { it.label },
            selected = settings.deleteMethod,
            onSelect = viewModel::setDeleteMethod,
            onDismiss = { deleteMethodDialog = false },
        )
    }
    if (viewerActionsDialog) {
        MultiChoiceDialog(
            title = "画面下部にアクションを表示",
            options = ViewerAction.entries,
            labelOf = { it.label },
            selected = settings.viewerBottomActions,
            onConfirm = viewModel::setViewerBottomActions,
            onDismiss = { viewerActionsDialog = false },
            maxSelection = MaxViewerBottomActions,
            note = "最大${MaxViewerBottomActions}個まで選べます。選ばなかった操作は、上部の︙メニューに入ります。",
        )
    }
    if (allPhotosLayoutDialog) {
        SingleChoiceDialog(
            title = "表示形式",
            options = AllPhotosLayout.entries,
            labelOf = { it.label },
            selected = settings.allPhotosLayout,
            onSelect = viewModel::setAllPhotosLayout,
            onDismiss = { allPhotosLayoutDialog = false },
        )
    }
    if (scrollDirectionDialog) {
        SingleChoiceDialog(
            title = "スクロールの向き",
            options = ScrollDirection.entries,
            labelOf = { it.label },
            selected = settings.scrollDirection,
            onSelect = viewModel::setScrollDirection,
            onDismiss = { scrollDirectionDialog = false },
        )
    }
    if (folderNameDialog) {
        SingleChoiceDialog(
            title = "フォルダ名表示の場所",
            options = FolderNamePosition.entries,
            labelOf = { it.label },
            selected = settings.folderNamePosition,
            onSelect = viewModel::setFolderNamePosition,
            onDismiss = { folderNameDialog = false },
        )
    }
    if (homeSectionsDialog) {
        MultiChoiceDialog(
            title = "表示する項目",
            options = HomeSection.entries,
            labelOf = { it.label },
            selected = settings.homeSections,
            onConfirm = viewModel::setHomeSections,
            onDismiss = { homeSectionsDialog = false },
        )
    }
}

/** 「分析済み 120 / 8,000枚」のように、写真の分析の進み具合を表す補足の文字。 */
private fun analysisSummary(
    analysis: AnalysisProgress,
    enabled: Boolean,
    textEnabled: Boolean,
): String {
    if (!enabled) return "「あいまい検索」をオンにすると、使えます"
    val total = analysis.total
    var progress = "分析済み %,d / %,d枚(ラベル付き %,d枚)".format(
        analysis.analyzed.coerceAtMost(total), total, analysis.labeled,
    )
    if (textEnabled) {
        progress += "\n文字認識済み %,d / %,d枚".format(analysis.recognized.coerceAtMost(total), total)
    }
    val allDone = total > 0 && analysis.analyzed >= total && (!textEnabled || analysis.recognized >= total)
    return when (analysis.status) {
        AnalysisStatus.Running -> "分析中… $progress"
        AnalysisStatus.Waiting -> "開始を待っています… $progress"
        AnalysisStatus.Idle ->
            if (allDone) "$progress\n(すべて分析しました)"
            else "$progress\n(押すと、いま分析を始めます)"
    }
}

/** 「お気に入り、編集、削除」のように、下部に出す操作を並べた補足の文字。 */
private fun viewerActionsSummary(selected: Set<ViewerAction>): String {
    val names = ViewerAction.entries.filter { it in selected }.map { it.label }
    return if (names.isEmpty()) "なし(すべて、上部の︙メニューに入ります)" else names.joinToString("、")
}

/** 「ピン留め、お気に入り」のように、表示する項目を並べた補足の文字。 */
private fun homeSectionsSummary(selected: Set<HomeSection>): String {
    val names = HomeSection.entries.filter { it in selected }.map { it.label }
    return if (names.isEmpty()) "なし(挨拶だけ)" else names.joinToString("、")
}

// ---- 部品(Material 3 の ListItem を使う) ----

/** カテゴリの見出し(テーマカラーの小さな文字)。 */
private fun LazyListScope.category(title: String) {
    item {
        Column {
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
    }
}

/** ON/OFFの項目(右端に Switch)。項目のどこを押しても切り替わる。 */
@Composable
private fun SwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = itemColors(enabled),
    )
}

/** 選んで決める項目(いまの選択を補足に表示し、押すとダイアログを開く)。 */
@Composable
private fun ChoiceItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        colors = itemColors(enabled),
    )
}

/** 別の画面へ移る項目(右に「>」)。 */
@Composable
private fun LinkItem(
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title) },
        trailingContent = {
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        colors = itemColors(enabled),
    )
}

/** 列数の項目([range] は、選べる列数。いまは 1〜5 か 2〜5)。項目の下に、SegmentedButton を置く。 */
@Composable
private fun ColumnsItem(
    title: String,
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange = 1..5,
    enabled: Boolean = true,
) {
    Column {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(if (enabled) "${value}列" else "${value}列(「正方形に固定」のときに使います)") },
            colors = itemColors(enabled),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            val options = range.toList()
            options.forEachIndexed { index, columns ->
                SegmentedButton(
                    selected = columns == value,
                    onClick = { onChange(columns) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    enabled = enabled,
                ) {
                    Text("$columns")
                }
            }
        }
    }
}

/** まだ動かない項目(グレーで、「準備中」と表示する)。押しても、何も起きない。 */
@Composable
private fun PlaceholderItem(
    title: String,
    summary: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(if (summary == null) ComingSoon else "$summary($ComingSoon)") },
        colors = itemColors(enabled = false),
    )
}

/** 項目の色。使えない項目(enabled が false)は、薄い文字にする。 */
@Composable
private fun itemColors(enabled: Boolean) =
    if (enabled) {
        ListItemDefaults.colors()
    } else {
        val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ListItemDefaults.colors(
            headlineColor = dim,
            supportingColor = dim,
            trailingIconColor = dim,
        )
    }

// ---- ダイアログ ----

/** 1つだけ選ぶダイアログ(ラジオボタン)。選んだら、すぐ閉じる。 */
@Composable
private fun <T> SingleChoiceDialog(
    title: String,
    options: List<T>,
    labelOf: (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // 選択肢が多い(キャッシュの保持容量など)ときは、スクロールできるようにする
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option)
                                onDismiss()
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            text = labelOf(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

/**
 * 複数選ぶダイアログ(チェックボックス)。「OK」を押したときに、まとめて保存する。
 * @param maxSelection 選べる最大の数。上限に達したら、選んでいないものは、押せなくなる。null なら、制限なし。
 * @param note 選択肢の下に出す、説明の文字。
 */
@Composable
private fun <T> MultiChoiceDialog(
    title: String,
    options: List<T>,
    labelOf: (T) -> String,
    selected: Set<T>,
    onConfirm: (Set<T>) -> Unit,
    onDismiss: () -> Unit,
    maxSelection: Int? = null,
    note: String? = null,
) {
    var current by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    val checked = option in current
                    // 上限に達していたら、選んでいないものは、押せない
                    val enabled = checked || maxSelection == null || current.size < maxSelection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                current = if (checked) current - option else current + option
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                        Text(
                            text = labelOf(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(current)
                    onDismiss()
                },
            ) { Text("OK") }
        },
    )
}
