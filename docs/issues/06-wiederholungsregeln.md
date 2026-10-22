# 06 – Wiederholungsregeln & mehrere Uhrzeiten

**Etappe:** 2 – Routinen & Erinnerungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §2  
**GitHub-Issue:** #6  
**Blockiert durch:** [05 – Einfache Routine anlegen & abhaken](05-einfache-routine.md)

## Was gebaut wird

Routinen wiederholen sich nicht nur täglich, sondern an Wochentagen, alle X Tage oder einmalig – und können mehrere Uhrzeiten pro Tag haben.

## Akzeptanzkriterien

- [ ] Wiederholung: täglich, bestimmte Wochentage, alle X Tage (ab Startdatum), einmalig (Datum)
- [ ] Mehrere Uhrzeiten pro Routine
- [ ] Fälligkeitsberechnung als reine Kotlin-Logik ohne Android-Abhängigkeiten
- [ ] Unit-Tests: Monatswechsel, Schaltjahr, Sommerzeit, „alle X Tage“ über Jahreswechsel
- [ ] Heute-Übersicht zeigt jede Uhrzeit als eigene Fälligkeit
