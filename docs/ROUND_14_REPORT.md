# Round 14 — Flow browser re-built: real login, real downloads, verified pictures, planned transitions

Everything in this round is about ONE complaint: *"jo tumne build kiya tha wo working nahi hai — Flow AI se
image generate kara kar app me use nahi kar pa raha, mere Google account ki profile image asset ban rahi hai."*

## 1. Why the old one failed (root causes, not guesses)

| Symptom | Real cause in the old code |
|---|---|
| Scroll / pinch-zoom dead on the login page | The browser was an `AndroidView(WebView)` inside a full-screen Compose **Dialog**. Dialogs steal touch handling, and Flow/Gemini ship `user-scalable=no`, so pinch-zoom was disabled by the page. |
| "One-time setup" felt like a lock | The banner only disappeared when `FlowAssetStore.count == 0` was false, i.e. after an import — and the only "login" affordance was that banner. Nothing explained that login can be repeated. |
| Google account profile photo became an asset | `COLLECT_JS` imported **every** `<img>` on the page, with no size/context/URL filter. `lh3.googleusercontent.com/a/...` (the avatar) matched. |
| Images never downloaded | Downloads used a bare `OkHttp` request with **no WebView cookies and no Referer**, and Flow result URLs need the signed-in session. `blob:` results were simply skipped. |
| Prompts did not match the video | Prompts were generated from the **topic** (`flowImagePrompts(topic, 15)`), never from the scene narration. |
| Nothing verified, no transitions | Verification was a vision call on the first 5 images only; transitions were whatever the scene planner had guessed before any picture existed. |

## 2. What is new

### 2.1 A real full-screen browser (`flow/web/`)
- **`FlowBrowserActivity`** — a normal Activity (not a dialog). Full-window WebView, so scroll, fling,
  pinch-zoom, keyboard and back all behave like a browser. Opened from: the studio top-bar **🌐 icon**,
  the director's **🌐 Flow browser** button, Settings → Flow, and automatically after prompts are prepared.
- **`FlowWebSession`** — the WebView + session owner. Persistent cookie jar (`CookieManager` +
  third-party cookies), Google account chooser available any time (**Login**), real **Logout**
  (cookies + WebStorage + cache), desktop-mode UA toggle (Google blocks sign-in when the UA contains
  `wv` — the mobile UA here has no `wv`, and desktop mode is one tap away), zoom **A− / A+ / 1x**
  (native `zoomIn/zoomOut` + a CSS zoom the page cannot disable), **scroll up/down** buttons,
  a working **download listener**, and a page-state poller (composer present? login needed?).
- Shortcuts: **Flow · FX · Whisk · ImageFX · Gemini**, address bar + Go, reload.
- **`FlowJs`** — three ES5 blocks injected into the page:
  - `SETUP` — forces a zoomable viewport (`maximum-scale=6, user-scalable=yes`), composer/button finders,
    a React-safe native value setter (textarea / input / contenteditable).
  - `CAPTURE` — **arms a baseline per prompt** and reports only media that appears *after* it; rejects
    avatars/profiles/logos/icons/sprites/GIF favicons/`gstatic` UI art/`/accounts/` URLs/fixed-size avatar
    requests, header/nav elements, and anything below 200 px. Handles `<img>`, `<video>`, `blob:` (read
    inside the page → base64 chunks) and `<canvas>` results.
  - `STATE` — `{url, hasComposer, needsLogin, images}` for the autopilot and the login banner.

### 2.2 The pipeline the owner asked for
1. **Script → scene-by-scene prompts** (`flow/FlowPrompts.kt`): one picture prompt per scene from that
   scene's *narration + visual objective* (plus the model's own `imagePrompt` when present), and a
   cut-out **key visual** every third scene. One model call polishes the whole list as strict JSON keyed
   by `sceneId`; if the model fails, the local builder still writes a usable prompt for every scene.
2. **Open Flow, generate one by one with an 8 s gap** (`flow/FlowAutopilot.kt`): arm → fill the prompt →
   press Generate → wait for the new picture → download → verify → `delay(8 s)` → next. Login can happen
   at any moment (no one-time lock); if the page has no composer the autopilot waits and says so.
3. **Download with the session** (`flow/FlowMedia.kt`): cookies + Referer from the live WebView, magic-byte
   type detection, 12 KB–120 MB guard, SHA-256 de-duplication, real pixel-size check (rejects the profile
   photo even if JS reported it), base64 chunk path for `blob:`/`canvas`.
4. **Verify every picture** (`FlowImageProbe`): decodes the frame and measures **crop loss** for the video
   aspect, **focal point** (so the crop keeps the subject), **brightness/contrast**, **detail/sharpness**
   (soft-at-1080p flag) and **corner uniformity** (can the background be removed cleanly?) — then decides
   fit, glow, grade, chroma-key, Ken-Burns and the entrance animation. Optional AI FIT/NOT-FIT check with
   the owner's own key rides along, never replacing the measurements.
5. **Plan transitions from the real pixels** (`flow/FlowTransitions.kt`): brightness jump → flash, big
   palette jump → whip, matching palettes → dissolve, very detailed frames → zoom-blur, long calm scene
   → slow push, plus rhythm accents. Every name exists in `SUPPORTED_TRANSITIONS`, so the renderer really
   plays it.
6. **Place + build the timeline** (`DirectorController.applyFlowPlanInternal`): scene transitions are
   written into the manifest, each picture becomes a `ResolvedAsset` with a full `AssetPlacement`
   (position, fit, glow, grade, vignette, Ken-Burns keyframes, entrance/exit), the manifest is
   re-assembled (scene code animations stay on top) and one tap applies it to the timeline.

### 2.3 Termux + commands
- `termux/TermuxBridge.kt`: detects Termux, runs a command through the official
  `com.termux.RUN_COMMAND` intent (with an honest error that names `allow-external-apps=true`), runs the
  same command in the app's own `/system/bin/sh` sandbox as a fallback, presets for ffmpeg/batch
  conversion/cover crop, copy-to-clipboard and open-Termux. UI: **⋮ → 🖥 Termux / commands** inside the
  browser. Nothing runs unless the owner taps Run.

### 2.4 Automatic provider switch when a limit is hit
- `llm/BackupProviders.kt` (keys encrypted with `KeyStoreCrypto`) + a Settings section
  **🔁 AUTO-SWITCH ON LIMIT** (provider, key, model, per-row on/off, master switch).
- `LlmGateway.callObserved` now switches **provider + key + model** automatically on daily quota,
  auth failure, retired model or too many per-minute waits (max 3 vendors, bounded), and only then
  returns the original error. `DirectorServices.providers()` adds the backups to the fallback chain, and
  the chain call now uses each entry's **own** provider (the old code always reused the settings provider).
- `SaifApplication` initialises `BackupProviders`.

## 3. Where things live
- `app/src/main/java/com/example/flow/` — models, library/plan store, rules + downloader + verifier,
  prompt planner, autopilot, transitions/placement, bridge bus.
- `app/src/main/java/com/example/flow/web/` — Activity, WebView session, injected JS.
- `app/src/main/java/com/example/termux/TermuxBridge.kt`
- `app/src/main/java/com/example/llm/BackupProviders.kt`, `ui/components/BackupProvidersSection.kt`
- Rewritten facade: `ui/components/FlowAiStudio.kt` (`FlowAssetStore` now delegates to `FlowLibrary`;
  the old dialogs just open the real Activity).
- Tests: `app/src/test/java/com/example/flow/FlowPipelineTest.kt`.

## 4. Honest limits
- Flow is a Google product; its DOM can change. The driver is defensive (many selectors, Enter/form
  fallback, manual "press Generate yourself" fallback that still captures the picture), but a Google
  redesign can always need a selector tweak.
- Google may refuse account sign-in inside a WebView on some accounts/devices; **Desktop mode** and the
  in-app account chooser are provided, and the app says plainly when login did not go through.
- Nothing in this round was compiled in the sandbox (no JDK/Android SDK). GitHub Actions is the first
  real compile; the injected JavaScript was syntax-checked with `node --check`.
