# 161 – Mein Warum: Board mit Text-Gründen

**Etappe:** 18 – Mein Warum  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §18 (Mein Warum: Gründe, Board); §1 (Navigation)  
**GitHub-Issue:** #161  
**Blockiert durch:** –

## Was gebaut wird

Ein neuer Bereich in Sonstiges mit einem Board aus Text-Gründen: anschauen, groß öffnen, bearbeiten, sortieren, löschen.

## Akzeptanzkriterien

- [ ] Sonstiges hat **Mein Warum** (🌱) als fünften Eintrag; öffnet im Tab und bleibt beim Wechsel des Tabs offen, wie Ziele und Sprachen; hinter der App-Sperre, ohne eigene Sperre
- [ ] Leer: „Hier sammelst du, was dich hält: Menschen, Momente, Pläne, Sätze.“ und „Ersten Grund hinzufügen“
- [ ] Ein **Grund** mit schlichtem Text bis 500 Zeichen, mittig, von rechts nach links, wenn er so beginnt; ganz leer wird er nicht gespeichert
- [ ] Ansicht beim Öffnen immer ohne Knöpfe außer dem Stift: zwei Spalten, jede Kachel so hoch wie ihr Inhalt, in der selbst gelegten Reihenfolge, ein neuer Grund oben
- [ ] Antippen öffnet einen Grund groß, Wischen führt zum nächsten
- [ ] Bearbeiten: hinzufügen, ändern, verschieben durch langes Drücken und Ziehen und über „Nach vorn“ / „Nach hinten“ im Menü eines Grundes; löschen mit Rückfrage („Diesen Grund löschen?“)
- [ ] Nichts hält fest, dass Mein Warum geöffnet wurde; nicht in der Suche (§8)
- [ ] Die Gründe im Backup: Schema-Version eins hoch, Migration mit Test (`docs/agents/schema-change.md`)
- [ ] SPEC.md §1, §8, §10 und §18 nennen es

## Tests

- [ ] Unit: ein leerer Grund wird abgelehnt, die Länge; Reihenfolge (neu oben, verschieben); löschen
- [ ] UI: Eintrag in Sonstiges, leeres Board, Grund anlegen, groß öffnen und wischen, verschieben über das Menü, löschen mit Rückfrage; die Suche findet keinen Grund

## Entscheidungen

Am 24.11.2026 mit der Nutzerin abgestimmt (SPEC.md §18, Entscheidungen).
