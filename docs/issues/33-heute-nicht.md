# 33 – Routine für heute auslassen („Heute nicht“)

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §2, §3, §8  
**GitHub-Issue:** #33  
**Blockiert durch:** –

## Was gebaut wird

Eine Routine lässt sich für den Tag bewusst fallen lassen: „Heute nicht“ setzt ihre restlichen Fälligkeiten auf den neuen Status „Ausgelassen“. Danach kommt keine Erinnerung mehr, und die Statistik zählt es ehrlich als nicht gemacht.

## Akzeptanzkriterien

- [ ] Neuer Status **Ausgelassen** für eine Fälligkeit
- [ ] „Heute nicht“ setzt alle offenen Fälligkeiten der Routine auf Ausgelassen, die in Heute stehen (auch eine von gestern, deren Zeitfenster noch läuft), und die späteren von heute; schon erledigte, übersprungene und verpasste bleiben, wie sie sind
- [ ] Danach kommen für die Routine heute keine Erinnerungen, Wiederholungs-Erinnerungen und Wecker mehr; eine gezeigte Benachrichtigung verschwindet, ein klingelnder Wecker verstummt
- [ ] „Heute nicht“ gibt es:
  - im entsperrten Vollbild-Wecker als eigenen Knopf neben „Erledigt · Später · Überspringen“
  - in der Benachrichtigung in der Auswahl von „Später“: „10 min · 30 min · Heute nicht“ (Android zeigt dort höchstens drei Knöpfe; „1 h“ bleibt im Vollbild-Wecker), auch wenn das Zeitfenster für „Später“ schon zu kurz ist
  - in Heute im Menü des Eintrags neben „Überspringen“
- [ ] In Heute rückgängig zu machen, solange der Tag läuft: Die Fälligkeiten werden wieder offen, solange ihr Zeitfenster läuft, sonst verpasst; ihre Erinnerungen kommen dann wieder wie geplant
- [ ] Heute und der Kalender der Statistik zeigen „Ausgelassen“ mit eigenem Namen und eigener Farbe
- [ ] Statistik: Ausgelassen bricht den Streak wie Verpasst und zählt in der Erledigt-Quote als nicht erledigt
- [ ] Backups enthalten den neuen Status
- [ ] Unit-Tests: welche Fälligkeiten ausgelassen werden (mehrere Uhrzeiten, schon beendete bleiben), Rückgängig vor und nach dem Ende des Zeitfensters, Streak und Quote

## Entscheidungen

- Eigener Status statt „Verpasst“: zählt gleich, zeigt aber, dass es eine bewusste Entscheidung war.
- Gilt für alle restlichen Uhrzeiten des Tages, nicht nur für die eine.
- In der Benachrichtigung steht „Heute nicht“ unter „Später“, weil Android höchstens drei Knöpfe zeigt.
- „Überspringen“ bleibt daneben unverändert und für den Streak neutral.
