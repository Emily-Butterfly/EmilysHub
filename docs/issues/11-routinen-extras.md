# 11 – Priorität, Checkliste, Pausieren & Archivieren

**Etappe:** 2 – Routinen & Erinnerungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §2  
**GitHub-Issue:** #11  
**Blockiert durch:** [05 – Einfache Routine anlegen & abhaken](05-einfache-routine.md)

## Was gebaut wird

Routinen bekommen Priorität, eine Checkliste und lassen sich pausieren oder archivieren, ohne den Verlauf zu verlieren.

## Akzeptanzkriterien

- [ ] Priorität niedrig / normal / hoch; sichtbar in Liste und Heute
- [ ] Checkliste: Unterpunkte pro Fälligkeit abhakbar; sind alle abgehakt, gilt die Routine als erledigt; alternativ direkt komplett abhaken
- [ ] Pausieren / Fortsetzen: während der Pause entstehen keine Fälligkeiten
- [ ] Archivieren / Wiederherstellen: Routine verschwindet aus der Liste, Verlauf bleibt erhalten
