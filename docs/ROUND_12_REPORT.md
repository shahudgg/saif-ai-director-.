# Round 12 report — Flow autopilot inside zero-to-video (the video's workflow, in-app)

Mirrors the reference video (youtu.be/faAj5PCkcjY / "Automate Google Flow") without Antigravity or Termux —
SAIF AI does everything natively:

1. **One-time setup, in-app**: zero-to-video screen shows a "1️⃣ Google Flow mein login karo" banner → opens the
   persistent in-app browser (flow.google.com / labs.google/fx). Log in with Gmail once; the WebView session
   persists (cookies saved by the app's WebView storage).
2. **Agent drives the browser**: "🖼 Flow images" opens `FlowBatchDialog`. The SAIF agent first writes N
   distinct English image prompts for your topic (`DirectorServices.flowImagePrompts`, strict JSON), then injects
   `window.SaifFlowAuto.queue(prompts)` into the logged-in Flow page: it fills the prompt box, clicks
   create/generate, waits **8 s between prompts**, and logs progress ("Generating 3/15: …") live in the dialog.
   Every generated image/video is auto-downloaded into the app (`flow_downloads/` + `FlowAssetStore`).
3. **Download → process → place**: imported images join the same library as Telegram assets (`local:` fileIds,
   served without network). Asset tools can crop (✂️), AI-edit / background-remove (🪄 per image or bulk
   "Sabka bg-remove"), delete. When the batch finishes, `DirectorController.attachFlowImages()` fixes the images
   onto the planned scenes **in order** (scene 1 → image 1, …) as full-frame backdrops with alternating entrances
   (fadeIn/slideUp/zoom/popIn/wipe) and ±drift — the code-animated graphics play on top. Without a plan yet, the
   hunter picks them up automatically by keywords on the next run.
4. **Animations**: scenes already get 40+ primitives, 17 text fx, camera moves, per-scene uniqueness gates; key
   visuals get Ken-Burns drift + varied entrances.

CI: pushed to `arena/6729de33-saif-ai-director` → `build-apk.yml` runs → APK on the `saif-ai-apk` release
(`https://github.com/shahudgg/saif-ai-director-./releases/download/saif-ai-apk/app-debug.apk`).
