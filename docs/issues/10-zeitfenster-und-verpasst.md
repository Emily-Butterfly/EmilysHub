# 10 – Zeitfenster & automatisch „verpasst“

**Etappe:** 2 – Routinen & Erinnerungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §2, §3  
**GitHub-Issue:** #10  
**Blockiert durch:** [07 – Erinnerungs-Pop-up zur Uhrzeit](07-erinnerungs-popup.md)

## Was gebaut wird

Jede Fälligkeit hat ein Zeitfenster. Läuft es ab, ohne dass die Routine abgehakt wurde, gilt sie als verpasst.

## Akzeptanzkriterien

- [ ] Feld Zeitfenster pro Routine (Standard 4 h) und Checkbox „bis Mitternacht“
- [ ] Nach Ablauf: Status **Verpasst**, Benachrichtigung wird entfernt
- [ ] Beim App-Start und nach Neustart werden abgelaufene Fälligkeiten nachträglich korrekt gesetzt
- [ ] Unit-Tests: Fenster über Mitternacht hinaus, „bis Mitternacht“, Sommerzeit
