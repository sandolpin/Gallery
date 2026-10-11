package com.sandolpin.gallery

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.sandolpin.gallery.data.repository.SettingsRepository
import com.sandolpin.gallery.data.settings.ThemeMode
import com.sandolpin.gallery.ui.fileop.FileOperationController
import com.sandolpin.gallery.ui.navigation.GalleryRoot
import com.sandolpin.gallery.ui.permission.AllFilesAccessGate
import com.sandolpin.gallery.ui.resize.ResizeController
import com.sandolpin.gallery.ui.rotation.RotationController
import com.sandolpin.gallery.ui.settings.LocalAppSettings
import com.sandolpin.gallery.ui.theme.GalleryTheme
import com.sandolpin.gallery.ui.viewer.ViewerController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var viewerController: ViewerController

    @Inject
    lateinit var fileOperationController: FileOperationController

    @Inject
    lateinit var rotationController: RotationController

    @Inject
    lateinit var resizeController: ResizeController

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 最初の1回だけ、設定の読み込みを待つ。
        // 待たないと、ダークモードにしていても、起動の一瞬だけ、明るい画面が出てしまうため。
        val initialSettings = runBlocking { settingsRepository.settings.first() }

        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = initialSettings)
            val darkTheme = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
            }

            // アプリのダークモードが、端末の設定と違っても、ステータスバー・ナビゲーションバーの
            // アイコンの色(明るい/暗い)が、見やすくなるようにする
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = Color.TRANSPARENT,
                        darkScrim = Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = LightScrim,
                        darkScrim = DarkScrim,
                    ) { darkTheme },
                )
                onDispose { }
            }

            CompositionLocalProvider(LocalAppSettings provides settings) {
                GalleryTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AllFilesAccessGate {
                            GalleryRoot(
                                viewerController,
                                fileOperationController,
                                rotationController,
                                resizeController,
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        /** 3ボタンナビゲーションのときの、ナビゲーションバーの背景(明るい/暗い)。 */
        val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}