# Tandem Planner

**A shared planner for two.** Tandem keeps one calendar in sync between two people in real time, and shows the whole year at a glance in colors that flow with the seasons.

> Status: in active development, used daily on two phones. Shared calendar, multi-day events, D-days, public holidays, home-screen widgets and shared checklists are working. AI-assisted planning is next.

<!-- Add screenshots once the UI settles, e.g.
<p align="center">
  <img src="docs/screenshots/year.png" width="260" />
  <img src="docs/screenshots/month.png" width="260" />
  <img src="docs/screenshots/widget.png" width="260" />
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
- A progress bar shows the year's full color flow and the number of days passed. Tapping the count switches to the days left ("55일 남음"): the digits roll over and the bar shows the rest of the year instead; the choice is remembered.
- On launch, the filled cells sweep in from January 1 to today.
- **Last and next year**: "‹ 2026년 ›" above the grid steps between last year, this year and next year (last year fully filled, next year not yet). While another year is shown, "올해로" (or the back gesture) returns to this year. The count, bar and D-day card above always stay on today.
- **D-day card**: the next event (or a trip under way) with its D-day. The badge deepens as the day approaches (D-8+, D-7…2, D-1, then filled on the day). Tapping it opens that day. With nothing coming up, it shows how many days are left in the season.

### Shared calendar for two
- One person creates a calendar and gets a 6-character code; the other joins with it.
- Events sync in real time through Cloud Firestore.
- Each member has an accent color, used to show who added what:
  - Year view: a thin bar under each day, split into up to three segments in display order.
  - Month view: up to two titles as small chips (each member's first event when both have one, otherwise that member's first two), plus "+N" for the rest.
  - Display order: multi-day events first, then all-day events ("하루 종일"), then by time.
- **Offline-first:** events can be read and added without a connection, and they sync automatically when it comes back.
- **Seat recovery:** each member keeps a personal recovery code. After reinstalling the app or changing phones, the room code and recovery code bring back the same seat: name, color and own events.

### Month view and events
- Tap a month to expand it in place (shared-element transition), and move between months with the arrows. The arrows continue across years (12월 › opens next year's 1월, shown as "2027년 / 1월"), within last year to next year. The "‹ 2026년 · ‹ 11월 ›" bar stays at the top while the rest scrolls.
- Saving or moving an event to another day (also in last or next year) opens that day, and so do the D-day card and widget taps.
- Going back always shrinks into the right card: the year grid keeps its scroll position and scrolls the card into view first.
- Today's date number sits in a dark, season-toned circle. The selected day has a floating ring in the season's color that glides between cells (a deep ring with a soft glow in light mode, a neon tube in dark mode). Every few seconds a light runs around it while its glow breathes.
- **Public holidays** (incl. substitute holidays and election days) show a red day number and a short name. Weekend day numbers are always colored (Sunday red, Saturday sky blue).
- **Multi-day events** (vacations, trips) are all-day and drawn as continuous bands across the grid, continuing on the next row at the end of a week. The day list shows "11월 3일 ~ 6일 · 2/4일째".
- Tap a day to see and add its events in a bottom-sheet editor. The page scrolls once so the whole list is visible.
- Tap an event to edit its date, period, time or title in the same sheet; it keeps its original author. ‹ › move a date by one day; tapping the date opens a month grid right below it to pick any day (the last day of a multi-day event too), and if the saved event no longer covers the day you opened it from, the month view follows it to its nearest day. When the other member edits it, the row notes who did and what the author originally wrote (e.g. "민지가 수정함 · 원래 오후 7:00"). Repeated edits keep pointing at the author's version. The note disappears when the author edits the event, or when it's changed back to the original.
- Deleting asks for confirmation first, with the event's date, time and title (and the whole period for a multi-day event).
- **Time without typing**: "시간 없음 / 시간 지정" toggle, then iOS-style 오전/오후, hour and minute wheels (5-minute steps; 오전/오후 also flips on tap) with a haptic tick per step. Wheels are cyclic, rows can be tapped, and the hour wheel walks through the whole day (오전 11 → 오후 12 switches 오전/오후 on its own).
- **Time recall**: while you type a title, the sheet suggests the time of past events with the same or a similar title (e.g. "필라테스" → 오후 8:00), shown as a small "과거 이력 기반 추천" hint. It stays quiet when past times disagree.

### Quick add
- A floating one-line input: type *"내일 저녁 7시 민지랑 저녁"* and the date, time and title are filled in.
- Periods work too: *"3일부터 5일까지 제주 여행"*, *"금요일부터 2박 3일 강릉"*, *"11월 3~6일 부산"*.
- A month and day without a year goes to whichever year is closest, so past events can be logged too ("10월 28일" typed on Nov 2 stays this year).
- A live preview shows how the sentence was understood before you submit; a date in another year shows its year.
- The result opens in the same editor sheet, so anything misread is fixed with a tap or a wheel spin.
- Parsing is behind an `EventParser` interface; today it is rule-based, and it is designed to be swapped for an AI model.

### Home-screen widgets
- Three entries in the widget list, each resizable: **1×1** D-day tile, **2×1** next-event card, **2×2** card with the events after it and a "+" for quick add; taller, the list fills the height with more events. Stretched to four columns, the same layouts use longer dates. Times read as in the app at every size ("오후 7:00", "하루 종일"). In the 2-column list, rows show day · dot · title to leave the title room (today's events show their time instead of the day); the 4-column list adds a time column. Events without a time get a hollow dot, and TalkBack reads each row with its full date and time.
- The 1×1 tile takes the D-day step's color, from a faint tint far away to the author's full color on the day.
- Per-widget background: the season color (default), see-through over the wallpaper, or your own photo. A slider sets how strong it is. Photos can be moved and zoomed inside a frame of the widget's real shape, stay on the phone, and are cut again after a resize.
- Tapping an event opens its day; "+" opens the app with the quick-add bar focused.
- Kept up to date when events change in the app, hourly in the background (so the other member's new events show up), at midnight, and when one of today's events passes.

### Shared checklists
- Lists for anything the two of you keep track of together: groceries, packing for a trip, chores. A list can stand alone or be linked to an event, and the event then shows it as a chip in its day ("☑ 짐 3/8"), which opens the list.
- From an event: its editor has a "체크리스트 만들기" row, which makes a list named "{event} 체크리스트" (numbered if the event already has one by that name), already linked, and opens it with the name selected for editing (unsaved event edits are saved on the way). Each list already linked gets its own row with its progress.
- An event can have several lists ("짐", "맛집"): each shows as its own chip, on a single line so event rows keep their height. Lists that don't fit fold into "+N", which opens a small menu (fading and growing in) with every list's full name and progress. Chips and the menu take the season's color while items are left, green once a list is done.
- Under an event, a list's name leaves out the event's own title ("제주 여행 짐" shows as "☑ 짐 3/8", the default name as "☑ 체크리스트"); other names stay as they are.
- A list opened from an event (or a message) goes back to the calendar, not to all lists.
- From a list: "일정에 연결" opens a small month calendar with the authors' dots on days with events; tap a day, then one of its events, whose days are painted as a block, and confirm with "연결". Past events can be linked too.
- Opened from the round button left of the quick-add bar. Its badge counts the lists still to finish (empty lists and lists of events that are over don't count).
- Checks sync live between both phones. Each checked item shows who checked it, and rows the other member just changed glow briefly.
- "민지 보는 중" appears with a breathing dot while the other member has the same list open.
- Tap to check (the box pops, then the row moves to the checked section), swipe sideways to delete (with "되돌리기"), long-press and drag to reorder. Checked items can be hidden per list.
- Unchecking the other member's check offers "되돌리기" too, which gives the check back to them.
- Checking the last item turns the progress bar green, with a short burst shaped by the season (spring petals, summer light bubbles, autumn leaves, winter snowflakes) and a firm haptic. If the other member finishes a list, you get a message ("민지님이 마지막 항목을 체크했어요 · ‘장보기’ 모두 완료").

### Polish
- Ambient seasonal particles: petals, light bubbles, falling leaves, snow. They follow the season of the month you open.
- Today's cell has a periodic shimmer; in dark mode, filled cells get a soft glow.
- Haptic feedback for taps, saves and invalid input.
- A system splash screen hands off to an optional full-screen intro image (skipped when opened from a widget).

---

## Roadmap

- [x] Edit existing events (with `updatedAt`, and an "edited by · original" note for the other member's edits)
- [x] Multi-day all-day events (vacations, trips) drawn as continuous bands across the month grid
- [x] Confirmation before deleting an event
- [x] Korean public holidays (incl. substitute holidays and election days), bundled per year
- [x] D-day card for the next event (or a trip under way) at the top of the year view; the badge deepens as the day approaches
- [x] Seat recovery after reinstalling the app (personal recovery codes; only the SHA-256 hash is stored)
- [x] Home-screen widgets (Jetpack Glance): 1×1 tile, 2×1 card, 2×2 list; season / see-through / own-photo background per widget
- [x] Shared checklists, standalone or linked to an event, with live check-off, "보는 중" presence, reorder and undo
- [ ] Checklists, part 2: suggest a checklist on events that likely need one (AI), reuse a past list or a template, assign an item to one person, quick add into a list ("장보기 우유, 계란"), clear checked items
- [ ] Checklist widget on the home screen
- [ ] Pinned D-days (choose which events to count down to)
- [ ] "Update required" gate driven by a minimum version in Firestore
- [ ] **AI-assisted planning**
  - [x] Natural-language quick add (rule-based `EventParser`)
  - [x] Periods in quick add ("3일부터 5일까지", "내일부터 2박 3일", "11월 3~6일")
  - [ ] LLM-backed `EventParser` (pasted chat messages, multiple events per sentence, fuzzy phrasing)
  - [ ] Weekly / monthly summaries of shared plans
  - [ ] On-device models where possible, for privacy
- [ ] Localization (UI strings currently in Korean)

---

## Tech stack

| Area     | Choice                                                         |
|----------|----------------------------------------------------------------|
| Language | Kotlin                                                         |
| UI       | Jetpack Compose, Material 3                                    |
| Widgets  | Jetpack Glance, WorkManager                                    |
| Backend  | Firebase Authentication (anonymous), Cloud Firestore           |
| Async    | Kotlin Coroutines                                              |
| Build    | Gradle (Kotlin DSL), Android Gradle Plugin 9, version catalog  |
| Min SDK  | 31 (Android 12)                                                |

---

## Project structure

```
app/src/main/java/io/github/sunken1110/tandemplanner/
├─ MainActivity.kt          # Entry point: splash → intro → setup or calendar; widget launches
├─ AppState.kt              # ViewModel holding the calendar and checklists across rotation
├─ AppConfig.kt             # Tunables: animation timing, limits, debug date override
├─ ai/
│   ├─ EventParser.kt       # Sentence → EventDraft interface (the AI seam)
│   ├─ RuleBasedEventParser.kt  # Regex-based Korean date, time and period extraction
│   └─ TimeRecall.kt        # Suggests a time from past events with similar titles
├─ model/
│   ├─ Season.kt            # Seasons, season boundaries, per-day color
│   └─ Holidays.kt          # Korean public holidays, listed per year
├─ data/
│   ├─ Event.kt             # Event model, day grouping, upcoming events
│   ├─ EventDraft.kt        # Unsaved event (date, optional time, title, optional last day)
│   ├─ Checklist.kt         # Shared checklists: models, live sync, presence, undoable delete
│   └─ SharedCalendar.kt    # Firebase repository: auth, rooms, recovery, real-time events
├─ widget/
│   ├─ TandemWidget.kt      # Glance widget: layouts per size, receivers
│   ├─ WidgetStore.kt       # Snapshot of upcoming events for the widget
│   ├─ WidgetStyle.kt       # Per-widget background, photo storage and crop
│   ├─ WidgetUpdater.kt     # Redraws, hourly sync, midnight / event-time ticks
│   ├─ WidgetRefreshWorker.kt   # WorkManager job behind those refreshes
│   └─ WidgetConfigActivity.kt  # Background settings screen with photo crop
└─ ui/
    ├─ theme/               # Material theme + calendar/member colors
    ├─ common/              # DayCell, MonthGrid, MiniMonthPicker (small date-picking calendar), EventMarks (chips, D-day badge), WheelPicker, YearPaint, GlyphButton, time/member helpers
    ├─ editor/              # Event editor bottom sheet, time wheel, inline date picker
    ├─ quickadd/            # Floating natural-language input with live preview
    ├─ checklist/           # Checklist button, sheet (all lists / one list), rows, celebration
    ├─ effects/             # Seasonal particles, intro overlay, haptics, reduce-motion check
    ├─ calendar/            # CalendarScreen: screen state and year↔month switching
    ├─ year/                # Year header (progress, D-day card), 12-month grid
    ├─ month/               # Month view, multi-day bands, event row, selection ring
    ├─ setup/               # Create / join / recover a shared calendar
    └─ share/               # Member bar and share dialog (room code, recovery code)
```

### Rendering notes

The year view draws hundreds of animated cells, so a few rules keep it smooth:

- Animated values (fill progress, shimmer, particle time) are read **only in the draw phase**, never during composition.
- `drawWithCache` builds brushes and paths once per size change.
- Continuously animating elements (particles, today's cell) live in their own `graphicsLayer`, so they don't invalidate the rest of the grid.
- The selection ring's glow is blurred once on the GPU and moved with layer transforms, so gliding never re-blurs it.
- After the launch animation, the month view is rendered invisibly for a few frames so the first real tap doesn't hit JIT compilation.
- Widgets are drawn directly (`GlanceAppWidget.compose`) instead of waiting for Glance's background session, so changes show up right away.

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
  time:       "18:30"            # "HH:mm", or blank for all-day
  title:      "Dinner"
  authorUid:  uid
  authorName: "name"             # snapshot, fallback for display
  createdAt:  timestamp
  updatedAt, editedByUid, editedByName, original   # edit history (optional)
  endDate:    "2026-10-20"       # multi-day events only: last day (all-day, time is blank)

rooms/{code}/claims/{uid}        # short-lived proof during a seat recovery
  seat:   0 | 1
  secret: "ABCDEFGHJKMN"

rooms/{code}/lists/{listId}      # a shared checklist
  title:     "장보기"            # 1–40 characters
  createdBy: uid
  createdAt, updatedAt: timestamp
  eventId:   eventId             # optional: the event this list belongs to

rooms/{code}/lists/{listId}/items/{itemId}
  text:      "우유"              # 1–100 characters
  done:      true | false
  doneBy:    uid                 # who checked it (only while done)
  order:     2.5                 # sort key; a moved item takes the midpoint of its neighbors
  createdBy: uid
  lastBy:    uid                 # who changed it last (must be the writer)
  createdAt, updatedAt: timestamp

rooms/{code}/presence/{uid}      # "보는 중"
  listId: listId | ""            # the list open on that phone, "" for none
  at:     timestamp              # refreshed every 20 s while open; older than 60 s = gone
```

Widget settings and photos are kept only on the phone (app-private storage); nothing about them is sent to Firestore.

**Seat recovery:** sign-in is anonymous, so reinstalling the app creates a new account. Each member gets a recovery code like `1-ABCD-EFGH-JKMN` (seat number + 12-character secret), shown in the share dialog. After installing the app again, enter the room code and that recovery code. The app writes a claim, which the rules accept only if its hash matches. The new account then takes over the seat in place (same color and name), and the used code is replaced by a new one. `formerUids` keeps the old account's events attributed to the same person.

**Compatibility rule:** both members update the app manually, so different versions read the same data. Fields are only ever **added**, never renamed or retyped. Readers treat missing fields as defaults and ignore unknown ones. When the rules change, they are published **before** the app update, and they keep accepting what older versions write.

Security rules live in [`firestore.rules`](firestore.rules). They cap a room at two members and keep the creator first. Each member can set only their own name, and a seat changes hands only with its recovery code. Every event keeps its author, and the edit-history fields can't be forged. Both members can edit any checklist, but an item's creator and its last writer (`lastBy`) can't be faked, and each member writes only their own presence. Every field is type-checked, so a malformed write can't crash the other phone.

---

## Getting started

### Prerequisites
- A recent stable Android Studio
- An Android device or emulator running Android 12 or later
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

| Resource                                                                  | Purpose                                      |
|---------------------------------------------------------------------------|----------------------------------------------|
| `res/drawable-nodpi/splash_bg.png`                                        | Full-screen intro image (skipped if missing) |
| `res/drawable-nodpi/splash_icon_src.png` + `res/drawable/splash_icon.xml` | Aspect-ratio-safe splash icon                |
| `res/values/colors.xml` → `splash_background`                             | Shared backdrop color for splash and intro   |

### Debugging other seasons
Set `DEBUG_TODAY` in `AppConfig.kt` (e.g. `LocalDate.of(2026, 3, 1)`) to preview another season's colors, particles and the season-start banner. Set it back to `null` before releasing.

### Updating public holidays
Holidays are listed per year in `model/Holidays.kt` (`COVERED_YEARS`). Add the next year's list once it is announced, usually in the summer before.

---

## Building a release APK

1. Keep the keystore **outside** the repository and back it up: every future update must be signed with the same key.
2. Add its details to `local.properties` (not committed):
   ```
   keystore.path=C:/path/to/keystore
   keystore.password=…
   key.alias=…
   key.password=…
   ```
   Debug, release and benchmark builds are then all signed with this key, so **Run** installs over a released APK and keeps the account and its data. Without these entries, builds fall back to the debug key.
3. Before each release, bump `versionCode` (required) and `versionName` in `app/build.gradle.kts`.
4. **Build → Build App Bundle(s) / APK(s) → Build APK(s)** (release variant).

The keystore, APKs and build outputs are excluded via `.gitignore`.

---

## License

No license has been chosen yet. All rights reserved until one is added.

---

## Author

Made by **[@sunken1110](https://github.com/sunken1110)**.
