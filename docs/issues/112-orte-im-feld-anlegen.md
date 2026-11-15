# 112 – Orte: einen neuen Ort direkt im Feld anlegen

**Etappe:** 14 – Kleinigkeiten aus dem Gutachten  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §4 (Orte), §8 (Übergreifend, Editoren)  
**GitHub-Issue:** #112  
**Blockiert durch:** –

## Was gebaut wird

Im Feld „Orte“ des Routine- und des To-do-Editors lässt sich ein neuer Ort anlegen, ohne den Editor zu verlassen. Bisher zeigt das Feld ohne Ort nur „Orte legst du in den Einstellungen unter ‚Orte verwalten‘ an.“, eine Sackgasse mitten im Formular; Tags und Lehrer lassen sich dagegen an Ort und Stelle anlegen (Gutachten, Befund U8).

## Akzeptanzkriterien

- [ ] Das Feld „Orte“ hat in beiden Editoren „+ Neuer Ort“, mit und ohne schon angelegte Orte; der Hinweis auf die Einstellungen entfällt
- [ ] „+ Neuer Ort“ öffnet den Orts-Editor über dem offenen Editor; dessen Entwurf bleibt erhalten, auch beim Drehen und über die App-Sperre, wie beim Öffnen einer Notiz aus einem To-do
- [ ] Nach dem Speichern ist der neue Ort im Feld gewählt, und es geht im Editor weiter, wo es war
- [ ] Abbrechen im Orts-Editor führt ohne neuen Ort zurück; am Feld ändert sich nichts
- [ ] Der Orts-Editor fragt die Berechtigungen (WLAN, Standort) wie bisher, und seine Regeln bleiben, wie sie sind
- [ ] UI-Tests: ein neuer Ort aus dem Routine- und aus dem To-do-Editor ist danach gewählt; der Entwurf übersteht den Umweg; Abbrechen lässt alles

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befund U8); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Der Orts-Editor öffnet als eigener Bildschirm, nicht als Dialog, weil er Berechtigungen fragt und WLAN oder Standort erfasst.
