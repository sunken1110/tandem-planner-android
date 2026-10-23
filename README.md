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
- Each member has an accent color. Event dots and author chips show who added what.
- **Offline-first:** events can be read and added without a connection, and they sync automatically when it comes back.
- Events saved by earlier local-only versions can be migrated into the shared calendar once.

### Month view and events
- Tap a month to expand it in place (shared-element transition), and swipe through months with the arrows.
- Tap a day to see and add its events (title plus an optional time).
- Weekend day numbers are always colored (Sunday red, Saturday sky blue).

### Polish
- Ambient seasonal particles: petals, light bubbles, falling leaves, snow. They follow the season of the month you open.
- Today's cell has a periodic shimmer; in dark mode, filled cells get a soft glow.
- Haptic feedback for taps, saves and invalid input.
- A system splash screen hands off to an optional full-screen intro image.

---

## Roadmap

- [ ] Edit existing events (with `updatedAt` / `updatedBy`)
- [ ] D-day countdowns pinned to the year view
- [ ] Shared checklists
- [ ] "Update required" gate driven by a minimum version in Firestore
- [ ] Google Sign-In, so membership survives reinstalls
- [ ] **AI-assisted planning**
  - [ ] Natural-language event entry (e.g. *"Dinner with Minji tomorrow at 7"* → date, time and title filled in)
  - [ ] Weekly / monthly summaries of shared plans
  - [ ] On-device models where possible, for privacy
- [ ] Home-screen widget (Jetpack Glance)
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
├─ model/
│   └─ Season.kt            # Seasons, season boundaries, per-day color
├─ data/
│   ├─ Event.kt             # Event model
│   ├─ SharedCalendar.kt    # Firebase repository: auth, rooms, real-time events
│   └─ LocalEventStore.kt   # Legacy on-device events (for one-time migration)
└─ ui/
    ├─ theme/               # Material theme + calendar/member colors
    ├─ common/              # DayCell, weekday labels, shared helpers
    ├─ effects/             # Seasonal particles, intro overlay, haptics
    ├─ calendar/            # CalendarScreen: screen state and year↔month switching
    ├─ year/                # Year header, progress bar, 12-month grid
    ├─ month/               # Month view, event row, add-event dialog
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

rooms/{code}/events/{eventId}
  date:       "2026-10-17"       # ISO-8601 string
  time:       "18:30"            # free-form, optional
  title:      "Dinner"
  authorUid:  uid
  authorName: "name"             # snapshot, fallback for display
  createdAt:  timestamp
```

**Compatibility rule:** both members update the app manually, so different versions read the same data. Fields are only ever **added**, never renamed or retyped. Readers treat missing fields as defaults and ignore unknown ones.

Security rules live in [`firestore.rules`](firestore.rules).

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
