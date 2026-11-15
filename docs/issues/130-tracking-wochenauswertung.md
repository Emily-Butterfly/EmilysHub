# 130 – Tracking: Wochenauswertung & Verlauf

**Etappe:** 15 – Tracking  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Wochenauswertung & Verlauf)  
**GitHub-Issue:** #130  
**Blockiert durch:** [127 – Stehtest-Assistent mit Auswertung](127-tracking-stehtest.md), [128 – Ereignisse mit Puls-Timer & Hautangaben](128-tracking-ereignisse.md), [129 – Anstrengungen, Vorschläge, PEM- & Crash-Episoden](129-tracking-anstrengungen-episoden.md)

## Was gebaut wird

Kennzahlen je Woche und Liniendiagramme über 4 oder 12 Wochen, aus allen Einträgen und Garmin-Werten.

## Akzeptanzkriterien

- [ ] **Wochenauswertung** (Mo–So), Woche wählbar, alle Kennzahlen nach §17, je mit Anzahl der zugrunde liegenden Einträge; fehlende Daten als „–“, nie als 0
- [ ] Anteil nachgetragener Einträge sichtbar
- [ ] **Verlauf:** je Symptom, Ruhepuls am Morgen und Garmin-Werte ein Liniendiagramm über 4 oder 12 Wochen, Lücken bei fehlenden Tagen; Medikamentenänderungen als senkrechte Markierung, Episoden als Band; Stufe-C-Werte beschriftet „Garmin-Schätzung“
- [ ] Diagramme wie in der Dokumentation, mit den Farben des Themes, ohne neue Bibliothek
- [ ] Rechenlogik ohne Android

## Tests

- [ ] Unit: jede Kennzahl mit vollständigen und lückenhaften Wochen, Wochengrenzen, Zeitumstellung; Markierungen und Bänder
- [ ] UI: Woche wechseln, Verlauf 4/12 Wochen

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Wochen Mo–So, Verlauf 4 oder 12 Wochen wie im Entwurf.
