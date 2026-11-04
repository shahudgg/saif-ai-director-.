---
name: hand-drawn-doodle
description: Generate high-retention 9:16 vertical and 16:9 horizontal hand-drawn crayon doodle and stickman explainer videos on Google Flow (Nano Banana 2 + Veo/Omni-Flash). Inspired by kaomei/hand-drawn-video-prompts. Features the warm #F8F6EF paper canvas, thick black crayon linework, 3-color palette (sunflower yellow, cobalt blue, tomato red), 4-6s semantic shot breakdowns, visual metaphors, stop-motion animation, and zero subtitle clash. Use when the user requests "hand drawn", "crayon doodle", "doodle video", "q-doodle", "chibi cartoon", or "stickman animation".
---

# Hand-Drawn Crayon Doodle & Stickman Explainer Engine

Turn any voiceover script or topic into high-retention, modern 2D hand-drawn doodle explainers on **Google Flow**.

---

## 1. Non-Negotiable Visual Rules

- **Exact Canvas Color**: `#F8F6EF` (warm off-white fine paper texture). Never pure digital white `#FFFFFF`, never dark, never drifting gradients or vignettes.
- **Linework**: Thick, expressive, imperfect black crayon / ink outlines.
- **Strict 3-Color Restraint**: Fixed signature palette:
  - 🌻 **Sunflower Yellow**
  - 🔵 **Cobalt Blue**
  - 🍅 **Tomato Red**
- **Restrained Proportions**: Cute, expressive chibi / stickman with natural proportions. Strictly **NO bug-eyes, NO creepy photorealism, NO 3D rendering**.
- **Generous Safe Margins**: Bottom 15% is kept completely empty and clean so subtitles never clash with the artwork.

---

## 2. The Semantic Shot Breakdown (4–6 Seconds)

Never cut on arbitrary word counts. Cut on **argument beats, emotional turns, and visual metaphors**:
- **Shot 01 (Golden Hook)**: Establish the premise and visual paradox.
- **Shot 02 (Disrupt Assumptions)**: Physical friction / shatter common belief.
- **Shot 03 (Unveil Secrets)**: Pull back the curtain on hidden mechanics.
- **Shot 04 (Climax & Realization)**: The breakthrough / ultimate truth.
- **Shot 05 (Elevation & Comment Bait)**: Punchline + interactive debate prompt.

---

## 3. Google Flow Dual-Prompt Architecture

### [Flow Image Prompt] (for Nano Banana 2):
```text
Modern minimalist crayon doodle illustration, {aspect_ratio}, warm off-white fine-textured paper background #F8F6EF, thick imperfect black ink outlines. {visual_metaphor}. Clear composition with generous negative space, sunflower yellow, cobalt blue, and tomato red accents, empty clean area at bottom reserved for subtitles, no 3D render, no CGI, no photorealism, ultra sharp.
```

### [Flow Image-to-Video Prompt] (for Veo / Omni-Flash):
```text
Gentle 2D hand-drawn stop-motion animation, 24 FPS, synchronized timing.
[0-3s]: The character establishes the premise: {action_start}.
[3-7s]: Escalates action and interacts dynamically with the visual metaphor.
[7-10s]: Delivers the dramatic punchline and holds steady for cut.
Strictly continuous character action, zero idle staring, fixed #F8F6EF background with zero flicker, strictly no speech bubbles, no dialogue boxes.
```

---

## 4. Entity & Caricature Guidance

- **Public Figures (e.g. Musk, Jensen Huang, Zuckerberg)**: Non-photorealistic Q-version caricatures using safe visual anchors (hair silhouette, iconic glasses, trademark jacket) without creepy face clones.
- **Logos & Tech Products**: Rendered as cute illustrated props (e.g. a smiling GPU microchip, a doodle rocket) rather than photorealistic brand marks.
