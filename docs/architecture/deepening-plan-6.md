# Deepening plan 6

From the sixth architecture review of 24 November 2026, made after plan 5 and the 71 commits since (the Vokabeltrainer's
daily limits and take-back, Tracking, Mein Warum). Vocabulary: module, interface, implementation, depth, seam, adapter,
leverage, locality. Domain terms are those of [SPEC.md](../../SPEC.md) and [CONTEXT.md](../../CONTEXT.md); this plan
added **Tagespensum**, **Runde** and **Ablage** there.

Order of work: **1 → 2 → 3 → 4**, each step green on `./gradlew lint testDebugUnitTest`, then pushed. Behaviour does
not change unless a step says so.

| # | Candidate | Strength | Dependency category |
|---|---|---|---|
| 1 | The trip to the camera app, once | Worth exploring (fixes a crash) | in-process |
| 2 | The Ablage knows its owners | Strong | local-substitutable (temp folder, in-memory Room) |
| 3 | The Tagespensum, told once | Strong | local-substitutable (in-memory Room) |
| 4 | The Runde as a module | Strong | local-substitutable (in-memory Room) |

Decided with the user (grilling, 24 November 2026):

- **1:** one module in `ui`, `CameraShot` with it, hands the caller the taken photo's address within a block and deletes
  the plain-text photo when the block ends; Tracking's trip out of its island (ADR 0003) hangs on as a start/end hook.
  No camera app: "Keine Kamera-App gefunden." at the button, in all three places, where Sessions and Mein Warum crashed.
- **2:** `Vault` in its own package `vault` is the Ablage. One fixed list of its two owners (Sessions, Gründe), each
  with how its files are listed and its maximum, read by saving and by the restore check alike; it claims and releases
  files. The backups use it directly; `AttachmentVault`, an interface with one adapter, goes. **Behaviour:** a Grund
  whose photo cannot be read as a backup is made keeps its text; only one with a photo alone is left out.
- **3:** `Workloads` in `languages` is the Tagespensum: always with the day's limits, no silent defaults (the
  evaluation asks for all that is due, as SPEC.md §15 has it); it tells the routines done by vocabulary. **Behaviour:**
  each such routine is done at most once a day, whatever told it. The app shell's watcher stays the trigger (while the
  app shows, at midnight) and decides nothing; the practice's flag and the flag parameters go.
- **4:** `PracticeRound` in `languages` is the Runde, on top of the Tagespensum: answer, take back, the card shown, a
  topic or exam practised, a card edited mid-round. Its state is a plain value the screen keeps as it does now. "Weiter
  mit Italienisch" stays outside.
- **Tests:** the rules get tests on the modules against in-memory Room; a UI test that only checked a rule goes once
  the module test covering it passes.

Looked at and left alone: `Fsrs`, `DailyLimits`, `RoundBalance` (deep pure functions, well tested); the backups'
read-back list (long, but fails loudly); the migrations; Tracking's events and photos (behind `IslandFileStore`, ADR
0004); the app shell, but for the decision step 3 takes out of it.

Not taken up, for a later review: the board's measures kept twice, in Compose and in the image of Mein Warum
(`ReasonLooks`, `BoardPicture`); the names of backups, now in three places (`Backups.fileName`, `NightlyBackups`'s
`PREFIX`/`SUFFIX` and its `NIGHTLY_NAME`, which spells them out again); the Doku deleting each level by hand next to
its `ON DELETE CASCADE`, which wants a device test first; the card's editor reading and choosing between create and
save, the shape plan 5 removed elsewhere.

---

## 1 · The trip to the camera app, once

**Status:** done. `rememberCameraTrip` (ui/CameraTrip.kt) is the way to the camera app for the Sessions, Tracking and
Mein Warum: `take()` lends the camera app the one file (`CameraShot`, moved to `ui`), false with nothing left behind if
the phone has no camera app; taken, the caller's block reads the photo, then the trip ends and the file is deleted,
whatever came of it. Tracking's `IslandTrip` is the second adapter of the `TripOut` hook, `NoTrip` the first. All three
say "Keine Kamera-App gefunden." where the Sessions and Mein Warum crashed.

Changed from the plan: the block reads the photo's **bytes** from the file, as Tracking did, rather than taking its
address: Sessions and Gründe read it through Android's document reader before, which the tests replace with a fake that
cannot know the camera's file. `LessonFiles.importPhoto` and `Reasons.importPhoto` take a photo read already, beside
the one at an address (the gallery). The tests clear `FileProvider`'s static cache of folders as each test's app starts
(`TestApplication`), as several tests now lend the camera a file. Tests: `MeinWarumScreenTest`, `LanguagesScreenTest`,
`TrackingPhotoScreenTest` (no camera app, each; a photo taken for a Grund).

## 2 · The Ablage knows its owners

**Status:** open.

## 3 · The Tagespensum, told once

**Status:** open.

## 4 · The Runde as a module

**Status:** open.
