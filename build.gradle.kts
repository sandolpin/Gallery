// プロジェクト直下(ルート)の build.gradle.kts
// 既にファイルがある場合は、plugins ブロックの中身だけを、
// ご自身のファイルにマージしてください(丸ごと置き換えないでください)。
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
}