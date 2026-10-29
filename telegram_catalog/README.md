# telegram_catalog

`catalog.json` is the running list of assets found in your Telegram channel (written by `scripts/telegram_catalog.py`).
It is merged into `app/src/main/assets/assets_catalog.json` at build time, so your library is inside the APK.
Do not edit by hand unless you want to add/remove an asset manually (same fields as `assets_catalog.json`).
