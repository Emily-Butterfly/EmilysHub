# 141 – Vokabeltrainer: Karten in beide Richtungen abfragen

**Etappe:** – (Einzelticket)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer: Abfrage, Beide Richtungen)  
**GitHub-Issue:** #141  
**Blockiert durch:** –

## Was gebaut wird

Ein Haken „In beide Richtungen abfragen“ an der Karte: Sie wird dann auch von der Rückseite zur Vorderseite abgefragt, mit eigener Planung je Richtung.

## Akzeptanzkriterien

- [x] Haken beim Anlegen und Ändern einer Karte, Standard aus; Ausschalten löscht die Planung der Rückrichtung, mit Rückfrage, wenn sie schon abgefragt wurde
- [x] Import aus Session-Notiz und Datei: ein Haken im Dialog für alle Karten des Imports
- [x] Eigene Planung je Richtung (Stabilität, Schwierigkeit, erste und letzte Abfrage, Fälligkeit, Abstand); das Protokoll vermerkt die Richtung
- [x] Tagespensum: die Rückrichtung zählt als eigene Abfrage und neu begonnen als eigene neue Karte im Limit
- [x] Hin- und Rückrichtung einer Karte nie am selben Tag, auch nicht beim Thema üben; eine neue Karte zuerst in der Hinrichtung
- [x] Abfrage der Rückrichtung zeigt die Rückseite mit dem Vermerk „Rückrichtung“, aufgedeckt Vorderseite, Umschrift und Notiz
- [x] „Sicher“ (Karten-Reiter, Themen, Prüfungen, Karten-Ziel) erst, wenn beide Richtungen sicher sind
- [x] Reiter Karten: die nächste Fälligkeit über beide Richtungen
- [x] Datenbank-Migration mit Test; Backups älterer Version lassen sich weiter einspielen

## Tests

- [x] Unit: Tagespensum mit beiden Richtungen (neu, fällig, nie am selben Tag, Limit), Thema üben, „sicher“, Antwort plant nur ihre Richtung
- [x] UI: Abfrage der Rückrichtung; Haken im Editor

## Entscheidungen

Abgestimmt am 16.11.2026: eigene Planung je Richtung wie bei Anki; Rückrichtung zählt im Pensum und als neue Karte; nie am selben Tag; sicher erst mit beiden; Haken beim Anlegen, Ändern und Import, Standard aus.

Beim Bauen festgelegt (16.11.2026), nicht einzeln bestätigt:

- Abgefragt wird eine Richtung einer Karte (`Prompt`); die Planung der Rückrichtung steht in eigenen Spalten der Karte (`reverse_*`), das Protokoll hat die Spalte `reverse`. Datenbank Version 55.
- Sind beide Richtungen am selben Tag fällig, kommt die Hinrichtung (die früher fällige zuerst); die andere wartet bis zum nächsten Tag.
- Die Rückfrage beim Ausschalten kommt nur, wenn die Rückrichtung schon einmal abgefragt wurde; vorher geht nichts verloren.
- Der Migrationstest liegt bei den Gerätetests (`HubDatabaseMigrationTest`) und läuft in der CI auf dem Emulator.
- Ausschalten löscht auch die Antworten der Rückrichtung im Protokoll, damit es zur Planung passt. Weil sich dabei „sicher“ und das Tagespensum ändern können, prüft das Speichern einer Karte die Karten-Ziele neu, und ist das Pensum danach geschafft, gilt eine „durch Vokabeln“ erledigte Routine als erledigt.
- Reiter Karten: „heute fällig“ vor „neu“, und „neu“ auch, wenn erst eine Richtung nie abgefragt wurde.

