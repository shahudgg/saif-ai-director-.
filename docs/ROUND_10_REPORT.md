# Round 10 report — SAIF AI Director update ZIP

Four asks, all in this ZIP. Nothing was compiled in the sandbox (no JDK); every touched file was
bracket-checked and every cross-module call was verified against its declaration. **GitHub Actions
(`.github/workflows/build-apk.yml`) is the first real compile** — if it fails, paste the error log.

## A. Purple mic (Normal / Screen share) works with ANY API key
- `SttRouter` pre-initializes Vosk and waits for whichever STT is configured instead of assuming Gemini.
- `LiveTurnLoop` runs a provider-agnostic turn-based **STT → `AiClient` → TTS** loop for non-Gemini
  providers; the Gemini Live WebSocket path is kept when the key is Gemini.
- Screen-share mode gets an accessibility-text vision fallback, and the host loop in
  `JarvisConversationService` no longer hard-requires a Gemini key.

## B. Zero-to-video, image-first, ~15 unique scenes per minute, live progress
- Scene planning now targets `clamp(estMs/4000, 6, 24)` beats with `targetBeatMs = clamp(estMs/target, 2200, 9000)`
  and `maxBeats = 30` — about **15 scenes for a 1-minute video**, flexible by topic length.
- `sceneBatchV2` prompt schema gained `"imagePrompt"` + an **IMAGE-FIRST RULE**: the model writes one detailed
  English image-generation prompt per scene (empty only for pure motion-graphics beats). `Scene.imagePrompt`
  is parsed and re-encoded with the manifest.
- New `llm/ImageGen.kt` — image **generation and editing** with the user's own key (BYOK, no developer keys):
  Gemini `:generateContent` with `responseModalities:[TEXT,IMAGE]` + inlineData parse; OpenAI / OpenRouter /
  custom OpenAI-compatible `/v1/images/generations|edits` (b64_json; OpenRouter edits send a base64 data-URL).
  The model picker keeps the active model when it is an image model, else falls back to
  `gemini-2.5-flash-image` / `gpt-image-1` / `google/gemini-2.5-flash-image`.
- `DirectorServices.generateSceneImages` generates **one AI key visual per scene** after planning and before
  narration, saves it to `filesDir/director_assets/gen/<workflow>/scene_<id>.png` and registers it as a
  full-frame **backdrop** `ResolvedAsset` (explicit placement — a png never falls into the sticker default),
  so the code-animated scene graphics play **on top of the generated image** (`SCENE_OVERLAY_MARKER`).
  Scenes whose image fails keep pure procedural graphics and are reported honestly.
- `huntAssets` skips the picture slot of scenes that already have an AI image (`skipVisualFor`), but still
  hunts their sounds. `refreshAssets` passes the already-visual scene ids.
- Live progress: the image phase streams through the existing `AgentActivityPanel` (new `ActivityKind.IMAGE_GEN`,
  begin/finish with per-scene detail and failure reasons) and the status bar shows
  "Generating AI key visuals i/n"; the status panel got a stage dot (amber = working, red = failed, green = done).
- UI polish: gradient **SAIF AI • AI STUDIO** header banner in the director screen, richer workflow cards
  (gradient icon tile, tag on Zero-to-video) in the AI Studio list.

## C. Telegram "your telegram is empty" — fixed for good
- The channel catalog is **baked into the APK**: `app/src/main/assets/assets_catalog.json` (93 items) ships with
  the app, and CI regenerates it in `build-apk.yml` (`sync-telegram-catalog.yml` still owns updates).
- `DirectorServices.libraryProblem` now has a live `diagnose()`: getMe, getUpdates counts, the 24 h window,
  the 20 MB bot limit, file_id sanity. The ⚙ dialog exposes Sync / Diagnose / Remove webhook buttons.

## D. In-app Flow AI browser + AI asset tools (our own app instead of "Antigravity")
- New `ui/components/FlowAiStudio.kt`:
  - **Flow AI browser**: full-screen persistent WebView — log in to `flow.google.com` / `labs.google/fx` once
    and stay logged in (cookie storage). A `@JavascriptInterface` bridge + injected collector imports every
    generated img/video URL (incl. googleusercontent) into the app; the DownloadListener stores downloads in
    `filesDir/flow_downloads/`. URL bar with Flow / FX / Whisk shortcuts and an imported-count badge.
  - **`FlowAssetStore`**: persists `filesDir/flow_assets.json`; items use `fileId = "local:<abs path>"`.
    `TelegramAssetManager.loadMasterCatalog` merges them and `downloadCandidate` serves `local:` files with no
    network — so **Flow images become first-class video assets** the director's hunter can place.
  - **AI asset tools** dialog: per image — ✂️ crop (`ImageCropEditPanel`), 🪄 AI edit / background-remove /
    restyle via `ImageGen.edit` with the key from Settings, 🗑 delete, plus paste-URL import.
  - Entry points: the ⚙ ("Research, asset sources & Flow AI") dialog in the director screen —
    "🌐 Flow AI browser" and "🖼 AI asset tools (n)".
- Dependency: BOM-managed `androidx.compose.ui:ui-viewinterop` added to the app module (catalog entry in
  `gradle/libs.versions.toml`) for the WebView `AndroidView`.

## Branding
Every remaining "Built on JARVIS Android Assistant by Tech GPTx • TAMCL v1.0" / techgpt.ai credit is removed
from app code, layouts, manifests and READMEs; the app brands itself **SAIF AI** everywhere. (Internal
package/drawable names like `com.jarvis.assistant` were left untouched on purpose — they are invisible and
renaming them without a compiler is riskier than keeping them.)

## Build & secrets
- Build on GitHub: `.github/workflows/build-apk.yml`. Secrets stay in GitHub env: `TG_BOT_TOKEN`, `TG_CHAT_ID`.
- No APK is claimed here — the ZIP is the source update; the workflow produces the APK.
