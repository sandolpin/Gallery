package com.sandolpin.gallery.ui.settings

import androidx.compose.runtime.staticCompositionLocalOf
import com.sandolpin.gallery.data.settings.AppSettings

/**
 * いまの設定。MainActivity で提供するので、どの画面からでも、`LocalAppSettings.current` で読める。
 * 設定を変えると、すぐ、この値が入れ替わる。
 */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }