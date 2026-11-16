# 144 – Vokabeltrainer: Kartenliste mit voller Breite für Vorder- und Rückseite

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer: Reiter Karten)  
**GitHub-Issue:** #144  
**Blockiert durch:** –

## Was gebaut wird

Im Reiter Karten stehen Fälligkeit, „sicher“ und Thema rechts neben dem Text („fällig 18.11.26 · sicher · Farben“). Auf einem schmalen Handy drückt das Vorder- und Rückseite zusammen, die dann früh abgeschnitten werden. Die Angaben bekommen eine eigene Zeile, und die Liste nennt, wie viele Karten sie zeigt.

## Akzeptanzkriterien

- [x] Jede Zeile: Vorderseite (bis zwei Zeilen), darunter die Rückseite (eine Zeile), darunter Fälligkeit · sicher · Thema klein; rechts daneben steht nichts mehr
- [x] „fällig heute“ steht in der Primärfarbe; „neu“ und „sicher“ wie bisher als Text
- [x] Text in Schriften von rechts nach links steht weiter rechts (`DirectedBy`), die Angaben-Zeile in der Richtung der App
- [x] Über der Liste steht „312 Karten“; mit Themenfilter oder Suche „12 von 312 Karten“
- [x] Antippen öffnet die Karte wie bisher

## Tests

- [x] UI (w320dp): eine lange Vorderseite nutzt die volle Breite; Angaben unter dem Text; Anzahl mit und ohne Filter

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Nicht einzeln bestätigt: drei Zeilen statt Angaben rechts, wie die Listen in Material 3 es für längere Texte vorsehen.
