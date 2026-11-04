---
name: saif_advance
description: The ultimate, self-contained AI video documentary and stickman explainer production engine. Unifies Vox-style documentary collage, modern stickman hybrid, hand-drawn crayon doodling (kaomei/hand-drawn-video-prompts), and 5-stage suspense directing (kaomei/stickman-video-director) with zero-millisecond voiceover synchronization (Whisper + silence detection), dynamic Ken Burns camera tracking, elastic spring overshoot physics, rich tactile drop shadows, and automated Google Flow generation.
---

# SAIF AI Advance Studio: Master Production Skill

## Overview
`saif_advance` is a unified production package designed to produce high-retention, broadcast-quality 2D documentary explainers, modern stickman animation videos, hand-drawn crayon doodle explainers, and suspense storytelling videos.

It incorporates the top production methodologies:
1. **Vox Documentary Engine** (from `vox-doc`): Code-drawn analog clocks with sweeps, maps with shockwave pins, red connector lines, and Devanagari text shaping.
2. **Modern Stickman & Crayon Doodle** (from `kaomei/hand-drawn-video-prompts` & `stickman-hybrid`): Warm `#F8F6EF` fine paper canvas, thick black crayon linework, signature 3-color palette, and stop-motion animation.
3. **Crazy Suspense Director** (from `kaomei/stickman-video-director`): 5-Stage High-Completion Heartbeat Story Engine (Golden Hook -> Elevation), CinemaScope letterbox snap, sub-bass drops, red wax stamps, and detective evidence wall.
4. **Zero-Millisecond Latency**: Guarantees zero millisecond gap between the user's authentic narration (`VO.wav`) and on-screen visuals using local speech recognition (`core/align.py` via `faster_whisper`).
5. **Living Creative Animation**: Continuous Ken Burns push-in (`1.0 -> 1.075`), snappy spring overshoot (`back_out`), living floating sway, and directional soft drop shadows (`BoxBlur 12`).

---

## Directory Architecture
```
saif_advance/
├── ANTIGRAVITY.md               # Master agent execution instructions & 9-step production protocol
├── PLAYBOOK.md                  # The full 221-line Vox documentary explainer methodology
├── SKILL.md                     # Skill manifest and quick reference
├── SETUP.md                     # Universal setup verification guide
├── RULES.md                     # Core rules & constraints
├── README.md                    # User guide & workflow overview
├── START_HERE.txt               # Immediate quick-start guide
├── core/                        # Engine scripts
│   ├── trends.py                # Real-time multi-platform trend scanner (YouTube, Twitter/X, Reddit)
│   ├── align.py                 # Zero-gap speech-to-text alignment (faster_whisper + silence)
│   ├── render.py                # High-end living animation engine (clocks, sweeps, pins, vline, rule, cross)
│   ├── elements.py              # Flow prompt generator on magenta with demarking & keying
│   ├── mix.py                   # Broadcast audio mixer with ducking (-14 LUFS)
│   ├── cutout.py                # rembg transparency fallback
│   ├── key.py                   # Physical chroma-keying with alpha edge unmixing
│   ├── flow_bridge.py           # Google Flow prompt builder with @character handle binding
│   └── assemble.py              # Final video concatenation and faststart export
├── example/                     # Complete 'एक देश, एक घड़ी' 2-minute dataset
├── skills/                      # Bundled specialized skills
│   ├── vox-doc/                 # Vox documentary explainer skill
│   ├── vox-doc-sound/           # Complete sound design skill with Kenney CC0 & Gemini music
│   ├── stickman-hybrid/         # Modern stick figure explainer with 9 examples & looks
│   ├── hand-drawn-doodle/       # Crayon doodle skill (kaomei/hand-drawn-video-prompts)
│   ├── suspense-director/       # 5-Stage Heartbeat suspense engine (kaomei/stickman-video-director)
│   ├── gemini-storyboard-director/ # Cinematic choreography (philschmid/gemini-samples)
│   ├── matanyone-video-matting/ # Video matting & chroma keying (pq-yang/MatAnyone)
│   └── retro-8bit-arcade-style/ # 8-bit retro gaming visuals (mughaldev0/8-bit-retro-intro-video-prompt)
├── fonts/                       # Kalam-Bold.ttf, PatrickHand-Regular.ttf
├── audio/                       # Curated Foley SFX and music loops
└── templates/                   # Clean starter projects
```

---

## Direct CLI Workflow

0. Scan real-time breakout trends across YouTube, Twitter & Reddit:
   ```bash
   python core/trends.py --sources youtube,twitter,reddit
   ```
1. Place voiceover audio as `VO.wav`.
2. Create `LINES.tsv` with your script lines (`id`, `text`, `gap`).
3. Align voiceover to exact spoken words:
   ```bash
   python core/align.py --vo VO.wav --lines LINES.tsv
   ```
4. Generate isolated cutouts on pure magenta (`#FF00FF`) via Google Flow:
   ```bash
   python core/elements.py
   ```
5. Check timing plan and test animation math:
   ```bash
   python core/render.py selfcheck
   python core/render.py plan
   ```
6. Render video shots in parallel:
   ```bash
   python core/render.py shots
   ```
7. Mix broadcast audio with voice ducking (-14 LUFS):
   ```bash
   python core/mix.py cues.tsv --vo VO.wav --words WORDS.tsv --lines VO.tsv --out mix.wav
   ```
8. Assemble master video with Devanagari / Latin labels burned:
   ```bash
   python core/render.py master
   ```
