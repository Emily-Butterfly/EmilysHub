# 69 – Bögen: Untere Extremität – Fuß, Sprunggelenk & Übrige

**Etappe:** 11 – Dokumentation: Assessmentbögen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Assessmentbögen)  
**GitHub-Issue:** #69  
**Blockiert durch:** [66 – Bögen: Grundgerüst, Anamnesebogen & PHQ-9](66-boegen-grundgeruest.md)

## Was gebaut wird

Die Bögen der Unterkategorie „Untere Extremität“ (zweiter Teil: Fuß, Sprunggelenk, Achillessehne und übrige) von DIGOTOR kommen in die App, mit Auswertung wie im Original.

## Akzeptanzkriterien

### Bögen

- [ ] Achilles Tendon Total Rupture Score (ATRS)
- [ ] ALR-RSI-Skala
- [ ] Ankle-GO Score
- [ ] Cumberland Ankle Instability Tool (CAIT)
- [ ] Foot and Ankle Ability Measure (FAAM-G)
- [ ] Foot and Ankle Disability Index (FADI)
- [ ] Foot and Ankle Outcome Score (FAOS)
- [ ] SEFAS – Fragebogen zu Fuß- und Sprunggelenk
- [ ] VISA-A Score
- [ ] VISA-G Score
- [ ] Lower Extremity Functional Scale (LEFS)
- [ ] Pelvic Girdle Questionnaire
- [ ] Running Readiness Scale

### Besonderheiten

- [ ] FAOS und FAAM-G: Teilskalen
- [ ] Ankle-GO mischt Fragebogen und Funktionstests (Zahl-Items)

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
