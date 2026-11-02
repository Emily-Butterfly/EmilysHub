# 51 – Haushaltsbuch: voraussichtlicher Endsaldo

**Etappe:** 9 – Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §13  
**GitHub-Issue:** #51  
**Blockiert durch:** [50 – Haushaltsbuch: Saldo übertragen, Anfangssaldo & Knopf in Notizen](50-haushaltsbuch-saldo-uebertrag.md)

## Was gebaut wird

Der laufende Monat zeigt, mit welchem Saldo er voraussichtlich endet: sein Endsaldo samt den Fixbuchungen, die bis zum Monatsende noch eingetragen werden.

## Akzeptanzkriterien

- [ ] Im aktuellen, offenen Monat unter den Summen die Zeile „Voraussichtlicher Endsaldo“ mit dem Zusatz „mit den Fixbuchungen bis zum Monatsende“
- [ ] Betrag = Anfangssaldo (keiner zählt als 0 €) + Einnahmen − Ausgaben bis jetzt + Fixbuchungen, die nach heute bis zum Monatsletzten noch eingetragen werden; Einnahmen wie Ausgaben
- [ ] Schon eingetragene Fixbuchungen zählen nicht doppelt; eine eingetragene und gelöschte kommt auch in der Vorhersage nicht wieder
- [ ] Fixbuchungen mit anderem Abstand (vierteljährlich usw.) zählen nur in dem Monat, in dem sie fällig sind
- [ ] Die Zeile erscheint nur im aktuellen Monat, solange er offen ist und darin noch eine Fixbuchung kommt; sonst wäre sie gleich dem Endsaldo
- [ ] Unit- und UI-Tests: Vorhersage mit kommenden Einnahmen und Ausgaben, nicht in anderen Monaten, gelöschte Fixbuchung nicht wieder

## Entscheidungen

- Auf Wunsch vom 02.11.2026; Einnahmen-Fixbuchungen (z. B. Gehalt) zählen mit, damit die Vorhersage stimmt.
