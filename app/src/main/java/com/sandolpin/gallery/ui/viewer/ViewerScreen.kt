package com.sandolpin.gallery.ui.viewer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.sandolpin.gallery.ui.components.DeleteConfirmDialog
import com.sandolpin.gallery.ui.components.RenameDialog
import com.sandolpin.gallery.ui.components.rememberMediaImageRequest
import com.sandolpin.gallery.ui.fileop.FileOperationType
import com.sandolpin.gallery.ui.fileop.LocalFileOperationController
import com.sandolpin.gallery.ui.resize.LocalResizeController
import com.sandolpin.gallery.ui.rotation.LocalRotationController
import com.sandolpin.gallery.ui.settings.LocalAppSettings
import kotlinx.coroutines.launch
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.settings.ViewerAction
import kotlin.math.roundToInt
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/** 「深いズーム」がONのときの、ズームの最大倍率。OFFのときは、ライブラリの標準の倍率を使う。 */
private const val DeepMaxZoomFactor = 16f

/** 「高画質処理」がOFFのときに、読み込む画像の大きさ(長辺のpx)。 */
private const val StandardImageSizePx = 2048

/** 画面の左右の端として扱う幅(画面の幅に対する割合)。「画面端を押して切り替える」で使う。 */
private const val EdgeTapFraction = 0.2f

/** これより大きく拡大していたら、「拡大中」とみなす(ズームの割合: 0 = 拡大なし、1 = 最大)。 */
private const val ZoomedThreshold = 0.02f

/** 下部のアクションバーの高さ。動画の操作パネルを、そのすぐ上に置くために使う。 */
internal val ViewerBottomBarHeight = 72.dp

/**
 * ビューアー(写真を1枚表示する画面)。画面全体に重ねて表示する。
 * - 左右のスワイプで前後の写真へ移動
 * - ピンチ・ダブルタップでズーム(拡大中は、端まで動かすと次の写真へ移れる)
 * - 画面のタップで、上下のバーの表示/非表示を切り替え
 * 動画の再生は VideoPlayerPage.kt、開閉のアニメーションは段階2-Dで追加する。
 */
@Composable
fun ViewerScreen(
    state: ViewerState,
    onClose: () -> Unit,
) {
    // 削除したら、この一覧から取り除く(開いた時点の一覧のコピーを、ここで持つ)
    var items by remember { mutableStateOf(state.items) }
    val viewerViewModel: ViewerViewModel = hiltViewModel()
    val fileOperations = LocalFileOperationController.current
    val rotation = LocalRotationController.current
    val resize = LocalResizeController.current

    // 回転ジェスチャーで回した、見た目の角度(度)。写真のID → 角度。保存するまでは、見た目だけで、ファイルは変わらない
    var pendingRotations by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }

    // 上書きで回転した写真は、画像の「版」を変えて、ビューアーの表示を、回転後の画像に読み直す
    LaunchedEffect(rotation) {
        rotation.rotatedIds.collect { rotatedIds ->
            // 保存した写真は、ファイルが回った。見た目だけの回転は、0に戻す(二重に回らないように)
            pendingRotations = pendingRotations - rotatedIds
            items = items.map {
                if (it.id in rotatedIds) it.copy(imageVersion = "${it.imageVersion}_v${System.nanoTime()}") else it
            }
        }
    }

    // 上書きでリサイズした写真も、同じように、画像の「版」を変えて、リサイズ後の画像に読み直す
    LaunchedEffect(resize) {
        resize.resizedIds.collect { resizedIds ->
            items = items.map {
                if (it.id in resizedIds) it.copy(imageVersion = "${it.imageVersion}_v${System.nanoTime()}") else it
            }
        }
    }

    // 移動が終わって、見ていた写真が、このフォルダからなくなったら、この一覧から取り除く
    // (最後の1枚なら閉じる)
    LaunchedEffect(fileOperations) {
        fileOperations.movedIds.collect { movedIds ->
            items = items.filter { it.id !in movedIds }
            if (items.isEmpty()) onClose()
        }
    }
    val favoriteIds by viewerViewModel.favoriteIds.collectAsState()
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    // 設定: 削除方法(ゴミ箱へ移動/そのまま削除)と、確認ダイアログの有無
    val settings = LocalAppSettings.current
    val toTrash = settings.deletesToTrash(viewerViewModel.isTrashSupported)
    var showDetails by remember { mutableStateOf(false) }
    // 名前を変更する写真(null なら、ダイアログは出さない)
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    val detailsContext = LocalContext.current

    // 位置情報(EXIF)の読み取りには、Android 10以降、ACCESS_MEDIA_LOCATION の許可が必要。
    // 許可されなくても、詳細情報は表示する(位置情報の行だけが出ない)
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { showDetails = true }
    val openDetails: () -> Unit = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    detailsContext, Manifest.permission.ACCESS_MEDIA_LOCATION,
                ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else {
            showDetails = true
        }
    }

    // 最後の1枚を削除した直後、閉じるアニメーションの間に、空の一覧で描画されないようにする
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = state.startIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )
    var controlsVisible by remember { mutableStateOf(true) }
    val currentItem = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]
    val context = LocalContext.current

    // いま見ている写真を削除する。成功したら、一覧から取り除く(ページは、次の写真に自然につながる)。最後の1枚なら閉じる
    val deleteCurrent: (permanent: Boolean) -> Unit = { permanent ->
        val target = currentItem
        scope.launch {
            val ok = viewerViewModel.delete(target, permanent)
            if (ok) {
                Toast.makeText(
                    context,
                    if (permanent) "削除しました" else "ゴミ箱へ移動しました",
                    Toast.LENGTH_SHORT,
                ).show()
                items = items.filter { it.id != target.id }
                if (items.isEmpty()) onClose()
            } else {
                Toast.makeText(context, "削除できませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 設定で「全画面表示時にシステムUIを隠す」がONなら、バーを隠している間は、ステータスバーなども隠す
    HideSystemBarsEffect(hide = settings.hideSystemUiInViewer && !controlsVisible)

    // 画面をタップしたとき。「画面端を押して切り替える」がONで、拡大していなければ、左右の端は、前/次の写真へ。
    // それ以外(中央・拡大中)は、バーの表示/非表示を切り替える
    val onTapAt: (x: Float, width: Int, zoomed: Boolean) -> Unit = { x, width, zoomed ->
        if (settings.edgeTapNavigation && !zoomed && width > 0) {
            val edge = width * EdgeTapFraction
            val page = pagerState.currentPage
            when {
                x < edge -> if (page > 0) scope.launch { pagerState.animateScrollToPage(page - 1) }
                x > width - edge -> if (page < items.lastIndex) scope.launch { pagerState.animateScrollToPage(page + 1) }
                else -> controlsVisible = !controlsVisible
            }
        } else {
            controlsVisible = !controlsVisible
        }
        // (最後の式の値を、使わないことを明確にする)
        Unit
    }

    // 操作(下部のボタン・上部の︙メニュー)を、実行する
    val isFavorite = currentItem.id in favoriteIds
    val performAction: (ViewerAction) -> Unit = { action ->
        when (action) {
            ViewerAction.Favorite -> viewerViewModel.toggleFavorite(currentItem.id)
            ViewerAction.Edit -> editMedia(context, currentItem)
            ViewerAction.Delete -> {
                // ゴミ箱へ移動で、確認がオフなら、すぐ実行。「そのまま削除」は、常に確認する
                if (toTrash && !settings.confirmDelete) deleteCurrent(false) else showDeleteDialog = true
            }
            ViewerAction.Rotate -> rotation.start(listOf(currentItem))
            ViewerAction.Resize -> resize.start(listOf(currentItem))
            ViewerAction.Copy -> fileOperations.start(FileOperationType.Copy, listOf(currentItem))
            ViewerAction.Move -> fileOperations.start(FileOperationType.Move, listOf(currentItem))
            ViewerAction.Details -> openDetails()
            ViewerAction.Rename -> renameTarget = currentItem
            ViewerAction.OpenWith -> openWithOtherApp(context, currentItem)
        }
        // (最後の式の値を、使わないことを明確にする)
        Unit
    }
    // 下部に出す操作は、設定で選んだもの。残りは、上部の︙メニューに入る
    val bottomActions = ViewerAction.entries.filter { it in settings.viewerBottomActions }
    val menuActions = ViewerAction.entries.filter { it !in settings.viewerBottomActions }

    BackHandler(onBack = onClose)

    // スワイプで別の写真に移ったら、目印をつける写真を切り替える
    // (閉じるときに、その写真のサムネイルに向かって縮むようにするため)
    val viewerController = LocalViewerController.current
    LaunchedEffect(pagerState.settledPage) {
        items.getOrNull(pagerState.settledPage)?.let { viewerController.onPageChanged(it.id) }
    }

    // 別の写真に移ったら、保存していない回転は、捨てて、元の向きに戻す
    LaunchedEffect(pagerState.settledPage) { pendingRotations = emptyMap() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 前後1枚を、先に読み込んでおく(1万枚を全部は読み込まない)
            beyondViewportPageCount = 1,
            key = { page -> items[page].id },
        ) { page ->
            ViewerPage(
                item = items[page],
                // 動画のプレイヤーは、スワイプが落ち着いたあとの、表示中のページだけで作る
                isCurrent = page == pagerState.settledPage,
                controlsVisible = controlsVisible,
                onTapAt = onTapAt,
                rotationDegrees = pendingRotations[items[page].id] ?: 0,
                onRotationSettled = { id, degrees -> pendingRotations = pendingRotations + (id to degrees) },
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ViewerTopBar(
                title = currentItem.displayName,
                onBack = onClose,
                onShare = { shareMedia(context, currentItem) },
                menuActions = menuActions,
                isFavorite = isFavorite,
                onAction = performAction,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ViewerBottomBar(
                actions = bottomActions,
                isFavorite = isFavorite,
                onAction = performAction,
            )
        }

        // 回転ジェスチャーで回したあと: 「この向きで保存」「元に戻す」
        val pendingDegrees = ((pendingRotations[currentItem.id] ?: 0) % 360 + 360) % 360
        AnimatedVisibility(
            visible = controlsVisible && pendingDegrees != 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = ViewerBottomBarHeight + 16.dp),
        ) {
            RotationSaveBar(
                onSave = { rotation.startWithDegrees(currentItem, pendingDegrees) },
                onReset = { pendingRotations = pendingRotations + (currentItem.id to 0) },
            )
        }

        renameTarget?.let { target ->
            RenameDialog(
                item = target,
                onRename = { newName -> viewerViewModel.rename(target, newName) },
                onDismiss = { renameTarget = null },
                onRenamed = { newName ->
                    renameTarget = null
                    // ビューアーが持っている一覧の、名前を更新する(上部バーのファイル名がすぐ変わる)
                    items = items.map { if (it.id == target.id) it.copy(displayName = newName) else it }
                },
            )
        }

        if (showDetails) {
            MediaDetailsDialog(
                item = currentItem,
                loadDetails = viewerViewModel::loadDetails,
                loadPlaceName = viewerViewModel::loadPlaceName,
                onDismiss = { showDetails = false },
            )
        }

        if (showDeleteDialog) {
            DeleteConfirmDialog(
                count = 1,
                toTrash = toTrash,
                onDismiss = { showDeleteDialog = false },
                onConfirm = { permanent ->
                    showDeleteDialog = false
                    deleteCurrent(permanent)
                },
            )
        }
    }
}

/**
 * ビューアーの1ページ。
 * - 写真: ピンチ・ダブルタップでズーム。設定の「深いズーム」「高画質処理」を反映する。
 *   設定の「ジェスチャーで画像を回転」がONなら、2本指のひねりで、自由な角度に回せる。
 *   指を離すと、最も近い90°に合う。拡大中は、回さない。
 * - 動画: VideoPlayerPage。
 * タップされたときは、タップの位置と、ページの幅、拡大中かどうかを、[onTapAt] に渡す。
 *
 * @param rotationDegrees 保存前の、見た目の回転の角度(度)。この値に向かって、なめらかに回る。
 * @param onRotationSettled ひねりが終わって、90°に合ったとき(写真のID、角度)。
 */
@Composable
private fun ViewerPage(
    item: MediaItem,
    isCurrent: Boolean,
    controlsVisible: Boolean,
    onTapAt: (x: Float, width: Int, zoomed: Boolean) -> Unit,
    rotationDegrees: Int,
    onRotationSettled: (id: Long, degrees: Int) -> Unit,
) {
    val settings = LocalAppSettings.current
    // ページの大きさ(画面の端のタップや、回転した写真の大きさの計算に使う)
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val widthPx = sizePx.width

    // 「深いズーム」がOFFのときは、ライブラリの標準のズームの上限を使う
    val zoomableState = rememberZoomableState(
        zoomSpec = if (settings.deepZoom) ZoomSpec(maxZoomFactor = DeepMaxZoomFactor) else ZoomSpec(),
    )
    fun isZoomed() = (zoomableState.zoomFraction ?: 0f) > ZoomedThreshold

    // 回転ジェスチャー(写真だけ)。いま見えている角度(度)と、ひねりが終わったことを知らせる回数
    val rotationEnabled = settings.rotationGesture && !item.isVideo
    var angle by remember(item.id) { mutableFloatStateOf(0f) }
    var settleCount by remember(item.id) { mutableIntStateOf(0) }

    // 角度が、決まった値(rotationDegrees)に向かって、なめらかに合う。
    // ひねりが終わるたびに、settleCount が増えるので、値が変わらなくても、90°に合わせ直す
    LaunchedEffect(rotationDegrees, settleCount) {
        val target = rotationDegrees.toFloat()
        if (angle != target) {
            animate(initialValue = angle, targetValue = target, animationSpec = tween(250)) { value, _ ->
                angle = value
            }
        }
    }

    // 開く・閉じるときの shared element transition の目印は、表示中のページにだけつける
    Box(
        modifier = Modifier
            .mediaSharedTarget(item.id, enabled = isCurrent)
            .fillMaxSize()
            .onSizeChanged { sizePx = it }
            // 回転する枠の「外側」で、2本指のひねりを見張る(指のイベントは、消費しない)
            .twistRotation(
                enabled = rotationEnabled,
                // 拡大していないときだけ、回す
                canStart = { !isZoomed() },
                onRotate = { delta -> angle += delta },
                onEnd = {
                    // 指を離したら、最も近い90°に合わせる
                    val snapped = (angle / 90f).roundToInt() * 90
                    onRotationSettled(item.id, snapped)
                    settleCount++
                },
            ),
    ) {
        if (item.isVideo) {
            VideoPlayerPage(
                item = item,
                isCurrent = isCurrent,
                controlsVisible = controlsVisible,
                onTap = { offset: Offset -> onTapAt(offset.x, widthPx, false) },
            )
        } else {
            // 回したときに、写真が画面からはみ出さないよう、大きさを合わせる倍率
            fun scaleFor(degrees: Float) = fitScaleForRotation(
                viewWidth = sizePx.width.toFloat(),
                viewHeight = sizePx.height.toFloat(),
                imageWidth = item.width.toFloat(),
                imageHeight = item.height.toFloat(),
                degrees = degrees,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (rotationEnabled) {
                            // 回転の中心は、枠の中央(graphicsLayerの初期値)
                            rotationZ = angle
                            val scale = scaleFor(angle)
                            scaleX = scale
                            scaleY = scale
                        }
                    },
            ) {
                ZoomableAsyncImage(
                    // 「高画質処理」がOFFのときは、画面に収まる大きさの画像だけを読み込む(拡大すると、粗くなる)
                    model = rememberMediaImageRequest(
                        item,
                        sizePx = if (settings.highQualityDisplay) null else StandardImageSizePx,
                    ),
                    contentDescription = item.displayName,
                    state = rememberZoomableImageState(zoomableState),
                    onClick = { offset: Offset ->
                        // 回している間・回したあとは、タップの位置が、回転した枠の中の座標になる。
                        // 画面の左右の端を判断するため、画面の座標に直す
                        val screenX = if (rotationEnabled && angle != 0f) {
                            toScreenX(
                                localX = offset.x,
                                localY = offset.y,
                                viewWidth = sizePx.width.toFloat(),
                                viewHeight = sizePx.height.toFloat(),
                                degrees = angle,
                                scale = scaleFor(angle),
                            )
                        } else {
                            offset.x
                        }
                        onTapAt(screenX, widthPx, isZoomed())
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 回転ジェスチャーで回したあとに出す、「この向きで保存」と「元に戻す」のボタン。 */
@Composable
private fun RotationSaveBar(
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 青い、塗りつぶしのボタン(白い文字)
        Surface(
            onClick = onSave,
            shape = CircleShape,
            color = Color(0xFF00A6FF),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "この向きで保存",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // 半透明の黒いボタン(写真の上でも見える)
        Surface(
            onClick = onReset,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Undo, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "元に戻す",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

/**
 * バーを隠している間([hide] が true)は、ステータスバー・ナビゲーションバーも隠す(画面の端からのスワイプで、一時的に出せる)。
 * [hide] が false に戻ったとき、またはビューアーが閉じたときは、元に戻す。
 */
@Composable
private fun HideSystemBarsEffect(hide: Boolean) {
    val view = LocalView.current
    DisposableEffect(hide) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (hide && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 操作ごとの、アイコン。お気に入りは、状態([isFavorite])で、★/☆が変わる。 */
private fun ViewerAction.icon(isFavorite: Boolean): ImageVector = when (this) {
    ViewerAction.Favorite -> if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder
    ViewerAction.Edit -> Icons.Default.Edit
    ViewerAction.Delete -> Icons.Default.Delete
    ViewerAction.Rotate -> Icons.Default.RotateRight
    ViewerAction.Resize -> Icons.Default.PhotoSizeSelectLarge
    ViewerAction.Copy -> Icons.Default.ContentCopy
    ViewerAction.Move -> Icons.Default.FolderOpen
    ViewerAction.Details -> Icons.Default.Info
    ViewerAction.Rename -> Icons.Default.DriveFileRenameOutline
    ViewerAction.OpenWith -> Icons.AutoMirrored.Filled.OpenInNew
}

/** 操作ごとの、名前。お気に入りは、状態([isFavorite])で、「追加」「外す」が変わる。 */
private fun ViewerAction.displayLabel(isFavorite: Boolean): String = when (this) {
    ViewerAction.Favorite -> if (isFavorite) "お気に入りから外す" else "お気に入りに追加"
    else -> label
}

/**
 * 上部バー: 写真に重なっても見えるよう、黒のグラデーションの中に表示する。
 * 戻る・ファイル名・共有・︙。︙メニューには、下部に出していない操作([menuActions])が入る。
 */
@Composable
private fun ViewerTopBar(
    title: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    menuActions: List<ViewerAction>,
    isFavorite: Boolean,
    onAction: (ViewerAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)),
            )
            .statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "共有")
            }
            // ︙メニュー: 下部に出していない操作。1つもなければ、︙自体を出さない
            if (menuActions.isNotEmpty()) {
                Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "その他")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        menuActions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.displayLabel(isFavorite)) },
                                leadingIcon = { Icon(action.icon(isFavorite), contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onAction(action)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 下部のアクション: 設定で選んだ操作([actions])を、並べる。1つも選んでいなければ、何も出さない。 */
@Composable
private fun ViewerBottomBar(
    actions: List<ViewerAction>,
    isFavorite: Boolean,
    onAction: (ViewerAction) -> Unit,
) {
    if (actions.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))),
            )
            .navigationBarsPadding()
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            actions.forEach { action ->
                IconButton(onClick = { onAction(action) }) {
                    Icon(
                        imageVector = action.icon(isFavorite),
                        contentDescription = action.displayLabel(isFavorite),
                    )
                }
            }
        }
    }
}