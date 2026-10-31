# 37 – Eigene Weckzeit für To-dos

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §5  
**GitHub-Issue:** #37  
**Blockiert durch:** [36 – Vollbild-Wecker für To-dos](36-todo-wecker.md)

## Was gebaut wird

Ein To-do kann zusätzlich zu seiner Fälligkeit eine eigene Weckzeit haben, z. B. Freitag 18 Uhr für ein To-do, das Samstag fällig ist.

## Akzeptanzkriterien

- [ ] Im To-do-Editor optional „Weckzeit“: entweder „N Tage vorher um HH:MM“ (N = 0 heißt am Fälligkeitstag) oder ein fester Zeitpunkt mit Datum und Uhrzeit
- [ ] „N Tage vorher“ geht nur mit Fälligkeit und wandert bei wiederkehrenden To-dos und beim Verschieben der Fälligkeit mit
- [ ] Ein fester Zeitpunkt geht auch ohne Fälligkeit; bei wiederkehrenden To-dos gilt er nur für dieses To-do, nicht für die nächsten
- [ ] Zur Weckzeit kommt die Erinnerung des To-dos mit „Erledigt · Später“, mit Vollbild-Alarm (#36) als Wecker
- [ ] Zur Fälligkeit (mit Uhrzeit) kommt zusätzlich die normale Erinnerung, falls das To-do dann noch offen ist
- [ ] Ein erledigtes To-do weckt nicht mehr
- [ ] Eine Weckzeit, die beim Speichern schon vorbei ist, weckt nicht nachträglich; der Editor weist darauf hin. War das Handy zur Weckzeit aus, kommt die Erinnerung beim Einschalten, wie die anderen Erinnerungen
- [ ] Liste und Editor zeigen die Weckzeit, z. B. „Wecker: Fr, 13.11., 18:00“
- [ ] Backups enthalten die Weckzeit; ältere Backups laden weiter (ohne)
- [ ] Unit-Tests: Weckzeit aus Fälligkeit und N Tagen (Monatsgrenze, Sommerzeit, Zeitzone), fester Zeitpunkt, Wiederholung, Erinnerung zur Fälligkeit zusätzlich

## Entscheidungen

- Beide Formen: relativ zur Fälligkeit und fest.
- Die Weckzeit ersetzt die Erinnerung zur Fälligkeit nicht.
