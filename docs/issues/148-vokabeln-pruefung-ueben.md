# 148 – Vokabeltrainer: Prüfung üben

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Themen & Prüfungen; Vokabeltrainer: Themen)  
**GitHub-Issue:** #148  
**Blockiert durch:** –

## Was gebaut wird

„Thema üben“ (#107) fragt die Karten eines Themas ab. Vor einer Prüfung will man alle ihre Themen üben, bisher Thema für Thema. Eine Prüfung bekommt „Prüfung üben“.

## Akzeptanzkriterien

- [x] Im Reiter Themen bietet das Menü einer Prüfung „Prüfung üben“, sobald ihre Themen Karten haben
- [x] Abgefragt werden alle Karten der Themen der Prüfung, in der Reihenfolge von „Thema üben“: die fälligen zuerst (am längsten fällige zuerst), dann die neuen in der Reihenfolge des Anlegens, dann die übrigen, die mit der nächsten Fälligkeit zuerst
- [x] Jede Antwort zählt wie bei „Thema üben“: neue Karten zählen nicht gegen die Grenze am Tag; Hin- und Rückrichtung nie am selben Tag (#141)
- [x] Die Abfrage sieht aus wie die tägliche; am Ende „Prüfung geübt: N Karten“
- [x] Auch eine abgelegte Prüfung lässt sich üben; Karten einer archivierten Sprache nicht
- [x] Verlassen und wieder „Prüfung üben“ setzt fort, wie bei „Thema üben“
- [x] SPEC.md §15 nennt „Prüfung üben“

## Tests

- [x] Unit: Reihenfolge über mehrere Themen; Karten ohne Thema und anderer Prüfungen zählen nicht; abgelegte Prüfung; archivierte Sprache
- [x] UI: Menüpunkt, üben bis zum Ende

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Nicht einzeln bestätigt: dieselben Regeln wie „Thema üben“, damit es nur eine Art des vorzeitigen Übens gibt.
- Hinweis zum Bau: `Flashcards.topicPractice` lässt sich auf eine Menge von Themen verallgemeinern; die Abfrage bekommt statt `topicId` eine Auswahl.
