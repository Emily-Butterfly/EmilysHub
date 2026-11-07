# 56 – Heute: „€“ öffnet das Haushaltsbuch

**Etappe:** 10 – Sonstiges, Heute & Dokumentation  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1 (Navigation, Heute-Übersicht), §13  
**GitHub-Issue:** #56  
**Blockiert durch:** –

## Was gebaut wird

In der oberen Leiste von Heute sitzt ein kleines „€“, das den laufenden Monat im Haushaltsbuch öffnet.

## Akzeptanzkriterien

- [ ] Im Tab Heute steht in der oberen Leiste ein kleiner Knopf „€“, so groß wie die Symbole daneben, links neben der Flamme; ohne Flamme links neben der Lupe
- [ ] Er öffnet die Monatsansicht des laufenden Monats, wie der Knopf in Notizen; Zurück führt nach Heute
- [ ] Für die Bedienungshilfe heißt er „Haushaltsbuch öffnen“
- [ ] Er erscheint nur auf Heute; der Knopf in der Mitte der Notizen-Leiste bleibt, wie er ist
- [ ] Titel „Heute“, Flamme, Lupe und Zahnrad überlappen sich mit ihm nicht, auch bei großer Systemschrift
- [ ] UI-Tests: Knopf nur auf Heute, öffnet den laufenden Monat, Name für die Bedienungshilfe

## Entscheidungen

Abgestimmt im Interview vom 07.11.2026.

- Klein als Symbol neben der Flamme statt groß in der Mitte wie in Notizen.
- Er öffnet die Monatsansicht, nicht direkt „Neue Buchung“: Dort ist eine neue Buchung einen Tipp entfernt, und Saldo und Budget sind gleich zu sehen.
