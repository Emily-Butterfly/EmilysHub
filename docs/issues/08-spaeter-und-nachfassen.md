# 08 – „Später“ & Wiederholungs-Erinnerungen

**Etappe:** 2 – Routinen & Erinnerungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §3  
**GitHub-Issue:** #8  
**Blockiert durch:** [07 – Erinnerungs-Pop-up zur Uhrzeit](07-erinnerungs-popup.md)

## Was gebaut wird

„Später“ bietet eine Auswahl, und ignorierte Pop-ups erinnern erneut – höchstens 5-mal.

## Akzeptanzkriterien

- [ ] „Später“ bietet 10 min / 30 min / 1 h
- [ ] Pro Routine: Nachfass-Abstand (Standard 30 min) und Anzahl (Standard 3, max. 5)
- [ ] Nach der letzten Wiederholung bleibt die Benachrichtigung still stehen
- [ ] Erledigt / Überspringen storniert alle ausstehenden Nachfass-Alarme
- [ ] Unit-Tests für die Nachfass-Planung
