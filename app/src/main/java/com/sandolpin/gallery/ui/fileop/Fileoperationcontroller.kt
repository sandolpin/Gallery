package com.sandolpin.gallery.ui.fileop

import android.content.ContentUris
import androidx.compose.runtime.staticCompositionLocalOf
import com.sandolpin.gallery.data.local.mediastore.CopyDestination
import com.sandolpin.gallery.data.local.mediastore.FileOperations
import com.sandolpin.gallery.data.local.mediastore.MoveOutcome
import com.sandolpin.gallery.data.model.MediaItem
import com.sandolpin.gallery.data.repository.FavoriteRepository
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

/** ファイル操作の種類。 */
enum class FileOperationType(val label: String, val progressTitle: String) {
    Copy(label = "コピー", progressTitle = "コピー中…"),
    Move(label = "移動", progressTitle = "移動中…"),
}

/** ファイル操作の、いまの段階。 */
sealed interface FileOperationState {
    /** 何もしていない。 */
    data object Idle : FileOperationState

    /** 行き先のフォルダを選んでいる。 */
    data class Picking(val type: FileOperationType, val items: List<MediaItem>) : FileOperationState

    /** 実行中。[remainingSeconds] は、1件終わるまでは null。 */
    data class Running(
        val type: FileOperationType,
        val done: Int,
        val total: Int,
        val remainingSeconds: Int?,
    ) : FileOperationState
}

/**
 * ファイル操作(コピー・移動)の、進み具合を持つ。
 * 画面(一覧・ビューアー)から始めて、フォルダ選択と進行状況は、GalleryRoot の [FileOperationHost] が表示する。
 * アプリ全体で1つだけ。画面を切り替えたり、回転したりしても、処理は続く。
 * (アプリを完全に閉じたときは、処理は途中で止まる)
 */
@Singleton
class FileOperationController @Inject constructor(
    private val fileOperations: FileOperations,
    private val mediaRepository: MediaRepository,
    private val favoriteRepository: FavoriteRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _state = MutableStateFlow<FileOperationState>(FileOperationState.Idle)
    val state: StateFlow<FileOperationState> = _state.asStateFlow()

    /** 「3件をコピーしました」などの、一度だけ表示するメッセージ。 */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * 移動が終わった写真(移動元の写真)のID。
     * ビューアーが、見ていた写真が移動して、なくなったことを知るために使う。
     */
    private val _movedIds = MutableSharedFlow<Set<Long>>(extraBufferCapacity = 4)
    val movedIds: SharedFlow<Set<Long>> = _movedIds.asSharedFlow()

    /** フォルダ選択を始める。ほかの操作の最中なら、何もしない。 */
    fun start(type: FileOperationType, items: List<MediaItem>) {
        if (items.isEmpty() || _state.value != FileOperationState.Idle) return
        _state.value = FileOperationState.Picking(type, items)
    }

    /** フォルダ選択をやめる。 */
    fun cancelPicking() {
        if (_state.value is FileOperationState.Picking) _state.value = FileOperationState.Idle
    }

    /** 実行中の操作を中止する。いま処理中の1件が終わってから止まる。終わった分は、そのまま残る。 */
    fun cancelRunning() {
        job?.cancel()
    }

    /** 選んだフォルダに、実行する。 */
    fun execute(destination: CopyDestination) {
        val picking = _state.value as? FileOperationState.Picking ?: return
        val type = picking.type
        val items = picking.items
        _state.value = FileOperationState.Running(type, done = 0, total = items.size, remainingSeconds = null)

        job = scope.launch {
            // 一覧に知らせる(スキャンする)ファイル
            val scanPaths = ArrayList<String>()
            // 成功した件数と、移動に成功した元の写真のID
            var succeeded = 0
            val movedIds = HashSet<Long>()
            // コピー+削除で移動したもの: 元のID → コピー先のパス(お気に入りの付け替えに使う)
            val relinks = ArrayList<Pair<Long, String>>()
            // 移動で、コピーはできたが、元を削除できなかった件数
            var leftover = 0
            var cancelled = false
            var directoryError = false

            try {
                val directory = fileOperations.resolveDirectory(destination)
                if (directory == null) {
                    directoryError = true
                } else {
                    val startedAt = System.nanoTime()
                    for ((index, item) in items.withIndex()) {
                        ensureActive()

                        when (type) {
                            FileOperationType.Copy -> {
                                val file = fileOperations.copyOne(item, directory)
                                if (file != null) {
                                    succeeded++
                                    scanPaths += file.path
                                }
                            }
                            FileOperationType.Move -> when (val outcome = fileOperations.moveOne(item, directory)) {
                                MoveOutcome.InPlace -> {
                                    succeeded++
                                    movedIds += item.id
                                }
                                is MoveOutcome.Copied -> {
                                    succeeded++
                                    movedIds += item.id
                                    scanPaths += outcome.file.path
                                    relinks += outcome.oldId to outcome.file.path
                                }
                                is MoveOutcome.CopiedOnly -> {
                                    leftover++
                                    scanPaths += outcome.file.path
                                }
                                MoveOutcome.Failed -> Unit
                            }
                        }

                        val done = index + 1
                        val elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
                        val remaining = ceil(elapsedSeconds / done * (items.size - done)).toInt()
                        _state.value = FileOperationState.Running(type, done, items.size, remaining)
                    }
                }
            } catch (e: CancellationException) {
                cancelled = true
            }

            // 中止されたときも、ここまでの結果は、一覧に反映する
            withContext(NonCancellable) {
                if (scanPaths.isNotEmpty()) {
                    val uris = fileOperations.scan(scanPaths)
                    // 移動(コピー+削除)で、写真のIDが変わったものは、お気に入りを新しいIDに付け替える
                    val mapping = HashMap<Long, Long>()
                    for ((oldId, path) in relinks) {
                        val uri = uris[path] ?: continue
                        runCatching { ContentUris.parseId(uri) }.getOrNull()?.let { mapping[oldId] = it }
                    }
                    favoriteRepository.replaceIds(mapping)
                }
                if (succeeded > 0 || scanPaths.isNotEmpty()) mediaRepository.notifyMediaChanged()
                if (movedIds.isNotEmpty()) _movedIds.emit(movedIds)

                _state.value = FileOperationState.Idle
                _messages.emit(
                    resultMessage(type, items.size, succeeded, cancelled, directoryError, leftover),
                )
            }
        }
    }

    private fun resultMessage(
        type: FileOperationType,
        total: Int,
        succeeded: Int,
        cancelled: Boolean,
        directoryError: Boolean,
        leftover: Int,
    ): String {
        val base = when {
            directoryError -> "${type.label}先のフォルダを開けませんでした"
            cancelled -> "キャンセルしました(${succeeded}件を${type.label}済み)"
            succeeded == total -> "${succeeded}件を${type.label}しました"
            succeeded == 0 && leftover == 0 -> "${type.label}できませんでした"
            else -> "${total}件中${succeeded}件のみ${type.label}しました"
        }
        // 元を削除できず、コピーだけになったもの
        return if (leftover > 0) "$base(${leftover}件は、元のファイルを削除できず、コピーのみ)" else base
    }
}

/** 画面のどこからでも、ファイル操作を始められるようにするための受け渡し。 */
val LocalFileOperationController = staticCompositionLocalOf<FileOperationController> {
    error("FileOperationController が提供されていません")
}