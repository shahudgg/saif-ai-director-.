---
name: suspense-director
description: Direct and edit crazy, high-suspense Vox and stickman documentary intros and outros synchronized to user voiceover. Features the 5-Stage High-Completion Heartbeat Engine (Golden Hook, Disrupting Assumptions, Unveiling Insider Secrets, Ultimate Truth, Elevation & Discussion), cold-open sub-bass risers, rapid micro-teaser flashes, anamorphic letterbox snap, classified red wax stamps, and lingering mystery evidence-wall outro. Use when the user requests "suspense intro", "crazy intro", "suspense editing", "mind blowing animation", "suspense outro", "better hook", or "make it suspenseful".
---

# Suspense Director: Crazy High-Tension Intro & Outro Engine

A master directing and editing skill designed to create heart-pounding, cinema-grade documentary and stickman explainers. Inspired by the **5-Stage Heartbeat Engine** (from `kaomei/stickman-video-director`) and Vox investigative editing.

---

## 1. The 5-Stage High-Completion Heartbeat Story Engine

To shatter viewer drop-off and push completion rates past 60–75%, every script must strictly follow this 5-stage emotional heartbeat:

```text
[0-5s]           [5-25%]            [25-60%]           [60-85%]           [Final 15%]
Golden Hook  ──> Disrupt Assumptions ──> Unveil Insider ──> Ultimate Truth ──> Elevation & Debate
(Visual Shock)   (Break Wisdom)          Secrets (Friction) (Aha! Breakthrough) (Comment Trap)
```

1. **Stage 1: Golden Hook (0–5s)**
   - **Timing**: Instant impact within the first 2–5 seconds. Never open with throat-clearing, greetings, or generic titles.
   - **Mechanism**: An unexpected, counter-intuitive paradox or high-stakes question that blows open an irresistible information gap.
   - *Example*: *"Every history book says the British empire conquered the Himalayas in 1814. But there is a sealed dispatch Whitehall tried to bury for two hundred years..."*

2. **Stage 2: Disrupting Assumptions (5–25%)**
   - **Goal**: State what 99% of people believe, then decisively shatter it in a single sentence to trigger cognitive conflict.
   - *Example*: *"You probably think superior British firepower decided the war. You are completely wrong."*

3. **Stage 3: Unveiling Insider Secrets (25–60%)**
   - **Goal**: Pull back the curtain on the hidden mechanics, tactical blunders, covert treaties, or psychological warfare.
   - Keep a relentless visual change every 2–3 seconds across timed beats: `[0–3s]`, `[3–7s]`, and `[7–10s]`.

4. **Stage 4: Ultimate Truth Revelation (60–85%)**
   - **Goal**: Deliver the philosophical, geopolitical, or psychological breakthrough. Give the audience the deeply satisfying "aha!" epiphany.

5. **Stage 5: Elevation & High-Engagement Discussion (Final 15%)**
   - **Goal**: Crystallize the moral into an unforgettable punchline, followed by a polarizing, open-ended question that forces viewers to debate in the comments.
   - *Example*: *"So was Nalapani a military defeat, or the greatest psychological victory in Asian history? Drop your take below."*

---

## 2. Anatomy of the Crazy Suspense Intro

```text
0.0s ─────────────────── 1.5s ─────────────────── 3.5s ─────────────────── 5.0s
[Blackout Letterbox]    [Teaser Glitch Flashes]  [Anamorphic Snap]       [Paper Desk Tear]
Deep Sub-Bass Impact    3 Rapid Clue Cutouts     TOP SECRET Wax Stamp    Launch Beat 1
Ticking Clock Riser     Heartbeat Spike          Golden Hook VO Drops    Paper Whoosh SFX
```

### Visual & Audio Choreography:
1. **0.0s – 1.5s: The Blackout Riser & Teaser Cuts**
   - **Letterbox**: CinemaScope bars (`2.39:1`).
   - **Audio**: 30Hz sub-bass impact drop (`boom_sub.wav`) + rapid ticking clock / heartbeat riser (`heartbeat_riser.wav`).
   - **Visual**: 3 lightning micro-teaser flashes (150ms each) showing key clue cutouts from the climax of the story with rapid glitch/flash sound effects.
2. **1.5s – 3.5s: The Anamorphic Slam & Red Stamp**
   - The letterbox aggressively snaps open into 16:9 with a spring whip.
   - Dramatic 3D push-in ($1.0 \to 1.15$).
   - Giant bold suspense headline cartouche slams down: e.g. `"THE 1814 CONSPIRACY"`.
   - A red wax seal or TOP SECRET classified stencil stamp slams onto the paper with a heavy thud impact.
   - The voiceover hits the Golden Hook paradox.
3. **3.5s – 5.0s: The Desk Tear & Scene Unfold**
   - A quick paper tear transition with an organic whip whoosh, launching straight into Beat 1.

---

## 3. Anatomy of the Mind-Blowing Suspense Outro

1. **Slow-Burn Crime-Board Pull-Out (Final 10s – 5s)**:
   - Voiceover delivers the ultimate truth.
   - Camera pulls back slowly ($1.12 \to 1.0$), assembling all previous artifacts, maps, and portraits onto a giant detective evidence wall with red connecting string lines.
2. **The Debate Trap & Punchline (Final 5s – 1.5s)**:
   - A punchline title card slams down in high-contrast gold/crimson: `"VICTORY OR COMPROMISE?"`.
   - An irresistible discussion prompt: *"What would you have done? Drop your thought below 👇"*.
   - A sub-bass tension drone holds the room silent.
3. **Cinematic Fade to Black (Final 1.5s)**:
   - Reverb echo tail on the audio, leaving the viewer breathless and compelled to comment or replay.

---

## 4. Stickman & Animation Directing Rules (Zero Jitter, Zero Drift)

- **Timed Visual Sequence**:
  - `[0–3s]`: Establish or inherit the premise.
  - `[3–7s]`: Transform, escalate, or explain the metaphor through character action.
  - `[7–10s]`: Deliver climax and create the transition.
- **Anti-Stutter & Anti-Lag**:
  - **No abstract liquid morphing**: Do not melt objects into other objects; it creates diffusion noise and jitter.
  - **Concrete physical actions**: Characters leap, pull paper tape, point at maps, open heavy doors, slam wooden stamps.
  - **Zero Idle Figures**: Characters never stand idle or stare blankly at the lens.
- **Audio-Only VO Contract**:
  - Voiceover is audio-only. **Strictly NO dialogue bubbles, speech boxes, or visual text transcription**.
  - All character actions reinforce the words without mechanically repeating them.

---

## 5. 1-Click Production Command

To render a project with the Suspense Intro & Outro automatically:
```bash
python core/render_box.py --suspense-intro --suspense-outro
```
