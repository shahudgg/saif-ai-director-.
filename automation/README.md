# SAIF AI AUTOMATION (module `:automation`)

Voice + phone-automation agent that lives inside SAIF AI. Open it with the **red "+" button** in the
home header (next to the purple and orange ones).

* Based on the open-source **JARVIS Android Assistant by Tech GPTx** (https://github.com/techgptx/Playlist),
  licensed under **TAMCL v1.0** (see `LICENSE`). Copyright (c) 2026 Tech GPTx. All Rights Reserved.
  The attribution must stay in the code, the manifest and the in-app credits
  (SAIF AI AUTOMATION > Settings > App Identity). The license also requires that content creators
  credit Tech GPTx in any video that uses this code.
* Changes made for SAIF AI: renamed to SAIF AI AUTOMATION, API key / provider / model now come from the
  SAIF AI Settings panel (`SaifAutomation.link(...)` in `SaifApplication`), UI colours follow the SAIF AI
  theme, runs as an Android library (package `com.jarvis.assistant` kept on purpose), the standalone
  anti-clone / release-pin checks were switched off, Room moved from kapt to KSP.

## How the API link works
`SaifApplication` registers a resolver that reads `ProviderSettingsManager` (provider, current key, active model).
The engine reads it through `SaifAutomation.currentApi()`. Saving new settings calls
`SaifAutomation.onHostApiChanged()`, which reconnects a running live session.

## Notes
* The live voice engine speaks the **Gemini Live** protocol: provider must be Gemini in SAIF AI Settings.
* Wake word stays "Hey Jarvis" (the offline Vosk model knows that word, not "SAIF").
* Vosk speech models (about 50 MB) are not bundled: they download once on first use (internet needed),
  or put them in `src/main/assets/model-en-us` and `model-spk`.
* `three.min.js` is not bundled: the home orb falls back to the cdnjs build (needs internet).
* minSdk is 26.
