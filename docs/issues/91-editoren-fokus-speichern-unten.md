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

- [x] Gilt für die Editoren von To-do, Routine, Ziel, Notiz, Ort, Buchung, Fixbuchung, Abo, Session und Lehrer und für den Budget-Rechner; die Seiten der Dokumentation bleiben, wie sie sind
- [x] Bei einem neuen Eintrag hat das erste Pflichtfeld (Titel, Name, bei der Buchung der Betrag) beim Öffnen den Fokus, und die Tastatur ist offen; beim Bearbeiten eines gespeicherten Eintrags nicht (ausgenommen die Session, siehe unten)
- [x] „Speichern“ ist ein gefüllter Knopf über die Breite am unteren Rand; ist die Tastatur offen, sitzt er direkt über ihr. Der Textknopf oben rechts entfällt
- [x] „Löschen“ und, bei Notizen, „Archivieren“ bleiben oben in der Leiste
- [x] Der Knopf verdeckt kein Feld: Der Inhalt lässt sich bis über ihn scrollen
- [x] Bei einem neuen To-do und einem neuen Ziel speichert „Fertig“ auf der Tastatur im Titelfeld direkt; in den anderen Editoren führt es ins nächste Feld
- [x] Lässt sich nicht speichern, zeigt der Editor das Problem wie bisher an seinem Feld und holt es in den sichtbaren Bereich
- [x] Bei Schriftgröße 150 % bleibt der Knopf einzeilig und lesbar
- [x] UI-Tests: Fokus beim neuen Eintrag und keiner beim Bearbeiten; „Speichern“ unten speichert; „Fertig“ im Titel eines neuen To-dos speichert; ein fehlender Titel hält den Editor offen

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befund U1, Vorschlag UX 1, Punkt 4); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Ein Knopf unten in allen Editoren, damit „Speichern“ überall an derselben Stelle liegt.
- „Fertig“ speichert nur bei To-do und Ziel direkt: Dort genügt der Titel. Eine Routine mit der voreingestellten Uhrzeit soll nicht aus Versehen entstehen.

Bei der Umsetzung festgelegt (13.11.2026):

- Der Fokus kommt einmal beim Öffnen, nicht wieder nach Drehen oder Sperre; scheitert das Speichern am Titel (Name, Betrag), geht er wieder dorthin.
- Die Session hat kein Feld, das zuerst getippt werden muss (Datum steht, die Dauer geht per Tipp), und öffnet ohne Tastatur; der Budget-Rechner ist kein neuer Eintrag.
- Scheitert das Speichern, kommt das erste Problem in den sichtbaren Bereich: das Titelfeld, wenn es dort liegt, sonst der oberste Hinweis (etwa „Bitte wähle eine Kategorie“). Ein Hinweis, der schon beim Öffnen steht (etwa ein abgeschlossener Monat oder ein vergangener Weckzeitpunkt), lässt den Editor oben. Die Seiten der Dokumentation bleiben davon unberührt.
- Bei einem Ziel speichert „Fertig“ wie beim To-do nur bei einem neuen; beim Bearbeiten führt es ins nächste Feld.
