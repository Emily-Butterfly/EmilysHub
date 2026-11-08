# 71 – Bögen: Schmerz

**Etappe:** 11 – Dokumentation: Assessmentbögen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Assessmentbögen)  
**GitHub-Issue:** #71  
**Blockiert durch:** [66 – Bögen: Grundgerüst, Anamnesebogen & PHQ-9](66-boegen-grundgeruest.md)

## Was gebaut wird

Die Bögen der Unterkategorie „Schmerz und Psychosoziales“ (erster Teil: Schmerz) von DIGOTOR kommen in die App, mit Auswertung wie im Original.

## Akzeptanzkriterien

### Bögen

- [ ] Central Sensitization Inventory (CSI-GE)
- [ ] Central Sensitization Inventory – Kurzversion (CSI-9)
- [ ] DN4-Fragebogen
- [ ] Leeds Assessment of Neuropathic Symptoms and Signs (LANSS)
- [ ] Graded Chronic Pain Scale (GCPS) (auch unter Kiefer / CMD)
- [ ] Neurophysiology of Pain Questionnaire (auch unter Kiefer / CMD)
- [ ] Migraine Disability Assessment (MIDAS)
- [ ] Örebro Musculoskeletal Pain Screening Questionnaire
- [ ] STarT Back Screening Tool – Deutsch
- [ ] Pain Catastrophizing Scale (PCS)
- [ ] Fragebogen zur schmerzspezifischen Selbstwirksamkeit

### Besonderheiten

- [ ] DN4 und LANSS mischen Fragen und Untersuchungsbefunde
- [ ] STarT Back: Gesamtwert und psychosozialer Teilwert, Einstufung niedrig/mittel/hoch nach beiden
- [ ] GCPS: Graduierung I–IV aus Schmerzintensität und Beeinträchtigungspunkten

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
