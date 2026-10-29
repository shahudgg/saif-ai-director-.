# SAIF AI Director — implementation notes

> Newest full briefing for new chats: **CLAUDE.md** (project root). Latest changes: `docs/ROUND_8_REPORT.md`.

**Build status:** after the first CI failure the project was re-checked with a real Kotlin 2.2.10 compiler against the Android 36 SDK jar.
Fixed: `ExportAudio.kt` (`MediaCodecInfo.CodecProfileLevel.AACObjectLC`), `StudioAiProModal.kt` (missing `setValue` import) and
`app/build.gradle.kts` (missing git-ignored `debug.keystore` no longer fails `validateSigningDebug`; falls back to Android's default debug key).
Compose/Firebase/Room libraries were not available offline, so the Compose UI files were verified by reviewing their calls into project code, not by a full compile.
The first real Gradle build on CI is still the final verification.

## How to use
1. Home → purple **+** → create project → editor opens (unchanged).
2. Tap the existing **SAIF AI** badge in the editor toolbar → sheet now has a **Classic tools | AI Studio** switch.
   *Classic* = the original four tools (Auto-Cut, Auto-Captions, Smart Ducking, Auto-Reframe), untouched.
   *AI Studio* = ZERO TO VIDEO, VIDEO TO EDITING, VIDEO EDITING WITH AI VOICE, VOICE OVER TO VIDEO.
3. Each workflow opens a full-screen AI Director chat (question cards with 4 presets + Custom + Skip + Edit, Pause/Cancel/Retry,
   Confirm & start, Approve script, Add to timeline, Back to timeline). State is saved per project+workflow.
4. Gear icon in the chat: add a **Tavily or Brave** search key (live research) and a **Telegram bot token** (asset library).
   Both are encrypted with the Android Keystore (AES-GCM); if the Keystore is unavailable nothing is saved.

## What each workflow really does
- **Zero to Video**: classify input (topic / full script preserved verbatim / partial / brief) → duration + language cards →
  research via Tavily/Brave (skipped, and said so, if no key) → script (word target from language/pace WPM table) → approval →
  scene manifest JSON (validated + 1 repair round) → per-scene device-TTS audio files, real measured durations → scenes retimed
  from audio → Telegram asset search/download/verify → timeline items (CODE scene, VOICE, TEXT, VISUAL/VIDEO) → "Add to timeline" (one undo step).
- **Video to Editing**: video analysis (metadata, ≤120 sampled frames for scene changes, audio silence map, optional Whisper
  transcript) → edit plan (AI keep-ranges, or deterministic silence trim if no model) → timeline cuts referencing the untouched
  source (`sourceIn/Out`), captions from transcript, overlays. **Never creates narration** (guarded in code + tested).
- **Video Editing with AI Voice**: same + narration written from the analysis/transcript, synthesized per window, placed at the mapped
  timeline time; original audio volume set by the chosen mode (replace / add / mix).
- **Voice Over to Video**: Whisper-style transcription with segment timestamps (chunked) → scene boundaries from the real transcript
  → assets/graphics → uploaded audio placed on the Voice lane unchanged. **Never synthesizes narration.** No transcript ⇒ honest error.

## Motion graphics
The AI emits a JSON scene graph (whitelisted primitives: text, rect, circle, ring, line, arrow, bars, linechart, nodes, grid, particles;
keyframed props; camera moves; transitions). `SceneRenderer` (Android Canvas) draws it deterministically — the *same* function is
used by the live preview and the exporter. No generated code is executed; unsupported primitives fail validation.
Stored inside `CodeSceneItem.htmlCode` with a marker, so the project format/schema is unchanged and old projects still load.

## Files
Added: `studio/director/*` (Models, Questions, ProviderRegistry, SceneSpec, SceneRenderer, Prompts, Services, Controller+Store,
AssetVerifier, AudioTools, VideoAnalyzer, WebResearch, SecretBox, TimelineAssembler), `studio/ui/SaifDirectorScreen.kt`,
`SaifAiWorkflowList.kt`, `StudioSceneLayers.kt`, `studio/export/ExportAudio.kt`, tests in `src/test/.../studio/director/`.
Modified: `StudioAiProModal` (mode switch), `StudioScreen` (opens director), `StudioPreviewPanel` (scene/image/audio layers),
`StudioExportEngine` (image + scene drawing, preflight, audio mix/mux), `AiClient` (vision call, maxTokens),
`TelegramAssetManager` (see below).

## Existing defects found and fixed
- Telegram manager wrote a **fake text file** as a "downloaded" asset on failure → now returns null. Bot token could appear in logs → redacted.
- Export drew every image as a **cyan placeholder box**, ignored code scenes, and wrote **no audio** → real image/scene drawing, AAC mix + mux.
- Preview played only video; scenes/images/narration were not rendered/played → added.

## Known limitations (honest list)
- Not compiled/run (see top). Export audio mux and TTS-to-file paths are the riskiest untested code; test on a real device.
- Narration uses **Android on-device TTS** (`synthesizeToFile`): requires a voice for the language (e.g. Hindi voice data). No cloud TTS and
  no voice cloning. The existing app has no file-producing voice system, so this is the real fallback.
- Transcription needs a Groq or OpenAI key (Whisper `/audio/transcriptions`). Gemini/others are not claimed as transcribers.
- Vision verification of assets only for providers known multimodal (Gemini, OpenAI, Anthropic, OpenRouter); otherwise assets are accepted on
  technical checks only and labelled "visual relevance NOT verified".
- Telegram library needs a bot token + the existing catalog; licences are unknown and recorded as "licence unverified".
- SFX/music cues are listed in the manifest but only added if an audio asset is found; otherwise reported, not faked.
- Legacy HTML/GSAP `CodeSceneItem`s still cannot be exported (export now blocks with a clear message instead of silently skipping).
- Pause takes effect between pipeline stages. No forced alignment (segment/measured-duration timing only).
- Existing `KeyStoreCrypto.encrypt()` returns plaintext (provider API keys are stored unencrypted). Not changed because other code reads them
  without decrypting; recommended follow-up.
- Beat-aware editing, before/after view, render queue, undo for AI edits beyond the single editor undo: not implemented.
