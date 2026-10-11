import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 从 local.properties（不入库）或环境变量读取 Release 签名配置
val qmusicLocalProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(key: String): String? =
    qmusicLocalProps.getProperty(key) ?: System.getenv(key.replace(".", "_").uppercase())

// ------------------------------------------------------------------
// 版本号与打包命名（android/version.properties）
//   - versionName 每次打包 +0.01
//   - versionCode 每次打包 +1
//   - APK 文件名 = <appName>-<versionName>-<buildType>.apk
// 由 android/scripts/bump-version.js 自动维护，也可手动改。
// ------------------------------------------------------------------
val versionFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionFile.exists()) versionFile.inputStream().use { load(it) }
}
val appDisplayName: String = versionProps.getProperty("appName") ?: "qMusic"
val appVersionName: String = versionProps.getProperty("versionName") ?: "1.0.0"
val appVersionCode: Int = (versionProps.getProperty("versionCode") ?: "1").toInt()

android {
    namespace = "com.qmusic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.qmusic"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 备注：BuildConfig 里也带上，便于运行期展示版本
        buildConfigField("String", "APP_DISPLAY_NAME", "\"$appDisplayName\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(prop("qmusic.storeFile") ?: "qmusic.keystore")
            storePassword = prop("qmusic.storePassword") ?: ""
            keyAlias = prop("qmusic.keyAlias") ?: "qmusic"
            keyPassword = prop("qmusic.keyPassword") ?: ""
        }
    }

    buildTypes {
        debug {
            // 调试统一走 debug 包，并且**沿用 release 证书**签名。
            //
            // 原因：debug 与 release 若各用一套证书，二者无法互相覆盖安装，
            // 每次切换都得先卸载 —— 本地收藏/歌单会被清空，桌面小组件实例也会被移除。
            // 同证书后 debug 可以直接覆盖 release（反之亦然），调试期数据不丢。
            // 若本机没有 release keystore（他人 clone 后首次构建），自动回落到默认 debug 证书。
            val ks = rootProject.file(prop("qmusic.storeFile") ?: "qmusic.keystore")
            if (ks.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            applicationIdSuffix = null
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // 学习项目：release 构建跳过 Lint 致命检查，保证可复现构建
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// ------------------------------------------------------------------
// APK 产物命名：qMusic-1.01-release.apk
// （同时把 debug 包命名为 qMusic-1.01-debug.apk，方便区分）
// ------------------------------------------------------------------
androidComponents {
    onVariants { variant ->
        val buildType = variant.buildType ?: "release"
        variant.outputs.forEach { output ->
            val fileName = "$appDisplayName-$appVersionName-$buildType.apk"
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName
                ?.set(fileName)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation("androidx.media:media:1.7.0")
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}