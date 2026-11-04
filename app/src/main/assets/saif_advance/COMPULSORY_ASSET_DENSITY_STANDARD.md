# COMPULSORY HIGH-DENSITY ASSET & VISUAL EXPLANATION STANDARD (V3.0)
## Mandatory Visual Asset Density & Zero-Unillustrated-Words Directive

---

## 1. Executive Summary & Root Cause Analysis

### The Critical Flaw of Sparse Animation
In previous versions and poorly constructed explainers, videos suffered from **visual starvation**:
- A single static cutout image would sit on screen for 8 to 12 seconds while 30 spoken words played out with zero visual change.
- The viewer experienced dead, static slides that felt completely devoid of animation, assets, and creative explanation ("whole video feel like without any asset and animation").
- The human attention span for vertical video is under 2.5 seconds. A video without continuous visual progression loses retention immediately.

### The Compulsory Standard
**Every single spoken concept, keyword, noun, number, action, and emotional shift MUST have an active visual asset, cutout sticker, badge, procedural animation, or explanatory graphic.**
- **Maximum Inter-Event Duration**: No scene may go longer than **1.5 to 2.0 seconds** without a new visual entrance (sticker pop, counter, clock sweep, badge, or icon).
- **Minimum Event Density**: A 60-second video MUST contain a minimum of **25 to 35 visual events** in `SCENES.tsv` (averaging 4 to 7 visual events per scene).
- **Layered Multi-Asset Stacking**: Scenes must NEVER rely on a single solitary image. Every scene must combine 3 to 5 coordinated visual layers.

---

## 2. The 5-Layer Visual Density Architecture

Every scene in `SCENES.tsv` must orchestrate five synchronized visual layers:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│ LAYER 5: WORD-LOCKED TYPOGRAPHY & PROCEDURAL INSTRUMENTS                   │
│   • libass Typography Badges: KEY, TAG, NUM, LBL (e.g. "+250% उछाल")       │
│   • Procedural Vector Instruments: Rotating Clocks, Red Crosses (✕), Pins  │
├─────────────────────────────────────────────────────────────────────────────┤
│ LAYER 4: KINETIC MICRO-STICKERS & EXPLANATORY BADGES                       │
│   • Concept Badges: Countdown Pill (⏱️ 5 MIN), Social Burst (+14.2K),       │
│     Checkmark Shield, Cyber Brain Lock, Rocket Surge, Casino Chips,        │
│     Swipe Gestures, Goldfish Comparison Card, Locked Books, Trophy Cup     │
├─────────────────────────────────────────────────────────────────────────────┤
│ LAYER 3: PRIMARY HERO CUTOUT / CONCEPT ANCHOR                               │
│   • High-resolution transparent PNG cutout on paper with dual drop shadow   │
│   • (e.g. Arjun Scrolling Bed, Brain Dopamine, Slot Phone, Stressed Arjun) │
├─────────────────────────────────────────────────────────────────────────────┤
│ LAYER 2: SECONDARY REINFORCING PROPS                                       │
│   • Contextual environment cutouts, comparison anchors, background badges   │
├─────────────────────────────────────────────────────────────────────────────┤
│ LAYER 1: AMBIENT LIVING CANVAS                                             │
│   • Warm `#F1E9D8` tactile paper canvas or `#1A213A` dark noir              │
│   • Continuous Ken Burns camera push-in (1.0 -> 1.075)                      │
│   • Micro-harmonic organic breathing (dy = 2.8*sin, dx = 1.4*cos)          │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Spoken Word-to-Asset Mapping Matrix

Whenever a script is analyzed and broken down into `SCENES.tsv`, every category of spoken expression must trigger its dedicated asset type:

| Spoken Expression / Concept | Mandatory Asset Type | Example Visual Asset | Motion Entrance | Screen Anchor |
| :--- | :--- | :--- | :--- | :--- |
| **Specific Time / Duration** (`5 मिनट`, `2 घंटे`, `15s`) | Countdown Pill / Clock Sweep | `10_pill_5min.png`, `clock 12:05>2:00` | `pop` (spring overshoot) | Top / Center-Top (`.22-.26`) |
| **Social Media / Tech Feed** (`रील्स`, `स्क्रॉलिंग`, `वीडियो`) | Social Media Burst Icons | `11_social_burst.png` (Hearts, +14K) | `rise` (smooth float) | Center-Right (`.75, .38`) |
| **Physical Gesture** (`स्वाइप`, `खोलते हैं`) | Kinetic Hand Gesture | `16_swipe_gesture.png` (Neon swipe trail) | `slideL` / `pop` | Center-Left (`.24, .64`) |
| **Psychological / Bio Mechanism** (`दिमाग हैक`, `डोपामिन`) | Biological / Cyber Metaphor | `13_brain_chip.png`, `14_dopamine_rocket.png` | `pop` / `rise` | Center (`.50, .54`) |
| **Negation / Debunking** (`गलती नहीं`, `रिलैक्स नहीं`) | Stamp / Shield Validation | `12_not_your_fault.png`, `cross ✕` | `stamp` / `pop` | Center (`.50, .22`) |
| **Metaphor / Analogy** (`कैसीनो`, `स्लॉट मशीन`, `जुआ`) | Gamification / Risk Props | `15_vegas_chips.png`, `04` (Slot phone) | `pop` / `zoom` | Center (`.50, .52`) |
| **Problem / Crisis** (`किताबें पढ़ना नामुमकिन`, `तनाव`) | Chained Objects / Overwhelm Hero | `18_books_locked.png`, `05` (Stressed Arjun) | `drop` / `rise` | Center (`.50, .58`) |
| **Scientific Comparison** (`4 सेकंड अटेंशन`, `गोल्डफिश`) | Comparison Stat Card | `17_goldfish_stat.png` (9s vs 4s card) | `pop` (elastic) | Center (`.50, .40`) |
| **Actionable Solution** (`ट्रैप तोड़ना`, `ग्रे-स्केल`) | Shatter / Tech Mode Toggle | `19_break_trap.png`, `06` (Greyscale phone) | `pop` / `slideR` | Center (`.50, .50`) |
| **Call-To-Action / Engagement** (`स्क्रीन टाइम`, `कमेंट`) | UI Stat Chart / Trophy Cup | `20_screentime_chart.png`, `21_trophy_detox.png` | `rise` / `pop` | Center-Bottom / Center |

---

## 4. Compulsory SCENES.tsv Density Protocol

### Quantitative Rules
1. **Event Frequency**: $\frac{\text{Total Visual Rows in SCENES.tsv}}{\text{Total Video Duration (seconds)}} \ge 0.45$
   - A 60-second video MUST have $\ge 28$ element rows in `SCENES.tsv`.
2. **Maximum Time Gap**: No gap between element entrances ($t_{in}^{(n+1)} - t_{in}^{(n)}$) may exceed **2.2 seconds**.
3. **Multi-Element Balance**:
   - Hero character cutout holds foundation.
   - 2 to 4 micro-stickers/badges enter sequentially on narrative keywords.
   - Text tags (`KEY`, `NUM`, `TAG`) pop synchronously with spoken emphasis.

### Concrete High-Density Example (Dopamine Reel Scrolling, 65s, 6 Scenes)

```tsv
scene	cue	kind	what	x	y	size	motion	out	label
# ─────────────────── SCENE 01: THE 5-MINUTE HOOK (0.00s - 9.75s) ───────────────────
01	@01	bg	night	-	-	-	-	-	-
01	@01	img	01	.50	.62	.42	pop	-	-
01	5	img	10_pill_5min	.78	.26	.18	pop	-	-
01	रील्स	img	11_social_burst	.22	.38	.22	rise	-	-
01	गायब	clock	12:05>2:00	.50	.24	.24	pop	-	2 घंटे गायब!
01	गलती	img	12_not_your_fault	.78	.70	.20	pop	-	-
01	दिमाग	img	13_brain_chip	.22	.70	.18	pop	-	-
01	हैक	text	KEY	.50	.88	-	-	-	दिमाग हैक हुआ है!

# ─────────────────── SCENE 02: THE DOPAMINE SURGE (9.75s - 20.55s) ───────────────────
02	@02	bg	paper	-	-	-	-	-	-
02	रिलैक्स	text	KEY	.50	.20	-	-	-	रिलैक्स?
02	लेकिन	cross	-	.50	.20	.15	pop	-	-
02	डोपामिन	img	02	.50	.54	.44	pop	-	-
02	ऊपर-नीचे	img	14_dopamine_rocket	.80	.54	.24	rise	-	-
02	250	text	NUM	.50	.86	-	-	-	+250% डोपामिन उछाल

# ─────────────────── SCENE 03: THE CASINO SLOT TRAP (20.55s - 31.60s) ───────────────────
03	@03	bg	night	-	-	-	-	-	-
03	लासवेगास	img	15_vegas_chips	.50	.20	.20	pop	-	-
03	कैसीनो	text	KEY	.50	.30	-	-	-	कैसीनो स्लॉट मशीन
03	स्लॉट	img	04	.50	.56	.46	pop	-	-
03	स्वाइप	img	16_swipe_gesture	.22	.68	.20	pop	-	-
03	जुआ	text	TAG	.50	.88	-	-	-	हर स्वाइप पर जुआ

# ─────────────────── SCENE 04: THE 4-SECOND ATTENTION CRASH (31.60s - 42.15s) ────────
04	@04	bg	paper	-	-	-	-	-	-
04	अटेंशन	clock	12:00>12:04	.50	.22	.22	pop	-	सिर्फ 4 सेकंड!
04	4	text	NUM	.50	.32	-	-	-	अटेंशन स्पैन: 4s
04	रहगया	img	17_goldfish_stat	.50	.46	.24	pop	-	-
04	किताबें	img	18_books_locked	.22	.68	.20	pop	-	-
04	नामुमकिन	img	05	.50	.72	.42	rise	-	-

# ─────────────────── SCENE 05: THE GREYSCALE CURE (42.15s - 55.85s) ───────────────────
05	@05	bg	paper	-	-	-	-	-	-
05	तोड़ा	img	19_break_trap	.50	.22	.22	pop	-	-
05	ग्रे-स्केल	img	06	.50	.52	.46	pop	-	-
05	ब्लैक	text	KEY	.50	.16	-	-	-	ग्रे-स्केल मोड ON
05	60%	text	NUM	.50	.86	-	-	-	-60% डोपामिन क्रैश

# ─────────────────── SCENE 06: THE 30-MIN DETOX CHALLENGE (55.85s - 65.20s) ───────────
06	@06	bg	night	-	-	-	-	-	-
06	@06	img	08	.50	.40	.38	pop	-	-
06	स्क्रीनटाइम	img	20_screentime_chart	.50	.66	.26	rise	-	-
06	कमेंट	text	KEY	.50	.84	-	-	-	स्क्रीन टाइम कितना है? 👇
06	सबसेकमहै	img	21_trophy_detox	.80	.30	.20	pop	-	-
```

---

## 5. Automated Density Validation Command

The engine provides an automated verification tool in `core/render.py` to inspect density:

```bash
# Validates timing, element existence, and flags visual dead zones (> 2.2s gap)
python core/render.py plan
```

If any scene contains fewer than 3 visual events or a gap greater than 2.2 seconds, the engine outputs:
```text
[WARNING] Scene 03 has a visual gap of 4.1s between 'कैसीनो' and 'जुआ'.
[COMPULSORY] Inject micro-asset or counter to maintain retention.
```

---

## 6. Sound Design Alignment (`cues.tsv`)

For every new micro-asset entrance in `SCENES.tsv`, a corresponding low-volume, tactile Foley sound effect MUST be registered in `cues.tsv`:
- `pop` / `pill`: `paper_popup.wav` (-18 dB)
- `stamp` / `cross`: `stamp_press.wav` (-14 dB)
- `clock`: `clock_tick.wav` (-22 dB)
- `chart` / `stat`: `scoreboard_flip.wav` (-16 dB)
- `shatter` / `trap`: `chalk_snap.wav` (-16 dB)
- `trophy`: `bell_ding.wav` (-18 dB)

This guarantees complete audiovisual synesthesia where every single word is felt and seen simultaneously.

---

## 7. Flow Connector Offline Protocol (Zero Remote Dependency)

### The Offline Mandate
If Google Flow connector (`saif_ai-flow`, `flow-connector`, or WebSocket bridge) is unavailable, offline, or disconnected:
- **NEVER attempt remote image generation** or invoke Flow tools.
- **NEVER wait or block** on external servers or network timeouts.
- **Strict Local Execution**:
  1. Use curated vector assets already in `elements/cut/`.
  2. Procedurally generate vector stickers, badges, countdown pills, shields, charts, and icons locally via Python scripts (`PIL`, `matplotlib`, `cairosvg`).
  3. Utilize native code-drawn procedural elements (`render.py` clocks, cross-out ✕, pins, trajectory rules, counters, and libass typography tags).
- **100% Deterministic Guarantee**: The entire explainer video must be completely renderable on a standard local CPU without internet access, external GPUs, or remote API dependencies.
