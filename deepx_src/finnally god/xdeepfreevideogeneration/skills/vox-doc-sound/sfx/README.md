# sfx/: your sound library

Put sounds here, one folder per category. The folder name is the category:

```
sfx/Whoosh/  sfx/PopClick/  sfx/Map/  sfx/Paper/  sfx/Stationery/  sfx/Data/
sfx/Reveal/  sfx/Emphasis/  sfx/Tonal/  sfx/Archive/  sfx/Time/  sfx/Transport/
sfx/Crowd/  sfx/Money/  sfx/Nature/  sfx/Ambience/  ...
```

To keep packs apart, add a level: `sfx/<Source>/<Category>/`, for example
`sfx/MyPack/Whoosh/`.

Then run `python scripts/index_sfx.py`. Run it again whenever you add files.

`STARTER.tsv` holds 36 tested prompts for a complete basic Vox palette:
`python scripts/eleven_sfx.py --batch sfx/STARTER.tsv` (needs your ElevenLabs key).
