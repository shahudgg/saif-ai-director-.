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
- **Round 10 (live on any API):** `SaifHostLiveEngine.kt` (the provider-agnostic turn-based engine, registered as `SaifAutomation.hostLiveEngine`),
  `util/SaifLiveScreen.kt` (buffers the newest screen frame so any vision provider can see the screen). `SaifAutomation` now **owns** its ten live flows
  (`EngineKind`, `activeEngine`, `HostLiveEngine`/`HostLiveSink`, `ensureGeminiBridge`, `isScreenSharePending`) instead of aliasing `JarvisConversationService`'s.
  `LiveVoiceSessionManager` gained `handleUtterance`/`submitText`/`isMicMuted`/`userCaption`/`aiCaption`/`turnError`. `LiveVoiceManager.startLiveVoice` is public.
  `LiveVoiceService`'s floating window is now `SaifLiveOrbBubble` (+ optional `onDrag`); `FloatingVoiceOverlay.kt` is dead code, kept like `AssetCurator.kt`.
- Tests: `app/src/test/java/com/example/studio/{director,composer}/` (+ Round 9: `AssetPlacementTest`, `AssetHunterTest`, `PlacementApplierTest`,
  `SceneUniquenessTest`, `ElementClampTest`) and `app/src/test/java/com/example/LiveEngineRoutingTest.kt` (Round 10 engine-routing table).

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
- Not compiled outside CI. Highest-risk code: export audio mux, TTS to file, and the new Round-9/10 Kotlin (`AssetHunter`, `AssetPlacement`, `SceneFx`, `huntAssets`, `SaifLiveOrb`, `SaifMemoryCore`, `SaifHostLiveEngine`, the `processAudioTurn` → `handleUtterance` split, `SaifAutomation`'s owned flows + bridge) — see section 9.
- **Live baatcheet works on every provider since Round 10, but the two engines are not equivalent.** The Gemini Live socket is full-duplex (barge-in while it speaks, continuous screen streaming, server-side VAD); every other provider runs SAIF AI's turn-based engine (VAD → STT → LLM → on-device TTS), which is half-duplex and sends **one** screen frame per turn instead of a video stream.
- Screen share on a non-Gemini provider only "sees" anything if the configured provider **and model** accept images (`base64Images`); a text-only model answers as if no screen was shared.
- Speech-to-text needs at least one Groq, OpenAI or Gemini key (`SttRouter.resolveSttCandidates`). A provider set with none of those three cannot transcribe; `LiveVoiceSessionManager.turnError` now says so instead of sitting silently on LISTENING.
- The Gemini-only live settings (Conversation Language / Personality from `GeminiConstants`) have no effect on the turn-based engine. The **Live Engine** card in Settings says which engine is active.
- `SaifAutomation.audioFrequency` for the turn-based engine is a proportional split derived from mic energy, not a real FFT, so the orb's bass/high rings are approximate.
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
| Orb opens but no voice | `SaifAutomation.activeEngine` (which engine ran), `LiveVoiceSessionManager.turnError` (no STT route), `SttRouter.resolveSttCandidates`, `SaifAutomation.liveError`, `JarvisConversationService` connection status |
| Nothing heard on a non-Gemini provider | `SaifHostLiveEngine.start` return value (mic permission), `LiveVoiceManager.isLiveModeActive`, `LiveVoiceService` notification, `TTSManager` voice availability |
| Orb shows no captions | `SaifAutomation.userTranscript`/`assistantTranscript` ← `userCaption`/`aiCaption` ← `handleUtterance`; the Gemini bridge only forwards while `activeEngine == GEMINI_LIVE` |
| Screen share does nothing (non-Gemini) | `SaifLiveScreen.isSharing`/`hasFrame`, `SaifHostLiveEngine.startScreenShare`, and whether the provider+model accepts `base64Images` |
| Screen share stays pending (Gemini) | `SaifAutomation.isScreenSharePending`, `connectionStatus` never reaching `LIVE`, `flushPendingScreenShare`, the 20 s timeout error |
| Orb frozen or state jumps between engines | `SaifAutomation.activeEngine`, the `ensureGeminiBridge` guard, `SaifHostLiveEngine.engineJob` |
| Floating bubble missing while the app is backgrounded | `SYSTEM_ALERT_WINDOW` grant, `LiveVoiceService.showOverlay`, `SaifLiveOrbBubble` (+ `onDrag`) |
| Voice changed in Settings but not in the video | `utils/SaifVoice` (single source), `BrandKit.narrationVoice`, `DeviceSpeech` |
| Voice cut / choppy | `VoiceGroups`, `retimeGrouped`, `NarrationPolish`, SFX ducking in `TimelineAssembler` |
| "Scene planning failed … 404" | wrong Gemini model name in settings; change model |
| Research shallow | `DeepResearch` note in chat; add a Tavily/Brave key for better search |

## 8. Backlog (not done yet)
- Feed a real FFT (`com.jarvis.assistant.audio.AudioUtils`) into `SaifAutomation.audioFrequency` for the turn-based engine instead of the derived bass/mid/high split.
- Streaming STT for the turn-based engine (true barge-in mid-sentence, like Gemini Live) and multi-frame screen context per turn.
- Delete the dead `FloatingVoiceOverlay.kt` once the orb bubble has been confirmed on a device.
- Show `SaifAutomation.activeEngine` in the live panel header so the user can see which engine answered.
- Wire `SaifMemoryCore` into the app builder and the phone agent too (chat, live and video are done), and surface `perFeatureCounts()` in the AI MEMORY dialog.
- Cloud TTS option for smoother voice; per-word timing alignment between narration and captions.
- Let the Director ask the user for a Telegram sync from the chat (button) and show catalog size.
- Scene thumbnails in the plan list; A/B regenerate for one scene with the model (currently local variant).
- Background music from library (`bgm`) with auto-ducking (SFX are done, music is not).
- Real instrumented tests for export and TTS on a device.

## 9. Change log (newest first)
**Round 10 (this ZIP)** — one ask: *"mere app ka har feature kisi bhi API par kaam kare"*. The live conversation no longer needs Gemini Live. No new engine was required — the app already contained a complete provider-agnostic turn-based live stack, it was simply unreachable from the automation feature. **Round 10 is a bridge, not a rewrite.**

*The routing rule (one place: `SaifAutomation.startLive`)*
- No API key → no engine; toast + the Settings panel opens (unchanged).
- `SaifApiConfig.isGemini` (a Gemini key — or a blank provider, which has always defaulted to Gemini) → the full-duplex **Gemini Live WebSocket** (`JarvisConversationService`), exactly as before.
- Any other provider (OpenAI, Anthropic, Groq, OpenRouter, Mistral, DeepSeek, custom…) → **`SaifHostLiveEngine`**: VAD (`VadGate` + `AudioPipeline`) → STT via `SttRouter` (Groq Whisper / OpenAI Whisper / Gemini, falling back across every saved key + the admin key + `GeminiKeyPool`) → the reply via `AIApiUtility.executeWithFallback` (the same gateway the chat uses) → the voice via on-device `TTSManager` (no API needed).

*`SaifAutomation.kt` — the live flows are now owned, not aliases*
- The ten live flows used to be **direct aliases** of `JarvisConversationService`'s companion flows, so they could only ever show the Gemini engine. They are now `MutableStateFlow`s owned by `SaifAutomation`.
- `EngineKind { NONE, GEMINI_LIVE, HOST_TURN_BASED }` + `activeEngine`/`isHostEngine`; `ensureGeminiBridge()` copies the Gemini service's flows into the owned ones **only while that engine is active**, so two engines can never fight over the orb.
- `HostLiveEngine`/`HostLiveSink` interfaces + `var hostLiveEngine`. The automation module cannot see `com.example.*` (the dependency is one-way, app→automation), so the engine is injected — the same pattern as `hostVoiceResolver`/`hostMemoryResolver`/`hostAutoLanguageResolver`. `SaifApplication` registers `SaifHostLiveEngine`.
- `stopLive`/`setMicMuted`/`interrupt`/`sendText`/`startScreenShare`/`stopScreenShare`/`clearLiveError`/`isServiceRunning` all route to the active engine. The string-based `stateFromKey` is gone — the sink passes a real `ConversationState`, so the two sides cannot drift.
- A turn-based engine only reports mic energy, so `hostSink.level()` also derives a proportional bass/mid/high split for the orb's audio-reactive rings (the Gemini bridge pushes the real FFT and wins while the socket engine is active).

*New `app/…/SaifHostLiveEngine.kt`*
- Reuses the **existing** `LiveVoiceService` foreground service (MICROPHONE type) through `LiveVoiceManager.startLiveVoice` — now public — so there is still exactly one mic owner and one notification.
- Collects `engineFlow` and forwards `sessionState`/`amplitude`/`userCaption`/`aiCaption`/`turnError` into the sink; maps `VoiceSessionState{IDLE,LISTENING,PROCESSING,SPEAKING}` → `ConversationState{IDLE,LISTENING,THINKING,SPEAKING}`.
- Checks the mic permission up front (a clear Hinglish error instead of a deaf orb); when `isLiveModeActive` goes false (closed from the notification/overlay) the flows reset.
- `interrupt()` → `stopSpeaking(causedByUserInterruption = true)`; `sendText()` → the new `submitText`.

*Screen share on any provider — new `app/…/util/SaifLiveScreen.kt`*
- `ScreenCaptureManager` was always a standalone singleton; only `JarvisConversationService.startScreenCapture` was Gemini-gated (it needs `activeInstance` + a ready WebSocket). The host engine calls the singleton directly.
- Frames are **not** streamed to a socket: the newest JPEG is buffered in `SaifLiveScreen`, and `LiveVoiceSessionManager` now passes `base64Images = SaifLiveScreen.frameForPrompt()` (it was `emptyList()`) to `executeWithFallback`, which already routes images to whichever provider is configured. Stale frames (>15 s) are dropped, with an on-demand `captureSnapshotJpeg` fallback.

*`LiveVoiceSessionManager.kt` — additions only, the existing mic path is unchanged*
- `processAudioTurn` was split: everything after transcription moved into `handleUtterance(text)`. The extracted body referenced **none** of the audio locals (checked before cutting), and its two `return@`s are lambda-local, so behaviour is identical. Speech and typed text now share one brain.
- New: `submitText(rawText)`, `isMicMuted` (frames dropped before VAD), `userCaption`/`aiCaption` (the orb's two caption lines — `liveSubtitle` mixes status tokens with sentences), `turnError` (surfaces a missing STT route).

*The floating window is finally replaced (requirement C's last piece)*
- `LiveVoiceService.showOverlay()` now builds **`SaifLiveOrbBubble`** instead of `FloatingVoiceOverlay` (which had no call sites left). `SaifLiveOrbBubble` gained an optional `onDrag` — a press that moves >10 px is a drag, otherwise it is a tap-to-open — so the overlay stays draggable. The bubble reads `SaifAutomation`'s flows, so it looks identical on both engines.
- `JarvisConversationService.beginActiveConversation` no longer refuses a non-Gemini provider with "GEMINI KEY REQUIRED" when a host engine is registered: it stops itself and delegates to `SaifAutomation.startLive`.

*Bug fixed on the Gemini path as well*
- Picking **Share screen** before the session was up used to fail silently: `startScreenCapture` rejects a projection unless the WebSocket reports `LIVE`, and it *stops* the projection it rejects. `startScreenShare` now queues the projection (`isScreenSharePending`) and the `connectionStatus` bridge hands it over the moment the socket is LIVE, with a 20 s timeout that stops the projection and reports why. The toast reads "Live session ready hote hi screen share apne aap shuru ho jayega."

*Settings + tests*
- `SaifLiveSettingsSection` gained a **Live Engine** card naming the engine the current provider will use, and stating that the language/personality pickers below belong to the Gemini Live socket.
- New `LiveEngineRoutingTest.kt` (plain JVM, no JSON, no Android): the routing table across 12+ providers, `isGemini` spellings, the blank-provider default, no-key → no engine, and the idle-state contract of the owned flows.

*Not verified here:* nothing was compiled (no JDK/Kotlin in the sandbox, and downloading one is blocked). GitHub Actions is the first real compile. The routing logic and every referenced symbol were checked by grep; runtime behaviour (mic, TTS, projection, overlay) can only be confirmed on a device.

**Round 9** — three asks: (A) assets fetched **one at a time** and placed with full editor control, (B) scenes that never repeat a template, (C) all of SAIF AI AUTOMATION absorbed into the main app and the red **+** button deleted.

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
- Credit: the feature is presented as **SAIF AI • LIVE AUTOMATION ENGINE** in the app and in the automation footer, with the required "Built on JARVIS Android Assistant by Tech GPTx • TAMCL v1.0" attribution kept in the layout, `automation/README.md` and the manifest.
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
