# 67 – Bögen: Wirbelsäule

**Etappe:** 11 – Dokumentation: Assessmentbögen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Assessmentbögen)  
**GitHub-Issue:** #67  
**Blockiert durch:** [66 – Bögen: Grundgerüst, Anamnesebogen & PHQ-9](66-boegen-grundgeruest.md)

## Was gebaut wird

Die Bögen der Unterkategorie „Wirbelsäule“ von DIGOTOR kommen in die App, mit Auswertung wie im Original.

## Akzeptanzkriterien

### Bögen

- [x] Cervical Spine Instability-Skala (CSIQ)
- [x] Headache Disability Index (HDI), deutsch
- [x] Lumbar Spine Instability Questionnaire (LSIQ)
- [x] Micheli Functional Scale (auch unter Schmerz und Psychosoziales)
- [x] Neck Disability Index (NDI)
- [x] Oswestry Disability Index (ODI), deutsche Version nach Mannion et al. 2006
- [x] Roland and Morris Disability Questionnaire (RMDQ)
- [x] Vestibular-Ocular-Motor Screening (VOMS)

### Besonderheiten

- [x] ODI und NDI: Ergebnis in Prozent; ein unbeantworteter Abschnitt verringert die Höchstpunktzahl
- [x] VOMS ist eine Untersuchung mit Symptomwerten je Prüfung (und ggf. Abstand in cm) – mit Zahl- und Skala-Items abbilden

### Für jeden Bogen

- [x] Je Bogen alle Items und Antworten **im Wortlaut** des PDFs aus der Kategorie „Scores“ (https://digotor.info/downloads/), mit Punkten, Auswertung, Regeln für fehlende Antworten, Einstufung und Richtung je Ergebnis; Quelle mit Stand aus dem Dateinamen
- [x] Jeder Bogen kommt in **alle** Unterkategorien, in denen er bei DIGOTOR steht (der Slice, der ihn überträgt, sortiert ihn überall ein), mit seiner Abkürzung für die Suche
- [x] Nur die Inhalte, nicht die PDFs oder ihr Layout
- [x] Lässt sich ein Bogen mit den Bausteinen aus #66 nicht abbilden, bringt dieser Slice den Baustein mit; ist die Auswertung im PDF unklar oder unvollständig, wird nachgefragt statt geraten

### Tests

- [x] Unit-Tests je Bogen: kleinstes und größtes Ergebnis, ein gemischter Fall von Hand aus dem PDF nachgerechnet, fehlende Antworten nach der Regel des Bogens, Einstufung an den Grenzen

## Entscheidungen

Abgestimmt im Interview vom 08.11.2026.

- Alle Bögen der Kategorie „Scores“, in Slices nach Unterkategorie.
- Nachgefragt am 08.11.2026, da die PDFs es offenlassen:
  - ODI und NDI: höchstens ein Abschnitt darf fehlen; fehlen zwei, „Ergebnis: –“.
  - Micheli Teil A: sechs Antworten von 0 bis 5, mit „Ich habe Schmerz, aber er hat keinen Einfluss.“ als 1; Teil C in ganzen Zentimetern 0–10.
  - HDI: Gesamtpunktzahl und die Teilskalen Emotional (E) und Funktional (F); 70 Punkte zählen als „Schwere Beeinträchtigung“.
- RMDQ: angekreuzte Sätze zählen je einen Punkt, nichts angekreuzt ist 0; dafür eine Ankreuzliste mit Punkten. Ein neuer RMDQ ohne Kreuz bleibt nur mit „Fertig“ als 0 stehen; mit Zurück oder beim Sperren geht er wieder (nachgefragt am 08.11.2026).
