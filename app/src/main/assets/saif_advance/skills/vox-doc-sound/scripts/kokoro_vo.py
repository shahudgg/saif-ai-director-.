"""LINES.tsv -> takes/NN.wav with Kokoro (local, CPU, Apache 2.0, commercial OK)

    python scripts/kokoro_vo.py LINES.tsv --lang a --voice am_michael --out takes

--lang   a American English · b British English · e Spanish · f French
         i Italian · p Brazilian Portuguese · h Hindi · j Japanese · z Mandarin
         (j needs: python -m pip install "misaki[ja]"; z needs "misaki[zh]")
--voice  e.g. af_heart, af_bella, am_michael, am_fenrir (US); bf_emma, bm_george (UK).
         The full list is at https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md
--speed  1.0 is normal; 0.95 reads as a calmer documentary pace

Kokoro's Hindi works but sounds flat. For Hindi and other Indian languages use
Indic Parler-TTS (kaggle/parler_vo). Then join the takes with vo_join.py.
"""
import argparse, csv, os
import numpy as np, soundfile as sf


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("lines")
    ap.add_argument("--lang", default="a")
    ap.add_argument("--voice", default="am_michael")
    ap.add_argument("--speed", type=float, default=1.0)
    ap.add_argument("--out", default="takes")
    a = ap.parse_args()
    from kokoro import KPipeline
    pipe = KPipeline(lang_code=a.lang)
    os.makedirs(a.out, exist_ok=True)
    with open(a.lines, encoding="utf-8") as f:
        rows = list(csv.DictReader(f, delimiter="\t"))
    for r in rows:
        p = f"{a.out}/{r['id']}.wav"
        if os.path.exists(p):
            continue                     # re-run safe: only missing lines are made
        audio = [np.asarray(x[2], np.float32) for x in pipe(r["text"], voice=a.voice, speed=a.speed)]
        sf.write(p, np.concatenate(audio), 24000)
        print(f"[{r['id']}] {sum(len(x) for x in audio) / 24000:.2f}s", flush=True)
    print(f"takes in {a.out}/ -- next: python scripts/vo_join.py {a.out} {a.lines}")


if __name__ == "__main__":
    main()
