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

**Status:** in progress.

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
4. **Deletion.** `delete` calls `lifecycle.forget(routineId)` (uncover all it covered, dissolve its group, settle its
   goal to None) before `dao.delete`.
5. **Seam guard test** (`RoutinesSeamTest`): `Routines.kt` contains none of `dao.setStatus(`, `dao.setStatuses(`,
   `dao.cover(`, `dao.uncover(`, `dao.uncoverAllBy(`. The lifecycle is the only writer of statuses.
6. **Tidy.** Remove `settlingGoal` from `Routines`, update the KDoc of `Routines` and of the lifecycle, and run the full
   suite and the two-axis review. Commit as `Occurrence lifecycle: one place where a status changes`.

### Done when

- `Routines.kt` has no status writes of its own (guard test green), and is about 100 lines shorter.
- `OccurrenceLifecycleTest` passes with at least 200 walks of 30 operations each, all seeded and reproducible.
- `CouplingTest`, `RoutinesTest`, `RoutineGoalsTest`, `GoalsTest`, `JointReminderTest` and `RemindersTest` pass unchanged.

### Open questions (behaviour, not refactor)

- **(a) Undoing a skip while another of the group is done.** B is skipped, then A of its group is done (B is not
  covered, as it was not open), then B's skip is undone. B is open again, reminds, and may be missed, although A was
  done that day. Should a reopened B be covered by A at once?
- **(b) Missed, then another of the group done.** A's window ends while B is skipped, so A is missed. Then B's skip is
  undone and B is done. A stays missed on a day the group was done. Should B being done turn A from missed into covered?

Both are one-line rules inside `OccurrenceLifecycle.move` once step 3 is in: that is the point of the module.

---

## 1 · One Erinnerung module for routines and to-dos

**Status:** next, after #2.

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

**Status:** after #1. The routine side is mostly absorbed by #2 already.

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

**Status:** after #5.

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
