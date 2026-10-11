plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.sandolpin.gallery"
    // AGP 8.5.2 が正式対応する最大値に合わせる(35以上にすると、今回と同じ
    // エラーが再発しやすいため、当面は34で固定する)
    compileSdk = 34

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    android {
        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a")
                isUniversalApk = false
            }
        }
    }

    defaultConfig {
        applicationId = "com.sandolpin.gallery"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Hilt(依存性注入)
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // WorkManager(裏で動く処理。写真の分析に使う)と、Hiltとのつなぎこみ
    // 2.9.1は compileSdk 34 でビルドされており、現在の環境と互換
    // (2.10.0以降は compileSdk 35を要求するため、上げないこと)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // 写真の中身の分類(ラベル検出)。モデルはアプリに内蔵(端末の中だけで動く)
    // 17.0.9は compileSdk 34 でビルドされており、現在の環境と互換
    implementation("com.google.mlkit:image-labeling:17.0.9")
    // 写真の中の文字の認識(日本語モデルをアプリに内蔵。日本語も英数字も読める。アプリが十数MB大きくなる)
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")

    // Room(お気に入り・ピン留め・分類結果の保存)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // DataStore(設定の保存)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // 画像表示
    // Coil「3系」はComposeマルチプラットフォーム版に依存しており、
    // AGP 9を要求する新しいCompose関連ライブラリを芋づる式に引き込むため、
    // Android専業の「2系」を使う。
    implementation("io.coil-kt:coil-compose:2.7.0")
    // 動画のサムネイル表示用
    implementation("io.coil-kt:coil-video:2.7.0")

    // ビューアーの、ピンチズーム・ダブルタップズーム・拡大中の移動
    // 0.14.0は、compileSdk 34 / Coil 2.7.0 でビルドされており、現在の環境と互換
    // (これより新しい版は compileSdk 35以上を要求するため、上げないこと)
    implementation("me.saket.telephoto:zoomable-image-coil:0.14.0")

    // 動画の再生(ExoPlayer)
    // 1.4.1は compileSdk 34 でビルドされており、現在の環境と互換
    // (1.5.0以降は compileSdk 35を要求するため、上げないこと)
    implementation("androidx.media3:media3-exoplayer:1.4.1")

    // 写真のEXIF(位置情報など)の読み取り(詳細情報ダイアログ用)
    // 1.3.7は compileSdk 34 でビルドされており、現在の環境と互換
    // (1.4.0以降は compileSdk 35を要求するため、上げないこと)
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

// 何らかの依存関係の変化で、上記より新しいバージョンへ自動的に
// 引き上げられないよう、主要なライブラリのバージョンを強制的に固定する。
// AGP 9への移行時に、まとめて見直す。
configurations.all {
    resolutionStrategy {
        force(
            "androidx.core:core-ktx:1.13.1",
            "androidx.core:core:1.13.1",
            "androidx.activity:activity:1.9.2",
            "androidx.activity:activity-ktx:1.9.2",
            "androidx.activity:activity-compose:1.9.2",
            "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6",
            "androidx.hilt:hilt-navigation-compose:1.2.0",
            "androidx.navigation:navigation-compose:2.8.0",
            "androidx.work:work-runtime:2.9.1",
            "androidx.work:work-runtime-ktx:2.9.1",
        )
    }
}
