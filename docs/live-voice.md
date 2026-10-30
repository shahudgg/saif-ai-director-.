# Live voice

Normal live conversation uses the configured chat provider/model, the shared STT router,
and the voice selected under Voice & TTS (SAIF ADVANCE VOICE by default). The automation
foreground service owns the session even when its UI is closed.

OpenAI, Groq and Gemini keys can supply speech-to-text. When the chosen provider has no
speech endpoint, the app falls back to its existing on-device Vosk model. That model is
English and is downloaded on first use; if it has not finished downloading, live voice
shows an actionable error instead of asking for a Gemini key. An available speech-capable
key may also be used by the STT router. Owner voice lock still verifies the captured audio
before the turn is sent.

Live settings include an **opt-in** Gemini native-audio fast path. It retains Gemini's
native voice rather than the selected app TTS voice. Screen sharing remains opt-in: the
native path streams frames to Gemini; the turn loop attaches the latest frame to a turn
only while sharing is active and only for vision-capable providers. Screen sharing stops
when the live session stops. Barge-in uses the device's communication audio source/AEC
when available and the shared VAD's near-field energy gate.
