# 44 – Haushaltsbuch: Buchungen, Kategorien, Monatsansicht

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1, §13  
**GitHub-Issue:** #44  
**Blockiert durch:** –

## Was gebaut wird

Ein Haushaltsbuch für Einnahmen und Ausgaben je Monat, mit eigenen Kategorien und einer Monatsansicht mit Summen, Balken und Tortendiagramm. Es öffnet sich über einen Knopf oben im Tab Notizen.

## Akzeptanzkriterien

- [ ] Ein Knopf oben im Tab Notizen öffnet das Haushaltsbuch; Zurück führt in die Notizen
- [ ] Buchung anlegen, ändern und löschen (mit Rückfrage): Einnahme oder Ausgabe, Betrag in Euro mit Cent (größer als 0), Datum (Standard heute), Kategorie, Beschreibung optional
- [ ] Beträge als „1.234,56 €“, Eingabe mit Komma; gerechnet wird in Cent, ohne Rundungsfehler
- [ ] Namen und Beschreibungen werden geprüft wie die anderen Eingaben der App
- [ ] Kategorien je Art, zum Start: Ausgaben Lebensmittel, Wohnen, Mobilität, Freizeit, Gesundheit, Kleidung, Sonstiges; Einnahmen Gehalt, Sonstiges
- [ ] Kategorien anlegen, umbenennen und löschen; Löschen einer Kategorie mit Buchungen fragt nach und verschiebt sie nach „Sonstiges“ ihrer Art; die beiden „Sonstiges“ sind nicht löschbar
- [ ] Monatsansicht, vorne der aktuelle Monat, Wechsel zum vorigen und nächsten: Einnahmen, Ausgaben, Saldo; Ausgaben je Kategorie als Balken (größte zuerst) und als Tortendiagramm mit Legende; darunter alle Buchungen nach Datum
- [ ] Das Tortendiagramm ist selbst gezeichnet, ohne neue Bibliothek, und in hellem wie dunklem Design lesbar
- [ ] Die Suche findet Buchungen nach Beschreibung und Kategorie
- [ ] Backups enthalten Buchungen und Kategorien; ältere Backups laden weiter
- [ ] Unit-Tests: Summen und Saldo, Monatsgrenzen, Kategorie löschen, Betragseingabe

## Entscheidungen

- Einnahmen und Ausgaben, Euro mit Cent.
- Eigene Kategorien mit Vorschlägen, getrennt nach Einnahmen und Ausgaben.
- Kein eigener Tab: Zugang über den Tab Notizen.
