# 14 – Ortsgebundene Routinen (WLAN)

**Etappe:** 3 – Orte  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §2, §4  
**GitHub-Issue:** #14  
**Blockiert durch:** [10 – Zeitfenster & automatisch „verpasst“](10-zeitfenster-und-verpasst.md), [13 – WLAN-Orte verwalten](13-wlan-orte.md)

## Was gebaut wird

Eine Routine kann an Orte gebunden werden. Ist man zur Uhrzeit nicht dort, wartet die Erinnerung und erscheint beim Ankommen – solange das Zeitfenster läuft.

## Akzeptanzkriterien

- [ ] Routine: Orte auswählen (keiner = überall; mehrere = an einem beliebigen davon)
- [ ] Zur Uhrzeit am Ort → Pop-up sofort
- [ ] Nicht am Ort → Fälligkeit wartet; Heute zeigt „wartet auf <Ort>“
- [ ] Ankommen (WLAN verbunden) innerhalb des Zeitfensters → Pop-up
- [ ] Funktioniert bei geschlossener App; Hintergrund-Standort wird erst hier abgefragt
- [ ] Unit-Tests für die Entscheidungslogik Uhrzeit × Ort × Zeitfenster
