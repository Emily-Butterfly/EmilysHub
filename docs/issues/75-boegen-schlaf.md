# 75 – Bögen: Schlafverhalten

**Etappe:** 11 – Dokumentation: Assessmentbögen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Assessmentbögen)  
**GitHub-Issue:** #75  
**Blockiert durch:** [66 – Bögen: Grundgerüst, Anamnesebogen & PHQ-9](66-boegen-grundgeruest.md)

## Was gebaut wird

Die Bögen der Unterkategorie „Schlafverhalten“ von DIGOTOR kommen in die App, mit Auswertung wie im Original.

## Akzeptanzkriterien

### Bögen

- [x] Athlete Sleep Behavior Questionnaire (ASBQ)
- [x] Athlete Sleep Screening Questionnaire (ASSQ)
- [x] Insomnia Severity Index (ISI)
- [x] Pittsburgher Schlafqualitäts-Index (PSQI)
- [x] Regensburger Insomnie Skala (RIS)

### Besonderheiten

- [x] PSQI: Uhrzeiten und Minuten als Eingabe, sieben Komponenten zu je 0–3 und Gesamtwert; braucht einen Baustein Uhrzeit

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
- „Schlafhygiene in der Physiotherapie“ ist ein Fachtext und kommt nicht in die App.
- Nachgefragt am 09.11.2026, da die PDFs es offenlassen:
  - **PSQI**: Das PDF gibt nur Eingabehinweise, keine Regeln für die Komponenten. Die sieben Komponenten nach Buysse et al. 1989, je 0–3: Subjektive Schlafqualität (Frage 6); Schlaflatenz (Frage 2: ≤ 15 Min. 0, 16–30 Min. 1, 31–60 Min. 2, > 60 Min. 3, plus 5a; Summe 0 → 0, 1–2 → 1, 3–4 → 2, 5–6 → 3); Schlafdauer (Frage 4: ab 7 h 0, ab 6 h 1, ab 5 h 2, darunter 3); Schlafeffizienz (Frage 4 durch die Zeit von Frage 1 bis Frage 3, über Mitternacht: ab 85 % 0, ab 75 % 1, ab 65 % 2, darunter 3); Schlafstörungen (5b–5j, Summe 0 → 0, 1–9 → 1, 10–18 → 2, 19–27 → 3); Schlafmittelkonsum (Frage 7); Tagesmüdigkeit (Fragen 8 und 9, Summe wie die Schlaflatenz). Gesamtwert 0–21, niedriger ist besser: 0–5 gute Schläfer, 6–10 schlechte Schläfer, 11–21 chronische Schlafstörung (Riemann & Backhaus). Die Fragen 5–9 zählen 0–3 (die 1–4 des PDFs minus eins).
  - **ASBQ**: Das PDF nennt nur ≤ 36 und ≥ 42; 37–41 heißt „mittelmäßiges Schlafverhalten“ wie bei Driller et al. 2018.
  - **ASSQ**: Neben dem Schlafschwierigkeiten-Score (Fragen 1, 3–6, 0–17, die vier Kategorien mit ihrer Interventionsempfehlung, z. B. „geringe Schlafschwierigkeiten: A + B“) die Modifikatoren als Ergebnisse: Atmung (13 und/oder 14 „ja“: „weiter untersuchen: D + E“), Reisen je Frage (11 „ja“: A spezifisch für Reisen; 12 „ja“: A + B + C (wenn indiziert) + D (wenn indiziert)) und Chronotyp (Fragen 7–10, 0–14, ≤ 4 „Abendtyp“, höher ist besser). Die Buchstaben A–E stehen im Hinweis des Bogens.
- Jedes Ergebnis erst, wenn alle seine Items beantwortet sind, wie in #72; beim PSQI so auch im PDF („nur gültig bei vollständig ausgefülltem Fragebogen“), bis auf die Häufigkeit von 5j: ohne Kreuz zählt sie als „gar nicht“, wie das PDF sagt.
- ISI: 0–28, keine (0–7), unterschwellige (8–14), moderate (15–21), schwere Schlaflosigkeit (22–28). RIS: 0–40, Unauffällig (0–12), Auffällig (13–24), Ausgeprägt (25–40); „Ich fühle mich voll leistungsfähig“ zählt umgekehrt. Alle niedriger ist besser.
- Neuer Baustein Uhrzeit (hh:mm, auch als Dauer): PSQI-Fragen 1, 3 und 4, RIS-Frage 0 (die Bettzeiten zählen nirgends). Die Minuten von PSQI-Frage 2 als Zahl. Dazu Stufen (ein Wert zählt nach dem Bereich, in dem er liegt) und der Anteil an einer Zeitspanne (Schlafeffizienz).
- PSQI: Frage 10, 10a–e und die Angaben zur Person gehen nicht in die Auswertung ein, stehen aber im Bogen.
- Alle fünf nur unter „Schlafverhalten“, wie bei DIGOTOR; Abkürzungen ASBQ, ASSQ, ISI, PSQI, RIS.
- Offensichtliche Tippfehler: ASSQ-Schlüssel Frage 10 „20:15 – 0:30 Uhr“ als „22:15 – 0:30 Uhr“ wie in der Frage; ASBQ „beeinträchtig“ als „beeinträchtigt“.
