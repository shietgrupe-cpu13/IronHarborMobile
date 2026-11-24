plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// Iron Harbor's Android app version is independent from the embedded VERA20K
// engine version. Keep the mobile label on its own track so it does not claim
// to be the upstream 0.4.2 release.
val ironHarborVersionName = providers.gradleProperty("ironHarborVersionName").orElse("0.4.1").get()
// versionCode is a monotonically rising integer that Android compares for
// upgrades; bump it by hand for every published build.
val veraVersionCode = 265
val veraBuildLabel = "B58"
val sideBySideDebug = providers.gradleProperty("ironHarborSideBySideDebug").orNull == "true"
val releaseStoreFile = System.getenv("ANDROID_RELEASE_STORE_FILE")
val releaseStorePassword = System.getenv("ANDROID_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_RELEASE_KEY_PASSWORD")
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { task ->
        task.project == project && task.name.contains("Release")
    }
    if (releaseRequested && !releaseSigningConfigured) {
        throw GradleException(
            "Release signing requires ANDROID_RELEASE_STORE_FILE, " +
                "ANDROID_RELEASE_STORE_PASSWORD, ANDROID_RELEASE_KEY_ALIAS, " +
                "and ANDROID_RELEASE_KEY_PASSWORD."
        )
    }
}

android {
    namespace = "org.vera20k"
    compileSdk = 36
    // Pinned to the installed version: AGP otherwise downloads the build-tools
    // release it prefers, which fails on a machine without network access to
    // dl.google.com in the middle of a build.
    buildToolsVersion = "36.1.0"
    // NDK r28 aligns LOAD segments for Android 15's 16 KB pages by default;
    // r27 and older need -Wl,-z,max-page-size=16384 instead (see P9).
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "org.ironharbor.mobile"
        // cpal 0.17's Android backend is AAudio, which needs API 26.
        minSdk = 26
        targetSdk = 36
        versionCode = veraVersionCode
        versionName = ironHarborVersionName
        resValue("string", "app_build_label", veraBuildLabel)

        // This build packages the shipping ARM64 Rust library. Do not advertise
        // x86_64 unless its native library is built and packaged as well.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // The debug keystore AGP generates lives in the checkout, not in
    // %USERPROFILE%\.android. That folder is not writable in a sandboxed shell
    // or most CI containers, and validateSigningDebug then fails on
    // `debug.keystore.lock` after every other task has already succeeded.
    signingConfigs {
        getByName("debug") {
            storeFile = System.getenv("ANDROID_DEBUG_STORE_FILE")
                ?.takeIf { it.isNotBlank() }
                ?.let(::file)
                ?: rootProject.file(".android/debug.keystore")
            storePassword = System.getenv("ANDROID_DEBUG_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("ANDROID_DEBUG_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("ANDROID_DEBUG_KEY_PASSWORD") ?: "android"
        }
        create("release") {
            storeFile = rootProject.file(
                releaseStoreFile ?: ".android/android-release.jks"
            )
            storePassword = releaseStorePassword ?: ""
            keyAlias = releaseKeyAlias ?: ""
            keyPassword = releaseKeyPassword ?: ""
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            if (sideBySideDebug) applicationIdSuffix = ".debug"
        }
        release {
            // Keep JVM entry points named for the native JNI callbacks.
            // The shipping Cargo command strips native symbols separately.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // cargo-ndk writes libvera20k_android.so here; the directory is generated
    // and git-ignored.
    sourceSets.getByName("main").jniLibs.srcDir("src/main/jniLibs")

    packaging {
        // Keep the .so uncompressed and mapped from the APK
        // (extractNativeLibs="false" in the manifest).
        jniLibs.useLegacyPackaging = false
    }
}
