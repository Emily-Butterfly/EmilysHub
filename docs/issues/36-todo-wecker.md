# 36 – Vollbild-Wecker für To-dos

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §3, §5  
**GitHub-Issue:** #36  
**Blockiert durch:** –

## Was gebaut wird

To-dos bekommen wie Routinen den Vollbild-Alarm: Eingeschaltet klingelt ihre Erinnerung wie ein Wecker, auch auf dem Sperrbildschirm.

## Akzeptanzkriterien

- [ ] Schalter „Vollbild-Alarm“ im To-do-Editor, Standard aus
- [ ] Eingeschaltet kommt jede Erinnerung des To-dos als Vollbild-Wecker: zur Uhrzeit der Fälligkeit, nach „Später“ und beim Ankommen an einem seiner Orte, auch auf dem Sperrbildschirm
- [ ] Der Wecker zeigt den Titel des To-dos mit „Erledigt · Später“; gesperrt nur „Erinnerung fällig“ mit „Entsperren“ und „Schließen“
- [ ] Klingeln, Vibration, Stummschalten, das Ende nach 10 Minuten und mehrere Wecker nacheinander wie bei Routinen; Routinen und To-dos können sich im selben Wecker abwechseln
- [ ] Ohne Uhrzeit (nur Datum) klingelt er nur beim Ankommen an einem Ort; der Editor weist darauf hin
- [ ] Kein Nachfassen: Nach dem Klingeln bleibt die Benachrichtigung stehen, bis das To-do erledigt ist, wie bisher
- [ ] Wiederkehrende To-dos: der Schalter wandert mit
- [ ] „Funktioniert alles?“ und die Warnung in Heute zählen To-dos mit Vollbild-Alarm mit, wo es um Vollbild-Benachrichtigungen geht
- [ ] Backups enthalten das neue Feld; ältere Backups laden weiter (aus)
- [ ] Tests: klingelt zur Uhrzeit, nach „Später“ und beim Ankommen; ohne Schalter nur die Benachrichtigung; „Erledigt“ im Wecker hakt das To-do ab

## Entscheidungen

- Der Wecker klingelt überall, wo die Erinnerung kommt, auch am Ort.
- Kein Nachfassen und kein Überspringen: To-dos werden nie übersprungen.
