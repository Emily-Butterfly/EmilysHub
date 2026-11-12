# 109 – Gesamt-Streak: ab wann ein Tag zählt, einstellbar

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §8 (Statistik, Gesamt-Streak), §1 (Flamme)  
**GitHub-Issue:** #109  
**Blockiert durch:** #99

## Was gebaut wird

Ein Tag zählt für den Gesamt-Streak schon ab der ersten Erledigung, ob Routine oder To-do, und wer es strenger will, stellt eine Quote ein. Bisher bricht eine einzige verpasste Routine den Gesamt-Streak, egal wie viel an dem Tag erledigt wurde, und To-dos zählen gar nicht.

## Akzeptanzkriterien

### Einstellung

- [ ] Die Einstellungen bekommen den Abschnitt „Statistik“ mit „Ein Tag zählt für den Gesamt-Streak“: **ab der ersten Erledigung** (Standard) oder **ab einer Quote**
- [ ] Bei „ab einer Quote“ lässt sich die Quote von 10 % bis 100 % in Zehnerschritten wählen, vorbelegt mit 50 %; ein Satz darunter sagt, was sie bedeutet, und dass 100 % heißt: nichts verpasst
- [ ] Die Einstellung bleibt auf dem Gerät und steht nicht im Backup; ohne sie gilt der Standard

### Was zählt

- [ ] Als Erledigung zählen erledigte Fälligkeiten der Routinen und abgehakte To-dos. Ein To-do zählt an dem Tag, an dem es abgehakt wurde, wie in der Heatmap; am Vortag Nachgetragenes zählt am Vortag
- [ ] Gegen einen Tag zählen nur verpasste und ausgelassene Fälligkeiten der Routinen. Ein offenes oder überfälliges To-do zählt nie gegen einen Tag, denn ein To-do kann nicht verpasst werden
- [ ] Übersprungenes, von Hand oder durch eine gekoppelte Routine, und noch Offenes zählen weder dafür noch dagegen
- [ ] Wird der Haken eines To-dos zurückgenommen oder ein erledigtes To-do endgültig gelöscht, zählt es nicht mehr, wie in der Heatmap

### Regel

- [ ] **Ab der ersten Erledigung:** Ein Tag zählt, sobald an ihm mindestens eine Erledigung steht
- [ ] **Ab einer Quote:** Ein Tag zählt, wenn mindestens eine Erledigung da ist und der Anteil der Erledigungen an allem, was dafür oder dagegen zählt, die Quote erreicht
- [ ] Ein Tag, gegen den etwas zählt und der die Regel nicht erfüllt, bricht den Streak. Der heutige Tag bricht ihn erst, wenn keine Fälligkeit mehr offen ist; bis dahin zählt er nicht und bricht nicht
- [ ] Ein Tag, an dem nichts dafür und nichts dagegen zählt (nichts fällig, alles übersprungen, alles pausiert, kein To-do abgehakt), zählt nicht und bricht nicht
- [ ] Mit 100 % zählt ein Tag nur, wenn nichts verpasst und nichts ausgelassen wurde

### Wo sie gilt

- [ ] Für die Flamme in Heute: Sie leuchtet, sobald der heutige Tag zählt, und ist grau, solange er noch nicht zählt. Es gibt sie, sobald in der Geschichte etwas für oder gegen einen Tag zählt, auch wenn es nur To-dos sind
- [ ] Für den aktuellen, den längsten und den letzten Gesamt-Streak in der Statistik und für den Kalender der Gesamt-Statistik: Ein Tag, der zählt, steht dort als erledigt
- [ ] Nicht für den Streak je Routine, die Routinen-Ziele und die Erledigt-Quoten; sie bleiben, wie sie sind
- [ ] Umstellen wirkt sofort und rückwirkend: Die Streaks werden aus der Geschichte neu berechnet, gespeichert wird nur die Einstellung

### Tests

- [ ] Unit-Tests: beide Arten; ein To-do allein lässt den Tag zählen; To-dos in der Quote; ein überfälliges To-do zählt nicht dagegen; Haken zurückgenommen und To-do endgültig gelöscht; die Quote genau erreicht und knapp verfehlt; Übersprungenes zählt nicht mit; heute mit Offenem; ein Tag ohne alles; 100 %; Umstellen ändert rückwirkend; der Streak je Routine bleibt
- [ ] UI-Tests: die Einstellung mit beiden Arten; die Flamme folgt ihr, auch nach einem abgehakten To-do

## Entscheidungen

- **Entschieden am 12.11.2026:** Die Quote ist in den Einstellungen wählbar; Standard ist „ab der ersten Erledigung am Tag“. To-dos zählen für den Streak mit.
- Nicht einzeln bestätigt: To-dos zählen nur für einen Tag, nie gegen ihn.
- Nicht einzeln bestätigt: Die Regel gilt nur für den Gesamt-Streak, nicht für den Streak je Routine und die Routinen-Ziele.
- Nicht einzeln bestätigt: Zehnerschritte, und der heutige Tag bricht den Streak erst, wenn keine Fälligkeit mehr offen ist.
