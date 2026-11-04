# Music for Vox-style documentaries

**Bundled:** `music/Gemini/<mood>/` already has instrumental beds and ambient
pads for 22 moods, free to use commercially and to share. Use them first.

Put beds in `music/<Mood>/` (for example `music/explainer/`, `music/tension/`)
and run `python scripts/index_sfx.py`. The catalog levels every track, so a bed
row at `-23` in cues.tsv sits about 20 dB under the voice whatever the file's
own loudness.

## Prompts that work (ElevenLabs Music, Google, or any AI music tool)

Keep ONE instrument family across the whole video (here: soft piano +
pizzicato strings + marimba), so all the cues feel like one show. Change only
the mood per act.

Add this to the end of every prompt (`scripts/eleven_music.py` does it for you):
> instrumental only, no vocals, no choir, sparse arrangement that sits under a voiceover, no loud drums, clean natural ending, high quality stereo mix

| Mood folder | Prompt (works in Gemini, ElevenLabs Music, any AI music tool) |
|---|---|
| explainer | Curious documentary explainer music, light pizzicato strings and soft marimba, gentle pulse, 100 BPM, warm and inquisitive, like a Vox explainer |
| reflective | Reflective documentary piano, slow and warm, soft sustained strings underneath, 75 BPM, nostalgic, historical archive feeling |
| tension | Subtle documentary tension, low pulsing cello ostinato, soft piano notes, muted timpani far away, 85 BPM, serious but restrained, not a trailer |
| data | Light rhythmic documentary music, marimba and plucked strings, soft shaker, 108 BPM, clean and analytical, map and data visualization feeling |
| question | Thoughtful questioning underscore, sparse piano with soft pads, 80 BPM, open-ended, unresolved chords, calm curiosity |
| reveal | Warm uplifting documentary reveal, piano melody building with strings, 90 BPM, hopeful and surprising, gentle crescendo then soft resolve |
| regional (India example) | Soft Indian documentary music, santoor and gentle bansuri flute over warm strings, light tabla, 90 BPM, peaceful morning, modern cinematic |
| outro | Short documentary ending sting, piano and strings, 8 to 10 seconds, warm final chord with natural ring-out |

For another region, swap the regional instruments: oud and frame drum, koto
and shakuhachi, kora and djembe, accordion and nylon guitar.

Lengths: 60-120 s for beds, 8-10 s for the sting. Two variants per mood keep
your videos from sharing one track. ElevenLabs Music costs about 900 credits per
75-90 s track. Check the tool's terms before using its music commercially.

## Free: generate with Google Gemini (Lyria)

1. Open https://gemini.google.com (signed in, 18+).
2. Settings: Length **Standard** (Short for an outro), Vocals **Instrumental**,
   Genre **Custom** (**Ambient** for pads, **Lo-fi** for a lo-fi bed).
3. Paste a tested prompt from below.
4. Download **one** track per prompt into `music/<mood>/`, then run
   `python scripts/index_sfx.py`.

Gemini sometimes returns two tracks for one prompt. A BPM number on its own is
often ignored; the prompts below pair it with "slow, half-time, relaxed,
unhurried, not busy", which keeps the beds calm under a narrator.

These prompts made the bundled `music/Gemini/` tracks.

The tail every music prompt ends with:
> Instrumental only, no vocals, no lyrics, no singing, no choir. Minimal and understated so a narrator can talk over it. Not epic, not a movie trailer. Clean natural ending.

The tail every ambient pad prompt ends with:
> Ambient soundscape, no melody, no drums, no beat, no vocals, no lyrics. Slowly evolving, very calm, made to sit quietly under a narrator. Clean natural fade-out ending.

Each music prompt starts "Create a 2 to 3 minute long …" and each pad prompt
starts "Create a 2 to 3 minute long … soundscape:". Fill in the rest from the
tables.

| mood | music prompt body (then add the music tail) |
|---|---|
| explainer | instrumental background track for a Vox-style explainer video at 85 BPM: light sparse plucked synth notes, soft marimba and gentle pizzicato strings over a warm analog synth pad. Curious and inquisitive, but slow and laid-back, half-time feel, relaxed and unhurried, not busy, plenty of space between notes. Keep the same calm groove for the whole length so it can sit under narration. |
| tension | instrumental tension underscore for a documentary at 80 BPM: a low slow pulsing synth bass, a very soft ticking hi-hat and sparse muted piano notes in a minor key. Uneasy but restrained, slow half-time feel, not busy. |
| reflective | reflective instrumental track for a documentary about history at 70 BPM: slow soft piano over a warm synth pad, gentle and nostalgic, a little bittersweet. Slow and unhurried, lots of space between notes. |
| reveal | warm instrumental reveal track at 85 BPM: soft piano and a gentle plucked synth slowly building with warm soft strings, hopeful and quietly surprising, then a soft resolve. Half-time feel, not busy. |
| data | analytical instrumental track for a data and maps explainer at 90 BPM: a light crisp plucked synth sequence, soft clicks and very light percussion over a warm pad. Clean, precise and curious, steady and unhurried. |
| question | thoughtful instrumental underscore at 75 BPM: sparse soft piano and an airy pad with open, unresolved, questioning chords. Calm curiosity, lots of space. |
| mystery | investigative instrumental underscore at 85 BPM: a soft plucked bass, muted piano and light ticking percussion in a minor key. Curious and mysterious, not busy. |
| hopeful | hopeful instrumental for an explainer at 90 BPM: soft piano and a light plucked synth over a warm pad with very gentle claps. Positive, warm, relaxed. |
| tech | modern tech instrumental at 90 BPM: gentle soft synth arpeggios, a light soft electronic beat and warm pads. Clean, optimistic, calm. |
| indian (or your region) | instrumental Indian explainer track at 85 BPM: soft santoor and gentle bansuri flute over a warm synth pad with a light soft tabla groove. Modern, calm, curious. Add "no humming". |
| history_serious | serious historical instrumental at 70 BPM: slow cello and soft piano over a warm pad, dignified and solemn but understated. |
| explainer_lofi | calm instrumental background track at 80 BPM: a mellow lo-fi beat, soft Rhodes piano chords and a warm pad. Relaxed, clear and friendly. |
| world | light world-music instrumental at 85 BPM: kalimba and soft hand percussion over a warm pad. Curious and light. |
| outro (Length: Short) | Create a short instrumental ending … at 85 BPM: soft plucked synth and warm piano gently landing on a warm, satisfying final chord with a long natural ring-out. |

| mood | ambient pad prompt body (then add the ambient tail) |
|---|---|
| ambient_warm | soft sustained synth pads with a gentle shimmer, hopeful and calm, very slow |
| ambient_tension | a low slowly evolving synth drone with faint metallic textures and distant airy noise, uneasy but quiet |
| ambient_space | deep airy pads, distant shimmering tones, a sense of scale and wonder |
| ambient_mystery | soft reversed textures, a low pulse far away and faint glassy tones |
| ambient_nature | soft wooden and breathy textures, gentle wind-like pads, peaceful and earthy |
| ambient_history | a warm tape-saturated pad with faint vinyl crackle and distant old-radio texture |
| ambient_tech | soft glitchy textures, gentle data-like blips far in the background and a smooth pad |
| ambient_sad | slow soft pads with a faint distant piano resonance, heavy and quiet |

There is a daily limit; it refreshes, or a Google AI plan raises it. Every track
carries an invisible SynthID watermark marking it as AI-generated.

**Commercial use:**
- Google's Terms of Service say "Google won't claim ownership over that
  content" (https://policies.google.com/terms).
- They put no restriction on commercial use. They do forbid training AI models
  on the output, and presenting it as human-made.
- No Google page grants commercial rights in so many words, for any plan.
- Using a track as background music in your own videos fits these terms.
- Before a large commercial project, read the current terms yourself.

## Free music you download yourself

- https://pixabay.com/music/ (search "documentary", "explainer", "corporate
  marimba", "piano documentary"). Free for commercial videos, no attribution.
- YouTube Audio Library (YouTube Studio → Audio Library). Some tracks require
  attribution; read the licence column.

Use them in your videos, but do not share the downloaded files.

## Mixing notes

- Match tempo to the narration: slow documentary 70-100 BPM, normal explainer
  95-120.
- Change cue on an act change, with a 2-5 s fade. Not every 12 seconds.
- If the music vanishes in the pauses, raise the bed 2-3 dB. If you can hum it
  while following the voice, lower it.
