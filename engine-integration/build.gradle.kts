plugins {
    id("com.android.library") version "9.1.1"
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.wire)
}
group = "com.saif.engine"
version = "1.0"

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
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
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

val bundleEngineResources by tasks.registering(Sync::class) {
    from("../vendor/antigem/extensions") { into("browser-extensions") }
    from("../vendor/antigem/extension_docs") { into("extension-docs") }
    from("../vendor/antigem/LICENSE") { into("licenses"); rename { "ANTIGEM_GPL-3.0.txt" } }
    from("../vendor/antigem/scripts") {
        include("backup_bootstrap.sh", "backup_chats.sh", "restore_chats.sh")
        into("bin")
        rename { it.removeSuffix(".sh") }
    }
    into(layout.buildDirectory.dir("generated/engineAssets"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/engineAssets").get().asFile)
tasks.named("preBuild").configure { dependsOn(bundleEngineResources) }

// The original app builds the Go bridge at build time for Android arm64.
// Generate it in the build directory, rather than modifying the vendored source tree.
val buildEngineBridge by tasks.registering(Exec::class) {
    val bridgeDir = file("../vendor/antigem/agy_ide_bridge")
    val output = layout.buildDirectory.file("generated/bridgeAssets/bin/agy_ide_bridge")
    inputs.dir(bridgeDir)
    outputs.file(output)
    workingDir = bridgeDir
    environment("GOOS", "android")
    environment("GOARCH", "arm64")
    environment("CGO_ENABLED", "0")
    doFirst { output.get().asFile.parentFile.mkdirs() }
    commandLine("go", "build", "-ldflags=-s -w", "-o", output.get().asFile.absolutePath, "main.go")
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/bridgeAssets").get().asFile)
tasks.named("preBuild").configure { dependsOn(buildEngineBridge) }
