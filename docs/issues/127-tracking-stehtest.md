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

- [x] „+ Stehtest“ auf der Startseite; Wahl **Stehen** (Messungen nach 1, 3, 5, 8, 10 min) oder **Anlehnen** (NASA-Lean-Test, jede Minute 1–10), mit kurzer Anleitung und Quellenkürzeln
- [x] **Prüfliste** zum Abhaken vor dem Start nach §17; ohne alle Haken kein Start
- [x] Liegephase 5–10 min mit Timer, dann Puls/Blutdruck liegend; danach Aufstehen; zu jedem Messpunkt Ton und Vibration in der App, Eingabe Puls, Blutdruck optional, Schwindel/Herzrasen/Atemnot 0–3, Verfärbung der Beine ja/nein
- [x] Großer Knopf **„Abbrechen – hinlegen“** zu jeder Zeit; speichert bis dahin mit Minute und Grund
- [x] **Ausnahme Messassistent** aus #123: Bildschirm bleibt an, 5-Minuten-Sperre ruht; Hintergrund oder Bildschirm aus sperren, der Test bleibt gespeichert und gilt als abgebrochen
- [x] Gespeichert: Uhrzeit, Stunden seit den heute eingetragenen Einnahmen (#125), Koffein, Mahlzeit, Trinkmenge davor
- [x] **Zusatzkurve** Garmin-Herzfrequenz für den Zeitraum (Stufe nach #126); ein fehlender Messpunkt wird daraus gefüllt und gekennzeichnet
- [x] **Auswertung** als reine Logik nach §17: größter Anstieg, Messungen mit Anstieg ≥ 30/min, Mittel der letzten drei Minuten (Anlehnen), größter Abfall systolisch/diastolisch innerhalb 3 min und danach, systolisch < 90 mmHg
- [x] Ergebnisseite mit Tabelle und Hinweis, dass ein Heimtest keine ärztliche Untersuchung ersetzt und Medikamente das Ergebnis verändern; Liste aller Stehtests

## Tests

- [x] Unit: Auswertung beider Varianten an allen Schwellen, mit Abbruch, mit fehlendem Blutdruck, mit Lücke aus der Zusatzkurve
- [x] UI: Test durchlaufen und abbrechen; Sperre bei Bildschirm aus

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: beide Varianten, Prüfliste und Abbruch wie im Entwurf; Garmin-Herzfrequenz als Zusatzkurve, eigene Messpunkte bleiben Hauptwerte.

Beim Bauen festgelegt (15.11.2026), nicht einzeln bestätigt:

- `tracking.db` auf Version 4 mit Migration und Test: `stand_test` (Variante, Beginn des Liegens, Aufstehen, Liegendwerte, Davor-Angaben, Abbruch mit Ursache, Minute und Grund, abgeschlossen), `stand_point` je Messminute, `stand_intake` mit den Minuten seit der letzten heutigen Einnahme je Medikament.
- Gespeichert wird beim Fortschreiten: Start, Aufstehen, jede Messung; die letzte Messung schließt den Test ab. Überspringen legt einen leeren Messpunkt an, den die Zusatzkurve füllen kann. Speichern und Überspringen gehen erst zur Minute des Messpunkts, damit der Wert zu ihr gehört.
- „Einnahmen heute“: die geplanten Zeiten der heute genommenen Medikamente bis zum Start; steht im heutigen Abendeintrag eine andere Uhrzeit, gilt sie, „vergessen“ zählt nicht.
- Davor: Koffein als Anzahl heute, letzte Mahlzeit vor wie vielen Stunden, Trinkmenge heute in Litern.
- Aufstehen geht nach 5 Minuten Liegen; Ton und Vibration in der App nach 5 und 10 Minuten Liegen und zu jedem Messpunkt (Alarm-Lautstärke, ohne Benachrichtigung).
- Abbruch: „Abbrechen – hinlegen“ oder Zurück fragt nach einem Grund (optional); die Minute zählt ganze Minuten seit dem Aufstehen, im Liegen keine. Sperrt Tracking (Hintergrund, Bildschirm aus), wird das schon Getippte des Messpunkts gespeichert und der Test gilt als „abgebrochen: gesperrt“. Eine Drehung des Bildschirms bricht nicht ab.
- Zusatzkurve: Garmin-Herzfrequenz vom Hinlegen bis eine Minute nach dem letzten Messpunkt, Stufe A (ein Stehtest gilt nach §17 als Ruhe). Ein fehlender Puls wird aus dem nächstgelegenen Wert höchstens 30 Sekunden neben der Messminute gefüllt (eigene Festlegung), der Liegendpuls aus dem letzten Wert der Minute vor dem Aufstehen; gefüllte Werte tragen ein Sternchen.
- Auswertung: Abfälle unter dem Liegendwert, ein Anstieg zählt als 0; „bis 3 min“ heißt Messminute ≤ 3. Das Mittel der letzten drei Minuten (Anlehnen) gibt es nur, wenn alle drei einen Puls haben. Ein abgebrochener Test wird bis zur Abbruchminute ausgewertet, dazu jeder schon gespeicherte Messpunkt; die Zusatzkurve reicht dann bis eine Minute nach dem Abbruch.
