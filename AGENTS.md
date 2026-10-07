# Quota for Android: agent instructions

Android app (Kotlin, Jetpack Compose) and Home screen widget (Jetpack Glance) that show the remaining subscription usage published by `quota-server` from the macOS project (github.com/turinglabsorg/quota). Keep behavior, copy and visuals in sync with the macOS app, Quota iOS (github.com/turinglabsorg/quota-ios) and Quotax (github.com/turinglabsorg/quotax).

## Layout

- `app/src/main/java/com/turinglabs/quota/model`: the server payload (`UsagePayload.parse`), windows, levels and formatting. Pure Kotlin, unit-tested.
- `data`: `QuotaStore` (preferences, payload cache, Keystore-encrypted token) and `QuotaClient` (pairing and usage).
- `work/RefreshWorker`: WorkManager job that refreshes the cache and the widget every 15 minutes.
- `widget/QuotaWidget`: Glance widget, responsive between small and medium.
- `ui`: `MainActivity`, `AppViewModel`, Compose screens and string helpers shared with the widget.
- `res/drawable/glyph_*.xml`: provider glyphs. They mirror `ProviderGlyph.swift` in the quota repository (same geometry, 15% stroke, round caps); regenerate them from that geometry when it changes.
- `DESIGN.md`: design notes for the Android surfaces. Read it before any UI change and update it when you add patterns.

## Commands

- Use JDK 17: `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`. `local.properties` (not committed) points to the Android SDK.
- Tests: `./gradlew testDebugUnitTest`. Build: `./gradlew assembleDebug`.
- Emulator: `$ANDROID_HOME/emulator/emulator -avd <avd>`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Screenshots: start the app with `--ez sample true` so the app and the widget show sample accounts, never real ones: `adb shell am start -n com.turinglabs.quota/.ui.MainActivity --ez sample true`, then `adb exec-out screencap -p > file.png`.

## Server API (version 1)

- `POST /v1/pair` with `{"code","name"}` → `{"token"}`; 401 invalid code, 410 expired code.
- `GET /v1/usage` with `Authorization: Bearer <token>` → `UsagePayload` JSON; 401 means the device was revoked: unpair.
- Skip providers and window kinds this app does not know; the server may be newer than the app.

## Rules

- No server address is built in; the user enters it when pairing. HTTPS only (Android blocks cleartext).
- The device token is stored only encrypted with the `quota-device-token` Android Keystore key; never log or print it. Backups are disabled because that key cannot be restored on another device.
- The widget never fetches by itself: `RefreshWorker` fetches, caches and calls `QuotaWidget().updateAll`.
- User-facing strings live in `res/values/strings.xml` (English) and `res/values-it/strings.xml`; keep the wording of the macOS and iOS apps. Percentages are built in code (`"$value%"`), never inside translated strings.
- Code, comments and docs in English.
