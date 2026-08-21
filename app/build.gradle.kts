plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.wechatshield"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.wechatshield"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // 仅编译期引入 LSPosed 依赖，不打包进 APK
    compileOnly("de.robv.android.xposed:api:82")
}
