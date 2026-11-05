import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.saifai.assistant"
    // 26: required by SAIF AI AUTOMATION (JARVIS engine)
    minSdk = 26
    targetSdk = 36
    versionCode = 164
    versionName = "16.4"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Telegram asset library: injected from CI secrets at build time, never committed to source.
    val tgToken = (System.getenv("TG_BOT_TOKEN") ?: "").trim().replace("\\", "").replace("\"", "")
    val tgChat = (System.getenv("TG_CHAT_ID") ?: "").trim().replace("\\", "").replace("\"", "")
    buildConfigField("String", "TG_BOT_TOKEN", "\"" + tgToken + "\"")
    buildConfigField("String", "TG_CHAT_ID", "\"" + tgChat + "\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Fallback chain: real release key -> project debug.keystore -> Android's default debug key.
      // The default "debug" config is always valid (AGP auto-creates ~/.android/debug.keystore),
      // so the build never fails with "validateSigning... keystore not found".
      signingConfig = when {
        signingConfigs.getByName("release").storeFile?.exists() == true -> signingConfigs.getByName("release")
        file("${rootDir}/debug.keystore").exists() -> signingConfigs.getByName("debugConfig")
        else -> signingConfigs.getByName("debug")
      }
    }
    debug {
      // debug.keystore is git-ignored, so on CI it is usually missing. Only use it if it exists;
      // otherwise keep AGP's built-in debug signing config.
      if (file("${rootDir}/debug.keystore").exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  if (providers.gradleProperty("embeddedEngine").isPresent) {
    implementation("com.saif.engine:embedded-engine:1.0")
  }
  // SAIF AI AUTOMATION (red + button): JARVIS by Tech GPTx, rebuilt as a module
  implementation(project(":automation"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation("com.android.tools.build:apksig:8.7.3")
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
  // Round 10: the in-app Flow AI browser uses AndroidView (androidx.compose.ui.viewinterop), which is already
  // on the classpath transitively via material3 — verified by main building green with SaifLiveOrb's AndroidView.
  // NOTE: do NOT add a version-less catalog entry for it — compose BOM 2024.09.00 does not constrain
  // ui-viewinterop and Gradle then fails with "Could not find androidx.compose.ui:ui-viewinterop:." (CI run 41286782472).
  implementation("androidx.media3:media3-exoplayer:1.5.1")
  implementation("androidx.media3:media3-ui:1.5.1")
  implementation("androidx.media3:media3-common:1.5.1")
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Firestore for cloud backend data:
  implementation(libs.firebase.firestore)

  // Firebase Auth and Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
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
  "ksp"(libs.moshi.kotlin.codegen)
}

if (providers.gradleProperty("embeddedEngine").isPresent) {
  configurations.configureEach {
    resolutionStrategy.force("com.squareup.okhttp3:okhttp:4.12.0", "com.squareup.okhttp3:okhttp-android:4.12.0")
  }
}
