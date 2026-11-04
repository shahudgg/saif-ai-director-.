# SAIF AI ADVANCE EDITING

Entry point: purple **+** → **SAIF AI ADVANCE EDITING** (top card in the sheet).

## What is inside

The complete production engine from the owner-supplied `deepx video generator studio.zip` is bundled
inside the APK as read-only assets in `app/src/main/assets/saif_advance/` — same file tree, same file
names, same code (530 files, ~19.4 MB), including:

| Folder | Contents |
|---|---|
| `core/` | `render.py` (865 lines), `align.py`, `phonetic_align.py`, `cutout.py`, `key.py`, `mix.py`, `elements.py`, `trends.py`, `parallel_generator.py`, `flow_bridge.py`, `dedupe_guard.py`, `direct_sync.py`, `cartesia_tts.py` |
| `skills/` | `bang-motion`, `embedded-captions`, `vox-doc`, `vox-doc-sound`, `stickman-hybrid` (their scripts, HTML/CSS motion templates, DNA json, assets) |
| `fonts/` | English, Hindi and Nepali font families used by the renderer |
| `elements/` | cut-out element PNG library |
| `styles/` | `stickman_hybrid.py` style definitions |
| docs | `AGENT.md`, `AGENTS.md`, `PLAYBOOK.md`, `SKILL.md`, `SETUP.md`, `START_HERE.txt`, `COMPULSORY_ASSET_DENSITY_STANDARD.md`, `README.md` |

## How it runs

The engine is Python + ffmpeg, so it runs in **Termux** (the app already ships `TermuxBridge`). The screen:

1. packs the bundled assets into `SAIF_AI_Advance_engine.zip` and publishes it to
   `Download/SAIF_AI_Advance/` (MediaStore on Android 10+, direct file below that),
2. gives one-tap Termux commands for the engine's own 9-step protocol
   (install → deps → self-check → trend scan → VO align → cut-outs → plan/shots → mix → master),
3. shows the exact command for every step so it can also be copied by hand.

Nothing is simulated: the button reports what the device actually did, and a failure is reported as a
failure.

## Credits

Every credit in the bundled source was replaced before it was committed:

`DeepX` / `XDeep` / `XDEEP` / `XDeepFreeVideoGeneration` / `xdeepfreevideogeneration` / `DEEPX_WS_URL`
→ **SAIF AI** wording (`SAIF AI Advance Studio`, `saif_advance`, `SAIF_AI_WS_URL`).

The remaining text files contain no other product name.
