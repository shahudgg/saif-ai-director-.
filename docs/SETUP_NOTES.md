# SAIF AI Director — setup notes

## GitHub secrets (Settings → Secrets and variables → Actions)
| Secret | Meaning |
|---|---|
| `TG_BOT_TOKEN` | Token of the Telegram bot that is an **admin of your asset channel** (BotFather). |
| `TG_CHAT_ID` | The channel id (`-100…`) or `@username`. Posts from other chats are ignored. |

Both are injected only at build time (`BuildConfig`). Nothing secret is stored in this repository.

## Indexing the Telegram channel
1. Add the bot as **administrator** of the channel (it must be able to see posts).
2. Upload every asset **as a file** (video/audio/photo/document) and put this caption on it:
```
[ASSET_INDEX]
Title: Whoosh fast 01
Type: audio            (image | audio | video | font)
Extension: mp3
Category: sfx          (sfx | bgm | b_roll | overlay_graphic | background_video | transition | font)
Subject: fast whoosh transition
Search Keywords: whoosh, swoosh, transition, riser
Tags: #sfx #whoosh
User_ID: all
[/ASSET_INDEX]
```
   Posts **without** a caption are still indexed automatically from the file name (e.g. `whoosh_fast_01.mp3` → SFX).
3. Keep files **under 20 MB** (Telegram Bot API limit for downloads).
4. In the app: Director → ⚙ → *Research & asset sources* → **Telegram library** → **Sync now**.
   The panel shows how many updates were read, how many posts were indexed, and why others were skipped.
   If it says a **webhook is set**, tap *Remove webhook* (a webhook blocks `getUpdates`).
5. The catalog is saved on the phone and the Telegram update offset is remembered, so assets stay available after restarts.

## What each workflow needs
| Workflow | Needs |
|---|---|
| AI video (Zero to Video) | A chat model (any provider). Search key optional (research). Works with a weak model: layout is designed on the device. |
| Edit my video with AI | Chat model; transcription key (Groq/OpenAI Whisper) for speech-based cuts. |
| AI voice on my video | Chat model + on-device TTS voice installed. |
| Voice-over to video | Transcription key + chat model (chat optional: beats are composed locally). |
| Telegram assets/SFX | Bot token + channel (above). Without them, SFX are synthesized on the device. |

## Where files go
* Exported video: `Movies/SAIF AI/`
* Voice-over script (narration only, one `.txt`): `Download/SAIF AI/<name>_voiceover_script.txt` (Director → *Voiceover script*)
