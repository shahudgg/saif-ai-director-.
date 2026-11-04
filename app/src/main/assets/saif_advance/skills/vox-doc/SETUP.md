# One-time setup for the vox-doc skill

This sets up the computer to make narrated Vox-style explainer videos: cut-out
PNG art, voiceover and sound design, rendered with Python and ffmpeg.

- **Prerequisite:** Flow Connector, set up earlier from the extension's own
  setup prompt. This file only verifies it.
- **Status file:** after every step, write the result to
  `~/.claude/skills/vox-doc/SETUP_STATUS.md` (one row per step: ✅ / ❌ /
  skipped, plus the version). A new chat can then resume from the first
  unfinished step.
- When everything required is ✅, go back to the vox-doc `SKILL.md` and start
  the user's video.

Steps 9-10 (HyperFrames, Remotion) are optional extras for later. Ask the user
once whether they want them, and skip them by default.

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
- `node --version` must be **22 or newer** (HyperFrames in Step 9 requires it), and
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

*Benefit: cuts, joins, mixes audio and burns Hindi titles into the video. It is the
editor of this whole pipeline.*
- Windows: `winget install Gyan.FFmpeg` (the full build). macOS: `brew install ffmpeg`.
  Linux: `sudo apt install ffmpeg`.
- ✅ Checkpoint 1: `ffmpeg -version` works and `ffmpeg -hide_banner -filters` lists `ass`.
- ✅ Checkpoint 2 (Hindi shaping): write a tiny `.ass` file containing the text "श्री
  क्षत्रिय", burn it onto a 2-second colour clip with `-vf "ass=test.ass:shaping=complex"`,
  extract one frame, and show it to me. The conjuncts must join correctly. Use the `ass`
  filter, not `subtitles`, because `subtitles` has no `shaping` option and breaks
  Devanagari.

## Step 3: Python and the core packages

*Benefit: the picture renderer, keying, word timing and voice tools all run on Python.*
- Python 3.11–3.13 (3.12 is the safest for wheels). If several Pythons exist, find the one
  that works and tell me which command to use.
- Install: `python -m pip install numpy pillow scipy soundfile faster-whisper`
- ✅ Checkpoint: import all five and print their versions. Then load the faster-whisper
  `tiny` model and transcribe a 2-second clip generated with ffmpeg (`sine` source). It
  must run without errors.

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
3. Create an API token: https://www.kaggle.com/settings → "API" section → "Create New
   Token". If it downloads `kaggle.json`, put it in `~/.kaggle/kaggle.json` (Windows:
   `C:\Users\<me>\.kaggle\kaggle.json`). If the page instead shows a new-style token string,
   store it in the `KAGGLE_API_TOKEN` environment variable. Follow whichever the page
   offers.
4. `python -m pip install kaggle`
- ✅ Checkpoint: `python -m kaggle.cli kernels list --mine` works (on Windows, set
  `PYTHONIOENCODING=utf-8` first, otherwise Hindi output crashes the CLI). Then push a
  2-line private test kernel (GPU T4, internet on) that prints `torch.cuda.get_device_name()`.
  Wait for it to finish, download its log, and show me the GPU name. No Kaggle secrets are
  needed. A private notebook's page opens only when the browser is logged into the same
  Kaggle account as the CLI; a "can't find that page" error usually means a different
  account.
- Kaggle traps to remember: at most 2 GPU jobs can run at once, so retry the push in a loop
  if it says so. Always set `USE_TF=0` in Parler jobs.

## Step 8: Fonts and the sound kit

*Benefit: Hindi titles render cleanly; you get the proven Vox method, plus 1,000+ free SFX
and ambiences and 40+ music beds you may use commercially, with a skill that does the
sound design for you.*

**8a. Fonts.** Devanagari font: Windows already has "Nirmala UI". On macOS use "Kohinoor
Devanagari". On Linux, `sudo apt install fonts-noto` gives "Noto Sans Devanagari".
✅ Confirm the font name that ffmpeg/libass will use.

**8b. The sound kit.** The installer has already copied it to
`~/.claude/skills/vox-doc-sound/`. If that folder is missing, say so: the user
must re-run the installer from the zip. Inside it, run
`python scripts/index_sfx.py`, which builds `CATALOG.tsv`.
✅ Checkpoint:
- Report how many SFX, ambiences and music tracks are in the catalog.
- Play one whoosh and 10 seconds of one music bed.

**8c. Making more sounds when the kit lacks one** (the skill does this during a video;
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

## Step 9: HyperFrames, HTML-based motion graphics (for later use)

*Benefit: ready-made animated titles, captions, text effects, transitions and overlays
written as HTML/GSAP and rendered to MP4. Good for intros, kinetic text and promo-style
pieces. No account needed for local rendering.*
- Needs Node 22+ and ffmpeg (Steps 1–2). Nothing is installed globally; it runs through `npx`.
- Install the Claude Code skills for it with `npx hyperframes skills update` (the core set).
  If the CLI cannot do that, use `npx skills add heygen-com/hyperframes --all`.
- ✅ Checkpoint 1: `npx hyperframes doctor --json` reports `"ok": true`. Fix anything it
  flags, for example the browser download.
- ✅ Checkpoint 2: in a scratch folder, run `npx hyperframes init hf-test --non-interactive`
  with one of the examples that `npx hyperframes init --help` lists, then
  `npx hyperframes render --quality draft --output test.mp4` inside it. Report the
  duration of `test.mp4` and open it for me.
- Cloud rendering (`npx hyperframes cloud render`, hosted by HeyGen) needs a login. Skip it
  now and only mention that it exists.

## Step 10: Remotion, video made with React code (for later use)

*Benefit: build videos as React components, which suits data-driven or templated videos
(the same layout with 100 different texts or numbers) and anything with complex
programmatic animation.*
- Licence, tell me this clearly: Remotion is free for individuals and companies with up to 3
  employees. Bigger companies need a paid company licence: https://www.remotion.dev/license
- Install the Claude Code skill: `npx skills add remotion-dev/skills`. If that repo name
  fails, search with `npx skills find remotion` and install the official one.
- ✅ Checkpoint: in a scratch folder run
  `npx create-video@latest --yes --blank --no-tailwind remotion-test`, then inside it
  `npx remotion browser ensure` (downloads the headless browser it renders with). Then run
  `npx remotion compositions` and render the first composition to `out.mp4` with
  `npx remotion render <id> out.mp4`. Report the duration and open it for me.

## Which tool is for what (tell me this at the end)

- **Python (Pillow) + ffmpeg:** the main pipeline for narrated explainers with cut-out
  PNGs, maps, clocks and Hindi labels. It is the lightest and runs on any machine.
- **HyperFrames:** quick animated titles, captions and motion-graphic templates.
- **Remotion:** React-coded videos and templated batch videos.

## Finish

Show the final table and save it as `~/.claude/skills/vox-doc/SETUP_STATUS.md`.
Add the exact Python command that works on this machine. Then:
- tell the user in one line what is still pending from their side, for
  example the Kaggle phone verification
- ask for the topic of their first video, and continue with the vox-doc skill
