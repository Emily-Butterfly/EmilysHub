# 73 – Bögen: Kiefer / CMD

**Etappe:** 11 – Dokumentation: Assessmentbögen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Assessmentbögen)  
**GitHub-Issue:** #73  
**Blockiert durch:** [66 – Bögen: Grundgerüst, Anamnesebogen & PHQ-9](66-boegen-grundgeruest.md)

## Was gebaut wird

Die Bögen der Unterkategorie „Kiefer / CMD“ von DIGOTOR kommen in die App, mit Auswertung wie im Original.

## Akzeptanzkriterien

### Bögen

- [ ] CMD – DC/TMD-Untersuchungsbogen
- [ ] CMD – DC/TMD Symptom-Fragebogen
- [ ] CMD – Schmerzbild
- [ ] Dizziness Handicap Inventory (DHI)
- [ ] Oral Behaviors Checklist (OBC)
- [ ] Generalized Anxiety Disorder Scale (GAD-7)
- [ ] Schon übertragen und hier nur einsortiert (durch #66, #71, #72): PHQ-9, PHQ-15, PHQ-4, FABQ, GCPS, Neurophysiology of Pain Questionnaire

### Besonderheiten

- [ ] **GAD-7**: Der Link bei DIGOTOR zeigt auf den GAQ-PP (Stand 11/2026), nicht auf den GAD-7. Erst nach dem richtigen PDF suchen; gibt es keins, nachfragen
- [ ] **DC/TMD Symptom-Fragebogen**: Seine Datei heißt wie der Untersuchungsbogen mit „-1“ – prüfen, dass es wirklich der Symptom-Fragebogen ist
- [ ] DC/TMD-Untersuchung und Schmerzbild brauchen Regionen im Kiefer- und Kopfbereich (z. B. Kiefergelenk, M. masseter, M. temporalis je links/rechts) – als eigene Liste für den Baustein Körperregionen

### Für jeden Bogen

- [ ] Je Bogen alle Items und Antworten **im Wortlaut** des PDFs aus der Kategorie „Scores“ (https://digotor.info/downloads/), mit Punkten, Auswertung, Regeln für fehlende Antworten, Einstufung und Richtung je Ergebnis; Quelle mit Stand aus dem Dateinamen
- [ ] Jeder Bogen kommt in **alle** Unterkategorien, in denen er bei DIGOTOR steht (der Slice, der ihn überträgt, sortiert ihn überall ein), mit seiner Abkürzung für die Suche
- [ ] Nur die Inhalte, nicht die PDFs oder ihr Layout
- [ ] Lässt sich ein Bogen mit den Bausteinen aus #66 nicht abbilden, bringt dieser Slice den Baustein mit; ist die Auswertung im PDF unklar oder unvollständig, wird nachgefragt statt geraten

### Tests

- [ ] Unit-Tests je Bogen: kleinstes und größtes Ergebnis, ein gemischter Fall von Hand aus dem PDF nachgerechnet, fehlende Antworten nach der Regel des Bogens, Einstufung an den Grenzen

## Entscheidungen

Abgestimmt im Interview vom 08.11.2026.

- Alle Bögen der Kategorie „Scores“, in Slices nach Unterkategorie.
