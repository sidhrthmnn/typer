# Typer

An Android keyboard with familiar everyday typing tools and a **Rambler-style voice-draft workflow**. Independent implementation; not affiliated with Google or Gboard.

**Status: initial development prototype, not a finished Gboard replacement.** The source is implemented, but an Android build and real-device QA have not been completed in the authoring workspace. No APK is included yet. See [Typer report](docs/Typer.md) for the precise feature coverage and outstanding work.

## Included

- Native Android IME, setup screen and in-app typing sandbox (Android 8+).
- English QWERTY; a basic direct Malayalam character layout; number and symbol layouts.
- Shift/caps lock, long-press accented letters, repeating backspace, field-aware Enter actions.
- Conservative common-typo correction with immediate backspace undo; local suggestions and user dictionary.
- Experimental English swipe input using a small bundled vocabulary, disabled by default.
- Spacebar cursor dragging, selection/copy/cut/paste, emoji grid and explicitly saved clipboard snippets.
- System/light/dark appearance, number row, key heights, one-handed layout and haptic toggle.
- Password/private-field protection: no word suggestions, clipboard tools or voice features.
- Speech recognition, draft preview, basic local cleanup, optional AI polish, spoken rewriting, undo and insert.
- Optional Python Gemini gateway, request validation, authentication, response bounds, timeout and rate limit.

## Build

Install JDK 17, Android SDK platform 35, and Gradle 8.9. Open this folder in Android Studio or run:

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

Set `ANDROID_HOME` to your SDK location or create an untracked `local.properties` with `sdk.dir=...`.

This archive does not contain a Gradle wrapper binary. With Gradle installed you can generate the standard wrapper with `gradle wrapper --gradle-version 8.9` and commit its generated files. The included GitHub Actions workflow installs the pinned Gradle version directly.

After pushing, **Actions → Android build → Typer-debug-apk** contains the debug APK when the build succeeds. The workflow has not yet run. Release signing keys are deliberately not included.

On the phone: install the APK → open Typer → Enable Typer → Choose Typer. Enable microphone permission only if you want dictation.

## Rambler-style workflow

1. Open **Voice** on the keyboard and tap **Dictate**.
2. Speech becomes a draft. More dictation appends to it.
3. **Basic cleanup** removes `um` / `uh` / `erm`, normalizes spacing and adds basic sentence punctuation locally. It is not a language model.
4. **AI polish ↗** sends only that draft and a cleanup instruction to your configured HTTPS server.
5. **Voice edit** records an instruction such as “make this shorter” and sends it with the draft to the server.
6. Review the result. **Undo** swaps with the previous draft. **Insert** commits it to the current field. **Cancel** discards it.

On Android 12+, on-device recognition is used when available. Availability also depends on installed language models and the speech implementation. On older devices, or when unavailable, system speech can be enabled explicitly in settings and may send audio to that provider. There is no silent online fallback.

Switching the editor, moving its selection while a voice task is active, or closing the keyboard invalidates pending voice results and discards the draft. This conservative behavior prevents insertion into the wrong location.

## Optional AI gateway

Requires Python 3.10+ and a Gemini API key. Choose an available text model for your account using `GEMINI_MODEL` (the app does not hard-code a model).

Set these environment variables using your hosting provider's secret manager:

- `GEMINI_API_KEY`: provider API key, server only.
- `GEMINI_MODEL`: supported model ID, without a `models/` prefix.
- `TYPER_ACCESS_TOKEN`: a random secret of at least 32 characters, shared with your own app installation.

Generate an access token locally with `python3 -c 'import secrets; print(secrets.token_urlsafe(32))'`. Do not commit it. Start with:

```sh
python3 server/server.py
```

The server binds to loopback on port 8080. Put an HTTPS reverse proxy in front of it; configure a 40 KB request limit and avoid request-body/Authorization logging. Add the public `https://your-host/rewrite` URL and the **server access token** in Typer settings. Never paste the Gemini provider key into the keyboard.

This is a single-user gateway, not a hosted service. Hosting, TLS, provider billing, production identity management and per-user quotas are not provisioned. It uses one global 20-requests/minute limit. Before a public launch, replace the reference HTTP server with a production deployment and per-user authentication.

The Android access token is encrypted with a device Android Keystore key. Connection settings can be removed in the app. Normal typing makes no network requests. Manual AI actions transmit the draft to the gateway and Gemini; their privacy and retention policies apply. No analytics, background microphone, or automatic clipboard harvesting is implemented.

## Tests

```sh
python3 -m unittest discover -s server -p 'test_*.py' -v
gradle testDebugUnitTest
```

Backend tests passed locally. Java unit tests are included for corrections, names, case preservation, suggestions, swipe ranking and cleanup; they still need to run through Gradle. Device QA checklist: [TESTING.md](TESTING.md).

## Architecture

- `TyperService`: editor lifecycle, composing text, UI, privacy gates and voice state.
- `TypingEngine`: pure Java deterministic suggestion/correction/seed swipe engine.
- `GlideLayout`: gesture capture over native letter buttons.
- `RamblerClient`: HTTPS rewrite client with bounded responses and no redirect following.
- `SettingsActivity` / `Prefs`: onboarding, configuration and encrypted server token.
- `server/server.py`: Gemini gateway. No app text or tokens in application logs.

## Reference documentation

- [Android input methods](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
- [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [Gemini text generation](https://ai.google.dev/gemini-api/docs/text-generation)
- [Google's Rambler description](https://support.google.com/gboard/answer/17468539?hl=en)
- [Android Gradle plugin 8.7 compatibility](https://developer.android.com/build/releases/agp-8-7-0-release-notes)
