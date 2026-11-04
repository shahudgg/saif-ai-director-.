# Setup & System Prerequisites

## 1. System Packages
Ensure ffmpeg and font utilities are installed:
```bash
sudo apt update && sudo apt install -y ffmpeg fonts-noto-core fonts-noto-ui-core
```

## 2. Python Dependencies
Install required Python libraries:
```bash
pip install faster-whisper pillow numpy scipy soundfile
```

## 3. Google Flow Bridge Connection
Verify DeepX Flow Bridge MCP connection:
- Ensure Google Chrome or Kiwi Browser is running with `flow.google.com` open.
- The DeepX bridge listens on `ws://127.0.0.1:8792/ws`.
- Check status via `deepx_status` tool.

## 4. Verification Check
Run self-check on the core engine:
```bash
python core/align.py --help
python core/trends.py --selfcheck
python core/render.py selfcheck
python core/mix.py --selfcheck
python core/cutout.py --demo
python core/key.py --demo
```
