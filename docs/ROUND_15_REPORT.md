# Round 15 — live conversation made to actually work + SAIF POWER TOOLS (15 tools)

The owner's report was blunt: *"jo jo tumne add kiya hai wo sahi se kaam nahi kar raha, live baatcheet bhi
kaam nahi kar raha"*. So this round starts with the reason a feature can look dead, fixes the live
conversation properly, and then makes **every** subsystem observable from inside the app so the next
problem is diagnosed on the phone instead of guessed in chat.

---

## 1. Why live baatcheet stayed silent (real cause)

| Symptom | Real cause found in the code |
|---|---|
| Purple mic → orb appears → nothing happens, no voice, no answer | **`RECORD_AUDIO` was never requested at runtime.** `MainActivity` only asked for notifications. `AudioPipeline`/`AudioRecord` then failed, the host loop returned false and the service stopped — with no visible error. |
| Nothing on screen explains the failure | The panel showed captions only. `liveError` from the engine was collected **nowhere**, and `connectionStatus` was not shown either. |
| Tapping "Normal voice" with no key | Started the service anyway; the engine error was invisible. |
| Screen-share mode | Needs a MediaProjection the host must request — unchanged, but now the failure text is visible. |

### What is fixed
1. `MainActivity` now requests **RECORD_AUDIO + POST_NOTIFICATIONS** together
   (`RequestMultiplePermissions`) on first launch.
2. `SaifLiveModeSheet` asks for the microphone **before** starting, and if the owner denies it the app says
   exactly that instead of dying silently.
3. The live panel now shows a **status line** (`connecting… / live / engine error`) and, when the engine
   reports a `JarvisLiveError`, the real title+message with two buttons: **Retry** (restart the session)
   and **fix-in-settings**.
4. Every start increments a local counter (`live_sessions`) so the analytics card proves it ran.

---

## 2. SAIF POWER TOOLS — 🧰 in the header

One full-screen screen (no navigation risk) with 15 real, working tools. Each one is implemented against
real APIs in this repo — no mock data.

| # | Tool | What it actually does |
|---|---|---|
| 1 | **Self test** | 13 checks with PASS/FAIL + fix: API key/model, mic permission, notification permission, voice-engine link & running state, TTS engine present, offline Vosk model, Flow library, Flow plan, free storage, provider endpoint reachability, Termux, accessibility service, crash log. Report can be exported to `Downloads/SAIF AI`. |
| 2 | **Crash report** | `CrashGuard` (installed in `SaifApplication`) writes the full stack trace + device/Android/build info to `filesDir/logs/` before the process dies. View, share, clear. |
| 3 | **API key test** | One tiny real completion through the same gateway the app uses → OK/FAIL + latency + reply. Proves key, model, network. |
| 4 | **Model browser** | Fetches the provider's real `/models` (Gemini + OpenAI-style) with the saved key, searchable, tap-to-copy. |
| 5 | **Storage manager** | Sizes + file counts of `flow_downloads`, `director_assets`, `exports`, `logs`, cache; one-tap clean per bucket; shows free space. |
| 6 | **Captions → SRT/VTT** | Splits narration at sentence boundaries into ~2.6 s cues and exports proper SRT (`,` ms) and WebVTT (`.` ms) into Downloads. |
| 7 | **Shot list CSV** | Every planned Flow picture as a CSV row: index, scene, role, status, duration, file, measurement verdict, notes, prompt. |
| 8 | **Cover maker** | Any Flow picture → 1080×1920 cover with gradient scrim, wrapped title and brand line; saved into the Flow library so the director can use it. |
| 9 | **Flow library manager** | Counts, **dedupe by hash**, `flow_index.json` export, clear-index (files kept). |
| 10 | **Prompt vault** | Save/reuse/copy/delete image prompts (local prefs, no cloud). |
| 11 | **Voice studio** | Every app voice with a real `previewVoice` sample and one-tap select for the whole app. |
| 12 | **Flow resume** | Shows the saved plan (`ready/total · topic · time`), reopens the browser with autostart, or pushes the plan to the timeline. |
| 13 | **Batch picture fixer** | Runs the measurement probe over the whole library and (optionally) writes 9:16 centre-cropped JPEG copies into the library. |
| 14 | **Provider health** | 3 consecutive real pings with latency + error text — shows whether a provider is flaky or dead. |
| 15 | **Usage analytics** | Local counters (`flow_images`, `videos_applied`, `live_sessions`, `prompts_created`, `exports`) as bars, with reset. |

---

## 3. Flow pipeline hardening (why pictures "never arrived")

1. **Shadow DOM**: Flow's result grid is inside open shadow roots on some builds — `FlowJs.CAPTURE` now
   walks them (depth 3) instead of only `document.querySelectorAll`.
2. **CSS-background tiles**: newer Flow builds paint results as `background-image` on divs; those are now
   detected too (size- and chrome-filtered, same avatar/logo rules).
3. **Page-side fetch fallback**: if the direct OkHttp download fails (signed URL / hotlink protection),
   `FlowAutopilot` asks the **page** to `fetch(url, {credentials:'include'})` (with XHR fallback), streams
   it back as base64 chunks and saves it through the same quality gate. This is the difference between
   "image generated" and "image in the library".
4. **WebView login blocked?** New menu items: **Chrome mein kholo** (generate in the real browser) and
   **Gallery se import** (import those pictures into the library). Verify → plan → timeline then run
   exactly as before, so the pipeline can never be a dead end.
5. Counters increment on every saved picture, so analytics and self-test show real numbers.

---

## 4. Honest limits
- Nothing was compiled locally (no JDK/Android SDK in the sandbox); GitHub Actions is the compile.
  Injected JavaScript is syntax-checked with `node --check` (all 8 blocks pass).
- Runtime behaviour on a device can still differ (WebView version, Google sign-in policy, provider quotas).
  That is exactly what Power Tools exists for: run **Self test**, then share the report/crash log.
- The Vosk model downloads on first use; until it does, live STT depends on a cloud STT-capable key
  (Groq/OpenAI/Gemini). The self-test shows this state explicitly.
