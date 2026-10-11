package com.sandolpin.gallery.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.sandolpin.gallery.data.repository.MediaRepository
import com.sandolpin.gallery.ui.settings.LocalAppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 下に引っ張って更新したときに、写真の一覧を、読み込み直させる。 */
@HiltViewModel
class RefreshViewModel @Inject constructor(
    private val repository: MediaRepository,
) : ViewModel() {
    /** 各画面に、一覧を読み直させる(MediaStoreから、新しく読み込む)。 */
    fun refresh() = repository.notifyMediaChanged()
}

/** 更新の、くるくるを出しておく時間(ミリ秒)。読み込みの完了は分からないため、決まった時間だけ出す。 */
private const val IndicatorMillis = 800L

/**
 * 下に引っ張って、写真の一覧を読み込み直す枠(material3 の PullToRefreshBox)。
 * 設定の「下に引っ張って更新」がOFFのときは、ただの Box になる。
 * スクロールする一覧(LazyColumn・LazyVerticalGrid)を、この中に入れる。
 *
 * @param indicatorTopPadding 更新の表示を、上から下げる量。上に重ねたヘッダーがある画面で、ヘッダーに隠れないように使う。
 * @param onRefresh 引っ張って更新したときに、一覧の読み直しに加えて呼ぶ(ホームで、「いろいろな写真」のカテゴリを入れ替えるのに使う)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(
    modifier: Modifier = Modifier,
    indicatorTopPadding: Dp = 0.dp,
    onRefresh: () -> Unit = {},
    viewModel: RefreshViewModel = hiltViewModel(),
    content: @Composable BoxScope.() -> Unit,
) {
    if (!LocalAppSettings.current.pullToRefresh) {
        Box(modifier = modifier, content = content)
        return
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                onRefresh()
                viewModel.refresh()
                delay(IndicatorMillis)
                isRefreshing = false
            }
        },
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = indicatorTopPadding),
            )
        },
        content = content,
    )
}
