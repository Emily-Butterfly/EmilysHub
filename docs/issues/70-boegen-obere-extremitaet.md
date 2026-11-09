# 70 – Bögen: Obere Extremität

**Etappe:** 11 – Dokumentation: Assessmentbögen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Assessmentbögen)  
**GitHub-Issue:** #70  
**Blockiert durch:** [66 – Bögen: Grundgerüst, Anamnesebogen & PHQ-9](66-boegen-grundgeruest.md)

## Was gebaut wird

Die Bögen der Unterkategorie „Obere Extremität“ von DIGOTOR kommen in die App, mit Auswertung wie im Original.

## Akzeptanzkriterien

### Bögen

- [x] Instability Severity Index Score (ISIS)
- [x] Karpaltunnel-Scoring
- [x] Nonoperative Instability Severity Score (NISIS)
- [x] Patient-Rated Tennis Elbow Evaluation (PRTEE)
- [x] Patient-Rated Wrist Evaluation (PRWE-G)
- [x] Shoulder Pain and Disability Index (SPADI)
- [x] Simple Shoulder Test (SST)
- [x] The DASH Outcome Measure
- [x] Walch-Duplay
- [x] Western Ontario Shoulder Instability Index (WOSI)

### Besonderheiten

- [x] DASH: Ergebnis 0–100 nur bei höchstens 3 fehlenden Antworten; optionale Module (Sport/Musik, Arbeit) als eigene Ergebnisse
- [x] SPADI und PRWE: Teilskalen Schmerz und Funktion plus Gesamtwert

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
  - ISIS, NISIS, Karpaltunnel-Scoring und Walch-Duplay: ein Ergebnis erst, wenn alle Items beantwortet sind; der SPADI verlangt es selbst.
  - SST: Ja-Antworten immer von 12, Unbeantwortetes zählt wie Nein, ein Ergebnis ab einer Antwort.
  - Karpaltunnel-Scoring: „NEIN (1)“ bei der Schwangerschaftsfrage ist ein Tippfehler, NEIN zählt 0.
  - ISIS: beide Grenzen des PDFs, 4–5 „höheres Rezidivrisiko (> 3)“, 6–10 „hoher Score (6/10 nach Balg)“.
  - DASH: das DIGOTOR-PDF verlinkt nur die Übersetzungen des Institute for Work & Health; Wortlaut und Auswertung aus dessen deutscher Fassung (DASH_German_2012, Scoring_DASH_German).
  - PRTEE: eine fehlende Frage zählt den Mittelwert ihrer Kategorie (Schmerz, Ausgewählte Tätigkeiten, Alltagsaktivitäten), solange je Kategorie mindestens die Hälfte beantwortet ist.
  - PRWE-G: „trifft nicht zu“ wird einfach nicht mitgezählt (zählt 0 in der Summe), beantwortet aber die Frage; alle Fragen nötig.
  - WOSI: das PDF hat keine Auswertung; Gesamtsumme 0–210 und je Abschnitt A–D die Summe, niedriger besser, alle Fragen nötig.
  - SPADI: Schmerz, Körperliche Aktivität und Gesamt je in Prozent ihres Höchstwerts (50, 80, 130).
- DASH: der Wert der Funktionsfähigkeit ab 27 von 30 Antworten (höchstens 3 fehlen, so im Bogen; das Auswertungsblatt sagt „3 oder mehr“ nicht); die Module Sport/Musik und Arbeit ohne fehlende Antwort.
- Karpaltunnel-Scoring und Walch-Duplay haben keine Abkürzung; die Suche findet sie über den Namen.
- Neue Bausteine: eine Summe, deren fehlende Items den Mittelwert ihres Abschnitts zählen (PRTEE); ein Teiler je Ergebnis („geteilt durch 2“); ein Ergebnis als Summe anderer Ergebnisse (Gesamt = Schmerz + Funktion), genau gerechnet und erst am Ende gerundet.
- Offensichtliche Tippfehler: WOSI „Klein „Klicken““ als „Kein“; PRTEE „durschnittlichen“, „enstprechende“; Walch-Duplay „Armnei-gung“ als „Armneigung“.
