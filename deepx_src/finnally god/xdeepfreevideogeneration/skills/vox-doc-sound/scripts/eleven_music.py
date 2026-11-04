"""Make an instrumental music bed with ElevenLabs Music (your own key) -> music/<mood>/, then re-index.

    python scripts/eleven_music.py "curious documentary explainer music, pizzicato and marimba, 100 BPM" \
        --seconds 90 --mood explainer --name explainer_a

Prompts that work are in music/README.md. The script appends an
"instrumental, sits under a voiceover" tail and sets force_instrumental.

Cost: music is expensive, about 900 credits for a 75-90 s track. Check your
balance first.

Licence (see https://elevenlabs.io/eleven-music-model-specific-terms): paid
plans may use the music in their own videos, and the Starter plan excludes
film, TV, radio and games. You may NOT upload the tracks to streaming services
or share them as a music library.
"""
import argparse, json, os, subprocess, sys, urllib.error, urllib.request

KIT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
URL = "https://api.elevenlabs.io/v1/music?output_format=mp3_44100_192"
TAIL = (", instrumental only, no vocals, no choir, sparse arrangement that sits under a "
        "voiceover, no loud drums, clean natural ending, high quality stereo mix")


def key():
    k = os.environ.get("ELEVENLABS_API_KEY")
    env = os.path.join(KIT, ".env")
    if not k and os.path.exists(env):
        for line in open(env, encoding="utf-8"):
            if line.strip().startswith("ELEVENLABS_API_KEY="):
                k = line.split("=", 1)[1].strip()
    if not k:
        sys.exit("No ELEVENLABS_API_KEY (https://elevenlabs.io/app/settings/api-keys).")
    return k


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("prompt")
    ap.add_argument("--seconds", type=float, default=90, help="3-600")
    ap.add_argument("--mood", default="beds", help="sub-folder of music/, e.g. explainer, tension")
    ap.add_argument("--name", required=True)
    a = ap.parse_args()
    body = {"prompt": a.prompt + TAIL, "music_length_ms": int(a.seconds * 1000), "force_instrumental": True}
    req = urllib.request.Request(URL, data=json.dumps(body).encode(), method="POST",
                                 headers={"xi-api-key": key(), "Content-Type": "application/json"})
    try:
        audio = urllib.request.urlopen(req, timeout=600).read()
    except urllib.error.HTTPError as e:
        sys.exit(f"ElevenLabs error {e.code}: {e.read().decode('utf-8', 'replace')[:400]}")
    if len(audio) < 10000:
        sys.exit(f"Response too small ({len(audio)} bytes) -- not audio.")
    dst = os.path.join(KIT, "music", a.mood, f"{a.name}.mp3")
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    open(dst, "wb").write(audio)
    print(f"made music/{a.mood}/{a.name}.mp3")
    subprocess.run([sys.executable, os.path.join(KIT, "scripts", "index_sfx.py")])


if __name__ == "__main__":
    main()
