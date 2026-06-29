import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.ksp)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

val zhipuApiKey = localProperties.getProperty("ZHIPU_API_KEY", "")
val weatherApiKey = localProperties.getProperty("WEATHER_API_KEY", "")
val amapApiKey = localProperties.getProperty("AMAP_API_KEY", "")

android {
    namespace = "com.example.healthmanager"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.healthmanager"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ZHIPU_API_KEY", "\"$zhipuApiKey\"")
        buildConfigField("String", "WEATHER_API_KEY", "\"$weatherApiKey\"")

        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.foundation)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("com.amap.api:3dmap:10.0.600")
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("com.google.android.gms:play-services-location:21.0.1")
    // 扩展图标库 (用于获取类似 Lucide 的丰富图标)
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    // 图片加载 (类似 React 的 <img>)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("no.nordicsemi.android:ble:2.7.2")
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 用于解析 JSON 数据
    implementation("com.google.code.gson:gson:2.10.1")
}
