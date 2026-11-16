# 137 – Garmin-Export: Import der Tageswerte

**Etappe:** 16 – Tracking, zweite Stufe  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Garmin-Export)  
**GitHub-Issue:** #137  
**Blockiert durch:** [136 – Struktur des echten Exports](136-garmin-export-struktur.md)

## Was gebaut wird

Im Menü Garmin „Export einlesen“: Tracking liest aus dem Gesamt-ZIP von Garmin Connect die Tageswerte, die Health Connect nicht liefert, und speichert sie als Kopie mit Stufe.

## Akzeptanzkriterien

- [ ] „Export einlesen“ öffnet die Dateiauswahl (`ACTION_OPEN_DOCUMENT`, keine Berechtigung); Tracking bleibt dafür offen wie bei Kamera und Speicherdialog (ADR 0003); darunter kurz, wie man den Export anfordert
- [ ] Das ZIP wird gestreamt gelesen (`ZipInputStream`), nichts davon kopiert; Dateien werden nach Inhalt erkannt (Feldnamen aus #136), nicht nach festem Pfad
- [ ] Übernommen aus JSON (`org.json`): Nacht-HRV als Mittel (B), Stress-Tagesmittel (C), Body Battery höchster und niedrigster Wert (C), SpO₂ Tagesmittel sowie Mittel und niedrigster Wert der Nacht (C), Intensitätsminuten (C). Nicht übernommen: Ruhepuls, Schlaf, Schritte
- [ ] Nachtwerte gehören zum Tag des Aufwachens, die übrigen zu ihrem Kalendertag
- [ ] Gespeichert ungerundet in `tracking.db` (Migration mit Test) mit Art, Tag oder Zeitpunkt, Wert, Quelle „Garmin-Export“; erneuter Import ergänzt nach Art und Zeitpunkt ohne Doppel
- [ ] Die Stufen stehen bei der bestehenden Zuordnung an einer Stelle im Code, mit Quellenkürzeln
- [ ] Ergebnis: „x Tage übernommen, y Dateien nicht erkannt“; danach der Hinweis „Das ZIP liegt unverschlüsselt in Downloads – jetzt löschen.“
- [ ] Ein fehlerhaftes oder fremdes ZIP bricht mit einer klaren Meldung ab, ohne halbe Tage zu speichern
- [ ] „Woher kommen meine Werte?“ zeigt die Quelle „Garmin-Export“ je Datentyp mit Anzahl und letztem Wert
- [ ] Die Werte kommen ins Tracking-Backup (#123); Alles löschen entfernt sie
- [ ] Keine neue Berechtigung, keine neue Bibliothek

## Tests

- [ ] Unit: Erkennen und Lesen der JSON-Dateien in der Struktur aus #136 mit erfundenen Werten; unbekannte Felder und Dateien; Tageszuordnung der Nachtwerte; Doppelimport; überlappende Exporte
- [ ] Unit: kaputtes ZIP, ZIP ohne Garmin-Dateien
- [ ] Instrumentiert: Import über eine Test-URI, Werte in der Datenbank und im Backup

## Entscheidungen

Abgestimmt im Interview vom 16.11.2026: nur der Export, kein USB; Tageswerte aus JSON ohne neue Bibliothek; übernommen wird, was Health Connect fehlt, plus Intensitätsminuten; das ZIP wird nur gelesen; Doppel nach Art und Zeitpunkt ausgeschlossen.
