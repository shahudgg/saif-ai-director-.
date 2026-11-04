"""VO.wav + VO.tsv -> WORDS.tsv   (the real time of every script word)

    python scripts/align.py --vo VO.wav --lines VO.tsv --out WORDS.tsv --lang hi

Each line is sent to faster-whisper on its own, with the script line as the
initial prompt so the spelling follows the script. The script's words are then
spread over Whisper's words in order: one to one when the counts match, by
proportion when they do not. mix.py word cues and the picture renderer both read
this file, so a click lands on the word that is spoken, not on a guessed time.
"""
import argparse, csv, subprocess
import numpy as np

SR = 16000


def pcm(vo, start, dur):
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-ss", str(start), "-t", str(dur),
                          "-i", vo, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         capture_output=True).stdout
    return np.frombuffer(raw, np.float32)


def spread(script_words, hits, t0, t1):
    """script words -> (start, end). Whisper's words in order, by proportion when counts differ."""
    n, m = len(script_words), len(hits)
    if not m:                                    # Whisper heard nothing: split by letters
        L = [len(w) + 1 for w in script_words]
        c = np.cumsum([0] + L) / sum(L)
        return [(t0 + (t1 - t0) * c[i], t0 + (t1 - t0) * c[i + 1]) for i in range(n)]
    out = []
    for i in range(n):
        a = hits[min(m - 1, int(i * m / n))]
        b = hits[min(m - 1, max(int(i * m / n), int((i + 1) * m / n) - 1))]
        out.append((a[0], b[1]))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--vo", required=True)
    ap.add_argument("--lines", required=True, help="VO.tsv: id, start, dur, text")
    ap.add_argument("--out", default="WORDS.tsv")
    ap.add_argument("--lang", default="hi", help="hi, en, es, ...")
    ap.add_argument("--model", default="medium", help="small is faster, medium is safer for Hindi")
    a = ap.parse_args()
    from faster_whisper import WhisperModel
    model = WhisperModel(a.model, device="cpu", compute_type="int8", cpu_threads=3)
    with open(a.lines, encoding="utf-8") as f:
        lines = list(csv.DictReader(f, delimiter="\t"))
    rows = []
    for ln in lines:
        s, d = float(ln["start"]), float(ln["dur"])
        segs, _ = model.transcribe(pcm(a.vo, max(0, s - 0.1), d + 0.2), language=a.lang,
                                   word_timestamps=True, initial_prompt=ln["text"],
                                   beam_size=5, condition_on_previous_text=False)
        hits = [(s - 0.1 + w.start, s - 0.1 + w.end) for g in segs for w in g.words]
        words = ln["text"].split()
        for i, (ws, we) in enumerate(spread(words, hits, s, s + d)):
            rows.append((ln["id"], i, round(ws, 3), round(we, 3), words[i]))
        print(f"line {ln['id']}: {len(words)} script / {len(hits)} heard", flush=True)
    with open(a.out, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n")
        w.writerow(["line", "i", "start", "end", "word"])
        w.writerows(rows)
    print(f"wrote {a.out} ({len(rows)} words)")


if __name__ == "__main__":
    assert spread(["a", "b"], [], 0, 2)[1][1] == 2
    assert spread(["a", "b", "c", "d"], [(0, 1), (1, 2)], 0, 2) == [(0, 1), (0, 1), (1, 2), (1, 2)]
    main()
