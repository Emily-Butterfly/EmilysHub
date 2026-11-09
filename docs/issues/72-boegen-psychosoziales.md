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

- [x] Fear Avoidance Belief Questionnaire (FABQ) (auch unter Kiefer / CMD)
- [x] Tampa Scale of Kinesiophobia
- [x] Impact of Event Scale (IES-R)
- [x] Injury-Psychological Readiness to Return to Sport Scale (I-PRRS)
- [x] Patient Health Questionnaire 15 (PHQ-15) (auch unter Kiefer / CMD)
- [x] Patient Health Questionnaire 4 (PHQ-4) (auch unter Kiefer / CMD)
- [x] Psychologische Screeningfragen
- [x] Ermittlung der Stressbelastung
- [x] Stress- und Spannungsfragebogen

### Besonderheiten

- [x] FABQ: Teilskalen Arbeit und körperliche Aktivität
- [x] Tampa: umgekehrt gepolte Items
- [x] PHQ-4: Teilwerte Angst (GAD-2) und Depression (PHQ-2)

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
  - Keiner der Bögen sagt etwas zu fehlenden Antworten: jedes Ergebnis erst, wenn alle seine Items beantwortet sind; nur PHQ-15 d) („nur Frauen“) darf leer bleiben, dann zählen die übrigen 14.
  - Psychologische Screeningfragen: die Paare (Fragen 2 und 3, 5 und 6, 7 und 8) werden addiert (0–20), positiv ab dem genannten Wert; Fragen 1 und 4 je für sich.
  - I-PRRS: jede Zuversicht 0 bis 100 in Zehnerschritten wie im Original von Glazer; Gesamtwert (Summe/10) unter 50 „psychisch noch nicht bereit“, die Ankerpunkte 0/20/40/60 im Hinweis.
  - Spannungsfragebogen: Frage 34 je Bereich (Betrieb/Amt, Zu Hause) in Worten und als Wahl a) 0, b) 2, c) 4; Frage 35 in Worten und als Anzahl der Probleme je Belastungsstärke (je 1, 2, 3 Punkte, bis 10 je Stärke).
- Tampa: die validierte deutsche Kurzfassung (TSK-GV, 11 Items) hat keine umgekehrt gepolten Items; A bis D zählen 1 bis 4, Summe 11–44.
- FABQ: körperliche Aktivität (Items 2–5, > 15) und Arbeit (Items 6, 7, 9–12, 15, > 34); 1, 8, 13, 14 und 16 zählen nicht und sind für kein Ergebnis nötig.
- IES-R: Intrusion, Vermeidung, Hyperarousal als Summen und X nach Maercker & Schützwohl als viertes Ergebnis; X > 0 „Verdachtsdiagnose auf PTB“. X ist in Hundertsteln genau, da alle Gewichte es sind. Der (traumatische) Vorfall als Textfeld.
- PHQ-4: Summenwert (Keiner/Gering/Moderat/Stark), Depression (Items 1, 2) und Angst (Items 3, 4) je ab 3 positiv.
- PHQ-15: Bänder nach der Grafik des PDFs: 0–4, 5–9, 10–14, 15–30.
- Psychologische Screeningfragen: positiv/negativ mit der Wahrscheinlichkeit einer Problematik aus dem PDF.
- Ermittlung der Stressbelastung: je Stressfaktor eine Summe (je bis 120), keine Einstufung, wie im Original (Netzdiagramm).
- Stress- und Spannungsfragebogen: ein Bogen mit zwei Ergebnissen; „Folgende weitere Stresssituationen“ mit Textfeld.
- Namen wie bei DIGOTOR; Abkürzungen: FABQ, TSK-GV, IES-R, I-PRRS, PHQ-15, PHQ-4; die übrigen haben keine. FABQ, PHQ-15 und PHQ-4 auch unter „Kiefer / CMD“.
- Neue Bausteine: Items, die leer bleiben dürfen (PHQ-15 d); ein Ergebnis aus früheren mit Gewichten und Konstante (IES-R X); eine Anzahl, die je Stück mehrere Punkte zählt (Frage 35).
- Offensichtliche Tippfehler: Tampa „Versio“; I-PRRS „valdierte“; FABQ „Georg 2010“ als „George 2010“; Stressbelastung Partnerschaft 7 „Partnerin/mein Partner“ als „Meine Partnerin/mein Partner“, 29 „mein Partnerin“. Der Blindtext am Ende des FABQ-PDFs bleibt weg.
