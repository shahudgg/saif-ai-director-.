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
  - `AssetCurator.kt` (AI asset editor prompt/parser), `AssetDirector.kt` (old keyword planner/ranker = fallback), `AssetVerifier.kt`, `TimelineAssembler.kt`
    (+ `SceneOverlay`), `AudioTools.kt` (`DeviceSpeech` TTS, `AudioDurations`), `SfxPlanner.kt`, `BrandKit.kt`, `ProviderRegistry.kt`, `SecretBox.kt`.
- `studio/composer/` — local deterministic designer used only as a **per-scene safety net**: `Beat.kt`, `BeatPlanner.kt`, `SceneComposer.kt`,
  `LayoutEngine.kt`, `SceneLint.kt`, `StyleDNA.kt` (random look per video), `CapabilityProfile.kt` (model probe).
- `studio/render/` — `RenderPlan.kt` (z-order), `StudioFrameRenderer.kt` (shared preview/export drawing), captions. `studio/export/` — video+audio export.
- `studio/audio/` — `AudioMixer`, `NarrationPolish` (silence trim, fades), `SfxSynth`. `studio/ui/` — Compose screens (`SaifDirectorScreen`, `StudioPreviewPanel`, `StudioSceneLayers`…).
- `ui/components/TelegramAssetManager.kt`, `TelegramCatalogStore.kt` — Telegram library (sync, caption parsing, download, ranking).
- `llm/`, `studio/ai/` — provider-agnostic LLM client (Gemini, OpenAI, Anthropic, Groq, OpenRouter, custom). Other packages (`agent/`, `builder/`, `compiler/`, `data/`) are the rest of the app.
- Tests: `app/src/test/java/com/example/studio/{director,composer}/`.

## 4. How ZERO TO VIDEO works now (pipeline)
1. Question cards → brief (`DirectorBrief`). `ensureSeed()` stores a random `styleSeed` → drives palette/backdrop (`StyleDNA`), **story shape** (`StoryStyles`) and **scene layout order** (`SceneArchetypes`) so every video differs.
2. **Research** (`DeepResearch.run`): model plans 5 search angles → search (user's Tavily/Brave key, else keyless Bing RSS + DuckDuckGo + Wikipedia + Google News) → opens top pages and extracts numbered facts (`F1…`). Digest kept in `DirectorServices.lastDigest`.
3. **Script** (`ScriptPipeline`): hook options → script in the chosen story shape, built on the facts → `ScriptCritic` (length, hook, CTA, repetition, unsupported numbers, **too generic**) → user approves.
4. **Scenes** (`planManifest`): `BeatPlanner.outline` splits the script at sentences/clauses; the **model designs every scene** (`sceneBatchV2`, 3 scenes per call, each with its own facts + suggested layout) and its output must pass `SceneValidator`, `SceneLint`, `LayoutEngine` (retries with the exact problem). Only a scene that still fails uses `SceneComposer`.
5. **Narration** (`synthNarration`): consecutive scenes that belong to one sentence are voiced **as one clip** (`VoiceGroups`, `NarrationClip.groupIds`); `SceneTiming.retimeGrouped` shares the real clip length between them. Voice = app's "Kabir" profile through device TTS, silence-trimmed (`NarrationPolish`).
6. **Assets** (`curateAssets`): the AI asset editor gets the real library listing + all scenes and returns exact picks (visual per scene, timed SFX) and a `missing` list; picks are downloaded and verified (`AssetVerifier`, vision if available). Missing items are requested on Telegram (`AssetRequester`, once per day per need). No model → old keyword matching.
7. **Timeline** (`TimelineAssembler.assemble`): CODE scene (+ overlay mode when it has a picture/clip), VOICE (spans a whole sentence group), TEXT captions, VISUAL/VIDEO assets, AUDIO SFX. One undo step on "Add to timeline".

## 5. Rendering rules that matter
- Scene graph = JSON of whitelisted primitives (`SUPPORTED_ELEMENT_TYPES`), stored in `CodeSceneItem.htmlCode`. No generated code is executed.
- Z-order (`RenderPlan`): CODE 10, VIDEO 20, VISUAL 30, TEXT 40, audio 50. A scene with a library asset is saved with `animationType = SCENE_OVERLAY_MARKER`, a transparent backdrop and a dark scrim, and is drawn at z 35 (above the media). Preview draws images first for overlay scenes; b-roll/pictures (`aiWhy` contains `:asset:`) are centre-cropped, user footage keeps its aspect.
- Voice clip `aiWhy` carries `grp=s1+s2` for grouped clips; SFX `aiWhy` carries `@offsetMs`. `retimeAfterChange` relies on both.

## 6. Known limitations (be honest about them)
- Not compiled outside CI. Highest-risk code: export audio mux, TTS to file, the new Round-8 Kotlin (see section 9).
- Narration is Android on-device TTS (needs a voice for the language), no cloud TTS/cloning. Quality depends on the device engine.
- Telegram: bots cannot read old history; library = posts seen by the bot within 24 h of upload (sync workflow every 6 h) + posts already in the app.
- Vision verification only on multimodal providers; otherwise technical checks only. Licences are "unverified".
- Per-scene edit actions (re-voice / strengthen hook) are blocked for scenes that are part of a multi-scene sentence (they say so).
- Keyless web search (Bing RSS / DuckDuckGo) can be rate-limited; then research falls back to Wikipedia + News and says so.
- Legacy HTML/GSAP `CodeSceneItem`s cannot be exported.

## 7. Symptom → cause cheat sheet
| Symptom | Look at |
|---|---|
| Videos look alike | `planManifest` log note ("beats used the local designer"); model JSON failing validation; `SceneComposer` fallback |
| Assets never appear | notes in the chat report ("Telegram library is empty…"), `telegram_catalog/catalog.json`, `TelegramSyncReport`, bot admin/webhook, caption format |
| Asset hidden behind graphics | `RenderPlan` z-order, `SCENE_OVERLAY_MARKER` |
| Voice cut / choppy | `VoiceGroups`, `retimeGrouped`, `NarrationPolish`, SFX ducking in `TimelineAssembler` |
| "Scene planning failed … 404" | wrong Gemini model name in settings; change model |
| Research shallow | `DeepResearch` note in chat; add a Tavily/Brave key for better search |

## 8. Backlog (not done yet)
- Cloud TTS option for smoother voice; per-word timing alignment between narration and captions.
- Let the Director ask the user for a Telegram sync from the chat (button) and show catalog size.
- Scene thumbnails in the plan list; A/B regenerate for one scene with the model (currently local variant).
- Background music from library (`bgm`) with auto-ducking (SFX are done, music is not).
- Real instrumented tests for export and TTS on a device.

## 9. Change log (newest first)
**Round 8 (this ZIP)** — fixes for "same template every time", "assets never used", "voice cuts", "weak script":
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
