# EmilysHub – Spezifikation

Persönliche, vollständig offline arbeitende Android-App als zentrale Anlaufstelle für Routinen, Erinnerungen, To-dos, Ziele, Notizen, Haushaltsbuch und eine gesperrte Dokumentation zu Patienten.

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

Untere Leiste mit den Tabs **Heute · Routinen · To-dos · Notizen · Sonstiges**.
Startbildschirm ist **Heute**. **Sonstiges** ist eine Liste mit **Ziele** (§6; hieß bis v1 „Wunschliste“ und war bis Etappe 10 ein eigener Tab) und **Dokumentation** (§14). Beide öffnen im Tab: Die untere Leiste bleibt, der Zurück-Pfeil führt zur Liste. Die Ziele bleiben beim Wechsel des Tabs offen, die Dokumentation sperrt sich dabei (§14). Orte, Statusseite, Backups und Sicherheit liegen in den **Einstellungen**. Das **Haushaltsbuch** (§13) öffnet im Tab Notizen ein Knopf in der Mitte der oberen Leiste, direkt unter der Kamera: 1/4 der Bildschirmbreite, beschriftet „Haushaltsbuch“, bei zu wenig Platz „€“. In Heute öffnet es ein kleines „€“ links neben der Flamme.

### Heute-Übersicht

- Alle heute fälligen Routinen und To-dos, nach Uhrzeit sortiert, direkt abhakbar
- Einträge, die auf das Erreichen eines Ortes warten
- Überfällige To-dos oben
- Angepinnte Notizen
- Abschnitt **Demnächst**: To-dos, die schon vor ihrer Fälligkeit gezeigt werden (§5)
- **Heatmap** der letzten 6 Monate unter den Einträgen: je Tag ein Kästchen, je mehr Routinen und To-dos erledigt, desto kräftiger
- **Flamme** in der oberen Leiste mit den Tagen des Gesamt-Streaks (§8); grau, solange heute noch etwas offen und nichts erledigt ist; Tippen öffnet die Statistik
- **„€“** in der oberen Leiste links neben der Flamme: öffnet den laufenden Monat im Haushaltsbuch (§13)
- **„‹ Gestern“** über der Liste: der Vortag zum Nachtragen (unten)
- Warnbanner bei fehlenden Berechtigungen, fehlgeschlagenem oder veraltetem Backup

### Gestern nachtragen

- „‹ Gestern“ zeigt den Vortag, deutlich markiert („Gestern, Fr. 6. Nov. – nachtragen“), mit „Heute ›“ zurück; die Zurück-Taste führt ebenfalls zurück. Es geht nur einen Tag zurück.
- Gezeigt werden nur die Routinen und To-dos des Vortags, ohne Demnächst, angepinnte Notizen, Heatmap und Warnbanner.
- Verpasste, ausgelassene und noch offene Routinen lassen sich als erledigt abhaken, offene To-dos ebenso; der Haken lässt sich wieder entfernen. Überspringen und „Heute nicht“ gibt es dort nicht.
- Nachgetragenes zählt voll als erledigt am Vortag: für Streak, Flamme, Statistik und Heatmap, ohne Sondermarkierung. Ein To-do gilt als gestern erledigt; bei Wiederholung „ab Erledigung“ rechnet der nächste Termin ab gestern.
- Wechsel des Tabs oder ein neuer Start der App zeigt wieder den heutigen Tag.

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
- Am Vortag (§1, Gestern nachtragen) lassen sich verpasste, ausgelassene und offene Fälligkeiten nachträglich als **Erledigt** abhaken; ohne Haken sind sie wieder verpasst.

### Gekoppelte Routinen

- Gekoppelte Routinen bilden eine Gruppe; eine Routine gehört zu höchstens einer.
- Ist eine Routine der Gruppe an einem Tag erledigt, gelten die anderen an dem Tag als **übersprungen** („durch Laufen erledigt“); ihre Erinnerungen kommen nicht mehr. Rückgängig machen bringt sie zurück, solange ihr Zeitfenster läuft.
- Das gilt für alles, was an dem Tag noch offen oder schon **verpasst** ist, und für eine Routine, deren „Überspringen“ an dem Tag rückgängig gemacht wird: Sie gilt dann gleich als durch die erledigte übersprungen.
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
- Am Vortag (§1, Gestern nachtragen) abgehakt, gilt ein To-do als **gestern erledigt**.
- To-dos mit Vorlauf stehen in Heute unter **Demnächst**, nach Fälligkeit sortiert, bis sie fällig sind; abhaken geht dort sofort. „Demnächst“ erinnert nicht.

## 6. Ziele

Bis v1 hieß der Bereich „Wunschliste“; überall ist jetzt von Zielen die Rede. Erreichbar über Sonstiges (§1).

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

### Archiv

- Notizen und ganze Ordner lassen sich **archivieren**: Sie verschwinden aus Tabs, Ordnern, „Alle“, Heute, Suche und Verknüpfungen und verlieren ihren Pin.
- Das **Archiv** (Menü der Notizen) zeigt sie nur zum Lesen; „Wiederherstellen“ bringt sie an den alten Ort, sonst in den nächsten Ordner darüber bzw. nach oben; „Endgültig löschen“ mit Rückfrage.
- Löschen eines Ordners oder Tabs löscht nichts Archiviertes mit; es rückt höher bzw. nach „Allgemein“.

## 8. Übergreifend

- **Gemeinsame Tags** für alle Bereiche.
- **Suche** über alle Bereiche (Routinen, To-dos, Ziele, Notizen, Haushaltsbuch); die Dokumentation hat ihre eigene (§14).
- **Statistik** pro Routine und gesamt:
  - Aktueller und längster Streak
  - Kalender-Ansicht (erledigt / übersprungen / ausgelassen / verpasst)
  - Erledigt-Quote für 7 / 30 / 90 Tage
  - Nur Tage, an denen die Routine fällig war, zählen.
  - **Übersprungen** unterbricht den Streak nicht, **Verpasst** und **Ausgelassen** schon; Ausgelassen zählt in der Erledigt-Quote als nicht erledigt.
  - Durch eine gekoppelte Routine erledigte Fälligkeiten zählen wie Übersprungen.
  - Am Vortag Nachgetragenes zählt wie Erledigt.

## 9. Sicherheit

| Bedrohung | Maßnahme |
|---|---|
| Datenabfluss | **Keine `INTERNET`-Berechtigung**, keine Google-Dienste, keine Analytics, minimale Abhängigkeiten |
| Andere Apps | Keine exportierten Komponenten außer dem Launcher; `PendingIntent`s immutable und explizit; strikte Eingabeprüfung |
| Diebstahl / Speicher auslesen | Datenbank mit SQLCipher verschlüsselt, Schlüssel im Android Keystore; `allowBackup=false`, Datenextraktionsregeln schließen alles aus |
| Zugriff auf entsperrtes Handy | App-Sperre per Fingerabdruck mit Geräte-PIN als Ersatz; erneute Sperre nach 1 min im Hintergrund |
| Mitlesen | `FLAG_SECURE` (keine Screenshots, ausgeblendet in „Zuletzt verwendet“); Sperrbildschirm zeigt nur „Erinnerung fällig“, Namen nur von Einträgen, die es erlauben (Standard: aus) |
| Manipulierte Dateien | Backup-Import mit Authentifizierung (AES-GCM) und strenger Schemaprüfung; Markdown ohne HTML/Links |
| Gesundheitsdaten (Dokumentation, §14) | Eigene SQLCipher-Datenbank, deren Schlüssel nur Doku-Passwort oder Fingerabdruck freigeben, nicht der Schlüssel der übrigen App; Sperre beim Verlassen, im Hintergrund und nach 5 min ohne Eingabe; nicht in Suche, Heute und Benachrichtigungen; Tastatur ohne Lernen; Zwischenablage vertraulich und beim Sperren geleert; gelöschte Daten überschrieben |

Release-Builds mit R8 (Minify + Shrink), keine Debug-Features.

## 10. Backups

- **Backup-Passwort** wird einmal festgelegt; daraus abgeleiteter Schlüssel (Argon2id bzw. PBKDF2) wird im Android Keystore geschützt abgelegt.
- Verschlüsselung: AES-256-GCM.
- **Automatisch**: täglich nachts in einen vom Nutzer gewählten Ordner (Storage Access Framework); die letzten 7 nächtlichen bleiben erhalten, ältere löscht die App.
- **Manuell**: Export und Import jederzeit; Export in einen beliebigen Ordner, auch den der nächtlichen; von Hand gespeicherte Backups löscht die App nie. Import auf neuem Gerät mit dem Passwort.
- Hinweis in der Heute-Übersicht, wenn das letzte Backup älter als 30 Tage ist oder fehlgeschlagen ist.
- Hinweis an den Nutzer: Backups regelmäßig vom Handy wegkopieren (PC, USB-Stick).
- Gewählte Töne stehen im Backup nur als Wahl, die Tondateien selbst nicht.
- Die **Dokumentation** (§14) liegt im Backup in ihrer eigenen Verschlüsselung, auch im nächtlichen, das sie dafür nicht entsperrt. Mit dem Backup-Passwort allein ist sie nicht lesbar; einspielen braucht zusätzlich das Doku-Passwort, das beim Backup galt.

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
| 9 | Erweiterungen: Anfangssaldo und Saldo-Übertrag im Haushaltsbuch, Haushaltsbuch-Knopf in der Mitte der Notizen-Leiste, voraussichtlicher Endsaldo, Fixbuchungen mit Ende |
| 10 | Sonstiges mit Zielen und Dokumentation, „€“ und „Gestern nachtragen“ in Heute, Dokumentation: Sperre, Patienten, Termine, Tests, Backup |
| 11 | Dokumentation: Assessmentbögen (eingebaut, nach Unterkategorie) |

Nach jeder Etappe: installierbare APK.

## 13. Haushaltsbuch

Einnahmen und Ausgaben je Monat, mit Archiv. Erreichbar über den Knopf in der Mitte der oberen Leiste im Tab Notizen und über das „€“ in Heute (§1).

| Feld | Details |
|---|---|
| Art | Einnahme oder Ausgabe |
| Betrag | Euro mit Cent, größer als 0 |
| Datum | Pflicht, Standard heute |
| Kategorie | Pflicht; eigene Kategorien je Art |
| Beschreibung | Optional |

- **Kategorien**: zum Start Vorschläge (Ausgaben: Lebensmittel, Wohnen, Mobilität, Freizeit, Gesundheit, Kleidung, Sonstiges; Einnahmen: Gehalt, Sonstiges). Anlegen, umbenennen, löschen; beim Löschen Rückfrage, die Buchungen wandern nach „Sonstiges“ ihrer Art.
- **Fixbuchungen**: Vorlagen (z. B. Miete, Gehalt, Abos) mit Abstand monatlich, vierteljährlich, halbjährlich oder jährlich und einem Tag im Monat. Optional mit Ende: nach einer Anzahl Buchungen ab dem ersten Monat oder an einem Datum (einschließlich). An ihrem Tag automatisch eingetragen, nachgetragen, wenn die App an dem Tag nicht lief; im Monat änderbar und löschbar, ohne die Vorlage zu ändern.
- **Monatsansicht**: Einnahmen, Ausgaben, Saldo (immer Einnahmen − Ausgaben); Ausgaben je Kategorie als Balken und als Tortendiagramm; alle Buchungen nach Datum.
- **Anfangssaldo**: Jeder Monat kann einen haben, einen eigenen Wert am Monat, keine Buchung; Euro mit Cent, auch 0 oder negativ. Hat ein Monat einen, zeigt die Monatsansicht über den Summen den Anfangssaldo mit seiner Herkunft („aus Oktober übertragen“ oder „von Hand festgelegt“) und darunter den **Endsaldo** = Anfangssaldo + Einnahmen − Ausgaben. Der Anfangssaldo zählt nie als Einnahme: nicht in Summen, Diagrammen, Budgets, Archiv- und Jahressummen, nicht in Buchungsliste und Suche.
- **Voraussichtlicher Endsaldo**: nur im laufenden, offenen Monat und nur, solange darin noch Fixbuchungen kommen: Endsaldo (Anfangssaldo, keiner als 0 €, + Einnahmen − Ausgaben) samt den Fixbuchungen, Einnahmen wie Ausgaben, die bis zum Monatsende noch eingetragen werden.
- **Übertrag**: Ein offener Monat ohne Anfangssaldo bietet „Saldo aus Vormonat übertragen“ (aus einem offenen Vormonat mit Rückfrage, sein Endsaldo kann sich noch ändern) und „Anfangssaldo festlegen“. In offenen Monaten lässt er sich von Hand ändern und entfernen. Beim Abschließen fragt der Dialog „Abbrechen · Nur abschließen · Abschließen & übertragen“: Der Endsaldo geht als Anfangssaldo in den Folgemonat (nie in den Kalendermonat von heute) und ersetzt einen vorhandenen, den der Dialog nennt; in einen abgeschlossenen Folgemonat wird nicht übertragen. Ein Übertrag ist ein fester Wert und ändert sich nicht mit dem Vormonat.
- **Budget** je Ausgaben-Kategorie und Monat: Balken ab 80 % orange, darüber rot („12,50 € über Budget“); kurzer Hinweis beim Speichern einer Buchung, die es überschreitet. Keine Benachrichtigungen.
- **Monatsbudget**: In der Monatsansicht eines offenen Monats lässt sich das Budget einer Ausgaben-Kategorie nur für diesen Monat ändern: ein anderer Betrag, ein neues oder keins. Es geht dem monatlichen vor, sieht aber aus wie jedes Budget; „Wie sonst“ nimmt es zurück. Beim Abschließen hält der Monat es fest.
- **Monatsabschluss** von Hand: abgeschlossene Monate liegen im **Archiv**, nur zum Lesen, und lassen sich mit Rückfrage wieder öffnen. Mehrere Monate können gleichzeitig offen sein. Ein abgeschlossener Monat behält sein Budget von damals; sein Anfangssaldo ist nur zu lesen. Das Archiv zeigt je Monat auch den Endsaldo, wenn er einen Anfangssaldo hat.
- **Jahresübersicht**: Summen je Monat und je Kategorie über ein Jahr; dazu oben der Anfangssaldo des Januars und unten der Endsaldo des Dezembers, wo vorhanden.
- Buchungen kommen in die Backups und in die Suche.

## 14. Dokumentation

Persönliche Arbeitsnotizen zu Patienten, **keine** offizielle Behandlungsdokumentation (Patientenakte nach §630f BGB): frei änderbar und löschbar, ohne Aufbewahrungsfrist. Es sind Gesundheitsdaten (Art. 9 DSGVO), daher ist die Dokumentation eine Insel mit eigener Sperre. Erreichbar über Sonstiges (§1).

### Sperre & Verschlüsselung

- **Doku-Passwort** (mindestens 8 Zeichen): beim ersten Öffnen festgelegt, zweimal eingegeben, mit dem Hinweis, dass es sich nicht wiederherstellen lässt. **Fingerabdruck** optional als Abkürzung, das Passwort als Ersatz; die Geräte-PIN gilt nicht. Ein am Handy neu eingerichteter Fingerabdruck verlangt einmal das Passwort.
- Eigene, mit SQLCipher verschlüsselte Datenbank. Ihr Schlüssel lässt sich nur mit dem Doku-Passwort oder dem Fingerabdruck öffnen, nicht mit dem Schlüssel der übrigen App; ohne beides kommt niemand an die Daten.
- Sperrt sich **sofort** beim Verlassen (anderer Tab, zurück zu Sonstiges), wenn die App in den Hintergrund geht oder der Bildschirm ausgeht, und nach **5 Minuten** ohne Eingabe. Was gerade getippt wird, wird vorher gespeichert.
- Doku-Passwort ändern und Fingerabdruck an/aus im Menü der Dokumentation, nicht in den Einstellungen.

### Insel

- Nicht in der Suche über alle Bereiche, nicht in Heute, keine Pins, gemeinsamen Tags oder Verknüpfungen, keine Erinnerungen oder Benachrichtigungen, nicht in Statistik und Heatmap.
- Die Tastatur wird gebeten, in der Dokumentation keine Wörter zu lernen.
- Kopierter Text ist als vertraulich markiert (keine Vorschau) und wird beim Sperren aus der Zwischenablage gelöscht.
- Kein Export, auch nicht je Patient (entschieden am 08.11.2026); nur das Backup (§10) verlässt das Gerät, in der eigenen Verschlüsselung.

### Startseite

- Oben ein **Löschhinweis**, sobald Patienten seit über 12 Monaten abgeschlossen sind („3 Personen seit über 12 Monaten abgeschlossen – jetzt löschen?“); nur hier, keine Benachrichtigung
- Eigene **Suche** über Patienten, ihre Felder, Termin-Dokumentationen und Bemerkungen
- Die **aktiven Patienten**, zuletzt behandelte zuerst (umschaltbar auf Name), je mit dem letzten Termin; „+ Patient“
- Menü: Tests · Bögen · Termin-Vorlage · Archiv · Doku-Passwort ändern · Fingerabdruck

### Patienten

| Feld | Details |
|---|---|
| Name / Kürzel | Pflicht, freier Text |
| Geburtsjahr | Optional, nur das Jahr |
| Diagnose | Optional, Markdown |
| Anamnese | Optional, Markdown |
| Therapeutische Zielsetzung | Optional, Markdown |
| Sonstiges | Optional, Markdown |
| Status | Aktiv oder abgeschlossen |

- Die Seite eines Patienten hat vier Reiter: **Übersicht** (die Felder) · **Termine** · **Tests** · **Bögen**.
- **Abschließen**: ins Doku-Archiv, nur zum Lesen, wiederherstellbar.
- **Endgültig löschen** mit Rückfrage, mit allen Terminen und Messungen; die Datenbank überschreibt Gelöschtes. In älteren Backups steckt es noch: die nächtlichen fallen nach 7 Nächten weg, die von Hand gespeicherten bleiben, bis man sie löscht.

### Termine

- Datum (Pflicht, Standard heute), Uhrzeit optional und **eine** Dokumentation als Markdown
- Optionale **Termin-Vorlage**, zentral angelegt, füllt neue Termine vor; im Termin frei änderbar
- Im Reiter Termine die neuesten oben, mit Datum und der ersten Zeile als Vorschau
- Termine sind nur Daten, an denen dokumentiert wird: keine Erinnerungen.

### Tests

- **Zentral angelegt**. Ein Test hat einen oder mehrere **Werte**, optional eine **Summe** daraus.
- Je Wert: Name, Einheit (optional), erlaubter Bereich (optional), Nachkommastellen ja/nein, **Richtung** (höher oder niedriger ist besser); die Reihenfolge ist frei.
- Nur Zahlenwerte; je Messung eine optionale Bemerkung.
- Änderungen an einem Test gelten auch für seine alten Messungen (Hinweis mit ihrer Anzahl). Ein Wert, der schon gemessen wurde, wird beim Entfernen stillgelegt statt gelöscht. Ein Test lässt sich archivieren (für neue Messungen nicht mehr wählbar) oder mit allen Messungen endgültig löschen.
- **Messungen** immer an einem Termin, mit dessen Datum; mehrere Tests je Termin.
- **Entwicklung** im Reiter Tests: je gemessenem Test eine Karte mit dem letzten Wert (bzw. der Summe) und der Veränderung zur ersten Messung, besser oder schlechter nach der Richtung. Der Verlauf zeigt je Wert ein Liniendiagramm über die Zeit und darunter alle Messungen als Tabelle; eine Zeile öffnet ihren Termin. Kein Vergleich zwischen Patienten.

### Assessmentbögen

Abgestimmt im Interview vom 08.11.2026.

- **Eingebaut, nicht änderbar**: die Bögen aus der Kategorie „Scores“ der Downloads von DIGOTOR (digotor.info), übernommen wird ihr Inhalt (Items, Antworten, Punkte, Auswertung), nicht die PDFs. Keine eigenen Bögen; ein neuer Bogen kommt mit einem App-Update. Weggelassen werden Fachtexte (Artikel, Schlafhygiene, Nachbehandlung und Expertenkonsens Spinalstenose), Varianten (Word, mit DIGOTOR-Logo) und der englische Anamnesebogen.
- Der Wortlaut der Bögen steht im Quellcode; das Repo ist deshalb **privat**.
- **Bausteine**: Antwort-Items mit festen Antworten und Punkten; ohne Punkte Ja/Nein, Skala (z. B. 0–10), Ankreuzliste, Freitext und **Körperregionen** zum Ankreuzen mit Freitext (statt „bitte einzeichnen“).
- **Auswertung wie im Original**: Summe, Prozent, Teilskalen, umgekehrt gepolte Items, Regeln für fehlende Antworten und die Einstufung als Text; live im Bogen. Reicht das Beantwortete nach diesen Regeln nicht, „Ergebnis: –“. Die Richtung (höher oder niedriger ist besser) je Ergebnis kommt aus dem Bogen.
- **Am Termin**: „+ Bogen“ unter den Tests; ein Bogen hat das Datum seines Termins, ein Termin kann mehrere haben. Ändern, mit Rückfrage löschen; ein gelöschter Termin nimmt seine Bögen mit. Beim abgeschlossenen Patienten nur zum Lesen. Ausgefüllt wird nur in der eigenen Hand, kein Patientenmodus zum Weiterreichen des Handys.
- **Auswahl** bei „+ Bogen“: nach Unterkategorie wie bei DIGOTOR, Suche nach Name und Abkürzung, oben „Zuletzt benutzt“ (die letzten 5). Ein Bogen in mehreren Kategorien ist derselbe Bogen. Unter **Bögen** im Menü lassen sich Bögen ausblenden.
- **Reiter Bögen** beim Patienten: je Bogen mit Ergebnis eine Karte wie bei den Tests (letztes Ergebnis, Veränderung zur ersten Ausfüllung, besser oder schlechter); der Verlauf mit einem Liniendiagramm je Ergebnis (bei Teilskalen je Teilskala) und einer Tabelle, eine Zeile öffnet den Bogen am Termin. Darunter die Bögen ohne Ergebnis nach Datum.
- Die Doku-Suche findet die Freitexte der Bögen; Bögen kommen ins Backup; es gelten die Regeln der Insel.
