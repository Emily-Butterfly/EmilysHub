# 68 – Bögen: Untere Extremität – Knie & Hüfte

**Etappe:** 11 – Dokumentation: Assessmentbögen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Assessmentbögen)  
**GitHub-Issue:** #68  
**Blockiert durch:** [66 – Bögen: Grundgerüst, Anamnesebogen & PHQ-9](66-boegen-grundgeruest.md)

## Was gebaut wird

Die Bögen der Unterkategorie „Untere Extremität“ (erster Teil: Knie, Hüfte, Rückkehr in den Sport) von DIGOTOR kommen in die App, mit Auswertung wie im Original.

## Akzeptanzkriterien

### Bögen

- [ ] Harris Hip Score
- [ ] Hip Osteoarthritis Outcome Score (HOOS)
- [ ] Hüft Outcome Score (HOS)
- [ ] International Cartilage Repair Society (ICRS)
- [ ] Knee Injury and Osteoarthritis Outcome Score (KOOS)
- [ ] KOOS-Kind Knie-Fragebogen (KOOS-Child)
- [ ] Knee Outcome Survey – ADL (KOS-ADL)
- [ ] Kujala Anterior Knee Pain Scale
- [ ] Lysholm Score
- [ ] Tegner Activity Scale (TAS)
- [ ] VISA-P-G Score
- [ ] ACL-RSI-Skala
- [ ] Landing Error Scoring System (LESS)
- [ ] Checkliste ACL-Prävention – nur, wenn sie zum Abhaken am Patienten taugt (dann ohne Ergebnis); ist sie eine reine Info-Liste, fällt sie weg und das wird im Issue vermerkt

### Besonderheiten

- [ ] KOOS, KOOS-Child und HOOS: Teilskalen, jede auf 0–100, keine Gesamtsumme
- [ ] Harris Hip Score mischt Fragen an den Patienten und Befunde (Bewegungsumfang, Deformität)
- [ ] Tegner: eine Stufe 0–10 statt Punkte je Item

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
