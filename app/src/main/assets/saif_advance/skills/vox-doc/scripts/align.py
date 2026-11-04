"""VO.wav + VO.tsv -> WORDS.tsv   (script के हर शब्द का असली समय)

    python align.py

हर line अलग से faster-whisper को दी जाती है, script की line ही initial_prompt है,
ताकि वर्तनी script जैसी आए। फिर script के शब्द whisper के शब्दों पर क्रम से बाँटे
जाते हैं -- गिनती बराबर हो तो सीधा, न हो तो अनुपात से। Scene का हर element इसी
समय पर आता है: "चार बजे" बोला गया उसी frame पर घड़ी 4 दिखाती है।
"""
import csv, io, subprocess, sys
import numpy as np
from faster_whisper import WhisperModel

SR = 16000
# python align.py --vo aman  -> VO_aman.wav + VO_aman.tsv -> WORDS_aman.tsv
SUF = "_" + sys.argv[sys.argv.index("--vo") + 1] if "--vo" in sys.argv else ""


def pcm(start, dur):
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-ss", str(start), "-t", str(dur),
                          "-i", f"VO{SUF}.wav", "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         capture_output=True).stdout
    return np.frombuffer(raw, np.float32)


def spread(script_words, hits, t0, t1):
    """script के शब्द -> (start, end). whisper के शब्द क्रम से, गिनती अलग हो तो अनुपात से।"""
    n, m = len(script_words), len(hits)
    if not m:                                    # whisper चुप -- अक्षरों के अनुपात से बाँटो
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
    model = WhisperModel("medium", device="cpu", compute_type="int8", cpu_threads=3)
    with open(f"VO{SUF}.tsv", encoding="utf-8") as f:
        lines = list(csv.DictReader(f, delimiter="\t"))
    rows = []
    for ln in lines:
        s, d = float(ln["start"]), float(ln["dur"])
        audio = pcm(max(0, s - 0.1), d + 0.2)
        segs, _ = model.transcribe(audio, language="hi", word_timestamps=True,
                                   initial_prompt=ln["text"], beam_size=5,
                                   condition_on_previous_text=False)
        hits = [(s - 0.1 + w.start, s - 0.1 + w.end) for g in segs for w in g.words]
        words = ln["text"].split()
        for i, (ws, we) in enumerate(spread(words, hits, s, s + d)):
            rows.append((ln["id"], i, round(ws, 3), round(we, 3), words[i]))
        print(f"line {ln['id']}: {len(words)} script / {len(hits)} whisper words", flush=True)
    with open(f"WORDS{SUF}.tsv", "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter="\t")
        w.writerow(["line", "i", "start", "end", "word"])
        w.writerows(rows)
    print(f"wrote WORDS{SUF}.tsv ({len(rows)} words)")


if __name__ == "__main__":
    assert spread(["a", "b"], [], 0, 2)[1][1] == 2
    assert spread(["a", "b", "c", "d"], [(0, 1), (1, 2)], 0, 2) == [(0, 1), (0, 1), (1, 2), (1, 2)]
    main()
