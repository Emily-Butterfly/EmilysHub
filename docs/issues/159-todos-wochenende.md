# 159 – To-dos: „Am Wochenende“ gilt Samstag und Sonntag

**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1 (Menü je Eintrag)  
**GitHub-Issue:** #159  
**Blockiert durch:** –

## Was gebaut wird

Ein To-do, das mit „Am Wochenende“ verschoben ist, war am Samstag fällig und am Sonntag schon überfällig. Es soll das ganze Wochenende gelten.

## Akzeptanzkriterien

- [x] „Am Wochenende“ verschiebt auf den kommenden Samstag (an einem Samstag oder Sonntag den nächsten), und das To-do ist bis zu dessen Sonntag fällig
- [x] Samstag und Sonntag steht es bei Heute als To-do von heute; eine Uhrzeit gilt nur am Samstag
- [x] Überfällig erst ab Montag, bei Heute und im Reiter To-dos, auch mit Uhrzeit
- [x] Ein über „Datum wählen“ gewählter Samstag ist nur dieser Tag; ein anderes Datum im Menü oder im Editor hebt das Wochenende auf, „Rückgängig“ stellt es wieder her
- [x] Das nächste To-do einer Reihe ist wieder an seinem Tag fällig
- [x] Schema-Version eins hoch (`todos.due_until`), Migration mit Test, im Backup
- [x] SPEC.md §1 nennt es

## Tests

- [x] Unit: Ziel des Verschiebens; überfällig Samstag/Sonntag/Montag mit und ohne Uhrzeit; Rückgängig, anderes Datum, Editor; Heute am Samstag, Sonntag und Montag
- [x] UI: verschoben im Reiter To-dos, am Sonntag bei Heute nicht überfällig

## Entscheidungen

Am 22.11.2026 im Chat gewünscht und gebaut (Commit 0c773eb). To-dos, die vor dem Update aufs Wochenende verschoben wurden, gelten nur am Samstag, bis sie erneut verschoben werden.
