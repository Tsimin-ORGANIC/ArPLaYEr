plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreB64 = System.getenv("KEYSTORE_BASE64").orEmpty()
val keystorePassword = System.getenv("KEYSTORE_PASSWORD").orEmpty()
val hasSigning = keystoreB64.isNotBlank() && keystorePassword.isNotBlank()

android {
    namespace = "com.tsimin.arplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tsimin.arplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file("keystore.jks")
                storePassword = keystorePassword
                keyAlias = "arplayer"
                keyPassword = keystorePassword
                storeType = "jks"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
}
