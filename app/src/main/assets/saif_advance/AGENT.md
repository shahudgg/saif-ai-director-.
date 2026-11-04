# AGENT_LESS_CREDIT.md — High-Efficiency Zero-Veo Production Engine

## 🚨 MANDATORY PROTOCOL: SAIF AI EXTENSION & 2-TURN AUTOPILOT LAWS

### 0. STARTUP MANDATE: SAIF AI EXTENSION CONNECTION CHECK (EVERY SESSION START)
- At the start of a session, check if the **SAIF AI Flow Bridge Extension** is connected (`saif_ai_status`).
- **ZERO REDUNDANT PROMPTS**: If the extension is ALREADY connected (`ready: true` or verified in this session), **NEVER ask the user to connect it again**. Silently proceed straight to work.
- Google Flow Image Generation (`🍌 Nano Banana 2.1` / `🍌 Nano Banana Pro`) is the EXCLUSIVE visual engine.

---

### 1. STRICT ONE-BY-ONE QUESTIONING PROTOCOL (THEN 100% AUTOPILOT)
> 🚨 **ZERO REDUNDANT QUESTIONS & SMART SKIP LAW (CRITICAL)**:
> - **Smart Parameter Skip**: If the user has ALREADY provided any parameter (e.g. Topic, Style, Duration, Ratio, or Language) in their prompt or conversation, **IMMEDIATELY SKIP THOSE QUESTIONS!**
> - **Ask ONLY the Remaining Questions One by One**: For example, if the user starts by giving you the Topic and Style, acknowledge them in one short line and ask **ONLY for the next missing question** (e.g. Timing/Duration) — NEVER ask for Topic or Style again!
> - **Full Concept / All Details Given**: If the user provides all details (or a full script/story), ask **ZERO setup questions** — proceed directly to Step 6 (Script & Cartesia Key) and launch 100% autopilot!
> - **Extension Check**: If SAIF AI Extension is already connected, **NEVER ask to connect it**. Silently proceed straight to work.

#### 💬 STEP 1: Video Style ONLY (Skip if already provided)
Present ONLY the 5 Video Styles with visual references:
- **1. Vox Documentary Explainer** (Waxy tactile paper, animated vector clocks, trajectory maps, forensic callouts)
- **2. Sticker / Stickman Hybrid** (Bold ink stickmen, vibrant pop-in cutout stickers, drop shadows)
- **3. Hand-Drawn Doodle & Sketchbook** (Warm textured paper, pastel yellow/blue/red accents, circled notes, wobbly arrows)
- **4. Suspense Noir / Crime Documentary** (Dark chiaroscuro shadows, "CLASSIFIED" stamps, red string boards)
- **5. Retro 8-Bit Arcade** (Pixel-art badges, arcade HP bars, coin counters, CRT scanlines)
*STOP and wait for the user's response. Do NOT mention topic, duration, ratio, or language yet.*

#### 💬 STEP 2: Topic ONLY
Confirm the selected style in one short line. Then present ONLY the Topic options:
- 3 real trending topics on YouTube/Twitter[X] tailored to the chosen style.
- 1 expert recommendation from you.
- Option to provide a custom topic or concept.
*STOP and wait for the user's response. Do NOT ask about duration, ratio, or language yet.*

#### 💬 STEP 3: Timing / Duration ONLY
Confirm the chosen topic. Then ask ONLY for the Timing / Duration:
- **1. Short-Form Explainer:** ~45–60 seconds (high-retention viral pace, ~25–35 visual cutouts)
- **2. Full-Length Deep Dive:** ~3–5 minutes (in-depth documentary breakdown)
*STOP and wait for the user's response. Do NOT ask about ratio or language yet.*

#### 💬 STEP 4: Aspect Ratio ONLY
Confirm duration. Then ask ONLY for the Aspect Ratio:
- **1. 9:16 Vertical** (YouTube Shorts, Instagram Reels, TikTok)
- **2. 16:9 Landscape** (YouTube Documentary Widescreen)
*STOP and wait for the user's response. Do NOT ask about language yet.*

#### 💬 STEP 5: Voiceover Language ONLY
Confirm aspect ratio. Then ask ONLY for the Voiceover Language:
- **Which language do you want the script in?** (e.g., Hindi, English, Hinglish, or specify your own).
*STOP and wait for the user's response.*

#### 💬 STEP 6: Full Script + Cartesia Free API Key Request & Guide
Once all 5 parameters are locked, in the NEXT message:
1. Generate the complete, high-retention **Narrative Breakdown & Visual Storyboard**.
2. Generate the **1-Click Ready-to-Copy Spoken Prose Script** in the exact requested language.
3. In the **EXACT SAME message**, request their **Cartesian AI Free API Key** (or ask to confirm using the existing key: `sk_car_VzbwXW6XvZTG7pA4pjRE7m`), accompanied by the 3-step 100% free guide:
   > **How to get your Free Cartesian API Key ($0 — No Card Needed):**
   > 1. Open [**play.cartesia.ai**](https://play.cartesia.ai) and sign in with any Google account.
   > 2. Go to **API Keys** and click **+ Create API Key**.
   > 3. Paste the key here (includes 20,000 free monthly characters — ~30 shorts or 6 full 5-min videos!).
*STOP and wait for the user to paste or confirm their API key.*

#### 🚀 AUTOPILOT ACTIVATION: 100% AUTOMATED EXECUTION (ZERO FURTHER QUESTIONS)
The moment the Cartesian API key is provided or confirmed, the agent MUST ask **ZERO further questions** and execute the entire production pipeline on complete autopilot:
1. **Instant Voiceover Generation**: Automatically synthesize broadcast audio via `core/cartesia_tts.py` / `cartesia-mcp` with emotion controls (`neutral`, `calm`, `content`, `excited`, `sad`, `angry`, `curious`, `dramatic`) and recommended voice presets (`rohan`, `dev`, `neha`, `anuj`, `skylar`, `mariana`), saving directly to `VO.wav` in ~1 second.
2. **0ms Sync Alignment**: Extract word-by-word timestamps via `core/align.py` (`WORDS.tsv`).
3. **Parallel Google Flow Image Generation**: Generate all visual assets and character cutouts in parallel using **Google Flow (`🍌 Nano Banana 2.1` / `Pro`)** on solid magenta `#FF00FF`. (NEVER use Antigravity models or local procedural images).
4. **Bang-Motion Mixed Animation**: Render all scenes with mixed animations (`fade` 60%, `draw`/`sketch` 25%, `pop` 15%, `slide` outros) adhering to the zero-overlap psychological layout engine (Modes A, B, C, D).
5. **Master Render & Final Export**: Mux the ducked broadcast mix (`mix.wav`) and assemble `master.mp4`.
6. Complete all generation, processing, and video rendering 100% automatically without pausing.

---

### 2. CRITICAL PRODUCTION LAWS (ZERO TOLERANCE)
1. **STRICT CAPTION GENERATION PERMISSION MANDATE**: Never generate, embed, or burn dialogue subtitles/captions on the video without explicit user permission. Only archival graphic callout badges/labels are permitted.
2. **EXCLUSIVE GOOGLE FLOW IMAGE MANDATE**: ALWAYS use Google Flow (`🍌 Nano Banana 2.1` first, `🍌 Nano Banana Pro` as fallback) on pure `#FF00FF` magenta. NEVER use Antigravity models, local procedural PIL scripts, or dummy shapes.
3. **PSYCHOLOGICAL SPATIAL PLACEMENT & ZERO OVERLAP**:
   - Mode A (Hero Center `x=0.50, y=0.50`, size `0.58-0.65`)
   - Mode B (Left vs Right `x=0.24-0.28` vs `x=0.72-0.76`, $\Delta x \ge 0.40$)
   - Mode C (Hero Left-Third `x=0.26-0.30`, Evidence Right-Third `x=0.72-0.74`)
   - Mode D (Triptych `x=0.20`, `0.50`, `0.80`)
   - Mandatory Bounding-Box Collision Prevention: Elements must never overlap.
4. **MIND-BLOWING CINEMATIC ACCRETION & KINETIC SHOCK SUITE (ANTI-PPT DOCTRINE)**:
   - **Zero-Static Law**: NEVER leave an image static on screen for > 1.2s.
   - **5-Layer Forensic Accretion Engine**: Scenes NEVER have just 1 lonely image. Every scene builds like an investigative documentary evidence board (3 to 5 layered elements per beat):
     - `Layer 1: Protagonist / Hero Subject` (enters with `slam` or `whipL`)
     - `Layer 2: Forensic Evidence Document / Map / Blueprint` (`draw` / `sketch` with drop shadow)
     - `Layer 3: Red Trajectory Yarn / Vector Dashed Lines` (`rule` connecting ideas/suspects)
     - `Layer 4: Tactile Distressed Rubber Stamp Slam` (`stamp` e.g. "CLASSIFIED", "FATAL", "PROOF", "EXPOSED" at -12° that triggers visceral camera screen shake!)
     - `Layer 5: Optical Anamorphic Light Leaks` (warm golden 35mm film burn sweeping across transition cuts).
   - **Dynamic Living 2.5D Camera Universe**:
     - Continuous organic camera breathing (`cam_drift` 1.0 -> 1.04).
     - Damped High-Frequency Screen Shudder ($\pm 14\text{px}$ impact shock on dramatic hits, stamps, and revelations).
     - Push-Through 3D Wormhole Transitions (`push_through` accelerating into keyholes, lenses, or documents into the next scene).
   - **Disney Squash & Stretch Dynamics (Elastic Jelly Physics)**:
     - High-velocity entries stretch along travel vector ($s_x=1.20, s_y=0.92$), compress on impact ($s_x=1.16, s_y=0.86$), and settle with critically damped Remotion spring physics.
   - **Ambient Micro-Sway (Anti-Cardboard Stillness)**:
     - Held elements continuously breathe with gentle sinusoidal phase-shifted floating ($dy = \pm 3.5\text{px}$), keeping the entire frame alive like a high-budget BBC/Vox documentary.
5. **100% ZERO-MILLISECOND AUDIO-VISUAL SYNC**: Every cut, motion, text, and SFX locked to the spoken word.
6. **FULL VIDEO EDITOR & SOUND DIRECTOR MANDATE**:
   - **Context-Aware Premium Mixed Font Pairings**: NEVER use a single flat font for the whole video. Always orchestrate curated, mixed font pairings (Headline/Display vs Body/Label vs Monospace) tailored to the content settlement:
     - *Prestige / Historical / Philosophy / High-Stakes*: `Playfair Display` (Headlines) + `Lato` / `Liberation Sans` (Body)
     - *Investigative / News / Documentary*: `Montserrat` (Headlines) + `Merriweather` (Body)
     - *Human Story / Biography / Emotional Deep Dive*: `Noto Sans` / `Roboto` (Headlines) + `Lora` (Warm Narrative Body)
     - *Tech / AI / Futuristic / Product*: `Manrope` (Display) + `Sora` / `Ubuntu` (Body)
     - *Cyber / Data / Financial Crime / Code*: `Space Mono` (Display & Data) + `Liberation Sans` (Body)
     - *Hindi / Indian Heritage & Viral Explainer*: `Khand` / `Yatra One` (Bold Display & Numbers) + `Noto Sans Devanagari` (Readable Body)
   - **Key Term & Critical Word Highlighting**:
     - The single most critical revelation per beat MUST pop using `KEY` style (accent pill highlight with spring pop).
     - Statistics, years, and metrics MUST use `NUM` style (oversized kinetic number pop).
     - Forensic markers and categories MUST use `TAG` style (inverted dark monospace chips).
   - **Full Sound Design (Foley SFX & Ducked Music Bed)**:
     - Every video MUST have an orchestrated sound design via `cues.tsv`:
       - Background music bed selected from `skills/vox-doc-sound/music/Gemini/` matching the emotional tone (`ambient_mystery`, `ambient_tech`, `tension`, `history_serious`, `explainer_lofi`, `hopeful`, etc.).
       - Tactile Foley SFX locked to spoken words (paper whoosh, rubber stamp slam thud, thumbtack click, analog clock tick, sub-drop risers).
       - Sidechain compressor ducking music under narration and SFX, mastered to **-14 LUFS** with a **-1.5 dBTP** ceiling.
7. **COMPULSORY ALL-AT-ONCE PARALLEL ASSET GENERATION MANDATE (ZERO SERIAL DISPATCH)**:
   - **Zero One-by-One Generation**: NEVER generate visual assets one-by-one, sequentially, or in slow consecutive turns. Generating 30–80 assets sequentially with individual pauses destroys speed and freezes the pipeline.
   - **100% Simultaneous Parallel Wave**: Formulate the complete `ELEMENTS.tsv` upfront and dispatch ALL pending assets simultaneously in parallel in a single wave via `core/parallel_generator.py` (or `saif_ai_batch_generate(mode="textToImage")`).
   - **Google Flow Cluster Concurrency**: All prompts fire concurrently into Google Flow (`🍌 Nano Banana 2.1` / `Pro`) so the cluster generates all image cutouts and canvases in parallel.
   - **Multi-Core Parallel Alpha Unmixing**: As assets download from Google Flow, execute multi-threaded parallel chroma keying via `core/key.py` across all CPU cores in a single pass.



> **Source Engine:** `saif_advance.zip` (HyperFrames Animation & Local Motion Compiler)  
> **Credit Preservation Mandate:** ZERO Veo Video Credits Used. Flow Image Generation ONLY + Local Python/HyperFrames Compositing.  
> **Model Selection Directive:** NEVER use Imagen 3 (`imagen-3`). ALWAYS use **🍌 Nano Banana 2.1** first. If rate/quota limit is reached, switch to **🍌 Nano Banana Pro**.  
> **Voiceover Directive:** Direct Cartesian AI Sonic TTS (Free API / MCP / `core/cartesia_tts.py`) with top voices (`rohan`, `dev`, `neha`, `anuj`, `skylar`, `mariana`) and emotion styling.  
> **Animation Directive:** ALWAYS activate ALL bundled animation skills (`hyperframes`, `vox-doc`, `vox-doc-sound`, `stickman-hybrid`, `hand-drawn-doodle`, `suspense-director`, `embedded-captions`, `retro-8bit`, `bang-motion`). Animation and creativity are EVERYTHING.  
> **Enforcement Level:** STRICT & ABSOLUTE — Universal Production Standard for all users and video workflows globally.

---

## 1.Core Philosophy

### 1.1 The Protocol
The engine operates as a universal, high-efficiency production framework. Antigravity MUST immediately activate the **High-Density Hybrid 2D Animation Engine**:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                 UNIVERSAL ZERO-VEO PRODUCTION ARCHITECTURE                  │
│                                                                             │
│   Google Flow (Cloud Image Assets)      External Voice & Local Motion       │
│   ┌───────────────────────────────┐     ┌───────────────────────────────┐   │
│   │ • 🍌 Nano Banana 2.1 (FIRST)  │     │ • Cartesian AI (Free Voice) / │   │
│   │ • 🍌 Nano Banana Pro (LIMIT)  │     │   ElevenLabs (Paid Voice)     │   │
│   │ • NEVER use Imagen 3          │ ──► │ • Zero-Millisecond Whisper    │   │
│   │ • Flat Magenta (#FF00FF)      │     │ • All 8 Bundled Motion Skills │   │
│   │ • ZERO Veo Credits Expended   │     │ • Physical Chroma Keying      │   │
│   │ • $0 / Low-Quota Friendly     │     │ • Broadcast Audio Ducking     │   │
│   └───────────────────────────────┘     └───────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 The Absolute Hard Bans for High-Efficiency Production Mode
1. **NO VEO VIDEO GENERATION TOOLS**:
   - 🚫 **BANNED**: Never call `saif_ai_generate_video`, `saif_ai_rapid_storyboard_autopilot`, `flow_generate_video`, or any tool invoking `veo-omni`, `veo-quality`, or `veo-lite`.
   - ✅ **ENFORCED**: Use ONLY Google Flow **Image Generation** (`saif_ai_generate_image`, `saif_ai_batch_generate(mode="textToImage")`, or `core/elements.py` / `flow-auto`) to produce isolated stickers and character keyframes.
2. **NO IMAGEN 3 MODEL USAGE (`imagen-3`)**:
   - 🚫 **BANNED**: Never use `imagen-3` / `NARWHAL`.
   - ✅ **ENFORCED**: Always use **`🍌 Nano Banana 2.1`** (`nano-banana-2`) as the primary image model. If quota/limit is exceeded, automatically switch to **`🍌 Nano Banana Pro`** (`nano-banana-pro`).
3. **NO LOCAL VOICE GENERATION**:
   - 🚫 **BANNED**: Never attempt to synthesize or generate voiceover audio locally using Python packages (e.g., no local Kokoro, eSpeak, Bark, Coqui, pyttsx3, or robotic TTS).
   - ✅ **ENFORCED**: Always provide the 1-click **Ready-to-Copy Clean Script** and instruct the user to generate broadcast audio on **Cartesian AI (Free)** or **ElevenLabs (Paid)**.
4. **NO DUMMY / PLACEHOLDER STYLES**:
   - 🚫 **BANNED**: Never use generic placeholder boxes, dull clip-art, repetitive canned emojis, or lazy static cardboard cutouts.
   - ✅ **ENFORCED**: High-effort, bespoke creative art direction. Every visual must feature rich tactile depth, expressive dynamic posing, dramatic chiaroscuro lighting, and unique narrative metaphors tailored directly to the story.
5. **NO VISUAL STARVATION OR STATIC SLIDESHOWS**:
   - 🚫 **BANNED**: Never leave an image static on screen for > 2.2 seconds without visual progression.
   - ✅ **ENFORCED**: Strict adherence to `COMPULSORY_ASSET_DENSITY_STANDARD.md` ($\ge 0.45$ visual events/sec, $\ge 28$ elements per 60s video, 5-layer visual density stack).
6. **NO AUDIO-VISUAL DRIFT (ZERO MILLISECOND TOLERANCE)**:
   - 🚫 **BANNED**: Never show visual assets or text badges earlier or later than the spoken word.
   - ✅ **ENFORCED**: 100% word-locked synchronization via `core/align.py` (`WORDS.tsv`).
7. **NO ANTIGRAVITY OR LOCAL IMAGE MODELS (EXCLUSIVE GOOGLE FLOW IMAGE MANDATE)**:
   - 🚫 **BANNED**: Never use Antigravity image models, local PIL procedural scripts, synthetic fallback image code, or dummy shapes to generate visual assets or cutout images.
   - ✅ **ENFORCED**: ALWAYS use **Google Flow** image generation (`🍌 Nano Banana 2.1` first, `🍌 Nano Banana Pro` as automatic fallback) via SAIF AI Flow Bridge (`saif_ai_generate_image`, `saif_ai_batch_generate(mode="textToImage")`, or `core/elements.py` / `flow-auto`) on solid flat pure magenta `#FF00FF`.
   - If Google Flow or SAIF AI extension is offline, disconnected, or turned OFF in Chrome, NEVER fall back to Antigravity models or local procedural generation. Prompt the user to turn ON the SAIF AI extension / open `flow.google.com` so Google Flow generates 100% of the image assets.
8. **NO CAPTIONS WITHOUT EXPLICIT USER PERMISSION**:
   - 🚫 **BANNED**: Never generate, overlay, burn, or add captions/subtitles to the video without explicit permission from the user.
   - ✅ **ENFORCED**: Only generate captions if the user explicitly says to generate captions (e.g. "generate captions", "add subtitles", "include captions"). Without explicit user permission, never generate captions.
9. **NO SERIAL ONE-BY-ONE ASSET GENERATION (MANDATORY ALL-AT-ONCE PARALLEL DISPATCH)**:
   - 🚫 **BANNED**: Never dispatch asset prompts serially, one-by-one, or in sequential loops with manual waits.
   - ✅ **ENFORCED**: ALWAYS dispatch ALL image assets at once in parallel in a single wave via `core/parallel_generator.py` / `core/elements.py` or `saif_ai_batch_generate(mode="textToImage")`.

---

## 2. Voiceover Directive: Cartesian AI & ElevenLabs Protocol

### 2.1 The Ban on Local Voice Synthesis
Local TTS engines produce flat, robotic, or metallic speech that completely destroys viewer retention. Antigravity is **STRICTLY PROHIBITED** from executing local speech synthesis scripts.

### 2.2 The Two Authorized Voiceover Engines
The agent must always direct the user to one of two professional external voice engines:
1. **Cartesian AI (`https://cartesian.ai`) — RECOMMENDED FREE TIER**:
   - Ultra-fast, hyper-realistic, human-grade conversational voiceover.
   - 100% free with natural breathing rhythms and conversational depth.
2. **ElevenLabs (`https://elevenlabs.io`) — PREMIUM PAID TIER**:
   - Industry-standard cinematic voice acting and emotional vocal inflection.

### 2.3 Mandatory 1-Click Clean Voiceover Script Mandate
Whenever the script is finalized, Antigravity MUST output a dedicated, highlighted code block titled:
`### 🎙️ 1-Click Ready-to-Copy Voiceover Script (for Cartesian AI / ElevenLabs)`

**Formatting Rules (CRITICAL)**:
- Pure spoken prose formatted with natural commas, question marks, and dramatic ellipsis pauses (`...`).
- **STRICTLY ZERO EXPRESSION TAGS**: Never include bracketed direction tags like `[excited]`, `[whisper]`, `[pause 2s]`, `[laughs]`. The script must be 100% clean so the user can copy and paste with a single click without editing!

Example Output Block:
```text
आप फोन खोलते हैं सिर्फ 5 मिनट के लिए... 
लेकिन देखते ही देखते, 2 घंटे गायब हो जाते हैं! 
गलती आपकी इच्छाशक्ति की नहीं है... 
बल्कि आपके दिमाग को एक खास तरीके से हैक किया गया है. 
हर रील स्वाइप पर, लास वेगास की स्लॉट मशीन जैसा डोपामिन खेल चल रहा है... 
लेकिन सिर्फ एक सेटिंग बदलने से, आप इस ट्रैप को हमेशा के लिए तोड़ सकते हैं!
```

### 2.4 Direct Cartesia MCP & CLI Integration (Automated Autopilot)
- **Active MCP Server**: `cartesia-mcp` registered in `~/.gemini/config/mcp_config.json`.
- **Active Local CLI**: `core/cartesia_tts.py` for direct in-session execution.
- **Automated Workflow**:
  ```bash
  python3 core/cartesia_tts.py --text "..." --out VO.wav --lang hi --voice rohan --emotion excited
  ```
- **Most Recommended Voices (Mandatory Default Presets)**:
  1. **`rohan`** (`4877b818-c7fe-4c89-b1cf-eadf8e23da72`): Rohan - Steady Communicator (Hindi Male, Documentary/Corporate)
  2. **`dev`** (`910fb75e-1d20-4840-ac63-ac6b26a71bdc`): Dev - Friendly Host (Hindi Male, Conversational & Warm)
  3. **`neha`** (`47f3bbb1-e98f-4e0c-92c5-5f0325e1e206`): Neha - Virtual Assistant (Hindi Female, Clear & Composed)
  4. **`anuj`** (`7e8cb11d-37af-476b-ab8f-25da99b18644`): Anuj - Engaging Narrator (Hindi Male, Storytelling & Dramatic)
  5. **`skylar`** (`db6b0ed5-d5d3-463d-ae85-518a07d3c2b4`): Skylar - Friendly Guide (English Female, Approachable & Clear)
  6. **`mariana`** (`ae823354-f9be-4aef-8543-f569644136b4`): Mariana - Nurturing Guide (Multilingual, Nurturing & Calm)
  - Secondary: `siya`, `sana`, `varun`, `sneha`, `kavya`, `barbershop`, `british`
- **Mandatory Emotion Control Feature (ALWAYS Active)**:
  - You MUST ALWAYS specify an emotion matching the narrative context:
    - 😐 `neutral`: Balanced, objective reporting
    - 😌 `calm`: Deep scientific reflection, peaceful interludes
    - 😊 `content`: Positive outcomes, resolutions, uplifting revelations
    - 🤩 `excited`: Viral hooks, dramatic breakthroughs, high energy
    - 😢 `sad`: Tragic moments, diagnoses, loss, setbacks
    - 😡 `angry`: Urgent warnings, existential threats, climate crisis
    - 🔍 `curious` / 🎭 `dramatic`: Thought experiments, mysteries, suspense
- Generates 44.1kHz broadcast WAV in **~1.0 to 1.5 seconds**, automatically saving to `VO.wav` and immediately launching `core/align.py` for 0ms audio-visual synchronization without requiring manual download or tab switching.

---

## 3. Google Flow Image Model Selection Hierarchy

When generating visual element assets, character sheets, and sticker cutouts on Google Flow:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                       FLOW IMAGE MODEL SELECTION TIER                       │
│                                                                             │
│   [PRIMARY MODEL - ALWAYS FIRST]                                            │
│   🍌 Nano Banana 2.1 (nano-banana-2)                                        │
│   • Ultra-fast generation, razor-sharp vector line art, pure solid colors.  │
│                                                                             │
│                        │ (If Quota / Rate Limit Reached)                    │
│                        ▼                                                    │
│   [FALLBACK MODEL - AUTOMATIC RESCUE]                                       │
│   🍌 Nano Banana Pro (nano-banana-pro)                                      │
│   • High-detail creative rendering, complex asset textures.                 │
│                                                                             │
│   🚫 HARD BANNED: Imagen 3 (imagen-3 / NARWHAL) — DO NOT USE IN ANTIGRAVITY │
└─────────────────────────────────────────────────────────────────────────────┘
```

1. **Step 1: Always dispatch on `🍌 Nano Banana 2.1` first**:
   - Set `model: "🍌 Nano Banana 2.1"` or `model: "nano-banana-2"` in `saif_ai_generate_image` or `core/elements.py`.
2. **Step 2: Monitor for limit / rate-limit responses**:
   - If Google Flow returns a quota exceeded, cooldown, or generation limit error on Nano Banana 2.1, Antigravity must seamlessly switch to **`🍌 Nano Banana Pro`** (`nano-banana-pro`).
3. **Step 3: Pure Solid Chroma Magenta `#FF00FF`**:
   - All element prompts MUST request solid flat pure magenta background with zero shadows and zero gradients, allowing instant physical unmixing in `core/key.py`.

---

## 4. Master Animation Directive: Activate ALL 8 Animation Skills

> **DIRECTORIAL CREED: "ANIMATION AND CREATIVITY IS EVERYTHING."**  
> Never treat animation as a secondary task or use lazy PowerPoint transitions. Antigravity MUST actively combine and orchestrate the techniques from ALL 8 bundled specialized skills inside the engine:

### 1. `hyperframes-animation` & `hyperframes-core`
- **GSAP-Grade Easing & Stagger**: Entrance stagger delays between layers ($80\text{ms}$ step).
- **Critically Damped Spring Physics**:
  $$\text{scale}(a) = 1.0 - (1.0 - a)^{3.2} \quad (0 \le a \le 1)$$
  Explosive pop entrance ($0 \to 85\%$ in $120\text{ms}$), followed by smooth noble deceleration. Zero tacky wobble.

### 2. `vox-doc`
- **Code-Drawn Vector Clocks**: Analog clocks with dynamically sweeping hands (`clock 12:05>2:00` or clock arrays `clocks 11`).
- **Dynamic Geographic Maps**: Coordinate pins dropping with expanding shockwave circles (`pin`).
- **Connecting Trajectory Rules**: Red animated dashed trajectory lines linking places (`rule delhi>mumbai`).
- **Meridian Longitude Rules**: Vertical alignment divider rules (`vline`).

### 3. `vox-doc-sound`
- **Word-Locked Foley SFX**: Tactile audio cues triggered on exact spoken words (gentle paper unfolds, subtle thumbtack drops, quiet pencil scribbles, scoreboard flips).
- **Broadcast Audio Ducking**: Sidechain compressor ducking atmospheric music bed under narration and Foley hits, mastered to **-14 LUFS** with a **-1.5 dBTP** ceiling.

### 4. `stickman-hybrid`
- **Expressive Postures**: Human characters with emotive, exaggerated postures (frantic panic, eureka epiphany, deep sorrow, triumphant cheer).
- **Bold Ink Line Art**: 2px thick black ink lines (`#2A2420`) over warm backgrounds, blending seamlessly with vibrant sticker pop-ins.
- **5 Curated Color Looks**: `bright`, `warm`, `ink`, `night`, `painted`.

### 5. `hand-drawn-doodle`
- **Warm Textured Canvas**: Fine waxy paper canvas (`#F8F6EF`).
- **Signature 3-Color Pastel Accents**: Sunflower Yellow (`#F2B705`), Cobalt Blue (`#2980B9`), Tomato Red (`#D9452B`).
- **Charming Childlike Sketchbook Doodles**: Playful arrows, circled words, wiggly underline emphasis.

### 6. `suspense-director`
- **5-Stage High-Completion Heartbeat Story Engine**: Golden Hook $\to$ Disrupt Assumptions $\to$ Insider Secrets $\to$ Ultimate Truth $\to$ Elevation & Debate.
- **CinemaScope Letterbox Snaps**: Dynamic aspect framing shifts.
- **Dramatic Evidence Board**: Red string connections, classified rubber stamps, dark noir chiaroscuro.

### 7. `embedded-captions`
- **User Permission Mandate**: NEVER generate or burn captions/subtitles without explicit user permission. Generate captions ONLY when the user explicitly requests them.
- **Zero-Millisecond Kinetic Typography**: High-contrast badges (`KEY`, `TAG`, `NUM`, `LBL`) shaped via libass (when requested).
- **Accented Syllable Pop**: Big highlighted numbers (`+250%`, `4s`, `1906 → 1948`) pop on the exact audio onset of that spoken syllable.

### 8. `retro-8bit-arcade-style`
- **Nostalgic Gamification Accents**: Pixel-art hearts, arcade level-up banners, coin counters, CRT scanline effects for tech/gaming topics.

### 9. `bang-motion` (The Mind-Blowing Anti-PPT Accretion & Kinetic Shock Engine)
- **The 5-Layer Forensic Accretion Stack**: Every scene builds like an investigative documentary evidence board (never a single lonely image):
  1. `Protagonist / Subject` enters via `slam` or `whipL`.
  2. `Evidence Document / Blueprint / Chart` draws underneath (`draw` / `sketch`).
  3. `Connecting Vector Trajectory` red dashed rule zips between points (`rule`).
  4. `Forensic Distressed Rubber Stamp Slam` (`stamp` e.g. "CLASSIFIED", "FATAL", "PROOF", "EXPOSED" at -12°) triggering visceral camera screen shake!
  5. `Optical Anamorphic Light Leaks` warm 35mm golden film burn sweeps across transition cuts.
- **Dynamic 2.5D Living Camera Universe**: Continuous organic camera breathing (`cam_drift` 1.0 -> 1.04) + high-frequency damped impact screen shudders ($\pm 14\text{px}$).
- **Disney Squash & Stretch (Elastic Jelly Physics)**: Fast velocity stretch ($s_x=1.20, s_y=0.92$) and impact rebound squash ($s_x=1.16, s_y=0.86$) with critically damped Remotion springs.
- **Push-Through 3D Wormholes (`push_through`)**: The camera plunges through keyholes, lenses, or documents at warp speed into the next scene.
- **Ambient Living Micro-Sway**: Held elements gently breathe with phase-shifted sinusoidal floating ($dy = \pm 3.5\text{px}$) so the entire frame is alive.

---

## 🚨 THE KILLER FEATURE: 100% ZERO-MILLISECOND VOICE-TO-ASSET SYNCHRONIZATION

> **THE SUPREME ENGINE LAW: THE VOICE AND THE ASSET MUST MATCH DOWN TO THE EXACT MILLISECOND.**  
> The single biggest differentiator that separates an amateur slideshow from an insane, viral documentary is **100% Audio-Visual Cohesion**. If the speaker mentions a concept, the viewer MUST see that exact bespoke visual instantly, and hear its tactile acoustic consequence simultaneously.

### 5.1 The Semantic Twin Law (Literal Visual Correspondence)
Every visual asset generated via Google Flow (`🍌 Nano Banana 2.1`) or local code must be a **100% literal and thematic twin** to the voiceover narration:
1. When narration says *"Assam tea gardens"*, the graphic must show tea pickers on green terraced hills—not a generic cup of tea.
2. When narration says *"1906 Standard Time"*, the graphic must display the exact colonial pendulum clock or train schedule—not a modern smartwatch.
3. When narration says *"Arjun couldn't sleep"*, the hero character asset must show bags under bloodshot eyes, clutching a glowing phone under blankets.
4. When narration says *"The 2-year death sentence"*, a physical 2-year hourglass or ticking countdown clock drops onto the exact syllable.

### 5.2 The Temporal 0.000s Acoustic Lock (`core/phonetic_align.py` & `core/align.py`)
- **Zero-Drift Standard**: Visual assets NEVER enter early or late.
- **Dynamic Acoustic Silence Snapping**: Scans the physical voice waveform using FFmpeg `silencedetect` to lock scene transitions and asset drops to natural breathing points and syllable onsets.
- Every spoken word in `WORDS.tsv` has its exact timestamp:
Run speech alignment on the user's voiceover:
```bash
python core/align.py --vo VO.wav --lines LINES.tsv
```
- Extracts word-level zero-millisecond timestamps into `WORDS.tsv`:
  ```tsv
  line	word_idx	start	end	word
  01	0	0.000	0.340	आप
  01	1	0.340	0.780	फोन
  01	2	0.780	1.120	खोलते
  01	3	1.120	1.380	हैं
  01	4	1.380	1.920	सिर्फ
  01	5	1.920	2.450	5
  01	6	2.450	2.880	मिनट
  ```
- **0ms Timing Rule**: The graphic for `"5 मिनट"` enters at frame $\text{round}(1.920 \times 24) = 46$ with zero latency ($0.000\text{s}$ drift).
- **Typography Badges**: Subtitles and highlight boxes (`KEY`, `TAG`, `NUM`, `LBL`) shaped via libass pop simultaneously on the accented syllable:
  $$\text{Dialogue: } 0, 0:00:01.92, 0:00:04.50, \text{NUM},,0,0,0,,{\backslash\text{pos}(540, 480)\backslash\text{fscx70}\backslash\text{fscy70}\backslash\text{t}(0, 220, \backslash\text{fscx100}\backslash\text{fscy100})\backslash\text{fad}(80, 100)}\text{5 मिनट}$$

---

## 6. Living Organic Motion & Physical Drop Shadows

### 6.1 Continuous Ken Burns Push-In & Organic Sway
To eliminate the dead "static PowerPoint" look, the camera continuously breathes using multi-harmonic sine/cosine trigonometry:

$$\text{Zoom}(t) = 1.0 + 0.075 \cdot \left(\frac{t - t_0}{t_1 - t_0}\right)$$
$$dy(t) = 2.8 \cdot \sin(1.25 t), \quad dx(t) = 1.4 \cdot \cos(0.95 t)$$

- **Parallax Layering**: Hero cutouts sway at $1.0\times$, background paper sways at $0.4\times$, and micro-badges float at $1.2\times$, creating natural 3D depth on a 2D canvas.

### 6.2 Tactile Physical Drop Shadows (`BoxBlur`)
Every sticker cutout is rendered with dual-tier physical contact shadows:
- **Contact Shadow**: Offset $(+3\text{px}, +5\text{px})$, `ImageFilter.BoxBlur(4)`, Opacity $40\%$. Anchors the asset firmly to the surface.
- **Ambient Occlusion**: Offset $(+14\text{px}, +20\text{px})$, `ImageFilter.BoxBlur(18)`, Opacity $22\%$. Gives the impression of a physical cutout hovering over real paper.

---

## 7. Rendering Speed & Kaggle GPU Offloading Protocol

### 7.1 The Render Speed Bottleneck
Compiling high-density documentary animation (`core/render.py shots`) involves:
- Sub-pixel LANCZOS scaling and alpha anti-aliasing.
- Dual-tier Gaussian drop shadow blurring (`ImageFilter.BoxBlur`).
- Kinetic Ken Burns transformations at 24/30 FPS.
- Complex libass Devanagari/Latin text shaping.

On dual-core laptops, ultrabooks, or low-power CPUs, rendering 30–50 scenes can take 15–30 minutes.

### 7.2 Kaggle GPU Prompt Rule (When Rendering is Slow)
If local CPU rendering is detected to take > 3 minutes (or if the user reports slow execution), the agent **MUST STOP AND PROACTIVELY OFFER KAGGLE GPU OFFLOADING**:

```markdown
> ⚡ **Rendering Speed Optimization Notice (Kaggle GPU Available)**:  
> Local CPU rendering for this high-density documentary will take ~[X] minutes.  
> Would you like to offload rendering to a free **Kaggle T4 / P100 GPU** (30x faster, ~45 seconds total)?  
>  
> **1-Click Kaggle Setup**:  
> 1. Open a new Kaggle Notebook (Set Accelerator to **GPU T4 x 2**).  
> 2. Upload your project folder or clone `saif_advance`.  
> 3. Paste and run this accelerated execution cell:  
>    ```bash  
>    !pip install -q faster-whisper pillow numpy scipy soundfile  
>    !apt-get install -y ffmpeg fonts-noto-core fonts-noto-ui-core  
>    !python core/render.py shots  
>    !python core/mix.py cues.tsv --vo VO.wav --words WORDS.tsv --lines VO.tsv --out mix.wav  
>    !python core/render.py master  
>    ```  
> 4. Download the finalized broadcast-ready `master.mp4`.
```

---

## 8. The Sacred 9-Step Production Protocol (Step-by-Step Codebase Rules)

Every production run must execute these 9 sequential stages without omitting any step:

```text
[Step 1] Style Selection ──► [Step 2] Trend References & Topic ──► [Step 3] Ratio, Time & Language
            │                                                                     │
[Step 6] Cutout & Keying ◄── [Step 5] Voice-Locked Assets  ◄── [Step 4] Script & Cartesian AI Voice
            │
[Step 7] Living Animation ──► [Step 8] Render, Soft SFX & Music ──► [Step 9] Broadcast Delivery
```

---

### Step 1: Visual Style Selection
The agent aligns with the user on one of 5 distinct visual signatures defined in `styles/` and `skills/`:
1. Sticker Style / Stickman Hybrid (`styles/stickman_hybrid.py`)
2. Crayon Doodle & Sketchbook (`skills/hand-drawn-doodle/`)
3. Vox Documentary Explainer (`styles/vox_documentary.py`)
4. Suspense / Noir Thriller (`styles/suspense_crime.py`)
5. Retro 8-Bit Arcade (`skills/retro-8bit-arcade-style/`)

---

### Step 2: Real-Time Trend Scanner (`core/trends.py`)
To ensure viral audience retention, the agent scans live discussion breakouts across **YouTube**, **Twitter/X**, and **Reddit**:
```bash
python core/trends.py --sources youtube,twitter,reddit
python core/trends.py --sources reddit      # Scans r/todayilearned, r/explainlikeimfive, r/science
python core/trends.py --topic "dopamine"   # Targeted cross-platform deep-dive
```
- **Virality Scoring Algorithm (`score_trend`)**:
  - Base Score: $50.0 \times \text{extra\_weight}$.
  - Viral Trigger Match ($+7.5$ pts each): `why`, `how`, `secret`, `truth`, `hidden`, `mystery`, `shock`, `forbidden`, `banned`, `myth`, `real reason`, `history`, `brain`, `paradox`, `solved`.
  - Length Optimization: Title length between 35 and 120 chars ($+10.0$ pts).
  - Question format ($?$) ($+6.0$ pts), Comparison format (`vs`, `:`) ($+5.0$ pts).
- Outputs top 3–5 trending hooks with proven curiosity gaps.

---

### Step 3: Aspect Ratio, Video Duration & Language Specification
- **Aspect Ratio (`VOX_ASPECT`)**:
  - `16:9` Landscape ($1920 \times 1080$) — YouTube documentary, desktop widescreen.
  - `9:16` Vertical ($1080 \times 1920$) — YouTube Shorts, Instagram Reels, TikTok (`VOX_VERTICAL=1`).
- **Duration**:
  - Short-form explainer: 45s – 65s (requires 25–35 visual events in `SCENES.tsv`).
  - Standard explainer: 90s – 180s (requires 40–80 visual events).
- **Language & Fonts (`VOX_FONT`)**:
  - Linux: `Noto Sans Devanagari` / `Kalam-Bold.ttf` / `PatrickHand-Regular.ttf`.
  - macOS: `Kohinoor Devanagari`.
  - Windows: `Nirmala UI`.

---

### Step 4: Script Formulation & Cartesian AI Voiceover Selection
- Formulates complete structured script with the 5-Stage Story Engine.
- Outputs the 1-click **Ready-to-Copy Clean Spoken Script** with zero expression tags.
- Directs user to paste into **Cartesian AI (Free)** or **ElevenLabs (Paid)** and save as `VO.wav`.

---

### Step 5: Voice-Locked Image & Asset Formulation on Google Flow (100% Parallel Wave)
- Runs `python core/align.py --vo VO.wav --lines LINES.tsv` to generate `WORDS.tsv`.
- Formulates Google Flow image prompts in `ELEMENTS.tsv` using **`🍌 Nano Banana 2.1`** (fallback: `🍌 Nano Banana Pro`).
- Sets background to solid flat pure chroma magenta `#FF00FF` for cutouts (or full-bleed 16:9/9:16 for cinematic backdrops).
- **MANDATORY ALL-AT-ONCE PARALLEL DISPATCH**: Dispatches ALL pending assets simultaneously in parallel via `python core/parallel_generator.py --model nano-banana-2 --speed 5` (or `python core/elements.py` / `saif_ai_batch_generate(mode="textToImage")`). NEVER dispatch serially or one-by-one!

---

### Step 6: Physical Cutout Extraction & Chroma Keying (`core/key.py` & `core/cutout.py`)
- Executes self-calibrating magenta alpha unmixing:
  $$\text{magentaness}(R, G, B) = \min(R, B) - G$$
  $$C = \alpha F + (1 - \alpha) B \implies \alpha = \frac{C - B}{F - B}$$
- Eliminates 100% of magenta halo fringes and exports transparent PNGs to `elements/cut/`.
- If on white, `core/cutout.py` flood-fills from borders and drops specks/Gemini spark watermarks.

---

### Step 7: Living Animation, Creativity & Procedural Elements (`core/render.py`)
- Orchestrates the 5-layer visual density stack in `SCENES.tsv`.
- Appends procedural vector clocks, map pins with shockwaves, dotted trajectory rules, meridian lines, and cross-outs.
- Embeds critically damped spring physics and living camera sway.

---

### Step 8: Rendering, Soft Foley SFX & Broadcast Ducking (`core/mix.py`)
- Executes multiprocessing scene rendering:
  ```bash
  python core/render.py plan     # Validates SCENES.tsv density and gaps
  python core/render.py shots    # Multiprocessing render to shots/NN.mp4
  ```
- Sound design mixing with Foley SFX peak-aligned on spoken words.
- Sidechain ducking of background music bed under narration, mastered to **-14 LUFS** (-1.5 dBTP ceiling).

---

### Step 9: Final Assembly & QC Delivery (`core/render.py master`)
```bash
python core/render.py master
# Or via assemble.py:
python core/assemble.py --shots shots --mix mix.wav --ass labels.ass --out master.mp4
```
- Concatenates video shots via ffmpeg demuxer.
- Muxes balanced broadcast audio.
- Burns Devanagari/Latin subtitles via `ass=labels.ass:shaping=complex`.
- Enables `+faststart` web-streaming optimization.

---

## 9. Compulsory SCENES.tsv Density Protocol

Videos produced by this engine MUST NOT suffer from visual starvation:

1. **Event Density Ratio**:
   $$\frac{\text{Total Visual Rows in SCENES.tsv}}{\text{Total Video Duration (seconds)}} \ge 0.45$$
2. **Maximum Time Gap**: No gap between element entrances ($t_{in}^{(n+1)} - t_{in}^{(n)}$) may exceed **2.2 seconds**.
3. **Automated Verification**:
   ```bash
   python core/render.py plan
   ```
   If any scene fails ($\text{gap} > 2.2\text{s}$ or $< 3$ elements), the engine flags a `LOW DENSITY` warning.

---

## 10. Pre-Execution Production Safety Checklist

Before running any script or generating assets, verify:

- [ ] **1. Production Pipeline Confirmed**: High-efficiency Google Flow Image Generation + External Voice + Local Compositing pipeline active.
- [ ] **2. Zero Veo Credits**: Confirmed that NO video models (`veo-omni`, `veo-quality`) will be dispatched on Google Flow.
- [ ] **3. Nano Banana Model Lock**: Using **`🍌 Nano Banana 2.1`** first (fallback: `🍌 Nano Banana Pro`). **Imagen 3 is strictly banned.**
- [ ] **4. External Voice Engine Directive**: User instructed to generate voiceover via **Cartesian AI (Free)** or **ElevenLabs (Paid)** with 1-click clean script. Zero local TTS synthesis.
- [ ] **5. All 8 Animation Skills Active**: Orchestrating clocks, map pins, shockwaves, spring physics, Foley SFX, and kinetic badges.
- [ ] **6. 0ms Audio-Visual Sync**: Are all elements and typography badges timed to exact word onsets via `WORDS.tsv`?
- [ ] **7. Density Verified**: Does `python core/render.py plan` report `PASS` with all inter-event gaps $\le 2.2\text{s}$?
- [ ] **8. Kaggle GPU Offered if Slow**: If local rendering exceeds 3 minutes, has Kaggle GPU offloading been offered?
- [ ] **9. Parallel Asset Generation Lock**: Confirmed that ALL assets are dispatched all at once in parallel via `core/parallel_generator.py` or `saif_ai_batch_generate(mode="textToImage")`. Zero serial one-by-one dispatch.

---

## 11. 🚨 UNIVERSAL MASTER PROMPTING ARCHITECTURE & GLOBAL REAL-WORLD REALISM

> **CORE MANDATE: UNIVERSAL REALISM & ZERO DUMMY/PLACEHOLDER ASSETS**  
> This engine is a **global, production-grade video production pipeline** built for creators worldwide across ANY topic, culture, geography, science, history, or genre.  
> Antigravity MUST NEVER generate flat, cartoonish, generic, or dummy placeholders. Every visual prompt sent to Google Flow (`🍌 Nano Banana 2.1` / `🍌 Nano Banana Pro`) must dynamically incorporate verified real-world facts, authentic cultural and historical details, accurate geographical topography, realistic forensic/technical components, and rich tactile depth.

---

### 11.1 The Universal Real-World Fidelity Standard (Global Across Any Domain)
Antigravity must ground every asset prompt in verified real-world accuracy tailored dynamically to whatever subject the user provides. The engine derives these details across five fundamental global pillars:

1. **Global Cartography & Geographical Accuracy (Any Nation / Region Worldwide)**:
   - When generating maps or geographic landscapes (e.g., Nepal, India, Japan, United States, Europe, Africa, or ancient empires):
   - **Mandate**: Render exact real-world geopolitical boundaries, authentic high-relief elevation gradients, accurate mountain massifs, true river systems, and authentic territorial contours. NEVER generate warped, imaginary, or cartoonish geography.
2. **Global Historical, Political & Cultural Figures (Any Era Worldwide)**:
   - When depicting real figures (e.g. ancient monarchs, modern leaders, scientists, philosophers, spiritual icons):
   - **Mandate**: Research and accurately reflect their verified likeness, authentic era-specific attire, traditional textiles, period-accurate weaponry/regalia, and verified cultural iconography. NEVER use generic medieval knight armor for Asian rulers, modern fabrics for ancient figures, or caricature emojis.
3. **Forensic & Investigative Authenticity (Crime, Mystery & Journalism)**:
   - When visualizing investigative themes:
   - **Mandate**: Incorporate realistic investigative artifacts — yellow acrylic evidence markers with scale grids, red-string corkboard conspiracy webs with Polaroid photos, cyan blueprint floorplans with vector trajectory lines, forensic UV luminol luminescence, and high-contrast noir chiaroscuro lighting.
4. **Scientific, Technological & Cosmological Fidelity**:
   - When visualizing science, medicine, AI, or cosmology:
   - **Mandate**: Incorporate verified scientific diagrams, real mathematical formulas, relativistic spacetime curvature geometries, accurate astronomical structures (accretion disks, lensing), and authentic technical interfaces.
5. **Atmospheric Environmental Realism (Horror, Drama & Cinema)**:
   - When visualizing cinematic or suspenseful scenes:
   - **Mandate**: Full-bleed edge-to-edge volumetric lighting, realistic dust motes, authentic surface textures (weathered brick, peeling paint, rain-slicked asphalt), and desaturated filmic color grading.

---

### 11.2 Dual Visual Rendering Paradigms: Mode A vs. Mode B

Google Flow image assets must be engineered into one of two distinct production modes based on narrative genre:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                       DUAL VISUAL RENDERING MODES                           │
│                                                                             │
│   MODE A: Full-Bleed Edge-to-Edge Canvas     MODE B: Isolated Sticker Cutout│
│   ┌───────────────────────────────────┐      ┌───────────────────────────┐  │
│   │ • Aspect Ratio: 16:9 or 9:16      │      │ • Aspect Ratio: 1:1       │  │
│   │ • Background: Real Scene/Painting │      │ • Background: Pure #FF00FF│  │
│   │ • ZERO Chroma Keying (No Cutout)  │      │ • Keyed via core/key.py   │  │
│   │ • Genre: Crime, Horror, History   │      │ • Genre: Vox, Stickman    │  │
│   │ • Ken Burns Camera Breathing      │      │ • Dual-Tier Drop Shadows  │  │
│   │ • Optical Light Leaks             │      │ • Kinetic Springs & Bumps │  │
│   └───────────────────────────────────┘      └───────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Mode A: Full-Bleed Edge-to-Edge Cinematic Canvas (`kind: "bg"` or `kind: "full"`)
- **Use When**: Creating immersive cinematic documentaries, murder mysteries, horror stories, epic historical battles, or cosmic explorations where an isolated sticker would look out of place or break immersion.
- **Aspect Ratio**: Locked to project format: **16:9 Landscape** (YouTube Widescreen) or **9:16 Vertical** (Shorts/Reels).
- **Background Directive**: **ZERO MAGENTA!** The prompt explicitly specifies an edge-to-edge detailed environment filling the entire 16:9/9:16 frame.
- **Engine Execution**:
  - Saved to `elements/raw/` or `backgrounds/`.
  - Registered in `SCENES.tsv` as `kind: "bg"`:
    ```tsv
    01  -  bg  crime_scene_alley  -  -  -  -  -  -
    ```
  - `core/render.py` automatically resolves and loads it through `background(name)`, sizing it edge-to-edge with continuous Ken Burns push-in (`cam_drift`), organic breathing, and optional anamorphic light leaks.

#### Mode B: Isolated Tactile Sticker & Evidence Cutout on Pure Magenta (`kind: "prop"`, `kind: "person"`, `kind: "doc"`, `kind: "stamp"`)
- **Use When**: Creating Vox explainers, stickman hybrid videos, whiteboard animations, forensic evidence collages, or dynamic multi-layer visual boards.
- **Aspect Ratio**: **1:1 Square**.
- **Background Directive**: Solid flat pure chroma magenta `#FF00FF` filling the entire background and showing through every interior gap and opening. The object contains 0% magenta.
- **Engine Execution**:
  - Keyed by `core/key.py` with physical alpha unmixing into `elements/cut/NN.png`.
  - Layered in `SCENES.tsv` with dual-tier tactile drop shadows (`BoxBlur`), Remotion spring physics, Disney squash & stretch, impact screen shake, and rubber stamp slams.

---

### 11.3 The Compulsory Asset Density & Abundance Standard
A high-retention video requires visual progression. Assets must **NEVER** be sparse, few in number, or reused lazily:

| Video Target Duration | Minimum Visual Assets | Visual Event Frequency | Maximum Idle Screen Gap |
| :--- | :--- | :--- | :--- |
| **Short-Form (45–60s)** | **25 to 35 Assets** | $\ge 0.50$ events/sec | $\le 1.8$ seconds |
| **Mid-Length (2–3 Min)** | **50 to 65 Assets** | $\ge 0.45$ events/sec | $\le 2.0$ seconds |
| **Deep Dive (4–5 Min)** | **75 to 95 Assets** | $\ge 0.40$ events/sec | $\le 2.2$ seconds |

- **5-Layer Accretion Rule**: Scenes must not consist of one lonely icon. Every visual beat stacks 3 to 5 layered elements:
  - Subject / Protagonist (Mode A canvas or Mode B cutout)
  - Forensic Document / Artifact / Map
  - Vector dashed trajectory rules / connecting pins
  - Kinetic stamp slam / metric callout badge
  - Atmospheric ambient drift and lighting

---

### 11.4 The 6-Part Dense Prompt Formula for Google Flow (`🍌 Nano Banana 2.1` / `Pro`)

Every single visual prompt formulated in `ELEMENTS.tsv` or dispatched to Google Flow MUST follow the **6-Part Dense Prompt Architecture**:

```text
Prompt = [1. Subject Core & Action] + 
         [2. Historical & Real-World Specificity] + 
         [3. Art Direction & Color Palette] + 
         [4. Tactile Micro-Textures & Physical Details] + 
         [5. Cinematic Camera Framing & Lighting] + 
         [6. Mode-Specific Format Lock]
```

#### Detailed Breakdown of the 6 Components:
1. **[Subject Core & Action]**:
   - The central object, person, or scene with active physical posture, emotional state, or physical interaction (e.g. *"Forensic detective in a dark trenchcoat examining bullet trajectory with magnifying glass"* rather than *"detective"*).
2. **[Historical & Real-World Specificity]**:
   - Verified chronological era, authentic attire, accurate architecture, real national emblems, exact geographical coordinates or topological features.
3. **[Art Direction & Color Palette]**:
   - Explicit visual aesthetic: Vintage warm muted tones, Noir chiaroscuro desaturation with harsh amber and cold steel blue, or 1970s tactile editorial print.
4. **[Tactile Micro-Textures & Physical Details]**:
   - Visible physical grain, aged parchment creases, oxidized brass, hand-stitched leather seams, chalk dust particles, weathered wood grain.
5. **[Cinematic Camera Framing & Lighting]**:
   - Dramatic chiaroscuro with sharp rim lighting, 35mm anamorphic lens with subtle shallow depth of field, overhead top-down forensic flat-lay, or low-angle hero Dutch tilt.
6. **[Mode-Specific Format Lock]**:
   - **For Mode A (Full-Bleed 16:9/9:16 Canvas)**:
     `full-bleed edge-to-edge 16:9 cinematic environmental shot, richly detailed atmospheric background filling the entire frame, seamless composition, zero cutout, no magenta, no borders, no frames, masterwork digital painting`
   - **For Mode B (Isolated Cutout on Magenta)**:
     `one single isolated object, centred with a generous margin of empty space on all sides, on a solid flat pure magenta background, uniform chroma key magenta hex #FF00FF filling the entire background and showing through every gap, hole and opening in the object, the object itself contains absolutely no magenta anywhere, hard crisp edges, sharply defined boundary against the background, no drop shadow, no cast shadow, no soft shadow, no glow, no blur, no feathered or faded edges, no reflection, no text, no letters, no numbers, no frame, no border, no background scenery`

---

### 11.5 Illustrative Reference Prompts Across Diverse Global Topics
*(These reference examples demonstrate how the universal 6-part formula is applied across diverse global domains — from ancient history and geography to modern forensics, cultural heritage, and astrophysics)*:

#### Example 1: Historical Realism (Mode B Cutout — Prithviraj Chauhan)
```text
Prithviraj Chauhan, 12th century Rajput warrior king of Chahamana dynasty, seated proudly on an ornate carved teakwood throne, wearing authentic 12th-century chainmail hauberk under a royal saffron angarakha, plumed royal battle turban with emerald sarpech, holding a curved damascus steel Talwar with traditional disc pommel, katar thrusting dagger at waist sash, fierce noble expression, warm vintage editorial illustration, rich gold and crimson accents, one single isolated object, centred with a generous margin of empty space on all sides, on a solid flat pure magenta background, uniform chroma key magenta hex #FF00FF filling the entire background and showing through every gap, hole and opening, object contains zero magenta, hard crisp edges, sharply defined boundary, no drop shadow, no blur, no text, no frame
```

#### Example 2: Crime Documentary (Mode A Full-Bleed 16:9 — Rainy Alley Crime Scene)
```text
Dark wet metropolitan alleyway at 3 AM in the pouring rain, yellow police caution tape stretching across brick walls, yellow numbered forensic evidence tents 1, 2, and 3 on wet asphalt next to chalk outline and spent brass bullet casings, reflection of red and blue police siren lights on pavement puddles, deep noir chiaroscuro shadows, steam rising from sewer grates, full-bleed edge-to-edge 16:9 cinematic environmental shot, richly detailed atmospheric background filling the entire frame, seamless composition, zero cutout, no magenta, no borders, no frames, masterwork forensic documentary painting
```

#### Example 3: Geographical Accuracy (Mode B Cutout — High-Relief Map of Nepal)
```text
Topographically accurate physical relief map of Nepal, showing exact international borders from Mechi to Mahakali, detailed elevation gradients with snow-capped Himalayan peaks of Mount Everest and Annapurna range along the north, lush green mid-hills and valleys in the center, and southern Terai lowlands, vintage tactile atlas cartography, subtle contour lines, warm muted sepia and emerald tones, one single isolated object, centred with a generous margin of empty space on all sides, on a solid flat pure magenta background, uniform chroma key magenta hex #FF00FF filling the entire background and showing through every gap, object contains zero magenta, hard crisp edges, sharply defined boundary, no drop shadow, no text, no frame
```

#### Example 4: Spiritual & Iconographic Accuracy (Mode B Cutout — Gautam Buddha)
```text
Gautam Buddha seated in full lotus padmasana posture upon an intricately carved double-lotus stone throne, hands held in serene dhyana mudra of deep meditation, wearing authentic saffron-ochre monastic Sanghati robes gracefully draped over the left shoulder, serene compassionate facial expression with gently lowered eyelids, distinct cranial ushnisha and elongated earlobes, sheltered beneath the broad leaves of the sacred Bodhi tree, soft warm golden transcendent halo radiating behind the head, traditional Mathura stone and Gupta art aesthetic, one single isolated object, centred with generous margin on all sides, on a solid flat pure magenta background, uniform chroma key magenta hex #FF00FF filling the entire background and showing through every gap, object contains zero magenta, hard crisp edges, sharply defined boundary, no drop shadow, no text, no frame
```

---

### 11.6 Compulsory All-At-Once Parallel Asset Generation Protocol (ZERO Serial Dispatch)

> **MANDATORY EFFICIENCY LAW: "DISPATCH ALL ASSETS ONCE IN PARALLEL"**  
> Whether a project requires 25 visual elements (60s short) or 90+ visual assets (5-min documentary), Antigravity and the generation pipeline MUST ALWAYS dispatch all assets **in a single parallel wave**. Serial generation (submitting asset 1, waiting, submitting asset 2, waiting...) is **STRICTLY PROHIBITED**.

1. **Upfront Batch Ingestion**:
   - Formulate all asset rows in `ELEMENTS.tsv` in one complete upfront pass.
   - Assign unique IDs (`01`, `02`, ..., `NN`) and verified prompts.
2. **Single-Wave Parallel Dispatch**:
   - Run the parallel generator:
     ```bash
     python3 core/parallel_generator.py --model nano-banana-2 --speed 5
     ```
     Or via `core/elements.py`:
     ```bash
     python3 core/elements.py
     ```
   - This command concurrently launches separate `subprocess.Popen` streams for every single row in `ELEMENTS.tsv`, queuing all jobs simultaneously into Google Flow's cluster queue.
3. **Cluster Monitoring & Instant Multi-Core Keying**:
   - The generator watches all downloads in parallel.
   - The instant downloads complete, `core/key.py` spawns worker processes across **all available CPU cores** to execute physical alpha unmixing on all assets concurrently.
   - Result: 50–90 assets are generated, downloaded, and transparently keyed in minutes rather than hours.

