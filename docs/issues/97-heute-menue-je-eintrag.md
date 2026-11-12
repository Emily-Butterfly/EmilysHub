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

- [ ] Jeder Eintrag in Heute hat rechts ein Menü, auch unter Überfällig, Schon vorbei und Demnächst; langes Drücken auf die Zeile öffnet dasselbe. Antippen der Zeile hakt weiter ab
- [ ] „Öffnen“ führt in den Editor des To-dos bzw. der Routine; zurück landet man wieder in Heute
- [ ] Die Abos unter Demnächst bleiben, wie sie sind; der Vortag (Gestern nachtragen) bekommt kein Menü

### To-do

- [ ] **Morgen · Am Wochenende · Nächste Woche · Datum wählen · Öffnen**
- [ ] Gerechnet wird vom heutigen Tag aus, nicht von der alten Fälligkeit: „Morgen“ ist der Tag nach heute, „Am Wochenende“ der kommende Samstag (am Samstag und Sonntag der Samstag danach), „Nächste Woche“ der kommende Montag
- [ ] Verschieben ändert nur das Datum der Fälligkeit: Die Uhrzeit bleibt, eine Weckzeit „N Tage vorher“ wandert mit, alles andere bleibt
- [ ] Danach sagt eine Leiste, wohin verschoben wurde („Auf Sa., 24. Okt. verschoben“), und bietet „Rückgängig“
- [ ] Eine laufende Erinnerung des To-dos verschwindet; die neue Fälligkeit erinnert, wie nach einer Änderung im Editor
- [ ] Bei einem wiederkehrenden To-do verschiebt sich nur dieses eine, wie beim Ändern der Fälligkeit im Editor
- [ ] Ein erledigtes To-do bietet nur „Öffnen“
- [ ] Dasselbe Menü haben die To-dos im Tab To-dos; ein To-do ohne Fälligkeit bekommt dort durch das Verschieben ein Datum ohne Uhrzeit

### Routine

- [ ] Offene Routine: **Überspringen · Heute nicht · Öffnen**; der Textknopf „Überspringen“ in der Zeile entfällt
- [ ] Übersprungen oder ausgelassen: „Rückgängig“ bleibt als Knopf in der Zeile, das Menü bietet „Öffnen“; erledigt oder verpasst: nur „Öffnen“

### Daten & Tests

- [ ] `Todos` bekommt eine Methode, die nur das Datum der Fälligkeit setzt und den alten Stand für „Rückgängig“ kennt; `Heute` reicht sie durch
- [ ] Unit-Tests: Morgen, Wochenende an jedem Wochentag, Nächste Woche, Uhrzeit bleibt, Weckzeit wandert, Rückgängig, wiederkehrend, Erinnerung folgt; UI-Tests: Menü öffnen, verschieben, Rückgängig, „Öffnen“ führt in den Editor, kein Knopf „Überspringen“ mehr in der Zeile

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befunde U4 und U7, Vorschlag UX 2); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Vom heutigen Tag aus gerechnet: Ein seit einer Woche überfälliges To-do soll mit „Morgen“ wirklich morgen fällig sein.
- „Überspringen“ ins Menü: In der Zeile war es das auffälligste Wort, öfter als jede Uhrzeit.
- Antippen hakt weiter ab; „Öffnen“ liegt im Menü, damit sich an der gewohnten Geste nichts ändert.
