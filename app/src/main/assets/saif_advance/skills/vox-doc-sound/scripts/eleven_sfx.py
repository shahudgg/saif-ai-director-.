"""Make sound effects with ElevenLabs (your own key) -> sfx/<Category>/, then re-index.

    python scripts/eleven_sfx.py "a soft map pin dropping onto paper" --seconds 0.6 --cat Map --name pin_drop_a
    python scripts/eleven_sfx.py --batch sfx/STARTER.tsv          # the starter library, skips what exists

Key: put ELEVENLABS_API_KEY=... in a file named .env in the skill folder, or set
it as an environment variable. Get it at https://elevenlabs.io/app/settings/api-keys.
Never paste it into a chat.

Licence: on a PAID ElevenLabs plan you may use the sounds in commercial videos.
On the free plan they are for non-commercial use only. On any plan you may NOT
share or sell the sound files themselves (ElevenLabs Prohibited Use Policy 9(c),
https://elevenlabs.io/use-policy).

Cost: roughly 5-10 credits per second of sound. The starter list (36 sounds,
about 86 s of audio) costs roughly 500-900 credits.
"""
import argparse, csv, json, os, subprocess, sys, urllib.error, urllib.request

KIT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
URL = "https://api.elevenlabs.io/v1/sound-generation"


def key():
    k = os.environ.get("ELEVENLABS_API_KEY")
    env = os.path.join(KIT, ".env")
    if not k and os.path.exists(env):
        for line in open(env, encoding="utf-8"):
            if line.strip().startswith("ELEVENLABS_API_KEY="):
                k = line.split("=", 1)[1].strip()
    if not k:
        sys.exit("No ELEVENLABS_API_KEY. Put it in .env in the skill folder "
                 "(https://elevenlabs.io/app/settings/api-keys).")
    return k


def peak_db(path):
    out = subprocess.run(["ffmpeg", "-i", path, "-af", "volumedetect", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    return float(out.split("max_volume:")[1].split("dB")[0]) if "max_volume:" in out else -99.0


def make(prompt, seconds, cat, name, loop):
    dst = os.path.join(KIT, "sfx", cat, f"{name}.mp3")
    if os.path.exists(dst):
        print(f"[{cat}/{name}] exists, skipped")
        return
    body = {"text": prompt, "model_id": "eleven_text_to_sound_v2", "prompt_influence": 0.3,
            "duration_seconds": seconds, "output_format": "mp3_44100_192"}
    if loop:
        body["loop"] = True
    req = urllib.request.Request(URL, data=json.dumps(body).encode(), method="POST",
                                 headers={"xi-api-key": key(), "Content-Type": "application/json"})
    try:
        audio = urllib.request.urlopen(req, timeout=300).read()
    except urllib.error.HTTPError as e:
        sys.exit(f"ElevenLabs error {e.code}: {e.read().decode('utf-8', 'replace')[:300]}")
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    open(dst, "wb").write(audio)
    if peak_db(dst) < -40:                         # a known failure: near-silent output
        os.remove(dst)
        print(f"[{cat}/{name}] came back nearly silent -- removed; run again to retry")
        return
    print(f"[{cat}/{name}] made ({seconds}s)")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("prompt", nargs="?")
    ap.add_argument("--seconds", type=float, help="0.5-30")
    ap.add_argument("--cat", help="category folder, e.g. Map, Whoosh, Ambience")
    ap.add_argument("--name")
    ap.add_argument("--loop", action="store_true", help="seamless loop, for ambience")
    ap.add_argument("--batch", help="TSV with columns category, name, seconds, loop, prompt")
    a = ap.parse_args()
    if a.batch:
        with open(a.batch, encoding="utf-8") as f:
            for r in csv.DictReader(f, delimiter="\t"):
                make(r["prompt"], float(r["seconds"]), r["category"], r["name"], r["loop"] == "1")
    else:
        if not (a.prompt and a.seconds and a.cat and a.name):
            ap.error("give a prompt with --seconds, --cat and --name, or use --batch")
        make(a.prompt, a.seconds, a.cat, a.name, a.loop)
    subprocess.run([sys.executable, os.path.join(KIT, "scripts", "index_sfx.py")])


if __name__ == "__main__":
    main()
