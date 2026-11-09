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

- [x] European Myelopathy Score (EMS)
- [x] Freezing of Gait Questionnaire (FOG)
- [x] Fugl-Meyer Assessment
- [x] Functional Ambulation Categories (FAC)
- [x] Goal Attainment Scale (GAS)
- [x] Mini-BESTest
- [x] Modified Fatigue Impact Scale (MFIS)
- [x] MOS Schmerz-Effekt-Skala (MOS-PES)
- [x] Rapid Screening for Depression in Multiple Sclerosis

### Besonderheiten

- [x] **GAS**: Die Ziele formulierst du je Patient selbst (Freitext je Ziel mit den Stufen −2 bis +2); Ergebnis als T-Wert nach der Formel im Bogen. Braucht einen Baustein „Ziele“ mit frei benannten Einträgen
- [x] Fugl-Meyer: mehrere Teile (obere/untere Extremität, Sensibilität …) als Teilskalen
- [x] MFIS: Teilskalen körperlich, kognitiv, psychosozial

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
  - **GAS**: Das PDF hat ein einziges Ziel und keine Formel. Ziele frei benannt, beliebig viele, je eine Stufe von −2 („Schlechter“) bis +2 („Ziel deutlich übertroffen“) im Wortlaut des Bogens; T-Wert nach Kiresuk & Sherman, alle Ziele gleich gewichtet (ρ = 0,3): T = 50 + 10 · Σx / √(0,7 · n + 0,3 · n²), höher ist besser. Erst mit mindestens einem Ziel und jedem bewertet; eine leere Zeile zählt nicht. Ein neuer GAS beginnt leer, ohne die Ziele des letzten. Neuer Baustein „Ziele“ (Freitext je Ziel mit Stufe); die Ziele findet die Doku-Suche.
  - **MFIS**: Das PDF hat nur die Aussagen 1–7 und keine Teilskalen, die Auswertung beschreibt den MFIS-5. Alle 21 Aussagen: 1–7 im Wortlaut von DIGOTOR, 8–21 nach der deutschen Fassung im Anhang der Dissertation von M. Hopfgartner (LMU München); deren Fassung fehlt Aussage 19 des Originals („trouble concentrating“), hier „… hatte ich Schwierigkeiten, mich zu konzentrieren.“ Teilskalen körperlich (4, 6, 7, 10, 13, 14, 17, 20, 21; 0–36), kognitiv (1, 2, 3, 5, 11, 12, 15, 16, 18, 19; 0–40), psychosozial (8, 9; 0–8), Gesamt (0–84) und MFIS-5 (1, 9, 10, 17, 19; 0–20) wie in der Auswertung des PDFs; niedriger ist besser.
  - **Fugl-Meyer**: H mit den vier Fragen zum Lagesinn des Originals (Schulter, Ellenbogen, Handgelenk, Daumen IP-Gelenk), mit den Antworten, die das PDF ohne ihre Fragen druckt; H also 0–12 statt 4. A 2) (Synergien) mit 0 nicht, 1 teilweise, 2 ganz wie im Original; das PDF druckt nur 0 und 2.
- Jedes Ergebnis erst, wenn alle seine Items beantwortet sind, wie in #72; keines der PDFs sagt etwas zu fehlenden Antworten.
- Fugl-Meyer: Teilskalen A (0–36), B (0–10), Hand (0–14), Koordination/Geschwindigkeit (0–6), Total A–D (0–66), H Empfindung, J passive Gelenkbewegung (0–24) und Gelenkschmerz (0–24), höher ist besser. A 5) steht immer da; seine erste Antwort nennt „0 Punkte auf Teil IV“.
- Mini-BESTest: Antizipation (1–3), Reaktive Gleichgewichtskontrolle (4–6), Sensorische Orientierung (7–9) und Dynamische Testung Gang (10–14), Total 0–28, höher ist besser. 3 und 6 je Seite; es zählt die schlechtere (neuer Baustein: von einem Paar zählt das schlechtere). Die Zeiten als Zahlen ohne Punkte.
- EMS: Summe 5–18, Grad 3 (5–8), Grad 2 (9–12), Grad 1 (13–16), Normal (17–18), höher ist besser. FOG: 0–24, MOS-PES: 6–30, niedriger ist besser, ohne Einstufung. FAC: die Nummer 0–5 mit dem Namen der Kategorie.
- Rapid Screening: die neun Symptome erscheinen, sobald eine der beiden Fragen mit Ja beantwortet ist (neuer Baustein: Bedingung mit „oder“), als Ankreuzliste der Symptome 1–2 und 3–9. Ergebnisse: Screening 0–2 (negativ/positiv), Zahl der Symptome, Symptome 1 und 2, und „Major Depression nach DSM-IV“: Kriterien erfüllt bei mindestens fünf Symptomen, eines davon 1 oder 2.
- Alle neun nur unter „Neurologie“, wie bei DIGOTOR. Abkürzungen EMS, FOG, FMA, FAC, GAS, MFIS, MOS-PES; Mini-BESTest und Rapid Screening ohne.
- Offensichtliche Tippfehler: EMS „Vitzhum“ als „Vitzthum“; Mini-BESTest 8 „(1) Moderat: < 3 sec.“ als „< 30 sec.“; MFIS „undun-koordiniert“; MOS-PES „Fall Sie Hilfe“ als „Falls Sie Hilfe“; Fugl-Meyer „Ellenogen“, „druchführen“ und das Komma nach „kann nicht freiwillig durchführen“.
- GAS: „Datum“ und „Kontrolle“ des Bogens sind das Datum des Termins. Mini-BESTest: ein Freitext „Hilfsmittel“, da der Bogen es notieren lässt.
- Rapid Screening: Sobald eine Frage mit Ja beantwortet ist, zählt eine leere Ankreuzliste als keine Symptome (wie bei allen Ankreuzlisten mit Punkten); das Ergebnis steht dann auf „Kriterien nicht erfüllt“, bis Symptome angekreuzt sind.
