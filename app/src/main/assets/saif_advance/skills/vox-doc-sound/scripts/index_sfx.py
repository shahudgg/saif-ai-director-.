"""Scan your own sounds and music -> CATALOG.tsv (what exists, how loud, where the peak is)

    python scripts/index_sfx.py

Put sounds in sfx/<Category>/ (for example sfx/Whoosh/, sfx/Map/, sfx/Ambience/),
or sfx/<Source>/<Category>/ when you keep packs apart, and music in music/ or
music/<Mood>/. The folder name is the category. Re-run after adding files. Only
new or changed files are measured again.

Nothing is modified. Each file gets a `level_offset`, the gain that would bring
it to the kit standard:
- one-shot: peak at -3 dBFS
- loop / ambience: -30 LUFS
- music: -12 LUFS

mix.py adds that offset to the cue's gain_db, so the gains in SKILL.md (-19 for
a pin drop, -23 for a music bed) mean the same thing for every file, whatever
its source. The `peak` column lets mix.py land a one-shot's loudest moment on
its cue word.

A file counts as a loop when its category is Ambience/RoomTone, its name
contains "loop", or it is longer than 8 s. Every file in music/ is music.
"""
import csv, os, subprocess
import numpy as np

KIT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(KIT, "CATALOG.tsv")
AUDIO = (".wav", ".mp3", ".flac", ".ogg", ".m4a", ".aif", ".aiff")
COLS = ["file", "source", "category", "kind", "seconds", "peak", "level_offset", "use_for",
        "licence", "size", "mtime"]


def measure(path, kind):
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-ac", "1", "-ar", "22050",
                          "-f", "f32le", "-"], capture_output=True).stdout
    x = np.abs(np.frombuffer(raw, np.float32))
    if not len(x) or x.max() < 1e-5:
        return None                                   # silent or unreadable: leave it out
    secs, peak_t = len(x) / 22050, int(np.argmax(x)) / 22050
    if kind == "one":
        return secs, peak_t, -3 - 20 * np.log10(x.max())
    out = subprocess.run(["ffmpeg", "-i", path, "-af", "ebur128", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    lufs = float(out.split("I:")[-1].split("LUFS")[0])
    return secs, 0.0, (-12 if kind == "music" else -30) - lufs


def kind_of(rel, cat, secs_hint=None):
    if rel.startswith("music/"):
        return "music"
    name = os.path.basename(rel).lower()
    if cat.lower() in ("ambience", "ambient", "roomtone", "room tone") or "loop" in name:
        return "loop"
    return "loop" if (secs_hint or 0) > 8 else "one"


def main():
    old = {}
    if os.path.exists(OUT):
        with open(OUT, encoding="utf-8") as f:
            old = {r["file"]: r for r in csv.DictReader(f, delimiter="\t")}
    rows, new, skipped = [], 0, []
    for top in ("sfx", "music"):
        for d, _, files in os.walk(os.path.join(KIT, top)):
            for fn in sorted(files):
                if not fn.lower().endswith(AUDIO) or fn.startswith("_"):
                    continue
                p = os.path.join(d, fn)
                rel = os.path.relpath(p, KIT).replace("\\", "/")
                parts = rel.split("/")
                st = os.stat(p)
                prev = old.get(rel)
                if prev and prev.get("size") == str(st.st_size) and prev.get("mtime") == f"{st.st_mtime:.0f}":
                    rows.append([prev.get(c, "") for c in COLS])      # unchanged: keep the old measurement
                    continue
                cat = parts[-2] if len(parts) > 2 else top
                source = parts[1] if len(parts) > 3 else "own"
                dur = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                            "-of", "csv=p=0", p], capture_output=True, text=True).stdout or 0)
                kind = kind_of(rel, cat, dur)
                m = measure(p, kind)
                if not m:
                    skipped.append(rel)
                    continue
                secs, peak_t, off = m
                rows.append([rel, source, cat, kind, f"{secs:.2f}", f"{peak_t:.3f}", f"{off:.1f}",
                             (prev or {}).get("use_for", ""), (prev or {}).get("licence", ""),
                             str(st.st_size), f"{st.st_mtime:.0f}"])
                new += 1
    with open(OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n")
        w.writerow(COLS)
        w.writerows(rows)
    print(f"CATALOG.tsv: {len(rows)} files ({new} measured now)")
    if skipped:
        print(f"left out, silent or unreadable: {', '.join(skipped)}")


if __name__ == "__main__":
    assert kind_of("music/a.mp3", "music") == "music"
    assert kind_of("sfx/Ambience/rain.mp3", "Ambience") == "loop"
    assert kind_of("sfx/Whoosh/w.wav", "Whoosh", 0.6) == "one"
    assert kind_of("sfx/Whoosh/w.wav", "Whoosh", 20) == "loop"
    main()
