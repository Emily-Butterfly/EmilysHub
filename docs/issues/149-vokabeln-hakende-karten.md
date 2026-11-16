# 149 – Vokabeltrainer: hakende Karten erkennen

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer)  
**GitHub-Issue:** #149  
**Blockiert durch:** –

## Was gebaut wird

Manche Karten bekommen immer wieder „Nochmal“ und kosten viel Zeit, ohne dass sie sitzen. Meist hilft es, sie umzuschreiben: eine Eselsbrücke in der Notiz, eine Karte in zwei teilen. Solche Karten werden markiert („hakt“), damit man sie findet. Anki nennt sie „Leech“.

## Akzeptanzkriterien

- [x] Eine Richtung einer Karte **hakt**, wenn unter ihren letzten 8 Antworten (ohne ihre allererste) mindestens 4-mal „Nochmal“ steht; eine Karte hakt, wenn eine ihrer Richtungen hakt (#141). Gerechnet aus dem Protokoll, ohne neues Feld; wird die Karte danach gewusst, verschwindet die Marke von selbst
- [x] Reiter Karten: Marke „hakt“ in der Zeile in der Fehlerfarbe; ein Filter „Hakt“ neben den Themen, solange es solche Karten gibt
- [x] Karten-Editor: oben der Hinweis „Diese Karte hakt: In letzter Zeit oft „Nochmal“. Eine Eselsbrücke in der Notiz oder eine Aufteilung hilft oft.“
- [x] Auswertung: die Zeile der Karten nennt „· hakt 3“, wenn es welche gibt
- [x] Die Abfrage zeigt nichts davon, und hakende Karten werden weiter abgefragt

## Tests

- [x] Unit: Grenze 3 und 4 von 8; die erste Antwort zählt nicht; je Richtung; Marke verschwindet nach gewussten Antworten; gelöschte Rückrichtung (#141)
- [x] UI: Marke und Filter im Reiter Karten; Hinweis im Editor

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Nicht einzeln bestätigt: ein gleitendes Fenster statt eines Zählers wie bei Anki, damit nichts zurückgesetzt werden muss und kein neues Feld nötig ist.
- Nicht einzeln bestätigt: keine automatische Sperre der Karte, nur die Marke.
