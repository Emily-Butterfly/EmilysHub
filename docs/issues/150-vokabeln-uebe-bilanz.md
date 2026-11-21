# 150 – Vokabeltrainer: Übe-Bilanz und Vorschau in der Auswertung

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Auswertung & Aufbau; Vokabeltrainer)  
**GitHub-Issue:** #150  
**Blockiert durch:** –

## Was gebaut wird

Die Auswertung zählt Karten, aber nicht, wie regelmäßig geübt wird und was ansteht. Sie bekommt beides in Zahlen, ohne Diagramme (§15).

## Akzeptanzkriterien

- [x] Reiter Auswertung, unter „Karten: 312 · sicher 120 · heute fällig 23“, je Zeitraum wie bei den Sessions: „Diesen Monat an 12 von 16 Tagen geübt · 240 Abfragen · 85 % gewusst“, für Jahr und insgesamt „an 120 Tagen geübt · …“
- [x] Ein Tag zählt als geübt, wenn das Protokoll an ihm eine Antwort zu einer Karte der Sprache hat, nach dem Kalender des Handys (`Day`); „von 16 Tagen“ sind die Tage des Monats bis heute
- [x] „gewusst“ ist der Anteil der Antworten außer „Nochmal“, ohne die allererste Antwort einer Richtung (da war sie noch nicht gelernt)
- [x] Vorschau: „Morgen fällig: 14 · nächste 7 Tage: 60“: die Richtungen, deren Fälligkeit auf morgen bzw. die nächsten sieben Tage fällt, ohne neue Karten
- [x] Bei einer archivierten Sprache keine Vorschau; ohne Karten nichts davon
- [x] Keine Diagramme

## Tests

- [x] Unit: geübte Tage über Mitternacht und Monatsgrenze, Anteil gewusst ohne erste Antworten, Vorschau mit beiden Richtungen
- [x] UI: Zeilen in der Auswertung

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Zahlen statt Diagramm, weil §15 keine Diagramme vorsieht.
