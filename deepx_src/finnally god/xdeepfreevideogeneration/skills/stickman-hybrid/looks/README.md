# Looks: pick one per video, by topic

Every look file has the same four parts:
- **When**: which topics it suits.
- **STYLE**: the paragraph that goes in the project's `STYLE.txt`, after the line
  `look: <name>`. `pictures.py` appends it to every prompt.
- **Character sheet**: the prompt for the one sheet that every character shot references.
- **Notes**: what went wrong before, and how to avoid it.

| Look | Feels like | Best for | Text card |
|---|---|---|---|
| `bright` | clean flat cartoon: blue skies, greens, white rooms | science, health, everyday-things history, anything that cuts between today and the past (the DEFAULT) | light cream to pale blue |
| `warm` | golden ochre, painterly, cosy | ancient humans, civilisation, religion, Indian history, Hindi audiences | ochre |
| `ink` | black ink line on off-white, a few flat colour accents | "explained" topics full of numbers and comparisons, psychology, money | white paper |
| `painted` | detailed painted places, muted and cinematic, one recurring hero | survival stories, "you" serials, disasters, war | grey-blue |
| `night` | deep blue night, firelight and moonlight | mysteries, dark history, space, horror-lite | dark navy, cream words |

## Adapting a look to the topic
- Edit the STYLE paragraph in the project's `STYLE.txt` to suit the topic. For example, add
  "Egyptian desert colours" for a topic about the Nile.
- Keep these four parts of the paragraph. They stop failures we saw in testing:
  - "full-bleed background that fills the whole frame"
  - the palette line
  - "simple round-head stick-figure characters"
  - the "no text" rule, which `pictures.py` adds itself
- Never add a brown or sepia filter to `bright`. A tester rejected a warm film for being
  "too brownish".
- Always test 2 shots (`pictures.py submit 1 8`) and look at them before running the
  full batch.
