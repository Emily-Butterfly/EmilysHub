# 153 – Vokabeltrainer: Antwort eintippen

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer: Abfrage)  
**GitHub-Issue:** #153  
**Blockiert durch:** –

## Was gebaut wird

Beim Aufdecken prüft man sich selbst, und „ungefähr gewusst“ wird schnell zu „Gut“. Wer die Antwort eintippt, übt die Schreibweise mit, gerade in einer anderen Schrift. Die Abfrage bekommt diesen Weg als Wahl je Sprache.

## Akzeptanzkriterien

- [ ] Je Sprache im Menü des Reiters Karten: „Abfrage: aufdecken / eintippen“, Standard aufdecken; gilt für die tägliche Abfrage, Thema üben und Prüfung üben
- [ ] Eintippen: unter der Frage ein Eingabefeld mit offener Tastatur; „Prüfen“ (oder Enter) deckt auf und vergleicht; „Weiß nicht“ deckt auf wie eine falsche Antwort
- [ ] Verglichen wird wie in der Suche (`InputText.searchable`): Groß- und Kleinschreibung, Leerzeichen am Rand und arabische Vokalzeichen zählen nicht. Hat die gesuchte Seite mehrere durch Komma oder Semikolon getrennte Bedeutungen („Buch, Heft“), reicht eine
- [ ] Richtig: „Richtig“, und „Gut“ ist hervorgehoben; nur in den Vokalzeichen anders: „Richtig, bis auf die Vokalzeichen“ mit beiden Schreibweisen; falsch: die Eingabe durchgestrichen, die richtige Antwort darunter, „Nochmal“ hervorgehoben
- [ ] Bewertet wird weiter von Hand: alle vier Antworten bleiben, die Hervorhebung ist nur ein Vorschlag
- [ ] In der Rückrichtung (#141) wird die Seite in der Sprache getippt; das Feld bittet die Tastatur um die Sprache (`hintLocales`), wo sie bekannt ist
- [ ] Die Eingabe wird nicht gespeichert
- [ ] Die Wahl je Sprache: Schema-Version eins hoch, Migration mit Test, im Backup (`docs/agents/schema-change.md`)

## Tests

- [ ] Unit: Vergleich mit Großschreibung, Vokalzeichen, mehreren Bedeutungen, Leerzeichen
- [ ] UI: eintippen richtig, falsch, „Weiß nicht“; Rückrichtung

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Nicht einzeln bestätigt: der Vergleich schlägt nur vor; die Bewertung bleibt bei der Person, weil Tippfehler und Synonyme sonst falsch planen.
- Offen beim Bau: welcher Sprachcode zu einer Sprache gehört, steht nirgends; ohne ihn bleibt `hintLocales` leer.
