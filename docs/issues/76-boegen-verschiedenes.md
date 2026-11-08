# 76 – Bögen: Verschiedenes

**Etappe:** 11 – Dokumentation: Assessmentbögen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Assessmentbögen)  
**GitHub-Issue:** #76  
**Blockiert durch:** [66 – Bögen: Grundgerüst, Anamnesebogen & PHQ-9](66-boegen-grundgeruest.md)

## Was gebaut wird

Die Bögen der Unterkategorie „Verschiedenes“ von DIGOTOR kommen in die App, mit Auswertung wie im Original.

## Akzeptanzkriterien

### Bögen

- [ ] Get Active Questionnaire for Postpartum (GAQ-PP)
- [ ] International Consultation on Incontinence Questionnaire – Urinary Incontinence Short Form (ICIQ-UI SF), deutsche Version
- [ ] Wells-Score Lungenembolie
- [ ] Wells-Score tiefe Venenthrombose (TVT)

### Besonderheiten

- [ ] Wells-Scores: Kriterien zum Ankreuzen mit Punkten (auch Dezimalpunkte), Einstufung der Wahrscheinlichkeit
- [ ] GAQ-PP: Screening mit Ja/Nein, ohne Punktsumme, aber mit Empfehlung je nach Antworten – als Einstufung

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
