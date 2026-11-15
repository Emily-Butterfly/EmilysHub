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

- [x] Menü **Arzt-Zusammenfassung**: Zeitraum in Wochen von 1 bis zur Zahl der getrackten Wochen (Standard 4 oder weniger)
- [x] Inhalt nach §17: stärkste Symptome mit Mittelwert, Kennzahlen, Stehtests als Tabelle, Ereignisse, Anstrengungen und Episoden, Medikamente mit Zeitleiste, Garmin-Werte mit Stufe, aktuelle Hinweise mit Fragen; eigene Fragen als Markdown
- [x] Vorschau in der App; **„Als PDF speichern“** über Storage Access Framework in einen gewählten Ordner, erzeugt mit `android.graphics.pdf.PdfDocument`, ohne neue Bibliothek, A4, lesbar in Schwarz-Weiß
- [x] Fotos optional (Standard aus; erst mit #133)
- [x] Vor dem Speichern der Hinweis: PDF nicht verschlüsselt, nach dem Termin löschen
- [x] Nur von Hand; kein Teilen-Dialog, keine Kopie im App-Speicher

## Tests

- [x] Unit: Zeitraum-Grenzen (nicht mehr Wochen als getrackt), Inhalt aus erfundenen Daten
- [x] Instrumentiert: PDF entsteht, hat die erwarteten Seiten

## Entscheidungen

Abgestimmt am 15.11.2026: Export erlaubt; beliebige Wochenzahl bis zur getrackten Dauer; Fotos standardmäßig nicht enthalten.

Abgestimmt beim Bauen (15.11.2026): Für den Speicherdialog bleibt Tracking offen, nur für diesen einen Ausflug (siehe [ADR 0003](../adr/0003-tracking-photos-in-its-database-trips-out-stay-open.md)).

Beim Bauen festgelegt (15.11.2026), nicht einzeln bestätigt:

- Die Wochen enden heute, je 7 Tage; getrackt ist ab dem ersten Tag mit Eintrag, Stehtest, Ereignis, Anstrengung oder Episode, angefangene Wochen zählen. Ohne etwas Getracktes gibt es keine Zusammenfassung.
- „Stärkste Symptome“: die 5 mit dem höchsten Mittel über 0. Kennzahlen sind die der Wochenauswertung über den ganzen Zeitraum. Garmin-Werte: Schritte (A), Ruhepuls von Garmin und Schlafdauer (B), Tiefschlaf (C, „Garmin-Schätzung“), nur die mit Daten. Hinweise: die der Startseite, „erledigte“ ohne.
- Eigene Fragen bleiben in den Einstellungen von Tracking für den nächsten Termin; in der PDF als Text (Überschriften fett, Listen mit Punkten).
- Die PDF wird direkt in die gewählte Datei geschrieben, nirgends sonst; sperrt Tracking während der Auswahl oder misslingt das Schreiben, wird die leere Datei wieder gelöscht.
- Tests: Robolectric hat kein `PdfDocument`; das Seitenlayout wird dort auf Bitmaps geprüft (`SummaryPdfTest`), die echte PDF instrumentiert (`SummaryAndPhotosOnDeviceTest`).
