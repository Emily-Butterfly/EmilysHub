# 55 – Sonstiges: Ziele wandern unter „Sonstiges“

**Etappe:** 10 – Sonstiges, Heute & Dokumentation  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1 (Navigation), §6  
**GitHub-Issue:** #55  
**Blockiert durch:** –

## Was gebaut wird

Der Tab „Ziele“ verschwindet aus der unteren Leiste. An seiner Stelle steht ganz rechts der Tab **„Sonstiges“**: eine Liste, die die Ziele öffnet und mit #58 auch die Dokumentation. Die Ziele öffnen im Tab, die untere Leiste bleibt dabei sichtbar.

## Akzeptanzkriterien

- [ ] Untere Leiste in dieser Reihenfolge: Heute · Routinen · To-dos · Notizen · **Sonstiges**, mit einem eigenen Symbol für Sonstiges (z. B. drei Punkte)
- [ ] Der Tab Sonstiges zeigt eine Liste mit großen Einträgen, zunächst nur **🎯 Ziele**; obere Leiste mit dem Titel „Sonstiges“, Suche und Einstellungen wie in den anderen Tabs
- [ ] „Ziele“ öffnet die Ziele **im Tab**: Die untere Leiste bleibt sichtbar, „Sonstiges“ ist dort ausgewählt, die obere Leiste zeigt „Ziele“ mit einem Zurück-Pfeil zur Liste
- [ ] Die Ziele können alles wie bisher: Tag-Filter, Rückblick, Anlegen, Öffnen, verknüpfte To-dos
- [ ] Wechselst du in einen anderen Tab und kommst zurück, sind die Ziele noch offen, auch nach Drehen und nach der App-Sperre (wie `TabViews`)
- [ ] Ein erneuter Tipp auf „Sonstiges“, während die Ziele offen sind, führt zur Liste
- [ ] Die Zurück-Taste führt aus den Zielen zur Liste und von der Liste nach Heute, wie aus den anderen Tabs
- [ ] Treffer der Suche, verknüpfte Ziele bei To-dos und Routinen und der Rückblick öffnen ihre Ziele wie bisher
- [ ] UI-Tests: fünf Tabs in der neuen Reihenfolge; Ziele über Sonstiges öffnen und zurück; Ziele bleiben beim Tabwechsel offen; erneuter Tipp auf den Tab führt zur Liste

## Entscheidungen

Abgestimmt im Interview vom 07.11.2026.

- „Sonstiges“ steht ganz rechts als Sammelplatz; „Ziele“ verlässt die Leiste.
- Ziele und Dokumentation öffnen im Tab, nicht als Bildschirm über den Tabs, damit die untere Leiste bleibt.
- Das Haushaltsbuch kommt nicht zusätzlich in die Liste; es bleibt beim Knopf in Notizen und dem „€“ in Heute (#56).
