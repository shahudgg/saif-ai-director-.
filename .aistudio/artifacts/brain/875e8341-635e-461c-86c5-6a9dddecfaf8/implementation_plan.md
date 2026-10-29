# SAIF AI Comprehensive Stability & Autonomous Automation Plan

This plan addresses and resolves all 5 critical issues identified from user testing, videos, and logs:
1. **Ashna AI Model Synchronization & 404 Bug**: Fix model selection overriding in `ProviderSettingsManager`, remove hardcoded fallback to invalid models, and respect user selection (such as `deepseek-v4.1-flash`) with graceful multi-model fallback.
2. **Autonomous Screen Automation Multi-Command Loop & Typo Fix**: Eliminate task re-execution loops by terminating finished tasks cleanly, preventing repeat triggers, robustly searching and typing in Google Play Store & YouTube, and executing multi-step voice workflows sequentially.
3. **Voice TTS Stutter & Robotic Glitch Fix**: Smooth out TTS utterance streaming by buffering whole sentences, preventing overlapping speech triggers, and tuning speech pitch/rate.
4. **SAIF AI Studio Workspace Isolation & Real Change Application**: Ensure that creating a new project starts with an isolated, clean workspace directory on disk instead of bleeding into the previous project, and guarantee user prompt changes generate real, functional UI with an expandable Google AI Studio change summary.
5. **In-App APK Installation Parsing Error Fix**: Fix `There was a problem while parsing the package` by aligning `minSdkVersion` in the compiled binary AXML with modern Android requirements and ensuring proper APK alignment and dual v1+v2 signing.

---

### Critical Review & Confirmed Decisions

> [!IMPORTANT]
> The architectural directions for this patch are based on the user's requirements:
> - **Ashna AI Model Handling**: User's selected model (e.g. `deepseek-v4.1-flash`) is strictly respected; if an HTTP 404/429 occurs, auto-fallback switches intelligently to `ashna-x1` without causing chat crashes.
> - **Autonomous Automation**: Full multi-step queue execution with atomic step advancement, auto-stop on completion, and loop prevention.
> - **Workspace Isolation**: Clean project creation initializes a dedicated filesystem workspace under `context.filesDir/projects/<sanitized_name>` with immediate state reset.
> - **APK Packaging**: Strict Android binary AXML attribute alignment (minSdk 26+ compatible) and v1+v2 dual signing.

---

## 1. Technical Architecture & Data Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             SAIF AI APP                                     │
├──────────────────────────────────────┬──────────────────────────────────────┤
│ 1. AI Engine & Provider Layer        │ 2. Voice & Autonomous Screen Agent   │
│ ┌──────────────────────────────────┐ │ ┌──────────────────────────────────┐ │
│ │ ProviderSettingsManager          │ │ │ LiveVoiceSessionManager          │ │
│ │ • Sanitizes without overriding   │ │ │ • Complete sentence buffering    │ │
│ │ • Saves activeModel & buildModel │ │ │ • Smooth TTS via TTSManager      │ │
│ └─────────────────┬────────────────┘ │ └─────────────────┬────────────────┘ │
│                   │                  │                   │                  │
│ ┌─────────────────▼────────────────┐ │ ┌─────────────────▼────────────────┐ │
│ │ LlmClient / AIApiUtility         │ │ │ AutonomousScreenAgent            │ │
│ │ • Uses exact selected model      │ │ │ • Play Store search & install    │ │
│ │ • Safe 404/429 auto-fallback     │ │ │ • YouTube play & subscribe       │ │
│ │ • Universal API key compatibility│ │ │ • No endless loop on finish      │ │
│ └──────────────────────────────────┘ │ └──────────────────────────────────┘ │
├──────────────────────────────────────┼──────────────────────────────────────┤
│ 3. Project Studio Workspace          │ 4. Local APK Compiler Engine         │
│ ┌──────────────────────────────────┐ │ ┌──────────────────────────────────┐ │
│ │ CodeEditorViewModel & BuildAgent │ │ │ BinaryAxmlGenerator              │ │
│ │ • Fresh workspace per project    │ │ │ • Valid binary AXML chunks       │ │
│ │ • State & file tree reset        │ │ │ • MinSdk 26 / TargetSdk 34       │ │
│ │ • Real working UI & summary card │ │ ├──────────────────────────────────┤ │
│ └─────────────────┬────────────────┘ │ │ ApkSigner (v1 + v2 schemes)      │ │
│                   │                  │ │ • Fix package parsing error        │ │
│                   │                  │ └──────────────────────────────────┘ │
└───────────────────┴──────────────────┴──────────────────────────────────────┘
```

---

## 2. Key Product Decisions & Component Specifications

### A. Ashna AI Model Selection & Chat Engine (`ProviderSettingsManager`, `AiProviderSettingsInline`, `LlmClient`)
- **Problem**: When user selects `deepseek-v4.1-flash` on Ashna AI, `sanitizeModelName` was resetting non-gemini/llama models to `ashna-x1` or falling back to `ashna-x1-flash` (which returns HTTP 404 on Ashna AI).
- **Fix**:
  - Preserve any non-blank model chosen by the user in `ProviderSettingsManager.sanitizeModelName` and `sanitizeBuildModelName`.
  - When saving in `AiProviderSettingsInline`, never override a user-selected model from the fetched models list.
  - In `LlmClient.kt`, update the 404/429 self-healing logic: instead of hardcoding `ashna-x1-flash`, fall back to `ashna-x1` (verified live on Ashna AI).

### B. Autonomous Screen Automation & Loop Termination (`LiveVoiceSessionManager`, `AutonomousScreenAgent`)
- **Problem**:
  1. Once a WhatsApp or YouTube task completes, the voice session repeats the prompt or keeps re-triggering the action continuously.
  2. Play Store search opens the app and taps search but fails to type the search query or install apps.
- **Fix**:
  - In `LiveVoiceSessionManager`, set a cooldown and distinct turn state when a task completes. Mark the task as completed in `TurnManager` and ignore residual transcription echoes.
  - In `AutonomousScreenAgent`:
    - Add explicit handling for Play Store search: `PLAY_STORE_SEARCH` that opens `com.android.vending`, targets the search box by view ID or text, types the query, and presses search.
    - Improve WhatsApp messaging with verification: ensure contact click, text entry, and send button click are verified before finishing, then emit a single completion event.

### C. Voice TTS Natural Speech & Glitch Elimination (`TTSManager`, `LiveVoiceSessionManager`)
- **Problem**: Voice speaks with robotic pauses and glitches because small token fragments are enqueued before complete sentences form.
- **Fix**:
  - Buffer streaming responses until full punctuation marks (`.`, `!`, `?`, `|`) occur before handing them to `TTSManager`.
  - In `TTSManager`, apply pitch and speech rate uniformly without abrupt restarts between adjacent utterances.

### D. Workspace Isolation for New Projects (`CodeEditorViewModel`, `ChatViewModel`, `SaifAiApp`)
- **Problem**: When creating a new project in SAIF AI Studio, the file tree and chat messages showed the old project's contents, and prompt modifications targeted old files.
- **Fix**:
  - In `CodeEditorViewModel.initProject`, reset `isInitialized = false`, clear existing file tree cache, active file, and undo/redo stacks when `project.appName` changes.
  - In `ChatViewModel.createProjectSession`, ensure project state is clean and distinct.
  - In `BuildAIScreen`, display the **Summary of Implemented Changes** card with Google AI Studio-style checklist always visible upon generation.

### E. In-App APK Packaging Parsing Error Fix (`BinaryAxmlGenerator`, `AppCompilerEngine`)
- **Problem**: Android package installer displays `There was a problem while parsing the package`. This happens when binary AXML format has attribute index offsets or when `minSdkVersion` / `targetSdkVersion` integer values are misaligned, or when v2 signing is required on newer Android versions.
- **Fix**:
  - Update `BinaryAxmlGenerator.kt` to ensure standard Android binary AXML attribute order and correct type tags (`TYPE_INT_DEC` with 32-bit values).
  - Ensure `AppCompilerEngine.kt` packages standard 4-byte aligned APK entries and applies both v1 (JAR signing) and v2 (APK Signature Scheme v2) with valid debug keystore.
  - Verify that `installApk` sets `Intent.FLAG_GRANT_READ_URI_PERMISSION` and provides clear guidance if Play Protect warns the user.

---

## 3. Verification Plan

1. **Compilation Check**: Run `compile_applet` to verify no syntax errors across all touched files.
2. **Provider Key & Model Test**: Verify that saving Ashna AI with `deepseek-v4.1-flash` retains `deepseek-v4.1-flash` as active model and executes chat requests without 404 errors.
3. **Automation Step Verification**: Ensure unit tests in `SaifBuildEngineTest.kt` and `LiveVoiceAutomationTests.kt` pass.
4. **Project Creation & Disk Cleanliness**: Verify that creating a new project loads a fresh template and writes files to its own distinct directory.
