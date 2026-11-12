# 91 – Editoren: Titel im Fokus & „Speichern“ unten

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §8 (Editoren)  
**GitHub-Issue:** #91  
**Blockiert durch:** –

## Was gebaut wird

Ein neuer Eintrag lässt sich sofort tippen und mit dem Daumen speichern: Die Tastatur steht im Titelfeld, und „Speichern“ ist ein großer Knopf unten statt eines kleinen Textknopfs oben rechts.

## Akzeptanzkriterien

- [ ] Gilt für die Editoren von To-do, Routine, Ziel, Notiz, Ort, Buchung, Fixbuchung, Abo, Session und Lehrer und für den Budget-Rechner; die Seiten der Dokumentation bleiben, wie sie sind
- [ ] Bei einem neuen Eintrag hat das erste Pflichtfeld (Titel, Name, bei der Buchung der Betrag) beim Öffnen den Fokus, und die Tastatur ist offen; beim Bearbeiten eines gespeicherten Eintrags nicht
- [ ] „Speichern“ ist ein gefüllter Knopf über die Breite am unteren Rand; ist die Tastatur offen, sitzt er direkt über ihr. Der Textknopf oben rechts entfällt
- [ ] „Löschen“ und, bei Notizen, „Archivieren“ bleiben oben in der Leiste
- [ ] Der Knopf verdeckt kein Feld: Der Inhalt lässt sich bis über ihn scrollen
- [ ] Bei einem neuen To-do und einem neuen Ziel speichert „Fertig“ auf der Tastatur im Titelfeld direkt; in den anderen Editoren führt es ins nächste Feld
- [ ] Lässt sich nicht speichern, zeigt der Editor das Problem wie bisher an seinem Feld und holt es in den sichtbaren Bereich
- [ ] Bei Schriftgröße 150 % bleibt der Knopf einzeilig und lesbar
- [ ] UI-Tests: Fokus beim neuen Eintrag und keiner beim Bearbeiten; „Speichern“ unten speichert; „Fertig“ im Titel eines neuen To-dos speichert; ein fehlender Titel hält den Editor offen

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befund U1, Vorschlag UX 1, Punkt 4); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Ein Knopf unten in allen Editoren, damit „Speichern“ überall an derselben Stelle liegt.
- „Fertig“ speichert nur bei To-do und Ziel direkt: Dort genügt der Titel. Eine Routine mit der voreingestellten Uhrzeit soll nicht aus Versehen entstehen.
