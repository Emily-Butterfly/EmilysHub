# 74 – Bögen: Neurologie

**Etappe:** 11 – Dokumentation: Assessmentbögen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Assessmentbögen)  
**GitHub-Issue:** #74  
**Blockiert durch:** [66 – Bögen: Grundgerüst, Anamnesebogen & PHQ-9](66-boegen-grundgeruest.md)

## Was gebaut wird

Die Bögen der Unterkategorie „Neurologie“ von DIGOTOR kommen in die App, mit Auswertung wie im Original.

## Akzeptanzkriterien

### Bögen

- [ ] European Myelopathy Score (EMS)
- [ ] Freezing of Gait Questionnaire (FOG)
- [ ] Fugl-Meyer Assessment
- [ ] Functional Ambulation Categories (FAC)
- [ ] Goal Attainment Scale (GAS)
- [ ] Mini-BESTest
- [ ] Modified Fatigue Impact Scale (MFIS)
- [ ] MOS Schmerz-Effekt-Skala (MOS-PES)
- [ ] Rapid Screening for Depression in Multiple Sclerosis

### Besonderheiten

- [ ] **GAS**: Die Ziele formulierst du je Patient selbst (Freitext je Ziel mit den Stufen −2 bis +2); Ergebnis als T-Wert nach der Formel im Bogen. Braucht einen Baustein „Ziele“ mit frei benannten Einträgen
- [ ] Fugl-Meyer: mehrere Teile (obere/untere Extremität, Sensibilität …) als Teilskalen
- [ ] MFIS: Teilskalen körperlich, kognitiv, psychosozial

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
