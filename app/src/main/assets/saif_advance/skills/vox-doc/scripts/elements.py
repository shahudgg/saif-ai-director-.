"""ELEMENTS.tsv -> elements/cut/NN.png  (generated on magenta with Google Flow, then keyed)

Run inside the project folder (needs Flow Connector: `flow-auto doctor` must pass):
    python <skill>/scripts/elements.py                 # generate missing ones, demark, key
    python <skill>/scripts/elements.py --new-project   # late additions (see PLAYBOOK, Flow lessons)
    python <skill>/scripts/elements.py key             # only re-key

One single object on flat magenta, background showing through every gap, thin dark
outline, no shadow. No palette is fixed: the model picks the colours.
Env: FLOW_DOWNLOADS = Chrome's download folder if it is not ~/Downloads (or one level
inside it); FLOW_PACE = flow-auto pacing flags.
"""
import csv, glob, os, re, shutil, subprocess, sys, time
import numpy as np
from PIL import Image, ImageDraw

FLOW = shutil.which("flow-auto") or "flow-auto"
# Flow saves into <Chrome download folder>/<FOLDER>/; FOLDER is this project's folder name
FOLDER = re.sub(r"[^A-Za-z0-9_-]+", "_", os.path.basename(os.getcwd())).strip("_").lower() or "voxdoc"
DL_CANDIDATES = ([os.environ["FLOW_DOWNLOADS"]] if os.environ.get("FLOW_DOWNLOADS") else
                 [os.path.expanduser("~/Downloads")] + glob.glob(os.path.expanduser("~/Downloads/*/")))
RAW, CUT = "elements/raw", "elements/cut"
KEYER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "key.py")
# Pro plan pace. At delay 0 Flow pairs images with the wrong prompt. Free plan: "--batch 4 --delay 8-12"
PACE = (os.environ.get("FLOW_PACE") or "--batch 8 --delay 5-8 --image-delay 1-2").split()
NEW_PROJECT = ["--new-project"] if "--new-project" in sys.argv else []

PROMPT = (
    "{obj}, bold flat vector illustration with a thin dark outline, Vox editorial explainer "
    "style, warm muted vintage colours, one single isolated object, centred with a generous margin of empty space on "
    "all sides, on a solid flat pure magenta background, uniform chroma key magenta hex "
    "#FF00FF filling the entire background and showing through every gap, hole and opening "
    "in the object, the object itself contains absolutely no magenta anywhere, hard crisp "
    "edges, sharply defined boundary against the background, no drop shadow, no cast "
    "shadow, no soft shadow, no glow, no blur, no feathered or faded edges, no reflection, "
    "no text, no letters, no numbers, no frame, no border, no background scenery")


def rows():
    with open("ELEMENTS.tsv", encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def generate():
    os.makedirs(RAW, exist_ok=True)
    todo = [r for r in rows() if not glob.glob(f"{RAW}/{r['id']}.*")]
    if not todo:
        print("sab elements pehle se hain"); return
    for r in todo:
        cmd = [FLOW, "generate", "create", "flow-text-to-image",
               "--prompt", PROMPT.format(obj=r["object"]), "--model", "nano-banana-2",
               "--aspect-ratio", "1:1", "--outputs", "1", "--index", str(int(r["id"])),
               "--folder", FOLDER] + PACE + NEW_PROJECT
        p = subprocess.run(cmd, capture_output=True, text=True)
        print(f"[{r['id']}] submitted" if not p.returncode else f"[{r['id']}] {p.stderr[:140]}")
    print(f"submitted {len(todo)}; watching", flush=True)
    end = time.time() + 45 * 60
    while time.time() < end:
        out = subprocess.run([FLOW, "generate", "list"], capture_output=True, text=True).stdout
        busy = len(re.findall(r"(?m)^\S{8}\S*\s+(queued|running|downloading)\b", out))
        print(f"  {busy} in flight", flush=True)
        if not busy:
            break
        time.sleep(15)
    for r in rows():
        hit = [q for dl in DL_CANDIDATES for q in glob.glob(os.path.join(dl, FOLDER, "*"))
               if re.match(rf"{int(r['id'])} - ", os.path.basename(q))]
        if hit:
            src = max(hit, key=os.path.getmtime)
            shutil.copy(src, f"{RAW}/{r['id']}{os.path.splitext(src)[1]}")
    print("raw:", len(glob.glob(f"{RAW}/*")))


def demark():
    """Flow ka sparkle mark background magenta se bhar do, warna key ke baad chipka rehta hai."""
    n = 0
    for p in glob.glob(f"{RAW}/*"):
        im = Image.open(p).convert("RGB")
        a = np.asarray(im, np.float32)
        ring = np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]])
        keyness = np.minimum(ring[:, 0], ring[:, 2]) - ring[:, 1]
        if (keyness > 40).mean() < 0.2:
            continue
        bg = tuple(int(v) for v in np.median(ring[keyness > 40], axis=0))
        W, H = im.size
        box = int(W * 0.10)
        ImageDraw.Draw(im).rectangle([W - box, H - box, W, H], fill=bg)
        im.save(p); n += 1
    print(f"demarked {n}")


def dupes():
    """Flow कभी पुराने grid की तस्वीर नए prompt के नाम से उतार देता है -- बाइट-दर-बाइट वही।"""
    import hashlib
    seen = {}
    for p in sorted(glob.glob(f"{RAW}/*")):
        h = hashlib.md5(open(p, "rb").read()).hexdigest()
        seen.setdefault(h, []).append(os.path.basename(p))
    return [v for v in seen.values() if len(v) > 1]


def key():
    d = dupes()
    assert not d, f"same image under different prompts (Flow mispair): {d}"
    demark()
    os.makedirs(CUT, exist_ok=True)
    r = subprocess.run([sys.executable, KEYER, RAW, CUT], capture_output=True, text=True)
    print((r.stdout or r.stderr).strip()[-1500:])
    got = glob.glob(f"{CUT}/*.png")
    assert got, "keyer ne kuch nahi banaya"
    # har cut-out ki border alpha ~0 honi chahiye; nahi to key fail hui
    bad = []
    for p in got:
        al = np.asarray(Image.open(p).convert("RGBA"))[..., 3]
        edge = np.concatenate([al[0], al[-1], al[:, 0], al[:, -1]])
        if (edge > 8).mean() > 0.5:
            bad.append(os.path.basename(p))
    print(f"keyed {len(got)}" + (f"  -- FAILED KEY: {bad}" if bad else ""))


if __name__ == "__main__":
    if "key" not in sys.argv[1:]:
        generate()
    key()
