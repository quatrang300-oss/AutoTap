plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.autotap.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autotap.app"
        minSdk = 30          // Android 11+ (cần cho chụp màn hình qua Trợ năng)
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }

    // Chữ ký cố định: mọi bản build (máy bạn hay GitHub) đều cùng chữ ký → cài đè được, không mất dữ liệu.
    signingConfigs {
        create("autotap") {
            storeFile = file("autotap.keystore")
            storePassword = "autotap123"
            keyAlias = "autotap"
            keyPassword = "autotap123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("autotap")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("autotap")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Quảng cáo Google AdMob
    implementation("com.google.android.gms:play-services-ads:23.6.0")
}
