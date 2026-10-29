# SAIF AI Director — fix report

**IMPORTANT — verification status.** This environment had no Android SDK, no Kotlin compiler and no network.
Nothing here was compiled and no unit test was executed. All code was written against the real source and then
reviewed statically (brace balance of all 53 changed files, symbol/signature cross-checks, and Python mirrors of the
numeric logic: YUV maths, caption chunking/wrapping, beat pacing). Expect a few compile errors on the first
GitHub Actions build; they should be small. Nothing was tested on a device.

## Root causes found in the uploaded material
* **Purple top / green bottom export** (`SAIF_My_Viral_Video…mp4`): the old exporter wrote planar I420 into a semi-planar
  buffer and ignored row/pixel strides; ffprobe also showed `color_space=gbr` (untagged).
* **Dropped frames / drift**: a single 10 ms `dequeueInputBuffer` try that silently skipped frames; EOS attached to the last frame.
* **Same project, same chat**: project id was `hash(name)`; default name was always "My Viral Video"; saved timeline lost
  transform/effects/animations/keyframes; legacy migration always won over saved studio data.
* **Only ~3 scenes per minute**: scenes were grouped to ≥28 words (~10 s); weak models then produced title cards.
* **Telegram "nothing works"**: catalog lived in memory only, `getUpdates` offset was never saved (old posts vanish), seed items had invented file ids.
* **Overlap/ghosting**: scene transitions faded opacity over the previous scene; captions were never chunked/wrapped.

## What changed, per phase
| Phase | Done |
|---|---|
| 1 Identity & persistence | UUID `projectId` (never from name), unique "Untitled N" names, lossless item codec, atomic writes, debounced autosave + flush on pause/back, "Saved/Saving/Save failed" indicator, saved studio project wins over legacy migration, one-time migration of old name-hash data **and chat** to the new id, per-project chat isolation, artifacts (narration/assets) persisted, delete removes timeline + chat. |
| 2 Export | New `YuvConverter` (stride-aware, BT.709 limited), encoder `getInputImage`, VBR + colour tags, hardware-first encoder fallback chain, no dropped frames, explicit EOS, storage pre-check, verification of the saved MP4, aspect-aware sizes, **one** shared renderer (`StudioFrameRenderer`) for preview and export, caption chunking/wrapping/auto-fit (`CaptionRenderer`). |
| 3 Weak/strong API | `CapabilityProbe` (cached per provider+model → LIGHT/STANDARD/STRONG), `BeatPlanner` (≈3 s beats), `LocalBeatAnalyzer`, `SceneComposer` (15 intents × 3 variants, never title-cards), `LayoutEngine` (overflow, collisions, caption zone, contrast, z-order), model graphs are validated/repaired or replaced. |
| 4 Script | `ScriptPipeline`: hook options scored locally, structured script, local review + one targeted rewrite or deterministic fixes, `VoiceoverScript.clean`, voiceover `.txt` export button. |
| 5 Motion/SFX | 15 transitions (non-ghosting), new easings, text fx (typewriter/words/counter/glow/outline), glow/vignette/grain/sweep/flash/glitch/burst/donut, camera moves, `SfxSynth` (9 procedural sounds), `SfxPlanner` (≤4 cues/beat, ducked), library SFX preferred. |
| 6 Telegram | Persistent catalog + offset, caption **and** filename indexing, webhook detection/removal, 20 MB limit message, diagnostics panel, no fake seeds. |
| 7 UI (partial) | Scene cards with rendered thumbnails (Variant / Lock / Change with AI), Saved indicator, export presets + quality list, duplicate-tap guard, project duplicate. |
| 8 Extras | Scene lock, regenerate variant, one-tap *Stronger hook*, brand kit (caption style, accent, auto-SFX toggle), chapter markers from beats, quality checker (missing media, static stretch, overlapping text, silence, black gaps), project duplicate, missing-media detection, export presets. |

## NOT DONE (honestly)
* Phase 7: full "+" sheet redesign, bottom-sheet inspector, track-label/snapping/zoom changes, haptics, AI chat as bottom sheet. (Existing screens were extended, not redesigned.)
* Phase 8: auto beat-aligned cuts for footage; "replace asset" per scene card.
* Telegram assets as scene backgrounds: still only used when a scene requests an asset (STRONG model path). SFX from Telegram are used.
* No device testing of: encoder on real hardware, WAV SFX decode in the audio mixer, TTS timing.

## Tests added (12 files, 69 tests; none executed here)
ProjectIdentity, StudioProjectPersistence (same-name projects, round-trip, chat isolation, legacy migration), YuvConverter (planar, NV12, padded strides, colours),
CaptionLayout/Chunker, SceneComposer (valid graph, clean layout, z-order, variants differ, all aspects), BeatPlanner (pacing), CapabilityProbe,
ScriptPipeline/Critic/Voiceover, SfxSynth, Cue/Transition planner, QualityChecker, TelegramUpdateProcessor.
CI now runs `testDebugUnitTest` after the APK is built (non-blocking) and uploads the report.

## Acceptance criteria
1. weak + strong API variety, ~1 beat / 2–4 s: **NOT TESTED** (logic + tests written)
2. Script with hook/CTA, Hinglish: **NOT TESTED**
3. Export colours/captions/sync: **NOT TESTED** (root cause fixed in code)
4. Reopen/restore/same-name: **NOT TESTED** (tests written)
5. Telegram diagnostics/SFX: **NOT TESTED**
6. Per-scene regenerate/replace: regenerate/variant/lock implemented; *replace asset* **NOT DONE**
7. Phase 7 UI: **PARTIAL**
8. CI build + unit tests: **NOT TESTED**
