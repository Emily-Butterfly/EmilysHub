# 40 – Eigene Audiodatei als Ton

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §3  
**GitHub-Issue:** #40  
**Blockiert durch:** [39 – Klingelton je Routine und To-do](39-klingelton.md)

## Was gebaut wird

Als Ton lässt sich auch eine eigene Audiodatei wählen, z. B. eine MP3. Die App kopiert sie in die Töne des Handys, damit sie auch in Benachrichtigungen klingt.

## Akzeptanzkriterien

- [ ] In der Tonauswahl zusätzlich „Eigene Datei…“: eine Audiodatei über Androids Dateidialog wählen
- [ ] Die App kopiert sie in die Töne des Handys (als Wecker- bzw. Benachrichtigungston); sie erscheint dann auch in Androids Tonauswahl und bleibt, wenn das Original gelöscht wird
- [ ] Sie klingt im Vollbild-Wecker und in Benachrichtigungen
- [ ] Ohne neue Berechtigung
- [ ] Dateien, die keine abspielbare Audiodatei oder größer als 20 MB sind, lehnt die App mit einem Hinweis ab
- [ ] Dieselbe Datei zweimal gewählt landet nur einmal in den Tönen
- [ ] Backups enthalten nur die Wahl; fehlt der Ton auf einem neuen Handy, gilt der Standard
- [ ] Tests: Kopie in den Tönen, die Wahl zeigt auf die Kopie, abgelehnte Dateien

## Entscheidungen

- In die Töne des Handys kopieren, weil Android eigene Töne nur so auch für Benachrichtigungen spielt.
