# 132 – Tracking: Arzt-Zusammenfassung als PDF

**Etappe:** 15 – Tracking  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Arzt-Zusammenfassung)  
**GitHub-Issue:** #132  
**Blockiert durch:** [131 – Hinweise mit Quellen](131-tracking-hinweise.md)

## Was gebaut wird

Eine vorausgefüllte Seite für den Arzttermin über frei wählbar viele Wochen, mit Vorschau und Export als PDF.

## Akzeptanzkriterien

- [ ] Menü **Arzt-Zusammenfassung**: Zeitraum in Wochen von 1 bis zur Zahl der getrackten Wochen (Standard 4 oder weniger)
- [ ] Inhalt nach §17: stärkste Symptome mit Mittelwert, Kennzahlen, Stehtests als Tabelle, Ereignisse, Anstrengungen und Episoden, Medikamente mit Zeitleiste, Garmin-Werte mit Stufe, aktuelle Hinweise mit Fragen; eigene Fragen als Markdown
- [ ] Vorschau in der App; **„Als PDF speichern“** über Storage Access Framework in einen gewählten Ordner, erzeugt mit `android.graphics.pdf.PdfDocument`, ohne neue Bibliothek, A4, lesbar in Schwarz-Weiß
- [ ] Fotos optional (Standard aus; erst mit #133)
- [ ] Vor dem Speichern der Hinweis: PDF nicht verschlüsselt, nach dem Termin löschen
- [ ] Nur von Hand; kein Teilen-Dialog, keine Kopie im App-Speicher

## Tests

- [ ] Unit: Zeitraum-Grenzen (nicht mehr Wochen als getrackt), Inhalt aus erfundenen Daten
- [ ] Instrumentiert: PDF entsteht, hat die erwarteten Seiten

## Entscheidungen

Abgestimmt am 15.11.2026: Export erlaubt; beliebige Wochenzahl bis zur getrackten Dauer; Fotos standardmäßig nicht enthalten.
