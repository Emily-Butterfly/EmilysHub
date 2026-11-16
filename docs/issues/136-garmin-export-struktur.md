# 136 – Garmin-Export: Struktur des echten Exports

**Etappe:** 16 – Tracking, zweite Stufe  
**Typ:** Vorbereitung  
**Art:** 🙋 HITL – braucht Emily (Export anfordern, Skript laufen lassen)  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Garmin-Export)  
**GitHub-Issue:** #136  
**Blockiert durch:** –

## Was gebaut wird

Ein kleines Skript, das aus einem echten Garmin-Export nur die **Struktur** ausgibt, keine Werte. Mit seiner Ausgabe wird der Import gegen das echte Format gebaut, und die Tests nutzen erfundene Werte in genau dieser Struktur.

## Akzeptanzkriterien

- [x] `tools/garmin-export-struktur.py`: Python 3 ohne Abhängigkeiten, Aufruf `python3 -I tools/garmin-export-struktur.py <export.zip>`; nimmt das Gesamt-ZIP und den Tagesexport, liest auch verschachtelte ZIPs
- [x] Ausgabe je JSON-Datei: Pfad mit Datumsteilen und Kennungen ersetzt (`<datum>`, `<id>`), Feldnamen als Baum mit Typ (Zahl, Text, Liste, Objekt) und, bei Feldern wie `type`, die vorkommenden Kennwörter (`HRV`, `SPO2` …); keine Zahlenwerte, keine Zeitpunkte, keine freien Texte
- [x] Ausgabe je FIT-Datei: `file_id.type`, Nachrichten mit Namen oder Nummer und Anzahl, Feldnummern je Nachricht; keine Feldwerte. E-Mail-Adressen und Kennungen in Dateinamen werden ersetzt
- [x] Das Skript liest nur, schreibt nichts außer auf die Standardausgabe
- [ ] 🙋 Emily fordert den Gesamtexport an (garmin.com/account/datamanagement) und einen Tagesexport, lässt das Skript laufen und gibt die Ausgabe weiter
- [ ] Die Struktur steht in `docs/research/garmin-export.md` (§2 ergänzt, [OFFEN]-Punkte geklärt: Intensitätsminuten, HRV-Status im JSON oder FIT, welche FIT-Typen im Gesamt-ZIP, wo der Tagesexport liegt)

## Tests

- [x] Ein erfundenes ZIP mit je einer JSON- und einer FIT-Datei: Die Ausgabe enthält Feldnamen und Nachrichten, aber keinen der eingesetzten Werte

## Entscheidungen

Abgestimmt im Interview vom 16.11.2026: Das Format wird an einem echten Export geprüft, aber nur die Struktur kommt ins Repo, nie Werte.

Beim Bauen festgelegt (16.11.2026), nicht einzeln bestätigt:

- Test: `python3 -I tools/test_garmin_export_struktur.py` (erzeugt ein ZIP mit erfundenen JSON- und FIT-Daten, auch verschachtelt, und prüft, dass keiner der Werte in der Ausgabe steht).
- Werte gezeigt werden nur bei Feldern, die eine Art benennen (`type`, `metricType`, `metricsType`, `aggregatorType`), und nur, wenn sie wie ein Kennwort aussehen (Buchstaben, Ziffern, Unterstrich, höchstens 40 Zeichen). FIT: Dateityp (`file_id.type`) als Zahl mit Namen, sonst nur Nachrichtennummern, Anzahl und Feldnummern.
- Verschachtelte ZIPs werden bis zur dritten Ebene geöffnet; Dateien ohne Endung (Tagesexport) erkennt das Skript am FIT-Kopf.

