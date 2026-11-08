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

- [x] Harris Hip Score
- [x] Hip Osteoarthritis Outcome Score (HOOS)
- [x] Hüft Outcome Score (HOS)
- [x] International Cartilage Repair Society (ICRS)
- [x] Knee Injury and Osteoarthritis Outcome Score (KOOS)
- [x] KOOS-Kind Knie-Fragebogen (KOOS-Child)
- [x] Knee Outcome Survey – ADL (KOS-ADL)
- [x] Kujala Anterior Knee Pain Scale
- [x] Lysholm Score
- [x] Tegner Activity Scale (TAS)
- [x] VISA-P-G Score
- [x] ACL-RSI-Skala
- [x] Landing Error Scoring System (LESS)
- [x] ~~Checkliste ACL-Prävention~~ – fällt weg: sie bewertet das Trainingsprogramm einer Mannschaft (Übungen, Sprungübungen, Alter der Athleten, Zeitpunkt, Ausbildung des Trainers; 0–11 Punkte), nicht den Patienten (entschieden am 08.11.2026)

### Besonderheiten

- [x] KOOS, KOOS-Child und HOOS: Teilskalen, jede auf 0–100, keine Gesamtsumme
- [x] Harris Hip Score mischt Fragen an den Patienten und Befunde (Bewegungsumfang, Deformität)
- [x] Tegner: eine Stufe 0–10 statt Punkte je Item

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
  - HOOS: jede Teilskala, sobald mindestens die Hälfte ihrer Fragen beantwortet ist (aufgerundet), aus den beantworteten.
  - KOOS und KOOS-Kind: je Teilskala dürfen höchstens zwei Fragen fehlen.
  - HOS: „Nicht zutreffend“ zählt wie nicht beantwortet und senkt die Höchstpunktzahl; ein Ergebnis gibt es, sobald eine Frage mit Punkten beantwortet ist. Die zwei mit * markierten Fragen und Frage 3 zählen nicht.
  - Harris Hip Score und KOS-ADL: das Gesamtergebnis braucht alle Items. Der Harris Hip Score zeigt zusätzlich jeden Teil (Schmerzen, ADL, Gehfähigkeit, Deformitäten, Mobilität) als eigenes Ergebnis, sodass die Teile des Patienten ohne die Befunde lesbar sind.
  - Checkliste ACL-Prävention: fällt weg (s. o.).
- LESS: „Exzellent: 10, Gut: 8-10, Moderat: <8, Defizitär: <5“ überlappt; gelesen als 10 Exzellent, 8–9 Gut, 5–7 Moderat, 0–4 Defizitär.
- ACL-RSI: „unter 51 %“ heißt bis 50,99 (der Mittelwert von zwölf Zehnern liegt dazwischen).
- VISA-P-G: Frage 8 wählt, welche von 8a, 8b und 8c erscheint; gezählt werden die Fragen 1–7 und die gewählte.
- Neue Bausteine: eine Antwort „Nicht zutreffend“, die wie keine Antwort zählt (HOS), und eine Skala in Schritten, z. B. 0, 10 … 100 (ACL-RSI).
- Offensichtliche Tippfehler bei Antworten (KOOS: „einge“, einmal „Sehr große“; KOOS-Kind: „??“) sind wie die übrigen Antworten derselben Frageart geschrieben; die Fragen selbst stehen im Wortlaut.
