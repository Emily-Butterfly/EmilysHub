# 126 – Tracking: Garmin-Werte über Health Connect & Stufen

**Etappe:** 15 – Tracking  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Garmin über Health Connect), §11  
**GitHub-Issue:** #126  
**Blockiert durch:** [124 – Morgen- und Abendeintrag, eigene Symptome & Nachtragen](124-tracking-tageseintraege.md)

## Was gebaut wird

Tracking liest aus Health Connect, was Garmin Connect dorthin schreibt (Herzfrequenz, Schlaf, Schritte, Ruhepuls falls vorhanden), speichert es als Kopie und ordnet jeden Wert einer Stufe A/B/C zu. Eine Übersicht zeigt, woher die Werte kommen.

## Akzeptanzkriterien

- [x] Menü **Garmin**: Einrichten fragt die Health-Connect-Berechtigungen `READ_HEART_RATE`, `READ_RESTING_HEART_RATE`, `READ_STEPS`, `READ_SLEEP` und einmal `READ_HEALTH_DATA_HISTORY` ab, mit deutscher Erklärung; ohne Health Connect oder unter Android 14 ein klarer Hinweis, Tracking funktioniert ohne
- [x] Gelesen wird nur, solange Tracking entsperrt offen ist (beim Öffnen und über „Jetzt lesen“), nie im Hintergrund; keine Schreibrechte
- [x] Kopie in `tracking.db` mit Zeitpunkt, Wert, Quell-App und Gerätetyp (Uhr, Brustgurt, unbekannt) aus den Metadaten; erneutes Lesen ergänzt ohne Doppel (Schlüssel aus Health-Connect-ID)
- [x] **„Woher kommen meine Werte?“**: je Datentyp die Quellen mit Gerätetyp, Anzahl und letztem Wert
- [x] **Stufen** als reine Logik an einer Stelle, mit Quellenkürzeln: A = Herzfrequenz in Ruhe (keine Schritte in der Minute davor und danach), Herzfrequenz mit Gerätetyp Brustgurt, Schritte; B = Herzfrequenz bei Bewegung von Uhr oder unbekannter Quelle, Ruhepuls von Garmin, Schlafdauer; C = Schlafphasen (und später HRV-Status, Sleep Score, Intensitätsminuten, Stress, Body Battery, SpO₂)
- [x] Der Garmin-Ruhepuls bleibt eine eigene Reihe neben dem Morgenwert aus #124
- [x] Garmin-Werte kommen ins Backup (#123)
- [x] §11 der Spezifikation ist die vollständige Liste; keine weitere Berechtigung

## Tests

- [x] Unit: Stufe je Wert und Gerätetyp, „in Ruhe“ an den Grenzen; Doppelimport; fehlende Metadaten → unbekannt
- [ ] Instrumentiert: Lesen mit dem Health-Connect-Testclient (erfundene Werte) – siehe unten

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Health Connect jetzt, FIT-Import (HRV, Stress, Body Battery, SpO₂) in der zweiten Stufe; drei Stufen statt Zahlengewichten; Brustgurt nach Gerätetyp, sonst Handgelenk-Regel. Ob Garmin Connect den Gerätetyp setzt, zeigt die Übersicht am echten Gerät.

Beim Bauen festgelegt (15.11.2026), nicht einzeln bestätigt:

- **Ausnahme in §9:** Health Connect zeigt ab Android 14 über einen Alias auf die Launcher-Activity, wozu die App liest; der Alias ist exportiert, aber nur das System darf ihn starten (`START_VIEW_PERMISSION_USAGE`). Die Manifest-Prüfung im Build erlaubt genau diesen Fall, SPEC §9 nennt ihn. Der Text, den er öffnet, zeigt nichts aus Tracking. Den exportierten `HealthDataSdkService` der Bibliothek (für die Health-Connect-App vor Android 14, ohne Berechtigung geschützt) entfernt das Manifest.
- Bibliothek `androidx.health.connect:connect-client` 1.1.0 (stabil). Den Health-Connect-Testclient gibt es nur als Alpha, die eine Vorabversion des Clients festnagelt; statt des instrumentierten Tests prüfen Unit-Tests die Umwandlung echter Health-Connect-Records (`HealthRecordsTest`) und das Lesen über eine eigene Fälschung der Schnittstelle (`TrackingGarminTest`).
- `tracking.db` auf Version 3 mit Migration und Test; Zeitpunkte der Garmin-Werte in Millisekunden, damit die Datenbank sie als Zahlen vergleicht.
- Gelesen wird beim Entsperren von Tracking und über „Jetzt lesen“; das erste Mal ein Jahr zurück, danach ab dem letzten Lesen mit einem Tag Überlappung. Gelesen werden die Werte aller Apps in Health Connect; die Übersicht nennt die App.
- „In Ruhe“: keine Schritte (Anzahl > 0) in Schrittintervallen, die die Minute davor oder danach berühren; ohne Schrittdaten gilt ein Wert als in Ruhe. Uhr = `TYPE_WATCH`, Brustgurt = `TYPE_CHEST_STRAP`, alles andere und fehlende Metadaten = unbekannt.
- Die Stufe steht in der Übersicht beim letzten Wert jeder Quelle.

