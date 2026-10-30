#!/usr/bin/env python3
"""
Telegram asset catalog snapshot for SAIF AI Director (runs in GitHub Actions, standard library only).

What it does
  1. Reads the pending posts of your Telegram channel with getUpdates (WITHOUT confirming them, so the app can still read them).
  2. Merges them into telegram_catalog/catalog.json (kept in the repo, so assets uploaded earlier are never lost).
  3. Writes app/src/main/assets/assets_catalog.json, which the app loads at start: your library is inside the APK.

Needs the repository secrets TG_BOT_TOKEN and TG_CHAT_ID. It NEVER fails the build: every problem is printed as a warning.
Telegram only keeps unread bot updates for 24 hours, so run the "Sync Telegram catalog" workflow (it runs every 6 hours) or
rebuild soon after uploading new assets. File ids of a bot stay valid, so catalogued assets keep working.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORE = os.path.join(ROOT, "telegram_catalog", "catalog.json")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "assets_catalog.json")

TOKEN = (os.environ.get("TG_BOT_TOKEN") or "").strip().replace('"', "").replace("\\", "")
CHAT = (os.environ.get("TG_CHAT_ID") or "").strip().replace('"', "").replace("\\", "")

SFX_WORDS = {"sfx", "whoosh", "pop", "hit", "click", "swoosh", "ding", "riser", "impact", "boom", "laugh", "applause", "glitch"}
AUDIO_EXT = {"mp3", "wav", "m4a", "ogg", "aac", "flac"}
VIDEO_EXT = {"mp4", "mov", "webm", "mkv"}
IMAGE_EXT = {"png", "jpg", "jpeg", "webp", "gif"}


def warn(msg):
    print("::warning::" + msg if os.environ.get("GITHUB_ACTIONS") else "WARNING: " + msg)


def api(method, params=None):
    url = "https://api.telegram.org/bot%s/%s" % (TOKEN, method)
    if params:
        url += "?" + urllib.parse.urlencode(params)
    try:
        with urllib.request.urlopen(url, timeout=40) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"ok": False, "description": "HTTP %s" % e.code}
    except Exception as e:  # network problems must not break the build
        return {"ok": False, "description": str(e)}


def parse_caption(caption, file_id, message_id, size):
    low = caption.lower()
    if "[asset_index]" not in low or "[/asset_index]" not in low:
        return None
    start = low.index("[asset_index]") + len("[asset_index]")
    end = low.index("[/asset_index]")
    if end <= start:
        return None
    item = {
        "asset_id": "AID_" + str(message_id)[-6:], "title": "Asset_%s" % message_id, "type": "video", "extension": "mp4",
        "category": "General", "subject": "", "keywords": [], "tags": [], "user_id": "", "telegram_file_id": file_id,
        "message_id": message_id, "file_size": size,
    }
    for line in caption[start:end].strip().splitlines():
        line = line.strip()
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        key = key.strip().lower(); value = value.strip()
        if key in ("asset_id", "assetid", "id", "aid"):
            item["asset_id"] = value
        elif key == "title":
            item["title"] = value
        elif key == "type":
            item["type"] = value.lower()
        elif key in ("extension", "ext"):
            item["extension"] = value.lower().lstrip(".")
        elif key == "category":
            item["category"] = value
        elif key == "subject":
            item["subject"] = value
        elif key in ("search keywords", "keywords"):
            item["keywords"] += [w.strip().lower() for w in re.split(r"[,; ]", value) if w.strip()]
        elif key in ("tags", "tag"):
            item["tags"] += [w.strip().lower() for w in re.split(r"[,;# ]", value) if w.strip()]
        elif key in ("user_id", "userid", "user"):
            item["user_id"] = value
    item["keywords"] = sorted(set(item["keywords"]))
    item["tags"] = sorted(set(item["tags"]))
    return item


def index_from_file(name, mime, file_id, message_id, size, duration, caption=""):
    ext = (name.rsplit(".", 1)[-1].lower() if "." in name else "") or (mime.split("/", 1)[-1].lower() if "/" in mime else "")
    if mime.startswith("audio") or ext in AUDIO_EXT:
        typ = "audio"
    elif mime.startswith("video") or ext in VIDEO_EXT:
        typ = "video"
    elif mime.startswith("image") or ext in IMAGE_EXT:
        typ = "image"
    else:
        return None
    base = name.rsplit(".", 1)[0] if "." in name else name
    # Telegram often sends videos/photos with NO file_name, which used to leave the asset with the
    # useless keyword "asset". The caption almost always describes the clip, so mine it as well.
    cap = re.sub(r"\s+", " ", (caption or "")).strip()
    if not base or re.fullmatch(r"asset[_-]?\d+", base, re.I):
        base = (cap[:48].strip() or "asset_%s" % message_id)
    words = [w for w in re.split(r"[\W_]+", (name or "").lower() + " " + (caption or "").lower()) if len(w) > 1]
    words = [w for w in words if not re.fullmatch(r"\d+", w)]
    if typ == "audio":
        cat = "sfx" if (0 < duration <= 10 or any(w in SFX_WORDS for w in words)) else "bgm"
    elif typ == "video":
        cat = "overlay_graphic" if any(w in ("overlay", "glitch", "transition") for w in words) else "b_roll"
    else:
        cat = "overlay_graphic"
    return {
        "asset_id": "AID_%s" % message_id, "title": base, "type": typ, "extension": ext or "bin", "category": cat,
        "subject": "auto-indexed from file name", "keywords": sorted(set(words)), "tags": sorted({"#" + w for w in words}),
        "user_id": "all", "telegram_file_id": file_id, "message_id": message_id, "file_size": size,
    }


def process(updates):
    out = []
    for u in updates:
        post = u.get("channel_post") or u.get("edited_channel_post") or u.get("message") or u.get("edited_message")
        if not post:
            continue
        chat = post.get("chat") or {}
        is_channel = (str(chat.get("id", "")) == CHAT
                      or (chat.get("username") or "").lower() == CHAT.lstrip("@").lower()) if CHAT else True
        # The Bot API cannot read channel history, so the owner can forward the existing library to
        # the bot in a private chat; those posts are indexed too (file ids never expire for a bot).
        is_owner_dm = chat.get("type") == "private"
        if not (is_channel or is_owner_dm):
            continue
        mid = int(post.get("message_id", 0))
        text = post.get("caption") or post.get("text") or ""
        fid, size, name, mime, dur = "", 0, "", "", 0
        for key in ("video", "audio", "voice", "document", "animation"):
            o = post.get(key)
            if o and not fid:
                fid, size, name, mime, dur = o.get("file_id", ""), int(o.get("file_size", 0) or 0), o.get("file_name", ""), o.get("mime_type", ""), int(o.get("duration", 0) or 0)
        photos = post.get("photo")
        if photos and not fid:
            last = photos[-1]
            fid, size, mime, name = last.get("file_id", ""), int(last.get("file_size", 0) or 0), "image/jpeg", "photo_%s.jpg" % mid
        if not fid:
            continue
        item = parse_caption(text, fid, mid, size) if text else None
        item = item or index_from_file(name, mime, fid, mid, size, dur, caption=text)
        if item:
            out.append(item)
    return out


def load_store():
    try:
        with open(STORE, encoding="utf-8") as f:
            data = json.load(f)
            return data if isinstance(data, list) else []
    except Exception:
        return []


def diagnose():
    """Prints the four things that make a channel look empty, so CI tells us which one it is."""
    me = api("getMe")
    if not me.get("ok"):
        warn("Telegram getMe failed: %s. TG_BOT_TOKEN looks invalid." % me.get("description", "unknown"))
        return
    bot = me.get("result") or {}
    print("Telegram bot: @%s (id %s)" % (bot.get("username", "?"), bot.get("id", "?")))
    if not CHAT:
        warn("TG_CHAT_ID is empty.")
        return
    print("Telegram channel id: %s" % CHAT)
    chat = api("getChat", {"chat_id": CHAT})
    if not chat.get("ok"):
        warn("getChat failed: %s -- add this bot as an ADMIN of the channel."
             % chat.get("description", "unknown error"))
        return
    c = chat.get("result") or {}
    print("Telegram channel: %r (type=%s)" % (c.get("title", "?"), c.get("type", "?")))
    member = api("getChatMember", {"chat_id": CHAT, "user_id": bot.get("id")})
    status = ((member.get("result") or {}).get("status") or "")
    if status:
        print("Bot membership: %s" % status)
        if status not in ("administrator", "creator"):
            warn("The bot is '%s' in the channel but must be 'administrator' to receive channel posts." % status)
    print("NOTE: the Bot API cannot read channel history. Only posts made while the bot is an admin are delivered.")
    print("      To import an existing library once, forward those posts to the bot in a private chat.")


def main():
    items = load_store()
    if not TOKEN:
        warn("TG_BOT_TOKEN is empty: the APK is built without the Telegram library. Add the repository secret and rebuild.")
    else:
        diagnose()
        hook = api("getWebhookInfo")
        if hook.get("ok") and (hook.get("result") or {}).get("url"):
            warn("A webhook is set on this bot, so getUpdates returns nothing. Remove it (deleteWebhook) to let the catalog sync.")
        else:
            resp = api("getUpdates", {"limit": 100, "timeout": 0,
                                      "allowed_updates": json.dumps(["channel_post", "edited_channel_post", "message", "edited_message"])})
            if not resp.get("ok"):
                warn("Telegram getUpdates failed: %s" % resp.get("description", "unknown error"))
            else:
                fresh = process(resp.get("result") or [])
                print("Telegram: %d pending update(s), %d asset(s) recognised." % (len(resp.get("result") or []), len(fresh)))
                for a in fresh:
                    idx = next((i for i, x in enumerate(items)
                                if x.get("telegram_file_id") == a["telegram_file_id"] or x.get("message_id") == a["message_id"]), -1)
                    if idx >= 0:
                        items[idx] = a
                    else:
                        items.append(a)
    os.makedirs(os.path.dirname(STORE), exist_ok=True)
    with open(STORE, "w", encoding="utf-8") as f:
        json.dump(items, f, ensure_ascii=False, indent=1)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(items, f, ensure_ascii=False)
    print("Catalog: %d asset(s) written to %s" % (len(items), os.path.relpath(OUT, ROOT)))
    if not items:
        warn("The asset catalog is EMPTY: the APK will be built without a Telegram library. "
             "Make the bot an administrator of the channel (or forward the assets to the bot once), "
             "remove any webhook, then re-run this workflow.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:  # never break the build
        warn("telegram_catalog.py failed: %s" % exc)
        sys.exit(0)
