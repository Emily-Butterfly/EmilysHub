# Slices

Vertikale Slices aus [SPEC.md](../../SPEC.md). Jede Datei entspricht einem GitHub-Issue mit derselben Nummer; Sammel-Issues: #29 **EmilysHub v1** (Etappen 1–7) und #49 **EmilysHub v1.1** (Etappe 8).

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
| 30 | [Wiederkehrende To-dos](30-wiederkehrende-todos.md) | 🤖 | 17, 18, 20 |

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

## Etappe 8 – Erweiterungen (v1.1)

Abgestimmt im Interview vom 31.10.2026; Sammel-Issue #49. Die Reihenfolge der Nummern ist die vorgeschlagene Reihenfolge fürs Umsetzen.

| # | Slice | Art | Blockiert durch |
|---|---|---|---|
| 31 | [App-Icon: „E“ im Haus](31-icon-e-im-haus.md) | 🤖 | – |
| 32 | [„Wunschliste“ heißt „Ziele“](32-ziele-statt-wunschliste.md) | 🤖 | – |
| 33 | [Routine für heute auslassen („Heute nicht“)](33-heute-nicht.md) | 🤖 | – |
| 34 | [Wiederholungen begrenzen](34-wiederholungen-begrenzen.md) | 🤖 | – |
| 35 | [To-dos vorher in Heute („Demnächst“)](35-todos-vorher-in-heute.md) | 🤖 | – |
| 36 | [Vollbild-Wecker für To-dos](36-todo-wecker.md) | 🤖 | – |
| 37 | [Eigene Weckzeit für To-dos](37-todo-weckzeit.md) | 🤖 | 36 |
| 38 | [Name auf dem Sperrbildschirm](38-name-auf-sperrbildschirm.md) | 🤖 | 36 |
| 39 | [Klingelton je Routine und To-do](39-klingelton.md) | 🤖 | 36 |
| 40 | [Eigene Audiodatei als Ton](40-eigene-tondatei.md) | 🤖 | 39 |
| 41 | [Routinen koppeln: eine reicht](41-routinen-koppeln.md) | 🤖 | 33 |
| 42 | [Streak-Flamme in Heute](42-streak-flamme.md) | 🤖 | – |
| 43 | [Heatmap in Heute](43-heatmap.md) | 🤖 | – |
| 44 | [Haushaltsbuch: Buchungen, Kategorien, Monatsansicht](44-haushaltsbuch.md) | 🤖 | – |
| 45 | [Haushaltsbuch: Fixbuchungen](45-haushaltsbuch-fixbuchungen.md) | 🤖 | 44 |
| 46 | [Haushaltsbuch: Monatsabschluss & Archiv](46-haushaltsbuch-abschluss-archiv.md) | 🤖 | 44, 45 |
| 47 | [Haushaltsbuch: Budget je Kategorie](47-haushaltsbuch-budget.md) | 🤖 | 44, 46 |
| 48 | [Haushaltsbuch: Jahresübersicht](48-haushaltsbuch-jahresuebersicht.md) | 🤖 | 44 |
