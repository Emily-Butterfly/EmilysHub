# Deepening plan

From the architecture review of 1 November 2026 (last 150 commits). Five candidates, each a refactor that turns shallow
modules into a deep one: more behaviour behind a smaller interface, for **leverage** (callers learn less) and
**locality** (a rule, its bugs and its tests live in one place). Vocabulary: module, interface, implementation, depth,
seam, adapter, leverage, locality. Domain terms are those of [SPEC.md](../../SPEC.md).

Order of work: **2 → 1 → 5 → 3 → 4**. #2 is first because it is small, stays inside `routines/`, and #1 builds on it.

Every step ends green: `./gradlew lint testDebugUnitTest` locally, then push, then CI. Behaviour does not change unless a
step says so. Each step is one commit.

---

## 2 · One place where a Fälligkeit changes status (occurrence lifecycle)

**Status:** done (commits `b55e1d4` and the review fixes after it). `routines/OccurrenceLifecycle.kt` with `move`,
`missNow` and `delete`, plus `settling`/`settleCreated` for goals of routines changed in the editor until #5 takes
those. `OccurrenceLifecycleTest` (200 seeded walks of 30 steps) and `RoutinesSeamTest` hold it. `Routines.kt` went from
727 to about 660 lines, as it gained what joint skipping needs (below).

How it went against the steps:

- **Behaviour changed, on purpose.** On its first run, the invariant test (step 1) found a bug: `skipTogether` wrote the
  status around the rules, so "Überspringen" on a joint reminder tapped after ticking one of it off on Heute left the
  others "skipped by" an occurrence no longer done, and they could not be undone. Rather than keep it out as step 1
  said, the refactor fixes it, as the fix is what the module is for: joint skipping now takes along what the one done
  covered at its time, so all it reminded of end up skipped (`CouplingTest`). A move away from Done also gives back
  what an occurrence left covered by an older version. Every move keeps the two rules the old single-occurrence query
  had: once missed, an occurrence stays missed, and one moved is covered by no other any more.
- **Coupling membership stays in `Routines`** (`saveCoupling`, `dissolveIfAlone`): it is editing a group, not a status.
- **One commit** for steps 1–6, as the steps only compile together, and one for the review fixes.
- `replan` still inserts occurrences as Dropped on a day dropped ("Heute nicht"): new occurrences, not a status change.

### Why

Coupling (#41), "Heute nicht" (#33) and goal settling (#21) are rules about *any* change of an occurrence's status. Today
eight methods in `routines/Routines.kt` each apply them by hand:

| Call site in `Routines.kt` | What it must remember today |
|---|---|
| `setStatus` (via `markDone`, `skip`) | `dao.uncover(by)` when leaving Done, `coverOthers` when entering Done, `settlingGoal` |
| `tick` → `tickOffIfAllTicked` | `coverOthers` after `dao.setStatus(Done)`; `settlingGoal` around it |
| `tick` (untick a done one) → private `reopen` | `dao.uncover(by)`, then `replan` |
| `reopen` (public) | refuse covered/missed, `deleteTicks`, `undoDrop`, `settlingGoal` |
| `undoDrop` | Dropped → Missed / Open by window, then `replan` |
| `drop` / `dropTogether` | `settlingGoal`, only open ones on Heute |
| `skipTogether` | `settlingGoal` per occurrence, one by one |
| `setAside` (pause, archive) | ended ones → Missed with `settlingGoal`; open ones deleted |
| `markMissed` | `!stillCovered(...)`; `partOf` snapshots by hand; `settleGoals` per routine |
| `delete` | `dao.uncoverAllBy(routineId)`, `dissolveIfAlone`, `settleGoals(part, None)` |
| `saveChecklist` | `tickOffIfAllTicked` for each open occurrence |

The review of #41 (commit `a23e45c`) fixed three of these call sites, each of which had forgotten a rule.

### Target shape

A new module `routines/OccurrenceLifecycle.kt`, `internal` to the `routines` package and used only by `Routines`. Its
interface is one idea: **move these occurrences to that status**. Behind it:

- leaving Done uncovers what that occurrence covered;
- entering Done covers the open occurrences of the other active routines of its coupling on its day;
- the goal of every routine touched is snapshotted before and settled after, once per routine and not once per
  occurrence;
- which open occurrences are missed now (window ended, and no other routine of the coupling can still be done that day);
- what a routine takes along when it is deleted (`uncoverAllBy`, dissolving a group left with one routine).

`Routines` keeps what is about routines and days: planning, `replan`, Heute, reminders, the editor. It no longer calls
`dao.setStatus`, `dao.setStatuses`, `dao.cover`, `dao.uncover` or `dao.uncoverAllBy`. A guard test holds that seam.

### Steps

1. **Invariant test first** (`app/src/test/java/de/emilyshub/routines/OccurrenceLifecycleTest.kt`, in-memory database
   as in `CouplingTest`). It runs seeded random walks of the public `Routines` operations over a coupled group of three
   routines and one with a checklist: `markDone`, `skip`, `reopen`, `tick`, `dropForToday`, `skipTogether`,
   `dropTogether`, `markMissed` after moving the `TestClock`, `pause`/`resume`, `delete`. After every operation it
   checks:
   - **I1:** an occurrence with `coveredBy` is Skipped, and its coverer exists, is Done, and is of the same coupling
     and day.
   - **I2:** nothing is skipped "by nothing": `uncoverAllBy` left no dangling `coveredBy`.
   - **I3:** every goal's done state equals what `settleGoals` would give from scratch, for the transitions the walk
     made. This is checked against a routine with a target under a goal.

   The test must pass against today's code before step 2. If it finds a real bug, write it down under *Open questions*
   and keep it out of this refactor.
2. **Create `OccurrenceLifecycle`** with the private helpers moved over unchanged: `coverOthers`, `stillCovered`,
   `dissolveIfAlone`, `partOf`, `settlingGoal`, `settleGoals`. `Routines` constructs it from its own
   `database`/`clock` and calls the moved helpers through it. No behaviour change.
3. **One transition.** Add `move(ids, to)` to the lifecycle: load the occurrences, snapshot each routine's goal part,
   uncover those leaving Done, write the statuses with one `dao.setStatuses` per status, cover for those entering Done,
   then settle each routine once. Route the call sites to it:
   - `setStatus` → `move(listOf(id), status)`
   - `tickOffIfAllTicked` → `move(listOf(id), Done)`
   - private `reopen(occurrence, routine)` → `move(listOf(occurrence.id), Open)`, then `replan` stays in `Routines`
   - `undoDrop` → `move(ended, Missed)` and `move(open, Open)`
   - `drop` → `move(open, Dropped)`
   - `skipTogether` → `move(togetherWith(id), Skipped)`, one call
   - `setAside` → `move(ended, Missed)`
   - `markMissed` → `lifecycle.missNow(now)`, which picks with `stillCovered` and calls `move(…, Missed)`
4. **Deletion.** `delete` calls `lifecycle.delete(routineId)` (uncover all it covered, delete, settle its goal to
   None), then dissolves a group left with one routine.
5. **Seam guard test** (`RoutinesSeamTest`): `Routines.kt` contains none of `dao.setStatus(`, `dao.setStatuses(`,
   `dao.cover(`, `dao.uncover(`, `dao.uncoverAllBy(`. The lifecycle is the only writer of statuses.
6. **Tidy.** Remove `settlingGoal` from `Routines`, update the KDoc of `Routines` and of the lifecycle, and run the full
   suite and the two-axis review. Commit as `Occurrence lifecycle: one place where a status changes`.

### Done when

- No module but the lifecycle writes a status, covers or uncovers (guard test green).
- `OccurrenceLifecycleTest` passes with at least 200 walks of 30 operations each, all seeded and reproducible.
- `CouplingTest`, `RoutinesTest`, `RoutineGoalsTest`, `GoalsTest`, `JointReminderTest` and `RemindersTest` pass unchanged.

### Decided (1 November 2026)

Both former open questions were answered "yes", and are now rules of `OccurrenceLifecycle` (`cover`, `coverOn`), in
SPEC.md §2:

- **(a)** One whose skip is undone on a day another of its group was done is skipped by that one at once.
- **(b)** One missed is skipped by another of its group done later that day, and comes back, to be missed again as
  `missNow` has it, once that is undone.

The rule behind both: on a day one of a group is done, none of the others is open or missed. The lifecycle applies it
after every move, and `Routines` after planning a day, replanning one, or bringing a routine back, whose occurrence done
then counts again. `OccurrenceLifecycleTest` holds it as an invariant, and as a missed one turning skipped can change a
streak, every move settles the goals of all routines of the group.

---

## 1 · One Erinnerung module for routines and to-dos

**Status:** done in its core (commits `dc1d49f`, `2d615e9` and the review fixes after them); two steps left as they are,
on purpose.

- **Steps 2 and 3 done.** `ReminderSource` with `RoutineReminding` and `TodoReminding`; `Reminders` loops over them.
  One answer path, `Reminders.answer(about, Answer)`, for the notification's receiver and the full-screen alarm alike;
  what writes runs outside the lock, inside `answer`. The ring service, `Sounds` and the alarm ask
  `Reminders.reminder(about)`, no longer the routines or to-dos.
- **Step 1 as a wrapper.** `DueReminder` wraps `Reminder` and `TodoReminder` with what both have (what it is about,
  title, full screen, tone, joint, window end) rather than one data shape: a routine's and a to-do's notification and
  alarm say different things (time window and joint choices against due date and places), and keep their own fields.
  `Alarmed` is the key. What "Später" offers is one rule over the window end, for both.
- **Step 4: the seam, not the split.** `ReminderScreen`, with `ReminderNotifications` on the phone and
  `FakeReminderScreen` in the tests, and `Offering` part of it. What a reminder offers stays in the notification's
  extras: it must outlive the app's process, which Android ends between an action and the next, and the notification
  is what outlives it. Building each kind's notification stays in the adapter.
- **Step 5: beside, not instead.** `RemindingTest` (through the fake screen) covers what `Reminders` does with both
  kinds and every answer. `RemindersTest` and `JointReminderTest` stay as they are, as the end-to-end tests through
  Android's notifications and alarms, which the fake cannot stand in for.
- **Step 6 not done.** `Reminders` still takes `routines` and `todos` and makes its sources, and looks around through
  `todos`, which only to-dos need.

Done when, as it stands: no branching on routine against to-do outside the two sources, save where a reminder is drawn
(`ReminderNotifications`, `AlarmActivity.screenOf`), as each kind looks different. A new way to remind touches the
reminder of its kind, its source, `DueReminder` if both have it, and the drawing of its kind.

### Why

The "what pops up when" pipeline is written twice. Routines and Todos each have `remindersDue`, `arrive`,
`releaseFromPlaces`, `popUp`, `stillDue`, `reminder(id)`, `nextAlarm`, `tones()`, and
`anyBoundToPlaces`/`anyWaiting`. `reminders/Reminders.kt` interleaves the pairs by hand: `tidyAndSetAlarm(justShown,
todosJustShown)` runs the same four lines twice. `ReminderNotifications` has twin `show`, `refresh`, `offerPutOff`,
`post` and `notification` for `Reminder` and `TodoReminder`. `ReminderReceiver.onEither(...)` and
`AlarmActivity.answer(ofOccurrence=…, ofTodo=…)` route each action twice, and differently: "Später" goes through
`Reminders`, while Done and Skip go straight to the domain. #36, #38 and #39 each threaded one field through about ten
files.

### Steps

1. **One reminder value.** Introduce `reminders/Reminding.kt` with a `ReminderKey` (`Occurrence(id)` or `Todo(id)`), and
   one `Reminder` shape holding key, title, due at, window end, how it reminds (`fullScreen`, `nameOnLockScreen`,
   `tone`) and `together` for joint reminders. Map today's `Reminder` and `TodoReminder` to it at the edge. No
   behaviour change.
2. **A due-source seam.** One small interface that `Routines` and `Todos` each implement, holding the twin methods
   above: `due(after, until, locate)`, `arrive(locate)`, `release()`, `stillDue(keys)`, `nextAlarm(after)`, `tones()`,
   `watchesPlaces()`. That makes two adapters, so the seam is real. `Reminders` takes a list of due-sources and loops,
   and the duplicated lines in `update`, `remind`, `arrived` and `tidyAndSetAlarm` go.
3. **One answer path.** `Reminders.answer(key, Answer)` with `Answer` = Done(of?), PutOff(by), Skip, Drop, OfferPutOff,
   OfferDone. `ReminderReceiver` and `AlarmActivity` parse the intent into `(key, Answer)` and call it. The lock rule
   ("nothing done while holding it may write …") is enforced inside `answer`, not by callers. `onEither` and the
   `answer(ofOccurrence, ofTodo)` duplicates go.
4. **A screen seam.** Split `ReminderNotifications` into the Erinnerung rules (offering state, which actions,
   lock-screen text) and an adapter that only posts, cancels and lists Android notifications. Add an in-memory adapter
   for tests. Offering state moves out of notification extras into the module's own state, read back from the
   adapter.
5. **Move the tests.** `RemindersTest` (652 lines) and `JointReminderTest` assert on the in-memory adapter, and a thin
   Robolectric test keeps the Android adapter honest (channels, full-screen intent, actions).
6. **Wiring.** `EmilysHubApplication` and `TestApplication` build `Reminders` from the due-sources once; the duplicated
   `onChange = ::updateReminders` wiring collapses.

### Done when

- Adding a new way to remind (a field like `tone`) touches the reminder value, the two due-sources and the Android
  adapter, and nothing else.
- No `if occurrence … else todo` branching outside the two due-sources.

---

## 5 · Ziel settling without caller snapshots

**Status:** done. `wishes/GoalWatch.kt`: what changes a to-do, routine or goal names it to the watch before
(`todo`, `routine`, `goal`) or once it made it (`…Added`), and the watch settles their goals as they are at the end. A
watch within another, in the same transaction, joins it and settles once, at the end of the outer one, so that a part
closed and opened again leaves its goal as the user set it. `GoalWatchTest` holds that, and `GoalSeamTest` that
nothing outside `wishes/` holds a `GoalPart` or settles a goal itself.

- One part in the place of another is not a part added and one gone: the next of a series takes the place of the one
  ticked off, and that one takes it back when opened again, both through `todoTakingPlaceOf`, which carries over how
  the one replaced was at the start of the change, so that one moved to another goal and ticked off by the same save
  opens that goal with its next. `deleteMovingPartsUp`, inside `wishes/`, settles its goal explicitly, as all its
  parts take the place of the goal deleted.
- Making a to-do of a wish goes through `Todos.create`, which names the to-do added; the wish is not changed.
- `OccurrenceLifecycle.settling` and `settleCreated` stay as the routines' way to the watch; how a routine counts
  towards its goal moved into the watch.

### Why

`wishes/GoalSettling.kt`'s `settleGoals(before: GoalPart, after: GoalPart, today)` is edge-triggered, so each caller
must snapshot before and after in the right order. In `todos/Todos.kt` there are six sites (lines ~113, 129, 167, 420,
446, 486) using `asPart()`. The `takingBack` case in `openAgain` picks the "before" by hand. A forgotten snapshot is a
silent bug.

### Steps

1. Add `wishes/GoalWatch.kt`: `HubDatabase.watchingGoals(today) { watch -> … }`. Inside, callers say
   `watch.routine(id)`, `watch.todo(id)` or `watch.goal(id)` **before** changing it. The watch snapshots then, and at
   the end of the block re-reads each part and calls the existing `settleGoals` for each. `GoalSettling` stays as the
   implementation.
2. `OccurrenceLifecycle` (from #2) uses the watch instead of `partOf`/`settlingGoal`.
3. `Todos`: replace the six `settleGoals(x.asPart(), y.asPart())` with `watch.todo(id)` at the start of each write
   transaction. `asPart()` moves into the watch.
4. `Wishes`: the subgoal and convert paths use `watch.goal(id)`.
5. Tests: `GoalsTest` and `RoutineGoalsTest` unchanged. Add `GoalWatchTest` for the takingBack, deleted-part and
   two-parts-in-one-transaction cases.

### Done when

- `GoalPart` is not mentioned outside `wishes/`.

---

## 3 · A back stack for the app shell

**Status:** done but for step 5. `ui/navigation/Destination.kt` (one case per screen over the tabs, with what it
shows) and `ui/navigation/BackStack.kt` (push, pop, replace the top, and the household book's rules: the year from a
month, a month from the archive), held by `BackStackTest` without Compose. `AppShell` keeps one saved `BackStack`
instead of 30 flags, draws what is on top, and pops it on back; the tab-local filters and views stay as they were.
Every screen keeps what was typed and scrolled while one opened from it covers it, not only the editors, and forgets it
once closed.

- Steps 1–4 done in one commit rather than by groups: the flags' order of `when` branches had to go at once.
- Step 5 not done: `TopLevelScaffold` keeps its parameters, one `onOpenX` each, now each a push.

### Why

`ui/EmilysHubApp.kt` (694 lines, changed in 49 of the last 150 commits) keeps navigation as 34 `rememberSaveable`
variables (`settingsOpen`, `routineEditor`, `archiveOpen`, `statisticsOpen`, `statisticsRoutineId`, `tagsOpen`,
`placesOpen`, `statusOpen`, `backupsOpen`, `todoEditor`, `todoArchiveOpen`, `wishEditor`, `wishReviewOpen`, `noteEditor`,
`noteTabsOpen`, `ledgerOpen`, `ledgerCategoriesOpen`, `bookingEditor`, `recurringOpen`, `ledgerArchiveOpen`, `ledgerYear`,
`ledgerMonthFromYear`, `ledgerMonthBeforeYear`, `recurringEditor`, `ledgerMonth`, `placeEditor`, …). Which screen is on
top depends on the order of the `when` branches. `TopLevelScaffold` takes 30 parameters. The Haushaltsbuch
month-from-year logic was fixed twice.

### Steps

1. **List the destinations.** `ui/navigation/Destination.kt`: a sealed, `@Parcelize` type with one case per screen
   above, carrying its argument (e.g. `RoutineEditor(target)`, `LedgerMonth(month, fromYear: Boolean)`). Tab-local
   view state (`notesView`, `todoOrder`, the tag filters, `searchQuery`) stays as it is: it is not navigation.
2. **The back stack.** `ui/navigation/BackStack.kt`: a saveable `List<Destination>` with `push`, `pop`,
   `replaceTop` and `popTo`, plus `current` and `isEmpty`. It is pure Kotlin, with `BackStackTest` covering the order
   rules (editor over list, statistics routine over statistics, ledger month over year, back from a month opened from
   the year).
3. **The renderer.** One `@Composable fun Render(destination, stack)` with one branch per destination. `BackHandler`
   pops once, globally.
4. **Migrate in groups**, one commit each: settings screens; routine screens; to-do and goal screens; notes; ledger
   (removes `ledgerMonthFromYear`/`ledgerMonthBeforeYear`). After each group, `AppShellTest` and the screens' Robolectric
   tests stay green.
5. **Shrink `TopLevelScaffold`** to `(current tab, onSelect, stack)`. The `onOpenX` callbacks become `stack.push(X)`.

### Done when

- `EmilysHubApp.kt` has no `…Open` flags. A new screen is one `Destination` case and one renderer branch.

---

## 4 · Derive the backup check from the schema

**Status:** last. Worth doing when the next schema change comes.

### Why

`backup/DataTables.kt` already reads tables generically. Around it, table and column facts are kept by hand: the entity
list in `backup/BackupDao.kt`, the `readBack` map in `backup/Backups.kt` (`check(reads.keys == tables)`), the
`asOfVersionN` chain in `test/.../backup/BackupsTest.kt`, and the `newer` column map in
`androidTest/.../BackupsOnDeviceTest.kt` (changed in 25 of the last 150 commits). Commit `1f0c7a5` touched 16 files, 8
of them schema or backup.

### Steps

1. `readBack`: derive the table → DAO read from `HubDatabase`'s entity list via `DataTables`, so that a missing read
   fails by construction rather than by `check`.
2. A test fixture `backupAsOf(version)` built from `app/schemas/de.emilyshub.data.HubDatabase/<n>.json`, reusing
   `BackupMigration.layoutOf`. `BackupsTest` replaces the `asOfVersionN` chain with it.
3. `BackupsOnDeviceTest`: derive `newer` by diffing the schema JSONs of the backup's version and the current one.
4. A checklist in `docs/agents/` for a schema change: entity, migration, migration test, export the schema. Nothing
   else.

### Done when

- A new column touches the entity, the migration and its test, and nothing in `backup/` or the backup tests.
