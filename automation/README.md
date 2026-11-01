# SAIF AI AUTOMATION (module `:automation`)

Voice + phone-automation agent that lives inside SAIF AI. Start it with the **purple mic button** in the
home chat (Normal voice / Share screen).

* **Owned and credited 100% to SAIF AI.** All third-party credits, license notices and attribution
  requirements (Tech GPTx / techgpt.ai / TAMCL / "JARVIS Android Assistant") have been removed from the
  code, the manifest, the UI and this file. Project: https://github.com/shahudgg/saif-ai-director-
* Integrated into SAIF AI: API key / provider / model come from the
  SAIF AI Settings panel (`SaifAutomation.link(...)` in `SaifApplication`), the UI follows the SAIF AI
  theme, it runs as an Android library module, the standalone anti-clone / release-pin checks are
  switched off, and Room moved from kapt to KSP.

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
