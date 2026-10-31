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
| Icon | Haus mit ausgestanztem „E“ (schlichte Blockform), adaptives Icon mit einfarbiger Variante, selbst erstellt (keine externen Grafiken); das Symbol in der Statusleiste zeigt dasselbe |
| Farben | Festes Farbschema: hell = Flieder, dunkel = Lila (kein Material You) |
| Hell/Dunkel | Folgt dem System, in der App umstellbar |

### Navigation

Untere Leiste mit den Tabs **Heute · Routinen · To-dos · Ziele · Notizen** (der Tab „Ziele“ hieß bis v1 „Wunschliste“).
Startbildschirm ist **Heute**. Orte, Statusseite, Backups und Sicherheit liegen in den **Einstellungen**. Das **Haushaltsbuch** (§13) öffnet ein Knopf oben im Tab Notizen.

### Heute-Übersicht

- Alle heute fälligen Routinen und To-dos, nach Uhrzeit sortiert, direkt abhakbar
- Einträge, die auf das Erreichen eines Ortes warten
- Überfällige To-dos oben
- Angepinnte Notizen
- Abschnitt **Demnächst**: To-dos, die schon vor ihrer Fälligkeit gezeigt werden (§5)
- **Heatmap** der letzten 6 Monate unter den Einträgen: je Tag ein Kästchen, je mehr Routinen und To-dos erledigt, desto kräftiger
- **Flamme** in der oberen Leiste mit den Tagen des Gesamt-Streaks (§8); grau, solange heute noch etwas offen und nichts erledigt ist; Tippen öffnet die Statistik
- Warnbanner bei fehlenden Berechtigungen, fehlgeschlagenem oder veraltetem Backup

## 2. Routinen

| Feld | Details |
|---|---|
| Name | Pflicht |
| Beschreibung | Optional |
| Uhrzeiten | Eine oder mehrere pro Tag |
| Wiederholung | Täglich, bestimmte Wochentage, alle X Tage, einmalig; außer bei „einmalig“ endet sie nie oder an einem Datum (einschließlich), danach wandert die Routine ins Archiv |
| Orte | Keiner („überall“), einer oder mehrere; gilt an einem beliebigen davon |
| Nachhol-Zeitfenster | Pro Routine einstellbar, Standard 4 Stunden; alternativ Checkbox „bis Mitternacht“ |
| Vollbild-Alarm | An/aus pro Routine (Standard: aus) |
| Ton | Ton der Erinnerung und des Vollbild-Alarms (§3); ohne Wahl der Standard aus den Einstellungen |
| Name auf dem Sperrbildschirm | An/aus pro Routine (Standard: aus), §3 |
| Kopplung | Mit anderen Routinen zu einer Gruppe, in der eine erledigte für den Tag reicht; nur Routinen mit genau einer Uhrzeit am Tag |
| Wiederholungs-Erinnerungen | Abstand (Standard 30 min) und Anzahl (Standard 3, max. 5) pro Routine |
| Priorität | Niedrig / Normal / Hoch |
| Checkliste | Unterpunkte; Routine ist erledigt, wenn alle abgehakt sind, oder direkt komplett abhaken |
| Tags | Gemeinsame Tags (alle Bereiche) |
| Routinen-Ziel (optional) | z. B. „30 Tage Streak“ oder „100 × erledigt“ – für den Fortschritt verknüpfter Ziele (§6) |
| Status | Aktiv, pausiert, archiviert; Statistik bleibt erhalten |

### Tracking

- Jede Fälligkeit endet als **Erledigt**, **Übersprungen**, **Ausgelassen** oder **Verpasst**.
- **Verpasst** wird automatisch gesetzt, wenn das Zeitfenster abläuft.
- **Ausgelassen**: mit „Heute nicht“ bewusst für den Tag fallen gelassen, und zwar alle restlichen Fälligkeiten der Routine von heute. Es kommen keine Erinnerungen mehr; zählt wie Verpasst, wird aber eigens angezeigt. In Heute rückgängig zu machen, solange der Tag läuft.
- Pausierte Tage zählen nicht als verpasst.

### Gekoppelte Routinen

- Gekoppelte Routinen bilden eine Gruppe; eine Routine gehört zu höchstens einer.
- Ist eine Routine der Gruppe an einem Tag erledigt, gelten die anderen an dem Tag als **übersprungen** („durch Laufen erledigt“); ihre Erinnerungen kommen nicht mehr. Rückgängig machen bringt sie zurück, solange ihr Zeitfenster läuft.
- Überspringen und „Heute nicht“ betreffen nur die eine Routine, außer im gemeinsamen Alarm (§3).

## 3. Erinnerungen (Pop-ups)

- Standard: Benachrichtigung mit hoher Priorität (Heads-up) und Aktionen **Erledigt · Später · Überspringen**.
- Optional pro Routine und To-do: **Vollbild-Alarm** (auch auf dem Sperrbildschirm). Bei Routinen bietet er entsperrt **Erledigt · Später · Überspringen · Heute nicht**.
- **Keine** Overlays über anderen Apps (`SYSTEM_ALERT_WINDOW` wird nicht verwendet).
- **Später**: Auswahl 10 min / 30 min / 1 h. In der Benachrichtigung einer Routine 10 min / 30 min / **Heute nicht** (§2), da Android dort höchstens drei Knöpfe zeigt; der Vollbild-Alarm bietet alle drei Zeiten.
- **Ignoriert**: erneute Erinnerung im eingestellten Abstand, höchstens die eingestellte Anzahl (max. 5); danach bleibt die Benachrichtigung still stehen, bis das Zeitfenster abläuft.
- Sperrbildschirm: nur „Erinnerung fällig“, außer der Eintrag erlaubt seinen Namen dort (dann Name und Uhrzeit, im Vollbild-Alarm wie in der Benachrichtigung). Aktionen in jedem Fall erst nach Entsperren.
- **Ton**: je Routine und To-do ein Ton; der Vollbild-Alarm spielt ihn in Schleife, die Benachrichtigung einmal. Zur Wahl stehen die Töne des Handys und eigene Audiodateien, die die App dafür in die Töne des Handys kopiert. In den Einstellungen je ein Standard für Wecker und für Benachrichtigungen; fehlt ein Ton, gilt der Standard. Benachrichtigungen mit eigenem Ton laufen über einen eigenen Kanal je Ton.
- **Gemeinsamer Alarm**: Sind gekoppelte Routinen (§2) zur selben Uhrzeit fällig, kommt eine Erinnerung für alle. Der Vollbild-Alarm hat einen „Erledigt“-Knopf je Routine; „Später“, „Überspringen“ und „Heute nicht“ gelten für alle zusammen. Die Benachrichtigung bietet „Erledigt · Später · Überspringen“ und fragt nach „Erledigt“, welche es war.

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
| Wiederholung | Optional, nur mit Fälligkeit: täglich, bestimmte Wochentage, alle X Tage, monatlich, jährlich; nächster Termin nach Plan oder ab Erledigung; endet nie, nach N Mal (N zählt die To-dos der Reihe) oder an einem Datum (einschließlich) |
| In Heute zeigen | Am Fälligkeitstag oder 1–30 Tage vorher (Abschnitt „Demnächst“), nur mit Fälligkeit |
| Vollbild-Alarm | An/aus pro To-do (Standard: aus); klingelt, wo die Erinnerung kommt: zur Uhrzeit, nach „Später“ und beim Ankommen an einem Ort |
| Weckzeit | Optional: N Tage vor der Fälligkeit um eine Uhrzeit (wandert bei Wiederholungen mit) oder ein fester Zeitpunkt (auch ohne Fälligkeit, gilt nur für dieses To-do); zur Fälligkeit kommt die Erinnerung trotzdem |
| Ton | Wie bei Routinen (§3) |
| Name auf dem Sperrbildschirm | An/aus pro To-do (Standard: aus), §3 |
| Ort | Optional; Erinnerung beim Ankommen, auch ohne Uhrzeit |
| Priorität | Niedrig / Normal / Hoch |
| Checkliste | Unterpunkte |
| Tags | Gemeinsame Tags |

- Erinnerungen nutzen dasselbe System wie Routinen.
- Erledigte To-dos wandern ins **Archiv** (wiederherstellen oder endgültig löschen).
- Ein **wiederkehrendes** To-do wandert abgehakt ebenso ins Archiv; zugleich erscheint das nächste mit der nächsten Fälligkeit.
- Überfällige To-dos stehen oben in der Heute-Übersicht.
- To-dos mit Vorlauf stehen in Heute unter **Demnächst**, nach Fälligkeit sortiert, bis sie fällig sind; abhaken geht dort sofort. „Demnächst“ erinnert nicht.

## 6. Ziele

Bis v1 hieß der Bereich „Wunschliste“; überall ist jetzt von Zielen die Rede.

| Feld | Details |
|---|---|
| Titel | Pflicht |
| Beschreibung | Optional |
| Tags | Gemeinsame Tags |
| Status | Offen → In Arbeit → Erledigt (mit Datum) |
| Zeitraum | Freitext (z. B. „2027“, „Sommer“), keine Erinnerung |

- **Mehrstufig**: beliebig tief verschachtelte Unterziele, eingerückt und auf-/zuklappbar.
- Verknüpfbar: **To-dos** und **Routinen** (Routinen mit optionalem Routinen-Ziel, z. B. 30 Tage Streak).
- **Fortschritt**: automatisch aus erledigten Unterpunkten, manuell überschreibbar.
- Ziel mit einem Tipp in verknüpfte To-dos umwandeln.
- **Rückblick**: Liste erreichter Ziele mit Datum.
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
- **Suche** über alle Bereiche (Routinen, To-dos, Ziele, Notizen, Haushaltsbuch).
- **Statistik** pro Routine und gesamt:
  - Aktueller und längster Streak
  - Kalender-Ansicht (erledigt / übersprungen / ausgelassen / verpasst)
  - Erledigt-Quote für 7 / 30 / 90 Tage
  - Nur Tage, an denen die Routine fällig war, zählen.
  - **Übersprungen** unterbricht den Streak nicht, **Verpasst** und **Ausgelassen** schon; Ausgelassen zählt in der Erledigt-Quote als nicht erledigt.
  - Durch eine gekoppelte Routine erledigte Fälligkeiten zählen wie Übersprungen.

## 9. Sicherheit

| Bedrohung | Maßnahme |
|---|---|
| Datenabfluss | **Keine `INTERNET`-Berechtigung**, keine Google-Dienste, keine Analytics, minimale Abhängigkeiten |
| Andere Apps | Keine exportierten Komponenten außer dem Launcher; `PendingIntent`s immutable und explizit; strikte Eingabeprüfung |
| Diebstahl / Speicher auslesen | Datenbank mit SQLCipher verschlüsselt, Schlüssel im Android Keystore; `allowBackup=false`, Datenextraktionsregeln schließen alles aus |
| Zugriff auf entsperrtes Handy | App-Sperre per Fingerabdruck mit Geräte-PIN als Ersatz; erneute Sperre nach 1 min im Hintergrund |
| Mitlesen | `FLAG_SECURE` (keine Screenshots, ausgeblendet in „Zuletzt verwendet“); Sperrbildschirm zeigt nur „Erinnerung fällig“, Namen nur von Einträgen, die es erlauben (Standard: aus) |
| Manipulierte Dateien | Backup-Import mit Authentifizierung (AES-GCM) und strenger Schemaprüfung; Markdown ohne HTML/Links |

Release-Builds mit R8 (Minify + Shrink), keine Debug-Features.

## 10. Backups

- **Backup-Passwort** wird einmal festgelegt; daraus abgeleiteter Schlüssel (Argon2id bzw. PBKDF2) wird im Android Keystore geschützt abgelegt.
- Verschlüsselung: AES-256-GCM.
- **Automatisch**: täglich nachts in einen vom Nutzer gewählten Ordner (Storage Access Framework); die letzten 7 bleiben erhalten.
- **Manuell**: Export und Import jederzeit; Import auf neuem Gerät mit dem Passwort.
- Hinweis in der Heute-Übersicht, wenn das letzte Backup älter als 30 Tage ist oder fehlgeschlagen ist.
- Hinweis an den Nutzer: Backups regelmäßig vom Handy wegkopieren (PC, USB-Stick).
- Gewählte Töne stehen im Backup nur als Wahl, die Tondateien selbst nicht.

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
| 8 | Erweiterungen (v1.1): Icon, Ziele statt Wunschliste, „Heute nicht“, Wiederholungs-Ende, „Demnächst“, Wecker, Weckzeit und Töne für To-dos, Name auf dem Sperrbildschirm, gekoppelte Routinen, Streak-Flamme, Heatmap, Haushaltsbuch |

Nach jeder Etappe: installierbare APK.

## 13. Haushaltsbuch

Einnahmen und Ausgaben je Monat, mit Archiv. Erreichbar über einen Knopf oben im Tab Notizen.

| Feld | Details |
|---|---|
| Art | Einnahme oder Ausgabe |
| Betrag | Euro mit Cent, größer als 0 |
| Datum | Pflicht, Standard heute |
| Kategorie | Pflicht; eigene Kategorien je Art |
| Beschreibung | Optional |

- **Kategorien**: zum Start Vorschläge (Ausgaben: Lebensmittel, Wohnen, Mobilität, Freizeit, Gesundheit, Kleidung, Sonstiges; Einnahmen: Gehalt, Sonstiges). Anlegen, umbenennen, löschen; beim Löschen Rückfrage, die Buchungen wandern nach „Sonstiges“ ihrer Art.
- **Fixbuchungen**: Vorlagen (z. B. Miete, Gehalt, Abos) mit Abstand monatlich, vierteljährlich, halbjährlich oder jährlich und einem Tag im Monat. An ihrem Tag automatisch eingetragen, nachgetragen, wenn die App an dem Tag nicht lief; im Monat änderbar und löschbar, ohne die Vorlage zu ändern.
- **Monatsansicht**: Einnahmen, Ausgaben, Saldo; Ausgaben je Kategorie als Balken und als Tortendiagramm; alle Buchungen nach Datum.
- **Budget** je Ausgaben-Kategorie und Monat: Balken ab 80 % orange, darüber rot („12,50 € über Budget“); kurzer Hinweis beim Speichern einer Buchung, die es überschreitet. Keine Benachrichtigungen.
- **Monatsabschluss** von Hand: abgeschlossene Monate liegen im **Archiv**, nur zum Lesen, und lassen sich mit Rückfrage wieder öffnen. Mehrere Monate können gleichzeitig offen sein. Ein abgeschlossener Monat behält sein Budget von damals.
- **Jahresübersicht**: Summen je Monat und je Kategorie über ein Jahr.
- Buchungen kommen in die Backups und in die Suche.
