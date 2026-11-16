# 145 – Vokabeltrainer: letzte Antwort zurücknehmen

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer: Abfrage)  
**GitHub-Issue:** #145  
**Blockiert durch:** –

## Was gebaut wird

Jede Antwort ist sofort gespeichert. Ein Fehltipp („Leicht“ statt „Nochmal“) plant eine Karte für Wochen falsch und lässt sich nicht zurücknehmen. Die Abfrage bekommt „Rückgängig“ für die letzte Antwort.

## Akzeptanzkriterien

- [x] Oben in der Leiste der Abfrage ein Knopf „Letzte Antwort zurücknehmen“ (Symbol Rückgängig), aktiv ab der ersten Antwort der Runde
- [x] Er stellt die Richtung der Karte her, wie sie vor der Antwort war: Stabilität, Schwierigkeit, erste und letzte Abfrage, Fälligkeit, Abstand; der Eintrag im Protokoll wird gelöscht
- [x] Die Karte wird wieder gezeigt, verdeckt; „7 von 23“ und die Runde („Nochmal“ am Ende) stehen wie vor der Antwort
- [x] Nur die letzte Antwort, eine Stufe; nur solange die Abfrage offen ist (auch nach Drehen oder Sperren), nicht nach dem Verlassen
- [x] Karten-Ziele gleichen sich an (`GoalWatch`), wie bei jeder Antwort
- [x] Hat die Antwort das Pensum geschafft und dadurch eine Routine erledigt (#106), bleibt die Routine erledigt; in Heute lässt sie sich wie jede andere zurücknehmen
- [x] Beim Thema üben genauso

## Tests

- [x] Unit: Zurücknehmen stellt die Planung her und löscht genau den Protokolleintrag; neue Karte wird wieder neu und zählt nicht mehr gegen die Grenze am Tag; Rückrichtung (#141); Karten-Ziel fällt wieder darunter
- [x] UI: antworten, zurücknehmen, dieselbe Karte verdeckt, Position wie vorher

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Nicht einzeln bestätigt: ein Knopf in der Leiste statt einer Snackbar, weil eine Snackbar nach Sekunden verschwindet und den Bereich der Antworten verdeckt.
- Nicht einzeln bestätigt: eine durch das Pensum erledigte Routine bleibt erledigt; das Zurücknehmen einer Routine bleibt in Heute.
- Hinweis zum Bau: `Flashcards.answer` kann die Karte vor der Antwort und die Id des Protokolleintrags zurückgeben; die Abfrage hält beides wie die Runde.
