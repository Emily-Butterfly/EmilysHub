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

- [x] Langes Drücken auf das App-Symbol zeigt den Kurzbefehl „Neuer Gedanke“, mit einem eigenen, selbst erstellten einfarbigen Symbol
- [x] Er öffnet eine schlanke Eingabe (ein Feld, „Festhalten“) mit offener Tastatur, über dem, was in der App offen war; ist die App gesperrt, erst nach der App-Sperre
- [x] „Festhalten“ legt den Text im Eingang ab (#93), leert das Feld und lässt es für den nächsten Gedanken offen; Zurück schließt die Eingabe
- [x] Was darunter offen war, bleibt, wie es ist: Ein offener Editor behält seinen Entwurf
- [x] Der Kurzbefehl zielt auf die Launcher-Activity mit einem Zusatz, wie die Erinnerung eines Abos ihr Ziel öffnet (`MainActivity.opening()`); es kommt keine weitere exportierte Komponente dazu (SPEC §9), und die Manifest-Prüfung des Builds bleibt grün
- [x] Ein fremder Aufruf mit demselben Zusatz öffnet nicht mehr als das: die leere Eingabe hinter der App-Sperre
- [x] Die Eingabe übersteht Drehen und Prozessende wie die anderen Ziele (`Destination`)
- [x] Tests: Ein Intent mit dem Zusatz öffnet die Eingabe; gesperrt erst nach dem Entsperren; ein Editor darunter behält seinen Entwurf; der Kurzbefehl ist gesetzt und zielt auf die Launcher-Activity

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Feature 1); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Ein fester Kurzbefehl statt eines Widgets: Ein Widget bräuchte eine exportierte Komponente (SPEC §9) und stünde offen auf dem Startbildschirm.
- Eine eigene Eingabe über dem Offenen statt eines Sprungs nach Heute: So geht auch aus einem offenen Editor heraus nichts verloren.

Bei der Umsetzung festgelegt (13.11.2026):

- Der Kurzbefehl ist ein dynamischer, den die App beim Start setzt, kein statischer aus `res/xml/shortcuts.xml`: Android startet einen statischen immer mit `FLAG_ACTIVITY_CLEAR_TASK` (AOSP, `ShortcutParser`), was ein offenes Editor-Fenster samt Entwurf schließen würde. Der dynamische zielt ebenso auf die Launcher-Activity mit dem Zusatz, mit `CLEAR_TOP | SINGLE_TOP` wie die Erinnerung eines Abos; der Test prüft ihn bei `ShortcutManager` statt in den Ressourcen des Manifests.
- Die Eingabe ist ein eigenes Ziel (`Destination.Capture`) über dem Offenen; zweimal hintereinander geöffnet, liegt sie nur einmal darauf.
