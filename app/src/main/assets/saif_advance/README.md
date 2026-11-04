# SAIF AI Advance Studio

The all-in-one AI documentary explainer and stickman animation production engine.

## Quickstart
1. Clone or extract `saif_advance.zip`.
2. Copy a template:
   ```bash
   cp -r templates/historical_vox_template my_project
   cd my_project
   ```
3. Put your voiceover as `VO.wav` and run:
   ```bash
   python ../core/align.py --vo VO.wav --lines LINES.tsv
   python ../core/render.py shots
   python ../core/mix.py cues.tsv --vo VO.wav --words WORDS.tsv --lines VO.tsv --out mix.wav
   python ../core/render.py master
   ```

Read `ANTIGRAVITY.md` for the full 9-step production protocol, real-time trend scanner (`core/trends.py`), Cartesian AI voiceover workflow, and HyperFrames motion standards.

