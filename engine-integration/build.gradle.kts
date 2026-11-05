plugins {
    id("com.android.library") version "9.0.1"
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.wire)
}
android {
    namespace = "com.example.gemini"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "APPLICATION_ID", "\"com.saifai.assistant\"")
        buildConfigField("String", "VERSION_NAME", "\"1.0.19\"")
        buildConfigField("int", "VERSION_CODE", "119")
        buildConfigField("int", "BRIDGE_PORT", "1236")
        buildConfigField("int", "HUB_PORT", "1237")
        buildConfigField("int", "BROWSER_MCP_PORT", "8767")
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("main").apply {
        java.srcDir("../vendor/antigem/app/src/main/java")
        res.srcDir("../vendor/antigem/app/src/main/res")
        assets.srcDir("../vendor/antigem/app/src/main/assets")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
kotlin { jvmToolchain(21) }
dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
  implementation("androidx.work:work-runtime-ktx:2.10.0")

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Networking and Serialization
  implementation(libs.okhttp)
  implementation(libs.okhttp.sse)
  implementation(libs.androidx.webkit)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.datastore.preferences)
  implementation("com.telemetrydeck:kotlin-sdk:6.0.1")

  // Image Loading (Coil for Markdown Images and IDE Asset Viewer)
  implementation("io.coil-kt:coil-compose:2.7.0")
  implementation("io.coil-kt:coil-svg:2.7.0")

  // Native Android JLaTeXMath / LaTeX Renderer (Pure Android Canvas, Zero WebViews)
  implementation("io.noties.markwon:ext-latex:4.6.2")
  implementation("ru.noties:jlatexmath-android:0.2.0")

  // Pure Java/Kotlin SSH Client (Persistent Termux SSH connection pool)
  implementation("com.github.mwiede:jsch:0.2.20")

  // HTML Parser & Web Content Scraper (Free Web Search & Webpage Reader)
  implementation("org.jsoup:jsoup:1.18.3")

  // Sora Editor (Industry standard Android Code Editor)
  implementation("io.github.Rosemoe.sora-editor:editor:0.23.6")
  implementation("io.github.Rosemoe.sora-editor:language-textmate:0.23.6")
  implementation("io.github.Rosemoe.sora-editor:language-java:0.23.6")

  // Archive and Compression Utilities (Supports tar, xz, ar, deb extraction)
  implementation("org.apache.commons:commons-compress:1.26.1")
  implementation("org.tukaani:xz:1.9")

  // Termux Terminal Emulator with Native PTY Support (Terminal View is compiled from source)
  implementation("com.github.termux.termux-app:terminal-emulator:v0.118.1")
  implementation("com.google.guava:listenablefuture:1.0")

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Wire Protobuf & gRPC runtime
  implementation(libs.wire.runtime)
  implementation(libs.wire.grpc.client)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}


wire {
    sourcePath { srcDir("../vendor/antigem/app/src/main/proto") }
    kotlin { rpcRole = "client"; rpcCallStyle = "suspending" }
}

configurations.configureEach {
    resolutionStrategy.force("com.squareup.okhttp3:okhttp:4.12.0", "com.squareup.okhttp3:okhttp-android:4.12.0")
}
