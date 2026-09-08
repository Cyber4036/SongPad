plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// :core:audio — Oboe capture engine, C++ ring buffer, writer thread, FGS, audio focus, Media3 playback,
// transcode worker, recovery manager (§7, §9). THE ONLY MODULE WITH NDK/CMAKE/JNI (§5).

android {
    namespace = "com.songpad.core.audio"
    compileSdk = 36
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-fno-exceptions", "-fno-rtti")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        prefab = true
        buildConfig = true
    }

    packaging {
        jniLibs.useLegacyPackaging = false
    }
}

dependencies {
    api(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Oboe via Prefab (§4.1)
    implementation(libs.google.oboe)

    // Media3 / ExoPlayer playback (§7.6)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // Transcode worker (§4.1)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:data:testing"))
    androidTestImplementation(libs.androidx.test.ext.junit)
}
