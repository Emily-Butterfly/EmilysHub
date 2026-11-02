# 50 – Haushaltsbuch: Saldo übertragen, Anfangssaldo & Knopf in Notizen

**Etappe:** 9 – Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1 (Navigation), §13  
**GitHub-Issue:** #50  
**Blockiert durch:** –

## Was gebaut wird

**Teil A:** Jeder Monat des Haushaltsbuchs kann einen Anfangssaldo haben, übertragen aus dem Vormonat oder von Hand festgelegt. Beim Abschließen eines Monats lässt sich sein Endsaldo gleich in den Folgemonat übertragen. Der Anfangssaldo ist keine Buchung und zählt nie als Einnahme.

**Teil B:** Der Knopf zum Haushaltsbuch sitzt im Tab Notizen groß in der Mitte der oberen Leiste, direkt unter der Kamera.

Beide Teile hängen nicht voneinander ab und lassen sich getrennt umsetzen.

## Begriffe

- **Saldo**: Einnahmen − Ausgaben eines Monats, wie bisher, überall gleich.
- **Anfangssaldo**: ein Betrag, mit dem ein Monat beginnt; ein eigener Wert am Monat, keine Buchung. Euro mit Cent, auch 0 oder negativ.
- **Endsaldo**: Anfangssaldo + Einnahmen − Ausgaben. Hat ein Monat keinen Anfangssaldo, zählt er beim Übertragen als 0 €; der Endsaldo ist dann sein Saldo.
- **Folgemonat**: der Monat direkt nach einem Monat, unabhängig vom heutigen Datum.

## Akzeptanzkriterien – Teil A: Anfangssaldo & Übertrag

### Anfangssaldo und Endsaldo in der Monatsansicht

- [ ] Jeder Monat hat höchstens einen Anfangssaldo: Betrag in Cent (auch 0 oder negativ) und seine Herkunft, „übertragen aus dem Vormonat“ oder „von Hand festgelegt“
- [ ] Hat der Monat einen Anfangssaldo, steht über den Summen die Zeile „Anfangssaldo“ mit dem Betrag und darunter der Herkunft („aus Oktober übertragen“ bzw. „von Hand festgelegt“), unter den Summen die Zeile „Endsaldo“ mit dem Betrag
- [ ] Hat er keinen, fehlen beide Zeilen; die Summen sehen aus wie bisher
- [ ] Die Summenzeile bleibt „Einnahmen | Ausgaben | Saldo“, Saldo = Einnahmen − Ausgaben
- [ ] Negative Beträge erscheinen als „−85,20 €“, wie ein negativer Saldo heute
- [ ] Skizze:
  ```
  ‹  November 2026  ›
  Anfangssaldo  120,00 €  · aus Oktober übertragen        ✎
  Einnahmen 1.500,00 € | Ausgaben 1.450,00 € | Saldo 50,00 €
  Endsaldo      170,00 €
  ```

### Anfangssaldo festlegen, ändern, entfernen

- [ ] In jedem offenen Monat **ohne** Anfangssaldo stehen über den Summen zwei Knöpfe: „Saldo aus Vormonat übertragen“ und „Anfangssaldo festlegen“, egal ob der Monat schon Buchungen hat (Fixbuchungen tragen sich oft schon am 1. ein)
- [ ] Sobald ein Anfangssaldo da ist (auch 0,00 €), verschwinden beide Knöpfe
- [ ] „Anfangssaldo festlegen“ öffnet einen Dialog mit einem Betragsfeld (Eingabe mit Komma, Minus erlaubt), „Speichern“ und „Abbrechen“; gespeichert heißt die Herkunft „von Hand festgelegt“
- [ ] In einem offenen Monat **mit** Anfangssaldo öffnet ein Tipp auf die Anfangssaldo-Zeile (✎) denselben Dialog mit dem Betrag von jetzt und zusätzlich „Entfernen“; danach zeigt der Monat wieder die beiden Knöpfe
- [ ] Wer einen übertragenen Betrag von Hand ändert, macht daraus „von Hand festgelegt“
- [ ] Der Betrag wird geprüft wie die Beträge der Buchungen (Format, Obergrenze, gerechnet in Cent), nur dass er auch 0 oder negativ sein darf; Fehler zeigt der Dialog am Feld

### „Saldo aus Vormonat übertragen“

- [ ] Ist der Vormonat abgeschlossen, wird sofort übertragen: Anfangssaldo = Endsaldo des Vormonats, Herkunft „aus <Vormonat> übertragen“
- [ ] Ist der Vormonat noch offen, fragt vorher ein Dialog: „Der Oktober ist noch offen; sein Endsaldo kann sich noch ändern. 120,00 € übertragen?“ mit „Übertragen“ und „Abbrechen“
- [ ] Auch ein Endsaldo von 0,00 € oder ein negativer wird übertragen

### Abschließen mit Übertrag

- [ ] Der Dialog beim Abschließen (#46) hat drei Knöpfe: „Abbrechen“, „Nur abschließen“, „Abschließen & übertragen“
- [ ] Sein Text nennt Endsaldo und Folgemonat, etwa: „Oktober 2026 abschließen? Der Monat wandert ins Archiv und ist dann nur noch zu lesen. Du kannst ihn wieder öffnen. Mit „Abschließen & übertragen“ geht der Endsaldo von 120,00 € als Anfangssaldo in den November.“
- [ ] Ziel ist immer der Folgemonat des abgeschlossenen Monats, auch wenn heute ein späterer Monat ist (August im November abgeschlossen → September) und auch wenn der Folgemonat noch nicht begonnen hat (laufender Monat an seinem letzten Tag abgeschlossen)
- [ ] Hat der Folgemonat schon einen Anfangssaldo, nennt der Text ihn: „… ersetzt den Anfangssaldo des Novembers (bisher 120,00 €).“; „Abschließen & übertragen“ ersetzt ihn, Herkunft danach „aus Oktober übertragen“
- [ ] Ist der Folgemonat abgeschlossen, fehlt „Abschließen & übertragen“; der Text sagt: „Der November ist abgeschlossen; um den Saldo zu übertragen, öffne ihn erst wieder.“
- [ ] „Nur abschließen“ ändert keinen Anfangssaldo
- [ ] Abschließen und Übertragen geschehen zusammen in einer Transaktion: beides oder keins
- [ ] Wieder öffnen (#46) ändert keinen Anfangssaldo, weder den des Monats noch den des Folgemonats

### Übertragen ist ein fester Wert

- [ ] Ein übertragener Anfangssaldo bleibt, wie er übertragen wurde: Ändern sich danach Buchungen oder der Anfangssaldo des Vormonats, ändert er sich nicht
- [ ] Er ändert sich nur durch „Abschließen & übertragen“ des Vormonats, durch Ändern oder Entfernen von Hand, oder nach dem Entfernen durch einen neuen Übertrag

### Abgeschlossene Monate

- [ ] In einem abgeschlossenen Monat ist der Anfangssaldo nur zu lesen: Die Zeile hat kein ✎, und es gibt keine der beiden Knöpfe
- [ ] Nichts schreibt in den Anfangssaldo eines abgeschlossenen Monats, weder ein Übertrag noch der Dialog; die Datenschicht lehnt es ab wie Buchungen in einem abgeschlossenen Monat

### Zählt nie als Einnahme

- [ ] Der Anfangssaldo zählt nirgends als Einnahme oder Ausgabe: nicht in Einnahmen, Ausgaben und Saldo des Monats, nicht in den Balken und dem Tortendiagramm, nicht in den Budgets (#47), nicht in den Summen von Archiv und Jahresübersicht
- [ ] Er erscheint nicht in der Buchungsliste und nicht in der Suche

### Archiv und Jahresübersicht

- [ ] Das Archiv zeigt je Monat weiter Einnahmen, Ausgaben und Saldo, dazu den Endsaldo, wenn der Monat einen Anfangssaldo hat
- [ ] Die Jahresübersicht zeigt oben den Anfangssaldo des Januars, wenn er einen hat, und unten den Endsaldo des Dezembers, wenn er einen Anfangssaldo hat; sonst fehlt die jeweilige Zeile
- [ ] Die Summen der Jahresübersicht (je Monat, im Jahr, je Kategorie) bleiben reine Einnahmen und Ausgaben

### Backups

- [ ] Backups enthalten die Anfangssalden mit ihrer Herkunft; ältere Backups laden weiter (ohne Anfangssalden); Schemaänderung nach `docs/agents/schema-change.md`

### Tests

- [ ] Unit-Tests: Endsaldo mit und ohne Anfangssaldo; Übertrag in den Folgemonat, auch nachträglich und über den Jahreswechsel (Dezember → Januar); Ersetzen eines vorhandenen Anfangssaldos; kein Übertrag in einen abgeschlossenen Folgemonat; Abschluss und Übertrag zusammen oder gar nicht; fester Wert nach Änderung im Vormonat; Wieder öffnen ändert keinen Anfangssaldo; 0 € und negativer Betrag; Betragseingabe mit Minus; kein Einfluss auf Summen, Budgets, Archiv- und Jahressummen; Anfangssaldo eines abgeschlossenen Monats abgelehnt
- [ ] UI-Tests: Knöpfe nur im offenen Monat ohne Anfangssaldo; Dialog beim Abschließen mit drei bzw. zwei Knöpfen; Anfangs- und Endsaldo-Zeile

## Akzeptanzkriterien – Teil B: Knopf in Notizen

- [ ] Im Tab Notizen sitzt der Haushaltsbuch-Knopf waagerecht in der Mitte der oberen Leiste, direkt unter der Kamera; „Notizen“ bleibt links, Suche und Einstellungen bleiben rechts
- [ ] Das kleine Einkaufswagen-Symbol rechts in der Leiste fällt weg
- [ ] Der Knopf ist ein gefüllter Knopf, 1/4 der Bildschirmbreite breit, auch nach dem Drehen des Geräts
- [ ] Er zeigt den Text „Haushaltsbuch“ in kleinerer Schrift, einzeilig; passt der Text nicht in die Breite (etwa bei großer Systemschrift), zeigt er stattdessen „€“
- [ ] Für die Bedienungshilfe heißt er immer „Haushaltsbuch öffnen“
- [ ] Wird es eng, kürzt sich der Titel „Notizen“ mit „…“; Titel und Knopf überlappen sich nie, und der Knopf bleibt in der Mitte
- [ ] Er erscheint nur im Tab Notizen und öffnet wie bisher den aktuellen Monat; Zurück führt in die Notizen
- [ ] UI-Tests: Knopf nur im Tab Notizen, öffnet das Haushaltsbuch, Name für die Bedienungshilfe; „€“, wenn der Text nicht passt

## Entscheidungen

Abgestimmt im Interview vom 02.11.2026.

- Übertragen wird der Endsaldo (Anfangssaldo + Einnahmen − Ausgaben), nicht nur das Ergebnis des Monats, damit die Kette Monat für Monat durchläuft.
- „Saldo“ bleibt überall Einnahmen − Ausgaben; Anfangs- und Endsaldo kommen dazu, nur in Monaten mit Anfangssaldo.
- Der Anfangssaldo ist ein eigener Wert am Monat, keine Buchung, und darf negativ sein; so zählt er von selbst nie als Einnahme.
- Ziel des Übertrags ist immer der Folgemonat, nicht der Kalendermonat von heute.
- Beim Abschließen ein Dialog mit drei Knöpfen statt eines Häkchens oder eines zweiten Dialogs.
- Der Knopf „Saldo aus Vormonat übertragen“ gilt für jeden offenen Monat ohne Anfangssaldo, auch mit Buchungen.
- Übertragen geht auch aus einem offenen Vormonat, mit Rückfrage; aus einem abgeschlossenen ohne.
- Der Anfangssaldo lässt sich in jedem offenen Monat von Hand festlegen, ändern und entfernen.
- Ein Übertrag ist ein fester Wert und läuft nicht mit dem Vormonat mit.
- Hat der Folgemonat schon einen Anfangssaldo, ersetzt ihn der Übertrag; der Dialog nennt den bisherigen Betrag.
- In einen abgeschlossenen Monat wird nicht übertragen.
- Archiv mit Endsaldo; Jahresübersicht mit Anfangssaldo Januar und Endsaldo Dezember, nur wo vorhanden.
- Haushaltsbuch-Knopf in der Mitte der oberen Leiste, 1/4 breit, Text „Haushaltsbuch“, bei Platzmangel „€“.
- Ein Issue für beides, wie gewünscht, mit getrennten Teilen.
