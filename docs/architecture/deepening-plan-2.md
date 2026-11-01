# Deepening plan 2

From the second architecture review of 1 November 2026, made after the five candidates of
[deepening-plan.md](deepening-plan.md) were done. Six candidates, each a refactor that turns shallow modules into a deep
one: more behaviour behind a smaller interface, for **leverage** (callers learn less) and **locality** (a rule, its bugs
and its tests live in one place). Vocabulary: module, interface, implementation, depth, seam, adapter, leverage,
locality. Domain terms are those of [SPEC.md](../../SPEC.md).

Order of work: **4 → 1 → 2 → 6 → 3 → 5**. #4 comes first because it is small and #1 stands on it. #2 and #6 share the
idea of a domain module answering what a screen may do. #3 can be done at any time, best the next time a module is
added. #5 is last, as part of it needs a migration.

Every step ends green: `./gradlew lint testDebugUnitTest` locally, then push, then CI. Behaviour does not change unless a
step says so.

| # | Candidate | Strength | Dependency category |
|---|---|---|---|
| 4 | The day as one module | Worth exploring | in-process (the clock broadcast is an adapter) |
| 1 | Heute as one deep module | Strong | local-substitutable (in-memory Room, `TestClock`) |
| 2 | Editing sessions | Strong | local-substitutable |
| 6 | The Haushaltsbuch month as one value | Worth exploring | local-substitutable |
| 3 | One composition root | Strong | ports & adapters |
| 5 | What routines and to-dos share | Worth exploring | local-substitutable |

Looked at and left alone: `Migrations.kt`/`HubDatabase.kt`, whose churn is the append-only log of migrations;
`search/Search.kt`, already deep; `Notebook`, a deep read model; `RoutineHistory`, pure and tested;
`Backups.readBack`, which keeps one read per table on purpose (plan 1, §4).

---

## 4 · The day as one module

**Status:** done in its core, with #1 in one commit. `Day.kt` with `now`, `zone`, `today`, `dayOf`, `minutes()` and
`dates()`, the system's broadcasts as `clockChanges(context)`; `DayTest` holds the minute tick, midnight and clock
changes on virtual time. `EmilysHubApplication` builds `day` once from `open val clock`, which `TestApplication`
overrides with its `TestClock`.

- **Steps 1–4 done.** `rememberToday(routines)` and its broadcast receiver are gone; `ui/Now.kt` keeps two one-line
  helpers over `day.minutes()` and `day.dates()`, collected with `collectAsStateWithLifecycle`, and no wait of its own.
  Heute, the To-dos list and archive, the Haushaltsbuch month and year, the statistics and the routine, to-do and wish
  editors take `day`. Gone: `Routines.timeUntilTomorrow` (and its test of 23- and 25-hour days, which no longer has a
  subject: the day turns with the minute), `Todos.now`/`zone`/`dayOf`, `Ledger.now`/`dayOf`, and `Wishes.today`
  (private now).
- **Behaviour changed, on purpose**: the routine and wish editors and the statistics follow the day while they show,
  rather than keeping the day they were opened on.
- **Step 5 holds as it was**: the month and year of the Haushaltsbuch are restarted by `rememberToday(day)`, which
  changes once a day, as `dayOf(now)` did.
- **Left for #2**: `Routines.today` (24 tests), `Todos.today`/`isPast`/`nextFullHour`, `Ledger.today`/`thisMonth`, which
  the editors and `AlarmActivity` still ask.

### Why

"The day turned" cuts across every area and has no owner, so each screen implements it again and gets it fixed again.

- Four domain modules re-export the clock: `Routines.today`/`timeUntilTomorrow` (Routines.kt:624–627),
  `Todos.now`/`zone`/`today`/`dayOf`/`nextFullHour` (Todos.kt:331–343), `Ledger.today`/`now`/`dayOf`/`thisMonth`
  (Ledger.kt:77–87), and `Wishes.today` (Wishes.kt:144). The UI calls these helpers 26 times, borrowing the clock from
  whichever module is at hand: the heatmap of routines and to-dos asks `todos.zone()`.
- "Today" is kept current in three different ways, or not at all:

  | Screen | Asks | Moves on |
  |---|---|---|
  | Heute (list and flame) | `rememberToday(routines)` | midnight wait, `ACTION_TIME/TIMEZONE/DATE_CHANGED`, resume |
  | To-dos list | `todos.dayOf(rememberNow(todos::now))` | minute tick, resume |
  | Haushaltsbuch month and year | `ledger.dayOf(rememberNow(ledger::now))` | minute tick, resume; restarts `month()`, which runs `enterDue` |
  | Heatmap | `todos.zone()` | as Heute recomposes |
  | Routine editor | `remember(routines) { routines.today() }` | never |
  | Statistics | `routines.today()` per composition | never |

- Review fixes, each at a separate site: `5195063` (#17, "look at the clock again each minute"), `ea67c83` (#45, "looks
  again when the day changes while it shows"), `2c737a5` (#42, "one today for the whole of Heute"), `9d39490` (#43, "the
  to-dos move to their days in a new time zone").

### Target shape

A module `Day` (`de/emilyshub/Day.kt`, next to `DeviceClock`) that owns the time of day for everything that shows it:

- `now()`, `zone()`, `today()`, `dayOf(instant)`: what the domain modules re-export today.
- `minutes(): Flow<Instant>`: the time now at once, then just after each full minute, and at once when the phone's clock
  or time zone changes.
- `dates(): Flow<LocalDate>`: `minutes()` as days, only when the day changes. A minute tick turns the day within a
  second of midnight, as the midnight wait did, so one ticker is enough for both.

Behind its seam, `clockChanges: Flow<Unit>` with two adapters: the system's broadcasts (`ClockChanges(context)`, a
`callbackFlow` over `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED` and `ACTION_DATE_CHANGED`, not exported) on the
phone and in the Robolectric tests, and a `MutableSharedFlow` in `DayTest`.

What the screens do on resume now comes from `collectAsStateWithLifecycle`: collection stops while the app is in the
background, where waits stand still anyway, and starts again at once on return, with the time read anew.

The domain modules keep their `Clock` for what they write: a `Day` is built on the same clock.

### Steps

1. **`DayTest` first** (`app/src/test/java/de/emilyshub/DayTest.kt`, `runTest` with virtual time and a `TestClock`):
   - `minutes()` gives the time now at once, then again just after the next full minute, however far into the minute it
     started.
   - `dates()` turns to the next day within a second of midnight, and gives each day once.
   - A clock change (moved time, new zone) gives the time and day anew at once, and the next wait is measured on the new
     clock.
2. **Create `Day` and `ClockChanges`**. `EmilysHubApplication` gets `open val clock: Clock = DeviceClock` and builds
   `day` from it; `TestApplication` overrides `clock` with its `TestClock`, so `day` is built once, in one place.
3. **Route the screens to it.** `rememberToday` and `rememberNow` go; Heute (through #1), the To-dos list, the
   Haushaltsbuch month and year, the heatmap, the statistics and the routine, to-do and wish editors take `day`.
   `TodayTest`'s midnight and time zone tests stay as they are and must pass unchanged.
4. **Remove the pass-throughs** that only the UI used: `Routines.timeUntilTomorrow`, `Todos.now`/`zone`/`dayOf`,
   `Ledger.now`/`dayOf`, `Wishes.today`. `Routines.today` stays for now: 24 tests use it, and it can go once they ask
   `app.day`. `Todos.isPast`/`nextFullHour` and `Ledger.thisMonth` stay with #2, where the editors' rules move.
5. **Day-triggered writes**: `Ledger.month()` enters the fixed bookings due (`enterDue`) inside a read, restarted by a UI
   key. Leave the behaviour, but key it on `day.dates()` rather than on a minute, so it runs once a day while the month
   shows rather than each minute. (#6 takes this further.)

### Done when

- No composable keeps the time with a delay or a broadcast receiver of its own; `ui/Now.kt` and `rememberToday` are gone.
- No screen asks a domain module what time or day it is.
- `DayTest` holds midnight, the minute tick and clock changes without Compose.

---

## 1 · Heute as one deep module

**Status:** done. `today/Heute.kt`: `day()` gives one `HeuteDay` (date, entries, upcoming, pinned, flame, heatmap) from
one clock, never mixing two days; `setDone`, `tick`, `skip`, `drop`, `undo` and `unpin` are what Heute does.
`DueRoutine.tickable`, `covered` and `offer` (`RoutineOffer`) hold what an occurrence offers, moved out of
`DueRoutineRow`. `HeuteTest` holds order, midnight, zone, overdue, flame, heatmap, the mixed-day walk and the offers
without Compose.

- `TopLevelScaffold` collects `heute.day()` once while Heute shows (`rememberHeute`), and hands the flame and the day to
  `StreakFlameButton` and `TodayScreen`, which only draw. `TodayHeatmap` draws a `Heatmap`.
- The warnings are a slot of `TodayScreen` filled by `TopLevelScaffold`, so that `ui/today/` takes neither `Routines`,
  `Todos`, `Notes` nor `NightlyBackups`.
- The routines' occurrences are read anew only when the day turns; the to-dos each minute, as before.

### Why

What is on Heute, its order, what each entry allows, and when "today" moves on are spread over four domain modules and
five composables:

- `TodayScreen` (TodayScreen.kt:87–105) takes `Routines`, `Todos`, `Notes` and `NightlyBackups`, subscribes to
  `routines.dueOn`, `todos.dueOn(today, now)`, `todos.upcoming(today, now)` and `notes.onToday()`, and puts them
  together with `todayList(...)` in the composable, waiting until all are there (`d578162`, #35).
- `TodayHeatmap` builds its inputs in the UI: `routines.history()` filtered to Done, `todos.archived()` mapped to
  `doneAt`, and `todos.zone()`.
- `StreakFlameButton` subscribes to `routines.history()` a second time.
- `TopLevelScaffold` keeps the day with `rememberToday(routines)` so that the list and the flame turn together
  (`2c737a5`, #42).
- `DueRoutineRow` (TodayScreen.kt:310–368) holds the occurrence rules: what can be ticked
  (`!= Missed && != Dropped && !covered`) and which action shows for which status, the mirror of what `Routines.reopen`
  refuses (Routines.kt:278–279).

33 commits touched Heute. Its rules are tested only through Robolectric: `TodayTest` ("at midnight the list moves on",
"when the phone changes time zone…"), `TodayTodosTest` ("a to-do turning overdue while Heute shows moves to the top"),
`TodayHeatmapTest`, `StreakFlameScreenTest`. Only the order (`TodayListTest`) is tested without Compose.

### Target shape

A module `Heute` in `today/` (`today/Heute.kt`), built from `Routines`, `Todos`, `Notes` and `Day`. Its interface:

- `day(): Flow<HeuteDay>`: the whole of Heute for the day it is, anew as the day turns, each minute (for to-dos turning
  overdue), and as anything on it changes. `HeuteDay` holds the date, the entries in their order (`todayList`), the to-dos
  under Demnächst, the notes pinned, the flame (or none), and the heatmap. It is only given once every part is there and
  all parts are of the same day, so that Heute never shows half of one day and half of the next.
- What an entry allows (`TodayEntry` gets `tickable` and the action it offers: skip and "Heute nicht" while open, undo
  while skipped or dropped and not covered, none once done or missed).
- What Heute does: tick an entry on or off, skip, drop for the day, undo, tick a checklist item, unpin a note. Each goes
  to `Routines`, `Todos` or `Notes`.

`TodayScreen`, `StreakFlameButton` and `TodayHeatmap` draw a `HeuteDay`. `TopLevelScaffold` collects `heute.day()` once
while Heute shows and hands it to both the flame and the screen. `TodayScreen` keeps `NightlyBackups` for its warning
card, which is about backups, not the day.

`rememberUse` stays where it is: it is what the reminders use, for the status page and the permission prompt as much as
for Heute's warning. It belongs with the reminders, not Heute.

### Steps

1. **`HeuteTest` first** (`app/src/test/java/de/emilyshub/today/HeuteTest.kt`, in-memory database, `TestClock`, `Day` with
   a `MutableSharedFlow` of clock changes). Through `Heute` only:
   - Routines and to-dos due come in `todayList`'s order; Demnächst and the pins are there.
   - Past midnight, the day before's routines stay while their window is open, and the day is the new one.
   - A to-do turning overdue moves to the top within a minute.
   - A zone change gives the day it is there.
   - The flame and the heatmap follow ticking off and undoing.
   - Never a `HeuteDay` mixing two days (a walk across midnight, checking every value given).
   - What an entry allows, for each status, covered or not.
2. **Create `Heute`** with `day()` and the actions; move the tickable/offers rules from `DueRoutineRow` into
   `TodayEntry`.
3. **Draw it.** `TodayScreen(day: HeuteDay, heute: Heute, nightlyBackups, …)`, `StreakFlameButton(flame, …)`,
   `TodayHeatmap(heatmap)`. `rememberToday` goes (with #4). Wire `heute` in `EmilysHubApplication` once, from `clock`.
4. **Tests.** `TodayTest`, `TodayTodosTest`, `TodayHeatmapTest`, `StreakFlameScreenTest`, `UpcomingTodosScreenTest`,
   `TodayWarningTest` and `TodayBackupWarningTest` pass unchanged. They stay as the end-to-end tests of drawing; the rules
   are held by `HeuteTest`.

### Done when

- No composable under `ui/today/` takes `Routines`, `Todos` or `Notes`, or calls `todayList`, `Heatmap.of` or `flame`.
- `HeuteTest` holds midnight, zone, overdue, flame, heatmap and the actions without Compose.

---

## 2 · Editing sessions: load, check and save a draft behind the domain module

**Status:** not started.

### Why

Whether a draft may be saved depends on the entity as stored and on today. That rule is split between the draft, the
screen and a domain `require` that throws:

- `RoutineEditorScreen.kt:132–137`: `endMoved`, `problemsOn(today)`, `rescheduled`, and
  `goneBy = rescheduled && !routines.comesDue(...)`. `Routines.checked` skips this on purpose (Routines.kt:658: "the
  editor tells, as the day can pass while it saves").
- `BookingEditorScreen.kt:91–125` works out the closed-month lock itself and swallows `IllegalArgumentException` when
  `Ledger` refuses.
- `RecurringScreens.kt:240, 294`: "the first month at most 12 months back" lives only in the screen;
  `Ledger.createTemplate` does not enforce it, though every month since is entered at once.
- `TodoEditorScreen` calls `todos.isPast`, `todos.today()` and `todos.nextFullHour()`.
- Loading a draft takes 4–5 shallow getters, each with one caller and not in one transaction:
  `Routines.checklist`, `tagIdsOf`, `placeIdsOf`, `coupledWith` (Routines.kt:93–102); `Todos.checklist`, `tagIdsOf`,
  `placeIdsOf` (Todos.kt:92–98); `Notes.linksOf`, `tagIdsOf`; `Wishes.tagIdsOf`. `Ledger` already has `draftOf`,
  `templateDraftOf` and `newDraft` (Ledger.kt:90–99, 295).
- The same close-once code (`loaded`, `closing`, `closeWith`, `launchNow`) is copied into 7 editors.

Review fixes this caused: `3ccc52f` (#6, `goneBy`), `a95f51a` (#34, `endMoved`/`problemsOn`), `1436e86` (#46, "saves
nothing until it knows which months are closed", "no longer crashes"), `ea67c83` (#45, "a year back at most"). The only
test of "a one-time routine is saved only while one of its times is ahead" is `RoutineEditingTest` (811 lines,
Robolectric, line 202).

### Target shape

Each domain module that has an editor offers an editing session:

- `editing(id?)`: the draft as stored, read in one transaction, or a new one with its defaults (now, today, this month).
- `problems(draft)`: its problems in context: today, the original as stored, closed months.
- `save(draft)`: `Saved(id)` or `Problems(...)`, never an exception for what a user can type.

Screens keep field state and drawing. One shared composable helper does close-once. Start with `Routines` (the most
rules) and `Ledger` (bookings and fixed bookings), then `Todos`, then `Notes` and `Wishes`, which have few rules and
mostly lose getters.

### Steps

1. **Session tests first**, in-memory, per module: "a one-time routine whose times are all past is refused as gone by",
   "an end before today is a problem only when it was moved", "a booking in a closed month is refused", "a fixed
   booking's first month at most 12 months back".
2. `RoutineDraft`/`Routines.editing`, then move `goneBy`/`endMoved` into it; `RoutineEditorScreen` shows the problems
   given.
3. `Ledger.editing` for bookings and fixed bookings; the lock and `MONTHS_BACK` move in; `BookingEditorScreen` drops its
   `catch`.
4. `Todos.editing` (with `isPast`, `nextFullHour`), `Notes.editing`, `Wishes.editing`. Delete the getters with no caller
   left.
5. A shared `rememberClosing()` for the seven editors.
6. `RoutineEditingTest` and the other editor tests shrink to wiring once the rules are held in-memory.

### Done when

- No editor screen holds a rule about what may be saved, nor catches a refusal.
- The getters listed above are gone or private.

---

## 6 · The Haushaltsbuch month as one consistent value

**Status:** not started.

### Why

`LedgerScreen` (LedgerScreen.kt:95–104, 129–175, 197–213) collects five separate flows: `isClosed`,
`openEarlierMonths`, `skipped`, `month` (which enters the fixed bookings due), and the minute tick for `now`. From them it
decides in the UI whether to show "+", "Monat abschließen" or "wieder öffnen", "Übersprungene eintragen", and the chips
of earlier months still open, with the synchronous `ledger.canClose(month)` besides. The booking editor works out the
same closed state again from `closedMonths()`.

The four flows each read `observeClosed()` and can disagree for a frame. Review `1436e86` (#46) fixed exactly that in four
of its six bullets: "does not look open for a moment", "crashes… after the editor closed", "saves nothing until it knows
which months are closed", "closing on its last day enters that day's fixed bookings first".

The rules behind the flows (closing, budgets, fixed bookings, the year) are already deep and tested without the UI
(`ClosedMonthsTest`, `BudgetTest`, `RecurringBookingsTest`, `YearOverviewTest`).

### Target shape

`Ledger.month(m): Flow<LedgerMonth>`: one value with the sums and budgets, whether it is closed, whether it can be closed
now, the fixed bookings skipped, the earlier months still open, and what it offers now. One read of the closed months
for all of it. `enterDue` runs as `Day.dates()` turns (#4), not inside the read. The booking editor asks its editing
session (#2) whether it may save.

### Steps

1. Tests in `ClosedMonthsTest` for what a month offers, open and closed, on its last day and after it.
2. `LedgerMonth` and `Ledger.month(m)`; `isClosed`, `skipped`, `canClose` become private or go.
3. `LedgerScreen` draws one `LedgerMonth`; `LedgerScreenTest` passes unchanged.

### Done when

- `LedgerScreen` collects one flow, and no rule about closing months is in `ui/ledger/`.

---

## 3 · One composition root: build the hub once from ports

**Status:** not started.

### Why

- 26 of the 31 commits that touch `EmilysHubApplication.kt` also touch `TestApplication.kt`. Every feature that added a
  module edited both: #12 tags, #17 to-dos, #19 wishes, #22 notes, #26 search, #44 ledger, #39/#40 tones.
- Each module is built twice, the same way with other arguments:
  `Routines({ database }, DeviceClock, onChange = ::updateReminders)` against
  `Routines({ testDatabase }, clock, Dispatchers.Unconfined, onChange = ::updateReminders)`. Which modules notify the
  reminders (Routines, Todos, Places, Settings and Backups, not Wishes, Notes or Ledger) is a rule repeated by hand in
  both.
- The UI gets the modules as loose parameters through three layers: `MainActivity` → `EmilysHubApp` (17) →
  `AppShell` (22) → `TopLevelScaffold` (31). `993e162` (#44) added 48 lines to `EmilysHubApp.kt`, mostly to pass
  `ledger` along.
- Left over from plan 1 (§1, step 6): `Reminders` takes `routines` and `todos`, builds its sources itself, and
  `lookAround` goes past `ReminderSource` to `todos.lookAround` (Reminders.kt:93).

### Target shape

A `Hub` that builds every domain module and the reminders' wiring from a few ports: the database, the clock, the
dispatcher for I/O, and the device adapters (wifi, location, tones, tone files, ringer, authenticator, keys, backup
folder, night alarm). `EmilysHubApplication` and `TestApplication` supply only the ports. Each port already has two
adapters (`SystemWifi`/`FakeWifi`, `SystemLocation`/`FakeLocation`, `PhoneTones`/`FakeTones`, `AlarmSound`/`FakeRinger`,
`SystemAuthenticator`/`FakeAuthenticator`, `DocumentFolder`/`FakeBackupFolder`), so each seam is a real one. The UI gets
the `Hub`.

### Steps

1. A `Ports` value and `Hub(ports)`; move the construction out of `EmilysHubApplication` unchanged.
2. `TestApplication` builds the same `Hub` from its fakes; its overrides go.
3. `Reminders` takes `ReminderSource`s and a look-around port, not `routines` and `todos` (plan 1, §1 step 6).
4. Worth exploring after: `EmilysHubApp(hub)`, and grouped parameters for `AppShell` and `TopLevelScaffold`.

### Done when

- A new module is one edit in `Hub`, and tests run the graph that production runs.

---

## 5 · What routines and to-dos share: tags, places, checklist and how they remind

**Status:** not started.

### Why

Each part an entry can have is implemented and threaded through once per kind of entry:

- `saveTags` three times (Routines.kt:598, Todos.kt:498, Wishes.kt:158), `savePlaces` twice (Routines.kt:593,
  Todos.kt:503), identical.
- `saveChecklist` in both with the same rule ("an open one with all ticked off is done then"), and `tick()` in both with
  the same branching and, word for word, the comment "Done as a whole, every item counts as ticked off until one is
  unticked" (Routines.kt:331, Todos.kt:311).
- `tagIdsOf`, `untagAll` and `tag` declared in four DAOs.
- The tag filter applied by hand in three list screens, each with the comment "One deleted meanwhile no longer filters"
  (RoutinesScreen.kt:61, TodosScreen.kt:81, WishlistScreen.kt:84).
- `fullScreen`, `nameOnLockScreen` and `tone`: separate columns in `Routine` and `Todo`, fields in both drafts,
  positional arguments of 16-argument constructors, three fields in each editor. #36, #38 and #39 touched 16, 14 and 28
  main files. `test/UserSteps.kt` changed in 31 commits.

### Target shape

Small shared modules for the parts, each with one interface:

- `Tagging(kind)`: save, read and filter by tag, over the four join tables.
- `Checklist`: save, tick, and "done when all ticked", parameterised by where ticks live.
- `RemindingStyle`: full screen, name on lock screen and tone as one value, `@Embedded` in both entities, one draft
  field, one editor section.

Wiederholung (routines) and recurrence (to-dos) mean different things and stay separate.

### Steps

1. `Tagging` first (no migration): tests over the four kinds, then route `Routines`, `Todos`, `Wishes`, `Notes` and the
   three list screens to it.
2. `Checklist` for routines and to-dos, tested once.
3. `RemindingStyle`: weigh the migration (`@Embedded` with the same column names needs none, if the names line up).

### Done when

- A new field like #38's touches the value, the migration if any, and the drawing.
