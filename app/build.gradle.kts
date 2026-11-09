plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.go.pyeongtaek.gpslogger"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.go.pyeongtaek.gpslogger"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
