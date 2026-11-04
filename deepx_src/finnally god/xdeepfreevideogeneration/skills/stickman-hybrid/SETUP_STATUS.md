# Setup Status: stickman-hybrid

| Step | Component | Status | Version / Details |
|---|---|---|---|
| 0 | Machine Profile | ✅ | Linux x86_64, 4GB RAM, Intel GPU (Weak machine profile - custom VO mode) |
| 1 | Flow Connector CLI | ✅ | flow-connector-cli 1.0.12, agent skill installed |
| 2 | FFmpeg & libass | ✅ | ffmpeg 7.0.2 static with complex Hindi libass shaping |
| 3 | Python & Core Libs | ✅ | Python 3.12, numpy, PIL, scipy, soundfile, faster-whisper |
| 3b| LaMa Watermark Clean| ✅ | Smooth fallback & PyTorch CPU enabled |
| 4 | Voiceover Engine | ✅ | User provided custom audio / recording |
| 5 | Hugging Face | ⏩ | Skipped (not required for custom audio) |
| 6 | Local Parler-TTS | ⏩ | Skipped (user provides audio) |
| 7 | Kaggle GPU | ⏩ | Skipped (user provides audio) |
| 8 | Sound Kit & SFX | ✅ | CATALOG.tsv generated with 1,053 sound effects & music beds |
