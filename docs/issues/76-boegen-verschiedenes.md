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

- [x] Get Active Questionnaire for Postpartum (GAQ-PP)
- [x] International Consultation on Incontinence Questionnaire – Urinary Incontinence Short Form (ICIQ-UI SF), deutsche Version
- [x] Wells-Score Lungenembolie
- [x] Wells-Score tiefe Venenthrombose (TVT)

### Besonderheiten

- [x] Wells-Scores: Kriterien zum Ankreuzen mit Punkten (auch Dezimalpunkte), Einstufung der Wahrscheinlichkeit
- [x] GAQ-PP: Screening mit Ja/Nein, ohne Punktsumme, aber mit Empfehlung je nach Antworten – als Einstufung

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
- „Get Active Questionnaire for Postpartum“ steht bei DIGOTOR auch unter „Kiefer / CMD“, dort aber als falscher Link des GAD-7 (siehe #73); der GAQ-PP kommt nur unter „Verschiedenes“.
- Nachgefragt am 09.11.2026, da die PDFs es offenlassen:
  - **ICIQ-UI SF**: Das PDF endet bei „Starke Inkontinenz: 11“. Die Einstufung nach Klovning et al. 2009: Keine (0), Leichte (1–5), Mäßige (6–12), Starke (13–18), Sehr starke Inkontinenz (19–21). Frage 4 hat wie im Original-ICIQ acht Antworten: die eine Zeile des PDFs mit „körperlicher Betätigung/sportlicher Aktivität“ und „nach dem Wasserlassen und Wiederankleiden“ wird zu zweien. Frage 4 zählt nicht.
  - **GAQ-PP**: Jedes „Ja“ der Fragen 1a–1p, 2 und 3 zählt; das Ergebnis „Empfehlung“ ist die Zahl der „Ja“ (× Ja) mit der Einstufung „Körperliche Aktivität wird empfohlen.“ (kein Ja) bzw. „Vor Beginn oder Fortsetzung körperlicher Aktivität mit Arzt/Ärztin oder Hebamme sprechen.“ Das dritte Kästchen („Ich habe bereits … gesprochen …“) erscheint als Ja/Nein, sobald eine Frage mit „Ja“ beantwortet ist, und ändert die Empfehlung nicht.
- Jedes Ergebnis erst, wenn alle seine Items beantwortet sind, wie in #72; keiner der Bögen sagt etwas zu fehlenden Antworten. Die Wells-Scores zählen, was angekreuzt ist; nichts angekreuzt ist 0.
- Wells-Scores: die Kriterien als Ankreuzliste, die Punkte hinter jedem Kriterium („· 1,5 Punkte“), gezählt in Zehnteln. Lungenembolie 0–10,5: geringe (< 2), mäßige (≥ 2 und ≤ 6), hohe Wahrscheinlichkeit (> 6). TVT −2 bis 9: TVT unwahrscheinlich (≤ 0), Mittleres Risiko (1–2), TVT wahrscheinlich (≥ 3); der „Wichtige Hinweis“ zu den D-Dimeren steht im Bogen. Alle niedriger ist besser.
- GAQ-PP: Entbindungsdatum als Text und die Wochen nach der Entbindung als Zahl; „Bitte angeben“ und „Bitte erläutern Sie“ erst nach „Ja“; die Tabelle der Aktivität (Häufigkeit, Intensität, Dauer je Zeile) und die Empfehlungen stehen im Bogen, zählen aber nicht. Unterschrift, Name und Datum am Ende entfallen wie die Angaben zur Person.
- ICIQ: Summe der Fragen 1–3, 0–21, niedriger ist besser; die minimale klinische Veränderung nach Nyström (2,52 Punkte) steht im Hinweis.
- Alle vier nur unter „Verschiedenes“; Abkürzungen GAQ-PP und ICIQ-UI SF, die Wells-Scores finden sich über „Wells“ im Namen.
- Offensichtliche Tippfehler: „Charkateristik“ als „Charakteristik“, „Immobilisation ≥ 3 Tage)“ als „Immobilisation (≥ 3 Tage)“, „Venentrombose“ als „Venenthrombose“, „Niessen“ als „Niesen“.
