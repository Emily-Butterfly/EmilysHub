# 138 – Garmin-Export: FIT-Decoder & feine Werte

**Etappe:** 16 – Tracking, zweite Stufe  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Garmin-Export)  
**GitHub-Issue:** #138  
**Blockiert durch:** [136 – Struktur des echten Exports](136-garmin-export-struktur.md), [137 – Import der Tageswerte](137-garmin-export-import.md)

## Was gebaut wird

Ein eigener, kleiner FIT-Decoder liest aus den FIT-Dateien des Gesamt-ZIPs (verschachtelte ZIPs unter Uploaded-Files) und des Tagesexports die feinen Werte: HRV alle 5 Minuten, Stress alle 3 Minuten, HRV-Status und Baseline.

## Akzeptanzkriterien

- [ ] Decoder als reine Logik ohne Android: Dateikopf, Definitions- und Datennachrichten, komprimierte Zeitstempel, Entwicklerfelder überspringen, CRC prüfen; liest nur die Nachrichten `file_id`, `hrv_value`, `hrv_status_summary`, `stress_level` (Nummern aus dem FIT-Profil, mit Fundstelle), alles andere wird übersprungen
- [ ] Erkannt wird über `file_id.type` und die Nachrichten, nicht über den Dateinamen; verschachtelte ZIPs werden gestreamt durchsucht
- [ ] Dieselbe Dateiauswahl wie #137 nimmt auch den **Tagesexport** an
- [ ] Gespeichert: HRV alle 5 Minuten (C), Stress alle 3 Minuten (C), HRV-Status und Baseline, wenn vorhanden (C); ungerundet, ohne Doppel; ins Backup
- [ ] Tracking rechnet aus den feinen Werten keine eigenen Tageswerte; die Tageswerte bleiben die aus #137
- [ ] Eine kaputte FIT-Datei zählt als „nicht erkannt“, der Rest des Imports läuft weiter
- [ ] Keine neue Bibliothek (das FIT-SDK von Garmin wird nicht eingebunden)

## Tests

- [ ] Unit: im Test erzeugte FIT-Bytes mit erfundenen Werten (Kopf, Definitionen, Daten, CRC); komprimierte Zeitstempel; unbekannte Nachrichten; falscher CRC; abgeschnittene Datei
- [ ] Unit: verschachteltes ZIP und Tagesexport in der Struktur aus #136; Doppelimport

## Entscheidungen

Abgestimmt im Interview vom 16.11.2026: feine Werte aus FIT mit eigenem Decoder statt Garmins SDK (keine neue Bibliothek, Lizenz „internal business purposes“); Gesamt-ZIP und Tagesexport; Stufen: 5-Minuten-HRV C (Kurzmessungen schwach überprüft), HRV-Status C.
