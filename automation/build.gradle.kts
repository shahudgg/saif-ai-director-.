// SAIF AI AUTOMATION - library module.
// Based on the JARVIS Android Assistant by Tech GPTx (TAMCL v1.0, see LICENSE).
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.jarvis.assistant"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 26
    consumerProguardFiles("consumer-rules.pro")

    buildConfigField("String", "DEVELOPER_NAME", "\"Tech GPTx\"")
    buildConfigField("String", "COPYRIGHT_OWNER", "\"Tech GPTx\"")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    viewBinding = true
    buildConfig = true
  }

  lint {
    abortOnError = false
    checkReleaseBuilds = false
    disable += "NullSafeMutableLiveData"
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("androidx.webkit:webkit:1.12.0")
  implementation("androidx.activity:activity-ktx:1.10.1")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  implementation("com.google.code.gson:gson:2.10.1")
  implementation("net.java.dev.jna:jna:5.13.0@aar")
  implementation("com.alphacephei:vosk-android:0.3.75@aar")

  // Memory database (KSP, same as the main app)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  "ksp"(libs.androidx.room.compiler)

  // Encrypted storage
  implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
