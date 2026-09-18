# Device verification checklist

Run on at least Android 8 (API 26), Android 12 (31) and Android 15 (35), with portrait, landscape, gesture navigation, large text and TalkBack. Use a spare/test device until lifecycle and deletion behavior are verified.

## Build gate
- [ ] `gradle testDebugUnitTest lintDebug assembleDebug` succeeds.
- [ ] GitHub Actions produces an installable APK.
- [ ] Install, enable, switch to Typer and switch back to another keyboard.

## Editor correctness
- [ ] Type, select a suggestion, move cursor into existing text; old composition does not overwrite another location.
- [ ] `teh ` becomes `the `; immediate backspace restores `teh`.
- [ ] Names/unknown words are not automatically replaced.
- [ ] Long-press delete stops on finger lift, cancellation and keyboard close.
- [ ] Delete emoji, skin-tone sequences, family emoji and Malayalam combining text without orphan surrogates. Validate ICU boundaries on each supported OS version.
- [ ] Email/URL/password fields never autocorrect. Numeric password fields hide private tools.
- [ ] Search/Send/Next/Done match the editor, while multiline fields insert newlines.
- [ ] Shift, double-tap caps, accents, double-space punctuation and space cursor drag behave consistently.

## Panels and settings
- [ ] Light/dark/system themes, size and one-handed settings apply after returning to typing.
- [ ] Dictionary add and clear take effect in the next editor session.
- [ ] Clipboard is accessed only when opening its panel. Save/clear work across process restart.
- [ ] Incognito turns off suggestions, voice and clipboard; no entered words are persisted automatically.
- [ ] English/Malayalam switching and system subtype changes work.
- [ ] Experimental swipe produces candidates and never inserts text on an ordinary tap.
- [ ] TalkBack can identify letter keys and every toolbar action.

## Voice and network
- [ ] Denied microphone permission yields a recoverable message.
- [ ] On-device recognition works with installed models; missing language packs and airplane mode fail gracefully.
- [ ] Online speech is used only after its setting is enabled.
- [ ] Dictate, stop, append, basic cleanup, AI polish, voice edit, undo, cancel and insert work.
- [ ] Move cursor/switch app/switch field/close keyboard while recording or awaiting AI: no stale text is inserted.
- [ ] Rotate or lock device while recording: microphone is released and no draft leaks across fields.
- [ ] Invalid server token, timeout, rate limit and provider errors preserve the original draft.
- [ ] HTTP URLs and redirects are rejected; response size bounds hold.
- [ ] Provider key never appears in APK, app preferences, request bodies or logs.
- [ ] AI output keeps names, dates, numbers, Malayalam and intended corrections intact in representative examples.
