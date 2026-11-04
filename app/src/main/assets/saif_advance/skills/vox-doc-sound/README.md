# Vox Doc Sound: documentary ke liye awaaz aur sound design

Kisi bhi topic ki Vox-style explainer documentary ke liye ek Claude Code skill.
Isme ye cheezein hain:
- **Voiceover:** English aur 8 doosri bhashayein Kokoro se, Hindi aur Indian
  bhashayein Indic Parler se. Dono commercial use ke liye free hain.
- **Har shabd ki timing:** jo sound jis shabd par bola gaya, usi par bajega
- **Sound design ke niyam:** kab sound lagana hai, kab chup rehna hai, topic
  ke hisaab se kaunsa sound, kitna loud
- **Final mix:** −14 LUFS (YouTube standard), awaaz hamesha upar

## Install: sabse aasaan tareeka

Zip kahin bhi unzip karo, phir Claude Code mein yeh paste karo
(`<path>` ki jagah unzip kiye `vox-doc-sound` folder ka poora path):

```
Install the vox-doc-sound skill from <path>.
1. Copy the whole folder to ~/.claude/skills/vox-doc-sound/ (on Windows: C:\Users\<me>\.claude\skills\vox-doc-sound\).
2. Check ffmpeg is installed with libass; if not, install it (winget/brew/apt).
3. Install Python packages with "python -m pip install numpy soundfile faster-whisper".
4. Run "python scripts/index_sfx.py" inside the installed folder to build CATALOG.tsv.
5. Read SKILL.md, then tell me in my language: how many SFX and music tracks are ready,
   what is optional (Kokoro for English VO, Kaggle for Hindi VO, an ElevenLabs key),
   and how to start a sound design for my documentary.
Never ask me to paste API keys into the chat; keys go in a .env file I edit myself.
```

## Install: haath se

1. `vox-doc-sound` folder ko yahan copy karo:
   - Windows: `C:\Users\<aapka-naam>\.claude\skills\vox-doc-sound\`
   - Mac/Linux: `~/.claude/skills/vox-doc-sound/`
2. Zaroori tools: **ffmpeg** (libass ke saath) aur Python ke saath
   `python -m pip install numpy soundfile faster-whisper`
3. English VO ke liye: `python -m pip install kokoro` + espeak-ng
4. Hindi VO ke liye: sirf Kaggle account, Hugging Face token nahi chahiye (tareeka
   `kaggle/make_parler_job.py` ke upar likha hai)
5. Claude Code mein bolo: *"vox-doc-sound skill se meri documentary ka sound design karo"*

## Sound library: saath mein aati hai (free, commercial, shareable)

| Folder | Kya hai | Licence |
|---|---|---|
| `sfx/StableAudio/` | Sainkdon Vox-style sounds: whoosh, transition, kagaz/collage, map, archive, data, paisa, tonal, topic ke hisaab se foley, aur 100 se zyada ambience (India + duniya) | Free, commercial, share kar sakte ho (`sfx/StableAudio/LICENSE.md`) |
| `sfx/Kenney_CC0/` | 99 UI click, pop, pin, data tone, halke thud, kitaab/card, sikke, pizzicato | CC0 public domain |

Pehli baar `python scripts/index_sfx.py` chalao, isse catalog ban jaata hai.

**Library mein sound na mile to?** Claude khud aapke system par bana dega:
- ElevenLabs key ho to usse (key `.env` file mein aap khud daalna, chat mein kabhi nahi)
- Key na ho to Kaggle ke free GPU par Stable Audio se. Setup Claude step-by-step karwayega, output ke maalik aap.
- Ya Freesound CC0 / Pixabay se download karke `sfx/<Source>/<Category>/` mein daalo, phir index chalao

**Music saath aati hai:** `music/Gemini/<mood>/` mein 22 mood ki BGM aur ambient pads hain, commercial use aur share dono ke liye free.
Pasand na aaye to `music/README.md` ke prompt se Gemini mein nayi bana lo, ya Pixabay Music (https://pixabay.com/music/) ya YouTube Audio Library (https://studio.youtube.com → Audio Library) se download karke `music/<mood>/` mein daalo.

## Folder

| | |
|---|---|
| `SKILL.md` | Claude ke liye niyam |
| `scripts/mix.py` | final mix |
| `scripts/align.py` | har shabd ka time |
| `scripts/index_sfx.py` | aapki sound library ka catalog |
| `scripts/kokoro_vo.py`, `kaggle/make_parler_job.py`, `scripts/vo_join.py` | voiceover |
| `scripts/eleven_sfx.py`, `scripts/eleven_music.py` | apni ElevenLabs key se sound/music banao |
| `sfx/StableAudio/`, `sfx/Kenney_CC0/` | free sound library |
| `kaggle/make_sfx_job.py` | free AI sound effects (Kaggle) |
| `sfx/STARTER.tsv` | 36 tested ElevenLabs prompts (optional) |
| `examples/` | LINES.tsv ke namune (Hindi, English) |

## Licence

- Skill aur scripts aap apni videos ke liye freely use kar sakte ho.
- Aapki banai videos aapki hain.
- Saath aane wale `sfx/StableAudio/` aur `sfx/Kenney_CC0/` sounds commercial use aur share dono ke liye free hain.
- **ElevenLabs, Pixabay ya khareede packs ki files aage mat baanto.** Unke licence mana karte hain.
