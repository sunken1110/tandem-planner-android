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
./gradlew.bat lint                   # Android lint
./gradlew.bat test                   # JVM unit tests
./gradlew.bat test --tests "io.github.sunken1110.tandemplanner.SomeTest"   # single test class
```

There are no test sources yet (`app/src/test` and `app/src/androidTest` don't exist). Build types: `debug`, `release`, `benchmark`.

## Device installs

- Never install or launch the app on a phone or emulator: no `installDebug`, `connectedAndroidTest`, `adb install`, `adb shell am start` or similar. `.claude/settings.json` denies the common forms as a backstop, but it can't catch every variant: this rule is the main control.
- Why: `installDebug` installs into every user profile on the device, including Dual Apps and Secure Folder.
- The user installs with **Run** in Android Studio. Claude stops after the build and tells the user: "Android Studio의 Run으로 확인해 주세요."

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
- Deferred writes never run in the row's scope: the delayed check goes through `SharedChecklists.setDoneLater` (flushed by `stop()`), and the swipe-delete animation runs in the parent list's scope.
- Read animated values only in the draw phase or inside `graphicsLayer {}`; derive values with `derivedStateOf`.
- Times are always "오후 3:00", untimed events "하루 종일", dates via `koreanDayLabel()`.

## How to work

- **UI changes: show a demo (mockup) first.** Write the code only after the user picks one.
- Show demos as an HTML mockup file or a Compose `@Preview`. Never commit mockup files.
- After a change, build with `./gradlew.bat assembleDebug` yourself, confirm it passes, and report the result. If it fails, report the error. Don't install it (see "Device installs"); ask the user to check it with Run.
- When you add a new file, tell the user (it must be added to version control).

## Git

- Never commit or push to `main` directly. Work on a branch named `<type>/<short-kebab-case-description>`, where `<type>` is one of `feature`, `fix`, `refactor`, `review`, `docs`, `chore`.
- Claude pushes the branch and opens the PR. The user merges it.
- Never merge a PR (no `gh pr merge`).
- Never use `git push --no-verify` or force push.
- Never delete or move tags.
- Before committing, check with `git status` which files will be included. Write commit messages in English.

## Pull requests

- PR titles and descriptions are in **English**. The description uses these sections:
  - **Summary**: one or two sentences on what the PR does.
  - **Changes**: the changes, grouped by file or area.
  - **Why**: the problem or reason behind them.
  - **How to test**: steps the user can follow (build, then what to check with Run in Android Studio).
  - **Notes**: risks, follow-ups, `firestore.rules` or Firestore field changes, new files. Write "None" if empty.
- After opening a PR, verify it with the `pr-reviewer` subagent (`.claude/agents/pr-reviewer.md`).
- Explain the review result to the user in **Korean** first. Only after the user confirms, post the reviewer's English report to the PR with `gh pr comment <number> --body-file <file>`. Write that file (and any PR body file) outside the repository, e.g. in the session scratchpad or a temp directory, so it is never committed.

## Security

- Never request, accept or create `local.properties` (it holds signing passwords) or keystore files (`*.jks`, `*.keystore`). Never read them either (also denied in `.claude/settings.json`).
- If the user is about to send one by mistake, stop them.
- `google-services.json` is not needed for any task. Don't read it (also denied in `.claude/settings.json`).

Line endings are LF (`.gitattributes`), except `*.bat`/`*.cmd` (CRLF).
