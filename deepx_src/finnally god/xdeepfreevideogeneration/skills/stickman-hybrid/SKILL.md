---
name: stickman-hybrid
description: Make a complete stickman documentary explainer in the HYBRID look, end to end: clean full-colour scene pictures (Google Flow, Nano Banana 2) with cut-out stickers that pop in on the spoken words, animated counters and paper collage beats, in the style of the viral "ancient humans" explainer channels. Covers setup, topic ideas, the script, the voiceover (Kokoro for English, Indic Parler with Aman on Kaggle for Hindi and Indian languages), a consistent stickman cast, LaMa watermark removal, word-timed cuts, Hindi and English on-screen text, sound design and the final MP4. Use when the user says "stickman hybrid", "hybrid stickman", "stickman sticker video", "stickman hybrid start", "stickman hybrid continue", or opens the stickman-animation (stickman-hybrid) zip.
---

# Stickman documentary, hybrid look, end to end

You make the whole video for the user. They choose the topic, language and length; you do
the rest and show them only three checkpoints: the script, the voice and the finished video.

**Talk to the user in their own language** (often Hinglish), simply. Commands, file names and
file contents stay in English. Always give full absolute paths. Give a `https://` link only
when you are certain it is right (one written in these files, or one a tool printed); never
guess a URL. If unsure, give the menu path or let the tool say it.

`<skill>` means this skill's folder: `~/.claude/skills/stickman-hybrid/` (Windows:
`C:\Users\<me>\.claude\skills\stickman-hybrid\`). The companion skill **vox-doc-sound**
(`~/.claude/skills/vox-doc-sound/`) makes the voice and the sound; read its SKILL.md at
those stages.

**Before planning any video, read `<skill>/PLAYBOOK.md` fully and `<skill>/looks/README.md`.**
The Part 1 rules are what made the test films work; follow them without exception.
`<skill>/example/` holds the approved films. **`boat_hi` is the current target** (follow it first). `food` (English) and `food_hi` (Hindi, the same
pictures re-voiced) and `teeth_hybrid` are the target: their SCENES (with `+` sticker rows),
ELEMENTS, PICK/ELPICK, cues and contact sheets show it. `teeth` and `cooking` are the plain-stills
ancestors.

**This pack makes the HYBRID look by default** (PLAYBOOK §9): every film has scene pictures plus
ELEMENTS.tsv stickers, counters and a few paper collage beats. Make plain stills only if the user
asks for them.

## Every time this skill starts ("start", "continue", a topic, anything)

1. **Setup check.** Read `<skill>/SETUP_STATUS.md`. If it is missing or any required row is
   not ✅, run or resume `<skill>/SETUP.md` from the first unfinished step, then come back.
   Prerequisite: Flow Connector, set up by the user from the extension's own setup prompt.
   At setup only check `flow-auto --version`. "Extension not connected" is NOT a setup
   failure; Flow is connected at the picture stage.
2. **Find the project.** Projects live in `~/StickmanHybrid/<topic-slug>/`.
   - "continue" with no topic: open the most recently modified `PROGRESS.md` under
     `~/StickmanHybrid/`; tell the user which project and stage you are resuming.
   - New topic: create `~/StickmanHybrid/<slug>/` (short, lowercase, no spaces) with `PROGRESS.md`.
   - No topic yet: ask in ONE short message, with no defaults:
     - **Topic**: their own, or "Want me to suggest 10 topics?" (PLAYBOOK §1). For Hindi
       audiences give bilingual titles.
     - **Language** of the narration: Hindi, English, Hinglish, **or any other**. Say honestly
       which have a free voice (vox-doc-sound SKILL.md, step 2).
     - **Length**: their choice (for example 2, 5, 8 or 10 minutes, or any number). Do not
       pre-select one.
3. Work only inside the project folder; run every script from there.
4. After each stage update `PROGRESS.md`: stage, ✅, decisions (look, cast, voice, language,
   length), what the user approved. Then "continue" in any new chat resumes correctly.

## Stages

| # | Stage | Do | Checkpoint |
|---|---|---|---|
| 1 | Script | `SCRIPT.md` + `LINES.tsv` (PLAYBOOK §1). Hindi narration runs about 25% longer than English: write about 15% fewer words for the chosen length. Check facts on the web if available; word debated points as such | **Wait for the user's OK** |
| 2 | Look, cast, pictures: START IMMEDIATELY after the OK, in parallel with stage 3 | Choose the look by topic (`looks/`), write `STYLE.txt`, decide the cast. Also write `ELEMENTS.tsv` (PLAYBOOK §9) and run `pictures.py elements 1 <a pose>` as the sticker test, then the rest. Connect Flow: `flow-auto bridge start` in the background, then `flow-auto doctor --site flow` (relay its words if a line fails; never write your own Flow URL). Make the character sheet, then `pictures.py refs`. Write `SHOTS.tsv` from the script (PLAYBOOK §3). `pictures.py submit` 2 test shots, look at them, then submit the rest, and start `delama.py --watch` in the background (LaMa cleans each picture as it arrives) | One line: "look X, N pictures generating while the voice is made" |
| 3 | Voice: same turn as stage 2 | vox-doc-sound. **Voice rule:** Hindi, Hinglish or any Indian language → **Indic Parler-TTS** on Kaggle (`kaggle/make_parler_job.py`), never Kokoro; for Hindi and Hinglish the first take is **always speaker Aman**. English and other languages → Kokoro. The user's own ElevenLabs key or recording only if they ask. Then `vo_join.py`, then `align.py --lang <code>` gives `WORDS.tsv` | Play 2 lines; the user approves Aman or asks for another speaker (Kabir, Bhanu, Dinesh, Aakash) |
| 4 | Scenes | `SCENES.tsv` on the words (PLAYBOOK §5, §9), then `render.py --pacing` and fix EVERY long hold with a beat the line names (cut, sticker, counter, tag, a "no" sticker) | A short table: line → what is on screen (no approval wait) |
| 5 | Collect pictures | When the queue is empty: re-submit refused ids once. `pictures.py review` → look → `PICK.tsv` for swaps → regenerate wrong ones → `pictures.py pick` → `delama.py` (copies the LaMa-cleaned pictures; instant) | One line: "N pictures in, fixed X" |
| 6 | Sound | vox-doc-sound: `cues.tsv` on words (PLAYBOOK §7), then `mix.py ... --out mix.wav` | Loudness numbers |
| 7 | Render + QC | `render.py`, then `pictures.py qc` and read `qc.jpg` YOURSELF: wrong picture, tag over a face, sheet line-ups, leftover sparkle. Fix, re-render. Do not show the contact sheet to the user | Open the video for the user and give its full path. **They judge the final video** |

**Never run the voice first and the pictures later.** The pictures depend only on the
script's words; start them in the same turn the script is approved (users noticed and
objected when this was done in sequence).

## Rules that are easy to forget

- **Voice:** Hindi, Hinglish and every Indian language are voiced with Indic Parler-TTS (Kaggle), never Kokoro, whose Hindi is flat. Hindi and Hinglish start with speaker **Aman**; offer Kabir, Bhanu, Dinesh or Aakash only if the user wants a change (Rohit sounds robotic). English and other languages use Kokoro (`--speed 1.0`). Parler uses make_parler_job.py's defaults (`natural` description, seed 11) and is NEVER sped up: no `--tempo`, ever (1.10x made Hindi robotic). vo_join adds the approved pauses itself (Indian +0.3 s, others +0.35 s); a line it flags `RE-MAKE` is voiced again with another seed. Counter sounds go on the same word as the counter's `+cue`.
- **Never swap in another voice engine on your own** (Edge/Azure voices such as Madhur,
  Google TTS, Piper, MMS, gTTS), not even "temporarily". The user did not choose it, the film
  would need re-timing, and those licences are not cleared for this pack.
- **Where Parler runs is the user's decision.**
  - SETUP Step 0 records the machine as strong (an NVIDIA GPU with ≥ 8 GB VRAM, or ≥ 16 GB
    RAM) or weak. Kaggle is the default either way.
  - If the Kaggle job is QUEUED, or the user would rather run it locally, follow PLAYBOOK §4
    "Kaggle queue": stop and ASK, with the options and their honest cost on THIS machine.
  - Do what the user picks. Never decide alone.
- Scene pictures are full, natural scenes (leave SHOTS `space` empty). Stickers go to the picture's quiet
  spots (`auto`) or onto plain collage cards; never spoil a scene for a sticker.
  On-screen text only on beat/question cards and collage cards (English), never on scene pictures.
  Hindi emphasis comes from short punch lines ending in "!" with ONE doc voice for all lines; per-line
  styles made the voice robotic (vox-doc-sound 2b).
- Scene pictures stay still. Motion comes only from stickers, counters and tags popping on the spoken
  words (PLAYBOOK §9). A new picture every ~2-3 s, more often in the hook. No zooms, no AI video.
- Pictures and stickers use Nano Banana 2 (Flow shows it as "Nano Banana 2.1"), never Pro: `pictures.py`
  already does this.
- Two stickers must never overlap; render.py separates them. Pin a sticker that sits near a face.
- A sticker only for a thing the line NAMES that the picture does NOT already show. No repeats of the
  picture, no decorations. If none fits, use a cut to a relevant picture.
- Flow swaps files between the scene and sticker folders too: check both `review.jpg` and
  `cutouts.jpg` before picking.
- Every picture is a full-colour scene in the chosen look. Test 2 shots before the batch.
- Text on pictures only for numbers and dates, names, map places and unseen terms. Never
  over a face (render.py places tags itself). 6-10 text cards per 2 minutes.
- Pick pictures by what they show, not by file number. Flow swaps them in batches.
- Never paste or ask for API keys or tokens in the chat. Keys go in `.env` files the user
  edits.
- On machines with 8 GB of RAM or less, keep ffmpeg at `-threads 3` (the scripts do) and run
  one heavy job at a time.
- Hindi and other non-Latin labels go through libass automatically; if a font lacks the
  script, set `STICK_FONT_COMPLEX` to a .ttf that covers it.
- For Claude usage: QC from grids, and suggest a new chat per new video.
