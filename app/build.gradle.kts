import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

// Release signing from a git-ignored keystore.properties or env vars; unsigned otherwise.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    (keystoreProps.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile != null && releaseStorePassword != null &&
    releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.bsfreetv.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bsfreetv.launcher"
        // NVIDIA SHIELD TV (SHIELD Experience 9.x) is Android 11.
        minSdk = 30
        targetSdk = 37
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        // Releases get the tag's version from CI (-PappVersionName). Local builds
        // describe themselves from git: "0.4.0" on a clean tag, "0.4.0-3-gabc1234" after
        // it, "-dirty" with uncommitted changes.
        versionName = (project.findProperty("appVersionName") as String?) ?: gitVersion() ?: "0.0.0-dev"
    }

    signingConfigs {
        getByName("debug") {
            val ks = rootProject.file("keystore/debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // en-XA (long accented text) and ar-XB (right-to-left) for layout checks.
            isPseudoLocalesEnabled = true
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Weblate translations arrive partially; a missing string falls back to English.
        warning += "MissingTranslation"
    }

    bundle {
        // The in-app language override needs every translation on the device;
        // language splits would drop the ones Play thinks you do not use.
        language { enableSplit = false }
    }

    androidResources {
        // Wallpapers are already WebP; recompressing them gains nothing.
        noCompress += "webp"
    }

    testOptions {
        // Robolectric renders real Compose UI for the screenshot tests.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Roborazzi without its Gradle plugin only writes in record mode.
            it.systemProperty("roborazzi.test.record", "true")
            // Screenshots land outside the repo unless a path is given.
            it.systemProperty("bsf.screenshots", (project.findProperty("bsfScreenshots") as String?) ?: layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "baselineProfile"(project(":baselineprofile"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.datastore.preferences)
    implementation(libs.dadb)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.kotlinx.coroutines.test)
}

/** `git describe` for local builds; null without git or tags. safe.directory: the Docker build runs as another user. */
fun gitVersion(): String? = runCatching {
    providers.exec {
        commandLine("git", "-c", "safe.directory=*", "describe", "--tags", "--dirty", "--match", "v[0-9]*")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().removePrefix("v").takeIf { it.isNotEmpty() }
}.getOrNull()
