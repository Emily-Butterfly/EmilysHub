# 22 – Notiz-Tabs & Markdown-Notizen

**Etappe:** 6 – Notizen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §7, §9  
**GitHub-Issue:** #22  
**Blockiert durch:** [03 – Verschlüsselte Datenbank](03-verschluesselte-datenbank.md)

## Was gebaut wird

Notizen in Tabs, geschrieben in Markdown mit sicherer Vorschau.

## Akzeptanzkriterien

- [ ] Fester Tab „Alle“; Standard-Tab „Allgemein“ (nicht löschbar); eigene Tabs anlegen, umbenennen, sortieren
- [ ] Tab löschen mit Rückfrage: Notizen nach „Allgemein“ verschieben oder mitlöschen
- [ ] Notiz: Titel und Markdown-Text, Bearbeiten und Vorschau
- [ ] Markdown-Rendering ohne HTML; Links werden nicht geöffnet
- [ ] Tests mit bösartigen Eingaben (HTML, `javascript:`-Links, sehr große Texte)
