# 46 – Haushaltsbuch: Monatsabschluss & Archiv

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §13  
**GitHub-Issue:** #46  
**Blockiert durch:** [44 – Haushaltsbuch: Buchungen, Kategorien, Monatsansicht](44-haushaltsbuch.md), [45 – Haushaltsbuch: Fixbuchungen](45-haushaltsbuch-fixbuchungen.md)

## Was gebaut wird

Einen Monat schließt du von Hand ab; dann liegt er im Archiv und ist nur noch zu lesen. Bei Bedarf lässt er sich wieder öffnen.

## Akzeptanzkriterien

- [ ] Einen Monat abschließen, mit Rückfrage; er wandert ins Archiv. Abschließen geht für vergangene Monate und für den laufenden ab seinem letzten Tag
- [ ] Archiv: Liste der abgeschlossenen Monate, neueste zuerst, mit Einnahmen, Ausgaben und Saldo; Tippen öffnet die Monatsansicht nur zum Lesen
- [ ] Abgeschlossene Monate lassen sich mit Rückfrage wieder öffnen
- [ ] Mehrere Monate können gleichzeitig offen sein; noch offene frühere Monate sind in der Monatsansicht leicht zu finden
- [ ] Buchungen mit einem Datum in einem abgeschlossenen Monat lassen sich weder anlegen noch ändern noch löschen, bis er wieder offen ist; der Editor sagt das
- [ ] In einen abgeschlossenen Monat trägt keine Fixbuchung (#45) nach
- [ ] Backups enthalten die Abschlüsse; ältere Backups laden weiter (alle Monate offen)
- [ ] Unit-Tests: Abschluss sperrt, Öffnen entsperrt, Buchung im abgeschlossenen Monat abgelehnt, keine Fixbuchung im abgeschlossenen Monat

## Entscheidungen

- Von Hand abschließen, nicht automatisch am Monatsende.
- Abgeschlossen heißt nur lesen; wieder öffnen geht mit Rückfrage.
