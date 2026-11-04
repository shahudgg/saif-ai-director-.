---
name: vox-doc-sound
description: Voiceover and sound design for Vox-style documentary explainers on any topic (narration over maps, cut-out PNGs, charts, archive). Makes the narration (Kokoro for English and other languages, Indic Parler-TTS for Hindi and other Indian languages), times every word, then places music, ambience and SFX on the spoken words and mixes to -14 LUFS. Ships with a free, commercially usable and shareable sound library (Stable Audio + Kenney CC0); users can add their own. Use for "sound design for my explainer", "add SFX and music", "make the voiceover", "mix the documentary", "Vox style sound", "Hindi narration", "BGM", "whoosh/click/map pin sounds".
---

# Vox-style documentary sound

Sound in a Vox-style explainer has one job: make the narration easier to follow.
Most moments get no sound at all. The few that do are quiet, land on the word
that names the thing, and come from one consistent palette. Talk to the user in
their own language.

```
scripts/kokoro_vo.py      English and other languages, local CPU          -> takes/NN.wav
kaggle/make_parler_job.py Hindi and Indian languages on a free Kaggle GPU -> takes/NN.wav
scripts/vo_join.py        takes + LINES.tsv -> VO.wav + VO.tsv (silence trimmed, your gaps)
scripts/align.py          VO.wav + VO.tsv -> WORDS.tsv (the time of every word)
scripts/index_sfx.py      your sfx/ and music/ folders -> CATALOG.tsv (level, peak time)
scripts/mix.py            cues.tsv + VO -> mix.wav (-14 LUFS, voice on top)
scripts/eleven_sfx.py     make sounds with the user's ElevenLabs key (--batch sfx/STARTER.tsv)
scripts/eleven_music.py   make an instrumental bed with ElevenLabs Music
sfx/StableAudio/, sfx/Kenney_CC0/  the bundled free library (commercial use + sharing allowed)
kaggle/make_sfx_job.py    free AI sound effects on Kaggle (Stable Audio, you own the output)
sfx/STARTER.tsv           36 tested ElevenLabs prompts (optional, paid plan)
music/README.md           music prompts that work, and free sources
```

Needs: ffmpeg (with libass), Python with numpy, soundfile, faster-whisper.
Optional: kokoro + espeak-ng (English VO), kaggle CLI + a Kaggle account (Hindi VO; no Hugging Face token needed),
an ElevenLabs key (to generate sounds/music).

## Step 0: the sound library

The skill ships with a **free library you may use commercially and share**:
- `sfx/StableAudio/<Category>/`: hundreds of sounds generated with Stable Audio
  Open, denoised and checked against the Vox rules (whooshes, transitions,
  paper and collage, maps, archive, data, money, time, tonal hits, topic
  foley, and 100+ ambiences of places in India and around the world).
  Licence: `sfx/StableAudio/LICENSE.md`.
- `sfx/Kenney_CC0/<Category>/`: 99 Kenney sounds, public domain (UI clicks,
  pops, pins, data tones, soft impacts, book and card foley, coins,
  pizzicato stings).

First run: `python scripts/index_sfx.py`, which builds `CATALOG.tsv` with
levels and peak times. Re-run it whenever files are added.

Licence note for the user: files from ElevenLabs, Pixabay or bought packs may
not be shared onward. The bundled StableAudio, Kenney and Gemini files may.

**Music is bundled too:** `music/Gemini/<mood>/` holds instrumental beds made
with Google Gemini (Lyria), levelled to -12 LUFS, free to use commercially and
to share (`music/Gemini/LICENSE.md`). Moods: explainer, explainer_lofi,
reflective, tension, data, question, reveal, mystery, hopeful, tech, indian,
history_serious, world, outro, and ambient pads (ambient_warm, ambient_tension,
ambient_space, ambient_mystery, ambient_nature, ambient_history, ambient_tech,
ambient_sad).

## When the library has no fitting sound: make it for the user

Search `CATALOG.tsv` first (category, file name). If nothing fits a cue, do not
silently drop the cue or use a wrong sound. Set up a source on the user's
machine and generate it, in this order:

1. **The user has an ElevenLabs key** (best quality, fastest).
   - Ask them to put `ELEVENLABS_API_KEY=...` in `.env` in the skill folder
     themselves. Never ask them to paste the key into the chat.
   - Key page: https://elevenlabs.io/app/settings/api-keys
   - Run `python scripts/eleven_sfx.py "<prompt>" --seconds <s> --cat <Category> --name <name>`
     for each missing sound, then `python scripts/index_sfx.py`.
   - Tell them once: on a paid plan they may use the sounds in commercial
     videos. On the free plan, non-commercial only. On any plan they may not
     share the files.
2. **No key: free, on Kaggle (Stable Audio Open).** The user owns the output
   and may use it commercially (yearly revenue under $1M).
   - Set it up with them, one step at a time:
     - `python -m pip install kaggle`
     - Kaggle API token: https://www.kaggle.com/settings → API → Create New
       Token, saved to `~/.kaggle/kaggle.json`. Phone-verify the account so
       GPU is allowed.
     - No Hugging Face token and no Kaggle secret: the job loads a pinned,
       ungated mirror.
   - Write the missing sounds to a TSV (`category, name, seconds, prompt`), run
     `kaggle/make_sfx_job.py`, then push.
   - Poll the status, download the output, move it into `sfx/StableAudio/`,
     then index.
   - Listen to or check every result before using it, and regenerate any
     that is bad.
3. **Quick manual fallback.** Freesound CC0 (https://freesound.org) or Pixabay
   (https://pixabay.com/sound-effects/). Put the files in
   `sfx/<Source>/<Category>/`, then index.

Music works the same way. Try the bundled `music/Gemini/` first, then a new
Gemini track (prompts in `music/README.md`). As a fallback, the user can
download from Pixabay Music (https://pixabay.com/music/) or the YouTube Audio
Library (https://studio.youtube.com → Audio Library) into `music/<mood>/`, then
index. With a key, `scripts/eleven_music.py` also works.

`index_sfx.py` stores a `level_offset` for each file and mix.py adds it, so the
gains below work for any file from any source.

## Workflow

1. **Script → LINES.tsv** (`id`, `text`, `gap`). One sentence per line. `gap` is
   the pause after the line: 0.3 inside a thought, 0.5-0.6 at a full stop,
   0.8-1.2 before a twist or a new act.
2. **Voice → takes/**
   - English, Spanish, French, Italian, Portuguese, Japanese, Mandarin:
     `python scripts/kokoro_vo.py LINES.tsv --lang a --voice am_michael`
   - Hindi and other Indian languages: `kaggle/make_parler_job.py` (its
     docstring walks through the setup). Good narrators: Aman, Kabir, Bhanu,
     Dinesh, Aakash. Rohit sounds robotic. Kokoro has Hindi but it is flat.
   - Any other language (German, Arabic, Russian, Korean, Turkish, …): no free
     voice ships with this skill. Say so up front and offer the user's own
     recording or their ElevenLabs key (paid plan for commercial use).
   - Never use Piper's Hindi voices or MMS-TTS for anything monetised: they are
     NonCommercial.
3. **Join**: `python scripts/vo_join.py takes LINES.tsv --out VO`
4. **QC the voice before anything else.** Listen to the first, a middle and the
   last line, or transcribe with Whisper and compare with the script. A line
   that is twice as long as its neighbours is usually a TTS glitch. Re-make
   only that take.
5. **Time every word**: `python scripts/align.py --vo VO.wav --lines VO.tsv --lang hi`
6. **Plan the sound FROM THE NARRATION** (rules below). Write `cues.tsv` using
   only files that are in CATALOG.tsv.
7. **Mix**: `python scripts/mix.py cues.tsv --vo VO.wav --lines VO.tsv --words WORDS.tsv --out mix.wav`
   Add `--dip <word>` for one held-breath moment (see Silence).
8. **QC the mix** (below), then mux: `ffmpeg -i video.mp4 -i mix.wav -map 0:v -map 1:a -c:v copy -c:a aac -b:a 192k -shortest out.mp4`

## cues.tsv

```
label   kind  file                              at        dur  gain_db  fade_in  fade_out
bed     loop  music/explainer/explainer_a.mp3   0         all  -23      2.5      5
morning loop  sfx/Ambience/village_morning.mp3  0         9.5  -14      1.5      1.2
pin1    one   sfx/Map/pin_drop_a.mp3            अरुणाचल   -    -19      0        0
year    one   sfx/PopClick/click_soft_a.mp3     w:1906    -    -19      0        0
ticks   loop  sfx/Time/clock_tick_loop.mp3      @07       8.8  -15      0.8      1.0
```

`at` is when it happens:
- a **spoken word** (`अरुणाचल`, `railway`): the first time it is spoken after the
  previous word cue. Keep rows in script order. `word+0.3` / `word-0.1` shift it.
- `w:1906` for a word that is itself a number, because a bare number means seconds
- `@07` for the start of line 07
- `12.4` for plain seconds

**Prefer word cues.** If the voice changes, re-run align.py and every sound
still lands on its word. One-shots are placed with their **peak** on the cue, so
a page turn lands on the word and not 7 frames late. The row labelled `bed`
is the music: it ducks under every SFX and under the voice.

## What gets a sound

Read each narration line and ask: what is the viewer looking at, and does a
sound make it clearer? Map the **word** to a sound only when the picture shows it.
Look up real files with `grep -i "<category or word>" CATALOG.tsv`. **Never
invent a filename.**

| The narration names... | Sound (folder) | Gain |
|---|---|---|
| a place, with a pin or map | soft pin drop on paper (Map) | -19 |
| a line drawn on a map (border, route), a cross-out | marker drawing a line (Map) | -20 to -21 |
| a map or the world appearing | map unfolding, soft zoom whoosh (Map, Whoosh) | -20 to -22 |
| a number, year or label appearing | soft UI click (PopClick) | -19 to -20 |
| a counter rising | quick ticking counter (Numbers) | -21 |
| an icon or picture popping in | soft pop (PopClick) | -20 to -21 |
| a decree, law, signature, approval | rubber stamp (Paper) | -18 |
| documents, files, paperwork | page turn, paper slide, paper stack (Paper) | -18 to -19 |
| a word highlighted, a collage piece torn or stuck on | highlighter, paper tear, sticker, tape (Paper, Stationery) | -20 to -21 |
| evidence, a dossier, an investigation board | push pin, string pulled tight, paper clip, stapler, folder (Stationery) | -19 to -21 |
| a bar, line or dot in a chart | soft rising tone, digital blip (Data) | -20 to -21 |
| history, an old era begins | film projector, camera shutter (Archive) | -20 to -21 |
| a time of day, a clock, waiting | clock chime, ticking (Time) | -15 to -20 |
| a vehicle that is SHOWN (train, plane, car, ship) | that vehicle (Transport) | -17 |
| a new act or chapter | soft long whoosh (Whoosh), and only here | -19 |
| the twist or reveal | reverse swell or soft riser; its peak lands on the word (Reveal) | -19 |
| a key fact that reframes everything | soft ding, or a low soft thud, rarely (Reveal, Emphasis) | -20 to -21 |
| the final line | soft outro chime (Reveal) | -19 |

**Tonal accents** (Tonal: a soft piano note, a pizzicato pluck, a marimba hit,
a string swell) are musical hits in the same family as the suggested music. Use
them instead of a whoosh or a thud on a key fact, about 3-6 per video. They
sound like part of the score.

Ambience: one loop per **place** the story is in, 6-15 s, -14 to -17, faded
in and out.

### By topic

| Topic | Spot sounds | Ambience / bed |
|---|---|---|
| Economy, money, business | coins, cash register, cash counting, stock ticker, card payment | market, office, city street |
| Politics, law, government | gavel, stamp, ballot drop, microphone tap, applause | parliament hall, distant protest, government office |
| History, empire, war | telegraph, marching boots, distant siren/cannon, bell toll, radio tuning, film projector | distant battle (very low), archive room, newsroom |
| Science, tech, internet, AI | data beeps, soft glitch, notification ping, power-up, camera focus | server room, office |
| Space, nuclear, labs | satellite beeps, rocket launch (distant), geiger counter, lab bubbling | space drone, server room |
| Health, medicine | heart monitor, heartbeat, pill rattle | hospital ward |
| Climate, nature, disaster | distant thunder, storm, fire, earthquake rumble, ice cracking | forest, jungle night, snowy mountain wind, ocean, desert wind, rain |
| Industry, jobs, infrastructure | machinery | factory, construction site |
| Travel, trade, migration | ship horn, jet, train, plane | airport, harbour, railway station, metro, traffic |
| Society, cities, villages, education | crowd murmur | city street, cafe, market, village morning/evening, classroom, playground |
| Farming, food | tractor, grain pouring, cow bells, rooster | farm fields, village |
| Religion, culture, festivals | conch, temple bell | temple courtyard, river ghat |
| Sport | bat hit, referee whistle | stadium crowd |
| Time, schedules, deadlines | clock chime, watch tick | clock ticking loop |
| Tension, crisis (any topic) | none | tension drone (very low) |

If a needed sound is not in the catalog, say so. Offer to make it with
`scripts/eleven_sfx.py "<prompt>" --seconds <s> --cat <Category> --name <name>`
(user's key), or leave the moment silent. Do not substitute a wrong sound
without saying so.

## Rules that keep it "Vox" and not YouTube noise

Priorities, in order: (1) the words are understood, (2) story meaning,
(3) emotion, (4) sync on physical events, (5) contrast and pacing, (6) one
palette. Never break 1 to serve anything below it.

- **Not every cut gets a whoosh.** Whoosh only on act changes, 3-5 per video.
- **One accent per beat**, at most about one every 3-4 seconds. Fast, dense
  narration means fewer SFX, not more.
- **One palette per video**: whooshes and clicks from one family/pack, so they
  sound related. Rotate variants (`_a/_b/_c`). After about three similar
  sounds in a short span, skip one or switch.
- **No trailer grammar**: no braams, no booms, no cartoon or meme sounds. An
  impact is a soft, low thud, and it is rare.
- **Don't place a bright sound on the keyword itself** if it masks the word;
  land it a hair after (`word+0.1`).
- **The first 5 seconds**: the hook line lands dry or over one quiet ambience.
  The music enters under it at -23 or lower, never with a bang.

## Music

- One theme for the whole video: keep the instrument family (for example soft
  piano + pizzicato + marimba) and change only the mood per act.
- A typical act structure: hook dry → explainer → reflective (history) →
  tension (the problem) → data (the world) → question → reveal (twist) →
  outro sting. Each act gets its own `loop` row with a 2-4 s fade in and out;
  only one row is labelled `bed`.
- The bed sits about 20 dB under the voice (-23 in cues.tsv, after the catalog
  offset). mix.py's ducking does the rest; do not push the bed up to make it
  "exciting".
- Change the cue when the story changes, on a phrase end, with a fade. Not
  every 12 seconds.
- Instrumental only under narration. No vocals and no busy drums.
- Getting music: `music/README.md` (prompts that work, free sources, and
  `scripts/eleven_music.py`).

## Silence

Silence is an event. Use **one** held breath per video, on the big question
or right before the twist: `--dip <word>` dips the bed smoothly around that
word. Before the reveal line, let the SFX stop for a second. A section that
just had a dense list of facts gets air before the next one.

## Levels and mix QC

mix.py:
- brings every voice to -22 LUFS first, so a loud TTS voice cannot push the
  music down
- applies each file's catalog level offset
- ducks the bed under SFX and everything under the voice
- finishes at -14 LUFS integrated, true peak -1.5 dBTP

Check before handing over:
- `ffmpeg -i mix.wav -af ebur128=peak=true -f null -` → I ≈ -14, peak ≤ -1.5
- Listen to 3 spots on a phone speaker: the hook, the busiest section, the twist.
  If a word is hard to hear, lower or move the SFX near it.
- If music and SFX are inaudible in the pauses, raise the bed 2-3 dB. If you can
  hum the music while following the voice, lower it.

## Licences (tell the user once)

- Their videos are theirs to publish. **Sound and music files from ElevenLabs,
  Pixabay or bought packs may not be shared or resold as files**, even inside a
  kit like this one.
- ElevenLabs sounds and music may be used commercially only on a paid plan.
- Kokoro and Indic Parler-TTS are Apache 2.0, free for commercial use.

## Output to the user

Give: the cue sheet (what plays on which word, and why), what you deliberately
left silent, any sound you had to generate or substitute, and the loudness
check numbers. Full absolute paths for every file.
