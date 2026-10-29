# SAIF AI Director — Phase 7/8 + bug-fix report

**Read this first: nothing in this ZIP was compiled or executed.** The environment this was written in has no Kotlin/Android
toolchain and no network. Every change was written against the existing code and statically reviewed (bracket balance
compared with the original ZIP, signatures cross-checked). GitHub Actions is the first real compile. Items below are marked
PASS only when something actually proved them; otherwise NOT TESTED / NOT DONE.

## What was wrong (found in your screenshot, video and code)
| Problem | Real cause |
|---|---|
| `Scene planning failed ... gemini-1.5-flash HTTP 404` | Google retired Gemini 1.5 and 2.0. The app's 404 "self-heal" only switched between those retired names. Two settings sanitizers also rewrote models back and forth (2.5 -> 2.0 and 3.x -> 2.5), which is why changing the model "fixed" it for a while. |
| Voice "kat rahi hai" | Every 2–3 s narration clip had 0.55–0.73 s of silence around it (measured on your video: ~23 % of the audio was silence), the voice was the plain device voice, not Kabir, and the export limiter jumped from 1.0 to 0.5 on loud peaks (crackle). Gain changes were also applied per 21 ms block (clicks). |
| Empty blocks, text outside shapes, same elements again and again | Scenes drawn by the AI model were accepted without any design check; rectangles named `rect1` were not treated as cards so text could hang out of them. |
| Telegram assets never used | Scenes built by the local composer never asked for any asset, so the library was never queried. |
| Research skipped | It only ran when a Tavily/Brave key was set. |

## What changed
### Gemini 404
- `llm/GeminiModelCatalog.kt` (new): asks Google's ListModels which models this key can use, ranks them (alias `gemini-flash-latest` first, newer Flash before Flash-Lite before Pro), remembers 404s, never offers 1.0/1.5/2.0.
- `LlmClient`, `LlmGateway`, `ProviderProfile`, `GeminiModels`, `ModelResolver`, `AIApiUtility`, `GeminiService`, `ProviderSettingsManager`, `AdminConfigManager` and the UI defaults now use it; sanitizers only rewrite retired models.
- Caveat: sources disagree about when Gemini 2.5 Flash shuts down, so no model name is trusted blindly; the live list decides.

### Voice
- `DeviceSpeech` now speaks with the **Kabir** profile (same voice and pitch/rate as the chat; Priya selectable in Style & sound), Indian-English locale for Romanised Hinglish, tries several matching voices if one fails, reuses one engine for the whole video.
- `studio/audio/NarrationPolish.kt` (new): trims silence, removes DC offset, fades edges, matches loudness of every clip. Scene padding after narration reduced from 350 ms to 170 ms.
- `AudioMixer.softLimit` is now continuous; `ExportAudio` ramps gain between blocks.

### Scene quality and variety
- `composer/SceneLint.kt` (new): model scenes are rejected (and re-requested with the reason) when they have almost no content, empty/full-screen blocks, unlabeled rectangles, repeated text, or look like the previous scene; rectangles holding text become cards so text is kept inside them.
- `composer/StyleDNA.kt` (new): a random look per video (palette set, backdrop, finishing). Stored in the brief so regenerated scenes keep it. Two videos on one topic no longer look alike.
- New `icon` element (chart, coin, shield, lock, bulb, clock, heart, rocket, check, warning, globe, target, brain, phone, star) drawn natively, picked from the scene's words (English + Hinglish), used as a faint glyph behind headlines.
- Stronger design rules in the scene prompt.

### Research and Telegram
- Keyless research (Wikipedia + Google News RSS) when no search key is set or the key fails.
- `AssetPlanner`: about one scene in three asks the Telegram library for b-roll/meme/picture even when the composer drew the scene. A video request falls back to a matching picture.
- Nothing matched -> `AssetRequester` sends a message to your `TG_CHAT_ID` listing the missing assets (once per need per 24 h). "Check Telegram again" re-reads the channel and rebuilds the timeline.
- Secrets still come only from the build (`TG_BOT_TOKEN`, `TG_CHAT_ID`); none are in this ZIP.

### Phase 7 UI
- Purple "+" now opens `NewProjectSheet`: AI video, Edit my video with AI, AI voice on my video, Voice-over to video, Blank project, "More options" (the old dialog), and Recent projects with thumbnail, duration and last edited. The create-tap guard prevents duplicates. Choosing an AI option opens that workflow straight away.
- Scene cards: Regenerate, Edit text (re-voices only that scene), Replace asset, Change style, Lock, Ask AI; tick several cards and apply one written change or a new style only to them. Live production activity list. Haptics on key actions, 48 dp touch targets.
- Safe-zone overlay now shades the Shorts/Reels UI areas.

### Phase 8
Already present from earlier phases and not re-verified: scene lock, regenerate variant, brand kit, auto-SFX toggle, quality checker (including missing media), export presets, "make hook stronger", chapter markers, project duplicate in `ProjectDataManager`.

## Acceptance criteria
| # | Criterion | Status |
|---|---|---|
| 1 | Varied video, weak and strong API, no overlap | NOT TESTED (design lint + StyleDNA added) |
| 2 | Script quality, natural Hinglish | NOT TESTED (not changed in this round) |
| 3 | Export colours, wrapped captions, A/V sync | NOT TESTED (limiter + gain-ramp fixes, unit-tested logic only in new tests) |
| 4 | Reopen restores timeline and own chat | NOT TESTED (not changed) |
| 5 | Telegram diagnostics real; assets/SFX used; local SFX fill gaps | NOT TESTED against a real channel (asset planning + request added) |
| 6 | Per-scene regenerate/replace without disturbing others | NOT TESTED (implemented) |
| 7 | Phase 7 UI with real actions | PARTIAL: "+" sheet, scene cards, activity list, safe-zone done. NOT DONE: editor workspace redesign (timeline snapping/zoom/sticky playhead, grouped scrollable tool tabs, inspector bottom sheet, chat as bottom sheet), empty/error-card polish everywhere |
| 8 | Builds on GitHub Actions, tests pass | NOT TESTED |

## Tests added (not run here)
`GeminiModelCatalogTest`, `NarrationPolishTest`, `SoftLimiterTest`, `SceneQualityTest` (lint, StyleDNA, layout-clean compose with DNA, icons), `AssetAndResearchTest` (asset planner, Telegram message, Wikipedia/RSS parsing, speech text prep).
The CI test step is `continue-on-error`, so a failing test never blocks the APK.

## Setup notes
- GitHub secrets: `TG_BOT_TOKEN`, `TG_CHAT_ID` (already read by `build-apk.yml`).
- The bot must be a member (for a channel: an admin) of the chat so it can read uploads **and post requests**. Upload assets with a descriptive file name or an `[ASSET_INDEX]` caption. If Telegram has a webhook set, use Settings > Remove webhook.
- Research needs no key now; a Tavily/Brave key is only an upgrade.
- A Gemini key needs nothing special; the app finds a live model itself.

## Known limits
- Kabir is the device's Indian-English male voice: quality depends on the Android TTS voices installed. Install "English (India)" voice data for the smoothest result.
- Telegram bots cannot read old channel history; only uploads made after the bot joined are indexed.
- Remaining Phase 7 items above are not built.
