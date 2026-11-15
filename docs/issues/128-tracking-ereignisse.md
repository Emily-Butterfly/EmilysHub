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

- [ ] „+ Ereignis“: Art, Uhrzeit, Wassertemperatur lauwarm/warm/heiß (bei Wasser), Dauer, stehend/sitzend
- [ ] Puls **vorher**, **direkt danach**, nach **5** und **10 Minuten** mit Timer (Ton und Vibration in der App, Ausnahme Messassistent wie #127); Sauerstoffsättigung optional; Atemnot und Schwindel 0–3; hinsetzen nötig (nach wie vielen Minuten)
- [ ] **Haut:** wo (Beine, Arme, Rumpf, Finger/Zehen, mehrfach), Muster gleichmäßig-geschlossen oder unterbrochen-netzartig, schmerzt ja/nein, verschwindet im Liegen ja/nein, Minuten bis zur Rückbildung; Minuten bis wieder belastbar
- [ ] Felder lassen sich später ergänzen (z. B. Rückbildungszeit nach einer Stunde)
- [ ] Garmin-Herzfrequenz von 15 min davor bis 30 min danach als Zusatzkurve mit ihrer Stufe (#126)
- [ ] Stunden seit den heute eingetragenen Einnahmen gespeichert
- [ ] Liste der Ereignisse, neueste oben; Rückfrage beim Löschen

## Tests

- [ ] Unit: Timer-Zeitpunkte, spätere Ergänzung, Stufe der Zusatzkurve bei Bewegung
- [ ] UI: Ereignis erfassen mit Timer, später ergänzen

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Felder wie im Entwurf; Fotos als eigener, letzter Slice.
