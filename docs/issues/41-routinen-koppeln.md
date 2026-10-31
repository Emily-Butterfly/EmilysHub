# 41 – Routinen koppeln: eine reicht

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §2, §3, §8  
**GitHub-Issue:** #41  
**Blockiert durch:** [33 – Routine für heute auslassen („Heute nicht“)](33-heute-nicht.md)

## Was gebaut wird

Routinen lassen sich koppeln, sodass eine erledigte für den Tag reicht, z. B. Tabletten um 8 Uhr zu Hause oder um 9 Uhr auf der Arbeit: Ist die eine erledigt, kommt die Erinnerung der anderen nicht mehr. Sind sie zur selben Uhrzeit fällig, gibt es eine gemeinsame Erinnerung.

## Akzeptanzkriterien

- [ ] Routinen-Editor: „Gekoppelt mit…“ wählt andere Routinen; gekoppelte Routinen bilden eine Gruppe aus zwei oder mehr, eine Routine gehört zu höchstens einer, und die Gruppe ist von jeder ihrer Routinen aus zu sehen und zu ändern
- [ ] Koppeln geht nur bei Routinen mit genau einer Uhrzeit am Tag; eine gekoppelte Routine bekommt keine zweite Uhrzeit, ohne die Kopplung zu lösen; der Editor sagt das
- [ ] Ist eine Routine der Gruppe an einem Tag erledigt, gelten die anderen an dem Tag als übersprungen, auch wenn ihr Zeitfenster schon vorbei ist; Heute zeigt sie durchgestrichen mit „durch ‹Name› erledigt“
- [ ] Ihre Erinnerungen, Wiederholungs-Erinnerungen und Wecker kommen dann nicht mehr; eine gezeigte Benachrichtigung verschwindet
- [ ] Eine gekoppelte Routine, deren Zeitfenster abgelaufen ist, wird erst verpasst, wenn an dem Tag keine Routine der Gruppe mehr erledigt werden kann; bis dahin erinnert sie nicht mehr
- [ ] Rückgängig machen der Erledigung macht die anderen wieder offen bzw. nach diesen Regeln verpasst
- [ ] Überspringen und „Heute nicht“ einer Routine betreffen nur sie
- [ ] Sind mehrere Routinen der Gruppe zur selben Uhrzeit fällig, kommt eine gemeinsame Erinnerung:
  - Vollbild-Wecker: ein Knopf je Routine („Laufen erledigt“ · „Yoga erledigt“), dazu „Später“, „Überspringen“ und „Heute nicht“ für alle zusammen
  - Benachrichtigung: „Erledigt · Später · Überspringen“; nach „Erledigt“ fragt sie, welche es war
  - gesperrt nur „Erinnerung fällig“, außer alle Routinen erlauben ihren Namen auf dem Sperrbildschirm (#38)
- [ ] Statistik: durch die Kopplung übersprungene Fälligkeiten zählen neutral wie Übersprungen
- [ ] Pausierte und archivierte Routinen bleiben in der Gruppe, zählen aber nicht, solange sie nicht aktiv sind
- [ ] Backups enthalten die Gruppen; ältere Backups laden weiter (ohne Kopplung)
- [ ] Tests: eine erledigt, die anderen übersprungen und still, auch nach abgelaufenem Zeitfenster; Rückgängig; gleiche Uhrzeit, eine Erinnerung, Auswahl nach „Erledigt“; Routinen mit mehreren Uhrzeiten lassen sich nicht koppeln

## Entscheidungen

- Eine erledigte reicht für den Tag; die anderen zählen neutral.
- Nur Routinen mit genau einer Uhrzeit am Tag, damit eindeutig ist, was wofür reicht.
- Im Wecker ein Knopf je Routine; in der Benachrichtigung „Erledigt“ mit Auswahl, weil Android höchstens drei Knöpfe zeigt.
