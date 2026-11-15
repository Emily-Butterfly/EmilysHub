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

- [ ] Menü **Garmin**: Einrichten fragt die Health-Connect-Berechtigungen `READ_HEART_RATE`, `READ_RESTING_HEART_RATE`, `READ_STEPS`, `READ_SLEEP` und einmal `READ_HEALTH_DATA_HISTORY` ab, mit deutscher Erklärung; ohne Health Connect oder unter Android 14 ein klarer Hinweis, Tracking funktioniert ohne
- [ ] Gelesen wird nur, solange Tracking entsperrt offen ist (beim Öffnen und über „Jetzt lesen“), nie im Hintergrund; keine Schreibrechte
- [ ] Kopie in `tracking.db` mit Zeitpunkt, Wert, Quell-App und Gerätetyp (Uhr, Brustgurt, unbekannt) aus den Metadaten; erneutes Lesen ergänzt ohne Doppel (Schlüssel aus Health-Connect-ID)
- [ ] **„Woher kommen meine Werte?“**: je Datentyp die Quellen mit Gerätetyp, Anzahl und letztem Wert
- [ ] **Stufen** als reine Logik an einer Stelle, mit Quellenkürzeln: A = Herzfrequenz in Ruhe (keine Schritte in der Minute davor und danach), Herzfrequenz mit Gerätetyp Brustgurt, Schritte; B = Herzfrequenz bei Bewegung von Uhr oder unbekannter Quelle, Ruhepuls von Garmin, Schlafdauer; C = Schlafphasen (und später HRV-Status, Sleep Score, Intensitätsminuten, Stress, Body Battery, SpO₂)
- [ ] Der Garmin-Ruhepuls bleibt eine eigene Reihe neben dem Morgenwert aus #124
- [ ] Garmin-Werte kommen ins Backup (#123)
- [ ] §11 der Spezifikation ist die vollständige Liste; keine weitere Berechtigung

## Tests

- [ ] Unit: Stufe je Wert und Gerätetyp, „in Ruhe“ an den Grenzen; Doppelimport; fehlende Metadaten → unbekannt
- [ ] Instrumentiert: Lesen mit dem Health-Connect-Testclient (erfundene Werte)

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Health Connect jetzt, FIT-Import (HRV, Stress, Body Battery, SpO₂) in der zweiten Stufe; drei Stufen statt Zahlengewichten; Brustgurt nach Gerätetyp, sonst Handgelenk-Regel. Ob Garmin Connect den Gerätetyp setzt, zeigt die Übersicht am echten Gerät.
