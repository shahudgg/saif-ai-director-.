# Embedded engine compilation experiment

This isolated Gradle project checks that the Android sources in `vendor/antigem` can compile as a library. The opt-in host build links this library and registers a small internal-only subset of activities and services. It is not a working embedded engine. The ordinary SAIF build is deliberately unaffected.

## Check

With JDK 21, Go (matching the bridge go.mod), Gradle 9.3.1 and the Android SDK installed:

```sh
gradle -p engine-integration assembleDebug
gradle :app:assembleDebug -PembeddedEngine
```

GitHub Actions checks the library and the opt-in host APK, including the bridge binary and other bundled assets. A separate API 35 x86_64 emulator smoke test installs the official Termux APK, launches the internal engine activity, checks that it reaches RESUMED, and verifies Termux remains installed. This is only a coexistence and launch smoke test: it does not exercise terminal installation, terminal execution, or agent setup. The original source and its GPL license are under `vendor/antigem`.

## Remaining integration work

- Complete and audit the host integration: the original UI, dependencies and selected components compile in an opt-in APK; remaining services, providers, permissions, assets and initialization are not yet complete.
- The original recommended flavor assumes package `com.termux` and a target SDK of 28. SAIF uses a different application ID and targets SDK 36. The original source's alternate Ubuntu/PRoot path looks for `assets/bin/proot` or `libproot.so`, neither of which is present in the uploaded archive; it must be supplied and verified for supported ABIs and Android versions. Android restrictions on executing writable app files also need to be addressed. Do not rely on the official Termux application's private files or uninstall it.
- The opt-in build compiles and bundles the original Android arm64 Go bridge, scripts, extension assets, documentation and GPL license. APK contents are checked in CI, but actual execution is not verified. Implement runtime downloads with checksums, network failure handling, storage, upgrades and cancellation; report installation success only after real execution is verified.
- Test original screens and real chat, editor, terminal, browser and agent integration on devices, including one with official Termux installed. Preserve GPL notices and third-party license obligations when rebranding.

A successful build and activity launch alone do not demonstrate a working Linux runtime, agent functionality, or compatibility with the official Termux app. The opt-in build must not be promoted as a complete engine until those paths are tested.
