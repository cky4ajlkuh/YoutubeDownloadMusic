plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.youtubedownloadmusic"
    compileSdk {
        version = release(37)
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    defaultConfig {
        applicationId = "com.example.youtubedownloadmusic"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation(libs.library)
    implementation(libs.ffmpeg)
    implementation(libs.kotlinx.coroutines.android)
}