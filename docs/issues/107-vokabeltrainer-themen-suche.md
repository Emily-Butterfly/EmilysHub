# 107 – Vokabeltrainer: Zahlen an Themen, Thema üben & Suche

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer: Themen), §8 (Suche)  
**GitHub-Issue:** #107  
**Blockiert durch:** #104

## Was gebaut wird

Die Themen einer Sprache zeigen, wie viele ihrer Karten sitzen, und lassen sich gezielt üben, etwa vor einer Prüfung. Die Suche findet Karten.

## Akzeptanzkriterien

### Themen

- [x] Im Reiter Themen zeigt jedes Thema mit Karten „12 Karten · 5 sicher“, neben dem, was es schon zeigt
- [x] Die nächste anstehende Prüfung zeigt dazu „Karten: 40 von 120 sicher“, die Summe über ihre Themen; ohne Karten nichts
- [x] Antippen der Zahl öffnet den Reiter Karten, auf das Thema gefiltert
- [x] Das Menü eines Themas bietet „Thema üben“: Es fragt alle Karten des Themas ab, die fälligen zuerst, dann die mit der nächsten Fälligkeit; die Abfrage sieht aus wie die tägliche (#104)
- [x] Jede Antwort zählt wie im Tagespensum, auch bei einer Karte, die noch nicht fällig war: FSRS rechnet ein, dass sie früh kommt, und lässt ihre Stabilität dann nur wenig wachsen (#104). Neue Karten des Themas zählen dabei nicht gegen die Grenze für neue Karten am Tag
- [x] Auch ein abgehaktes Thema und ein Thema einer abgelegten Prüfung lassen sich üben

### Suche & Auswertung

- [x] Die Suche über alle Bereiche findet Karten nach Vorderseite, Rückseite, Umschrift und Notiz, im Bereich Sprachen und mit der Sprache dabei; ein Treffer öffnet die Karte
- [x] Arabisch wird mit und ohne Vokalzeichen gefunden, wie in den Notizen
- [x] Der Reiter Karten bekommt ein Suchfeld über die Karten der Sprache
- [x] Der Reiter Auswertung nennt die Karten der Sprache in einer Zeile: „Karten: 312 · sicher 120 · heute fällig 23“; weiter ohne Diagramme

### Tests

- [x] Unit-Tests: Zahlen je Thema und Prüfung, Thema üben mit fälligen und nicht fälligen Karten, Suche über alle Felder, Arabisch mit und ohne Vokalzeichen; UI-Tests: Zahl am Thema, Thema üben, ein Treffer der Suche öffnet die Karte

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Feature 3); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Keine Sonderregel für vorzeitiges Üben: Der Algorithmus aus #104 rechnet den kurzen Abstand selbst ein.
- Eine feste Reihenfolge statt einer zufälligen, damit die Abfrage sich fortsetzen und prüfen lässt.
