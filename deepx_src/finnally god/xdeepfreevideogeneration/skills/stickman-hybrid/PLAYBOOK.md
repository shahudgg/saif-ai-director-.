# Stickman explainer playbook

These are the rules and the method behind stickman documentaries in the style of the big
"ancient humans" and stick-figure explainer channels. The method was worked out by studying
nine reference videos (14M, 1.2M, 855K, 385K views and others) and then making test films
until the reviewer approved them.

---

## Part 1: The rules (each one came from a measurement or a rejection)

1. **Still pictures, not animation (motion only from stickers).** Over two-minute stretches, the reference videos are
   92-99% frozen frames: no pan, no zoom, no AI video. What keeps viewers watching is a new
   picture every 2-3 seconds that shows exactly what is being said. Never add Ken Burns
   moves or video-model clips. In this pack the stills carry cut-out stickers that pop in on
   the words (§9); that is the only motion.

2. **Pace like the viral video, and measure it.** `render.py --pacing` lists every hold where nothing
   changes on screen: over 2 s in the first 30 s, over 3.5 s after that. Fix every hold with a beat
   on a spoken word before rendering:
   - a cut to another picture
   - a sticker
   - a counter or a tag
   - a "no" sticker for "no fridge, no freezer, no packet" (see §9)

   Each beat must be something the line names; never add one just to fill time. A reviewer found
   12-16 s spans with one frame and asked for "zyada frame, lekin script ke acc". The re-paced Hindi
   film went from 60 to 96 changes, with 24 in the hook.
   The viral reference:  The 14M-view reference has 292 shots in 11 minutes:
   - first 30 s: one picture every ~1.2 s
   - 30-120 s: one every ~1.8 s
   - after that: one every ~2.6 s

   Its median shot is 2.1 s. Aim for a mean of 2.0-2.6 s, a faster hook, and no shot
   longer than ~6 s.

3. **The picture shows the sentence, literally.** Plan every scene from the voiceover line it
   serves:
   - "Rice. Bread. Curry." means three quick close-ups.
   - "A chimpanzee spends six hours chewing" means a chimp chewing, with a clock.

   A shot list written before the narration produced films where nothing matched. Cut on the
   spoken word (WORDS.tsv), never on a guessed time.

4. **One cast, one look, all video long.**
   - Make one character sheet.
   - Split it into one reference per character (`pictures.py refs`).
   - Attach the right reference to every shot that has that character.

   With the whole sheet attached, Flow pasted the sheet's line-up into the scene, and drew
   the wrong character.

5. **Every picture is a full, colourful scene.**
   - White or empty backgrounds were rejected.
   - A whole film in golden ochre was "too brownish".
   - Pick the look by topic (`looks/README.md`) and keep its palette line in every prompt.
   - Before the batch, test 2 shots and look at them.

6. **Text only where the picture cannot say it.** The reference channels let the picture
   carry the line. A label on every shot was rejected ("they don't use captions, remove
   them"). Put text only on:
   - a number or date: "~6 HOURS A DAY", "1498"
   - a person's name: "RICHARD WRANGHAM | HARVARD"
   - a place on a map: "NORTHERN ITALY"
   - a term that cannot be drawn: "BEESWAX", "DATUN (NEEM)"

   That came to 11-13 tags in a 2-minute film. In Hindi text, put digits and Latin punctuation on a line of their own (`पीटर ड्यूरंड|1810`) and leave out colons: in the handwritten Hindi font a `1` reads as `।` and a `:` as `ः`. A line that is only digits and Latin letters is drawn in the Latin font. Text is drawn in code, so spelling is always
   right, and it is placed automatically in the quietest corner, never over a face. Tags
   placed by hand ended up "in the characters' mouths".

7. **Data is drawn in code.** Percentages go in a bar chart (`bars:RAW=51,COOKED=91`). Text
   cards ("AND HERE'S THE STRANGE PART...") work as beats; use 6-10 in 2 minutes. Cards and
   charts cost no picture credits.

8. **The hook follows a fixed formula.**
   1. Start with the viewer's normal day: "This morning, you picked up a plastic stick..."
   2. Rewind: "Rewind fourteen thousand years."
   3. Ask the big question.
   4. Answer it with escalating discoveries and a twist.
   5. End by sending the viewer back to their own life, with no "subscribe" ending.

9. **Facts are checked, and doubt is said aloud.** Use numbers and names that come from
   sources. Phrase debated ideas as "some scientists think... others say the evidence isn't
   strong enough yet". Never invent studies, dates or quotes.

10. **Flow returns pictures under the wrong number.** In every batch, 2-6 pictures came back
    swapped. One file was named "sofa" but showed the savanna animals. Always look at
    `review.jpg` and fix swaps in `PICK.tsv`. Never trust file numbers.

---

## Part 2: The pipeline

```
topic + language + length
  └─ SCRIPT.md ──> LINES.tsv (id, text, gap)                    [user approves the script]
        ├─ in parallel ─> STYLE.txt + cast ──> character sheet ──> pictures.py refs
        │                   └─> SHOTS.tsv ──> pictures.py submit 1 N (test) ──> submit (rest)
        └─ in parallel ─> voice (vox-doc-sound) ──> VO.wav + VO.tsv ──> align.py ──> WORDS.tsv
  WORDS.tsv + SHOTS.tsv ──> SCENES.tsv (cue on words, text only where needed)
  downloads ──> pictures.py review ──> PICK.tsv ──> pictures.py pick ──> img/sNN.jpg
  cues.tsv ──> mix.py ──> mix.wav
  render.py ──> <project>.mp4 ──> pictures.py qc ──> qc.jpg (Claude checks it, fixes, re-renders)
```

---

## Part 3: Step by step

### §1 Topic and script

**Topic list,** if the user wants one:
- Give 8-10 titles that each carry one sharp question ("How Did Ancient Humans Clean Their
  Teeth?", "Why Are Humans the Only Animals That Cook?").
- Each needs a visual journey: from today, to the past, through discoveries, and back.
- Hindi audiences: give the title in both languages, for example "इंसानों ने देश क्यों बनाए?
  Why Did Humans Create Countries?".
- Proven niches: ancient humans' daily life, survival, food, the body, inventions, "what if".

**SCRIPT.md:** conversational and spoken.
- Address the viewer as "you", "imagine", "here's the strange part".
- Vary sentence length, with short punch lines after longer ones.
- Every paragraph answers one question and opens the next (a curiosity loop).
- Structure: hook → big question → context → first discovery → problem → bigger discovery →
  twist → bigger picture → modern ending. No timestamps, no headings in the narration.

**Length:** English Kokoro speaks about 155-165 words a minute. Hindi narration (Indic Parler,
Aman) runs about 25% longer for the same meaning: a 2-minute English script became 2:47 in
Hindi. Write about 25% fewer words for Hindi: the voice is never sped up (§4). Always join Parler
takes with `vo_join.py`; if it still runs long, cut words, do not go faster. Write to the user's chosen length. After the voice is made, cut lines if it runs
long.

**LINES.tsv:**
- One sentence or short beat per row: `id  text  gap`.
- Short lines give more cut points.
- `gap` is the silence after the line:
  - 0.15-0.3 inside a thought
  - 0.4-0.6 at a full stop or a question
  - 1.0-1.2 after the last line

Example: `example/teeth/LINES.tsv`.

### §2 Look and cast
1. Pick the look from `looks/README.md`. Write `STYLE.txt`: line 1 `look: <name>`, then the
   look's STYLE paragraph, adapted to the topic.
2. Decide the cast from the script:
   - "you" (the modern person)
   - one or two story characters, such as a caveman, a farmer or a scientist

   Background people are not cast. Costumes such as an Egyptian or a Ming craftsman are
   changes written into a shot's prompt, on top of the same face.
3. Character sheet:
   - One Flow text-to-image with `--outputs 2`, using the look's sheet prompt and
     `--folder stick_<project>_sheet --wait`.
   - Keep the cleaner one. All views must be the same person, with no text.
4. Split it: `python <skill>/scripts/pictures.py refs <sheet.jpg> --names modern,caveman`.
   - Characters must not be cut in half.
   - If they are not evenly spaced, pass `--cuts 0.51` (a fraction of the width).
   - Look at each `img/ref_*.jpg`.

### §3 SHOTS.tsv (pictures)

**Columns:** `id  ref  prompt`.
- `ref` is `-` for no character, a cast name, or `sheet` for a shot with the whole cast.
- Write the prompt as one literal scene: who does what, where, with what, plus the light and
  colour of that moment.

**Budget:**
- About one picture per 3 seconds of film (2 min = 38-44 pictures), plus 6-10 text cards.
- Reuse a picture when the narration stays on it:
  - a second scene with a tag added
  - a bar chart that grows from one value to two

**Prompt rules:**
- Objects and food: add "no people", or random background characters appear.
- Maps: "simple cartoon map of <region>, no text, no borders, a red location pin on
  <place>". Rough is fine; text is added in code.
- A costume change: "The modern man as a professor, now wearing round glasses and a tweed
  jacket...". The face stays from the reference.
- Never ask the image for words, numbers or labels. `pictures.py` adds "no text" to every
  prompt.
- Model: Nano Banana 2 (Flow labels it "Nano Banana 2.1") for scenes and stickers, never Pro.
  `pictures.py` uses it by default (`STICK_MODEL`).
- Ask for clean, clear pictures: the project's STYLE.txt ends with "Crisp clean linework,
  clear readable composition, sharp high-quality detail, no blur, no muddy colours."

**Order:**
1. `pictures.py submit 1 <a character shot>` (2 shots). Look at both. If the look or the cast
   is wrong, fix STYLE.txt or the refs now.
2. `pictures.py submit <the rest>`. It submits everything without waiting, in batches of
   `STICK_BATCH` (default 4).

### §4 Voice: in parallel with the pictures
Use the vox-doc-sound skill.

**Which engine:**
| Narration language | Engine | First speaker |
|---|---|---|
| Hindi, Hinglish | **Indic Parler-TTS** on Kaggle (`kaggle/make_parler_job.py --speaker Aman`) | **Aman, always first** |
| Bengali, Marathi, Tamil, Telugu, Gujarati and other Indian languages | **Indic Parler-TTS** on Kaggle | a native speaker of that language (see the vox-doc-sound docs) |
| English, Spanish, French and other non-Indian languages | Kokoro (`kokoro_vo.py --speed 1.0`), local | `am_michael` |

- Never use Kokoro for an Indian language: its Hindi sounds flat.
- Hindi fallbacks, only if the user wants a different voice after hearing Aman: Kabir,
  Bhanu, Dinesh, Aakash. Rohit sounds robotic.

**Then:** `vo_join.py` gives `VO.wav` and `VO.tsv`, and `align.py --lang <code>` gives
`WORDS.tsv`. The user approves the voice from two lines.

**Pace and pauses (approved by ear, 2026-10-20):** Indian languages: make_parler_job.py's defaults (`natural` description, seed 11) and NO speed-up; `vo_join.py` adds +0.3 s to every pause. English and other languages: Kokoro `--speed 1.0`, every pause +0.35 s (~3.0 words/s). Never pass `--tempo`: speeding Parler up 1.10-1.25x is what made every first Hindi film sound robotic. A line vo_join flags `RE-MAKE` gets voiced again with another seed (`--only NN --seed 12`), never sped up. The full rules are in the vox-doc-sound
SKILL.md, "Narration that sounds alive".

**Kaggle queue: reassure the user, ask, never decide alone.**

Sometimes `kaggle kernels status <user>/<job>` says `QUEUED` for 30 minutes to 2 hours. **This
is normal, not an error.** Kaggle's free T4 GPUs are shared by everyone, and the queue is
longest in the Indian evening and night (the US daytime). Now and then a job also sits in the
queue with no visible reason. Nothing is wrong with the job, and nothing is lost.

1. **Tell the user at once, calmly, in their language**, so they don't worry. In Hinglish,
   for example: "Kaggle ka free GPU abhi busy hai, isliye Aman ki awaaz queue mein hai. Yeh
   normal hai, koi error nahi. Tab tak main pictures ka kaam kar raha hoon."
2. Check `kaggle kernels list --mine`. Any other job of the user's that is `RUNNING` takes one
   of the 2 GPU slots; mention it. Push only ONE voice job at a time. Two queued copies do not
   start faster.
3. Keep working on everything that does not need the voice: pictures, review, PICK.
4. Check the status every ~10 minutes, without asking the user each time.
5. After ~60 minutes of `QUEUED`, ask the user in one message:
   - **a) Keep waiting.** It usually starts within 1-2 hours.
   - **b) Push the same job once more.** Run `kaggle kernels push -p <job folder>`. A stuck
     job sometimes starts at once after this. It is harmless, but not guaranteed.
   - **c) Push it in the Indian morning,** when the queue is shortest. Meanwhile the
     pictures can be finished.
   - **d) Check the weekly GPU quota** (about 30 h a week) on https://www.kaggle.com/settings.
     When it is used up, the job cannot start until the quota resets.
   - **e) Run Parler on this computer.** Give the honest cost for THIS machine, using its
     RAM and GPU from SETUP_STATUS.md:
     - strong machine: a separate `parlerenv` (SETUP Step 6)
     - weak machine: may be very slow, may swap, or may crash

     The user may still choose it.
   - **f) Their own recording, or their ElevenLabs key.**
6. Do what the user picks. Never fill the wait with another TTS voice, and never start a local
   install without the user's OK.

### §5 SCENES.tsv: cut on the words
Columns are `cue  img  text  pos  extra`:
- `cue`: `line.word`, for example `14.5` is the 6th word of line 14 (counting from 0).
- Leave `pos` empty; render.py chooses the spot.

**How to cut:**
- Read each line in WORDS.tsv and cut when the line names a new thing.
  Example: "Bread(10.0). Porridge(10.1). Grain(10.2)" gives three scenes.
- **Hook** (first ~30 s): cut on every noun.
- **Later:** cut on each new idea, at most ~6 s per shot.
- **Text cards:** `card` with the words in `text`, using `|` for a new line. Use them for
  beats, questions and twists.
- **Reusing one picture:**
  - add a tag on its second scene, on the word that names the number
  - two consecutive rows with the same picture and the same text are one scene; merge them
- **Bar charts:** `extra` = `bars:RAW=51,COOKED=91`. The picture stays; the chart sits on
  the right.

Example: `example/teeth/SCENES.tsv` (51 scenes, 2:10, 13 tags).

### §6 Collect the pictures
**Theme consistency comes first.** Every picture of a film must match that film's own chosen
look (STYLE.txt): the same detail level, brightness and palette. Do not add a fixed style
sentence to every prompt; the look differs per topic.
- In `review.jpg`, look for an outlier that does not match the others and regenerate only that
  shot. A reviewer spotted one dense, detailed jungle shot among flat bright ones ("theme se
  match nahi kar raha").
- Fix it by describing that one scene more simply, in the same terms as its neighbours.

1. `pictures.py review`: look at `review.jpg`. Each tile shows the download's number.
2. For every picture that belongs to another shot, write it in `PICK.tsv` (`shot  file`).
3. If a picture is wrong, regenerate only that shot: `pictures.py submit <id>`. A rewritten
   prompt keeps the same id.
4. `pictures.py pick` copies the pictures into `img/sNN.jpg` and warns about byte-identical
   duplicates.
5. LaMa erases the Flow sparkle **in parallel**:
   - Right after the first `pictures.py submit`, start
     `python <skill>/scripts/delama.py --watch` in the background. It cleans every download
     the moment it lands, about 3 s each, into a cache keyed by the file's content. Flow's
     mispaired numbers therefore do not matter.
   - After `pictures.py pick`, run `python <skill>/scripts/delama.py`. It only copies the
     cleaned pictures into `img/clean/`, so it is instant. render.py uses them.
   - Do not render or run `align.py` while the watcher is still running on a machine with 8 GB
     of RAM or less. Whisper alignment died with `mkl_malloc: failed to allocate memory` when
     both ran together. Stop the watcher first (`delama.py` without --watch finishes the rest),
     and always check that WORDS.tsv was actually written.
   - If LaMa was skipped at setup, render.py's smooth fill is used instead.

### §7 Sound
Use the vox-doc-sound skill: write `cues.tsv` on words, then `mix.py`. Keep it light:
- a music bed at -23: the `explainer` mood for most videos, `explainer_lofi` for cosy ones,
  `mystery` or `tension` for `night`. Keep `history_serious` for truly grave topics: under a
  curious explainer it made the narration sound flat
- about 10 one-shots in 2 minutes, only where the picture shows the sound:
  - a film rewind on "Rewind"
  - a soft paper slam on a twist card
  - fire crackle on fire
  - a quiet paper "card-place" (`sfx/Kenney_CC0/Paper/card-place-1/2.ogg`, gain -25) on a map pin
    or a sticker, sparingly
  - NEVER pops, plucks, clicks or pizzicato jingles (`PopClick/*`, `Tonal/jingles_*`,
    `Map/b5_country_pop_*`) on stickers or beats. A reviewer called the pop sound "bahut bekar".
  - Not every sticker needs a sound. A run of stickers (a list) gets one sound on the first

Silence is fine. If a sound is missing, vox-doc-sound says how to make one.

### §8 Render and check
1. `python <skill>/scripts/render.py`. It uses mix.wav if present, else VO.wav. The look
   comes from STYLE.txt, and it writes `<project>.mp4`.
2. `python <skill>/scripts/pictures.py qc`, then open `qc.jpg` yourself. Check:
   - pictures in the right order
   - no tag over a face
   - no Flow sparkle left (delama.py, or render.py's fallback fill)
   - no leftover sheet line-ups
3. Fix the problems, re-render, and only then show the user.

### §9 The hybrid look (the default of this pack): stills + cut-out stickers

**Three rules from the reviewer's last round:**
1. **The scene picture comes first; never shape it for a sticker.** Write SHOTS prompts as full,
   natural scenes, the way the approved `food` film was made.
   - Stickers go to the quietest spot the picture already has, such as sky, ground or a wall
     (`auto`). render.py keeps them off faces, white stickman bodies, skin and the centre.
   - When a picture has no calm spot, put that line's stickers on a plain collage card right
     after it.
   - A test that forced empty left/right/top areas into the pictures (the `space` column) was
     rejected: "left right center wala aacha nahi lag raha". Leave `space` empty. It still exists
     in pictures.py but is not used by default.
2. **Split the work: the scene shows the place and the action; the line's named objects are
   stickers.**
   - Write SHOTS prompts without the small objects the line names. Example: "the caveman stands
     on the bank staring across the river", without "a fruit tree and fish".
   - Put those objects in ELEMENTS: fruit, fish, rope, a kangaroo.
   - They then pop into the scene's quiet spot on the word, and every main frame gets 1-2 related
     stickers.
   - When Flow paints the object into the scene anyway, a sticker of it would only repeat it.
     That is how a film ends up with few stickers; the reviewer asked for more and for no repeats.
   - Maps get a sticker of what the line says happened there: a kangaroo on Australia, the
     dugout on the Netherlands.
3. **More stickers, all related.** About one sticker per scene picture, plus collage cards with
   2-4 stickers. A film with few stickers "felt boring". Every sticker shows something the line
   names that the picture does not already show.
4. **Text: none on scene pictures.** On-screen text lives only on the big beat and question cards
   ("SO HOW DID PEOPLE TELL TIME?") and on collage cards, in English even for a Hindi narration.
   No tags, labels or counters on scene pictures.
This is the stills film with Vox-style cut-outs added on top. It has the same script, voice
and scene pictures. A small set of sticker PNGs then pops in on the spoken words:
- objects such as the toothbrush, the beeswax or a flint
- character poses
- animated counters ("0 → 14,000 YEARS AGO")
- tags
- now and then a paper collage beat, for example three toothbrushes on a timeline:
  1498 → c. 1780 → 1938

This pack always makes it. A reviewer approved it as the main style.

1. **ELEMENTS.tsv** (`id  ref  object`): about 1 sticker per 6-8 seconds. Each row is one
   object, or one character pose (ref = a cast name). Write what the narration names but the
   picture does not already show large.
   - **Never a sticker that repeats the picture.** No match sticker on a match-striking scene,
     no grass nest when the caveman already holds one.
   - **Never a decoration on a word that does not name it.** No spark on "garam".
   - Where no sticker truly fits, fill the hold with a CUT to a picture the line describes
     ("kabhi bujhne nahi diya" cut to the man guarding the fire). A reviewer called repeated
     and unrelated stickers out.
2. `pictures.py elements 1 4` to test two, then `pictures.py elements` for the rest. They are
   generated on magenta, 1:1.
3. `pictures.py cutouts` keys them to `elements/cut/NN.png`. Look at `cutouts.jpg`. Flow swaps
   these too: fix them in `ELPICK.tsv` (`shot  file`), then delete `elements/cut` and run
   cutouts again.
4. **SCENES.tsv overlay rows.** A row whose cue starts with `+` belongs to the scene above it
   and pops in on that word:
   - `+15.5  elements/cut/10.png  tiny scratch marks  auto,0.4  pop`
   - `+06.1  count  14000|YEARS AGO  0.7,0.5,0.42  rise`
   - `+03.0  tag  2 MINUTES  auto,0.3  pop`
   - A paper collage beat is a scene row with `card` and no text, plus overlay rows with
     fixed `x,y,h`.
   - Motions: `pop` (default), `slideL`, `slideR`, `rise`.
   - **"No X" moments** ("no smoke, no fire", "no fridge"): generate the sticker with the sign
     already inside it. A red X drawn in code looked cheap to a reviewer; do not use `pop+x`.
     The ELEMENTS row:

     "<object>, with a bold red prohibition sign drawn directly over it: one thick solid red circle
     and one thick red diagonal slash, flat red with a thin dark outline, NO white outline, NO
     white border, NO sticker edge around the sign or the object, the magenta background shows
     through inside the circle around the object"

     In SCENES use `pop+plain`, so render.py adds no white sticker border. A white rim and a black
     and white stroke looked wrong on these. Keep them small, 0.32-0.36 high, close together near
     the centre; big ones looked odd.
5. `render.py` sees the `+` rows and renders frame by frame at 30 fps (about 1.5 minutes for a
   2-minute film). `auto` picks the quietest free spot.
   - Big white stickman heads are counted as busy, because plain edge detection called them
     empty and a sticker landed on a head.
   - Auto placement prefers the corners and the top, and keeps off the centre, where the main
     element usually is. Auto stickers are at most 0.34 of the frame high (default 0.3), and tag
     text is small (70 px; 60 px under a sticker). A reviewer found bigger text and centred
     stickers covered the main subject.
   - Two stickers in one scene never overlap: render.py measures each one with its label and
     pushes them apart, shrinking them if they cannot fit.
   - After `pictures.py qc`, pin any sticker that still sits near a face to a fixed `x,y,h`.
   - Flow also swaps files ACROSS folders: a scene picture once landed among the stickers and a
     sticker among the scenes. Move such a file into the right folder, renamed to the right
     number, then pick and cut out again.
6. **Re-voicing in another language:**
   - The new voice is usually longer (Hindi ran about 13-25% longer). Keeping the same beats
     stretches every hold, so run `render.py --pacing` and add beats on the new language's words
     until no hold is left.
   - Copy `img/`, `elements/`, `STYLE.txt`, SHOTS and ELEMENTS, then translate LINES.tsv line by
     line, keeping the ids.
   - Make the new voice and WORDS.tsv (`align.py --lang hi`).
   - Rewrite only SCENES.tsv: cues on the new words, text in the new language.
   - No new pictures are needed. See `example/food_hi`.

Examples: `example/food/` (17 stickers, 28 overlays, 37 scenes), `example/food_hi/` (the same
film in Hindi), `example/teeth_hybrid/` (20 stickers, 29 overlays, 47 scenes).

---

## Part 4: Flow, as it actually behaves

- **Swapped pictures** happen in every batch (see rule 10). Fix them with PICK.tsv.
- **Repeated submissions.** One extension build re-submitted the same prompt up to 4 times
  when it missed a finished picture. The duplicates arrive as "N - name (1).jpg";
  `pictures.py` always takes the first.
- **"We noticed some unusual activity"** refuses one prompt and pauses the queue 5-10
  minutes on its own. When the queue is empty, re-submit only those ids. Never retry at
  once.
- **After `flow-auto stop` or `queue clear`, wait 1-2 minutes before submitting again.**
  Submitting at once drew a warning.
- **`--new-project` on its own can leave Flow sitting on the project gallery.** If nothing
  starts within a few minutes:
  1. `flow-auto stop --site flow`, then `flow-auto reload-extension --site flow`.
  2. Test one shot without `--new-project`.
  3. Submit the rest.
- **The plan reads "free" and the queue is PAUSED:** the extension's own side panel must be
  open, or the extension licence must be active. Ask the user to open the panel. Flow's PRO
  badge is the Google plan, not the extension licence.
- `flow-auto doctor --site flow` is the first command for anything unexplained. Relay its
  own words.

---

## Part 5: Credits

- **Flow credits** go only on pictures: about 40 for a 2-minute film, plus the sheet and
  any retries. The hybrid look adds about 20 stickers. Cards, tags, charts, the voice, the mix and the render are free.
- **Claude usage** grows with conversation length and with every image looked at.
  - QC from grids (`review.jpg`, `qc.jpg`), never frame by frame. One grid costs the same as
    one frame.
  - Start a new chat for each new video. PROGRESS.md makes "continue" work anywhere.

---

## Checklist before showing the video

- [ ] Script approved; facts checked, debated points worded as such
- [ ] Look matches the topic; 2 test shots looked at before the batch
- [ ] Every character shot used the right per-character reference
- [ ] SCENES cut on spoken words: mean shot 2.0-2.6 s, hook faster, none over ~6 s
- [ ] Text only for numbers and dates, names, map places and unseen terms; 6-10 cards
- [ ] review.jpg checked and PICK.tsv written; no duplicates
- [ ] Mix at -14 LUFS, voice on top, a few SFX on the words
- [ ] qc.jpg checked by you; tags off faces; re-rendered after fixes
- [ ] Licence check, written in PROGRESS.md:
  - **Voice:** Kokoro or Indic Parler (both Apache 2.0), or the user's own recording or key.
    Never edge-tts or Edge/Azure "Neural" voices: edge-tts drives the Edge browser's
    read-aloud service without a commercial licence.
  - **Sound:** every file comes from the kit's `sfx/StableAudio`, `sfx/Kenney_CC0` or
    `music/Gemini`, or from a source the user owns.
  - **Pictures:** the user's own Flow generations.
  - **Tools:** faster-whisper (MIT) only times the words; nothing from it is in the video.
