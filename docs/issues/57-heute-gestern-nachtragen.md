# 57 – Heute: Gestern nachtragen

**Etappe:** 10 – Sonstiges, Heute & Dokumentation  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1 (Gestern nachtragen), §2, §5, §8  
**GitHub-Issue:** #57  
**Blockiert durch:** –

## Was gebaut wird

Ein Knopf in Heute zeigt den Vortag. Dort lassen sich Routinen und To-dos von gestern nachträglich als erledigt abhaken, auch Routinen, die schon als verpasst gelten. Nachgetragenes zählt voll für Streak, Statistik und Heatmap.

## Akzeptanzkriterien

### Ansicht

- [ ] Über der Liste in Heute eine schmale Zeile mit „‹ Gestern“ links
- [ ] Am Vortag oben deutlich markiert „Gestern, Fr. 6. Nov. – nachtragen“ und rechts „Heute ›“, das zurückführt; die Zurück-Taste führt ebenfalls nach Heute
- [ ] Es geht nur einen Tag zurück: Am Vortag gibt es kein „‹ Gestern“
- [ ] Der Vortag zeigt nur seine Routinen und To-dos, ohne Demnächst, angepinnte Notizen, Heatmap und Warnbanner; die Flamme oben zeigt weiter den Streak von heute und ändert sich mit dem Nachgetragenen
- [ ] Wechsel des Tabs, ein neuer Start der App oder die App-Sperre zeigen wieder den heutigen Tag
- [ ] Wird es Mitternacht, während der Vortag offen ist, zeigt die Ansicht den neuen Vortag

### Routinen von gestern

- [ ] Alle Fälligkeiten von gestern stehen da, mit ihrem Status, nach Uhrzeit
- [ ] **Verpasste**, **ausgelassene** und noch **offene** lassen sich als erledigt abhaken; ihre Checkliste lässt sich abhaken wie in Heute
- [ ] Ein Haken lässt sich wieder entfernen: Die Fälligkeit ist dann verpasst, oder offen, solange ihr Zeitfenster noch läuft
- [ ] Übersprungene und durch eine gekoppelte Routine erledigte bleiben, wie sie sind; Überspringen und „Heute nicht“ gibt es am Vortag nicht
- [ ] Gekoppelte Routinen (§2): Wird eine am Vortag nachgetragen, gelten die anderen der Gruppe an dem Tag als übersprungen („durch … erledigt“); den Haken entfernen nimmt das zurück
- [ ] Abhaken einer offenen Fälligkeit beendet ihre Erinnerungen, wie in Heute
- [ ] Die Datenschicht lässt das Nachtragen nur für Fälligkeiten von gestern zu, nicht für ältere

### To-dos von gestern

- [ ] Gezeigt werden die To-dos, die gestern fällig waren oder überfällig offen standen, und die gestern erledigten
- [ ] Ein offenes To-do dort abhaken trägt es als **gestern erledigt** ein (Erledigt-Zeitpunkt: gestern 23:59 in der Zeitzone von jetzt); es verschwindet aus den Überfälligen von heute und wandert ins Archiv
- [ ] Wiederkehrend: Das nächste To-do entsteht wie sonst; bei „ab Erledigung“ zählt gestern als Tag der Erledigung
- [ ] Den Haken entfernen öffnet es wieder, wie in Heute

### Zählen

- [ ] Nachgetragenes zählt voll als erledigt am Vortag: Streak, Flamme, Statistik je Routine und gesamt, Erledigt-Quoten, Kalender und Heatmap, ohne Sondermarkierung
- [ ] Unit-Tests: verpasst, ausgelassen und offen nachtragen und zurücknehmen; Streak vor und nach dem Nachtragen; gekoppelte Gruppe; To-do gestern erledigt mit Wiederholung „ab Erledigung“; ältere Tage abgelehnt; Wechsel um Mitternacht
- [ ] UI-Tests: Gestern öffnen und zurück; abhaken am Vortag; Tabwechsel zeigt wieder heute

## Entscheidungen

Abgestimmt im Interview vom 07.11.2026.

- Nur einen Tag zurück, nicht weiter blättern.
- Am Vortag nur Abhaken und Rückgängig, kein Überspringen und kein „Heute nicht“.
- Nachgetragenes sieht aus wie normal Erledigtes und repariert den Streak.
- Eine Zeile über der Liste statt eines Symbols in der vollen oberen Leiste oder einer Wischgeste.
- Übersprungene bleiben am Vortag, wie sie sind; das kam im Interview nicht vor und folgt aus „nur Abhaken und Rückgängig“.
