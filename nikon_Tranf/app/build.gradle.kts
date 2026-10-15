import icons.GenerateAppIconTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.shinemeow.app"
    compileSdk = 37
    // 用本机已装的 NDK 30，避免 AGP 联网去装它默认的 ndk;28.2.13676358
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.shinemeow.app"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // 只编 arm64；以后要支持老 32 位机再加 armeabi-v7a（需重编一遍库）
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    packaging {
        jniLibs {
            // 关键：让 .so 以解压形式放在 /data/app/.../lib/，
            // 这样 camlib 的 dlopen 才能找到依赖（默认 false 会直接从 APK 加载）
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        // Gradle 9.x 已移除 kotlinOptions DSL，用 compilerOptions 替代
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}


// ===== 应用图标：构建时自动读取仓库根 img/logo.png 生成 mipmap =====
// 逻辑在 buildSrc（Gradle Kotlin DSL 脚本里引用不到 java.awt）；换图标只需替换 logo.png 重新编译
val generateAppIcon = tasks.register("generateAppIcon", GenerateAppIconTask::class.java)
tasks.named("preBuild") { dependsOn(generateAppIcon) }

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // [SCROLLBAR-SEALED] 快速滚动条库已封存：依赖不参与编译，相关 UI 代码以 SCROLLBAR-SEALED
    // 标记注释封存（build.gradle.kts / UiSpec / MainViewModel / MainActivity / GridParts /
    // SettingsScreens / CardsIcons / DownloadsQueue / PreviewPagers）。重新启用步骤：
    //   1. 取消下面这行依赖注释（恢复后仍必须用 2.2.0：3.0.0 是 Kotlin 2.4 元数据，本工程读不了）
    //   2. 全局搜索 "SCROLLBAR-SEALED"，逐处取消注释后重新编译
    // implementation("com.github.nanihadesuka:LazyColumnScrollbar:2.2.0")
}