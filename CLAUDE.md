# CLAUDE.md — SAIF AI Director (read this first in every new chat)

> Owner writes in **Hinglish** (Hindi + English in Roman letters). Reply in Hinglish, short and clear, no jargon dump.
> Owner builds the APK on **GitHub Actions** (no local build). Always deliver **a modified ZIP of the whole project**
> (Claude cannot produce an APK) and **update this file** with what changed (section 9) before zipping.
> No Kotlin compiler / network exists inside Claude's sandbox: code is reviewed by reading. The first CI build is the real
> compile check, so write conservative Kotlin (no experimental APIs, no non-local break/continue in lambdas, explicit types
> where inference is doubtful) and keep unit tests in `app/src/test` updated.

## 1. What the app is
Android app **SAIF AI** (Kotlin, Jetpack Compose, Room, Firebase, Media3). It has a chat assistant, phone agent, app builder
and a **video studio** (timeline editor: lanes CODE, VIDEO, VISUAL, TEXT, VOICE, AUDIO). On top of the editor sits the
**AI Director** ("SAIF AI" badge in the editor toolbar → *AI Studio*; purple **+** → create project → editor):

| Workflow | Input | Output |
|---|---|---|
| ZERO TO VIDEO | topic / script | researched script → scenes → narration → assets → timeline |
| VIDEO TO EDITING | footage | reversible cut plan, captions (never adds narration) |
| VIDEO EDITING WITH AI VOICE | footage | edit + narration written from footage |
| VOICE OVER TO VIDEO | user's audio | scenes built around the untouched voice-over |

## 2. Build, secrets, CI
- `.github/workflows/build-apk.yml`: push to `main` → (1) `python3 scripts/telegram_catalog.py` (never fails the build) →
  (2) `gradle assembleDebug` → (3) unit tests (`continue-on-error`) → uploads `app-debug.apk` + test report.
- **Secrets** `TG_BOT_TOKEN`, `TG_CHAT_ID` (GitHub → Settings → Secrets → Actions). `app/build.gradle.kts` reads them from the
  environment into `BuildConfig.TG_BOT_TOKEN / TG_CHAT_ID`; `TelegramAssetManager.DEFAULT_BOT_TOKEN/DEFAULT_CHANNEL_ID` and
  `WebResearch.telegramToken()` use them. Nothing secret is committed. User keys (LLM, search) live encrypted in app settings.
- **Telegram library baked into the APK**: `scripts/telegram_catalog.py` reads pending channel posts (`getUpdates`, without
  confirming them), merges into `telegram_catalog/catalog.json` (committed) and writes `app/src/main/assets/assets_catalog.json`,
  which `TelegramAssetManager.loadMasterCatalog()` loads. `.github/workflows/sync-telegram-catalog.yml` runs every 6 h and commits
  new posts (Telegram deletes unread bot updates after **24 h** and bots cannot read old channel history — this is why the sync exists).
- Bot must be **admin of the channel**, and **no webhook** may be set. Assets need an `[ASSET_INDEX]…[/ASSET_INDEX]` caption
  (Title, Type, Extension, Category, Subject, Search Keywords, Tags, User_ID) or a descriptive file name (auto-indexed).
- Bot API download limit is 20 MB per file.

## 3. Code map (`app/src/main/java/com/example/`)
- `studio/director/` — the AI Director brain
  - `DirectorController.kt` (state machine, chat, per-scene actions, persistence `DirectorStore`), `DirectorServices.kt` (all real work),
    `DirectorModels.kt`, `DirectorQuestions.kt` (question cards), `DirectorPrompts.kt` (all prompts, `StoryStyles`, `SceneArchetypes`).
  - `DeepResearch.kt` (planned multi-angle research, page reading, fact extraction, `SceneFacts`), `WebResearch.kt` (Tavily/Brave/Wikipedia/News RSS).
  - `ScriptPipeline.kt` (hook options → script → `ScriptCritic` review/auto-fix), `ScenePlanning.kt`, `SceneSpec.kt` (scene graph model, validator,
    `SceneTiming.retime/retimeGrouped`, `VoiceGroups`, JSON), `SceneRenderer.kt` (Canvas renderer, same for preview + export).
  - **Round 9:** `AssetHunter.kt` (`AssetFormats`, `HuntSlot`, `HuntNeed`, `HuntAttempt`, `HuntStep`, `AssetLibrary`, `AssetHunter` prompts/parsers, `HuntSlots`)
    and `AssetPlacement.kt` (`AssetPlacement`, `PlacementKey`, `AssetPlacements` clamp/parse/encode/decode/defaults) — the one-at-a-time hunt and full placement control.
    `SceneFx.kt` renders the 20 advanced primitives.
  - `AssetCurator.kt` (AI asset editor prompt/parser — **kept but no longer called**), `AssetDirector.kt` (old keyword planner/ranker = fallback), `AssetVerifier.kt`
    (`MediaInspector`, `AssetDecision`, `HuntVerdict`, `verifyForHunt`), `TimelineAssembler.kt` (+ `SceneOverlay`, **`PlacementApplier`**), `AudioTools.kt`
    (`DeviceSpeech` TTS, `AudioDurations`), `SfxPlanner.kt`, `BrandKit.kt`, `ProviderRegistry.kt`, `SecretBox.kt`.
- `studio/composer/` — local deterministic designer used only as a **per-scene safety net**: `Beat.kt`, `BeatPlanner.kt`, `SceneComposer.kt`,
  `LayoutEngine.kt`, `SceneLint.kt` (+ **`SceneUniqueness`**, the anti-template gate), `StyleDNA.kt` (random look per video), `CapabilityProfile.kt` (model probe).
- `studio/render/` — `RenderPlan.kt` (z-order), `StudioFrameRenderer.kt` (shared preview/export drawing), **`LayerEffects.kt`** (`destRect`, `driftTransform`,
  `AnimPose` — the placement box, glow/blur/grade and entrance/exit), captions. `studio/export/` — video+audio export.
- `studio/audio/` — `AudioMixer`, `NarrationPolish` (silence trim, fades), `SfxSynth`. `studio/ui/` — Compose screens (`SaifDirectorScreen`, `StudioPreviewPanel`, `StudioSceneLayers`…).
- `ui/components/TelegramAssetManager.kt`, `TelegramCatalogStore.kt` — Telegram library (sync, caption parsing, download, ranking).
- `llm/`, `studio/ai/` — provider-agnostic LLM client (Gemini, OpenAI, Anthropic, Groq, OpenRouter, custom). Other packages (`agent/`, `builder/`, `compiler/`, `data/`) are the rest of the app.
- **Round 9 app-wide backends:** `utils/SaifVoice.kt` (ONE voice for chat + live + video; **SAIF ADVANCE VOICE** is the default),
  `util/SaifMemoryCore.kt` (memory recall tagged per feature, mirrored into the automation engine's Room bank),
  `ui/components/SaifLiveOrb.kt` (3D orb WebView + `SaifLiveConversationPanel` + `SaifLiveOrbBubble`), `ui/components/SaifLiveModeSheet.kt`
  (Normal voice / Share screen chooser + `SaifLiveSession`), `ui/components/SaifLiveSettingsSection.kt` (merged automation settings).
  `SaifHeader.kt` no longer has the red **+** button. The automation module is reached only through `com.jarvis.assistant.SaifAutomation`
  (host-facing API: `LiveMode`, engine flows, `startLive`/`stopLive`/`startScreenShare`/…, and the `hostVoiceName`/`hostMemory`/`hostAutoLanguage` resolvers
  that `SaifApplication` registers).
- Tests: `app/src/test/java/com/example/studio/{director,composer}/` (+ Round 9: `AssetPlacementTest`, `AssetHunterTest`, `PlacementApplierTest`,
  `SceneUniquenessTest`, `ElementClampTest`).

## 4. How ZERO TO VIDEO works now (pipeline)
1. Question cards → brief (`DirectorBrief`). `ensureSeed()` stores a random `styleSeed` → drives palette/backdrop (`StyleDNA`), **story shape** (`StoryStyles`) and **scene layout order** (`SceneArchetypes`) so every video differs.
2. **Research** (`DeepResearch.run`): model plans 5 search angles → search (user's Tavily/Brave key, else keyless Bing RSS + DuckDuckGo + Wikipedia + Google News) → opens top pages and extracts numbered facts (`F1…`). Digest kept in `DirectorServices.lastDigest`.
3. **Script** (`ScriptPipeline`): hook options → script in the chosen story shape, built on the facts → `ScriptCritic` (length, hook, CTA, repetition, unsupported numbers, **too generic**) → user approves.
4. **Scenes** (`planManifest`): `BeatPlanner.outline` splits the script at sentences/clauses; the **model designs every scene** (`sceneBatchV2`, 3 scenes per call, each with its own facts + suggested layout) and its output must pass `SceneValidator`, `SceneLint`, `LayoutEngine` **and `SceneUniqueness`** (retries with the exact problem, including "scene 5 repeats the design of scene 2"). Only a scene that still fails uses `SceneComposer`, which itself rotates 27 `SceneArchetypes`. `lastPlanNote` reports any pair of scenes that still look alike.
5. **Narration** (`synthNarration`): consecutive scenes that belong to one sentence are voiced **as one clip** (`VoiceGroups`, `NarrationClip.groupIds`); `SceneTiming.retimeGrouped` shares the real clip length between them. Voice = the app-wide voice from `utils/SaifVoice` (**SAIF ADVANCE VOICE** by default, the same one the chat and the live conversation use) through device TTS, silence-trimmed (`NarrationPolish`). The script prompt also carries `SaifMemoryCore.promptBlock(VIDEO)`, so a repeat owner is not asked to explain their channel again.
6. **Assets** (`huntAssets`, Round 9 — **one asset at a time**, never one big list): Telegram is synced once, `HuntSlots.build` turns the real manifest into an ordered list of places to fill (each scene's picture first, then its sound cues), and for each slot the loop runs up to 3 tries: ask for ONE need (**2–3 keywords + one file format**) → `AssetLibrary.search` (strict format first, then relaxed) → `TelegramAssetManager.downloadCandidate` → `AssetVerifier.verifyForHunt` (vision check *in the situation it will be cut into*: narration + cue time + `visualObjective`) → on reject take `betterKeywords` and search again → on accept place it and move to the next slot. A need the model says is unnecessary becomes an upload request on Telegram (`AssetRequester`, once per day per need). No model → `AssetHunter.seedNeed` (keywords from the narration). Every decision is logged in `lastHuntLog` and shown in the chat report. `curateAssets` still exists but is no longer called.
7. **Timeline** (`TimelineAssembler.assemble`): CODE scene (+ overlay mode when it has a picture/clip), VOICE (spans a whole sentence group), TEXT captions, VISUAL/VIDEO assets, AUDIO SFX. Each asset arrives with an `AssetPlacement` (`PlacementApplier.of`) that becomes the project's own `ItemTransform` / `ItemEffects` / `TransformKeyframe` / `ItemAnimation`, so **position, size, keyframes, glow, blend, grade, corner, chroma key, entrance and exit are all editable afterwards** in the Tune inspector — nothing is locked. Video is always forced to a full-frame backdrop. One undo step on "Add to timeline".

## 5. Rendering rules that matter
- Scene graph = JSON of whitelisted primitives (`SUPPORTED_ELEMENT_TYPES`), stored in `CodeSceneItem.htmlCode`. No generated code is executed.
- Z-order (`RenderPlan`): CODE 10, VIDEO 20, VISUAL 30, sticker 37/38, overlay 35, TEXT 40, audio 50. A scene with a library asset is saved with `animationType = SCENE_OVERLAY_MARKER`, a transparent backdrop and a dark scrim, and is drawn above the media. Preview draws images first for overlay scenes; b-roll/pictures (`aiWhy` contains `:asset:`) are centre-cropped, user footage keeps its aspect.
- **Placement contract (Round 9):** geometry, glow, drift, shadow, blur, vignette, corner and chroma key travel in an `@pl=` tag inside `aiWhy` (`@pl=x,y,WxH,fit,role[,glow][,rot][,op][,drift][,shd][,blr][,vig][,crn][,chromaRRGGBB:tol]`); grade and blend live in `ItemEffects` / `ItemTransform`; motion lives in `TransformKeyframe`. The renderer owns the box (`LayerEffects.destRect`), so `ItemTransform.x/y` stay 0 and act only as extra nudges. `TransformKeyframe.timeUs` is **absolute** µs while `PlacementKey.tMs` is **local** to the clip — `PlacementApplier.keyframes` adds `startUs`. The tag is data: `AssetPlacements.f2` is pinned to `Locale.US` and `decodeTag` needs exactly the 5 fields `encodeTag` writes.
- Placement easings must use `LayerMath.evaluateEasing`'s vocabulary (linear, easeIn/Out(+quad), easeInOut, the cubic family, easeOutBack, elastic, bounce, bezierCubic). Unknown names silently fall back to linear, and `SceneSpec.SUPPORTED_EASINGS` (`overshoot`, `expoOut`, `spring`) is a **different** list used only by the scene renderer.
- `ItemEffects` / `ItemTransform.blendMode` are consumed by `StudioFrameRenderer.drawMedia` and `SceneRenderer.blendXfer`; other bitmap paths still ignore them. Glow/blur/shadow are downscale→upscale bilinear copies, because `setShadowLayer` / `BlurMaskFilter` do nothing to `drawBitmap` on a hardware canvas, `RenderEffect` needs API 31 (minSdk is 26), and `Paint.setBlendMode` needs API 29 (so `PorterDuffXfermode` is used).
- Voice clip `aiWhy` carries `grp=s1+s2` for grouped clips; SFX `aiWhy` carries `@offsetMs`. `retimeAfterChange` relies on both.

## 6. Known limitations (be honest about them)
- Not compiled outside CI. Highest-risk code: export audio mux, TTS to file, and the new Round-9 Kotlin (`AssetHunter`, `AssetPlacement`, `SceneFx`, `huntAssets`, `SaifLiveOrb`, `SaifMemoryCore`) — see section 9.
- **The live conversation still speaks only the Gemini Live WebSocket protocol** (`GeminiLiveWebSocket`, gated by `SaifApiConfig.isGemini`). The orb, screen share, mic and settings all work with any provider, but with a non-Gemini provider there is no turn-based STT → `AiClient` → TTS fallback yet, so the voice session will not connect. This is the biggest remaining piece of the automation merge.
- Narration is Android on-device TTS (needs a voice for the language), no cloud TTS/cloning. Quality depends on the device engine.
- Telegram: bots cannot read old history; library = posts seen by the bot within 24 h of upload (sync workflow every 6 h) + posts already in the app.
- Vision verification only on multimodal providers; otherwise technical checks only. Licences are "unverified".
- Per-scene edit actions (re-voice / strengthen hook) are blocked for scenes that are part of a multi-scene sentence (they say so).
- Keyless web search (Bing RSS / DuckDuckGo) can be rate-limited; then research falls back to Wikipedia + News and says so.
- Legacy HTML/GSAP `CodeSceneItem`s cannot be exported.

## 7. Symptom → cause cheat sheet
| Symptom | Look at |
|---|---|
| Videos look alike | `lastPlanNote` ("N pair(s) of scenes still look similar"); `SceneUniqueness.reason`; model JSON failing validation; `SceneComposer` fallback |
| Assets never appear | `lastHuntLog` lines in the chat report ("Telegram library is empty…", "rejected … — reason"), `telegram_catalog/catalog.json`, `TelegramSyncReport`, bot admin/webhook, caption format |
| Same wrong asset every try | `AssetVerifier.verifyForHunt` verdict, `VisualCheck.betterKeywords`, `AssetHunter.keywordLadder` rungs, `AssetLibrary.search` `strictFormat` |
| Asset hidden behind graphics | `RenderPlan` z-order, `SCENE_OVERLAY_MARKER`, `AssetPlacement.role` (backdrop vs sticker) |
| Asset in the wrong place / wrong size / spins oddly | the `@pl=` tag in `aiWhy`, `AssetPlacements.decodeTag`, `PlacementApplier.transform/keyframes`, `StudioSceneLayers.PlacedImage` |
| Asset does not move at all | `PlacementApplier.keyframes` (needs ≥2 keys inside the scene), absolute `timeUs` vs local `tMs`, easing name not in `LayerMath`'s list |
| Orb opens but no voice | provider is not Gemini (see section 6), `SaifAutomation.liveError`, `JarvisConversationService` connection status |
| Voice changed in Settings but not in the video | `utils/SaifVoice` (single source), `BrandKit.narrationVoice`, `DeviceSpeech` |
| Voice cut / choppy | `VoiceGroups`, `retimeGrouped`, `NarrationPolish`, SFX ducking in `TimelineAssembler` |
| "Scene planning failed … 404" | wrong Gemini model name in settings; change model |
| Research shallow | `DeepResearch` note in chat; add a Tavily/Brave key for better search |

## 8. Backlog (not done yet)
- **Provider-agnostic live engine**: a turn-based STT → `AiClient` → TTS path so the orb works without Gemini Live (see section 6).
- Wire `SaifMemoryCore` into the app builder and the phone agent too (chat, live and video are done), and surface `perFeatureCounts()` in the AI MEMORY dialog.
- Cloud TTS option for smoother voice; per-word timing alignment between narration and captions.
- Let the Director ask the user for a Telegram sync from the chat (button) and show catalog size.
- Scene thumbnails in the plan list; A/B regenerate for one scene with the model (currently local variant).
- Background music from library (`bgm`) with auto-ducking (SFX are done, music is not).
- Real instrumented tests for export and TTS on a device.

## 9. Change log (newest first)
**Round 11 (this ZIP)** — (A) build pushed to GitHub and CI (`build-apk.yml`) compiles the APK, (B) SAIF AGENT autopilot inside the app, (C) any-API power: key rotation + model fallback everywhere, (D) Flow-video parity without Antigravity: Flow browser assets → crop/AI-edit/bg-remove → director pipeline.

*A. SAIF Agent autopilot (zero-to-video)*
- `DirectorServices.agentDecide(goal, brief)`: the model decides the ENTIRE brief (language, duration, aspect, style, voice, pace, subtitles, asset policy) + drafts the script; strict-JSON, defensively parsed.
- `DirectorController.agentRun(goal)`: research → script → `produceFromScript` (plan, per-scene AI key visuals, narration, asset hunt) → auto `applyToTimeline()`. Entry: "🤖 SAIF Agent" button in the zero-to-video action bar + `AgentDialog`. Every step streams live via the existing activity panel.

*B. Any-API power / low-quota keys*
- `ProviderEntry` gained `apiKey`; `DirectorServices.providers()` now emits one chain entry PER saved key (plus the provider's `defaultFallbackModels` on the first key), and `llm()` calls through that entry — so quota/auth on one key rotates to the next automatically (`ProviderRegistry.runWithFallback`).
- `LiveTurnLoop` (non-Gemini host loop) rotates through up to 3 saved keys on QUOTA/AUTH instead of failing the turn.
- Image gen (`ImageGen`) already works with gemini/openai/openrouter/custom keys; Flow-imported `local:` assets join the Telegram library (`FlowAssetStore` + `downloadCandidate` special-case).

*C. CI* — branch `arena/...` pushes trigger `build-apk.yml` (push on `arena/**`); APK artifact `app-debug`, compile errors are also pushed to the `ci-errors` branch. Round 10 commit's run was triggered automatically on push.

**Round 10 (this ZIP)** — four asks: (A) purple mic Normal/Screen-share works with ANY provider key, (B) zero-to-video = unique scenes + one AI image per scene + live progress, (C) Telegram "empty" bug fixed for good, (D) in-app Flow AI browser + AI asset tools. All SAIF AI automation / Tech GPTx / TAMCL credits removed; everything is branded SAIF AI.

*A. Any-key live modes*
- `SttRouter` now pre-initializes Vosk and waits for whichever STT is configured; `LiveTurnLoop` runs a provider-agnostic turn-based STT → `AiClient` → TTS loop for non-Gemini providers (Gemini Live WebSocket path unchanged); accessibility-text vision fallback for screen share; `JarvisConversationService` host loop no longer hard-requires Gemini.

*B. Zero-to-video, image-first*
- New `llm/ImageGen.kt`: image generation + editing through the user's OWN key — Gemini `:generateContent` (`responseModalities:[TEXT,IMAGE]`, inlineData), OpenAI/OpenRouter/custom `/v1/images/generations|edits` (b64_json; OpenRouter edits send a base64 data-URL). Model picker keeps the active model when it is an image model, else `gemini-2.5-flash-image` / `gpt-image-1` / `google/gemini-2.5-flash-image`.
- `ProviderRegistry` grants `Capability.IMAGE_GENERATION` for gemini/openai/openrouter/custom; new `ActivityKind.IMAGE_GEN`; `Scene.imagePrompt` (parse + encode); `DirectorPrompts.sceneBatchV2` schema gained `"imagePrompt":str` + an IMAGE-FIRST RULE (one detailed English prompt per scene).
- `DirectorServices.planManifest` now targets `clamp(estMs/4000, 6, 24)` beats with `targetBeatMs = clamp(estMs/target, 2200, 9000)`, `maxBeats = 30` (≈15 scenes for 1 minute). New `generateSceneImages(b, m, onStep)` writes `filesDir/director_assets/gen/<wf>/scene_<id>.png` as a full-frame BACKDROP `ResolvedAsset` (explicit placement — never the png→sticker default), with a local `imagePromptFor` fallback built from StyleDNA palettes. `huntAssets` gained `skipVisualFor` so scenes that already have an AI image don't also hunt a picture.
- `DirectorController` calls it in `produceFromScript` and `runVoiceOver` (after planning, before narration; status GENERATING_ANIMATIONS "Generating AI key visuals i/n") and merges `assets = genAssets + ao.assets`; `refreshAssets` skips visual slots that already have one. Overlay needs no assembler change: `hasBackdrop` → SCENE_OVERLAY_MARKER draws the code scene on top of the image.
- Live progress was already streamed through `AgentActivityPanel` + `StatusPanel`; the image phase now appears there too, and the status panel got a stage dot (amber working / red failed / green done).

*C. Telegram "your telegram is empty"*
- The bundled catalog is now baked into the APK (`app/src/main/assets/assets_catalog.json`, 93 items; CI regenerates it in `build-apk.yml`), `DirectorServices.libraryProblem` has a live `diagnose()` (getMe / getUpdates counts / 24h window / 20 MB limit / file_id lengths), and the UI exposes sync + diagnose + remove-webhook buttons.

*D. Flow AI browser + asset tools (replaces the "Antigravity" idea with our own app)*
- New `ui/components/FlowAiStudio.kt`: `FlowAssetStore` (persists `filesDir/flow_assets.json`; `fileId = "local:<abs path>"`), full-screen persistent WebView (login to flow.google.com / labs.google/fx once, stays logged in) with a `@JavascriptInterface` bridge + injected collector that imports every img/video URL into the store, DownloadListener → `flow_downloads/`, URL bar with shortcuts. `TelegramAssetManager.loadMasterCatalog` merges Flow items; `downloadCandidate` serves `local:` files without network. Entry points: ⚙ dialog in `SaifDirectorScreen` ("🌐 Flow AI browser", "🖼 AI asset tools (n)"). Asset tools: crop (`ImageCropEditPanel`), 🪄 AI edit / background-remove via `ImageGen.edit` with `AiProfile.fromSettings`, delete, paste-URL import.
- UI polish: gradient SAIF AI • AI STUDIO header banner, stage dot in the status panel, richer workflow cards in `SaifAiWorkflowList`.
- `app/build.gradle.kts` + `gradle/libs.versions.toml`: added BOM-managed `androidx.compose.ui:ui-viewinterop` for the in-WebView AndroidView.
- Like every round: nothing was compiled here (no JDK in the sandbox); all touched files were bracket-checked and cross-module signatures verified by reading declarations. **GitHub Actions is the first real compile.**
**Round 9 (this ZIP)** — three asks: (A) assets fetched **one at a time** and placed with full editor control, (B) scenes that never repeat a template, (C) all of SAIF AI AUTOMATION absorbed into the main app and the red **+** button deleted.

*A. Sequential asset hunt + placement (ZERO TO VIDEO)*
- New `studio/director/AssetHunter.kt`: `AssetFormats` (png/jpg/mp4/mp3… → kind, strict vs relaxed format matching), `HuntSlot` (one place on the timeline), `HuntNeed` (2–3 keywords + one file format, never a list), `AssetLibrary` (tenant-filtered, keyword-ranked search), `AssetHunter` (prompts + tolerant parsers + `keywordLadder` retry rungs + `seedNeed` fallback when no model can answer), `HuntSlots.build/unfilled`.
- New `studio/director/AssetPlacement.kt`: `AssetPlacement`/`PlacementKey` — normalized position, size, fit, role, rotation, opacity, blend, glow+colour, shadow, blur, brightness/contrast/saturation/hue, vignette, chroma key, corner, keyframes, entrance/exit animation, drift. `AssetPlacements.clamp/parse/toJson/encodeTag/decodeTag/defaultFor/referenceSize`.
- `DirectorServices.huntAssets` replaces `curateAssets` at all three call sites: sync Telegram **once**, then per slot loop up to `AssetHunter.MAX_TRIES` (3) — ask for ONE need → search with only its 2–3 keywords in the exact format → download → `AssetVerifier.verifyForHunt` (vision check in the situation it will be cut into) → on reject adopt `betterKeywords` and retry → on accept place it and move to the next slot. `lastHuntLog` reports every decision; `lastPlanNote` reports scene-similarity collisions. `curateAssets` is kept but no longer called.
- `AssetVerifier`: `MediaInspector.hasAlpha`, `previewJpegBase64`, `VisualCheck.betterKeywords`, `HuntVerdict`, `verifyForHunt`. `DirectorPrompts.assetMatchAt` returns `{match, score, issues, betterKeywords}`.
- `TimelineAssembler.PlacementApplier` converts a placement into the project's own `ItemTransform`/`ItemEffects`/`TransformKeyframe`/`ItemAnimation`; geometry travels in an `@pl=` tag inside `aiWhy` so the timeline schema is unchanged and the editor's Tune inspector can still change everything.
- Rendering: new `studio/render/LayerEffects.kt` (`destRect`, `driftTransform`, `AnimPose`, entrance/exit), `StudioFrameRenderer.drawMedia` (legacy + placement paths), `RenderPlan` sticker z 38/37 + `isSticker()`, `StudioSceneLayers.PlacedImage` + `gradeFilter`.
- **Bugs found and fixed while writing tests:** `decodeTag` required 6 fields but `encodeTag` writes 5, so **every** placement was thrown away on project reload (guard 6→5); `encodeTag` used locale-dependent `%.2f`, so a comma-decimal locale corrupted the tag (now `Locale.US`); the preview added `p.rotation + t.rotation` while the export applied it once, so placed assets spun twice as far (preview now uses `t.rotation`, and the glow copy rotates with it).

*B. Unique scenes (no templates)*
- `SceneSpec.kt`: 20 new primitives (wave, radar, steps, table, quote, ticker, arcdial, spotlight, halftone, scanline, confetti, beam, ribbon, compare, heat, spiral, orbit, stack, badge, barcode — 40 total), 8 new text fx (scramble, slide, scale, blur, neon, shadow3d, karaoke, bounce — 17 total), 14 new per-element look fields (glow, radius, shadow, blend, gradient, skew, letterSpacing, weight, align, dash, outline, path), whitelists `SUPPORTED_ELEMENT_BLENDS/_PATHS/_WEIGHTS/_ALIGNS`, and `Element.clamped()` applied at parse.
- New `studio/director/SceneFx.kt`: native renderer for the 20 advanced primitives (glow/shadow/gradient stamps, motion paths, weight→typeface).
- `SceneRenderer`: `blendXfer`, blend + path offset + `SceneFx` delegation, text align/weight/letterSpacing/skew, rect radius/gradient/outline, text fx scramble/karaoke/neon/shadow3d, and the seven motion/focus fx implemented (they were whitelisted but silently did nothing).
- `SceneLint`: `structure` (counts#palette#camera), `family` (counts), `tooSimilar` (catches "same shapes shifted a little" and "same shapes recoloured", which the old fingerprint check missed); new `SceneUniqueness.repeatsOf/reason/collisions`. `DirectorServices.planManifest` gates every scene through it and retries with the reason; `DirectorPrompts.sceneBatchV2` gained ADVANCED ELEMENTS / TEXT FX / LOOK / ORIGINALITY rules and `SceneArchetypes` grew 9 → 27.

*C. Automation absorbed, red + button deleted*
- `SaifAutomation` is now the host-facing API: `LiveMode.NORMAL/SCREEN_SHARE`, flows (`isLiveRunning`, `orbState`, `connectionStatus`, `audioLevel`, `audioFrequency`, `userTranscript`, `assistantTranscript`, `isScreenSharing`, `liveError`, `liveMode`) and `startLive/stopLive/setMicMuted/interrupt/sendText/startScreenShare/stopScreenShare/wantsScreenShare/clearLiveError/isServiceRunning`. It never opens its own screen.
- New UI: `SaifLiveOrb.kt` (the real `orb.html` three.js orb in a WebView + `SaifLiveConversationPanel` + draggable `SaifLiveOrbBubble` replacing the old sparkle `FloatingVoiceOverlay`), `SaifLiveModeSheet.kt` (Normal voice / Share screen chooser behind the purple mic), `SaifLiveSettingsSection.kt` (merged settings).
- **Red + button removed** from `SaifHeader.kt`; `onAutomationClick` is kept only so old callers compile and now opens the live mode sheet.
- **SAIF ADVANCE VOICE**: new `utils/SaifVoice.kt` is the ONE voice source for chat, live baatcheet and video narration; added as the first `VoiceProfile` and the new default; `BrandKit.narrationVoice`/`DeviceSpeech`/`TTSManager`/`SaifDirectorScreen` all read it; the live engine asks the provider for the mapped name (`Puck`) via `SaifAutomation.hostVoiceName()`.
- **Memory recall is app-wide**: new `util/SaifMemoryCore.kt` tags every fact with the feature that learned it (chat/live/video/builder/agent), mirrors into the engine's Room bank, and answers `promptBlock(feature)`. Wired into `ChatViewModel`, `AIApiUtility`, `LiveVoiceSessionManager`, `ScriptPipeline` (video) and `JarvisConversationService` (live). `DirectorController.answer` remembers channel/niche/audience/style/language.
- **AUTO LANGUAGE** moved into the main Settings panel (`SaifMemoryCore.autoLanguage`), together with the engine's persona, silent execution, 3D-orb look, floating overlay, system permissions and owner voice-print lock. Nothing existing was removed and nothing was duplicated: API key/model stays in AI PROVIDER & MODELS, memories in AI MEMORY, export/clear in DATA & PRIVACY, theme in APPEARANCE, voice in VOICE & TTS.
- Branding: the feature is presented as **SAIF AI • LIVE AUTOMATION ENGINE** in the app, the automation footer, `automation/README.md` and the manifest. Round 10 removed every remaining "Built on JARVIS Android Assistant by Tech GPTx • TAMCL v1.0" / techgpt.ai credit — nothing in the app attributes third-party automation any more.
- Tests added: `AssetPlacementTest`, `AssetHunterTest`, `PlacementApplierTest`, `SceneUniquenessTest`, `ElementClampTest`.

*Still open (not done in this round)*
- The live engine still speaks the **Gemini Live WebSocket** protocol and is gated by `SaifApiConfig.isGemini`; with another provider the orb connects but there is no turn-based STT → `AiClient` → TTS fallback yet.
- Nothing here was compiled: the sandbox has no JDK/Kotlin and GitHub release assets redirect to a host that is not allowlisted. Verification was read-only (bracket balance on all 39 touched `.kt` files, every cross-module signature checked against its declaration). **GitHub Actions is the first real compile.**

**Round 8** — fixes for "same template every time", "assets never used", "voice cuts", "weak script":
- Scene design: the model now designs **every** scene (previously only for models guessed "strong"; `gemini-flash-latest` fell into the local template composer). New `sceneBatchV2` prompt with per-scene research facts, shuffled layout archetypes, quality bar; layout gate inside the retry loop.
- Research: `DeepResearch` (planned angles, keyless search, page reading, scored fact extraction); `SceneFacts` gives each scene its facts. Script prompt: story shape per video, originality rules, `ScriptCritic` "too generic" check.
- Assets: **AI asset editor** (`AssetCurator` + `DirectorServices.curateAssets`) matches the real library to scenes, places timed SFX, lists missing assets and requests them on Telegram; **bug fixed**: library b-roll/pictures were drawn above (export) or below (preview) an opaque scene, so they never showed together → overlay mode + z-order + centre-crop.
- Telegram at build time: `scripts/telegram_catalog.py`, catalog snapshot step in `build-apk.yml`, `sync-telegram-catalog.yml`, loader reads `message_id` / `file_size`.
- Voice: sentence-level synthesis (`VoiceGroups`, `groupIds`, `retimeGrouped`), no more half-sentence clips with a full-stop tone; quieter SFX (`SceneOverlay.sfxVolume`, ducking 0.55).
- Tests: `Round2Test.kt`. Docs: this file.
**Earlier rounds** (see `docs/`): Phase 1-6 director build (4 workflows, scene renderer, Telegram manager, export audio), Phase 7-8 UI/UX overhaul + extra features, FIX_REPORT (CI compile fixes, Telegram diagnostics, Kabir voice, StyleDNA, SceneLint/LayoutEngine, capability probe).

## 10. Rules for Claude in the next chat
1. Read this file, `docs/*.md`, `SAIF_AI_DIRECTOR.md` before changing anything; keep existing behaviour unless the owner asks.
2. Never invent success: say what was verified (read, unit-tested by reasoning) vs. what only CI/device can verify.
3. Keep every change small and compile-safe; update tests; update section 9; deliver the full ZIP.
4. Never put secrets in the repo. Never claim you can build an APK.
