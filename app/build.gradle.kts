plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alertme.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alertme.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        // TDLib بيتبني للأربع معماريات، بنحتفظ بس بمعماريات الموبايلات
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // TdApi.java المتولّد بيستخدم @IntDef من المكتبة دي
    implementation("androidx.annotation:annotation:1.8.2")
}
