# 152 – Eingang: als Karte einsortieren

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §16 (Eingang: Einsortieren); §15 (Vokabeltrainer: Anlegen)  
**GitHub-Issue:** #152  
**Blockiert durch:** –

## Was gebaut wird

Im Unterricht fällt ein Wort, und der Karten-Editor ist zu weit weg. Im Eingang (§16) ist es schnell festgehalten, auch über den Kurzbefehl oder die Benachrichtigung. Beim Einsortieren gibt es dann „Karte“.

## Akzeptanzkriterien

- [x] Jeder Eintrag im Eingang bietet „To-do · Notiz · Routine · Karte · Löschen“; „Karte“ nur, solange es eine nicht archivierte Sprache gibt
- [x] „Karte“ öffnet den Karten-Editor; bei mehreren Sprachen zuerst die Wahl der Sprache, die zuletzt für eine Karte gewählte oben
- [x] Der Text wird zerlegt wie bei „Karten aus der Notiz“ (#105): liest sich die erste Zeile als Vorder- und Rückseite („kitab – Buch“, „ = “, Tabulator), sind das die Seiten, sonst ist die erste Zeile die Vorderseite; der Rest wird Notiz
- [x] Der Eintrag verschwindet erst, wenn die Karte gespeichert ist, wie bei den anderen (§16); „Speichern und neue“ gibt es von hier aus nicht
- [x] SPEC.md §16 nennt „Karte“

## Tests

- [x] Unit: Zerlegen mit jedem Trennzeichen, ohne Trennzeichen, mit weiteren Zeilen
- [x] UI: Eintrag als Karte einsortieren mit einer und mit zwei Sprachen; Eintrag verschwindet erst nach dem Speichern

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Beim Bau: „die zuletzt für eine Karte gewählte“ Sprache ist die der zuletzt angelegten Karte (unter den nicht archivierten), aus den Karten gelesen, ohne eigenes Feld.
- Nicht einzeln bestätigt: kein eigener Kurzbefehl „Neue Vokabel“; der Eingang reicht und hält die Zahl der Wege klein.
