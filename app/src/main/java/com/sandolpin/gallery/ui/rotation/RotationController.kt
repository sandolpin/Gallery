package com.sandolpin.gallery.ui.rotation

import androidx.compose.runtime.staticCompositionLocalOf
import com.sandolpin.gallery.data.local.mediastore.FileOperations
import com.sandolpin.gallery.data.local.mediastore.ImageRotator
import com.sandolpin.gallery.data.model.MediaItem
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

/** 回転する写真1枚と、時計回りの角度(90・180・270)。 */
data class RotationJob(val item: MediaItem, val degrees: Int)

/**
 * 回転の作業の途中経過。
 * @param items 回転できる写真(対応していない形式は、除いてある)
 * @param unsupported 対応していないため、除いた枚数
 * @param applyToAll true: 1枚目をプレビューにして、全部に同じ角度を適用。false: 1枚ずつ、順番に角度を決める
 * @param index 1枚ずつのとき、いま何枚目か(0始まり)
 * @param jobs 1枚ずつのとき、ここまでに決めた回転(まとめて、最後に実行する)
 * @param overwrite 上書き(true)/コピーを保存(false)。最初に1回だけ聞いて、残りにも同じ選択を使う
 */
data class RotationSession(
    val items: List<MediaItem>,
    val unsupported: Int,
    val applyToAll: Boolean,
    val index: Int = 0,
    val jobs: List<RotationJob> = emptyList(),
    val overwrite: Boolean? = null,
) {
    val current: MediaItem get() = items[index]
}

/** 回転の、いまの段階。 */
sealed interface RotationState {
    data object Idle : RotationState

    /** 複数枚を選んだとき: 「1枚ずつ」か「すべてに同じ角度」かを選んでいる。 */
    data class ChoosingScope(val items: List<MediaItem>, val unsupported: Int) : RotationState

    /** 回転ダイアログで、角度を決めている。 */
    data class Editing(val session: RotationSession) : RotationState

    /** 保存ダイアログで、上書きかコピーかを選んでいる。[degrees] は、決めた角度。 */
    data class ChoosingSave(val session: RotationSession, val degrees: Int) : RotationState

    /** 実行中。[remainingSeconds] は、1枚終わるまでは null。 */
    data class Running(val done: Int, val total: Int, val remainingSeconds: Int?) : RotationState
}

/**
 * 回転の流れ(選択肢 → 回転 → 保存の選択 → 実行)を管理する。
 * 画面(一覧・ビューアー)から [start] で始めて、ダイアログは GalleryRoot の [RotationHost] が表示する。
 * 角度を決めている間は、ファイルには何もしない。実行するのは、最後に、まとめて。
 * (途中でキャンセルすると、何も変わらない)
 */
@Singleton
class RotationController @Inject constructor(
    private val rotator: ImageRotator,
    private val fileOperations: FileOperations,
    private val mediaRepository: MediaRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _state = MutableStateFlow<RotationState>(RotationState.Idle)
    val state: StateFlow<RotationState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * 上書きで回転した写真のID。
     * ビューアーが、持っている写真の「版」を更新して、回転後の画像を読み直すために使う。
     */
    private val _rotatedIds = MutableSharedFlow<Set<Long>>(extraBufferCapacity = 4)
    val rotatedIds: SharedFlow<Set<Long>> = _rotatedIds.asSharedFlow()

    /** 回転を始める。対応していない形式は除く。ほかの操作の最中なら、何もしない。 */
    fun start(items: List<MediaItem>) {
        if (items.isEmpty() || _state.value != RotationState.Idle) return

        val supported = items.filter(rotator::isSupported)
        val unsupported = items.size - supported.size
        when {
            supported.isEmpty() ->
                _messages.tryEmit("回転できる写真がありません(JPEG・PNG・WebPのみ対応)")
            supported.size == 1 ->
                _state.value = RotationState.Editing(
                    RotationSession(supported, unsupported, applyToAll = false),
                )
            else -> _state.value = RotationState.ChoosingScope(supported, unsupported)
        }
    }

    /**
     * ビューアーの回転ジェスチャーで決めた角度([degrees]: 90・180・270)で、
     * 回転ダイアログを飛ばして、すぐ、保存ダイアログ(上書き/コピーを保存)を開く。
     */
    fun startWithDegrees(item: MediaItem, degrees: Int) {
        if (_state.value != RotationState.Idle || degrees % 360 == 0) return
        if (!rotator.isSupported(item)) {
            _messages.tryEmit("この形式は、回転して保存できません(JPEG・PNG・WebPのみ対応)")
            return
        }
        _state.value = RotationState.ChoosingSave(
            session = RotationSession(items = listOf(item), unsupported = 0, applyToAll = false),
            degrees = ((degrees % 360) + 360) % 360,
        )
    }

    /** 選択肢ダイアログで、「1枚ずつ」(false)か「すべてに同じ角度」(true)を選んだ。 */
    fun chooseScope(applyToAll: Boolean) {
        val choosing = _state.value as? RotationState.ChoosingScope ?: return
        _state.value = RotationState.Editing(
            RotationSession(choosing.items, choosing.unsupported, applyToAll),
        )
    }

    /** 回転の作業をやめる(まだ何も実行していないので、何も変わらない)。 */
    fun cancel() {
        if (_state.value !is RotationState.Running) _state.value = RotationState.Idle
    }

    /** 実行中の回転を中止する。いま処理中の1枚が終わってから止まる。終わった分は、そのまま残る。 */
    fun cancelRunning() {
        job?.cancel()
    }

    /** 1枚ずつのとき、いまの写真を回転せずに、次へ進む。 */
    fun skip() {
        val editing = _state.value as? RotationState.Editing ?: return
        advance(editing.session)
    }

    /** 回転ダイアログで「保存」。角度が0°のときは、何もしない。 */
    fun save(degrees: Int) {
        val editing = _state.value as? RotationState.Editing ?: return
        if (degrees % 360 == 0) return
        if (editing.session.overwrite == null) {
            // 上書きかコピーかを、最初の1回だけ聞く
            _state.value = RotationState.ChoosingSave(editing.session, degrees)
        } else {
            commit(editing.session, degrees)
        }
    }

    /** 保存ダイアログで、「ファイルを上書き」(true)か「コピーを保存」(false)を選んだ。 */
    fun chooseSave(overwrite: Boolean) {
        val choosing = _state.value as? RotationState.ChoosingSave ?: return
        commit(choosing.session.copy(overwrite = overwrite), choosing.degrees)
    }

    /** 決めた角度を記録する。全部に同じ角度なら、すぐ実行。1枚ずつなら、次の写真へ(最後なら実行)。 */
    private fun commit(session: RotationSession, degrees: Int) {
        if (session.applyToAll) {
            run(session.items.map { RotationJob(it, degrees) }, session)
        } else {
            advance(session.copy(jobs = session.jobs + RotationJob(session.current, degrees)))
        }
    }

    private fun advance(session: RotationSession) {
        val next = session.index + 1
        when {
            next < session.items.size -> _state.value = RotationState.Editing(session.copy(index = next))
            session.jobs.isEmpty() -> _state.value = RotationState.Idle
            else -> run(session.jobs, session)
        }
    }

    private fun run(jobs: List<RotationJob>, session: RotationSession) {
        val overwrite = session.overwrite ?: false
        _state.value = RotationState.Running(done = 0, total = jobs.size, remainingSeconds = null)

        job = scope.launch {
            val scanPaths = ArrayList<String>()
            // 上書きで回転できた写真のID(コピーは、新しいファイルになるため、対象外)
            val overwrittenIds = HashSet<Long>()
            var cancelled = false

            try {
                val startedAt = System.nanoTime()
                for ((index, rotation) in jobs.withIndex()) {
                    ensureActive()
                    val file = rotator.rotate(rotation.item, rotation.degrees, overwrite)
                    if (file != null) {
                        scanPaths += file.path
                        if (overwrite) overwrittenIds += rotation.item.id
                    }

                    val done = index + 1
                    val elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
                    val remaining = ceil(elapsedSeconds / done * (jobs.size - done)).toInt()
                    _state.value = RotationState.Running(done, jobs.size, remaining)
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
                        _rotatedIds.emit(overwrittenIds)
                    } else {
                        mediaRepository.notifyMediaChanged()
                    }
                }
                _state.value = RotationState.Idle
                _messages.emit(resultMessage(jobs.size, scanPaths.size, cancelled, session.unsupported))
            }
        }
    }

    private fun resultMessage(total: Int, succeeded: Int, cancelled: Boolean, unsupported: Int): String {
        val base = when {
            cancelled -> "キャンセルしました(${succeeded}件を回転済み)"
            succeeded == total -> "${succeeded}件を回転しました"
            succeeded == 0 -> "回転できませんでした"
            else -> "${total}件中${succeeded}件のみ回転しました"
        }
        return if (unsupported > 0) "$base(${unsupported}件は非対応のため、回転していません)" else base
    }
}

/** 画面のどこからでも、回転を始められるようにするための受け渡し。 */
val LocalRotationController = staticCompositionLocalOf<RotationController> {
    error("RotationController が提供されていません")
}