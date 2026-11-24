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

- [x] Sonstiges hat **Mein Warum** (🌱) als fünften Eintrag; öffnet im Tab und bleibt beim Wechsel des Tabs offen, wie Ziele und Sprachen; hinter der App-Sperre, ohne eigene Sperre
- [x] Leer: „Hier sammelst du, was dich hält: Menschen, Momente, Pläne, Sätze.“ und „Ersten Grund hinzufügen“
- [x] Ein **Grund** mit schlichtem Text bis 500 Zeichen, mittig, von rechts nach links, wenn er so beginnt; ganz leer wird er nicht gespeichert
- [x] Ansicht beim Öffnen immer ohne Knöpfe außer dem Stift: zwei Spalten, jede Kachel so hoch wie ihr Inhalt, in der selbst gelegten Reihenfolge, ein neuer Grund oben
- [x] Antippen öffnet einen Grund groß, Wischen führt zum nächsten
- [x] Bearbeiten: hinzufügen, ändern, verschieben durch langes Drücken und Ziehen und über „Nach vorn“ / „Nach hinten“ im Menü eines Grundes; löschen mit Rückfrage („Diesen Grund löschen?“)
- [x] Nichts hält fest, dass Mein Warum geöffnet wurde; nicht in der Suche (§8)
- [x] Die Gründe im Backup: Schema-Version eins hoch, Migration mit Test (`docs/agents/schema-change.md`)
- [x] SPEC.md §1, §8, §10 und §18 nennen es

## Tests

- [x] Unit: ein leerer Grund wird abgelehnt, die Länge; Reihenfolge (neu oben, verschieben); löschen
- [x] UI: Eintrag in Sonstiges, leeres Board, Grund anlegen, groß öffnen und wischen, verschieben über das Menü, löschen mit Rückfrage; die Suche findet keinen Grund

## Entscheidungen

Am 24.11.2026 mit der Nutzerin abgestimmt (SPEC.md §18, Entscheidungen).
