# Tandem Planner

**A shared planner for two.** Tandem keeps one calendar in sync between two people in real time, and shows the whole year at a glance in colors that flow with the seasons.

> Status: early development. Shared calendar and seasonal year view are working; D-days, checklists and AI-assisted planning are on the roadmap.

<!-- Add screenshots once the UI settles, e.g.
<p align="center">
  <img src="docs/screenshots/year.png" width="260" />
  <img src="docs/screenshots/month.png" width="260" />
  <img src="docs/screenshots/share.png" width="260" />
</p>
-->

---

## Features

### Seasonal year view
- All 365 (or 366) days of the year in a 12-month grid; days up to and including today are filled in.
- Each season has its own palette, and colors shift a little every day within the season:
  - **Spring** (Mar–May): sprout green → fresh green → emerald
  - **Summer** (Jun–Aug): pale sky → clear sky → deep sky blue
  - **Autumn** (Sep–Nov): ginkgo yellow → amber → maple orange
  - **Winter** (Dec–Feb): frost → deep silver → clear silver
- A progress bar shows the year's full color flow, along with days passed and days left in the current season.
- On launch, the filled cells sweep in from January 1 to today.

### Shared calendar for two
- One person creates a calendar and gets a 6-character code; the other joins with it.
- Events sync in real time through Cloud Firestore.
- Each member has an accent color, used to show who added what:
  - Year view: a thin bar under each day, split into up to three segments in display order.
  - Month view: up to two titles as small chips — each member's first event when both have one,
    otherwise that member's first two — plus "+N" for the rest.
  - Display order: all-day events (no time, shown as "하루 종일") first, then by time.
- **Offline-first:** events can be read and added without a connection, and they sync automatically when it comes back.

### Month view and events
- Tap a month to expand it in place (shared-element transition), and swipe through months with the arrows.
  Going back always shrinks into the right card: the year grid keeps its scroll position and scrolls the card into view first.
- Today's date number sits in a dark, season-toned circle; the selected day has a floating ring in the season's color that glides between cells (a deep ring with a soft glow in light mode, a neon tube in dark mode); every few seconds a light runs around it while its glow breathes.
- Tap a day to see and add its events in a bottom-sheet editor. The page scrolls once so the whole list is visible.
- Tap an event in the list to edit its date, time or title in the same sheet; it keeps its original author.
  When the other member edits it, the row notes who did and what the author originally wrote
  (e.g. "민지가 수정함 · 원래 오후 7:00"). Repeated edits keep pointing at the author's version;
  the note disappears if the author edits it or it's changed back to the original.
- **Time without typing**: "시간 없음 / 시간 지정" toggle, then iOS-style 오전/오후, hour and minute wheels (5-minute steps; 오전/오후 also flips on tap) with a haptic tick per step. Wheels are cyclic, rows can be tapped, and the hour wheel walks through the whole day (오전 11 → 오후 12 switches 오전/오후 on its own).
- **Time recall**: while you type a title, the sheet suggests the time of past events with the same or a similar title (e.g. "필라테스" → 오후 8:00), shown as a small "과거 이력 기반 추천" hint. It stays quiet when past times disagree.

### Quick add
- A floating one-line input: type *"내일 저녁 7시 민지랑 저녁"* and the date, time and title are filled in.
- A live preview under your fingers shows how the sentence was understood before you submit.
- The result opens in the same editor sheet, so anything misread is fixed with a tap or a wheel spin.
- Parsing is behind an `EventParser` interface; today it is rule-based, and it is designed to be swapped for an AI model.
- Weekend day numbers are always colored (Sunday red, Saturday sky blue).

### Polish
- Ambient seasonal particles: petals, light bubbles, falling leaves, snow. They follow the season of the month you open.
- Today's cell has a periodic shimmer; in dark mode, filled cells get a soft glow.
- Haptic feedback for taps, saves and invalid input.
- A system splash screen hands off to an optional full-screen intro image.

---

## Roadmap

- [x] Edit existing events (with `updatedAt`, and an "edited by · original" note for the other member's edits)
- [x] Multi-day all-day events (vacations, trips) drawn as continuous bands across the month grid
- [x] Confirmation before deleting an event
- [x] Korean public holidays (incl. substitute holidays and election days), bundled per year
- [x] D-day card for the next event (or a trip under way) at the top of the year view; the badge deepens as the day approaches (D-8+, D-7…2, D-1, D-DAY)
- [ ] Pinned D-days (choose which events to count down to)
- [ ] Shared checklists
- [ ] "Update required" gate driven by a minimum version in Firestore
- [x] Seat recovery after a reinstall (personal recovery codes; only a SHA-256 hash is stored)
- [ ] **AI-assisted planning**
  - [x] Natural-language quick add (rule-based `EventParser`)
  - [x] Periods in quick add ("3일부터 5일까지", "내일부터 2박 3일", "11월 3~6일")
  - [ ] LLM-backed `EventParser` (pasted chat messages, multiple events per sentence, fuzzy phrasing)
  - [ ] Weekly / monthly summaries of shared plans
  - [ ] On-device models where possible, for privacy
- [x] Home-screen widget (Jetpack Glance): D-day card that grows into an upcoming list, hourly background refresh
- [ ] Localization (UI strings currently in Korean)

---

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Backend | Firebase Authentication (anonymous), Cloud Firestore |
| Async | Kotlin Coroutines |
| Build | Gradle (Kotlin DSL), Android Gradle Plugin 9, version catalog |
| Min SDK | 26 (Android 8.0) |

---

## Project structure

```
app/src/main/java/io/github/sunken1110/tandemplanner/
├─ MainActivity.kt          # Entry point: splash → intro → setup or calendar
├─ AppConfig.kt             # Tunables: animation timing, debug date override
├─ ai/
│   ├─ EventParser.kt       # Sentence → EventDraft interface (the AI seam)
│   ├─ RuleBasedEventParser.kt  # Regex-based Korean date/time extraction
│   └─ TimeRecall.kt        # Suggests a time from past events with similar titles
├─ model/
│   └─ Season.kt            # Seasons, season boundaries, per-day color
├─ data/
│   ├─ Event.kt             # Event model
│   ├─ EventDraft.kt        # Unsaved event (date, optional time, title)
│   └─ SharedCalendar.kt    # Firebase repository: auth, rooms, real-time events
└─ ui/
    ├─ theme/               # Material theme + calendar/member colors
    ├─ common/              # DayCell, MonthGrid, EventMarks, WheelPicker, YearPaint, time/member helpers
    ├─ editor/              # Event editor bottom sheet, time wheel
    ├─ quickadd/            # Floating natural-language input with live preview
    ├─ effects/             # Seasonal particles, intro overlay, haptics
    ├─ calendar/            # CalendarScreen: screen state and year↔month switching
    ├─ year/                # Year header, progress bar, 12-month grid
    ├─ month/               # Month view, event row, selection ring
    ├─ setup/               # Create / join a shared calendar
    └─ share/               # Member bar and share dialog
```

### Rendering notes

The year view draws hundreds of animated cells, so a few rules keep it smooth:

- Animated values (fill progress, shimmer, particle time) are read **only in the draw phase**, never during composition.
- `drawWithCache` builds brushes and paths once per size change.
- Continuously animating elements (particles, today's cell) live in their own `graphicsLayer`, so they don't invalidate the rest of the grid.
- After the launch animation, the month view is rendered invisibly for a few frames so the first real tap doesn't hit JIT compilation.

---

## Data model

```
rooms/{code}
  members:   [uid, uid]          # max 2, enforced by security rules
  names:     { uid: "name" }
  createdAt: timestamp
  recovery:  { uid: bytes }      # SHA-256 of each member's recovery secret
  formerUids: { oldUid: newUid } # seat recoveries: old account → its successor

rooms/{code}/events/{eventId}
  date:       "2026-10-17"       # ISO-8601 string
  time:       "18:30"            # free-form, optional
  title:      "Dinner"
  authorUid:  uid
  authorName: "name"             # snapshot, fallback for display
  createdAt:  timestamp
  updatedAt, editedByUid, editedByName, original   # edit history (optional)
  endDate:    "2026-10-20"       # multi-day events only: last day (all-day, time is blank)

rooms/{code}/claims/{uid}        # short-lived proof during a seat recovery
  seat:   0 | 1
  secret: "ABCDEFGHJKMN"
```

**Seat recovery:** sign-in is anonymous, so reinstalling the app creates a new account. Each member gets a recovery code like `1-ABCD-EFGH-JKMN` (seat number + 12-character secret), shown in the share dialog. Entering the room code and that recovery code on a fresh install writes a claim the rules only accept if its hash matches; the new account then takes over the seat in place (same color and name), the code is replaced by a new one, and `formerUids` keeps the old account's events attributed to the same person.

**Compatibility rule:** both members update the app manually, so different versions read the same data. Fields are only ever **added**, never renamed or retyped. Readers treat missing fields as defaults and ignore unknown ones.

Security rules live in [`firestore.rules`](firestore.rules). They cap a room at two members, keep the creator first, let each member set only their own name, allow a seat to change hands only with its recovery code, keep every event's author fixed, protect the edit-history fields from forgery, and type-check every field so a malformed write can't crash the other phone.

---

## Getting started

### Prerequisites
- A recent stable Android Studio
- An Android device or emulator running Android 8.0 or later
- A Firebase project (free Spark plan is enough)

### 1. Clone
```bash
git clone https://github.com/sunken1110/tandem-planner-android.git
```

### 2. Set up Firebase
1. In the [Firebase console](https://console.firebase.google.com), create a project and **add an Android app** with package name `io.github.sunken1110.tandemplanner`.
2. Download `google-services.json` and place it in `app/`.
3. Go to **Authentication → Sign-in method** and enable **Anonymous**.
4. Go to **Firestore Database → Create database** and pick a nearby region (e.g. `asia-northeast3`) in production mode.
5. Open the **Rules** tab, paste the contents of [`firestore.rules`](firestore.rules), and click **Publish**.

### 3. Run
Open the project in Android Studio, let Gradle sync, and press **Run**.

### Optional resources
| Resource | Purpose |
|---|---|
| `res/drawable-nodpi/splash_bg.png` | Full-screen intro image (skipped if missing) |
| `res/drawable-nodpi/splash_icon_src.png` + `res/drawable/splash_icon.xml` | Aspect-ratio-safe splash icon |
| `res/values/colors.xml` → `splash_background` | Shared backdrop color for splash and intro |

### Debugging other seasons
Set `DEBUG_TODAY` in `AppConfig.kt` (e.g. `LocalDate.of(2026, 3, 1)`) to preview another season's colors, particles and the season-start banner. Set it back to `null` before releasing.

---

## Building a release APK

1. **Build → Generate Signed App Bundle or APK → APK**.
2. Choose your keystore (`.jks`). Keep it **outside** the repository and back it up: every future update must be signed with the same key.
3. Before each release, bump `versionCode` (required) and `versionName` in `app/build.gradle.kts`.

The keystore, APKs and build outputs are excluded via `.gitignore`.

---

## License

No license has been chosen yet. All rights reserved until one is added.

---

## Author

Made by **[@sunken1110](https://github.com/sunken1110)**.
