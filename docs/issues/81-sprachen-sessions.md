# 81 – Sprachen: Sessions, Sprachen, Lehrer & Auswertung

**Etappe:** 12 – Budget-Rechner, Abos & Sprachen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Sprachen & Lehrer, Sessions, Auswertung & Aufbau), §1 (Navigation)  
**GitHub-Issue:** #81  
**Blockiert durch:** –

## Was gebaut wird

Der neue Bereich **Sprachen** unter „Sonstiges“: gehaltene Unterrichts-Sessions eintragen, nach Sprache und Lehrer, und sehen, wie viel Zeit und wie viele Sessions es waren. Themen (#82), Anhänge (#83) sowie Suche und Ziele (#84) kommen danach.

## Akzeptanzkriterien

### Navigation

- [x] „Sonstiges“ zeigt als dritten Eintrag **Sprachen**; er öffnet im Tab wie die Ziele (untere Leiste bleibt, Zurück-Pfeil zur Liste) und bleibt beim Tabwechsel, nach Drehen und nach der App-Sperre offen

### Sprachen & Lehrer

- [x] Sprachen: Name (Pflicht, eindeutig); anlegen, umbenennen, archivieren und zurückholen; eine archivierte wird nicht mehr angeboten, ihre Sessions bleiben
- [x] Lehrer: Name (Pflicht), optionale Notiz, mindestens eine Sprache; anlegen, ändern, archivieren und zurückholen; eine weggenommene Sprache lässt die alten Sessions, wie sie sind
- [x] Beide über das Menü der Startseite verwaltbar; nichts davon lässt sich löschen, solange Sessions daran hängen

### Sessions

- [x] Felder: Datum (Pflicht, Standard heute, keine Uhrzeit), Dauer in Minuten (Pflicht; Schnellwahl 30 / 45 / 60 / 90 und freie Eingabe), Sprache (Pflicht), Lehrer (Pflicht), Preis (optional, Euro mit Cent), Notiz (optional, Markdown mit Vorschau wie in den Notizen, bis 100 000 Zeichen); strikte Eingabeprüfung (§9)
- [x] Der Lehrer-Wähler bietet die Lehrer der gewählten Sprache, dazu „Neuer Lehrer“ (gleich mit dieser Sprache) und „Anderen Lehrer wählen“ (gibt einem vorhandenen die Sprache dazu); eine neue Sprache lässt sich im Editor nebenbei anlegen
- [x] Ändern und, mit Rückfrage, löschen; der Editor lädt und speichert über eine Sitzung wie die anderen Editoren (`editing` / `save`, `Saving`)
- [x] Der Preis macht keine Buchung im Haushaltsbuch

### Auswertung & Aufbau

- [x] Startseite: oben Stunden und Anzahl Sessions für diesen Monat, dieses Jahr und insgesamt; darunter je Sprache eine Karte mit ihren Zahlen; „+“ legt eine Session an
- [x] Seite einer Sprache mit den Reitern **Sessions** (nach Datum, neueste oben, filterbar nach Lehrer) und **Auswertung** (Stunden, Sessions und, wo Preise stehen, Kosten je Lehrer, für Monat, Jahr und insgesamt); der Reiter „Themen“ kommt mit #82
- [x] Unterrichtet ein Lehrer mehrere Sprachen, zählt jede Sprache für sich; keine Diagramme
- [x] Backups enthalten Sprachen, Lehrer und Sessions; ältere Backups laden weiter (ohne)

### Tests

- [x] Unit-Tests: Eingabeprüfung; Summen je Sprache und Lehrer über Monats- und Jahresgrenzen; Lehrer mit mehreren Sprachen; archivierte Sprachen und Lehrer; weggenommene Sprache; Sitzung laden, speichern, ablehnen. UI-Tests: Bereich über Sonstiges öffnen; Session anlegen mit neuem Lehrer; Filter nach Lehrer

## Entscheidungen

Abgestimmt im Interview vom 11.11.2026.

- Allgemein für Sprachunterricht, nicht nur italki; alles von Hand, da die App kein Internet hat.
- Nur gehaltene Sessions; keine Stoppuhr; Preis in Euro nur für die Auswertung.
- Lehrer haben ihre Sprachen hinterlegt.
- Keine Tags, nichts in Heute, keine eigene Sperre.
