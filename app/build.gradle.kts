import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.craftflowtechnologies.meetingmind"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.craftflowtechnologies.meetingmind"
    minSdk = 24
    targetSdk = 36
    versionCode = 37
    versionName = "1.0-v37"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // YouVersion Platform app key (docs/PLAN_V1.md §7). Read from local.properties
    // (youversion.appKey=...) or the YOUVERSION_APP_KEY environment variable — never committed to
    // this public repository. It is designed to ship in the app; without it, verse text shows as
    // unavailable and references still work.
    val youVersionKey = run {
      val props = Properties()
      val local = rootProject.file("local.properties")
      if (local.exists()) local.inputStream().use { props.load(it) }
      props.getProperty("youversion.appKey") ?: System.getenv("YOUVERSION_APP_KEY") ?: ""
    }
    buildConfigField("String", "YOUVERSION_APP_KEY", "\"${youVersionKey.replace("\"", "")}\"")
    // System Gemini API key for closed testing (via SYSTEM_GEMINI_API_KEY env var)
    val systemGeminiKey = System.getenv("SYSTEM_GEMINI_API_KEY") ?: ""
    buildConfigField("String", "SYSTEM_GEMINI_API_KEY", "\"${systemGeminiKey.replace("\"", "")}\"")
    // DeepSeek, the fallback for AI text. The key stays out of public builds: production goes
    // through the proxy in server/deepseek-proxy (DEEPSEEK_PROXY_URL), which holds the key and
    // enforces each install's monthly allowance. SYSTEM_DEEPSEEK_API_KEY is for private builds only.
    buildConfigField("String", "DEEPSEEK_PROXY_URL", "\"${(System.getenv("DEEPSEEK_PROXY_URL") ?: "").replace("\"", "")}\"")
    buildConfigField("String", "SYSTEM_DEEPSEEK_API_KEY", "\"${(System.getenv("SYSTEM_DEEPSEEK_API_KEY") ?: "").replace("\"", "")}\"")
    buildConfigField("Boolean", "SYSTEM_GEMINI_MODE", "true")
  }

  signingConfigs {
    // One fixed key for development builds, committed on purpose: it only signs the separate
    // "MeetingMind Dev" app (.dev), and a fixed key means each new dev APK updates the last one
    // in place — keeping its notes — instead of every build machine minting its own.
    getByName("debug") {
      storeFile = file("dev-debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
    create("release") {
      // Secrets come from the environment only: this repository is public.
      storeFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/meetingmind-upload-key.jks")
      storePassword = System.getenv("KEYSTORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    debug {
      // Installs beside the tester/Play app, never over it: its own package, name and data.
      applicationIdSuffix = ".dev"
      versionNameSuffix = "-dev"
    }
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
  /**
   * arm64-v8a and armeabi-v7a split APKs.
   *
   * The sherpa-onnx and MediaPipe native libraries dominate this app's size, and a universal APK
   * ships every architecture's copy to every device — a quarter of a gigabyte, of which any given
   * phone uses about a third. Building one ABI takes that to ~86MB for arm64, ~60MB for armv7.
   *
   * arm64-v8a is the primary target: it is what the Galaxy S20 family runs (Exynos 990 and
   * Snapdragon 865 are both 64-bit ARM), and what every Android phone shipped in roughly the last
   * decade runs.
   * 32-bit armeabi-v7a is also built for legacy devices from 2015–2017 that are still in use.
   * The models run on 32-bit but with reduced capability; the LLM falls back to smaller models.
   *
   * Consequence worth knowing: there is no x86_64 output, so this will not install on an x86
   * emulator. An arm64 emulator (the default on Apple Silicon) is fine. Add "x86_64" to the
   * include list if an x86 emulator is needed.
   */
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a")
      isUniversalApk = false
    }
  }

  // Robolectric sandboxes and screenshot bitmaps outgrow Gradle's default 512 MB test heap, which shows up as UI tests waiting forever.
  testOptions { unitTests { isIncludeAndroidResources = true; all { it.maxHeapSize = "3g"; it.jvmArgs("-Dfile.encoding=UTF-8") } } }
  // Room's exported schemas, one JSON per database version, so migrations are tested against the
  // real shape of every version (MigrationTestHelper reads them from test assets).
  sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
  // Prompts are versioned assets (assets/prompts); also on the classpath so the pure engines
  // and their unit tests read the very same files.
  sourceSets.getByName("main").resources.srcDir("src/main/assets/prompts")
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

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
  // Optional App Lock (docs/APP_LOCK.md): the platform BiometricPrompt, so MeetingMind only ever
  // asks Android "is this the owner?" and never handles biometric data itself.
  implementation(libs.androidx.biometric)
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
  // Document scanner for the Work Inbox: runs on the device, and hands back a PDF or pictures (docs/PLAN_PROFESSIONAL.md D7).
  implementation(libs.mlkit.document.scanner)
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
  // Real word-level diffing for the AI-tools result review screen (recording page redesign
  // phase 6) — per docs/recording-page-implementation.md §4, a real diff library, not a
  // hand-rolled one.
  implementation(libs.java.diff.utils)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.androidx.room.testing)
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
