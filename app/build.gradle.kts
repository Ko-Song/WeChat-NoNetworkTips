plugins {
    id("com.android.application")
}

// 自动获取 GitHub Actions 的构建序号作为版本号，本地编译则默认为 1
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1

android {
    namespace = "com.example.wechatshield"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.wechatshield"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
