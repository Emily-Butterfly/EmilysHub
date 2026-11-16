# 147 – Vokabeltrainer: Bilanz am Ende der Runde und weiter zur nächsten Sprache

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer: Abfrage)  
**GitHub-Issue:** #147  
**Blockiert durch:** –

## Was gebaut wird

Am Ende steht nur „Für heute geschafft: 23 Karten“ und „Zurück“. Wer mehrere Sprachen übt, muss zurück zur Startseite und dort die nächste öffnen. Das Ende zeigt eine kurze Bilanz und bietet die nächste Sprache an.

## Akzeptanzkriterien

- [ ] Unter „Für heute geschafft: 23 Karten“ steht „19 gewusst · 4 Nochmal“: je abgefragter Richtung des Tages ihre erste Antwort an diesem Tag, aus dem Protokoll, also auch nach Verlassen und Fortsetzen richtig
- [ ] Hat eine andere nicht archivierte Sprache heute noch Offenes, steht über „Zurück“ der Knopf „Weiter mit Italienisch (12)“; er öffnet deren Abfrage an Stelle dieser; bei mehreren die mit dem meisten Offenen
- [ ] Beim Thema üben (und Prüfung üben) nur die Bilanz, keine nächste Sprache

## Tests

- [ ] Unit: Bilanz aus dem Protokoll mit „Nochmal“ und Wiederholung in der Runde
- [ ] UI: Ende mit nächster Sprache, die sich öffnet; ohne Offenes nur „Zurück“

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Nicht einzeln bestätigt: die erste Antwort je Richtung zählt, damit eine Wiederholung nach „Nochmal“ das Bild nicht schönt.
