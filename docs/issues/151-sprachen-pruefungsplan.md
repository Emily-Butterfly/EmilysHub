# 151 – Sprachen: Tempo bis zur Prüfung

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Themen & Prüfungen)  
**GitHub-Issue:** #151  
**Blockiert durch:** –

## Was gebaut wird

Die nächste Prüfung zeigt „noch 43 Tage · 12 von 30 offen“. Ob das reicht, muss man selbst rechnen. Die Zeile nennt das nötige Tempo.

## Akzeptanzkriterien

- [x] Die Zeile der nächsten Prüfung (Startseite und Reiter Themen) bekommt „· etwa 2 Themen pro Woche“: offene Themen geteilt durch die verbleibenden Wochen (Tage bis zur Prüfung durch 7, mindestens 1), aufgerundet
- [x] In der letzten Woche vor der Prüfung statt dessen „· 12 Themen bis zur Prüfung“
- [x] Ohne offene Themen, am Tag der Prüfung, danach und bei abgelegten Prüfungen nichts davon
- [x] Im Zielbaum (#84) bleibt die Zeile der Prüfung, wie sie ist
- [x] SPEC.md §15 nennt das Tempo

## Tests

- [x] Unit: Rechnung an den Grenzen (7 und 8 Tage, 1 Tag, aufrunden)
- [x] UI: Zeile mit Tempo

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Nicht einzeln bestätigt: Themen pro Woche statt pro Tag, weil Unterricht meist wöchentlich ist.
