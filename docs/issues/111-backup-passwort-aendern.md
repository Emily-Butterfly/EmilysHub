# 111 – Backup-Passwort ändern

**Etappe:** 14 – Kleinigkeiten aus dem Gutachten  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §10 (Backup-Passwort)  
**GitHub-Issue:** #111  
**Blockiert durch:** –

## Was gebaut wird

Das Backup-Passwort lässt sich im Backup-Bildschirm ändern. Bisher wird es einmal festgelegt und lässt sich danach nur ändern, indem ein Backup mit einem anderen Passwort eingespielt wird; wird es bekannt, gibt es keinen Ausweg (Gutachten, Befund T5).

## Akzeptanzkriterien

### Ändern

- [x] Der Backup-Bildschirm hat, sobald ein Passwort festgelegt ist, „Passwort ändern“
- [x] Gefragt werden das alte Passwort und zweimal das neue; für das neue gelten dieselben Regeln wie beim Festlegen
- [x] Das alte Passwort wird gegen den gespeicherten Schlüssel geprüft (gleiches Salz, gleiche Runden); ist es falsch, sagt das der Dialog, und nichts ändert sich
- [x] Danach entsteht aus dem neuen Passwort ein neuer Schlüssel mit neuem Salz, wie beim ersten Festlegen im Android Keystore geschützt; alle künftigen Backups nutzen ihn, die von Hand und die nächtlichen
- [x] Der Dialog sagt vorher, dass schon geschriebene Backup-Dateien mit dem alten Passwort lesbar bleiben und dass sie dafür das alte Passwort brauchen

### Vergessen

- [x] Im Dialog führt „Passwort vergessen?“ dazu, ein neues festzulegen, ohne das alte zu kennen; davor steht die App-Sperre, auch wenn die App gerade entsperrt ist
- [x] Davor sagt eine Rückfrage, dass schon geschriebene Backups ohne das alte Passwort nicht mehr lesbar sind und dass das nächste Backup bald geschrieben werden sollte
- [x] Danach gilt der neue Schlüssel wie beim Ändern

### Sonst

- [x] Ein Abbruch an irgendeiner Stelle lässt das alte Passwort, wie es war; ein Fehler beim Schreiben des neuen Schlüssels ebenso
- [x] Das Einspielen eines Backups fragt weiter nach dem Passwort, mit dem es geschrieben wurde
- [x] SPEC §10 sagt, dass sich das Passwort ändern lässt, mit dem alten oder nach der App-Sperre
- [x] Unit-Tests: Ändern mit richtigem und falschem altem Passwort; ein Backup vor und eins nach dem Ändern lassen sich je mit ihrem Passwort einspielen; Vergessen legt ein neues fest; ein Abbruch lässt das alte; UI-Tests: beide Wege im Backup-Bildschirm

## Entscheidungen

- **Entschieden am 14.11.2026:** Zum Ändern wird das alte Passwort verlangt. Wer es vergessen hat, legt nach der App-Sperre ein neues fest, mit dem Hinweis, dass alte Backups dann nicht mehr lesbar sind.
- Nicht einzeln bestätigt: Die App-Sperre steht beim Vergessen auch dann davor, wenn die App schon entsperrt ist, damit ein kurz unbeaufsichtigtes Handy nicht reicht, um ein fremdes Passwort unterzuschieben.

Bei der Umsetzung festgelegt (15.11.2026):

- **Entschieden am 15.11.2026:** Ohne Displaysperre gibt es keine App-Sperre, die fragen könnte; dann bietet der Dialog „Passwort vergessen?“ nicht an, sondern sagt, dass erst eine Displaysperre einzurichten ist. Sonst könnte jemand mit dem offenen Handy ein eigenes Passwort festlegen und alle Daten in ein Backup holen, das nur er öffnen kann.
- Nach einem Drehen oder dem Ende des App-Prozesses kommt der Schritt nach der App-Sperre nicht von selbst zurück: Die Rückfrage erscheint wieder, und die App-Sperre wird neu gefragt.
