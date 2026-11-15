# 128 – Tracking: Ereignisse mit Puls-Timer & Hautangaben

**Etappe:** 15 – Tracking  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Ereignisse)  
**GitHub-Issue:** #128  
**Blockiert durch:** [127 – Stehtest-Assistent mit Auswertung](127-tracking-stehtest.md)

## Was gebaut wird

Ein Protokoll für Situationen, die Beschwerden auslösen (Duschen, Baden, Sauna, Hitze, langes Stehen, Sonstiges), mit Puls-Timer, Haut und Erholung. Fotos folgen in #133.

## Akzeptanzkriterien

- [x] „+ Ereignis“: Art, Uhrzeit, Wassertemperatur lauwarm/warm/heiß (bei Wasser), Dauer, stehend/sitzend
- [x] Puls **vorher**, **direkt danach**, nach **5** und **10 Minuten** mit Timer (Ton und Vibration in der App, Ausnahme Messassistent wie #127); Sauerstoffsättigung optional; Atemnot und Schwindel 0–3; hinsetzen nötig (nach wie vielen Minuten)
- [x] **Haut:** wo (Beine, Arme, Rumpf, Finger/Zehen, mehrfach), Muster gleichmäßig-geschlossen oder unterbrochen-netzartig, schmerzt ja/nein, verschwindet im Liegen ja/nein, Minuten bis zur Rückbildung; Minuten bis wieder belastbar
- [x] Felder lassen sich später ergänzen (z. B. Rückbildungszeit nach einer Stunde)
- [x] Garmin-Herzfrequenz von 15 min davor bis 30 min danach als Zusatzkurve mit ihrer Stufe (#126)
- [x] Stunden seit den heute eingetragenen Einnahmen gespeichert
- [x] Liste der Ereignisse, neueste oben; Rückfrage beim Löschen

## Tests

- [x] Unit: Timer-Zeitpunkte, spätere Ergänzung, Stufe der Zusatzkurve bei Bewegung
- [x] UI: Ereignis erfassen mit Timer, später ergänzen

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Felder wie im Entwurf; Fotos als eigener, letzter Slice.

Beim Bauen festgelegt (15.11.2026), nicht einzeln bestätigt:

- `tracking.db` auf Version 5 mit Migration und Test: `tracking_event` mit allen Feldern, `event_intake` mit den Minuten seit der letzten heutigen Einnahme je Medikament (dieselbe Regel wie beim Stehtest, `TodaysIntakes`).
- „+ Ereignis“ fragt zuerst die Art und legt das Ereignis jetzt an; die Seite speichert wie die Tageseinträge beim Verlassen und vor dem Sperren. Datum und Uhrzeit lassen sich ändern, auf die Minute; das Ende rückt mit, und die Stunden seit den Einnahmen gelten dann für die neue Zeit.
- Timer: „Vorbei – Timer starten“ hält das Ende fest; Puls direkt danach, nach 5 und nach 10 Minuten, je mit Ton und Vibration bei 5 und 10, nur wenn es fällig wird, während die Seite offen ist. Der Timer läuft, bis alle drei eingetragen sind, höchstens 5 Minuten über die 10 hinaus (eigene Festlegung); solange gilt die Ausnahme Messassistent.
- Zusatzkurve: Garmin-Herzfrequenz von 15 Minuten vor der Uhrzeit bis 30 Minuten nach dem Ende (ohne Ende: Uhrzeit plus Dauer), je Wert mit Stufe A oder B nach den Schritten darum (#126); auf der Kurve tragen die Werte der Stufe B einen Punkt, dazu die Anzahl je Stufe.
- Wassertemperatur nur bei Duschen und Baden; „nach wie vielen Minuten“ nur bei „hinsetzen nötig: ja“. Löschen auf der Seite des Ereignisses, mit Rückfrage.

