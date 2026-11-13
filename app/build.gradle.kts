import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing is read from keystore.properties (never committed). See docs/RELEASE.md.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.tervyn.vpn"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tervyn.vpn"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    splits {
        abi {
            // Per-ABI APKs keep sideload downloads small (the Xray binary is ~10 MB per ABI).
            isEnable = project.hasProperty("splitApks")
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // libxray.so is an executable, not a JNI library: it must be extracted to nativeLibraryDir
        // so it can be exec()'d (W^X forbids executing files from the app's data directory).
        jniLibs { useLegacyPackaging = true }
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*") }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    sourceSets {
        // Xray-core executables (scripts/build-xray.sh or scripts/fetch-xray.sh) packaged as libxray.so per ABI.
        getByName("main") { jniLibs.directories.add(rootProject.file("xray/android").path) }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

/** Verifies packaged Xray binaries against xray/android/SHA256SUMS (or warns if they are missing). */
val verifyXray by tasks.registering {
    val dir = rootProject.file("xray/android")
    inputs.files(fileTree(dir) { include("*/libxray.so", "SHA256SUMS*") }).optional()
    doLast {
        val bins = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86").map { File(dir, "$it/libxray.so") }
        if (bins.none { it.exists() }) {
            logger.warn("Xray-core binaries not found in xray/android. Advanced protocols will be unavailable. Run scripts/build-xray.sh or scripts/fetch-xray.sh.")
            return@doLast
        }
        val sums = listOf("SHA256SUMS", "SHA256SUMS.official").map { File(dir, it) }.filter { it.exists() }
            .flatMap { it.readLines() }.filter { it.isNotBlank() }
            .associate { it.substringAfter("  ").trim() to it.substringBefore("  ").trim() }
        bins.filter { it.exists() }.forEach { f ->
            val rel = f.parentFile.name + "/libxray.so"
            val actual = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
            val expected = sums[rel] ?: throw GradleException("No checksum recorded for $rel")
            if (expected != actual) throw GradleException("SHA-256 mismatch for $rel: expected $expected, got $actual")
        }
        logger.lifecycle("Xray-core binaries verified (${bins.count { it.exists() }} ABIs)")
    }
}
tasks.named("preBuild") { dependsOn(verifyXray) }

tasks.withType<Test>().configureEach {
    systemProperty("xray.host.binary", rootProject.file("xray/host/xray").absolutePath)
    maxHeapSize = "3g"
    forkEvery = 40
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    implementation(libs.biometric)
    implementation(libs.splashscreen)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
