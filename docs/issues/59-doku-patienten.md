# 59 – Dokumentation: Patienten, Archiv & Löschhinweis

**Etappe:** 10 – Sonstiges, Heute & Dokumentation  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Startseite, Patienten)  
**GitHub-Issue:** #59  
**Blockiert durch:** [58 – Dokumentation: Sperre](58-doku-sperre.md)

## Was gebaut wird

In der Dokumentation legst du Patienten an, mit wenigen Feldern nach dem Grundsatz der Datensparsamkeit. Die Startseite listet die aktiven Patienten. Abgeschlossene liegen im Doku-Archiv, und ein Hinweis erinnert daran, sie nach 12 Monaten zu löschen.

## Akzeptanzkriterien

### Patient

- [ ] Felder: **Name / Kürzel** (Pflicht, freier Text), **Geburtsjahr** (optional, 1900 bis zum laufenden Jahr), **Diagnose**, **Anamnese**, **Therapeutische Zielsetzung**, **Sonstiges** (je optional, Markdown wie in den Notizen: Bearbeiten und Vorschau, ohne HTML und Links)
- [ ] Eingaben werden geprüft wie in den übrigen Bereichen (Länge, Leerzeichen am Rand)
- [ ] „+ Patient“ auf der Startseite: Name/Kürzel und Geburtsjahr, danach die Seite des neuen Patienten
- [ ] Die Seite eines Patienten: oben Name/Kürzel und, wenn vorhanden, „Jg. 1983 (≈ 43 Jahre)“; darunter drei Reiter **Übersicht · Termine · Tests**
- [ ] Übersicht: die vier Textfelder als Vorschau, leere mit „–“; Tippen auf ein Feld bearbeitet es; Name und Geburtsjahr über ✎ oben
- [ ] Termine und Tests zeigen bis #60 bzw. #62 nur einen leeren Zustand
- [ ] Menü des Patienten: „Abschließen“ und „Endgültig löschen“

### Startseite

- [ ] Die aktiven Patienten, Standard „Zuletzt behandelt“: nach dem letzten Termin bis heute, neueste zuerst; ohne Termin nach dem Anlegen. Umschaltbar auf „Name“; die Wahl bleibt (in der Doku-Datenbank)
- [ ] Je Zeile Name/Kürzel und „letzter Termin 03.11.2026“ bzw. „noch kein Termin“
- [ ] **Suche** oben: findet Patienten nach Name/Kürzel und dem Text ihrer vier Felder, aktive und archivierte (diese markiert „Archiv“); #60 und #62 nehmen Termine und Bemerkungen dazu
- [ ] Menüpunkt **Archiv**

### Abschließen & Archiv

- [ ] „Abschließen“ ohne Rückfrage: Der Patient verschwindet von der Startseite und liegt mit dem Datum des Abschlusses im **Doku-Archiv**
- [ ] Das Archiv listet die abgeschlossenen, der zuletzt abgeschlossene zuerst, mit dem Abschlussdatum; ihre Seiten sind nur zum Lesen
- [ ] „Wiederherstellen“ macht ihn wieder aktiv
- [ ] **Endgültig löschen** mit Rückfrage, die den Namen nennt: löscht den Patienten mit allem, was zu ihm gehört (Termine, Messungen), in einer Transaktion
- [ ] Die Doku-Datenbank überschreibt Gelöschtes (`PRAGMA secure_delete = ON`)
- [ ] Die Rückfrage sagt: „In älteren Backups ist er noch enthalten: in den nächtlichen bis zu 7 Nächte, in den von Hand gespeicherten, bis du sie löschst.“

### Löschhinweis

- [ ] Sind Patienten seit über 12 Monaten abgeschlossen, steht oben auf der Startseite: „3 Personen seit über 12 Monaten abgeschlossen – jetzt löschen?“
- [ ] Tippen öffnet das Archiv; die betroffenen sind dort markiert, und „Diese 3 endgültig löschen“ löscht sie nach einer Rückfrage
- [ ] Der Hinweis erscheint nur in der Doku, nie als Benachrichtigung

### Tests

- [ ] Unit-Tests: Anlegen und Prüfen der Felder; Sortierung „Zuletzt behandelt“ und „Name“; Abschließen, Wiederherstellen, endgültig Löschen mit allem; Löschhinweis genau ab 12 Monaten; Suche über Felder, auch im Archiv
- [ ] UI-Tests: Patient anlegen, Felder bearbeiten, Reiter, Archiv nur zum Lesen, Löschhinweis

## Entscheidungen

Abgestimmt im Interview vom 07.11.2026.

- Datensparsamkeit: kein volles Geburtsdatum, keine Adresse, Telefonnummer, Krankenkasse oder Fotos; die gehören in die Praxissoftware.
- Diagnose, Anamnese und Therapeutische Zielsetzung als eigene Felder, dazu ein freies Feld „Sonstiges“.
- Drei Reiter statt einer langen Seite, damit Termine und Tests auch nach vielen Terminen direkt erreichbar sind.
- Löschhinweis nach 12 Monaten, weil die DSGVO verlangt, nicht mehr Gebrauchtes zu löschen; ohne Aufbewahrungspflicht, da keine Patientenakte.
