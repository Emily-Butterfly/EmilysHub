# Deepening plan 3

From the third architecture review of 9 November 2026, made after Etappe 11 (the assessment forms, #66–#76) was
done. Its hot spot is the Doku's forms: `Assessment.kt` and `Scoring.kt` changed with nearly every slice. As no more
forms are planned, the candidates are weighed by what changes whenever a form is filled in, saved or corrected, not by
new forms. Vocabulary: module, interface, implementation, depth, seam, adapter, leverage, locality. Domain terms are
those of [SPEC.md](../../SPEC.md) §14.

Order of work: **1 → 2 → 3 → 4 → 5**, each step green on `./gradlew lint testDebugUnitTest`, then pushed. Behaviour does
not change unless a step says so.

The net under all of them comes first: `AllFormsTest` runs over every built-in form, so that each score has a range,
answers of the fewest and the most points stay within it, and a plain sum of items always shown reaches both ends.

| # | Candidate | Strength | Dependency category |
|---|---|---|---|
| 1 | Deepen `AssessmentSheet`: one module for a filled form | Strong | in-process |
| 2 | A Doku draft session: save on close, written once | Worth exploring | local-substitutable (in-memory `DokuDatabase`) |
| 3 | `Score` as a sealed type; each rule owns its value and its range | Worth exploring | in-process |
| 4 | Item kinds own their answer, fit, points and range | Worth exploring | in-process |
| 5 | Exact score values: one band rule, hundredths only at the edge | Speculative | in-process |

Looked at and left alone: `Fraction` (private, deep), `Condition`, `AnswerCodec` (a storage adapter, round-trip tested),
`Options.kt` (the forms' authoring helpers), `BuiltInAssessments`, `DokuPages`, `DokuNumbers`/`LineChart`/`Change`
(already shared by measurements and assessments), and the form files with their tests (big data, good locality).
Tags and checklists stay per kind (ADR 0001).

---

## 1 · Deepen AssessmentSheet

**Status:** done. `AssessmentSheet` is a pure value of the filled form and its answers: `sections` (each with the
items shown and their answers), `answering(item, answer)`, `orphans`, `results` (once), `stored` and `changed`;
`AssessmentSheetTest` holds it without Compose. `AssessmentPage` renders it; `Assessments.write` keeps what
`AssessmentSheet.kept` says. "Says nothing" is `Answer.isNothing`, which `AnswerCodec` uses too, so the page no longer
asks the codec. `FilledAssessment.results` is reckoned once. **Behaviour changed, slightly**: words of spaces alone in a
text field are no answer while typing, as they were never stored.

The rule "answers to items not shown neither count nor are kept" lives in two call sites: `AssessmentPage` combines
`Scoring.visible`, `fits`, `orphans` and `results` itself, and `Assessments.write` derives again what is kept. The page
decides whether an answer is empty by asking the storage codec (`AnswerCodec.encode(it) != null`), and
`FilledAssessment.results` reckons anew on every access.

`AssessmentSheet` already exists, as a thin pair of the filled form and whether it can change. It becomes a pure value
of a form and its answers that answers what the page and `write` ask: the items shown with their answers, an answer
given (empty is none), the answers its form no longer has, its results (once), and what is stored. The page renders it;
`write` stores what it says. Tested at its interface, without Compose.

## 2 · A Doku draft session

**Status:** done. `DokuDraft` (doku/DokuDraft.kt) is what an open page has not saved yet, as its domain module judges
it: a `DokuSaver` for the lock, and `close(turning)` for the page closing. `Assessments.draft(sheet, new, confirmed)`,
`Appointments.draft(id, original, text, keep)`, `Appointments.templateDraft` and `Patients.textDraft` build them; text
drafts are one `TextDraft`. One Composable, `KeepDraft(doku, key) { draft }`, replaces `SaveAnswers`,
`DropIfUntouched` and `SaveOnClose`; it asks for the draft only as the Doku locks or the page closes, so that it sees
"Fertig" or "Messen" pressed in the same moment. Gone from the interfaces: `saveLater`, `pending`, `dropIfEmptyLater`,
`pendingDrop`, `setTextLater`, `pendingText`, `dropIfUntouchedLater`, `keep`, `setTemplateLater`, `pendingTemplate`.
Whether the page may change is the draft's to say (`DokuDraft.None` for a closed patient's form), and whether a new
appointment is new is `Appointments`' own knowledge, not the page's.

Saving on close, or dropping a form left empty, is written three times as a Composable (`SaveAnswers`,
`DropIfUntouched`, `SaveOnClose`), two of them guarding against a change of configuration and one not; the repositories
offer five entry points each (`saveLater`, `pending`, `dropIfEmptyLater`, `pendingDrop`, …). Plan 2's editing sessions
(§2) did this for routines, the Haushaltsbuch and to-dos, not for the Doku. One session per open page owns the pending
write, the rule to drop and the guard.

## 3 · Score as a sealed type

One data class stands for three shapes (a score of items, a weighted sum of scores, a grade by scores), held together
by `require`s; `Scoring.value` and `Scoring.range` dispatch on the rule in parallel, with `error("reckoned …")` branches.
Four review fixes re-collected "the points of the answered items" in one special path or another. A sealed `Score` whose
rules carry their own data and reckon value and range together, over one collector of answered points.

## 4 · Item kinds own their behaviour

A kind of item is spread over about twelve `when` sites in five modules; one that makes points but is missing in
`points` or `range` compiles, shows "–" and throws on the history page. Each kind answers whether an answer fits it, its
points and its range itself; only the Compose rendering stays a `when`, as an adapter.

## 5 · Exact score values

Bands compare the rounded value, inclusive; a grade's bounds compare the exact value, "below" exclusive. Form authors
reason about rounding by hand (`Band(5099)`, `Band(199)`), and the Wells-Scores' tenths ride on `divisor = 10`. Bands
compare the exact value as bounds do, option points may be decimal, and hundredths appear only at the storage and
display adapters.
