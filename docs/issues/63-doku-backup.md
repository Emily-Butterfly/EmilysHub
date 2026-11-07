# 63 – Dokumentation im Backup, doppelt verschlüsselt

**Etappe:** 10 – Sonstiges, Heute & Dokumentation  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §10, §14  
**GitHub-Issue:** #63  
**Blockiert durch:** [58 – Dokumentation: Sperre](58-doku-sperre.md)

## Was gebaut wird

Die Dokumentation kommt in die Backups, nächtlich wie von Hand, in ihrer eigenen Verschlüsselung: Mit dem Backup-Passwort allein ist sie nicht lesbar. Weil das Backup sie dafür nicht entschlüsseln muss, klappt auch das nächtliche Backup bei gesperrter Doku.

## Akzeptanzkriterien

### Sichern

- [ ] Das Backup enthält einen eigenen Abschnitt „Dokumentation“: die mit SQLCipher verschlüsselte `doku.db` und den mit dem Doku-Passwort verpackten Schlüssel (Salt, Iterationen, verpackter Schlüssel), nicht den an den Fingerabdruck gebundenen
- [ ] Das Backup entschlüsselt die Doku nicht und fragt nicht nach dem Doku-Passwort
- [ ] Es sichert eine in sich stimmige Fassung der Doku, auch wenn sie gerade entsperrt ist
- [ ] Ohne eingerichtete Doku fehlt der Abschnitt
- [ ] Die Doku zählt zur Größengrenze der Backups; wird sie überschritten, schlägt das Backup fehl wie heute bei zu großen Backups

### Einspielen

- [ ] Hat das Backup eine Doku, fragt das Einspielen zusätzlich nach dem **Doku-Passwort**, das beim Backup galt, und prüft es, bevor etwas ersetzt wird; ein falsches zeigt „Falsches Doku-Passwort“ und ändert nichts
- [ ] Die Rückfrage vor dem Ersetzen nennt die Doku: „Auch die Dokumentation wird durch die aus dem Backup ersetzt.“
- [ ] „Ohne Dokumentation einspielen“ spielt alles andere ein; die vorhandene Doku bleibt, wie sie ist
- [ ] Ein Backup ohne Doku (auch jedes ältere) lässt die vorhandene Doku unverändert
- [ ] Nach dem Einspielen gilt das Doku-Passwort aus dem Backup; der Fingerabdruck ist aus und lässt sich nach dem ersten Entsperren wieder einschalten
- [ ] Eine Doku aus einem Backup einer älteren App-Version wird beim ersten Öffnen mit ihren Migrationen auf den Stand gebracht
- [ ] Fehler beim Einspielen der Doku lassen alle Daten unverändert, wie bei den übrigen Daten

### Tests

- [ ] Tests: Roundtrip mit Doku; Backup bei gesperrter und bei entsperrter Doku; falsches Doku-Passwort beim Einspielen; „Ohne Dokumentation einspielen“; älteres Backup ohne Doku lässt die vorhandene; manipulierter Doku-Abschnitt wird abgelehnt; Backup-Passwort allein öffnet die Doku nicht

## Entscheidungen

Abgestimmt im Interview vom 07.11.2026.

- Die Doku kommt in die normalen Backups, doppelt verschlüsselt, statt gar nicht oder in ein eigenes Backup.
- Ältere Backups brauchen das Doku-Passwort, das damals galt.
- Ein Backup ohne Doku oder „Ohne Dokumentation einspielen“ lässt die vorhandene Doku stehen, damit ein Einspielen sie nicht unbemerkt löscht.
