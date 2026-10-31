# 35 – To-dos vorher in Heute („Demnächst“)

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1, §5  
**GitHub-Issue:** #35  
**Blockiert durch:** –

## Was gebaut wird

Ein To-do kann schon einige Tage vor seiner Fälligkeit in Heute stehen, im neuen Abschnitt „Demnächst“, damit es nicht überraschend kommt.

## Akzeptanzkriterien

- [ ] To-do-Editor, mit Fälligkeit: „In Heute zeigen: am Fälligkeitstag · N Tage vorher“ (N von 1 bis 30), Standard am Fälligkeitstag wie bisher
- [ ] Ab N Tagen vor der Fälligkeit steht das To-do in Heute im Abschnitt „Demnächst“, unter den Einträgen von heute
- [ ] „Demnächst“ ist nach Fälligkeit sortiert und zeigt sie, z. B. „morgen“ oder „Sa, 14.11. · in 3 Tagen“, mit Uhrzeit, wenn es eine gibt; der Abschnitt erscheint nur, wenn er etwas zeigt
- [ ] Dort direkt abhaken, auch die Checkliste; ein abgehaktes bleibt für den Rest des Tages abgehakt stehen, wie in der normalen Liste
- [ ] Am Fälligkeitstag rückt es in die normale Liste
- [ ] „Demnächst“ erinnert nicht; die Erinnerungen bleiben, wie sie eingestellt sind
- [ ] Wiederkehrende To-dos: die Einstellung wandert mit
- [ ] Backups enthalten das neue Feld; ältere Backups laden weiter (am Fälligkeitstag)
- [ ] Unit-Tests: welche To-dos „Demnächst“ zeigt (Grenze bei N Tagen, Mitternacht, Zeitzone, abgehakt, wiederkehrend)

## Entscheidungen

- Je To-do, ohne Standard für alle.
- Eigener Abschnitt statt Mischung mit den Einträgen von heute.
