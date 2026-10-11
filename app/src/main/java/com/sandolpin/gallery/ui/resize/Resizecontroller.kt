package com.sandolpin.gallery.ui.resize

import androidx.compose.runtime.staticCompositionLocalOf
import com.sandolpin.gallery.data.local.mediastore.FileOperations
import com.sandolpin.gallery.data.local.mediastore.ImageResizer
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.model.ResizeTarget
import com.sandolpin.gallery.data.model.sizeFor
import com.sandolpin.gallery.data.repository.MediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/** リサイズの、いまの段階。 */
sealed interface ResizeState {
    data object Idle : ResizeState

    /**
     * リサイズダイアログで、設定を決めている。
     * @param items リサイズできる写真(対応していない形式は、除いてある)
     * @param unsupported 対応していないため、除いた枚数
     */
    data class Editing(val items: List<MediaItem>, val unsupported: Int) : ResizeState

    /** 保存ダイアログで、上書きかコピーかを選んでいる。[target] は、決めた設定。 */
    data class ChoosingSave(
        val items: List<MediaItem>,
        val unsupported: Int,
        val target: ResizeTarget,
    ) : ResizeState

    /** 実行中。[remainingSeconds] は、1枚終わるまでは null。 */
    data class Running(val done: Int, val total: Int, val remainingSeconds: Int?) : ResizeState
}

/**
 * リサイズの流れ(設定 → 保存の選択 → 実行)を管理する。
 * 画面(一覧・ビューアー)から [start] で始めて、ダイアログは GalleryRoot の [ResizeHost] が表示する。
 * 設定を決めている間は、ファイルには何もしない(途中でキャンセルすると、何も変わらない)。
 */
@Singleton
class ResizeController @Inject constructor(
    private val resizer: ImageResizer,
    private val fileOperations: FileOperations,
    private val mediaRepository: MediaRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _state = MutableStateFlow<ResizeState>(ResizeState.Idle)
    val state: StateFlow<ResizeState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * 上書きでリサイズした写真のID。
     * ビューアーが、持っている写真の「版」を更新して、リサイズ後の画像を読み直すために使う。
     */
    private val _resizedIds = MutableSharedFlow<Set<Long>>(extraBufferCapacity = 4)
    val resizedIds: SharedFlow<Set<Long>> = _resizedIds.asSharedFlow()

    /** リサイズを始める。対応していない形式は除く。ほかの操作の最中なら、何もしない。 */
    fun start(items: List<MediaItem>) {
        if (items.isEmpty() || _state.value != ResizeState.Idle) return

        val supported = items.filter(resizer::isSupported)
        if (supported.isEmpty()) {
            _messages.tryEmit("リサイズできる写真がありません(JPEG・PNG・WebPのみ対応)")
            return
        }
        _state.value = ResizeState.Editing(supported, items.size - supported.size)
    }

    /** リサイズの作業をやめる(まだ何も実行していないので、何も変わらない)。 */
    fun cancel() {
        if (_state.value !is ResizeState.Running) _state.value = ResizeState.Idle
    }

    /** 実行中のリサイズを中止する。いま処理中の1枚が終わってから止まる。終わった分は、そのまま残る。 */
    fun cancelRunning() {
        job?.cancel()
    }

    /** リサイズダイアログで「保存」。保存ダイアログ(上書き/コピー)へ進む。 */
    fun save(target: ResizeTarget) {
        val editing = _state.value as? ResizeState.Editing ?: return
        _state.value = ResizeState.ChoosingSave(editing.items, editing.unsupported, target)
    }

    /** 保存ダイアログで、「ファイルを上書き」(true)か「コピーを保存」(false)を選んだ。実行する。 */
    fun chooseSave(overwrite: Boolean) {
        val choosing = _state.value as? ResizeState.ChoosingSave ?: return
        run(choosing.items, choosing.unsupported, choosing.target, overwrite)
    }

    private fun run(items: List<MediaItem>, unsupported: Int, target: ResizeTarget, overwrite: Boolean) {
        _state.value = ResizeState.Running(done = 0, total = items.size, remainingSeconds = null)

        job = scope.launch {
            val scanPaths = ArrayList<String>()
            // 上書きでリサイズできた写真のID(コピーは、新しいファイルになるため、対象外)
            val overwrittenIds = HashSet<Long>()
            // 設定より小さい(または同じ)ため、リサイズしなかった枚数
            var skipped = 0
            var cancelled = false

            try {
                val startedAt = System.nanoTime()
                for ((index, item) in items.withIndex()) {
                    ensureActive()

                    val size = target.sizeFor(item.width, item.height)
                    if (size == null) {
                        skipped++
                    } else {
                        val file = resizer.resize(item, targetWidth = size.first, overwrite = overwrite)
                        if (file != null) {
                            scanPaths += file.path
                            if (overwrite) overwrittenIds += item.id
                        }
                    }

                    val done = index + 1
                    val elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
                    val remaining = ceil(elapsedSeconds / done * (items.size - done)).toInt()
                    _state.value = ResizeState.Running(done, items.size, remaining)
                }
            } catch (e: CancellationException) {
                cancelled = true
            }

            // 中止されたときも、ここまでの結果は、一覧に反映する
            withContext(NonCancellable) {
                if (scanPaths.isNotEmpty()) {
                    fileOperations.scan(scanPaths)
                    if (overwrittenIds.isNotEmpty()) {
                        // 上書きした写真は、画像の「版」を変えて、サムネイルを読み直させる(一覧も、読み直される)
                        mediaRepository.markImagesChanged(overwrittenIds)
                        _resizedIds.emit(overwrittenIds)
                    } else {
                        mediaRepository.notifyMediaChanged()
                    }
                }
                _state.value = ResizeState.Idle
                _messages.emit(
                    resultMessage(items.size, scanPaths.size, skipped, cancelled, unsupported),
                )
            }
        }
    }

    private fun resultMessage(
        total: Int,
        succeeded: Int,
        skipped: Int,
        cancelled: Boolean,
        unsupported: Int,
    ): String {
        val base = when {
            cancelled -> "キャンセルしました(${succeeded}件をリサイズ済み)"
            succeeded == 0 && skipped == total -> "設定より小さいため、リサイズされませんでした"
            succeeded == total -> "${succeeded}件をリサイズしました"
            succeeded == 0 -> "リサイズできませんでした"
            else -> "${total - skipped}件中${succeeded}件をリサイズしました"
        }
        val notes = buildList {
            if (skipped > 0 && succeeded > 0) add("${skipped}件は、設定より小さいため、そのまま")
            if (unsupported > 0) add("${unsupported}件は非対応のため、リサイズしていません")
        }
        return if (notes.isEmpty()) base else "$base(${notes.joinToString("、")})"
    }
}

/** 画面のどこからでも、リサイズを始められるようにするための受け渡し。 */
val LocalResizeController = staticCompositionLocalOf<ResizeController> {
    error("ResizeController が提供されていません")
}