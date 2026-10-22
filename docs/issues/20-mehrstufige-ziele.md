# 20 – Mehrstufige Ziele mit To-dos & Fortschritt

**Etappe:** 5 – Wunschliste / Ziele  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §6  
**GitHub-Issue:** #20  
**Blockiert durch:** [17 – To-dos](17-todos.md), [19 – Wunschliste](19-wunschliste.md)

## Was gebaut wird

Wünsche werden zu Zielen mit beliebig tiefen Unterzielen und verknüpften To-dos – z. B. „Arabisch A2 erreichen“.

## Akzeptanzkriterien

- [ ] Unterziele beliebig tief; eingerückt und auf-/zuklappbar
- [ ] To-dos an Ziele hängen; „In To-do umwandeln“ erzeugt ein verknüpftes To-do
- [ ] Fortschritt automatisch aus erledigten Unterpunkten (rekursiv), manuell überschreibbar und zurücksetzbar
- [ ] Ein vollständig erledigtes Ziel erscheint im Rückblick
- [ ] Schutz gegen Zyklen (ein Ziel kann nicht unter sich selbst hängen)
- [ ] Unit-Tests für Fortschrittsberechnung und Zyklus-Schutz
