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

- [x] Central Sensitization Inventory (CSI-GE)
- [x] Central Sensitization Inventory – Kurzversion (CSI-9)
- [x] DN4-Fragebogen
- [x] Leeds Assessment of Neuropathic Symptoms and Signs (LANSS)
- [x] Graded Chronic Pain Scale (GCPS) (auch unter Kiefer / CMD)
- [x] Neurophysiology of Pain Questionnaire (auch unter Kiefer / CMD)
- [x] Migraine Disability Assessment (MIDAS)
- [x] Örebro Musculoskeletal Pain Screening Questionnaire
- [x] STarT Back Screening Tool – Deutsch
- [x] Pain Catastrophizing Scale (PCS)
- [x] Fragebogen zur schmerzspezifischen Selbstwirksamkeit

### Besonderheiten

- [x] DN4 und LANSS mischen Fragen und Untersuchungsbefunde
- [x] STarT Back: Gesamtwert und psychosozialer Teilwert, Einstufung niedrig/mittel/hoch nach beiden
- [x] GCPS: Graduierung I–IV aus Schmerzintensität und Beeinträchtigungspunkten

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
- Nachgefragt am 09.11.2026, da die PDFs es offenlassen:
  - Keiner der Bögen sagt etwas zu fehlenden Antworten: jedes Ergebnis erst, wenn alle seine Items beantwortet sind (bei der PCS je Teilskala ihre Items).
  - GCPS: Schmerzintensität und subjektive Beeinträchtigung (Mittelwert × 10) fallen genau, ohne vorher zu runden, in die Stufen: unter 30 sind 0 BP, unter 50 1 BP, unter 70 2 BP, darüber 3 BP; unter 50 Schmerzintensität ist Grad I. Mit ganzen Antworten kommen dort ohnehin keine Brüche vor (26,67 / 30, 46,67 / 50, 66,67 / 70).
  - DN4: „aufgelöst“ in Frage 4 ist ein Tippfehler für „ausgelöst“.
- GCPS: Grad I bei weniger als 3 BP und unter 50 Schmerzintensität, Grad II bei weniger als 3 BP ab 50, Grad III bei 3–4 BP, Grad IV bei 5–6 BP; die Zwischenergebnisse (Tage, BP (a), subjektive Beeinträchtigung, BP (b), Gesamtbeeinträchtigung, Schmerzintensität) stehen als eigene Ergebnisse da.
- STarT Back: Gesamtpunktzahl bis 3 geringes Risiko, sonst Teilsumme der Fragen 5–9 bis 3 mittleres Risiko, sonst hohe Chronifizierungsgefahr; „Chronifizierungsrisiko“ als drittes Ergebnis (1 bis 3).
- Tage als Zahl: MIDAS-Fragen 1–5 je 0 bis 92 (drei Monate), GCPS-Frage 1 0 bis 184 (sechs Monate); mehr oder Nachkommastellen nimmt das Feld nicht an. MIDAS A und B zählen nicht mit.
- GCPS: „Hauptschmerzen:“ als Textfeld vor Frage 1, da die Fragen „betreffend Ihrer Hauptschmerzen“ gestellt sind; die übrigen Kopfzeilen (Name, Datum …) nicht.
- CSI-GE Teil B ohne Punkte, das Jahr der Diagnosestellung erst nach einem Ja.
- Örebro: Frage 1 zählt 1 bis 10 nach der Auswertung („über 1 Jahr“ = 10, im Bogen ohne Zahl); Fragen 3, 4 und 8 als 10 minus die Zahl.
- Namen wie bei DIGOTOR; Abkürzungen aus den PDFs: CSI-GE, CSI-9, DN4, LANSS, GCPS, NPQ-D, MIDAS, ÖMPSQ, PCS, FESS; der STarT Back hat keine, die Suche findet ihn über den Namen.
- GCPS und Neurophysiology of Pain Questionnaire stehen auch unter „Kiefer / CMD“, die übrigen nur unter „Schmerz und Psychosoziales“.
- Neue Bausteine: eine Zahl mit Höchstwert, die als Punkte zählt (Tage); ein Ergebnis als Grad aus Grenzen auf frühere Ergebnisse, genau verglichen (GCPS, STarT Back).
- Offensichtliche Tippfehler: DN4 „Weißt“; LANSS „im betroffen Gebiet“, „auf die nicht-betroffenen Stelle“, „Es wir empfohlen“; CSI-9 „Schweirigkeiten“, „Nigshigima“; PCS „Ausssagen“.
