plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cudgk.retrovhs"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cudgk.retrovhs"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")

    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

// --- Rust (ntsc-rs JNI) build integration ---

fun resolveAndroidNdkDir(): File? {
    val sdkDir = System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { f ->
            f.readLines().firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("=")?.replace("\\:", ":")?.replace("\\\\", "\\")
        }
        ?: return null
    val ndkRoot = File(sdkDir, "ndk")
    if (!ndkRoot.isDirectory) return null
    return ndkRoot.listFiles { f -> f.isDirectory }?.toList()?.maxByOrNull { it.name } ?: null
}

val cargoNdkBuild by tasks.registering(Exec::class) {
    group = "rust"
    description = "Build ntscrs-jni for Android architectures via cargo-ndk."
    val rustDir = rootProject.file("rust/ntscrs-jni")
    val outDir = file("src/main/jniLibs")
    workingDir = rustDir
    inputs.dir(rustDir.resolve("src"))
    inputs.file(rustDir.resolve("Cargo.toml"))
    outputs.dir(outDir)
    val ndk = resolveAndroidNdkDir()
    if (ndk != null) environment("ANDROID_NDK_HOME", ndk.absolutePath)
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val args = listOf(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "armeabi-v7a",
        "-t", "x86_64",
        "-t", "x86",
        "-o", outDir.absolutePath,
        "build", "--release",
    )
    commandLine = if (isWindows) listOf("cmd", "/c") + args else args
    isIgnoreExitValue = false
    doFirst {
        if (ndk == null) error("Android NDK not found. Install via SDK Manager or set ANDROID_NDK_HOME.")
    }
}

tasks.named("preBuild") { dependsOn(cargoNdkBuild) }
