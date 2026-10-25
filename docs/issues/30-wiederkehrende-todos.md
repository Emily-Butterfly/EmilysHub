# 30 – Wiederkehrende To-dos

**Etappe:** 4 – To-dos (Ergänzung)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §5  
**GitHub-Issue:** #30  
**Blockiert durch:** [17 – To-dos](17-todos.md), [18 – To-do-Erinnerungen](18-todo-erinnerungen.md), [20 – Mehrstufige Ziele](20-mehrstufige-ziele.md)

## Was gebaut wird

Ein To-do mit Fälligkeit kann sich wiederholen, z. B. „Miete überweisen“ am 1. jedes Monats oder „Müll rausstellen“ montags. Abgehakt wandert es ins Archiv, und sofort steht das nächste mit der nächsten Fälligkeit da.

## Akzeptanzkriterien

- [ ] Wiederholung im To-do-Editor, nur mit Fälligkeit: täglich, bestimmte Wochentage, alle X Tage, monatlich am selben Tag, jährlich am selben Datum
- [ ] Abgehakt wandert das To-do ins Archiv; das nächste erscheint mit der nächsten Fälligkeit (gleiche Uhrzeit), mit Titel, Beschreibung, Priorität, Tags, Orten, Ziel und Wiederholung, die Checkliste wieder offen
- [ ] Pro To-do wählbar, ab wann der nächste Termin zählt (bei „alle X Tage“, monatlich und jährlich): **nach Plan** (der nächste Termin laut Regel nach der bisherigen Fälligkeit und nach heute; verpasste Termine entfallen) oder **ab Erledigung** (der Abstand zählt ab dem Tag des Abhakens)
- [ ] Monatlich am 29.–31.: in kürzeren Monaten am Monatsletzten, danach wieder am gewählten Tag; jährlich am 29.2. in anderen Jahren am 28.2.
- [ ] Rückgängig machen oder wieder öffnen nimmt das gerade angelegte nächste To-do wieder weg, solange es offen und unverändert ist; sonst bleibt es, und das wieder geöffnete wiederholt sich nicht mehr
- [ ] Wiederholung entfernen beendet die Reihe
- [ ] Liste, Heute und Editor zeigen, dass und wie sich ein To-do wiederholt
- [ ] Unit-Tests für die nächste Fälligkeit (Wochentage, Monatsende, Schaltjahr, zu spät und zu früh erledigt, nach Plan und ab Erledigung)

## Entscheidungen

- Abgehakt: neues To-do, das alte bleibt als Verlauf im Archiv (nicht dasselbe To-do weiterspringen lassen).
- Regeln wie bei Routinen (täglich, Wochentage, alle X Tage) plus monatlich und jährlich.
- „Nach Plan“ oder „ab Erledigung“ ist pro To-do wählbar.
- Ein einzelner Termin lässt sich verschieben, ohne die Reihe zu verschieben; monatlich und jährlich bieten dann den neuen Tag als eigene Wahl für die Reihe an.
- Am Ziel zählt nur das aktuelle To-do einer Reihe; die erledigten bleiben als Verlauf am Ziel.
