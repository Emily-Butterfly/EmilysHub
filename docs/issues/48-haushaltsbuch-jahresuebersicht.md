# 48 – Haushaltsbuch: Jahresübersicht

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §13  
**GitHub-Issue:** #48  
**Blockiert durch:** [44 – Haushaltsbuch: Buchungen, Kategorien, Monatsansicht](44-haushaltsbuch.md)

## Was gebaut wird

Eine Jahresübersicht zeigt je Monat Einnahmen, Ausgaben und Saldo und die Ausgaben je Kategorie über das ganze Jahr.

## Akzeptanzkriterien

- [ ] Jahresübersicht je Kalenderjahr, zwischen den Jahren wechselbar
- [ ] Je Monat Einnahmen, Ausgaben und Saldo, dazu die Summen des Jahres
- [ ] Ausgaben je Kategorie über das Jahr mit Summe und Anteil, größte zuerst
- [ ] Offene und abgeschlossene Monate zählen gleich
- [ ] Tippen auf einen Monat öffnet seine Monatsansicht
- [ ] Unit-Tests: Summen über Monate und über den Jahreswechsel

## Entscheidungen

- Summen je Monat und je Kategorie; weitere Auswertungen bei Bedarf als eigenes Issue.
