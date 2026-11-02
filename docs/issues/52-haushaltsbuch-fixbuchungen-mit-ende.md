# 52 – Haushaltsbuch: Fixbuchungen mit Ende

**Etappe:** 9 – Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §13  
**GitHub-Issue:** #52  
**Blockiert durch:** –

## Was gebaut wird

Eine Fixbuchung kann enden: nach einer Anzahl Buchungen, z. B. „12 Raten“, oder an einem Datum, z. B. „Abo bis 31.03.2027“.

## Akzeptanzkriterien

- [ ] Editor der Fixbuchung: „Endet: Kein Ende · Nach einer Anzahl · An einem Datum“, Standard „Kein Ende“, wie bei To-dos (#34)
- [ ] „Nach einer Anzahl“: insgesamt N Buchungen ab dem ersten Monat, die schon eingetragenen mitgezählt, auch gelöschte; nach der N-ten kommt keine weitere
- [ ] „An einem Datum“ gilt einschließlich: eine Buchung an dem Tag kommt noch, danach keine mehr
- [ ] Die Liste der Fixbuchungen zeigt das Ende („noch 2 von 12“, „letztes Mal“, „bis 31.03.“); eine beendete steht mit „beendet“ am Ende der Liste und lässt sich ändern oder löschen
- [ ] Das Ende lässt sich später ändern oder entfernen; die Änderung gilt wie bisher ab dem nächsten Eintrag, nichts wird nachgetragen
- [ ] Prüfung im Editor: Anzahl 1 bis 999; das Datum nicht vor dem ersten Termin
- [ ] Der voraussichtliche Endsaldo (#51) rechnet beendete Fixbuchungen nicht mehr ein
- [ ] Backups enthalten das Ende; ältere Backups laden weiter (ohne Ende)
- [ ] Unit-Tests: Ende nach N mit gelöschter Buchung, Enddatum einschließlich, Nachtragen nach mehreren Monaten endet richtig, Ende nachträglich geändert, beendete in der Liste; UI-Test im Editor

## Entscheidungen

- Auf Wunsch vom 02.11.2026; dieselben Felder und Texte wie das Ende von To-do-Reihen (#34).
- „N Mal“ zählt Termine ab dem ersten Monat, nicht nur die noch vorhandenen Buchungen.
