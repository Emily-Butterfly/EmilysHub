# 38 – Name auf dem Sperrbildschirm

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §3, §9  
**GitHub-Issue:** #38  
**Blockiert durch:** [36 – Vollbild-Wecker für To-dos](36-todo-wecker.md)

## Was gebaut wird

Je Routine und To-do lässt sich erlauben, dass ihr Name auf dem Sperrbildschirm steht statt nur „Erinnerung fällig“.

## Akzeptanzkriterien

- [ ] Schalter „Name auf dem Sperrbildschirm zeigen“ in den Editoren von Routinen und To-dos, Standard aus, mit einem Satz dazu, dass dann jeder den Namen auf dem gesperrten Handy sehen kann
- [ ] Eingeschaltet zeigt der gesperrte Vollbild-Wecker Name und Uhrzeit statt „Erinnerung fällig“, weiterhin mit „Entsperren“ und „Schließen“
- [ ] Eingeschaltet zeigen alle Benachrichtigungen des Eintrags (zur Uhrzeit, nach „Später“, Wiederholungs-Erinnerungen, die Auswahl von „Später“) den Namen auch dann, wenn Android die Inhalte auf dem Sperrbildschirm ausblendet
- [ ] Antworten (Erledigt, Später, Überspringen, Heute nicht) geht in jedem Fall erst nach dem Entsperren
- [ ] Beschreibung, Checkliste und Orte stehen nie auf dem Sperrbildschirm
- [ ] Ausgeschaltet bleibt alles wie bisher
- [ ] Wiederkehrende To-dos: der Schalter wandert mit
- [ ] Backups enthalten das neue Feld; ältere Backups laden weiter (aus)
- [ ] Tests: gesperrter Wecker und öffentliche Fassung der Benachrichtigung mit und ohne Schalter; Aktionen erst nach dem Entsperren

## Entscheidungen

- Standard aus.
- Nur Name und Uhrzeit, keine Aktionen ohne Entsperren.
