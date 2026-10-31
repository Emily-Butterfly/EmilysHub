# 34 – Wiederholungen begrenzen

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §2, §5  
**GitHub-Issue:** #34  
**Blockiert durch:** –

## Was gebaut wird

Wiederholungen können enden: To-dos nach einer Anzahl oder an einem Datum, z. B. „jeden Samstag, 4 Mal“ oder „samstags bis 19.12.“; Routinen an einem Datum, z. B. „täglich bis 15.11.“ für eine Kur.

## Akzeptanzkriterien

- [ ] To-do-Editor, mit Wiederholung: „Endet: nie · nach N Mal · am Datum“, Standard „nie“
- [ ] „Nach N Mal“: Die Reihe hat insgesamt N To-dos, das erste mitgezählt, auch wenn ein Termin wegfällt, weil eines zu spät erledigt wurde; nach dem N-ten kommt kein weiteres. Das gilt auch für „ab Erledigung“
- [ ] „Am Datum“ gilt einschließlich: Ein To-do, dessen nächster Termin nach dem Datum läge, ist das letzte
- [ ] Routinen-Editor, bei täglich, Wochentagen und alle X Tage: „Endet: nie · am Datum“ (einschließlich); bei „einmalig“ gibt es kein Ende
- [ ] Nach dem letzten Tag einer Routine, sobald dessen Fälligkeiten beendet sind, wandert sie ins Archiv; ihre Statistik bleibt, wiederherstellen geht wie bisher
- [ ] Listen, Heute und Editoren zeigen das Ende, z. B. „noch 2 von 4“, „letztes Mal“ oder „bis 15.11.“
- [ ] Das Ende lässt sich später ändern oder entfernen; bei To-dos zählt N weiter ab dem ersten der Reihe, und ist N kleiner als die schon gekommenen, ist das aktuelle das letzte
- [ ] Rückgängig machen oder wieder öffnen des letzten To-dos einer Reihe verhält sich wie bisher (#30)
- [ ] Prüfung im Editor: N mindestens 1; das Datum nicht vor der aktuellen Fälligkeit bzw. nicht vor heute
- [ ] Backups enthalten Ende und Zählstand; ältere Backups laden weiter, ohne Ende
- [ ] Unit-Tests: Zählen mit weggefallenen Terminen, ab Erledigung, Enddatum einschließlich, Routine ins Archiv nach dem Enddatum, Ende nachträglich geändert

## Entscheidungen

- „N Mal“ zählt To-dos, nicht Kalendertermine.
- Routinen enden nur an einem Datum, nicht nach einer Anzahl.
- Eine beendete Routine wandert ins Archiv, statt gelöscht zu werden.
