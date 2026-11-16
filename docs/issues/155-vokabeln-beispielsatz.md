# 155 – Vokabeltrainer: Beispielsatz mit Lücke

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer)  
**GitHub-Issue:** #155  
**Blockiert durch:** –

## Was gebaut wird

Ein Wort sitzt besser im Zusammenhang. Eine Karte bekommt optional einen Beispielsatz. Fragt die Rückrichtung nach dem Wort, steht der Satz mit einer Lücke an seiner Stelle als Hilfe da.

## Akzeptanzkriterien

- [ ] Neues optionales Feld „Beispielsatz“ an der Karte, bis 500 Zeichen, in der Sprache der Vorderseite; Text von rechts nach links wie die anderen Felder
- [ ] Hinrichtung: aufgedeckt steht der Satz unter der Rückseite, das Wort darin hervorgehoben
- [ ] Rückrichtung (#141): unter der Frage steht der Satz mit „…“ an der Stelle der Vorderseite; aufgedeckt mit dem Wort hervorgehoben
- [ ] Das Wort wird im Satz gesucht wie in der Suche (Groß- und Kleinschreibung und Vokalzeichen zählen nicht); kommt es nicht vor (gebeugt), steht der Satz in beiden Richtungen erst nach dem Aufdecken da, und der Editor sagt: „Die Vorderseite steht so nicht im Satz; er wird erst nach dem Aufdecken gezeigt.“
- [ ] Import aus Datei: fünftes optionales Feld nach der Notiz (#105); Suche findet den Satz (#107)
- [ ] Schema-Version eins hoch, Migration mit Test, im Backup (`docs/agents/schema-change.md`)
- [ ] SPEC.md §15 nennt das Feld

## Tests

- [ ] Unit: Lücke mit Großschreibung und Vokalzeichen, Wort nicht im Satz, Import mit fünftem Feld, Länge
- [ ] UI: Rückrichtung mit Lücke, Hinrichtung aufgedeckt

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Nicht einzeln bestätigt: die Lücke nur in der Rückrichtung, weil die Hinrichtung das Wort schon zeigt.
