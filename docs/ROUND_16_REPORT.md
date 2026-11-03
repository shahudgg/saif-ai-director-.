# SAIF AI — Round 16 report

**Theme:** the whole video pipeline now runs by itself — agent opens Flow, presses Flow's own arrow, plans
layers per scene (background + overlays), cuts the overlay backgrounds out locally, plans per-scene
animation/motion-graphics, and a final QC agent repairs everything before the timeline is built.

Commit for this round: see `CLAUDE.md` §9 (Round 16). Build: GitHub Actions `build-apk.yml`.

---

## 1. The six complaints, and what actually changed

| # | Owner's complaint (Round 15 build) | Root cause | Round 16 fix |
|---|---|---|---|
| 1 | "Flow AI khud open nahi hota, hume tap karna padta hai" | `agentRun()` finished with "Flow pictures nahi mili — 🖼 Flow images (auto) dabao". The browser was only launched from a button. | `buildFlowPlanAndOpen()` is now a **step inside the agent crew**. It builds the layer plan, saves it and calls `FlowBrowserActivity.start(..., autostart = true, autofinish = true)`. No tap. |
| 2 | "prompt chat mein chala jata hai par Arrow button nahi dabta" | `S.clickGenerate()` scored buttons only by **text/aria-label**. Flow's send control is an icon-only SVG arrow at the composer's bottom-right, so nothing matched and `pressEnter()` "succeeded" without sending. | New `FlowJs.SUBMIT_V2`: scores controls by label **and geometry** (square-ish button hugging the composer's bottom-right corner), fires a full pointer/mouse/click sequence, then `GEN_STATE` polls the page to **confirm** the prompt was taken (composer cleared / "generating" / new image). `FlowAutopilot` re-sends up to 3× while unconfirmed. |
| 3 | "background remove nahi ho raha hai" | The app only *detected* `needsBgRemove` and passed a guessed chroma colour to a shader. Flow paints flat grey/white studios, not green screens. | New `FlowCutout`: dominant-border-colour detection → 4-neighbour flood fill (auto-raised tolerance for green/blue) → 1-px feather → transparent PNG saved as `*_cut.png`. It refuses (and keeps the photo) when <6 % or >94 % would be removed — no silent garbage. |
| 4 | "ek scene ke liye background + asset + overlay chahiye, total count aur aspect bhi planning mein ho" | One prompt per scene. | New `LayerPlanner`: per scene **1 background plate + 1 overlay + optional accent** (cap 3/scene), each with `layer`, `z`, target box `x/y/w/h`, `motion`, `removeBg`, and the **owner's aspect ratio** on every slot. Plan notes carry the totals. |
| 5 | "keyframe overlays ke saath code animations bhi chalein, unique, background se clash na ho" | Transitions were planned; motion graphics were whatever the scene graph already had. | New `SceneAnimPlanner`: per scene camera move (pushIn/panLeft/panRight/tiltUp/pullOut/parallaxDrift, never repeating the previous), an accent primitive, one information primitive, optional narration kicker — all placed in the **emptiest of five zones** computed from the real overlay boxes. Overlay keyframes come from `FlowPlacement.overlayKeys` (floatUp / orbitSmall / parallax / popDrift / sway). |
| 6 | "last agent check kare ki keyframe/layer/jagah/code galat na ho" | Nothing verified the assembled plan. | New `FinalQcAgent`: drops dead files & orphan assets, clamps every keyframe into the scene, enforces **one backdrop per scene** (extra backdrops become overlays), shrinks overlays that cover the frame, moves motion graphics off overlays, varies repeated transitions/cameras, and reports each fix in the chat. |

---

## 2. Pipeline as it runs now (no taps after the topic)

```
topic + question-card answers
   → 🚀 "Poori video auto"  (= DirectorController.startFullAuto → agentRun)
   1  PLANNER     research (DeepResearch)
   2  SCRIPT      narration script
   3  ASSEMBLER   scene plan + graphics + narration voice-over + first timeline
   4  LAYER PLAN  LayerPlanner: background + overlays per scene, aspect locked
   5  FLOW        browser opens ITSELF, aspect control clicked, per prompt:
                     type → press arrow (verified) → 8 s gap → capture → download
                     → local background removal (overlays) → verify → next
   6  PLACEMENT   FlowPlacement.forSlot: plate = full frame, overlay = planned box + keyframes
   7  ANIMATION   SceneAnimPlanner: camera + motion graphics in free zones (per scene, unique)
   8  QC          FinalQcAgent: repairs layers, keyframes, collisions, continuity
   9  ASSEMBLE    TimelineAssembler → applyToTimeline (auto), export from the editor
```

The browser is only a *window* on step 5 — it hands the finished plan back through `FlowBridgeBus`, and the
controller continues by itself (`pendingAutoFinish`). Login is still possible at any time.

---

## 3. Files

New:

| File | Purpose |
|---|---|
| `flow/LayerPlanner.kt` | layered planning (bg + overlay + accent), aspect-locked slots, one model call for background prompts, local fallback |
| `flow/FlowCutout.kt` | real background removal (flood fill + feather) → transparent PNG |
| `studio/director/SceneAnimPlanner.kt` | per-scene camera + motion graphics in free zones, continuity-aware merge into scene graphs |
| `studio/director/FinalQcAgent.kt` | last-agent checks + repairs, honest report |

Changed:

| File | Change |
|---|---|
| `flow/web/FlowJs.kt` | `SUBMIT_V2` (geometry-scored arrow + full pointer sequence), `GEN_STATE`, `SET_ASPECT` |
| `flow/web/FlowWebSession.kt` | `submitSmart()` (click + confirm loop), `setAspect()` |
| `flow/FlowAutopilot.kt` | `FlowSubmitResult`, 3× verified submit, aspect lock, local cut-out step, cut-out path in the plan |
| `flow/FlowModels.kt` | `FlowRoles.OVERLAY`, `FlowLayerKind`, slot fields `layer/z/target*/motion/cutPath/removeBg/borderHex` + JSON |
| `flow/FlowTransitions.kt` | `FlowPlacement.forSlot` honours the planned box/motion/cut-out; new `overlayKeys` tracks |
| `flow/web/FlowBrowserActivity.kt` | `autofinish` intent extra, plan-wait before autostart, auto-close after DONE, layer badge per slot |
| `studio/director/DirectorController.kt` | `buildFlowPlanAndOpen()`, `startFullAuto()`, auto-open inside `agentRun`, layered asset build, animation merge, QC, auto-finish |
| `studio/director/DirectorServices.kt` | `buildFlowPlan` → LayerPlanner (old planner kept as declared fallback) |
| `studio/director/TimelineAssembler.kt` | scene scrim 0.52 → 0.24 (real plate is no longer buried in black) |
| `studio/ui/SaifDirectorScreen.kt` | 🚀 "Poori video auto" button + layer-aware Flow card |

---

## 4. Honest limits

- Nothing is compiled locally (no JDK/Android SDK in this sandbox). GitHub Actions compiles; every injected JS
  block is `node --check`ed (11 blocks) and every touched Kotlin file passes a comment/string-aware bracket check.
- Flow's DOM is not ours. The arrow finder is heuristic by design (label + geometry + Enter + form fallback) and
  every attempt is **confirmed** from the page; if Flow changes its composer again, the chat says
  "send unconfirmed" instead of pretending.
- Background removal works on flat / chroma backgrounds (which is exactly what the overlay prompts ask for).
  A busy photo background is returned unchanged on purpose.
- Aspect ratio: the app clicks Flow's ratio control when it exists; otherwise the plan is still built for the
  owner's ratio and the pictures are cover-cropped.
- More pictures = more time: a 10-scene video with overlays is ~15-20 images at 8 s+ each.
