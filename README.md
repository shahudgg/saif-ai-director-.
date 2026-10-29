<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/875e8341-635e-461c-86c5-6a9dddecfaf8

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

## GitHub build + Telegram assets (no user setup)
Add two repository secrets: `TG_BOT_TOKEN` and `TG_CHAT_ID` (Settings > Secrets and variables > Actions).
The workflow injects them into the APK at build time; they are never stored in the source. Note that anything inside an APK
can be extracted by a determined user, so use a bot that only reads your asset channel.

## Telegram library at build time (new)
The build also runs `scripts/telegram_catalog.py`, which snapshots your channel's assets into the APK (`assets_catalog.json`).
`.github/workflows/sync-telegram-catalog.yml` keeps `telegram_catalog/catalog.json` up to date every 6 hours (run it manually
once after uploading new assets). Requirements: the bot is **admin** of the channel, no webhook is set, assets carry an
`[ASSET_INDEX]` caption or a descriptive file name. See `CLAUDE.md` for the full project briefing.
