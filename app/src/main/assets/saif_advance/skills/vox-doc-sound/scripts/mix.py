"""cues.tsv + VO.wav -> mix.wav   (Vox-style documentary mix)

    python scripts/mix.py cues.tsv --vo VO.wav --lines VO.tsv --words WORDS.tsv --out mix.wav
    python scripts/mix.py --selfcheck

cues.tsv columns (tab separated):
    label    any name. The row labelled "bed" is the music bed: it gets ducked by
             every SFX and by the voice, and it gets the optional --dip
    kind     loop | one       loop = repeat the file for `dur` seconds
    file     path relative to the kit folder (sfx/..., music/...) or absolute
    at       WHEN it starts. One of:
               12.4         seconds
               @05          the start of VO line 05
               भारत         the first spoken word containing "भारत", searched
                            forward from the previous word cue, so cues stay in
                            script order
               भारत+0.3     the same word, shifted by +0.3 s (use -0.1 to lead)
               w:1906       a word that is itself a number (a bare number means seconds)
    dur      loop length in seconds, or "all" (until the end). Ignored for "one"
    gain_db  level of this sound, e.g. -18
    fade_in  seconds
    fade_out seconds

Word cues are the point: change the voice, re-run align.py, and every click
still lands on its word. No retiming.

One-shots are placed PEAK on the cue, not file-start on the cue: a page turn
whose loudest moment is 0.3 s in would otherwise land 7 frames late.

Levels: every file in CATALOG.tsv (built by scripts/index_sfx.py) carries a
level_offset. mix.py adds it to the cue's gain_db, so the gains in SKILL.md
mean the same thing for any sound, from any pack, at any original loudness.
Files that are not in the catalog are used exactly as they are. Run index_sfx.py
after adding sounds.

Mix chain:
- Each voice is first brought to -22 LUFS. Without this, a loud narrator (one
  measured -17 LUFS) made the final loudnorm pull the whole mix down, and the
  music and SFX sank about 5 dB.
- The bed ducks under SFX, and everything ducks under the voice
  (sidechaincompress).
- Final loudness is -14 LUFS with a -1.5 dBTP ceiling, the YouTube target.
"""
import argparse, csv, os, re, subprocess, sys

KIT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def tsv(path):
    with open(path, encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def clean(w):
    return re.sub(r"[।?,.!\"'“”‘’:;]", "", w)


class Resolver:
    """Turns an `at` value into seconds."""

    def __init__(self, lines=None, words=None):
        self.lines = {r["id"]: float(r["start"]) for r in lines or []}
        self.words = [(float(r["start"]), clean(r["word"])) for r in words or []]
        self.pos = 0

    def __call__(self, at):
        at = at.strip()
        m = re.fullmatch(r"(.+?)([+-]\d+(?:\.\d+)?)?", at)
        base, off = m.group(1), float(m.group(2) or 0)
        if not base.startswith("w:"):
            try:
                return max(0.0, float(base) + off)
            except ValueError:
                pass
        if base.startswith("w:"):
            base = base[2:]
        elif base.startswith("@"):
            if base[1:] not in self.lines:
                raise SystemExit(f"cue {at!r}: no VO line {base[1:]} (pass --lines VO.tsv)")
            return max(0.0, self.lines[base[1:]] + off)
        for k in range(self.pos, len(self.words)):
            if base in self.words[k][1]:
                self.pos = k
                return max(0.0, self.words[k][0] + off)
        raise SystemExit(f"cue {at!r}: word not found after word #{self.pos} "
                         f"(pass --words WORDS.tsv, keep cues in script order)")


def catalog():
    """file -> (peak seconds, level offset dB), from CATALOG.tsv (index_sfx.py)."""
    p = os.path.join(KIT, "CATALOG.tsv")
    if not os.path.exists(p):
        return {}
    return {r["file"]: (float(r["peak"] or 0), float(r.get("level_offset") or 0)) for r in tsv(p)}


def media_len(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                          "-of", "csv=p=0", path], capture_output=True, text=True).stdout
    return float(out.strip())


def build(cues, vo, out, dur, dip):
    cmd, parts, sfx = ["ffmpeg", "-y", "-loglevel", "error"], [], []
    labels = [c["label"] for c in cues]
    for i, c in enumerate(cues):
        path = c["file"] if os.path.isabs(c["file"]) else os.path.join(KIT, c["file"])
        assert os.path.exists(path), f"cue {c['label']}: file missing {path}"
        gain, fi, fo = float(c["gain_db"]), float(c["fade_in"] or 0), float(c["fade_out"] or 0)
        if c["kind"] == "loop":
            d = dur - c["t"] if c["dur"] in ("all", "") else float(c["dur"])
            cmd += ["-stream_loop", "-1", "-i", path]
            f = [f"atrim=0:{d:.3f}", "asetpts=N/SR/TB"]
            if fi:
                f.append(f"afade=t=in:st=0:d={fi}")
            if fo:
                f.append(f"afade=t=out:st={max(0, d - fo):.3f}:d={fo}")
        else:
            cmd += ["-i", path]
            f = []
        f += ["aformat=sample_fmts=fltp:sample_rates=48000:channel_layouts=stereo", f"volume={gain}dB"]
        if c["label"] == "bed" and dip is not None:
            # a held breath: a smooth dip in the bed, not a step
            f.append(f"volume='1-0.62*exp(-pow((t-{dip})/0.85,2))':eval=frame")
        if c["t"] > 0:
            f.append(f"adelay={int(c['t'] * 1000)}:all=1")
        parts.append(f"[{i}:a]" + ",".join(f) + f"[a{i}]")
        if c["label"] != "bed":
            sfx.append(f"[a{i}]")

    n = len(cues)
    if "bed" in labels and sfx:
        bed = f"[a{labels.index('bed')}]"
        parts.append("".join(sfx) + f"amix=inputs={len(sfx)}:normalize=0:dropout_transition=0[sfx]")
        parts.append("[sfx]asplit=2[sfxmix][key]")
        parts.append(f"{bed}[key]sidechaincompress=threshold=0.04:ratio=4:attack=25:release=450[duck]")
        parts.append("[duck][sfxmix]amix=inputs=2:normalize=0[world]")
    else:
        parts.append("".join(f"[a{i}]" for i in range(n)) + f"amix=inputs={n}:normalize=0[world]")

    tail = f"apad=whole_dur={dur:.3f},atrim=0:{dur:.3f},loudnorm=I=-14:TP=-1.5:LRA=11[out]"
    if vo:
        cmd += ["-i", vo]
        parts.append(f"[{n}:a]aformat=sample_fmts=fltp:sample_rates=48000:channel_layouts=stereo,"
                     "loudnorm=I=-22:TP=-2:LRA=11,aresample=48000,volume=2dB,asplit=2[vomix][vokey]")
        parts.append("[world][vokey]sidechaincompress=threshold=0.03:ratio=6:attack=15:release=380[wd]")
        parts.append(f"[vomix][wd]amix=inputs=2:normalize=0,{tail}")
    else:
        parts.append(f"[world]{tail}")
    r = subprocess.run(cmd + ["-filter_complex", ";".join(parts), "-map", "[out]",
                              "-c:a", "pcm_s16le", "-ar", "48000", out])
    if r.returncode:
        sys.exit("ffmpeg failed")
    print(f"wrote {out}  ({dur:.2f}s, {n} cues{', with voice' if vo else ''})")


def selfcheck():
    lines = [{"id": "01", "start": "0.0"}, {"id": "02", "start": "4.5"}]
    words = [{"start": "0.1", "word": "भारत"}, {"start": "1.0", "word": "1906"},
             {"start": "5.0", "word": "1906।"}]
    r = Resolver(lines, words)
    assert r("12.5") == 12.5 and r("@02") == 4.5 and r("@02-0.2") == 4.3
    assert r("1906") == 1906.0           # a bare number is seconds
    assert r("w:1906") == 1.0
    assert r("w:1906+0.3") == 1.3        # same word again: the search does not skip ahead
    r.pos += 1
    assert r("w:1906") == 5.0            # punctuation is ignored
    assert Resolver(lines, words)("भारत-0.05") == 0.05
    try:
        r("नहींमिला")
        raise AssertionError("missing word must fail")
    except SystemExit:
        pass
    print("selfcheck ok")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cues", nargs="?")
    ap.add_argument("--vo", help="narration wav")
    ap.add_argument("--lines", help="VO.tsv (id, start, dur, text) for @NN cues")
    ap.add_argument("--words", help="WORDS.tsv from align.py, for word cues")
    ap.add_argument("--out", default="mix.wav")
    ap.add_argument("--dur", type=float, help="mix length (default: voice + 1.0 s)")
    ap.add_argument("--dip", help="seconds or a cue: dip the bed here for a held breath")
    ap.add_argument("--selfcheck", action="store_true")
    a = ap.parse_args()
    if a.selfcheck:
        return selfcheck()
    if not a.cues:
        ap.error("cues.tsv is required")
    res = Resolver(tsv(a.lines) if a.lines else None, tsv(a.words) if a.words else None)
    cues = tsv(a.cues)
    cat = catalog()
    for c in cues:
        c["t"] = res(c["at"])
        peak, offset = cat.get(c["file"].replace("\\", "/"), (0.0, 0.0))
        c["gain_db"] = str(float(c["gain_db"]) + offset)    # kit-standard level, whatever the file
        if c["kind"] == "one":                    # land the loudest moment on the cue
            c["t"] = max(0.0, c["t"] - peak)
    dur = a.dur or ((media_len(a.vo) + 1.0) if a.vo else max(c["t"] for c in cues) + 5)
    dip = Resolver(tsv(a.lines) if a.lines else None, tsv(a.words) if a.words else None)(a.dip) \
        if a.dip else None
    build(cues, a.vo, a.out, dur, dip)


if __name__ == "__main__":
    main()
