# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Tandem is an Android app for two people: a shared calendar, shared checklists and home-screen widgets.

- Package: `io.github.sunken1110.tandemplanner` (repository: tandem-planner-android). Single module `:app`.
- Stack: Kotlin, Jetpack Compose (Material 3), Jetpack Glance widgets, WorkManager, Firebase (anonymous auth + Cloud Firestore).
- SDK: minSdk 31, targetSdk/compileSdk 37.
- `gradle/libs.versions.toml` is the source of truth for library versions. Check it instead of relying on memory.
- `README.md` describes every feature and the Firestore data model. For structure, the reasons behind decisions and known pitfalls, read `docs/handoff.md` (Korean) before non-trivial changes.

## Commands (Windows, run from repo root)

```bash
./gradlew.bat assembleDebug          # debug APK; run after every change
./gradlew.bat installDebug           # install on a connected device/emulator
./gradlew.bat lint                   # Android lint
./gradlew.bat test                   # JVM unit tests
./gradlew.bat test --tests "io.github.sunken1110.tandemplanner.SomeTest"   # single test class
./gradlew.bat connectedAndroidTest   # instrumented tests (needs a device)
```

There are no test sources yet (`app/src/test` and `app/src/androidTest` don't exist). Build types: `debug`, `release`, `benchmark`.

## Architecture

Package root: `app/src/main/java/io/github/sunken1110/tandemplanner/`.

- **Entry**: `MainActivity` (single activity, `singleTop`) runs system splash → optional intro image → `SetupScreen` (no room yet) or `CalendarScreen`. Widget taps arrive as intent extras (`WidgetExtras`) and become a `LaunchRequest` (open a day or focus quick add).
- **Data** (`data/`): `SharedCalendar` is the Firebase repository (auth, rooms, seat recovery, real-time events). `SharedChecklists` (in `Checklist.kt`) builds on it for lists, items, presence and undoable deletes. Both expose Compose state that the UI reads directly.
- **UI** (`ui/`): `calendar/CalendarScreen` assembles the screen and switches between year and month views. Feature packages: `year/`, `month/`, `editor/`, `quickadd/`, `checklist/`, `setup/`, `share/`. Shared pieces are in `common/`, and particles, haptics and reduce-motion in `effects/`.
- **Seasons**: `model/Season.kt` computes the season and the color of each day; most theming comes from it. `model/Holidays.kt` lists Korean public holidays per year.
- **AI seam**: `ai/EventParser` (sentence → `EventDraft`). `RuleBasedEventParser` is the current regex implementation, meant to be replaced by an LLM-backed one behind the same interface.
- **Widgets** (`widget/`): Glance `TandemWidget`, `WidgetStore` (snapshot of upcoming events), `WidgetStyle` (per-widget background, kept on the device only), `WidgetUpdater` + `WidgetRefreshWorker` (redraws and the scheduled refreshes).
- **Tunables**: `AppConfig.kt`. `DEBUG_TODAY` overrides "today" to preview other seasons and must be `null` in a release.

## Talking to the user

- The user is new to Android development. Explain in **Korean**, in detail, and spell out technical terms.
- Explain why: the cause → the fix → how to check it.
- When there are several options, lay them out as A/B/C with pros and cons, then give your recommendation and the reason.
- Apart from obvious bug fixes, explain large changes first and get confirmation before making them.

## Code rules

- Code comments in **English**; user-facing UI strings in **Korean**.
- Firestore fields are only ever **added**. Never rename or retype an existing field: older app versions share the same data.
- If `firestore.rules` changes, always tell the user. They publish it in the Firebase console themselves.
- Use `kotlin.time.Duration` instead of `Long` milliseconds wherever the API allows (e.g. `6_000.milliseconds`).
- Use KTX (e.g. `prefs.edit { }`, `bitmap.scale()`).
- `build.gradle.kts` and `gradle/libs.versions.toml` are **managed by the user**. Don't edit them; propose needed changes in your explanation.
- The user decides `versionCode` and `versionName`.

## Pitfalls

Reasons and history: `docs/handoff.md`, section 4.

- Glance draws at most 10 children per Row/Column/Box and silently drops the rest → group them in nested Columns of 10.
- `SubcomposeLayout` under `IntrinsicSize.Min` crashes → use a plain `Layout`.
- `SharedChecklists.stop()` is also called on screen rotation → never clear data in `stop()`.
- Deferred writes (check-then-move, swipe delete) run in the parent list's scope, not the row's.
- Read animated values only in the draw phase or inside `graphicsLayer {}`; derive values with `derivedStateOf`.
- Times are always "오후 3:00", untimed events "하루 종일", dates via `koreanDayLabel()`.

## How to work

- **UI changes: show a demo (mockup) first.** Write the code only after the user picks one.
- Show demos as an HTML mockup file or a Compose `@Preview`. Never commit mockup files.
- After a change, build with `./gradlew.bat assembleDebug` yourself, confirm it passes, and report the result. If it fails, report the error.
- When you add a new file, tell the user (it must be added to version control).

## Git

- Never commit or push to `main` directly. Work on a `feature/…` branch.
- Claude pushes the branch and opens the PR. The user merges it.
- Never merge a PR (no `gh pr merge`).
- Never use `git push --no-verify` or force push.
- Never delete or move tags.
- Before committing, check with `git status` which files will be included. Write commit messages in English.
- Write PR titles and descriptions in English.

## Security

- Never request, accept or create `local.properties` (it holds signing passwords) or keystore files (`*.jks`, `*.keystore`). Never read them either (also denied in `.claude/settings.json`).
- If the user is about to send one by mistake, stop them.
- `google-services.json` is not needed for any task.

Line endings are LF (`.gitattributes`), except `*.bat`/`*.cmd` (CRLF).
