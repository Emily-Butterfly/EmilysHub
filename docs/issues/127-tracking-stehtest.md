# 127 – Tracking: Stehtest-Assistent mit Auswertung

**Etappe:** 15 – Tracking  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Stehtest, Sperre & Insel)  
**GitHub-Issue:** #127  
**Blockiert durch:** [125 – Grunddaten & Medikamente mit Zeitleiste](125-tracking-medikamente.md), [126 – Garmin-Werte über Health Connect & Stufen](126-tracking-garmin-health-connect.md)

## Was gebaut wird

Ein Assistent führt durch einen Stehtest zu Hause, in zwei Varianten, mit Prüfliste, Timer, Abbruch und automatischer Auswertung. Liegen Garmin-Daten vor, kommt die Herzfrequenz als Zusatzkurve dazu.

## Akzeptanzkriterien

- [ ] „+ Stehtest“ auf der Startseite; Wahl **Stehen** (Messungen nach 1, 3, 5, 8, 10 min) oder **Anlehnen** (NASA-Lean-Test, jede Minute 1–10), mit kurzer Anleitung und Quellenkürzeln
- [ ] **Prüfliste** zum Abhaken vor dem Start nach §17; ohne alle Haken kein Start
- [ ] Liegephase 5–10 min mit Timer, dann Puls/Blutdruck liegend; danach Aufstehen; zu jedem Messpunkt Ton und Vibration in der App, Eingabe Puls, Blutdruck optional, Schwindel/Herzrasen/Atemnot 0–3, Verfärbung der Beine ja/nein
- [ ] Großer Knopf **„Abbrechen – hinlegen“** zu jeder Zeit; speichert bis dahin mit Minute und Grund
- [ ] **Ausnahme Messassistent** aus #123: Bildschirm bleibt an, 5-Minuten-Sperre ruht; Hintergrund oder Bildschirm aus sperren, der Test bleibt gespeichert und gilt als abgebrochen
- [ ] Gespeichert: Uhrzeit, Stunden seit den heute eingetragenen Einnahmen (#125), Koffein, Mahlzeit, Trinkmenge davor
- [ ] **Zusatzkurve** Garmin-Herzfrequenz für den Zeitraum (Stufe nach #126); ein fehlender Messpunkt wird daraus gefüllt und gekennzeichnet
- [ ] **Auswertung** als reine Logik nach §17: größter Anstieg, Messungen mit Anstieg ≥ 30/min, Mittel der letzten drei Minuten (Anlehnen), größter Abfall systolisch/diastolisch innerhalb 3 min und danach, systolisch < 90 mmHg
- [ ] Ergebnisseite mit Tabelle und Hinweis, dass ein Heimtest keine ärztliche Untersuchung ersetzt und Medikamente das Ergebnis verändern; Liste aller Stehtests

## Tests

- [ ] Unit: Auswertung beider Varianten an allen Schwellen, mit Abbruch, mit fehlendem Blutdruck, mit Lücke aus der Zusatzkurve
- [ ] UI: Test durchlaufen und abbrechen; Sperre bei Bildschirm aus

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: beide Varianten, Prüfliste und Abbruch wie im Entwurf; Garmin-Herzfrequenz als Zusatzkurve, eigene Messpunkte bleiben Hauptwerte.
