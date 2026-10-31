# 42 – Streak-Flamme in Heute

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1, §8  
**GitHub-Issue:** #42  
**Blockiert durch:** –

## Was gebaut wird

Oben in Heute steht eine Flamme mit den Tagen des Gesamt-Streaks, wie ihn die Statistik zählt; grau, solange heute noch nichts geschafft ist.

## Akzeptanzkriterien

- [ ] In der oberen Leiste von Heute eine Flamme mit der Zahl der Tage des aktuellen Gesamt-Streaks, dieselbe Zahl wie in der Statistik
- [ ] Grau, solange der heutige Tag noch offen ist (Routinen fällig, noch keine erledigt, nichts verpasst); farbig, sobald heute zählt, und wenn heute keine Routine fällig oder alles übersprungen ist
- [ ] Ohne Streak (0 Tage) keine Flamme
- [ ] Tippen auf die Flamme öffnet die Statistik
- [ ] Zahl und Farbe folgen sofort, wenn etwas erledigt, rückgängig gemacht, übersprungen, ausgelassen oder verpasst wird, und beim Tageswechsel
- [ ] TalkBack liest z. B. „Streak 12 Tage, heute noch offen“
- [ ] Nur in Heute, nicht in den anderen Tabs
- [ ] Unit-Tests: Zahl und Zustand (heute offen, heute erledigt, heute alles übersprungen, heute nichts fällig, gestern verpasst)

## Entscheidungen

- Der Gesamt-Streak der Statistik, keine eigene Zählung; To-dos zählen nicht.
- Grau als Ansporn, solange heute noch nichts erledigt ist.
