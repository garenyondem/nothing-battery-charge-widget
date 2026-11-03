plugins {
    id("com.android.application")
}

android {
    namespace = "com.garen.glyphcharge"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.garen.glyphcharge"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
