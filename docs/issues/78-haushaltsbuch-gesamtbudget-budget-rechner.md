# 78 – Haushaltsbuch: Gesamtbudget & Budget-Rechner

**Etappe:** 12 – Budget-Rechner, Abos & Sprachen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §13 (Gesamtbudget, Budget-Rechner, Monatsabschluss, Jahresübersicht)  
**GitHub-Issue:** #78  
**Blockiert durch:** –

## Was gebaut wird

Die Budgets der Ausgaben-Kategorien bekommen ein **Gesamtbudget** über sich und einen **Budget-Rechner**: eine Seite, die alle Budgets zusammenrechnet und gegen das Gesamtbudget stellt, damit du sie anpassen kannst, bis es passt.

## Akzeptanzkriterien

### Gesamtbudget

- [x] Standard für alle offenen und kommenden Monate: **keins** (wie bisher), ein **fester Betrag** (Euro mit Cent, größer als 0) oder **aus Einnahmen** mit einem **Sparbetrag** (Standard 0 €)
- [x] „Aus Einnahmen“ = die im Monat eingetragenen Einnahmen + die Fixbuchungen, die bis zum Monatsende noch als Einnahme kommen, − Sparbetrag; nie unter 0 €; der Anfangssaldo zählt nicht
- [x] Jeder offene Monat, auch künftige, kann abweichen: eigener Betrag, „aus Einnahmen“ mit eigenem Sparbetrag oder keins; „Wie sonst“ nimmt es zurück. Ein geänderter Standard gilt in allen offenen Monaten außer denen mit eigenem
- [x] Beim Abschließen hält der Monat sein Gesamtbudget als Betrag fest; wieder geöffnet gilt seine Einstellung wieder, auch der Standard von damals: ein inzwischen geänderter Standard gilt für ihn nicht
- [x] Monatsansicht: oben ein **Gesamtbalken** „ausgegeben von Gesamtbudget“ mit den Farben der Kategorie-Balken (ab 80 % orange, darüber rot mit „… über Budget“); ohne Gesamtbudget keiner
- [x] Kurzer Hinweis beim Speichern einer Buchung, die das Gesamtbudget ihres Monats überschreitet, neben dem der Kategorie; keine Benachrichtigungen

### Budget-Rechner

- [x] Eine Seite mit allen Ausgaben-Kategorien, je einem Budget-Feld, der laufenden Summe, dem Gesamtbudget und der Differenz („50 € frei“ oder rot „120 € drüber“); ohne Gesamtbudget nur die Summe
- [x] Neben jedem Feld der Durchschnitt der Ausgaben der Kategorie in den letzten drei abgeschlossenen Monaten (weniger, wenn es weniger gibt; keiner ohne abgeschlossenen Monat)
- [x] Kategorien ohne Budget heißen „ohne Budget“ und zählen 0 €; ein leeres Feld nimmt das Budget weg
- [x] Die Felder prüft `BudgetInput` (ledger/Budget.kt); mit einem Problem in einem Feld lässt sich nicht speichern
- [x] Gespeichert wird alles auf einmal, auch über dem Gesamtbudget; nichts wird von selbst gekürzt; Verlassen ohne Speichern verwirft
- [x] Einstieg aus den **Kategorien**: bearbeitet die monatlichen Budgets gegen das Standard-Gesamtbudget und lässt dieses ändern; ist es „aus Einnahmen“, rechnet die Seite mit dem Wert des laufenden Monats und sagt das
- [x] Einstieg aus einer **offenen Monatsansicht**: bearbeitet die Budgets dieses Monats gegen sein Gesamtbudget und lässt dieses ändern; was vom monatlichen abweicht, wird ein Monatsbudget, was ihm gleicht, keins (wie #53); ein abgeschlossener Monat bietet den Rechner nicht

### Archiv, Jahresübersicht, Backup

- [x] Archiv: je Monat mit Gesamtbudget neben den Ausgaben „1 840 € von 2 000 € · 160 € frei“ oder rot „… · 75 € drüber“
- [x] Jahresübersicht: unter den Ausgaben eine Zeile „Gesamtbudget“ je Monat (offene mit ihrem von jetzt), in der Jahresspalte deren Summe neben den Ausgaben derselben Monate; Monate ohne Gesamtbudget bleiben leer und zählen in diesem Vergleich auf keiner Seite mit
- [x] Backups enthalten Standard, Monats-Einstellungen und festgehaltene Beträge; ältere Backups laden weiter (ohne Gesamtbudget)

### Tests

- [x] Unit-Tests: die drei Arten des Standards; „aus Einnahmen“ mit eingetragenen und noch kommenden Einnahmen, Sparbetrag, nie unter 0; Monat abweichend und „Wie sonst“; Abschluss und Wiederöffnen; Hinweis beim Speichern; Durchschnitt aus drei, weniger und keinen Monaten; Rechner speichert Standard-Budgets bzw. Monatsbudgets; Archiv- und Jahreszahlen
- [x] UI-Tests: Gesamtbalken in der Monatsansicht; Rechner aus Kategorien und aus dem Monat mit Summe und Differenz

## Entscheidungen

Abgestimmt im Interview vom 11.11.2026.

- Das Gesamtbudget wird von Hand festgelegt; „aus Einnahmen“ ist eine Wahl, als Standard oder je Monat.
- Durchschnitt als Hilfe, kein „alle anteilig kürzen“.
- Über dem Gesamtbudget speichern ist erlaubt, es steht dann rot da.
- Archiv und Jahresübersicht zeigen das Gesamtbudget.
- Nachgefragt am 11.11.2026: Die Jahresübersicht zählt auch kommende offene Monate mit ihrem Gesamtbudget von jetzt; eine Kategorie ohne Ausgaben in den abgeschlossenen Monaten zeigt „Ø 0,00 €“; ein wieder geöffneter Monat übernimmt keinen inzwischen geänderten Standard.
