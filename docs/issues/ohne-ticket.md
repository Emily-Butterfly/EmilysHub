# Ohne Ticket

Themen aus dem Gutachten vom 12.11.2026, die noch kein Ticket haben. Jedes steht hier mit Befund, Fundstelle und Vorschlag, damit es sich in einem neuen Chat aufgreifen lässt, ohne das Gutachten noch einmal zu lesen.

**So geht es weiter:** Ein Thema wird ein Ticket wie die anderen (`docs/agents/issue-tracker.md`: GitHub-Issue und Datei `docs/issues/NN-*.md` mit derselben Nummer, Zeile in `README.md`), oder es wird, wenn es klein ist, mit einem anderen Ticket zusammen erledigt. Danach verschwindet es aus dieser Datei. Was unter „Zu entscheiden“ steht, braucht vorher eine Antwort von Emily.

Das Gutachten: https://claude.ai/artifact/49Q6Zngame3CUwHUE5S2nF. Die Tickets der Etappe 13 hängen an #86; die übrigen kleinen Themen aus dem Gutachten sind seit dem 14.11.2026 die Etappe 14 (#110).

## 1. Tracking: Stability-Wert (später)

- **Stand:** Die zweite Stufe von Tracking (Garmin-Export mit HRV, Monatsfragebogen FUNCAP55) ist seit dem Interview vom 16.11.2026 die Etappe 16 ([SPEC.md](../../SPEC.md) §17, Sammel-Issue in [README.md](README.md)). Ein Stability-Wert kommt dort nicht vor, weder als Note noch als Faktorkarten.
- **Offen gehalten:** Er soll später möglich bleiben. Die Faktoren, die Visible nennt (Ruhepuls am Morgen, Nacht-HRV, Schlafqualität, Symptome des Vorabends), liegen je Tag in der Tracking-Datenbank; die HRV ungerundet. Gewichte, Schwellen und Aggregation von Visible sind nicht öffentlich (`docs/research/visible-apk-hrv-symptomtracking.md` §5), daher wäre er eine eigene, versionierte Berechnung („Stability V1“) mit eigenem Namen und Tests, gekennzeichnet als eigene Festlegung.
- **Zu entscheiden, wenn es so weit ist:** Faktorkarten oder Note 1–5, Baseline (Zeitraum, Spannweite), Umgang mit fehlenden Faktoren; Hinweis- oder nur Anzeigewert.
- **Öffentliches Repo:** In Spezifikation, Tickets, Code und Tests nur die allgemeine Funktion, nie persönliche Werte, Medikamente oder Beschwerden (§17, Grundsatz).
