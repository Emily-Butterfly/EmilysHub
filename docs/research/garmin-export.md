# Garmin-Export: Aufbau, Werte, Wege

- **Zweck:** Grundlage für den Garmin-Export in SPEC.md §17 (Etappe 16)
- **Stand:** Recherche vom 16.11.2026. Garmin dokumentiert den Aufbau des Exports nicht. Als Primärquelle gelten daher Parser, die an echten Exporten getestet wurden; Forum und Ratgeber sind Sekundärquellen. Die Garmin-FAQ „Accessing your Personal Data“ war nicht abrufbar (403). Unbelegtes steht als **[OFFEN]**.
- **Prüfung am echten Export:** Vor dem Bauen gibt ein Skript nur die Struktur eines echten Exports aus (Pfade, Feldnamen mit Typ, FIT-Dateitypen und -Nachrichten mit Anzahl), keine Werte. Sie ergänzt diese Datei.

## 1. Gesamt-ZIP („Export Your Data“)

- Angefordert unter garmin.com/account/datamanagement; der Link kommt per E-Mail und läuft ab. Solange ein Export läuft, geht kein neuer. Dauer laut Sekundärquellen von Minuten bis etwa eine Woche, bei großen Konten länger. [RUNALYZE] [TAKEOUTDAY] [PORTMYDATA]
- Unter `<uuid>_1/DI_CONNECT/`: [NOOP-29] [GARMIN-INSIGHTS] [COLINZH] [RUNALYZE]
  - `DI-Connect-Aggregator/UDSFile_<von>_<bis>.json`: Tageszusammenfassungen, etwa 3 Monate je Datei
  - `DI-Connect-Wellness/`: `…_sleepData.json`, `…_healthStatusData.json`, `BloodPressureFile_*.json`, Biometrie, Fitnessalter
  - `DI-Connect-Metrics/`: VO₂max, Training Readiness, Belastung
  - `DI-Connect-Fitness/`: Aktivitäten-Zusammenfassungen, Bestleistungen
  - `DI-Connect-Uploaded-Files/` (auch `DI-Connect-Fitness-Uploaded-Files`): verschachtelte ZIPs (`UploadedFiles_0-_Part2.zip`) mit den **Original-FIT-Dateien**, generisch benannt
  - `DI-Connect-User/`, `DI-Connect-Social/`
- **FIT nur unter Uploaded-Files**, alles andere ist JSON.
- Ordner- und Feldnamen haben sich über die Jahre geändert. [TEMPO-224] Deshalb nach Inhalt erkennen, nicht nach festem Pfad.

## 2. Wo welcher Wert liegt

| Wert | Datei und Feld | Auflösung |
|---|---|---|
| Nacht-HRV | `healthStatusData`: je `calendarDate` eine Liste `metrics` mit `{"type": "HRV", "value": …}` | Mittel der Nacht |
| HRV alle 5 Minuten | FIT-Nachricht `hrv_value` in kleinen Dateien unter Uploaded-Files [GF-EXPORT] | 5 Minuten |
| HRV-Status, Baseline | FIT-Nachricht `hrv_status_summary` [GF-EXPORT]; im JSON nicht sicher belegt **[OFFEN]** | Tag |
| Stress | `UDSFile`: `allDayStress.aggregatorList[]` mit `type` (TOTAL, AWAKE …), `averageStressLevel`, Dauern in s; fein nur in FIT (`stress_level`) | Tag / 3 Minuten |
| Body Battery | `UDSFile`: `bodyBatteryChargedValueAndDrainedValue`, Höchst- und Tiefstwert | Tag |
| SpO₂ | `UDSFile.averageSpo2Value`; `sleepData.spo2SleepSummary` (`averageSPO2`, `lowestSPO2`); `healthStatusData` Typ `SPO2` | Tag / Nacht |
| Intensitätsminuten | `UDSFile` (mäßig, intensiv) **[OFFEN]: genaue Feldnamen** | Tag |
| Ruhepuls, Schlaf, Atmung | `UDSFile.restingHeartRate`, `sleepData`, `healthStatusData` | Tag / Nacht (nicht übernommen) |

- Zeitangaben in `sleepData` wie `2026-07-26T18:59:17.0`, ohne Zeitzone.
- Ob alle Monitoring-FIT-Dateien (Monitor, Sleep, Metrics) im Gesamt-ZIP stecken, ist **[OFFEN]**; belegt sind nur HRV-Dateien. Prüfen über `file_id.type` der FIT-Dateien.

## 3. Tagesexport

- „Einen ganzen Tag Wellness-Daten exportieren“ liefert ein ZIP mit dem Datum als Namen und etwa 12–24 FIT-Dateien (`*_HRV_STATUS.fit`, `*_SLEEP_DATA.fit`, `*_WELLNESS.fit`, `*_METRICS.fit`, `*_SKIN_TEMP.fit`). [GF-DAY] Ein Garmin-Mitarbeiter bestätigt den Tagesexport im FIT-Format. [GF-STAFF]
- Wo er in der Oberfläche liegt, ist **[OFFEN]**.
- Die Angabe in `garmin-health-connect.md` §2.2, der Export enthalte `_HRV_STATUS.fit`, gilt für diesen Tagesexport, nicht für das Gesamt-ZIP.

## 4. Per USB (verworfen)

- Ordner auf der Uhr: `GARMIN/Monitor`, `Sleep`, `HRVStatus`, `Metrics`, `Activity`; neuere Uhren melden sich per MTP. [GARMINDB] [GF-INSTINCT]
- Laut mehreren Nutzern löscht die Connect-App beim Sync die Dateien in Sleep, HRVStatus und Monitor von der Uhr. [GF-USB] **[OFFEN]**, nicht von Garmin bestätigt.
- Verworfen im Interview vom 16.11.2026: Kabel und Adapter, und die Dateien sind nach dem Sync weg.

## 5. Quellen

Abgerufen am 16.11.2026.

| Kürzel | Quelle |
|---|---|
| COLINZH | https://github.com/COLINZH26/garmin-ai-skill/blob/main/references/data_schema.md |
| GARMIN-INSIGHTS | https://github.com/subhashsharma-glitch/garmin-insights |
| GARMINDB | https://github.com/tcgoetz/GarminDB (`garmindb/copy.py`, `garmin_connect_config_manager.py`); Diskussion zu MTP: https://github.com/tcgoetz/GarminDB/discussions/156 |
| GF-DAY | Garmin-Forum (sekundär): https://forums.garmin.com/apps-software/mobile-apps-web/f/garmin-connect-web/430280/downloading-health-data-for-analysis |
| GF-EXPORT | Garmin-Forum (sekundär): https://forums.garmin.com/apps-software/mobile-apps-web/f/garmin-connect-web/423807/hrv-in-garmin-data-export |
| GF-INSTINCT | Garmin-Forum (sekundär): https://forums.garmin.com/outdoor-recreation/outdoor-recreation/f/instinct-2-series/346787/hrv-status-no-more-sync-on-connect-2xsolar/1687122 |
| GF-STAFF | Garmin-Forum, Antwort eines Mitarbeiters: https://forums.garmin.com/apps-software/mobile-apps-web/f/garmin-connect-web/445549/downloading-data-from-my-garmin/2069332 |
| GF-USB | Garmin-Forum (sekundär): https://forums.garmin.com/sports-fitness/healthandwellness/f/index-sleep-monitor/423093/manual-downloading-via-usb-is-pratically-impossible |
| NOOP-29 | https://github.com/vuongvm812/noop/issues/29 und /pull/30 (Export vom Nov. 2026) |
| PORTMYDATA | https://portmydata.github.io/findings |
| RUNALYZE | https://blog.runalyze.com/allgemein-en/import-your-garmin-history/ |
| TAKEOUTDAY | https://takeoutday.org/guides/how-to-export-garmin-connect-data (nennt `DI-Connect-Wellness/Monitoring/`, sonst nirgends bestätigt) |
| TEMPO-224 | https://github.com/trevordavies095/tempo/issues/224 |
