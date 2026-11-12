# 109 – Gesamt-Streak: ab wann ein Tag zählt, einstellbar

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §8 (Statistik, Gesamt-Streak), §1 (Flamme)  
**GitHub-Issue:** #109  
**Blockiert durch:** #99

## Was gebaut wird

Ein Tag zählt für den Gesamt-Streak schon ab der ersten Erledigung, und wer es strenger will, stellt eine Quote ein. Bisher bricht eine einzige verpasste Routine den Gesamt-Streak, egal wie viel an dem Tag erledigt wurde.

## Akzeptanzkriterien

### Einstellung

- [ ] Die Einstellungen bekommen den Abschnitt „Statistik“ mit „Ein Tag zählt für den Gesamt-Streak“: **ab der ersten Erledigung** (Standard) oder **ab einer Quote**
- [ ] Bei „ab einer Quote“ lässt sich die Quote von 10 % bis 100 % in Zehnerschritten wählen, vorbelegt mit 50 %; ein Satz darunter sagt, was sie bedeutet, und dass 100 % heißt: nichts verpasst
- [ ] Die Einstellung bleibt auf dem Gerät und steht nicht im Backup; ohne sie gilt der Standard

### Regel

- [ ] **Ab der ersten Erledigung:** Ein Tag zählt, sobald mindestens eine Fälligkeit einer Routine an ihm erledigt ist
- [ ] **Ab einer Quote:** Ein Tag zählt, wenn mindestens eine Fälligkeit erledigt ist und der Anteil der erledigten an den erledigten, verpassten und ausgelassenen Fälligkeiten des Tages die Quote erreicht. Übersprungenes, von Hand oder durch eine gekoppelte Routine, und Offenes zählen nicht mit
- [ ] Ein Tag mit erledigten, verpassten oder ausgelassenen Fälligkeiten, der die Regel nicht erfüllt, bricht den Streak. Der heutige Tag bricht ihn erst, wenn nichts mehr offen ist; bis dahin zählt er nicht und bricht nicht
- [ ] Ein Tag ohne solche Fälligkeiten (nichts fällig, alles übersprungen, alles pausiert) zählt nicht und bricht nicht, wie bisher
- [ ] Mit 100 % verhält sich der Gesamt-Streak wie bisher
- [ ] Am Vortag Nachgetragenes (Gestern nachtragen) folgt derselben Regel

### Wo sie gilt

- [ ] Für die Flamme in Heute: Sie leuchtet, sobald der heutige Tag zählt, und ist grau, solange er noch nicht zählt
- [ ] Für den aktuellen, den längsten und den letzten Gesamt-Streak in der Statistik und für den Kalender der Gesamt-Statistik: Ein Tag, der zählt, steht dort als erledigt
- [ ] Nicht für den Streak je Routine, die Routinen-Ziele, die Erledigt-Quoten und die Heatmap; sie bleiben, wie sie sind
- [ ] Umstellen wirkt sofort und rückwirkend: Die Streaks werden aus der Geschichte neu berechnet, gespeichert wird nur die Einstellung

### Tests

- [ ] Unit-Tests: beide Arten; die Quote genau erreicht und knapp verfehlt; Übersprungenes zählt nicht mit; heute mit Offenem; ein Tag ohne Fälligkeiten; 100 % gegen die bisherigen Tests des Gesamt-Streaks; Umstellen ändert rückwirkend; der Streak je Routine bleibt
- [ ] UI-Tests: die Einstellung mit beiden Arten; die Flamme folgt ihr

## Entscheidungen

- **Entschieden am 12.11.2026:** Die Quote ist in den Einstellungen wählbar; Standard ist „ab der ersten Erledigung am Tag“.
- Nicht einzeln bestätigt: Die Regel gilt nur für den Gesamt-Streak, nicht für den Streak je Routine und die Routinen-Ziele.
- Nicht einzeln bestätigt: Als Erledigung zählen die Fälligkeiten der Routinen; To-dos zählen für den Streak weiter nicht.
- Nicht einzeln bestätigt: Zehnerschritte, und der heutige Tag bricht den Streak erst, wenn nichts mehr offen ist.
