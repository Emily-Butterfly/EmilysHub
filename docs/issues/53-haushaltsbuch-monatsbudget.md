# 53 – Haushaltsbuch: Budget für einzelne Monate ändern

**Etappe:** 9 – Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §13  
**GitHub-Issue:** #53  
**Blockiert durch:** –

## Was gebaut wird

Das Budget einer Ausgaben-Kategorie lässt sich für einen einzelnen Monat ändern, z. B. „Geschenke im Dezember 300 € statt 50 €“, ohne das monatliche Budget anzufassen.

## Akzeptanzkriterien

- [ ] Monatsansicht eines offenen Monats: Antippen eines Kategorie-Balkens öffnet „Budget für Lebensmittel im Dezember 2026“; „Budgets für Dezember 2026 ändern“ listet alle Ausgaben-Kategorien, auch solche ohne Budget und ohne Ausgaben
- [ ] Ein Monatsbudget kann ein anderer Betrag sein, ein neues Budget für eine Kategorie ohne monatliches, oder „kein Budget“ (Feld leer) für eine mit monatlichem
- [ ] Der Dialog sagt „Nur für diesen Monat“ und nennt das monatliche Budget; „Wie sonst“ nimmt das Monatsbudget zurück. Gleich dem monatlichen gespeichert, hat der Monat kein eigenes und folgt späteren Änderungen des monatlichen
- [ ] Jeder offene Monat, auch künftige; ein abgeschlossener bietet nichts zum Ändern
- [ ] Ein geändertes monatliches Budget gilt in allen offenen Monaten außer denen mit eigenem
- [ ] Beim Abschließen hält der Monat sein Budget fest, das eigene, wenn er eins hat; wieder geöffnet gilt sein eigenes wieder
- [ ] Der Hinweis beim Speichern einer Buchung rechnet mit dem Budget ihres Monats
- [ ] Die Anzeige unterscheidet ein Monatsbudget nicht von einem monatlichen
- [ ] Eine gelöschte Kategorie nimmt ihre Monatsbudgets mit
- [ ] Backups enthalten die Monatsbudgets; ältere Backups laden weiter (ohne)
- [ ] Unit-Tests: Betrag, neu und ausgesetzt; monatliches geändert; gleich dem monatlichen; Abschluss und Wiederöffnen; Hinweis beim Speichern; UI-Test in der Monatsansicht

## Entscheidungen

- Auf Wunsch vom 02.11.2026: in der Monatsansicht, nicht in den Kategorien; alle drei Arten; jeder offene Monat; kein eigener Hinweis am Balken.
