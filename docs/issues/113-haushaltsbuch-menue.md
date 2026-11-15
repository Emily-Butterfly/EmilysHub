# 113 – Haushaltsbuch: ein Menü mit Text statt vier Symbolen

**Etappe:** 14 – Kleinigkeiten aus dem Gutachten  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §13 (Haushaltsbuch)  
**GitHub-Issue:** #113  
**Blockiert durch:** –

## Was gebaut wird

Die obere Leiste des Haushaltsbuchs zeigt statt vier Symbolen ohne Text ein Menü mit Text. Bisher stehen dort Schloss (Archiv), Kalender (Fixbuchungen), Kreispfeil (Abos) und Liste (Kategorien); ihre Bedeutung muss man raten, und mit jedem neuen Bereich kommt eins dazu (Gutachten, Befund U9).

## Akzeptanzkriterien

- [x] In der Leiste steht nur noch ein Menü-Knopf (⋮), für Screenreader „Mehr“
- [x] Das Menü nennt in dieser Reihenfolge: Fixbuchungen, Abos, Kategorien, Archiv; jeder Eintrag öffnet, was bisher sein Symbol geöffnet hat
- [x] Sonst ändert sich am Haushaltsbuch nichts
- [x] SPEC §13 nennt, wo die vier Bereiche zu finden sind, falls es sie dort nach Symbolen nennt
- [x] UI-Tests: jeder Menüeintrag öffnet seinen Bildschirm; die Leiste hat keins der vier Symbole mehr

## Entscheidungen

- **Entschieden am 14.11.2026:** Ein Menü mit Text, und alle vier stehen darin; in der Leiste bleibt kein eigenes Symbol.
- Nicht einzeln bestätigt: die Reihenfolge im Menü, vom Häufigsten zum Archiv.
