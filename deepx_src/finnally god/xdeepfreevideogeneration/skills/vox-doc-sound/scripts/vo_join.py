"""takes/NN.wav + LINES.tsv -> VO.wav + VO.tsv

    python scripts/vo_join.py takes LINES.tsv --out VO

LINES.tsv columns: id, text, gap   (gap = seconds of silence AFTER that line)

Each take is trimmed of its leading and trailing silence first. TTS engines
pad their output unevenly; without the trim, one voice of the same script came
out 128 s instead of 121 s and every pause sounded random. The gaps you write
are the only pauses left.
"""
import argparse, csv
import numpy as np, soundfile as sf

SR = 44100
FLOOR = 0.004          # below this |amplitude| counts as silence


def trim(a):
    loud = np.abs(a) > FLOOR
    if not loud.any():
        return a[:0]
    return a[np.argmax(loud):len(a) - np.argmax(loud[::-1])]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("takes", help="folder with one wav per line, named by id (01.wav ...)")
    ap.add_argument("lines", help="LINES.tsv: id, text, gap")
    ap.add_argument("--out", default="VO", help="writes <out>.wav and <out>.tsv")
    a = ap.parse_args()
    with open(a.lines, encoding="utf-8") as f:
        rows = list(csv.DictReader(f, delimiter="\t"))
    track, out, t = [], [], 0.0
    for r in rows:
        x, sr = sf.read(f"{a.takes}/{r['id']}.wav", dtype="float32")
        if x.ndim > 1:
            x = x.mean(axis=1)
        if sr != SR:
            n = int(len(x) * SR / sr)
            x = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype(np.float32)
        x = trim(x)
        gap = float(r.get("gap") or 0.4)
        out.append((r["id"], round(t, 3), round(len(x) / SR, 3), r["text"]))
        track += [x, np.zeros(int(gap * SR), np.float32)]
        t += len(x) / SR + int(gap * SR) / SR
    sf.write(f"{a.out}.wav", np.concatenate(track), SR)
    with open(f"{a.out}.tsv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n")
        w.writerow(["id", "start", "dur", "text"])
        w.writerows(out)
    print(f"wrote {a.out}.wav ({t:.2f}s, {len(out)} lines) and {a.out}.tsv")


if __name__ == "__main__":
    z = np.zeros(10, np.float32)
    assert len(trim(np.concatenate([z, np.ones(5, np.float32), z]))) == 5
    main()
