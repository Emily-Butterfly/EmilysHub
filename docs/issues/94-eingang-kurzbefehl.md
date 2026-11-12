# 94 – Eingang: Kurzbefehl am App-Symbol

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §16 (Eingang), §9  
**GitHub-Issue:** #94  
**Blockiert durch:** #93

## Was gebaut wird

Langes Drücken auf das App-Symbol bietet „Neuer Gedanke“ und führt ohne Umweg zur Eingabe für den Eingang.

## Akzeptanzkriterien

- [ ] Langes Drücken auf das App-Symbol zeigt den Kurzbefehl „Neuer Gedanke“, mit einem eigenen, selbst erstellten einfarbigen Symbol
- [ ] Er öffnet eine schlanke Eingabe (ein Feld, „Festhalten“) mit offener Tastatur, über dem, was in der App offen war; ist die App gesperrt, erst nach der App-Sperre
- [ ] „Festhalten“ legt den Text im Eingang ab (#93), leert das Feld und lässt es für den nächsten Gedanken offen; Zurück schließt die Eingabe
- [ ] Was darunter offen war, bleibt, wie es ist: Ein offener Editor behält seinen Entwurf
- [ ] Der Kurzbefehl zielt auf die Launcher-Activity mit einem Zusatz, wie die Erinnerung eines Abos ihr Ziel öffnet (`MainActivity.opening()`); es kommt keine weitere exportierte Komponente dazu (SPEC §9), und die Manifest-Prüfung des Builds bleibt grün
- [ ] Ein fremder Aufruf mit demselben Zusatz öffnet nicht mehr als das: die leere Eingabe hinter der App-Sperre
- [ ] Die Eingabe übersteht Drehen und Prozessende wie die anderen Ziele (`Destination`)
- [ ] Tests: Ein Intent mit dem Zusatz öffnet die Eingabe; gesperrt erst nach dem Entsperren; ein Editor darunter behält seinen Entwurf; der Kurzbefehl steht in den Ressourcen des Manifests

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Feature 1); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Ein fester Kurzbefehl statt eines Widgets: Ein Widget bräuchte eine exportierte Komponente (SPEC §9) und stünde offen auf dem Startbildschirm.
- Eine eigene Eingabe über dem Offenen statt eines Sprungs nach Heute: So geht auch aus einem offenen Editor heraus nichts verloren.
