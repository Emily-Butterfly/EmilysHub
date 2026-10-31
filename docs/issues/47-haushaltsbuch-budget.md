# 47 – Haushaltsbuch: Budget je Kategorie

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §13  
**GitHub-Issue:** #47  
**Blockiert durch:** [44 – Haushaltsbuch: Buchungen, Kategorien, Monatsansicht](44-haushaltsbuch.md), [46 – Haushaltsbuch: Monatsabschluss & Archiv](46-haushaltsbuch-abschluss-archiv.md)

## Was gebaut wird

Je Ausgaben-Kategorie lässt sich ein monatliches Budget festlegen; die Monatsansicht zeigt, wie viel davon verbraucht ist.

## Akzeptanzkriterien

- [ ] Je Ausgaben-Kategorie optional ein monatliches Budget in Euro
- [ ] Monatsansicht: Der Balken der Kategorie zeigt den Verbrauch am Budget mit „noch 87,50 € übrig“; ab 80 % orange, über 100 % rot mit „12,50 € über Budget“
- [ ] Beim Speichern einer Buchung, die ein Budget überschreitet, ein kurzer Hinweis
- [ ] Keine Benachrichtigungen
- [ ] Ein geändertes Budget gilt für alle offenen Monate; ein abgeschlossener Monat zeigt das Budget, das beim Abschließen galt
- [ ] Backups enthalten die Budgets; ältere Backups laden weiter (ohne)
- [ ] Unit-Tests: Stufen bei 80 % und 100 %, Rest und Überschreitung, Budget beim Abschluss festgehalten

## Entscheidungen

- Hinweis in der Ansicht und beim Speichern, keine Benachrichtigung.
- Budgets gibt es nur für Ausgaben.
