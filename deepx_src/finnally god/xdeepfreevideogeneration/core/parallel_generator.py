# -*- coding: utf-8 -*-
"""parallel_generator.py: Ultra-Fast Parallel Asset Generator for Google Flow.

Concurrently dispatches and monitors image asset generation on Google Flow cluster
(Banana 2.1 / Nano Banana Pro) with 4-tier deduplication, zero-repeat checks,
and automatic multi-core chroma-keying.
"""

import argparse
import csv
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import time
from typing import List, Dict

FLOW_BIN = shutil.which("flow-auto") or "flow-auto"
FOLDER = re.sub(r"[^A-Za-z0-9_-]+", "_", os.path.basename(os.getcwd())).strip("_").lower() or "voxdoc"
DL_CANDIDATES = ([os.environ["FLOW_DOWNLOADS"]] if os.environ.get("FLOW_DOWNLOADS") else
                 [os.path.expanduser("~/Downloads"),
                  os.path.expanduser(f"~/Downloads/{FOLDER}"),
                  os.path.expanduser("~/Downloads/deepx_renders"),
                  os.path.expanduser(f"~/Downloads/deepx_renders/{FOLDER}")] +
                 glob.glob(os.path.expanduser("~/Downloads/*/")))

RAW_DIR = "elements/raw"
CUT_DIR = "elements/cut"

MAGENTA_PROMPT = (
    "{obj}, bold flat vector illustration with a thin dark outline, Vox editorial explainer "
    "style, warm muted vintage colours, one single isolated object, centred with a generous margin of empty space on "
    "all sides, on a solid flat pure magenta background, uniform chroma key magenta hex "
    "#FF00FF filling the entire background and showing through every gap, hole and opening "
    "in the object, the object itself contains absolutely no magenta anywhere, hard crisp "
    "edges, sharply defined boundary against the background, no drop shadow, no cast "
    "shadow, no soft shadow, no glow, no blur, no feathered or faded edges, no reflection, "
    "no text, no letters, no numbers, no frame, no border, no background scenery"
)


class ParallelAssetGenerator:
    """Dispatches all pending image generation jobs concurrently to Google Flow."""

    def __init__(self, elements_tsv: str = "ELEMENTS.tsv", model: str = "nano-banana-2", speed: int = 5):
        self.elements_tsv = elements_tsv
        self.model = model
        self.speed = speed
        os.makedirs(RAW_DIR, exist_ok=True)
        os.makedirs(CUT_DIR, exist_ok=True)

    def load_rows(self) -> List[Dict]:
        if not os.path.exists(self.elements_tsv):
            return []
        with open(self.elements_tsv, encoding="utf-8") as f:
            return list(csv.DictReader(f, delimiter="\t"))

    def get_pending(self) -> List[Dict]:
        rows = self.load_rows()
        try:
            from core.dedupe_guard import get_pending_assets
            return get_pending_assets(rows)
        except Exception as e:
            pending = []
            for r in rows:
                aid = str(r['id']).zfill(2)
                if not (glob.glob(f"{CUT_DIR}/{aid}.png") or glob.glob(f"{RAW_DIR}/{aid}.*")):
                    pending.append(r)
            return pending

    def dispatch_all_parallel(self, max_concurrent: int = 8) -> int:
        pending = self.get_pending()
        if not pending:
            print("✓ All assets already generated and ready — ZERO duplicate generation dispatched!")
            return 0

        print(f"🚀 Parallel Dispatcher: Submitting {len(pending)} assets concurrently to Google Flow (speed={self.speed})...")
        from core.dedupe_guard import register_dispatched

        processes = []
        for r in pending:
            aid = str(r['id']).zfill(2)
            raw_obj = r.get('prompt') or r.get('object') or r.get('name') or ''
            is_full = "#FF00FF" in raw_obj or "magenta" in raw_obj.lower() or "full-bleed" in raw_obj.lower()
            obj_prompt = raw_obj if is_full else MAGENTA_PROMPT.format(obj=raw_obj)
            ar = r.get("aspect_ratio") or ("16:9" if "full-bleed" in raw_obj.lower() or r.get("kind") == "bg" else "1:1")
            register_dispatched(aid, obj_prompt)

            cmd = [
                FLOW_BIN, "generate", "create", "flow-text-to-image",
                "--prompt", obj_prompt,
                "--model", self.model,
                "--aspect-ratio", ar,
                "--outputs", "1",
                "--index", str(int(r["id"])),
                "--folder", FOLDER,
                "--speed", str(self.speed),
                "--batch", str(max(len(pending), 8)),
                "--image-delay", "0",
                "--delay", "2-5"
            ]
            # Spawn in parallel
            p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            processes.append((aid, p))
            print(f"  [+] Dispatched asset #{aid} ({raw_obj[:35]}...)")

        # Wait for submission commands to register in flow-auto queue
        for aid, p in processes:
            stdout, stderr = p.communicate()
            if p.returncode != 0:
                print(f"  [!] Warning on asset #{aid}: {stderr[:120]}")

        print(f"✓ All {len(pending)} assets queued into Google Flow! Monitoring downloads...")
        return len(pending)

    def watch_and_sync(self, timeout_sec: int = 1800) -> int:
        """Watches Google Flow generation and copies completed downloads."""
        end_time = time.time() + timeout_sec
        rows = self.load_rows()
        synced_count = 0

        while time.time() < end_time:
            # Check flow-auto list
            try:
                res = subprocess.run([FLOW_BIN, "generate", "list"], capture_output=True, text=True)
                busy = len(re.findall(r"(?m)^\S{8}\S*\s+(queued|running|downloading)\b", res.stdout or ""))
                print(f"  Flow Cluster: {busy} assets in flight...", flush=True)
            except Exception:
                busy = 0

            # Scan downloads and copy hits
            for r in rows:
                aid = str(r['id']).zfill(2)
                raw_dest = f"{RAW_DIR}/{aid}.jpg"
                if os.path.exists(raw_dest) or glob.glob(f"{RAW_DIR}/{aid}.*"):
                    continue

                hits = []
                for dl in DL_CANDIDATES:
                    if os.path.exists(dl):
                        matches = [
                            q for q in glob.glob(os.path.join(dl, "*"))
                            if re.match(rf"{int(r['id'])}\s*-\s*", os.path.basename(q))
                        ]
                        hits.extend(matches)
                if hits:
                    best = max(hits, key=os.path.getmtime)
                    ext = os.path.splitext(best)[1] or ".jpg"
                    dest = f"{RAW_DIR}/{aid}{ext}"
                    shutil.copy(best, dest)
                    print(f"  [✓] Arrived #{aid} -> {dest}")
                    synced_count += 1

            # Check if all rows now have raw or cut
            all_done = all(
                glob.glob(f"{CUT_DIR}/{str(r['id']).zfill(2)}.png") or
                glob.glob(f"{RAW_DIR}/{str(r['id']).zfill(2)}.*")
                for r in rows
            )
            if all_done or (busy == 0 and synced_count > 0):
                break
            time.sleep(5)

        return synced_count

    def auto_parallel_key(self):
        """Runs parallel chroma-keying on all raw assets across all CPU cores."""
        key_script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "key.py")
        subprocess.run([sys.executable, key_script, RAW_DIR, CUT_DIR], check=True)
        from core.dedupe_guard import mark_asset_completed
        for r in self.load_rows():
            aid = str(r['id']).zfill(2)
            if glob.glob(f"{CUT_DIR}/{aid}.png"):
                mark_asset_completed(aid)
        print("✓ All raw assets keyed into elements/cut/ in parallel!")


def run_parallel_pipeline(model: str = "nano-banana-2", speed: int = 5):
    gen = ParallelAssetGenerator(model=model, speed=speed)
    dispatched = gen.dispatch_all_parallel()
    if dispatched > 0:
        gen.watch_and_sync()
    gen.auto_parallel_key()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Parallel Google Flow Asset Generator")
    parser.add_argument("--model", default="nano-banana-2", choices=["nano-banana-2", "nano-banana-pro"])
    parser.add_argument("--speed", type=int, default=5, help="Speed 1-5 (default: 5)")
    args = parser.parse_args()
    run_parallel_pipeline(model=args.model, speed=args.speed)
