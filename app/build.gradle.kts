plugins {
    id("com.android.application")
}

val releaseVersionName = (project.findProperty("versionName") as String?) ?: "0.1.0"
val releaseVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

val keystorePath = System.getenv("KEYSTORE_PATH")
val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("KEY_ALIAS")
val releaseKeyPassword = System.getenv("KEY_PASSWORD")
val hasReleaseKeystore = listOf(keystorePath, keystorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.garen.glyphcharge"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.garen.glyphcharge"
        minSdk = 36
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                this.keyAlias = releaseKeyAlias
                this.keyPassword = releaseKeyPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
