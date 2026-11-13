# 100 – Rettungsbildschirm, wenn sich die Daten nicht öffnen lassen

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §10 (Wenn sich die Daten nicht öffnen lassen), §9  
**GitHub-Issue:** #100  
**Blockiert durch:** –

## Was gebaut wird

Geht die Datenbank beim Start nicht auf, zeigt die App einen Bildschirm mit Auswegen, statt bei jedem Öffnen abzustürzen. Bisher lässt sich in diesem Fall nicht einmal ein Backup laden, weil das nur in der App geht.

## Akzeptanzkriterien

### Erkennen

- [x] Scheitert das Öffnen der Datenbank beim Start, zeigt die App den Bildschirm „Deine Daten lassen sich nicht öffnen“. Das gilt für: Schlüsseldatei unlesbar oder beschädigt, Schlüssel im Android Keystore verloren, Datenbankdatei beschädigt oder mit anderem Schlüssel, Schema neuer als die App
- [x] Er nennt den Grund in einem Satz, aus einer von drei Gruppen: Der Schlüssel fehlt oder ist beschädigt · Die Datei ist beschädigt · Die Daten stammen aus einer neueren Version der App. Keine technischen Einzelheiten, nichts aus den Daten
- [x] Er sagt, dass bis dahin keine Erinnerungen kommen
- [x] Die App-Sperre steht davor; ohne lesbare Einstellung gilt sie wie bisher als an
- [x] Im Hintergrund (Wecker, Neustart des Handys, Empfänger) führt derselbe Fehler zu keinem Absturz und wird wie bisher ohne Inhalte protokolliert

### Auswege

- [x] **Erneut versuchen** öffnet noch einmal
- [x] **Backup laden**: Dateiwähler und Passwort wie im Backup-Bildschirm; das Backup wird in eine neue Datenbank mit neuem Schlüssel gespielt, danach startet die App normal. Ein Backup mit Dokumentation fragt wie sonst nach dem Doku-Passwort oder spielt ohne sie ein
- [x] **Neu beginnen**, nach einer Rückfrage: Die App startet leer, wie neu installiert, also auch ohne Backup-Passwort und ohne Ordner für die nächtlichen Backups
- [x] Bei Daten aus einer neueren Version steht zuerst der Hinweis, die neuere App zu installieren; die anderen Auswege stehen darunter
- [x] „Backup laden“ und „Neu beginnen“ löschen die unlesbaren Dateien (Datenbank, Schlüsseldatei, Anhänge mit ihrem Schlüssel) nicht, sondern legen sie beiseite. Schlägt das Einspielen fehl, bleibt alles, wie es war
- [x] Der Backup-Bildschirm zeigt beiseitegelegte Daten mit ihrem Datum und „Endgültig löschen“, mit Rückfrage
- [x] Die Dokumentation mit ihrer eigenen Datenbank bleibt unberührt und lässt sich danach wie zuvor öffnen

### Tests

- [x] Robolectric: Eine Datenbank, die beim Öffnen wirft, zeigt je Grund den Bildschirm; Erneut versuchen; Backup laden führt in die App mit den Daten des Backups; Neu beginnen legt beiseite und startet leer; ein fehlgeschlagenes Einspielen lässt alles; die Dokumentation bleibt
- [ ] Auf dem Emulator: beschädigte Schlüsseldatei; Schema-Version höher als die der App (geschrieben in `OpenOnPhoneTest`, noch nicht auf einem Gerät gelaufen)

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befund T2); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Beiseitelegen statt löschen: Ein Problem mit dem Keystore kann vorübergehend sein, und ein Fehlgriff soll nichts zerstören.
- Kein Ausweg geschieht von selbst; die App entscheidet nie allein, Daten aufzugeben.
