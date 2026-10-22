# 07 – Erinnerungs-Pop-up zur Uhrzeit

**Etappe:** 2 – Routinen & Erinnerungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §3, §9, §11  
**GitHub-Issue:** #7  
**Blockiert durch:** [05 – Einfache Routine anlegen & abhaken](05-einfache-routine.md)

## Was gebaut wird

Zur fälligen Uhrzeit erscheint pünktlich eine Benachrichtigung mit Aktionen. Auf dem Sperrbildschirm ist nur „Erinnerung fällig“ zu sehen.

## Akzeptanzkriterien

- [ ] Exakter Alarm über `AlarmManager` zur Fälligkeit
- [ ] Benachrichtigung mit hoher Priorität und Aktionen **Erledigt · Später · Überspringen** (Später vorerst fest 10 min)
- [ ] Sperrbildschirm: öffentliche Version zeigt nur „Erinnerung fällig“; Aktionen erst nach Entsperren
- [ ] Berechtigungen `POST_NOTIFICATIONS` und exakte Wecker werden erst bei der ersten Routine abgefragt, mit deutscher Erklärung
- [ ] Alarme werden nach Neustart, App-Update und Zeit-/Zeitzonenänderung neu geplant
- [ ] Alle `PendingIntent`s immutable und explizit; Receiver prüfen die Intent-Action
- [ ] Unit-Tests für die Berechnung des nächsten Alarmzeitpunkts
