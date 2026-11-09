# Deepening plan 4

From the fourth architecture review of 9 November 2026, made after plan 3. Its hot spots are what changed since plan 2
outside the forms: the Doku's modules and pages, the Haushaltsbuch month and the app shell; and the reminders, as one
decision with two adapters. Vocabulary: module, interface, implementation, depth, seam, adapter, leverage, locality.
Domain terms are those of [SPEC.md](../../SPEC.md).

Order of work: **1 → 2 → 3 → 4 → 5 → 6 → 7 → 8**, each step green on `./gradlew lint testDebugUnitTest`, then pushed.
Behaviour does not change unless a step says so.

| # | Candidate | Strength | Dependency category |
|---|---|---|---|
| 1 | The Doku's "only to read" behind one guard and one read per page | Strong | local-substitutable (in-memory Room) |
| 2 | The Haushaltsbuch month answers its whole page | Strong | in-process, local-substitutable |
| 3 | `DokuPages` owns its moves | Strong | in-process |
| 4 | One reminder face for the notification and the alarm | Worth exploring | in-process |
| 5 | The Doku's modules take a store, not the vault | Worth exploring | ports & adapters |
| 6 | The Doku lock in one module | Worth exploring | in-process, with a clock |
| 7 | Tests write through the interface production uses | Worth exploring | local-substitutable |
| 8 | The shell stops passing settings and the Doku through | Speculative | in-process |

Looked at and left alone: coupling in `Routines.kt` (quiet since plan 2); the note and goal editors without a session
(plan 2's leftover; making a to-do of a goal spans three transactions, for the next time goals change); `Backups` and
`NightlyBackups` behind their ports; the Doku vault's core and the `DokuVault` seam (two adapters); `BackStack`; the
forms (plan 3); tags and checklists (ADR 0001); bands (ADR 0002).

Found on the way, not deepenings: ten Doku pages save and leave without `Closing` (a double tap may save a new
measurement twice); budget and amount input checked in four places; measurement order written three times; cascading
deletes both as foreign keys and by hand in three DAOs; the names of backups made by hand and at night in two modules;
`LineChart`'s scaling untested.

---

## 1 · The Doku's "only to read" behind one guard

**Status:** done. `doku/Editable.kt`: `editablePatient` and `editableAppointment`, deciding by `Patient.canChange`;
every write of `Patients`, `Appointments`, `Measurements` and `Assessments` goes through them, and the four private
helpers are gone. `Appointments.page(id)` gives the appointment with its patient's name and `canChange` in one read, in
place of `get`, `patients.get` and `canChange`; `Patients.textToWrite` says whether a text page may open;
`Measurements.newForm(appointmentId, testId)` and `form(appointmentId, id)` give nothing for a closed patient's
appointment, so `MeasurementPage` closes rather than trusting its caller. Gone: `Appointments.canChange` and
`Assessments.canChange` (no caller). The pages' measurements and forms at an appointment stay reads of their own, as
they belong to `Measurements` and `Assessments` and change while the page shows. **Behaviour changed, slightly**: a
measurement page opened for a closed patient's appointment closes at once instead of offering a form whose saving does
nothing.

"A closed patient is only to read" (SPEC.md §14) is decided about 20 times: three identical two-query helpers
(`Appointments.changeable`, `Measurements.isChangeable`, `Assessments.isChangeable`), two more in `Patients`, and every
page takes the answer from somewhere else (`patient.isClosed`, `appointments.canChange`, `sheet.canChange`).
`MeasurementPage` asks nothing and trusts `AppointmentPage` not to open it; `AppointmentPage` makes four reads to open.

One guard, `DokuDatabase.editablePatient` and `editableAppointment` (doku/Editable.kt), which every write goes through,
deciding by `Patient.canChange`. Each page opens on a read that says what it may do: `Appointments.page(id)` (the
appointment, its patient's name and `canChange`), `Patients.textToWrite`, `Measurements.newForm`/`form` (none for a
closed patient's appointment) and `Assessments.sheet`. Gone: `Appointments.canChange`, `Assessments.canChange`.

## 2 · The Haushaltsbuch month answers its whole page

**Status:** done in its core; the pure `LedgerMonth.of` not done. `MonthOffer` says all the page may do: besides `book`,
`closing` and `enterSkipped`, `editOpening`, `carry` (`Carry.AtOnce` after a closed month, `Carry.AskFirst` after an
open one, none with an opening balance or closed), `carryOnClosing` (not into a closed month) and `budgets`; its fields
have no defaults, so a new one is not left out. `LedgerScreen` and the dialogs no longer ask `closed`. "The same amount
again leaves one carried over as carried over" moved from `OpeningDialog` into `Ledger.setOpening`. The end balance has
one formula, `LedgerMonth.endBalance(opening, balance)`, used by the month, the month before and `Ledger.close`; a test
holds that closing carries over the very end balance the month shows. **Not done**: assembling the month in a pure
`LedgerMonth.of`. The assembly leans on `Ledger`'s own rules for budgets and fixed bookings to come, and is already one
read tested on in-memory Room; moving it would split those rules rather than concentrate them.

`MonthOffer` answers three questions; the page still asks `closed` ten times and holds two rules no domain test sees:
carrying over at once when the month before is closed, else asking first (LedgerScreen.kt), and an opening balance saved
unchanged staying carried over (OpeningBalance.kt). The end balance is reckoned in four places (`Ledger.month`,
`Ledger.endBalance` through SQL, `LedgerMonth.endBalance`, `MonthSums.closingBalance`), and the figure the close dialog
shows is not the one `close` carries. The offers grow to all the page may do, both rules move behind `Ledger`, and the
month is built by a pure `LedgerMonth.of` with one end-balance formula.

## 3 · DokuPages owns its moves

**Status:** done. `DokuPages` has the moves by name: `openFoundAppointment`, `openFoundAssessment`, `measureAt`,
`fillAt` and `gone`, and says whether a page lies `covers`ed under the one shown. `DokuArea` calls them. A draft is told
how its page goes, `Leaving.Closed`, `Turning` or `Covered`, in place of `turning`; `KeepDraft` reads it from the pages
as they are once the page has gone (`LocalDokuPages`), so that `Appointments.draft` keeps a new appointment under a
measurement or form without being told. Gone: the `measuring` flag, set by hand in four places (opening a form to read
among them), the `keep` parameter, and `DokuPage.Appointment.new` with the `replaceTop(copy(new = false))` around it.
`DokuPagesTest` holds the moves without Compose; a screen test holds that a new appointment stays under a measurement
opened over it, and fails without the covered check.

The pages' moves are built inline in `DokuArea` (a search hit opens two or three pages; measuring replaces the top and
opens; a patient gone drops its pages or goes back). `AppointmentPage` tells its draft to keep a new appointment with a
`measuring` flag it sets by hand in three places, as opening a page over it disposes it, and `DokuPage.Appointment.new`
is written three times and never read. `DokuPages` offers the moves by name, as `BackStack.openLedgerYear` does; whether
a page is covered by one over it comes from the stack. `DokuPagesTest` beside `BackStackTest`.

## 4 · One reminder face

**Status:** done. `reminders/ReminderFace.kt`: `ReminderFace.of(due, today, words)` gives the title, the text, what the
lock screen shows (`OnLockScreen`, or none), the joint routines by name (`Choice`) and whether it can be skipped; the
notification and the alarm render it, `AlarmShown` is the face with its key, and `LockScreenShown`, `AlarmChoice`,
`Resources.reminderText` and `Resources.lockScreenText` are gone. Its words come through `ReminderWords`, a seam with
two adapters: `ResourceWords` (the app's strings) and plain ones in `ReminderFaceTest`, which holds the face on the JVM
without Robolectric. The alarm takes today from `Day`. Not done: the alarm's queue and ringing as a pure `AlarmQueue`,
for the next time the alarm changes.

What a due reminder shows and offers (the time it is due, the version for the lock screen, the joint routines by name,
whether it can be skipped, the choices to put it off) is decided twice, by `ReminderNotifications` and `AlarmActivity`,
each with today from its own clock rather than `Day`. A pure `ReminderFace` both adapters render.

## 5 · The Doku's modules take a store

**Status:** done. `doku/DokuStore.kt`: `withDatabase` and `changing`, all `Patients`, `Appointments`, `Measurements`,
`Assessments`, `TestCatalog` and the text drafts take; `Doku` is its production adapter, `InMemoryDokuStore` (tests) the
other, Room in memory locked by a flag. `PatientsTest`, `MeasurementsTest` and `TestCatalogTest` no longer set up a
vault; "once locked, nothing is read or written" is `locked = true`. `AppointmentsTest` and `AssessmentsTest` keep the
vault, as they hold what a page saves before the Doku locks. `Doku.database`, public for tests and past the mutex, is
gone: `DokuTest` and the device test `DokuDatabaseTest` read through `withDatabase`, and "the database overwrites what
is deleted" moved to `DokuTest`, as it is the vault's. `changes` stays on `Doku`, as only the pages read it.

`Patients`, `Appointments`, `Measurements`, `Assessments` and `TestCatalog` use three things of `Doku` (`withDatabase`,
`changing`, `changes`) but take all of it, so every test of theirs sets up the vault's key and fingerprint. A narrow
`DokuStore` port with two adapters: `Doku`, and an in-memory one for the tests, locked by a flag.

## 6 · The Doku lock in one module

**Status:** done for the idle lock; the dialog wrappers decided against. `Doku.lockWhenIdle()` waits while the Doku is
open and locks it after five minutes without a touch, returning once it is locked, this way or another; `DokuArea` only
runs it while the Doku shows open. `lockIfIdle` is gone and `millisUntilIdle` is internal (a screen test reads it);
`DokuTest` holds the five minutes on virtual time rather than calling a check by hand. **Not done**: `DokuDialog` and
`DokuDropdown` wrappers to apply `touchesDoku` themselves. They would pass through all of `AlertDialog`'s and
`DropdownMenu`'s parameters to save a one-line modifier at 17 sites, a shallow module. Whether the dialogs nested in a
page need `DokuInsel` again (`DateDialog` and `TimeDialog` have it, `PatientDialog` does not) is open: it depends on how
Android hands a dialog's window its keyboard, which only a device shows.

When the Doku locks is spread over seven files; `millisUntilIdle` and `lockIfIdle` exist only for a loop in `DokuArea`
that polls, and `Modifier.touchesDoku` is written by hand 17 times. `Doku` keeps its own idle timer; the pages report
touches; Doku dialogs apply the island and the touches themselves.

## 7 · Tests write through `save`

**Status:** done for the bookings; the rest decided against, as its premise was wrong. `Routines.save` and
`Ledger.saveTemplate` are made of `create` and `update`, which keep the same rules as `require`s: tests through them
cross the same rules as the editors, getting an exception where the editor gets `Saving.Refused`. Moving their 255 test
call sites onto `save` would only move `create` into a test helper. The one write path the editors did not use was the
bookings' `Ledger.create`, `update` and `delete(id)`, beside `save` and `delete(editing)`: gone, with `draftOf` private.
Their tests write through `saveNew`, `change`, `deleteBooking` and `savedDraft` (`ledger/LedgerSeeding.kt`, tests), which
go through `save` as the editor does, so that "a closed month is locked" now holds `Saving.Refused(MonthClosed)` and
`delete` returning false, as the editor meets them.

`Routines` and `Ledger` keep a second way of writing (`create`, `update`, `createTemplate`) that only tests use, about 255
call sites, so those tests never meet the refusals production relies on. Tests seed through `save`; the writers, and the
getters plan 2 §2 kept for tests, go.

## 8 · The shell stops passing things through

**Status:** open.

`EmilysHubApp` passes seven settings callbacks to `SettingsScreen` and builds the Doku's `DokuModules` bag of five
modules. The settings screen takes the settings module, which saves non-cancellably itself; the Doku is handed over
whole.
