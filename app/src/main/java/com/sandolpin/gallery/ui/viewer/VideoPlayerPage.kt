package com.sandolpin.gallery.ui.viewer

import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.ui.components.formatDuration
import kotlinx.coroutines.delay
import androidx.media3.common.MediaItem as ExoMediaItem

/** true にすると、動画を開いたときに、自動で再生を始める。 */
private const val AutoPlay = false

/**
 * ビューアーの、動画1ページ分。
 * プレイヤーは、いま表示中([isCurrent])の動画でだけ作る。
 * 前後のページでは、サムネイルだけを表示する(1万枚・大量の動画でも、メモリを使いすぎないため)。
 */
@Composable
fun VideoPlayerPage(
    item: MediaItem,
    isCurrent: Boolean,
    controlsVisible: Boolean,
    /** 画面をタップしたとき。引数は、タップした位置(画面の端のタップで、前/次の写真へ切り替えるために使う)。 */
    onTap: (Offset) -> Unit,
) {
    if (isCurrent) {
        CurrentVideoPlayer(item, controlsVisible, onTap)
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { offset -> onTap(offset) }) },
        ) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CurrentVideoPlayer(
    item: MediaItem,
    controlsVisible: Boolean,
    onTap: (Offset) -> Unit,
) {
    val context = LocalContext.current

    val player = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = AutoPlay
        }
    }

    // 画面から消えたら、プレイヤーを解放する
    DisposableEffect(player) { onDispose { player.release() } }

    // アプリがバックグラウンドに移ったら、一時停止する
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(item.durationMillis ?: 0L) }
    var aspect by remember { mutableFloatStateOf(item.aspectRatio.coerceAtLeast(0.1f)) }
    var firstFrameShown by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
                if (state == Player.STATE_ENDED) {
                    positionMs = durationMs
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onRenderedFirstFrame() {
                firstFrameShown = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 再生中は、経過時間を、0.25秒ごとに更新する
    LaunchedEffect(player, isPlaying) {
        while (isPlaying) {
            positionMs = player.currentPosition
            delay(250)
        }
    }

    val togglePlay: () -> Unit = {
        if (player.isPlaying) {
            player.pause()
        } else {
            // 最後まで再生したあとは、最初から再生し直す
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { offset -> onTap(offset) }) },
        contentAlignment = Alignment.Center,
    ) {
        // 映像。縦横比を保って、画面に収まる最大の大きさで表示する
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
            modifier = Modifier.aspectRatio(aspect),
        )

        // 最初の1コマが出るまでは、サムネイルを表示する(黒い画面を見せないため)
        if (!firstFrameShown) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 一時停止中は、中央に大きな再生ボタン
        AnimatedVisibility(
            visible = controlsVisible && !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                onClick = togglePlay,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "再生",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(48.dp),
                )
            }
        }

        // 下部の操作パネル(再生/一時停止・経過時間・シークバー・全体の時間)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                // 下部のアクションバーの、すぐ上に置く
                .padding(start = 16.dp, end = 16.dp, bottom = ViewerBottomBarHeight + 8.dp),
        ) {
            ControlPanel(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                onTogglePlay = togglePlay,
                onSeek = { target ->
                    player.seekTo(target)
                    positionMs = target
                },
            )
        }
    }
}

@Composable
private fun ControlPanel(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    // シークバーをドラッグしている間は、指の位置を優先して表示する
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val progress = dragValue
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownPositionMs = dragValue?.let { (it * durationMs).toLong() } ?: positionMs

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "一時停止" else "再生",
                    tint = Color.White,
                )
            }
            Text(
                text = formatDuration(shownPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            Slider(
                value = progress,
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { onSeek((it * durationMs).toLong()) }
                    dragValue = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}