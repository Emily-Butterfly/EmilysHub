# EmilysHub – Spezifikation

Persönliche, vollständig offline arbeitende Android-App als zentrale Anlaufstelle für Routinen, Erinnerungen, To-dos, Ziele und Notizen.

## 1. Grundlagen

| Thema | Entscheidung |
|---|---|
| Zielgerät | Samsung Galaxy A52 5G, Android 14 (One UI 6) |
| Mindestversion | Android 11 (API 30) |
| Technik | Kotlin, Jetpack Compose, Room + SQLCipher |
| Sprache | Deutsch |
| Name | EmilysHub |
| Icon | Haus mit Häkchen, adaptives Icon, selbst erstellt (keine externen Grafiken) |
| Farben | Festes Farbschema: hell = Flieder, dunkel = Lila (kein Material You) |
| Hell/Dunkel | Folgt dem System, in der App umstellbar |

### Navigation

Untere Leiste mit den Tabs **Heute · Routinen · To-dos · Wunschliste · Notizen**.
Startbildschirm ist **Heute**. Orte, Statusseite, Backups und Sicherheit liegen in den **Einstellungen**.

### Heute-Übersicht

- Alle heute fälligen Routinen und To-dos, nach Uhrzeit sortiert, direkt abhakbar
- Einträge, die auf das Erreichen eines Ortes warten
- Überfällige To-dos oben
- Angepinnte Notizen
- Warnbanner bei fehlenden Berechtigungen, fehlgeschlagenem oder veraltetem Backup

## 2. Routinen

| Feld | Details |
|---|---|
| Name | Pflicht |
| Beschreibung | Optional |
| Uhrzeiten | Eine oder mehrere pro Tag |
| Wiederholung | Täglich, bestimmte Wochentage, alle X Tage, einmalig |
| Orte | Keiner („überall“), einer oder mehrere; gilt an einem beliebigen davon |
| Nachhol-Zeitfenster | Pro Routine einstellbar, Standard 4 Stunden; alternativ Checkbox „bis Mitternacht“ |
| Vollbild-Alarm | An/aus pro Routine (Standard: aus) |
| Wiederholungs-Erinnerungen | Abstand (Standard 30 min) und Anzahl (Standard 3, max. 5) pro Routine |
| Priorität | Niedrig / Normal / Hoch |
| Checkliste | Unterpunkte; Routine ist erledigt, wenn alle abgehakt sind, oder direkt komplett abhaken |
| Tags | Gemeinsame Tags (alle Bereiche) |
| Ziel (optional) | z. B. „30 Tage Streak“ oder „100 × erledigt“ – für den Fortschritt verknüpfter Wünsche |
| Status | Aktiv, pausiert, archiviert; Statistik bleibt erhalten |

### Tracking

- Jede Fälligkeit endet als **Erledigt**, **Übersprungen** oder **Verpasst**.
- **Verpasst** wird automatisch gesetzt, wenn das Zeitfenster abläuft.
- Pausierte Tage zählen nicht als verpasst.

## 3. Erinnerungen (Pop-ups)

- Standard: Benachrichtigung mit hoher Priorität (Heads-up) und Aktionen **Erledigt · Später · Überspringen**.
- Optional pro Routine: **Vollbild-Alarm** (auch auf dem Sperrbildschirm).
- **Keine** Overlays über anderen Apps (`SYSTEM_ALERT_WINDOW` wird nicht verwendet).
- **Später**: Auswahl 10 min / 30 min / 1 h.
- **Ignoriert**: erneute Erinnerung im eingestellten Abstand, höchstens die eingestellte Anzahl (max. 5); danach bleibt die Benachrichtigung still stehen, bis das Zeitfenster abläuft.
- Sperrbildschirm: nur „Erinnerung fällig“; Aktionen erst nach Entsperren.

## 4. Orte

- Benannte Orte (z. B. „Zuhause“, „Arbeit“, „Gym“), verwaltet in den Einstellungen.
- **WLAN-Ort**: aktuelles WLAN übernehmen oder Name (SSID) eintippen; mehrere WLANs pro Ort.
- **GPS-Ort**: aktuellen Standort übernehmen oder Koordinaten eingeben; Radius einstellbar (50–500 m, Standard 150 m).
- Ortserkennung ohne Google Play Services (Android `LocationManager` / `ConnectivityManager`).

### Ortsgebundene Erinnerung

1. Zur fälligen Uhrzeit am Ort → Pop-up sofort.
2. Nicht am Ort → Eintrag wartet; Pop-up beim Ankommen, solange das Zeitfenster (bzw. „bis Mitternacht“) läuft.
3. Zeitfenster abgelaufen → **Verpasst**.

Ankommen erkennt die App beim Verbinden mit einem WLAN. Solange ein To-do auf einen Ort mit GPS-Bereich wartet, sieht sie zusätzlich alle 30 Minuten nach, ob du dort angekommen bist (in den Einstellungen abschaltbar).

## 5. To-dos

| Feld | Details |
|---|---|
| Titel | Pflicht |
| Beschreibung | Optional |
| Fälligkeit | Ohne / Datum / Datum + Uhrzeit |
| Wiederholung | Optional, nur mit Fälligkeit: täglich, bestimmte Wochentage, alle X Tage, monatlich, jährlich; nächster Termin nach Plan oder ab Erledigung |
| Ort | Optional; Erinnerung beim Ankommen, auch ohne Uhrzeit |
| Priorität | Niedrig / Normal / Hoch |
| Checkliste | Unterpunkte |
| Tags | Gemeinsame Tags |

- Erinnerungen nutzen dasselbe System wie Routinen.
- Erledigte To-dos wandern ins **Archiv** (wiederherstellen oder endgültig löschen).
- Ein **wiederkehrendes** To-do wandert abgehakt ebenso ins Archiv; zugleich erscheint das nächste mit der nächsten Fälligkeit.
- Überfällige To-dos stehen oben in der Heute-Übersicht.

## 6. Wunschliste / Ziele

| Feld | Details |
|---|---|
| Titel | Pflicht |
| Beschreibung | Optional |
| Tags | Gemeinsame Tags |
| Status | Offen → In Arbeit → Erledigt (mit Datum) |
| Zeitraum | Freitext (z. B. „2027“, „Sommer“), keine Erinnerung |

- **Mehrstufig**: beliebig tief verschachtelte Unterziele, eingerückt und auf-/zuklappbar.
- Verknüpfbar: **To-dos** und **Routinen** (Routinen mit optionalem Ziel, z. B. 30 Tage Streak).
- **Fortschritt**: automatisch aus erledigten Unterpunkten, manuell überschreibbar.
- Wunsch mit einem Tipp in verknüpfte To-dos umwandeln.
- **Rückblick**: Liste erfüllter Wünsche mit Datum.
- Bewusst **keine** Erinnerungen.

Beispiel:

```
🎯 Arabisch A2 erreichen                    [40 %]
 ├─ 🎯 A1 abschließen                        [✓]
 │   ├─ ☑ To-do: Lehrbuch Kapitel 1–6
 │   └─ ☑ To-do: A1-Probetest machen
 ├─ 🎯 Wortschatz 1000 Wörter               [60 %]
 │   └─ 🔁 Routine: täglich 20 min Vokabeln
 └─ ☐ To-do: A2-Prüfung anmelden
```

## 7. Notizen

### Tabs

- Notizen liegen in **Tabs** (eine Ebene); jede Notiz in genau einem Tab.
- Fester Tab **„Alle“** ganz vorne; **„Allgemein“** ist der Standard-Tab und nicht löschbar.
- Tabs anlegen, umbenennen, sortieren, löschen. Beim Löschen Rückfrage: Inhalt nach „Allgemein“ verschieben oder mitlöschen.

### Ordner

- In jedem Tab **beliebig tief verschachtelte Ordner**, auf-/zuklappbar, mit Pfad-Anzeige (z. B. „Arabisch › Grammatik › Verben“).
- „Verschieben nach…“ für Notizen und Ordner, auch zwischen Tabs.
- Beim Löschen Rückfrage: Inhalt eine Ebene höher verschieben oder mitlöschen.

### Reihenfolge innerhalb eines Ordners

1. 📌 Angepinnte Ordner
2. 📌 Angepinnte Notizen
3. 📁 Normale Ordner
4. Normale Notizen

Innerhalb jeder Gruppe gilt die pro Ordner gewählte Sortierung (Name / letzte Änderung / manuell).

### Inhalt

- **Markdown** mit Vorschau; Rendering ohne HTML und ohne ausführbare Links.
- Notizen anpinnen (auch in die Heute-Übersicht).
- Verknüpfung mit Zielen, To-dos und Routinen.
- **Keine Anhänge** in Version 1.

## 8. Übergreifend

- **Gemeinsame Tags** für alle Bereiche.
- **Suche** über alle Bereiche (Routinen, To-dos, Wünsche, Notizen).
- **Statistik** pro Routine und gesamt:
  - Aktueller und längster Streak
  - Kalender-Ansicht (erledigt / übersprungen / verpasst)
  - Erledigt-Quote für 7 / 30 / 90 Tage
  - Nur Tage, an denen die Routine fällig war, zählen.
  - **Übersprungen** unterbricht den Streak nicht, **Verpasst** schon.

## 9. Sicherheit

| Bedrohung | Maßnahme |
|---|---|
| Datenabfluss | **Keine `INTERNET`-Berechtigung**, keine Google-Dienste, keine Analytics, minimale Abhängigkeiten |
| Andere Apps | Keine exportierten Komponenten außer dem Launcher; `PendingIntent`s immutable und explizit; strikte Eingabeprüfung |
| Diebstahl / Speicher auslesen | Datenbank mit SQLCipher verschlüsselt, Schlüssel im Android Keystore; `allowBackup=false`, Datenextraktionsregeln schließen alles aus |
| Zugriff auf entsperrtes Handy | App-Sperre per Fingerabdruck mit Geräte-PIN als Ersatz; erneute Sperre nach 1 min im Hintergrund |
| Mitlesen | `FLAG_SECURE` (keine Screenshots, ausgeblendet in „Zuletzt verwendet“); Sperrbildschirm zeigt nur „Erinnerung fällig“ |
| Manipulierte Dateien | Backup-Import mit Authentifizierung (AES-GCM) und strenger Schemaprüfung; Markdown ohne HTML/Links |

Release-Builds mit R8 (Minify + Shrink), keine Debug-Features.

## 10. Backups

- **Backup-Passwort** wird einmal festgelegt; daraus abgeleiteter Schlüssel (Argon2id bzw. PBKDF2) wird im Android Keystore geschützt abgelegt.
- Verschlüsselung: AES-256-GCM.
- **Automatisch**: täglich nachts in einen vom Nutzer gewählten Ordner (Storage Access Framework); die letzten 7 bleiben erhalten.
- **Manuell**: Export und Import jederzeit; Import auf neuem Gerät mit dem Passwort.
- Hinweis in der Heute-Übersicht, wenn das letzte Backup älter als 30 Tage ist oder fehlgeschlagen ist.
- Hinweis an den Nutzer: Backups regelmäßig vom Handy wegkopieren (PC, USB-Stick).

## 11. Berechtigungen

Ausschließlich:

- `POST_NOTIFICATIONS`
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
- `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`
- `USE_FULL_SCREEN_INTENT`
- `VIBRATE` (Vollbild-Alarm vibriert wie ein Wecker)
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `RECEIVE_BOOT_COMPLETED` (Alarme nach Neustart wiederherstellen)
- `USE_BIOMETRIC`
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` (Vollbild-Alarm klingelt wie ein Wecker weiter, auch wenn Android bei benutztem Gerät nur die Benachrichtigung zeigt)
- `FOREGROUND_SERVICE_LOCATION` (nur falls für die Ortserkennung nötig)

- Abfrage **erst bei Bedarf**, jeweils mit deutscher Erklärung.
- **Statusseite „Funktioniert alles?“** in den Einstellungen: zeigt fehlende Berechtigungen, Samsung-Hinweis („Nie in Standby versetzen“), Testbutton für eine Probe-Erinnerung in 10 s.
- Warnbanner in der Heute-Übersicht bei fehlenden wichtigen Berechtigungen.

## 12. Lieferung

- GitHub Actions baut bei jedem Push eine **signierte Release-APK** (Artefakt zum Herunterladen).
- Signaturschlüssel liegt ausschließlich als **GitHub-Secret**, nie im Repo.
- Automatische Tests für die Logik (Wiederholungen, Zeitfenster, Streaks, Verschlüsselung).

### Etappen

| # | Inhalt |
|---|---|
| 1 | Grundgerüst & Sicherheit: Projekt, verschlüsselte DB, App-Sperre, keine Internet-Berechtigung, CI-Build, Tabs + Heute-Gerüst, Theme |
| 2 | Routinen & Erinnerungen: Routinen komplett, Pop-ups, Später, Wiederholungen, Erledigt/Übersprungen/Verpasst |
| 3 | Orte: WLAN- und GPS-Orte, Nachholen beim Ankommen, Zeitfenster, Statusseite |
| 4 | To-dos mit Orten, Erinnerungen und Archiv |
| 5 | Wunschliste / Ziele: mehrstufig, Fortschritt, Rückblick |
| 6 | Notizen: Tabs, Ordner, Markdown, Anpinnen |
| 7 | Statistik, Suche über alle Bereiche, Backups |

Nach jeder Etappe: installierbare APK.
