# One-time setup for the stickman-hybrid skill

This sets up the computer to make narrated stickman explainer videos: Flow pictures,
voiceover (English locally, Hindi on Kaggle's free GPU) and sound design, rendered with
Python and ffmpeg.

- **Prerequisite:** Flow Connector, set up earlier from the extension's own
  setup prompt. This file only verifies it.
- **Status file:** after every step, write the result to
  `~/.claude/skills/stickman-hybrid/SETUP_STATUS.md` (one row per step: ✅ / ❌ /
  skipped, plus the version). A new chat can then resume from the first
  unfinished step.
- When everything required is ✅, go back to the stickman-hybrid `SKILL.md` and start
  the user's video.

## How to work

- **Talk to me in my language**: the one I write my messages in (Hinglish, Hindi, English
  or any other). If you cannot tell, ask once. Keep it simple, no jargon. Commands, paths
  and file contents stay in English.
- For every step, tell me in one line **what** it is and
  **what I gain** from it, then do it, then show the **checkpoint result** (✅ / ❌ and the
  version).
- **Check before installing.** If a tool already works, say so and move on. Never reinstall
  or downgrade something that already works.
- Detect the OS first and use the matching commands: Windows → `winget`, macOS → `brew`,
  Linux → `apt`. Always give full absolute paths. Give a `https://` link only when you are certain it is right (one written in these files, or one a tool printed); never guess a URL. If unsure, give the menu path or let the tool say it.
- Python: always use `python -m pip install ...`, never bare `pip`. After every install,
  verify with a real `python -c "import X; print(X.__version__)"`, because pip can exit 0
  without installing anything.
- Ask me before any download over 2 GB.
- **Logins and tokens:** when something needs an account, a login or a token, stop and
  guide me click by click, with the exact URL and the menu path. Tell me where to paste the
  token, and never ask me to paste a token into this chat. Then verify that it works.
- If a step fails, find the cause, fix it, and re-run the checkpoint. Do not skip a step
  silently.
- At the end, give me one table: tool · status · version · what is still pending from me.

## Step 0: Know the machine

Report the OS, RAM, GPU (name + VRAM, or "none"), free disk space and the default shell.
Then decide, and tell me the decision:
- **Strong machine** (NVIDIA GPU with ≥ 8 GB VRAM, or ≥ 16 GB RAM): Hindi voices can run
  locally.
- **Weak machine** (anything less): Hindi/Indian-language voices will run on Kaggle's free
  GPU (Step 7). Kokoro (English) still runs locally on any machine.

## Step 1: Node.js + Flow Connector (verify, update if old)

*Benefit: free image generation with Google Flow (Nano Banana) from the terminal.*
- `node --version` must be **20 or newer**, and
  `npm --version` must work. If Node is older, upgrade it (Windows:
  `winget install OpenJS.NodeJS.LTS`, macOS: `brew install node`, Linux: NodeSource or nvm).
- Compare `flow-auto --version` with `npm view flow-connector-cli version`. If the latest is
  newer, run `npm install -g flow-connector-cli@latest`.
- Check the agent skill: `flow-auto doctor --site flow` must show `Agent skill … installed`.
  "Bridge not running" and "Extension not connected" are normal here: the bridge starts
  on the first generation, and the extension connects when a Flow tab is open. Do not
  fail the setup on them; the video's picture stage connects Flow. Never give the user a
  Flow web address of your own.
- ✅ Checkpoint: `flow-auto --version` prints a version and the agent skill is installed.

## Step 2: ffmpeg (full build, with libass)

*Benefit: joins the pictures on the spoken words, mixes the audio, and shapes Hindi labels
correctly. It is the editor of this whole pipeline.*
- Windows: `winget install Gyan.FFmpeg` (the full build). macOS: `brew install ffmpeg`.
  Linux: `sudo apt install ffmpeg`.
- ✅ Checkpoint 1: `ffmpeg -version` works and `ffmpeg -hide_banner -filters` lists `ass`.
- ✅ Checkpoint 2 (labels in every look, Hindi shaping): after Step 3, in a scratch folder run
  `python ~/.claude/skills/stickman-hybrid/scripts/render.py --selfcheck`. It writes
  `selfcheck_<look>.png` for the five looks. Open `selfcheck_warm.png` for me: the Hindi line
  must have joined conjuncts (श्री, क्ष, त्रि). It goes through libass with `shaping=complex`,
  because Pillow alone breaks Devanagari on most machines.

## Step 3: Python and the core packages

*Benefit: the picture renderer, keying, word timing and voice tools all run on Python.*
- Python 3.11–3.13 (3.12 is the safest for wheels). If several Pythons exist, find the one
  that works and tell me which command to use.
- Install: `python -m pip install numpy pillow scipy soundfile faster-whisper` (scipy keys the stickers off their magenta background)
- ✅ Checkpoint: import all five and print their versions. Then load the faster-whisper
  `tiny` model and transcribe a 2-second clip generated with ffmpeg (`sine` source). It
  must run without errors.

## Step 3b: LaMa, the clean watermark remover (recommended)

*Benefit: every Flow picture carries a small Gemini sparkle near its bottom-right corner.
LaMa paints it out properly, even on busy corners, and leaves every other pixel unchanged.
Without it, render.py falls back to a smooth fill: invisible on sky and walls, slightly soft on
detailed corners.*
- Licence: LaMa (Samsung AI, github.com/advimman/lama) is Apache-2.0. Use it only on your own
  Flow generations. It does not touch Google's invisible SynthID marking, and you must not claim
  that it does.
- Check first: `python -c "import torch; print(torch.__version__)"`. If that fails, install the
  CPU build: `python -m pip install torch` (about 200 MB to download, under 1 GB installed;
  CPU is enough). Tell me the size before downloading.
- Model, 206 MB, one time: download
  https://github.com/enesmsahin/simple-lama-inpainting/releases/download/v0.1.0/big-lama.pt
  to `~/.cache/lama/big-lama.pt` (Windows: `C:\Users\<me>\.cache\lama\big-lama.pt`). Skip
  this if the file is already there. Its size must be exactly 205,803,670 bytes.
- ✅ Checkpoint: in a scratch folder run
  `python ~/.claude/skills/stickman-hybrid/scripts/delama.py --selfcheck`. It must print a mean
  error under 12 and write `delama_selfcheck.png`; open that file for me. The drawn mark must
  be gone without a smudge.
- If I decline, mark this step "skipped". Videos still work.

## Step 4: Kokoro, the English and international voice (local, free, Apache 2.0)

*Benefit: free narration that you may use commercially. It runs on CPU, so it works even
on a weak laptop.*
- `python -m pip install kokoro soundfile`, plus espeak-ng (Windows:
  `winget install eSpeak-NG.eSpeak-NG`, macOS: `brew install espeak-ng`, Linux:
  `sudo apt install espeak-ng`).
- Languages: American and British English, Spanish, French, Italian, Brazilian Portuguese,
  Hindi, Japanese and Mandarin. Japanese needs `python -m pip install "misaki[ja]"`,
  Mandarin needs `"misaki[zh]"`. Install those two only if I say I need them; ask me.
- Note for me: Kokoro's Hindi works but sounds flat. For Hindi, use Step 6/7. Do not use
  Piper's Hindi voices or MMS-TTS for videos you plan to monetise: they are NonCommercial.
- ✅ Checkpoint: generate one English line (`af_heart` voice) to `kokoro_test.wav`, report
  its length, and open it so I can listen.

## Step 5: Hugging Face (only as a backup; skip now)

Not needed normally. The Hindi voice (Indic Parler-TTS, Apache 2.0) and the AI sound effects
(Stable Audio Open) load from ungated mirrors pinned to revisions whose files match the
official repos, so no Hugging Face login, access request or token is needed. Tell me this
in one line and move on.

Do this step only if a Kaggle voice or SFX job log says "mirror failed". Then guide me:
1. Sign up or log in: https://huggingface.co/join
2. Accept the terms of https://huggingface.co/ai4bharat/indic-parler-tts (Hindi voice)
   and/or https://huggingface.co/stabilityai/stable-audio-open-1.0 (sound effects).
3. Create a **Read** token: https://huggingface.co/settings/tokens → "Create new token".
4. On that Kaggle notebook's page: Add-ons → Secrets → add `HF_TOKEN`, paste the token
   there (never in this chat), tick it to attach it → Save Version. Then push again.

## Step 6: Indic Parler-TTS locally (strong machine only)

*Benefit: natural Hindi narration, plus 20 other Indian languages and English, with a
style you control from a text description.*
- Skip this step on a weak machine and say why.
- Create a **separate virtual environment**, for example `parlerenv` next to the project.
  Parler pins an old `transformers` that would break Kokoro and Whisper in the main
  environment. Inside it: `python -m pip install torch` (the CUDA build if there is an
  NVIDIA GPU), then `python -m pip install git+https://github.com/huggingface/parler-tts.git`.
  Set `USE_TF=0` before importing transformers. Load the model and tokenizer from
  `RXD03/indic-parler-tts` at `revision="6ffcecc3b3d72a6256019897be0610fa3317bec2"` (no token).
- ✅ Checkpoint: generate one Hindi line with speaker "Aman" ("Aman speaks with a clear,
  confident and engaging voice at a moderate pace, like a documentary narrator…"), save it
  and open it for me.
- Note for me: good male narrators are Aman, Kabir, Bhanu, Dinesh and Aakash. Rohit sounds
  robotic.

## Step 7: Kaggle, the free GPU for when this machine is too weak

*Benefit: about 30 free GPU hours a week (T4) for Hindi voices, voice cloning, SFX and
music generation.*
Guide me through:
1. Sign up or log in: https://www.kaggle.com
2. Verify the phone number. Without it there is no GPU and no internet in notebooks:
   https://www.kaggle.com/settings → "Phone verification".
3. `python -m pip install -U kaggle` (the new tokens need CLI 1.8.0 or newer).
4. Create an API token: https://www.kaggle.com/settings → the **API Tokens** tab →
   **Generate New Token** → give it any name. A box shows the token **once** (it starts with
   `KGAT_`). I copy it with the copy button. No file is downloaded; that is normal.
5. Save the token without it ever reaching this chat: you create the folder `~/.kaggle`
   and an empty file `~/.kaggle/access_token` (Windows:
   `C:\Users\<me>\.kaggle\access_token`), then open that file in a text editor for me
   (Windows: `notepad`). I paste the token, save and close. The Kaggle CLI reads that file
   by itself.
   - If I paste the token into the chat or show it in a screenshot by mistake, tell me to
     expire it on the same page (the ⋮ menu on its row) and generate a new one.
   - Older way, only if the above fails: **Create Legacy API Key** on the same tab
     downloads `kaggle.json` for `~/.kaggle/kaggle.json`. Warn me first that it expires my
     earlier legacy key.
- ✅ Checkpoint: `python -m kaggle.cli kernels list --mine` works (on Windows, set
  `PYTHONIOENCODING=utf-8` first, otherwise Hindi output crashes the CLI). Then push a
  2-line private test kernel (GPU T4, internet on) that prints `torch.cuda.get_device_name()`.
  Wait for it to finish, download its log, and show me the GPU name. No Kaggle secrets are
  needed. A private notebook's page opens only when the browser is logged into the same
  Kaggle account as the CLI; a "can't find that page" error usually means a different
  account.
- Kaggle traps to remember: at most 2 GPU jobs can run at once, so retry the push in a loop
  if it says so. Always set `USE_TF=0` in Parler jobs.

## Step 8: The sound kit

*Benefit: 1,000+ free SFX and ambiences and 40+ music beds you may use commercially, with a
skill that places them on the spoken words and mixes to YouTube loudness. (The label fonts,
Patrick Hand and Kalam, ship inside the skill under the SIL Open Font Licence; nothing to
install.)*

**8a. The sound kit.** The installer has already copied it to
`~/.claude/skills/vox-doc-sound/`. If that folder is missing, say so: the user
must re-run the installer from the zip. Inside it, run
`python scripts/index_sfx.py`, which builds `CATALOG.tsv`.
✅ Checkpoint:
- Report how many SFX, ambiences and music tracks are in the catalog.
- Play one whoosh and 10 seconds of one music bed.

**8b. Making more sounds when the kit lacks one** (the skill does this during a video;
here only verify that each route works):
- **Free AI SFX and ambience on Kaggle** (Stable Audio Open; I own the output and may use
  it commercially while my yearly revenue is under $1M). Needs Steps 5 and 7. Write a
  one-row TSV (`Test, test_whoosh, 2, a soft airy whoosh transition, clean high quality
  sound effect, isolated`), run the skill's `kaggle/make_sfx_job.py` on it, push it, wait,
  download the WAV and play it for me. Then delete the test sound.
- **Music and ambient pads: Google Gemini** (free, made by me, may be used commercially and
  shared). Show me https://gemini.google.com and the prompts in the skill's
  `music/README.md`. Tell me the settings (Instrumental, a length of 2–3 minutes, Ambient
  genre for pads), and that new tracks go into the skill's `music/<mood>/` folder before I
  re-run `index_sfx.py`. Nothing to install.
- **Optional: ElevenLabs** (best quality, paid plan for commercial use). Only if I want
  it: I put `ELEVENLABS_API_KEY=...` into the skill folder's `.env` file myself. Never ask
  me to paste the key into this chat. ✅ One 1-second test sound via
  `scripts/eleven_sfx.py`.
- **Manual fallback:** Pixabay (https://pixabay.com/sound-effects/,
  https://pixabay.com/music/), Freesound CC0 (https://freesound.org) and the YouTube Audio
  Library (https://studio.youtube.com → Audio Library). Files downloaded from these may be
  used in my videos but not shared onward.

## Finish

Show the final table and save it as `~/.claude/skills/stickman-hybrid/SETUP_STATUS.md`.
Add the exact Python command that works on this machine. Then:
- tell the user in one line what is still pending from their side, for
  example the Kaggle phone verification
- ask for the topic, language and length of their first video, and continue with the stickman-hybrid skill
