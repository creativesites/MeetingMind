import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.meetmind.qxynvp"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    // debug build type intentionally left unconfigured here so AGP falls back to its
    // built-in default debug signing config (auto-generated ~/.android/debug.keystore),
    // matching standard Android tooling behavior and requiring no manual setup step.
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.play.services.location)
  // sherpa-onnx: on-device speech recognition (VAD + Parakeet TDT ASR). Prebuilt AAR from
  // GitHub Releases — see settings.gradle.kts ivy repository and docs/AI_ARCHITECTURE.md.
  // Declared with an explicit "@aar" artifact type since the ivy repo pattern resolves a
  // release-asset file, not a Maven/Ivy module with its own descriptor.
  implementation("k2-fsa:sherpa-onnx:${libs.versions.sherpaOnnx.get()}@aar")
  // Used only for streaming the (optional, user-initiated) local AI model download over
  // HTTPS with progress/cancellation/resume — never for any AI inference call. See
  // docs/AI_ARCHITECTURE.md privacy notes.
  implementation(libs.okhttp)
  // The speaker-segmentation model (pyannote segmentation-3.0) is distributed upstream only as
  // a .tar.bz2 release asset — this extracts the one .onnx file we need from it after download.
  // Never used for anything else (no APK asset bundling, no other archive formats).
  implementation(libs.commons.compress)
  // On-device LLM runtime for local Meeting Intelligence (see docs/AI_ARCHITECTURE.md). Runs a
  // downloaded-on-demand .task model fully offline; never makes a network call itself.
  implementation(libs.mediapipe.tasks.genai)
  // Global playback architecture: a single MediaSessionService-backed player, exposing standard
  // Android media controls (notification/lock-screen/Bluetooth). See docs/ARCHITECTURE.md.
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.exoplayer)
  // Background AI processing survives app minimization/backgrounding — see MeetingProcessingWorker.
  implementation(libs.androidx.work.runtime.ktx)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
