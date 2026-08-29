import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Ponder mirrors the web app: it works fully offline on-device with no setup at
// all, and lights up Google sign-in + Firestore sync as soon as a real
// google-services.json is dropped into app/. Applying the plugin conditionally
// keeps the project buildable in both states instead of failing configuration.
val googleServicesJson = layout.projectDirectory.file("google-services.json").asFile
val firebaseConfigured = googleServicesJson.exists()
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
}

// The release signing key, described by an untracked keystore.properties at the
// repo root (see keystore.properties.example). Kept out of this file and out of
// git so the key and its passwords never end up in version control — but read
// here rather than from the environment so a plain `./gradlew assembleRelease`
// just works on the machine that holds them.
//
// Absent, the release build falls back to the debug key: still installable,
// still testable, just not something to publish. Google sign-in is bound to
// whichever certificate actually signs the APK, so the two cases need different
// SHA-1 fingerprints registered in Firebase — see SETUP.md.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
val releaseKeystore = keystoreProperties?.getProperty("storeFile")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.hanifedma.ponder"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.hanifedma.ponder"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Read at runtime so the UI can explain *why* sign-in is unavailable
        // instead of silently doing nothing.
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = keystoreProperties?.getProperty("storePassword")
                keyAlias = keystoreProperties?.getProperty("keyAlias")
                keyPassword = keystoreProperties?.getProperty("keyPassword")
                // Both schemes: v1 keeps Android 6 and older able to install it,
                // v2/v3 are what everything since verifies with.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The real key when there is one, otherwise the debug key — so
            // `assembleRelease` always produces an APK you can install and test.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Cloud: the same Firebase project (and the same /users/{uid}/{collection}
    // documents) the web app reads and writes.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    // Google sign-in through Credential Manager.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Inline media previews.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
