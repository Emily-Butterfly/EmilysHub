# 09 – Vollbild-Alarm pro Routine

**Etappe:** 2 – Routinen & Erinnerungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §3, §11  
**GitHub-Issue:** #9  
**Blockiert durch:** [07 – Erinnerungs-Pop-up zur Uhrzeit](07-erinnerungs-popup.md)

## Was gebaut wird

Wichtige Routinen können statt einer Benachrichtigung einen Vollbild-Alarm auslösen – wie ein Wecker, auch auf dem Sperrbildschirm.

## Akzeptanzkriterien

- [ ] Schalter „Vollbild-Alarm“ pro Routine (Standard: aus)
- [ ] Auf dem Sperrbildschirm zeigt das Vollbild nur „Erinnerung fällig“; Name und Aktionen erst nach Entsperren
- [ ] Unter Android 14 wird `USE_FULL_SCREEN_INTENT` bei Bedarf über die Systemeinstellung angefragt
- [ ] Ohne Berechtigung fällt die App auf die normale Benachrichtigung zurück
