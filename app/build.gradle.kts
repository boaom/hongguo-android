import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hongguo.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.hongguo.android"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("appVersionCode").orElse("45").get().toInt()
        versionName = providers.gradleProperty("appVersionName").orElse("0.7.34").get()
        // 更新源指向自己的仓库，避免弹出上游更新提示（签名与分发渠道都不同）。
        buildConfigField("String", "UPDATE_REPOSITORY", "\"boaom/hongguo-android\"")
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"https://github.com/boaom/hongguo-android/releases/latest/download/update.json\"")
        testInstrumentationRunner = "com.hongguo.android.HongguoTestRunner"
    }

    // 固定签名：CI 从 GitHub Secrets 还原 keystore，保证每次构建的 APK 签名一致、可覆盖安装。
    // 本机没有这些环境变量时回退到 debug 签名，不影响日常开发。
    signingConfigs {
        create("fixed") {
            val storeB64 = System.getenv("SIGNING_KEYSTORE_B64")
            val storeFileEnv = System.getenv("SIGNING_KEYSTORE_FILE")
            if (!storeB64.isNullOrBlank()) {
                val file = layout.buildDirectory.file("signing/release.p12").get().asFile
                file.parentFile.mkdirs()
                file.writeBytes(Base64.getDecoder().decode(storeB64))
                storeFile = file
            } else if (!storeFileEnv.isNullOrBlank()) {
                storeFile = file(storeFileEnv)
            }
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            storeType = "pkcs12"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
        debug {
            // App 目前分发 debug 包；有固定签名时也用它，否则回退默认 debug 签名。
            if (!System.getenv("SIGNING_KEYSTORE_B64").isNullOrBlank() ||
                !System.getenv("SIGNING_KEYSTORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("fixed")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
