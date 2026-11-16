# 154 – Vokabeltrainer: Vokalzeichen in der Abfrage ausblenden

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer: Abfrage)  
**GitHub-Issue:** #154  
**Blockiert durch:** –

## Was gebaut wird

Arabisch wird meist ohne Vokalzeichen gelesen. Karten stehen oft mit ihnen da, und die Abfrage verrät damit die Aussprache. Je Sprache lassen sie sich in der Frage ausblenden, als schwierigere Stufe.

## Akzeptanzkriterien

- [ ] Je Sprache im Menü des Reiters Karten: „Vokalzeichen in der Frage ausblenden“, Standard aus; angeboten, sobald eine Karte der Sprache Vokalzeichen hat
- [ ] Eingeschaltet steht die Seite in der Sprache in der Frage ohne Vokalzeichen (dieselben Zeichen, die `InputText` beim Suchen übergeht, dazu das Tatweel wie dort); aufgedeckt steht sie mit ihnen da
- [ ] In der Rückrichtung (#141) ändert sich nichts: gefragt ist die Übersetzung, aufgedeckt die Seite in der Sprache mit Vokalzeichen
- [ ] Gespeichert wird nichts an der Karte
- [ ] Die Einstellung je Sprache: Schema-Version eins hoch, Migration mit Test, im Backup (`docs/agents/schema-change.md`)

## Tests

- [ ] Unit: Ausblenden lässt Buchstaben stehen
- [ ] UI: Frage ohne, aufgedeckt mit Vokalzeichen

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Nicht einzeln bestätigt: je Sprache statt je Karte, weil es eine Stufe des Lernens ist und nicht eine Eigenschaft eines Wortes.
- Andere Schriften mit Vokalzeichen (Hebräisch) kommen dazu, sobald es eine solche Sprache gibt.
