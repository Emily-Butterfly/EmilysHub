# 84 – Sprachen: Suche & Prüfungen an Zielen

**Etappe:** 12 – Budget-Rechner, Abos & Sprachen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Themen & Prüfungen: Ziel; Auswertung & Aufbau), §6, §8  
**GitHub-Issue:** #84  
**Blockiert durch:** #82

## Was gebaut wird

Der Bereich Sprachen schließt an den Rest der App an: Die Suche findet Sessions, und eine Prüfung kann als Teil an einem Ziel hängen.

## Akzeptanzkriterien

### Suche

- [ ] Die Suche über alle Bereiche findet Sessions nach Notiz, Themen und Name des Lehrers, in einem eigenen Abschnitt „Sprachen“; ein Treffer zeigt Datum, Sprache und Lehrer und öffnet die Session
- [ ] Es gelten die Regeln der Suche (Groß-/Kleinschreibung, ß/ss, unsichtbare Zeichen, mehr als 50 Treffer)

### Prüfung am Ziel

- [ ] Der Editor der Prüfung hat ein Feld „Ziel“ wie beim To-do: höchstens eins
- [ ] Am Ziel steht die Prüfung als eigene Zeile, „Prüfung: Arabisch A2 · 18 von 30 Themen“; Antippen öffnet die Themen der Prüfung
- [ ] Sie zählt als Teil wie ein To-do: Ihr Fortschritt ist der Anteil abgehakter Themen (ohne Themen 0 %), erledigt ist sie, sobald sie abgelegt ist; ein von Hand gesetzter Fortschritt am Ziel geht vor
- [ ] Abgelegt erledigt ein Ziel, dessen übrige Teile erledigt sind; „abgelegt“ zurückgenommen öffnet es wieder, wie bei einem To-do (über `GoalWatch`)
- [ ] Eine neue Prüfung unter einem erledigten Ziel öffnet es wieder; eine gelöschte verschwindet aus dem Ziel; wird das Ziel gelöscht, bleibt die Prüfung ohne Ziel; „Löschen, Teile rücken auf“ nimmt sie mit nach oben
- [ ] Themen abhaken, öffnen, zuordnen und verschieben ändert den Fortschritt des Ziels
- [ ] Backups enthalten die Verknüpfung

### Tests

- [ ] Unit-Tests: Treffer nach Notiz, Thema und Lehrer; Fortschritt aus Themen; abgelegt und zurück mit Ziel erledigt und wieder offen; neue Prüfung öffnet erledigtes Ziel; Löschen auf beiden Seiten; manueller Fortschritt geht vor. UI-Tests: Session über die Suche öffnen; Prüfung im Zielbaum

## Entscheidungen

Abgestimmt im Interview vom 11.11.2026.

- Die Prüfung hängt am Ziel, nicht die Sprache; kein Stundenziel.
- Sie zählt für den Fortschritt, nicht nur als Verweis.
