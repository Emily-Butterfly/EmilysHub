# 98 – Erinnerungen: „Später“ für To-dos bis heute Abend und morgen früh

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §3 (Später), §5  
**GitHub-Issue:** #98  
**Blockiert durch:** –

## Was gebaut wird

Die Erinnerung eines To-dos lässt sich auf heute Abend oder morgen früh legen. Bisher bietet „Später“ auch für To-dos nur 10, 30 und 60 Minuten, obwohl ein To-do kein Zeitfenster hat, das abläuft.

## Akzeptanzkriterien

- [ ] Die Erinnerung eines To-dos bietet nach „Später“ zusätzlich **Heute Abend** (18:00) und **Morgen früh** (08:00), in der Zeitzone des Handys
- [ ] Vollbild-Alarm, entsperrt: 10 min, 30 min, 1 h, Heute Abend, Morgen früh
- [ ] Benachrichtigung, mit höchstens drei Knöpfen: 30 min, Heute Abend, Morgen früh; ab 17:00 steht statt „Heute Abend“ 1 h
- [ ] „Heute Abend“ wird nur bis 17:00 angeboten. „Morgen früh“ ist 08:00 am nächsten Kalendertag, nachts vor 04:00 das 08:00 desselben Tages
- [ ] Die Wahl verschiebt nur die Erinnerung: Die Fälligkeit bleibt, das To-do steht weiter in Heute, und das Nachfassen zählt ab dem neuen Zeitpunkt, wie bei „Später“ sonst
- [ ] Das gilt überall, wo ein To-do erinnert: zur Uhrzeit, zur Weckzeit und beim Ankommen an einem Ort
- [ ] Routinen und Abos behalten ihre Auswahl
- [ ] Der gewählte Zeitpunkt übersteht einen Neustart des Handys; nach einem Wechsel der Zeitzone gilt der gespeicherte Moment
- [ ] Eine Antwort, die jetzt nicht mehr angeboten wird (etwa „Heute Abend“ aus einer alten Benachrichtigung nach 17:00), lässt die Erinnerung, wie sie ist
- [ ] Unit-Tests: Auswahl vor und nach 17:00, beide Zeitpunkte, kurz vor und nach Mitternacht, Sommerzeitwechsel, Nachfassen ab dem neuen Zeitpunkt, veraltete Antwort; Tests für die Knöpfe der Benachrichtigung und des Alarms

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befund U4, Vorschlag UX 2, Punkt 3).

- Nicht einzeln bestätigt: die festen Zeiten 18:00 und 08:00. Einstellbar werden sie erst, wenn sie nicht passen.
- „Später“ verschiebt nur die Erinnerung. Die Fälligkeit verschiebt das Menü in Heute (#97).
- In der Benachrichtigung entfallen 10 min und 1 h zugunsten der beiden neuen Zeiten; der Vollbild-Alarm bietet alle.
