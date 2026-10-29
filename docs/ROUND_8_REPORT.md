# Round 8 — what was wrong and what changed

## Root causes found in the code
1. **"Every video looks the same / template".** `CapabilityProbe` classifies models by name. `gemini-flash-latest` is not recognised as
   "strong", so the app used the *local* `SceneComposer` (15 intents × 3 variants) for every scene — a template by design. Fixed: the
   model designs every scene whenever it can return JSON; the composer is only a per-scene fallback.
2. **"Assets never appear in the video".** Three separate causes:
   - Matching used keyword overlap between a *Hinglish* narration and *English* asset titles → almost never matched. Fixed by the AI asset editor.
   - Z-order: CODE scenes (opaque background) were drawn **below** b-roll in export and **above** it in preview, so scene graphics and
     assets never showed together. Fixed with overlay scenes (transparent backdrop + scrim, z 35).
   - Export stretched b-roll to the frame. Fixed: centre-crop for library assets, aspect-preserving for user footage.
   - Library empty: Telegram bots cannot read history and drop unread updates after 24 h → build-time snapshot + 6-hourly sync workflow.
3. **"Voice cuts".** Long sentences were split into clause fragments and each fragment was synthesized alone with a closing full stop
   (falling tone + gap). Fixed: one clip per spoken sentence, shared over its scenes; SFX quieter and ducked harder.
4. **"Script is weak".** Research was 3-5 search snippets. Fixed: planned angles, page reading, numbered facts, story shapes, critic check.

## Files
New: `DeepResearch.kt`, `AssetCurator.kt`, `scripts/telegram_catalog.py`, `.github/workflows/sync-telegram-catalog.yml`,
`telegram_catalog/`, `Round2Test.kt`, `CLAUDE.md`. Changed: `DirectorServices`, `DirectorController`, `DirectorPrompts`, `SceneSpec`,
`TimelineAssembler`, `ScriptPipeline`, `RenderPlan`, `StudioFrameRenderer`, `StudioSceneLayers`, `StudioPreviewPanel`,
`TelegramAssetManager` (loader), `build-apk.yml`, `README.md`.

## Not verified here
No compiler was available. Everything was checked by reading and by reasoning through the unit tests in `Round2Test.kt`.
The first GitHub build is the real compile; if it fails, send the error text.
