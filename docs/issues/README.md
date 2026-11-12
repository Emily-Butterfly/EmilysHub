# Slices

Vertikale Slices aus [SPEC.md](../../SPEC.md). Jede Datei entspricht einem GitHub-Issue mit derselben Nummer; Sammel-Issues: #29 **EmilysHub v1** (Etappen 1–7), #49 **EmilysHub v1.1** (Etappe 8), #64 **Etappe 10**, #77 **Etappe 11**, #85 **Etappe 12** und #86 **Etappe 13**; Etappe 9 hat keins.

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

## Etappe 9 – Erweiterungen

Abgestimmt im Interview vom 02.11.2026.

| # | Slice | Art | Blockiert durch |
|---|---|---|---|
| 50 | [Haushaltsbuch: Saldo übertragen, Anfangssaldo & Knopf in Notizen](50-haushaltsbuch-saldo-uebertrag.md) | 🤖 | – |
| 51 | [Haushaltsbuch: voraussichtlicher Endsaldo](51-haushaltsbuch-voraussichtlicher-endsaldo.md) | 🤖 | 50 |
| 52 | [Haushaltsbuch: Fixbuchungen mit Ende](52-haushaltsbuch-fixbuchungen-mit-ende.md) | 🤖 | – |
| 53 | [Haushaltsbuch: Budget für einzelne Monate](53-haushaltsbuch-monatsbudget.md) | 🤖 | – |
| 54 | [Notizen: Archiv für Notizen und Ordner](54-notizen-archiv.md) | 🤖 | – |

## Etappe 10 – Sonstiges, Heute & Dokumentation

Abgestimmt im Interview vom 07.11.2026; Sammel-Issue #64. #55–#57 hängen nicht voneinander ab; die Dokumentation baut aufeinander auf.

| # | Slice | Art | Blockiert durch |
|---|---|---|---|
| 55 | [Sonstiges: Ziele wandern unter „Sonstiges“](55-sonstiges-mit-zielen.md) | 🤖 | – |
| 56 | [Heute: „€“ öffnet das Haushaltsbuch](56-heute-haushaltsbuch-knopf.md) | 🤖 | – |
| 57 | [Heute: Gestern nachtragen](57-heute-gestern-nachtragen.md) | 🤖 | – |
| 58 | [Dokumentation: eigener verschlüsselter Bereich mit Passwort & Fingerabdruck](58-doku-sperre.md) | 🤖 | 55 |
| 59 | [Dokumentation: Patienten, Archiv & Löschhinweis](59-doku-patienten.md) | 🤖 | 58 |
| 60 | [Dokumentation: Termine mit Dokumentation & Vorlage](60-doku-termine.md) | 🤖 | 59 |
| 61 | [Dokumentation: Tests zentral anlegen](61-doku-tests.md) | 🤖 | 58 |
| 62 | [Dokumentation: Testwerte am Termin & Verlauf](62-doku-messungen-und-verlauf.md) | 🤖 | 60, 61 |
| 63 | [Dokumentation im Backup, doppelt verschlüsselt](63-doku-backup.md) | 🤖 | 58 |

Die zurückgestellten **Assessmentbögen** wurden im Interview vom 08.11.2026 geklärt (Etappe 11); einen **Export** je Patient gibt es nicht.

## Etappe 11 – Dokumentation: Assessmentbögen

Abgestimmt im Interview vom 08.11.2026; Sammel-Issue #77. #66 braucht #65, weil sonst der Wortlaut der Bögen öffentlich gepusht würde. #67–#76 hängen nur an #66, nicht voneinander.

| # | Slice | Art | Blockiert durch |
|---|---|---|---|
| 65 | [Repo privat schalten](65-repo-privat.md) | 🙋 | – |
| 66 | [Bögen: Grundgerüst, Anamnesebogen & PHQ-9](66-boegen-grundgeruest.md) | 🤖 | 65 |
| 67 | [Bögen: Wirbelsäule](67-boegen-wirbelsaeule.md) | 🤖 | 66 |
| 68 | [Bögen: Untere Extremität – Knie & Hüfte](68-boegen-knie-huefte.md) | 🤖 | 66 |
| 69 | [Bögen: Untere Extremität – Fuß, Sprunggelenk & Übrige](69-boegen-fuss-sprunggelenk.md) | 🤖 | 66 |
| 70 | [Bögen: Obere Extremität](70-boegen-obere-extremitaet.md) | 🤖 | 66 |
| 71 | [Bögen: Schmerz](71-boegen-schmerz.md) | 🤖 | 66 |
| 72 | [Bögen: Psychosoziales](72-boegen-psychosoziales.md) | 🤖 | 66 |
| 73 | [Bögen: Kiefer / CMD](73-boegen-kiefer-cmd.md) | 🤖 | 66 |
| 74 | [Bögen: Neurologie](74-boegen-neurologie.md) | 🤖 | 66 |
| 75 | [Bögen: Schlafverhalten](75-boegen-schlaf.md) | 🤖 | 66 |
| 76 | [Bögen: Verschiedenes](76-boegen-verschiedenes.md) | 🤖 | 66 |

## Etappe 12 – Budget-Rechner, Abos & Sprachen

Abgestimmt im Interview vom 11.11.2026; Sammel-Issue #85. Gebaut wird in der Reihenfolge der Tabelle. #78, #79 und #81 hängen von nichts ab.

| # | Slice | Art | Blockiert durch |
|---|---|---|---|
| 78 | [Haushaltsbuch: Gesamtbudget & Budget-Rechner](78-haushaltsbuch-gesamtbudget-budget-rechner.md) | 🤖 | – |
| 79 | [Haushaltsbuch: Abos mit Kündigungsfrist](79-haushaltsbuch-abos.md) | 🤖 | – |
| 80 | [Abos: Erinnerung zum Kündigen & „Demnächst“ in Heute](80-abos-erinnerung.md) | 🤖 | 79 |
| 81 | [Sprachen: Sessions, Sprachen, Lehrer & Auswertung](81-sprachen-sessions.md) | 🤖 | – |
| 82 | [Sprachen: Themen & Prüfungen](82-sprachen-themen-pruefungen.md) | 🤖 | 81 |
| 83 | [Sprachen: Session-Notizen importieren – Textdatei, Foto, PDF](83-sprachen-import-anhaenge.md) | 🤖 | 81 |
| 84 | [Sprachen: Suche & Prüfungen an Zielen](84-sprachen-suche-ziele.md) | 🤖 | 82 |

## Etappe 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer

Aus dem Gutachten vom 12.11.2026; Sammel-Issue #86. Die Reihenfolge und die Entscheidungen zu #95, #96, #98, #104 und #109 sind vom 12.11.2026; die Einzelheiten der Slices sind nicht in einem Interview abgestimmt. Gebaut wird in der Reihenfolge der Tabelle. #91 und #92 sind der Rest von „UX 1“ und können auch später gebaut werden; #108 ist dran, sobald Heute merklich zäh wird.

| # | Slice | Art | Blockiert durch |
|---|---|---|---|
| 87 | [Editoren: Rückfrage statt stillem Verwerfen](87-editoren-rueckfrage.md) | 🤖 | – |
| 88 | [Notizen & Sessions: beim Verlassen von selbst speichern](88-notizen-sessions-selbstspeichern.md) | 🤖 | 87 |
| 89 | [Backups: ans Auslagern erinnern](89-backups-auslagern-erinnern.md) | 🤖 | – |
| 90 | [Versionsnummer aus dem Build, sichtbar in den Einstellungen](90-versionsnummer.md) | 🤖 | – |
| 91 | [Editoren: Titel im Fokus & „Speichern“ unten](91-editoren-fokus-speichern-unten.md) | 🤖 | – |
| 92 | [Routine & To-do: das Nötige zuerst, der Rest unter „Mehr Optionen“](92-editoren-das-noetige-zuerst.md) | 🤖 | 91 |
| 93 | [Eingang: Gedanken festhalten & einsortieren](93-eingang.md) | 🤖 | – |
| 94 | [Eingang: Kurzbefehl am App-Symbol](94-eingang-kurzbefehl.md) | 🤖 | 93 |
| 95 | [Eingang: Benachrichtigung mit Antwortfeld](95-eingang-benachrichtigung.md) | 🤖 | 93 |
| 96 | [Heute: Kopfzeile & drei Blöcke](96-heute-drei-bloecke.md) | 🤖 | – |
| 97 | [Heute: Menü je Eintrag – verschieben, öffnen, überspringen](97-heute-menue-je-eintrag.md) | 🤖 | 96 |
| 98 | [Erinnerungen: „Später“ für To-dos bis heute Abend und morgen früh](98-spaeter-fuer-todos.md) | 🤖 | – |
| 99 | [Flamme: bei null stehen bleiben, letzter Streak in der Statistik](99-flamme-bei-null.md) | 🤖 | – |
| 109 | [Gesamt-Streak: ab wann ein Tag zählt, einstellbar](109-gesamt-streak-quote.md) | 🤖 | 99 |
| 100 | [Rettungsbildschirm, wenn sich die Daten nicht öffnen lassen](100-rettungsbildschirm.md) | 🤖 | – |
| 101 | [Ablauf-Modus: eine Routine Schritt für Schritt](101-ablauf-modus.md) | 🤖 | 97 |
| 102 | [Ablauf-Modus: Minuten je Schritt, Restzeit & laufende Benachrichtigung](102-ablauf-modus-zeit.md) | 🤖 | 101 |
| 103 | [Ablauf-Modus: Start aus Erinnerung und Vollbild-Alarm](103-ablauf-modus-start-aus-erinnerung.md) | 🤖 | 101 |
| 104 | [Vokabeltrainer: Karten, Fächer & Abfrage](104-vokabeltrainer-karten.md) | 🤖 | – |
| 105 | [Vokabeltrainer: Karten aus Session-Notiz und Textdatei](105-vokabeltrainer-karten-anlegen.md) | 🤖 | 104 |
| 106 | [Vokabeltrainer: Routine und Ziel zählen mit](106-vokabeltrainer-routine-und-ziel.md) | 🤖 | 104 |
| 107 | [Vokabeltrainer: Zahlen an Themen, Thema üben & Suche](107-vokabeltrainer-themen-suche.md) | 🤖 | 104 |
| 108 | [Heute & To-dos: nur laden, was gebraucht wird](108-nur-laden-was-gebraucht-wird.md) | 🤖 | – |

## Ohne Ticket

Was aus dem Gutachten noch kein Ticket hat, steht in [ohne-ticket.md](ohne-ticket.md), mit Befund, Fundstelle und Vorschlag, damit es sich in einem neuen Chat aufgreifen lässt.
