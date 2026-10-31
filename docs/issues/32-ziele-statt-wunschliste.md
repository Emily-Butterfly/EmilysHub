# 32 – „Wunschliste“ heißt „Ziele“

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1, §6  
**GitHub-Issue:** #32  
**Blockiert durch:** –

## Was gebaut wird

Der Bereich Wunschliste heißt überall „Ziele“: im Tab, in allen Texten und in der Suche. An den Daten ändert sich nichts.

## Akzeptanzkriterien

- [ ] Tab in der unteren Leiste: „Ziele“ statt „Wunschliste“
- [ ] Alle Texte sprechen von Zielen statt von Wünschen: Überschriften, leere Liste, „Ziel anlegen“, „Neues Ziel“, „Ziel bearbeiten“, „Ziel löschen?“, Tag-Filter, Rückblick („erreichte Ziele“), Suche (Bereich „Ziele“, Hinweistext), Zusammenfassung eines Backups („3 Ziele“) und Bedienungshilfen
- [ ] Das „Routinen-Ziel“ einer Routine behält seinen Namen
- [ ] Daten, Unterziele, Fortschritt, Verknüpfungen und Backups bleiben, wie sie sind; keine Datenbank-Migration
- [ ] In den Texten der App steht kein „Wunsch“ und keine „Wünsche“ mehr; Tests, die nach den alten Texten suchen, sind angepasst

## Entscheidungen

- Umbenannt wird überall, nicht nur der Tab.
- Das Haushaltsbuch bekommt keinen eigenen Tab, sondern einen Knopf oben im Tab Notizen (#44).
