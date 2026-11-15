# 114 – Untere Leiste: Tab-Namen bei großer Schrift ohne Umbruch

**Etappe:** 14 – Kleinigkeiten aus dem Gutachten  
**Typ:** Fehler  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1 (Grundlagen, untere Leiste)  
**GitHub-Issue:** #114  
**Blockiert durch:** –

## Was gebaut wird

Die Namen in der unteren Leiste bleiben auch bei großer Systemschrift einzeilig. Bisher brechen sie bei 150 % mitten im Wort um („Routine-n“, „Sonstige-s“), im Gutachten mit einer Aufnahme belegt (Befund U10).

## Akzeptanzkriterien

- [ ] Jeder Tab-Name steht auf einer Zeile, ohne Umbruch im Wort, bei 100 %, 150 % und 200 % Schriftgröße auf der Bildschirmbreite des A52
- [ ] Die Schrift der Leiste wächst mit der Systemschrift mit, aber nur bis zu der Größe, bei der der längste Name noch auf eine Zeile passt
- [ ] Passt ein Name trotzdem nicht, wird er mit „…“ gekürzt statt umbrochen; für Screenreader bleibt der volle Name
- [ ] Sonst ändert sich an der Leiste nichts
- [ ] UI-Tests bei 150 % und 200 %: jeder Name einzeilig und ungekürzt auf der Bildschirmbreite des A52

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befund U10); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Begrenzt wird nur die Schrift der Leiste, nicht die übrige App: Dort soll die große Schrift wirken.
