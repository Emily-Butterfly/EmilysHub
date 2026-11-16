# 143 – Vokabeltrainer: fällig und neu getrennt zählen

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Auswertung & Aufbau: Startseite; Vokabeltrainer: Reiter Karten)  
**GitHub-Issue:** #143  
**Blockiert durch:** –

## Was gebaut wird

Die Startseite sagt bisher nur „Heute fällig: 23“. Ob darunter neue Karten sind, die mehr Zeit brauchen als Wiederholungen, sieht man erst beim Üben. Die Zeile zählt beides getrennt.

## Akzeptanzkriterien

- [ ] Die Zeile einer Sprache auf der Startseite heißt „Heute: 18 fällig · 5 neu“; ohne neue „Heute: 18 fällig“, nur mit neuen „Heute: 5 neu“; geschafft weiter „Für heute geschafft“
- [ ] Dieselbe Zeile oben im Reiter Karten (`WorkloadLine`, seit 32ce029)
- [ ] Gezählt wird, was das Tagespensum noch offen hat (`Workload.remaining`): neu ist eine Richtung, die noch nie abgefragt wurde (`Prompt.new`), alles andere ist fällig; jede Richtung einer Karte in beide Richtungen zählt für sich (#141)
- [ ] Die Auswertung („Karten: 312 · sicher 120 · heute fällig 23“) bleibt, wie sie ist
- [ ] SPEC.md §15 nennt die neue Zeile

## Tests

- [ ] Unit: Zählung mit fälligen, neuen und beiden Richtungen
- [ ] UI: Startseite mit beidem, nur fällig, nur neu

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Nicht einzeln bestätigt: getrennt statt einer Summe, weil neue Karten mehr Zeit brauchen.
