plugins {
    // AGP 9.0+ 内置 Kotlin 支持, 无需 org.jetbrains.kotlin.android
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fengling.share"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fengling.share"
        minSdk = 33
        targetSdk = 34
        versionCode = 104
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 版本号集中管理 (BuildConfig.VERSION_NAME 供更新检查使用)
    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/fengling.jks")
            storePassword = "REDACTED_KEYSTORE_PASS"
            keyAlias = "fengling"
            keyPassword = "REDACTED_KEYSTORE_PASS"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    // Material 3 (标准 MD3 风格)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    // 导航 (返回栈 + 预测性返回动画)
    implementation(libs.androidx.navigation.compose)
    // Miuix (小米设计语言)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    // 液态玻璃 (miuix blur)
    implementation(libs.miuix.blur)
    // Miuix SearchBar 需要 NavigationEventDispatcherOwner
    implementation(libs.navigationevent.compose)
    // 网络 + 图片
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
