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

- [x] Achilles Tendon Total Rupture Score (ATRS)
- [x] ALR-RSI-Skala
- [x] Ankle-GO Score
- [x] Cumberland Ankle Instability Tool (CAIT)
- [x] Foot and Ankle Ability Measure (FAAM-G)
- [x] Foot and Ankle Disability Index (FADI)
- [x] Foot and Ankle Outcome Score (FAOS)
- [x] SEFAS – Fragebogen zu Fuß- und Sprunggelenk
- [x] VISA-A Score
- [x] VISA-G Score
- [x] Lower Extremity Functional Scale (LEFS)
- [x] Pelvic Girdle Questionnaire
- [x] Running Readiness Scale

### Besonderheiten

- [x] FAOS und FAAM-G: Teilskalen
- [x] Ankle-GO mischt Fragebogen und Funktionstests (Zahl-Items)

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
  - ATRS, ALR-RSI, Ankle-GO, CAIT (je Seite), SEFAS, VISA-A, VISA-G und Running Readiness Scale: ein Ergebnis erst, wenn alle gezählten Items beantwortet sind (bei VISA-A und VISA-G die Fragen 1–7 und die gewählte von 8a, 8b, 8c).
  - FAAM-G wie der HOS: Prozent der mit Punkten beantworteten Items, „Nicht zutreffend“ senkt die Höchstpunktzahl, ein Ergebnis ab einer beantworteten Frage.
  - FADI: Punkte (0–104 Alltag, 0–32 Sport), alle Items nötig; „nicht anwendbar“ lässt die Summe ohne Ergebnis.
  - Pelvic Girdle Questionnaire: immer von 75, ausgelassene Fragen zählen 0, beliebig viele dürfen fehlen; höher ist schlechter.
  - Ankle-GO: je Test der Bereich zum Auswählen mit seinen Punkten, die „+1“ als Ja/Nein; gesamt 0–25.
- FAOS: dieselbe 50-%-Regel wie der HOOS (gleicher Wortlaut im PDF): jede Teilskala, sobald die Hälfte ihrer Fragen beantwortet ist. Die Steifigkeit (S6, S7) zählt zu den Symptomen, wie im PDF unter „Gesamtscore Subskala Symptome“.
- LEFS: Summe der beantworteten, höchstens vier dürfen fehlen (so im PDF); unter 20 „Grundlegende Hilfsbedürftigkeit“.
- ALR-RSI: das PDF rechnet je Frage 0–10 („Score x 100 / 120“); angekreuzt wird 0–100 in Zehnern, das Ergebnis in Prozent ist dasselbe.
- CAIT: links und rechts je ein eigenes Ergebnis; unter 25 die Einstufung des PDFs.
- Neuer Baustein: ein Prozent immer vom Höchstwert aller Items, Ausgelassenes zählt 0 (Pelvic Girdle Questionnaire).
- Offensichtliche Tippfehler: Ankle-GO FAAM-Sport „> 80 %“ als „< 80 %“; CAIT „auf der Stell hüpfe“; SEFAS „mir geringen“; FAOS „PS1“ als „SP1“ und „slebst“, „Schwieirgkeiten“, „Frezeitaktivitäten“ in den Hinweisen.
- FADI heißt wie im Titel des PDFs und bei DIGOTOR „Foot and Ankle Disability Score“; die Auswertung des PDFs nennt ihn „Index“.
- Ein Bogen ganz ohne Antwort hat kein Ergebnis, auch der Pelvic Girdle Questionnaire (nicht 0 %).
