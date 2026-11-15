# Ohne Ticket

Themen aus dem Gutachten vom 12.11.2026, die noch kein Ticket haben. Jedes steht hier mit Befund, Fundstelle und Vorschlag, damit es sich in einem neuen Chat aufgreifen lässt, ohne das Gutachten noch einmal zu lesen.

**So geht es weiter:** Ein Thema wird ein Ticket wie die anderen (`docs/agents/issue-tracker.md`: GitHub-Issue und Datei `docs/issues/NN-*.md` mit derselben Nummer, Zeile in `README.md`), oder es wird, wenn es klein ist, mit einem anderen Ticket zusammen erledigt. Danach verschwindet es aus dieser Datei. Was unter „Zu entscheiden“ steht, braucht vorher eine Antwort von Emily.

Das Gutachten: https://claude.ai/artifact/49Q6Zngame3CUwHUE5S2nF. Die Tickets der Etappe 13 hängen an #86; die übrigen kleinen Themen aus dem Gutachten sind seit dem 14.11.2026 die Etappe 14 (#110).

## 1. Tracking, zweite Stufe: HRV, Stability & Monatsfragebogen

- **Stand:** Die erste Stufe ist seit dem Interview vom 15.11.2026 die Etappe 15 **Tracking** ([SPEC.md](../../SPEC.md) §17, Sammel-Issue in [README.md](README.md)). Offen bleibt die zweite Stufe: HRV-Messung oder -Import, Stability-Wert, Monatsfragebogen (PROMIS), vorbereitet in `docs/research/pacing.md`, `docs/research/visible-apk-hrv-symptomtracking.md` und `docs/research/garmin-health-connect.md` (FIT-Dateien, Validierung).
- **Entschieden am 15.11.2026:** Sie kommt in denselben Bereich Tracking. HRV, Stress, Body Battery und SpO₂ von Garmin kommen dann über den Import der FIT-Dateien (Export-ZIP aus Garmin Connect oder per USB von der Uhr), weil Garmin sie nicht nach Health Connect schreibt.
- **Zu entscheiden:** Messquelle der HRV (FIT-Import, eigene Messung), ob ein eigener Stability-Wert gebaut wird (die Gewichte von Visible sind nicht belegt) und die Lizenz deutscher PROMIS-Fassungen. Dafür braucht es ein Interview, sobald Etappe 15 fertig ist.
- **Öffentliches Repo:** In Spezifikation, Tickets, Code und Tests nur die allgemeine Funktion, nie persönliche Werte, Medikamente oder Beschwerden (§17, Grundsatz).
