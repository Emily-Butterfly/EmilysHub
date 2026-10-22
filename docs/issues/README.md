# Slices

Vertikale Slices aus [SPEC.md](../../SPEC.md). Jede Datei entspricht einem GitHub-Issue mit derselben Nummer; Sammel-Issue: #29 **EmilysHub v1**.

## Etappe 1 – Grundgerüst & Sicherheit

| # | Slice | Art | Blockiert durch |
|---|---|---|---|
| 01 | [Projektgerüst](01-projektgeruest.md) | 🤖 | – |
| 02 | [Signierte Release-APK](02-release-signierung.md) | 🙋 | 01 |
| 03 | [Verschlüsselte Datenbank](03-verschluesselte-datenbank.md) | 🤖 | 01 |
| 04 | [App-Sperre & Design-Umschalter](04-app-sperre.md) | 🤖 | 03 |

## Etappe 2 – Routinen & Erinnerungen

| # | Slice | Art | Blockiert durch |
|---|---|---|---|
| 05 | [Einfache Routine anlegen & abhaken](05-einfache-routine.md) | 🤖 | 03 |
| 06 | [Wiederholungsregeln & mehrere Uhrzeiten](06-wiederholungsregeln.md) | 🤖 | 05 |
| 07 | [Erinnerungs-Pop-up zur Uhrzeit](07-erinnerungs-popup.md) | 🤖 | 05 |
| 08 | [„Später“ & Wiederholungs-Erinnerungen](08-spaeter-und-nachfassen.md) | 🤖 | 07 |
| 09 | [Vollbild-Alarm pro Routine](09-vollbild-alarm.md) | 🤖 | 07 |
| 10 | [Zeitfenster & automatisch „verpasst“](10-zeitfenster-und-verpasst.md) | 🤖 | 07 |
| 11 | [Priorität, Checkliste, Pausieren & Archivieren](11-routinen-extras.md) | 🤖 | 05 |
| 12 | [Gemeinsame Tags](12-gemeinsame-tags.md) | 🤖 | 05 |

## Etappe 3 – Orte

| # | Slice | Art | Blockiert durch |
|---|---|---|---|
| 13 | [WLAN-Orte verwalten](13-wlan-orte.md) | 🤖 | 03 |
| 14 | [Ortsgebundene Routinen (WLAN)](14-ortsgebundene-routinen.md) | 🤖 | 10, 13 |
| 15 | [GPS-Orte mit Ankunftserkennung](15-gps-orte.md) | 🤖 | 14 |
| 16 | [Statusseite „Funktioniert alles?“](16-statusseite.md) | 🤖 | 07 |

## Etappe 4 – To-dos

| # | Slice | Art | Blockiert durch |
|---|---|---|---|
| 17 | [To-dos](17-todos.md) | 🤖 | 11, 12 |
| 18 | [To-do-Erinnerungen zur Uhrzeit & am Ort](18-todo-erinnerungen.md) | 🤖 | 15, 17 |

## Etappe 5 – Wunschliste / Ziele

| # | Slice | Art | Blockiert durch |
|---|---|---|---|
| 19 | [Wunschliste](19-wunschliste.md) | 🤖 | 12 |
| 20 | [Mehrstufige Ziele mit To-dos & Fortschritt](20-mehrstufige-ziele.md) | 🤖 | 17, 19 |
| 21 | [Routinen an Ziele hängen](21-routinen-an-zielen.md) | 🤖 | 20, 25 |

## Etappe 6 – Notizen

| # | Slice | Art | Blockiert durch |
|---|---|---|---|
| 22 | [Notiz-Tabs & Markdown-Notizen](22-notiz-tabs-markdown.md) | 🤖 | 03 |
| 23 | [Verschachtelte Notiz-Ordner](23-notiz-ordner.md) | 🤖 | 22 |
| 24 | [Anpinnen & Verknüpfen von Notizen](24-anpinnen-und-verknuepfen.md) | 🤖 | 20, 23 |

## Etappe 7 – Statistik, Suche & Backups

| # | Slice | Art | Blockiert durch |
|---|---|---|---|
| 25 | [Statistik: Streak, Kalender & Quoten](25-statistik.md) | 🤖 | 10, 11 |
| 26 | [Suche über alle Bereiche](26-suche.md) | 🤖 | 17, 19, 22 |
| 27 | [Verschlüsseltes Backup: Export & Import](27-backup-manuell.md) | 🤖 | 03 |
| 28 | [Automatisches tägliches Backup](28-backup-automatisch.md) | 🤖 | 27 |
