# 39 – Klingelton je Routine und To-do

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §3  
**GitHub-Issue:** #39  
**Blockiert durch:** [36 – Vollbild-Wecker für To-dos](36-todo-wecker.md)

## Was gebaut wird

Jede Routine und jedes To-do kann einen eigenen Ton haben, für den Vollbild-Wecker wie für die Benachrichtigung; in den Einstellungen gibt es dazu je einen Standard.

## Akzeptanzkriterien

- [ ] Feld „Ton“ in den Editoren von Routinen und To-dos: „Standard“ oder ein Ton aus Androids Tonauswahl (Wecker-, Klingel- und Benachrichtigungstöne des Handys, auch „Lautlos“), mit Probehören
- [ ] Mit Vollbild-Alarm klingelt der Ton in Schleife wie ein Wecker; ohne spielt die Benachrichtigung ihn einmal
- [ ] Einstellungen: ein Standard-Ton für Wecker (anfangs der Wecker-Ton des Handys) und einer für Benachrichtigungen (anfangs der Benachrichtigungston des Handys)
- [ ] „Lautlos“ heißt beim Wecker: nur Vibration
- [ ] Benachrichtigungen mit eigenem Ton laufen über einen eigenen Benachrichtigungskanal je Ton; Kanäle, die kein Eintrag mehr nutzt, räumt die App weg
- [ ] Ein geänderter Ton gilt ab der nächsten Erinnerung
- [ ] Fehlt ein gewählter Ton (gelöscht, neues Handy), gilt der Standard
- [ ] Die Vibration bleibt wie bisher
- [ ] Wiederkehrende To-dos: der Ton wandert mit
- [ ] Backups enthalten die Wahl, nicht die Tondatei; ältere Backups laden weiter (Standard)
- [ ] Tests: Kanal je Ton, Standard, ein fehlender Ton fällt auf den Standard zurück, der Wecker spielt den gewählten Ton

## Entscheidungen

- Ein Ton je Eintrag für Wecker und Benachrichtigung; je ein Standard für beide Arten.
- Auch normale Erinnerungen können einen eigenen Ton haben, über einen Kanal je Ton.
