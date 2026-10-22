# 26 – Suche über alle Bereiche

**Etappe:** 7 – Statistik, Suche & Backups  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §8  
**GitHub-Issue:** #26  
**Blockiert durch:** [17 – To-dos](17-todos.md), [19 – Wunschliste](19-wunschliste.md), [22 – Notiz-Tabs & Markdown-Notizen](22-notiz-tabs-markdown.md)

## Was gebaut wird

Eine Suche, die Routinen, To-dos, Wünsche und Notizen gleichzeitig durchsucht.

## Akzeptanzkriterien

- [ ] Suchfeld erreichbar von jedem Tab
- [ ] Durchsucht Titel, Beschreibungen, Notiztext und Tags
- [ ] Ergebnisse nach Bereich gruppiert; Tippen öffnet den Eintrag
- [ ] Volltextsuche in der Datenbank; Suchbegriffe werden parametrisiert und escaped
