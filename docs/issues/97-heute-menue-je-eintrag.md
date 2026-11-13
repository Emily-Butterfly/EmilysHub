# 97 – Heute: Menü je Eintrag – verschieben, öffnen, überspringen

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1 (Menü je Eintrag), §5  
**GitHub-Issue:** #97  
**Blockiert durch:** #96

## Was gebaut wird

Ein To-do lässt sich in Heute mit einem Tipp auf einen anderen Tag verschieben, und jeder Eintrag lässt sich von dort öffnen. Bisher lässt sich in Heute nur abhaken; neu einplanen kostet den Umweg über den Tab und den Editor.

## Akzeptanzkriterien

### Menü

- [x] Jeder Eintrag in Heute hat rechts ein Menü, auch unter Überfällig, Schon vorbei und Demnächst; langes Drücken auf die Zeile öffnet dasselbe. Antippen der Zeile hakt weiter ab
- [x] „Öffnen“ führt in den Editor des To-dos bzw. der Routine; zurück landet man wieder in Heute
- [x] Die Abos unter Demnächst bleiben, wie sie sind; der Vortag (Gestern nachtragen) bekommt kein Menü

### To-do

- [x] **Morgen · Am Wochenende · Nächste Woche · Datum wählen · Öffnen**
- [x] Gerechnet wird vom heutigen Tag aus, nicht von der alten Fälligkeit: „Morgen“ ist der Tag nach heute, „Am Wochenende“ der kommende Samstag (am Samstag und Sonntag der Samstag danach), „Nächste Woche“ der kommende Montag
- [x] Verschieben ändert nur das Datum der Fälligkeit: Die Uhrzeit bleibt, eine Weckzeit „N Tage vorher“ wandert mit, alles andere bleibt
- [x] Danach sagt eine Leiste, wohin verschoben wurde („Auf Sa., 24. Okt. verschoben“), und bietet „Rückgängig“
- [x] Eine laufende Erinnerung des To-dos verschwindet; die neue Fälligkeit erinnert, wie nach einer Änderung im Editor
- [x] Bei einem wiederkehrenden To-do verschiebt sich nur dieses eine, wie beim Ändern der Fälligkeit im Editor
- [x] Ein erledigtes To-do bietet nur „Öffnen“
- [x] Dasselbe Menü haben die To-dos im Tab To-dos; ein To-do ohne Fälligkeit bekommt dort durch das Verschieben ein Datum ohne Uhrzeit

### Routine

- [x] Offene Routine: **Überspringen · Heute nicht · Öffnen**; der Textknopf „Überspringen“ in der Zeile entfällt
- [x] Übersprungen oder ausgelassen: „Rückgängig“ bleibt als Knopf in der Zeile, das Menü bietet „Öffnen“; erledigt oder verpasst: nur „Öffnen“

### Daten & Tests

- [x] `Todos` bekommt eine Methode, die nur das Datum der Fälligkeit setzt und den alten Stand für „Rückgängig“ kennt; `Heute` reicht sie durch
- [x] Unit-Tests: Morgen, Wochenende an jedem Wochentag, Nächste Woche, Uhrzeit bleibt, Weckzeit wandert, Rückgängig, wiederkehrend, Erinnerung folgt; UI-Tests: Menü öffnen, verschieben, Rückgängig, „Öffnen“ führt in den Editor, kein Knopf „Überspringen“ mehr in der Zeile

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befunde U4 und U7, Vorschlag UX 2); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Vom heutigen Tag aus gerechnet: Ein seit einer Woche überfälliges To-do soll mit „Morgen“ wirklich morgen fällig sein.
- „Überspringen“ ins Menü: In der Zeile war es das auffälligste Wort, öfter als jede Uhrzeit.
- Antippen hakt weiter ab; „Öffnen“ liegt im Menü, damit sich an der gewohnten Geste nichts ändert.

Bei der Umsetzung festgelegt (13.11.2026):

- „Am Wochenende“ ist der nächste Samstag nach heute: vom Montag bis Freitag der dieser Woche, vom Samstag und Sonntag aus der danach.
- „Datum wählen“ beginnt beim Tag der Fälligkeit, bei einem überfälligen oder ohne Fälligkeit bei heute; Tage vor heute lassen sich nicht wählen.
- „Rückgängig“ setzt die alte Fälligkeit nur zurück, solange das To-do offen und noch so fällig ist, wie das Verschieben es gesetzt hat; wurde es inzwischen erledigt oder anders geändert, bleibt es.
- Auch eine durch eine gekoppelte Routine erledigte Routine bietet nur „Öffnen“, wie eine erledigte.
- Die Weckzeit „N Tage vorher“ wandert von selbst, da sie am Tag der Fälligkeit hängt; eine feste Weckzeit bleibt, wie im Editor.
- Im Tab To-dos öffnet ein langes Drücken auf die Zeile dasselbe Menü; Antippen öffnet dort weiter den Editor.
