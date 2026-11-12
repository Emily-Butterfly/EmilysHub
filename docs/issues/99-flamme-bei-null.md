# 99 – Flamme: bei null stehen bleiben, letzter Streak in der Statistik

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1 (Heute-Übersicht), §8 (Statistik)  
**GitHub-Issue:** #99  
**Blockiert durch:** –

## Was gebaut wird

Bricht der Gesamt-Streak, verschwindet die Flamme nicht mehr aus Heute, und die Statistik nennt den letzten Streak. Bisher ist nach einer einzigen verpassten Routine nichts mehr davon zu sehen.

## Akzeptanzkriterien

- [ ] Läuft kein Gesamt-Streak, bleibt die Flamme in Heute stehen: grau, mit der Zahl 0
- [ ] Ohne jede Fälligkeit in der Geschichte (neue Installation, keine Routinen) gibt es weiter keine Flamme
- [ ] Antippen öffnet die Statistik, wie bisher
- [ ] Die Statistik, gesamt und je Routine, nennt bei einem Streak von 0 den letzten: „Letzter Streak: 23 Tage, bis Mi., 21. Okt.“; gab es noch keinen, steht dort nichts
- [ ] Der letzte Streak ist die Folge erledigter Tage vor dem jüngsten Bruch; übersprungene und offene Tage zählen darin nicht und brechen ihn nicht, wie beim Streak selbst
- [ ] Für Screenreader sagt die Flamme „Kein Streak. Letzter: 23 Tage“ bzw. „Kein Streak“
- [ ] An den Regeln des Streaks ändert dieses Ticket nichts; ab wann ein Tag für den Gesamt-Streak zählt, regelt #109
- [ ] Unit-Tests: letzter Streak nach einem Bruch, nach mehreren, mit übersprungenen Tagen dazwischen, ohne früheren; UI-Tests: graue Flamme mit 0 in Heute, die Zeile in der Statistik

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befund U6); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Nur die Anzeige ändert sich. Ab wann ein Tag für den Gesamt-Streak zählt, regelt #109.
- Eine graue 0 statt nichts: Die Stelle bleibt, und der Weg zur Statistik auch.
