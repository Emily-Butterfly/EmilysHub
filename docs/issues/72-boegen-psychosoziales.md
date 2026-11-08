# 72 – Bögen: Psychosoziales

**Etappe:** 11 – Dokumentation: Assessmentbögen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Assessmentbögen)  
**GitHub-Issue:** #72  
**Blockiert durch:** [66 – Bögen: Grundgerüst, Anamnesebogen & PHQ-9](66-boegen-grundgeruest.md)

## Was gebaut wird

Die Bögen der Unterkategorie „Schmerz und Psychosoziales“ (zweiter Teil: Psychosoziales) von DIGOTOR kommen in die App, mit Auswertung wie im Original.

## Akzeptanzkriterien

### Bögen

- [ ] Fear Avoidance Belief Questionnaire (FABQ) (auch unter Kiefer / CMD)
- [ ] Tampa Scale of Kinesiophobia
- [ ] Impact of Event Scale (IES-R)
- [ ] Injury-Psychological Readiness to Return to Sport Scale (I-PRRS)
- [ ] Patient Health Questionnaire 15 (PHQ-15) (auch unter Kiefer / CMD)
- [ ] Patient Health Questionnaire 4 (PHQ-4) (auch unter Kiefer / CMD)
- [ ] Psychologische Screeningfragen
- [ ] Ermittlung der Stressbelastung
- [ ] Stress- und Spannungsfragebogen

### Besonderheiten

- [ ] FABQ: Teilskalen Arbeit und körperliche Aktivität
- [ ] Tampa: umgekehrt gepolte Items
- [ ] PHQ-4: Teilwerte Angst (GAD-2) und Depression (PHQ-2)

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
