# Deepening plan 5

From the fifth architecture review of 11 November 2026, made after plan 4. It picks up what the earlier plans left: the
editors that still load and save in their Composable (Notizen, Wünsche, Orte), the goal made into a to-do, and the
budget input. Vocabulary: module, interface, implementation, depth, seam, adapter, leverage, locality. Domain terms are
those of [SPEC.md](../../SPEC.md).

Order of work: **1 → 2 → 3 → 4**, each step green on `./gradlew lint testDebugUnitTest`, then pushed. Behaviour does
not change unless a step says so.

| # | Candidate | Strength | Dependency category |
|---|---|---|---|
| 1 | Notizen join the editing sessions | Strong | local-substitutable (in-memory Room) |
| 2 | A to-do made of a goal, in one move | Worth exploring | local-substitutable |
| 3 | Wünsche and Orte join the editing sessions too | Worth exploring | local-substitutable |
| 4 | A budget as typed, judged once | Worth exploring | in-process |

Looked at and left alone: the app shell (`EmilysHubApp.kt`, the router and top-level scaffold, wiring rather than
decisions); the full-screen alarm's queue (bound to the Activity, covered by `AlarmTest` and `TodoAlarmTest`); a pure
`LedgerMonth.of` (`MonthOffer` answers the page already); the opening balance's input (its own rule: signed, zero
allowed).

Not taken up, for a later review: the names of backups made by hand and at night, known to two modules and held
together by a comment (`Backups.fileName`, `NightlyBackups.NIGHTLY_NAME`); the Doku deleting each level by hand next
to its `ON DELETE CASCADE` foreign keys, which wants a device test first.

---

## 1 · Notizen join the editing sessions

**Status:** done. `NoteEditing` (notes/NoteEditing.kt) is the note being edited, as Routinen, To-dos and the
Haushaltsbuch have theirs: new, or the one saved with its draft and whether it is in the archive. `Notes.editing(id)`
reads it in one transaction, with its links and tags; `Notes.save(editing, draft)` makes or changes the note, or
refuses the draft for its problems as `Saving.Refused`, with nothing saved. The editor no longer reads a note in four
calls nor picks between `create` and `update`; it opens an archived note only to read by what `editing` says.
`Notes.isArchived` is gone, its only caller was the editor. `create` and `update` stay, as the tests seed notes with
them and `save` is built from them. Tests: `notes/NoteEditingTest`.

## 2 · A to-do made of a goal, in one move

**Status:** done, in a smaller shape. `Todos.ofGoal(goalId)` reads the goal and makes its to-do in one transaction,
through the same insert as `create` (the goal watch, tags, reminder); none once the goal is deleted. The editor calls it
after saving the goal, the step "add a subgoal" takes too, so the goal's own save stays a transaction of its own:
dying in between leaves the goal saved and no to-do, which a second tap mends. `Wishes.todoDraftOf` is gone, and with it
the order of the steps from the Composable and `GoalsTest`. Tests: `wishes/GoalsTest` (the goal done is in progress again;
a goal deleted makes none).

## 3 · Wünsche and Orte join the editing sessions too

**Status:** open.

## 4 · A budget as typed, judged once

**Status:** open.
