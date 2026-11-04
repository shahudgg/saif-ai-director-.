---
name: vox-doc
description: Make a complete Vox-style documentary explainer video on any topic, end to end. It covers one-time tool setup, the script, the voiceover, word timing, a scene plan, bespoke cut-out PNG art from Google Flow, rough maps with pins, clocks and charts drawn in code, labels on everything, sound design, and the final MP4. Works in Hindi, English and many other languages. Use when the user says "start", "continue", "setup", "vox doc", "documentary banao", "explainer video", "video banao", names a topic for a video, or opens the vox-doc zip.
---

# Vox-style documentary, end to end

You make the whole video for the user. They give a topic. You do everything else
and show them checkpoints along the way.

**Talk to the user in their own language**, the one they write in (often
Hinglish). Keep it simple. Commands, file names and file contents stay in
English. Always give full absolute paths. Give a `https://` link only when you are certain it is right (one written in these files, or one a tool printed); never guess a URL. If unsure, give the menu path or let the tool say it.

`<skill>` below means this skill's folder: `~/.claude/skills/vox-doc/`. On
Windows that is `C:\Users\<me>\.claude\skills\vox-doc\`.

The companion skill **vox-doc-sound** (`~/.claude/skills/vox-doc-sound/`) does
the voice and the sound. Read its `SKILL.md` when you reach those stages.

Read `<skill>/PLAYBOOK.md` fully before planning any video. Its Part 1 rules are
what make the film understandable. Follow them without exception.

## Every time this skill starts ("start", "continue", a topic, anything)

1. **Setup check.** Read `<skill>/SETUP_STATUS.md`.
   - If it is missing, or any required row is not ✅, run or resume
     `<skill>/SETUP.md` from the first unfinished step. Then come back here.
   - Prerequisite: Flow Connector, installed by the user from the extension's own
     setup prompt. At setup only check that it is installed (`flow-auto --version`).
     If `flow-auto` is missing, stop and ask the user to run that extension prompt
     first, then say "continue". "Extension not connected" is NOT a setup failure:
     it only means no Flow tab is open right now. Check the connection at stage 5.
2. **Find the project.**
   - Projects live in `~/VoxDoc/<topic-slug>/`.
   - "continue" with no topic: open the `PROGRESS.md` modified most recently
     under `~/VoxDoc/`. Tell the user which project and which stage you are
     resuming.
   - A new topic: create `~/VoxDoc/<topic-slug>/` (short, lowercase, no
     spaces) and a `PROGRESS.md` in it.
   - No topic yet: ask three things in one short message, and suggest no defaults:
     - **Topic**: their own, or "Want me to suggest a list of topics?" If yes,
       give 8-10 Vox-style topics with a one-line hook each, mixed between
       India and the world. They pick one or ask for more.
     - **Language** of the narration: Hindi, English, Hinglish, **or any other
       language**. Always show "other" as an option. Say honestly which
       languages have a free voice (vox-doc-sound SKILL.md, step 2).
     - **Length**: the user decides. Offer choices such as 1, 3, 5 or 10
       minutes, or any length they type, and do not pre-select one.
3. **Work only from inside the project folder.** Run every script from there,
   for example `python <skill>/scripts/render.py plan`. The scripts read and
   write the current folder.
4. After each stage, update `PROGRESS.md`:
   - the stage name, ✅, and the key decisions (voice, length, language)
   - what the user approved
   That way "continue" in any new chat picks up at the right stage.

## Stages (show the checkpoint, then go on)

| # | Stage | Do | Checkpoint shown to the user |
|---|---|---|---|
| 1 | Script | `SCRIPT.md`: structure and rules in PLAYBOOK Part 3 §1. About 15 lines per minute of the chosen length, facts checked. Also write `LINES.tsv` (`id, text, gap`) | **Wait for the user's OK** on the script |
| 2 | Voice | vox-doc-sound skill: Kokoro (English and others), Indic Parler on Kaggle (Hindi and Indian languages), the user's ElevenLabs key, or their own recording. Then `vo_join.py` gives `VO.wav` and `VO.tsv` | Play 2 lines; the user picks or approves the voice |
| 3 | Word timing | `python <skill>/scripts/align.py` gives `WORDS.tsv` | Word count, and any line that aligned badly |
| 4 | Scene plan | `SCENES.tsv` and `ELEMENTS.tsv` per PLAYBOOK Part 3 §4-5. Copy the style of `<skill>/example/` | A short table: line, what is on screen |
| 5 | Pictures | First connect Flow: start `flow-auto bridge start` in the background, then `flow-auto doctor --site flow`. If a line fails, tell the user exactly what doctor says (its words, its address) and wait for "continue". Never write your own Flow URL. Then `python <skill>/scripts/elements.py` (Flow on magenta, keyed to `elements/cut/`). Late additions use `--new-project` | A contact sheet of all cut-outs. Regenerate bad ones before going on |
| 6 | Maps | For each map PNG, measure places on a gridded copy and write `PLACES.tsv` (`map, place, u, v`). See `<skill>/example/PLACES.tsv` | The map with pins drawn |
| 7 | Shots | `render.py plan` (check the times), then `render.py shots`. Then **you** QC it yourself: make a contact sheet of every scene's last frame, read it, fix overlaps, cut-off labels or wrong pictures, and re-render only those scenes. Do not show it to the user or wait for them | One line: "all N scenes rendered, fixed X". Go straight on to sound |
| 8 | Sound | vox-doc-sound skill: `cues.tsv` on words, then `mix.py … --out mix.wav` in the project folder. A missing sound is generated (its SKILL.md says how) | The loudness numbers; play the hook |
| 9 | Master | `python <skill>/scripts/render.py master` gives `<project>.mp4` | Open the video for the user, and give the full path |

## Rules that are easy to forget

- Every spoken noun gets its own bespoke PNG. Never reuse one generic picture.
- Draw data in code: clocks, numbers, bars and lines. Never ask AI to draw them.
- Every named place gets a map with a pin. Rough maps are fine; never drop a map
  for accuracy.
- Put a label on every element. Hold every reveal at least 0.7 s before the cut.
- Never paste or ask for API keys or tokens in the chat. Keys go in `.env`
  files that the user edits.
- On machines with 8 GB of RAM or less, keep ffmpeg at `-threads 3` (the
  scripts already do) and run one heavy job at a time.
- Labels use the font in `VOX_FONT`, or the OS default with Devanagari. For
  other scripts (Arabic, Tamil, and so on) set `VOX_FONT` to a font that covers
  them.
