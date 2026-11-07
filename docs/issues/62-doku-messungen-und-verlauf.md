# 62 – Dokumentation: Testwerte am Termin & Verlauf

**Etappe:** 10 – Sonstiges, Heute & Dokumentation  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Tests)  
**GitHub-Issue:** #62  
**Blockiert durch:** [60 – Termine](60-doku-termine.md), [61 – Tests](61-doku-tests.md)

## Was gebaut wird

Im Termin trägst du die Werte der zentral angelegten Tests ein. Im Reiter Tests des Patienten siehst du die Entwicklung: eine Karte je Test mit der Veränderung seit der ersten Messung und einen Verlauf mit Diagrammen und Tabelle.

## Akzeptanzkriterien

### Messung am Termin

- [ ] Im Termin unter der Dokumentation der Abschnitt **Tests** mit den Messungen dieses Termins und „+ Test“
- [ ] „+ Test“ lässt einen nicht archivierten Test wählen und zeigt ein Feld je nicht stillgelegtem Wert, mit Einheit und Bereich als Hinweis, dazu eine optionale **Bemerkung**
- [ ] Zahlen mit Komma, nur so viele Nachkommastellen wie der Wert erlaubt; außerhalb des Bereichs zeigt das Feld einen Fehler und speichert nicht
- [ ] Einzelne Werte dürfen leer bleiben, mindestens einer muss da sein
- [ ] Die **Summe** rechnet live mit, sobald alle Werte des Tests da sind; sonst „Summe: –“
- [ ] Eine Messung hat das Datum ihres Termins; ein Termin kann mehrere Messungen haben, auch zweimal denselben Test (z. B. vorher und nachher)
- [ ] Messungen lassen sich im Termin ändern und mit Rückfrage löschen; ein stillgelegter Wert erscheint beim Ändern nur, wenn die Messung einen hat
- [ ] Einen Termin löschen löscht seine Messungen mit; die Rückfrage nennt ihre Anzahl
- [ ] Beim abgeschlossenen Patienten nur zum Lesen

### Reiter Tests

- [ ] Eine **Karte je Test**, der beim Patienten mindestens einmal gemessen wurde (auch archivierte Tests), zuletzt gemessene zuerst
- [ ] Auf der Karte: Name des Tests, Datum der letzten Messung und je nicht stillgelegtem Wert, bei Tests mit Summe nur für die Summe: letzter Wert mit Einheit und Veränderung zur ersten Messung, z. B. „120° ▲ +30° besser seit 03.09.2026“
- [ ] Besser oder schlechter nach der Richtung des Werts, mit Pfeil und Wort, nicht nur in Farbe; gleich: „= unverändert“; bei nur einer Messung keine Veränderung

### Verlauf

- [ ] Tippen auf eine Karte öffnet den Verlauf: Name des Tests und des Patienten oben
- [ ] Je Wert (auch stillgelegte mit Messungen) und für die Summe ein **Liniendiagramm** über die Zeit: Abstand der Punkte nach Datum, Skala nach dem Bereich, sonst nach den Werten; eine fehlende Messung lässt eine Lücke; bei einer Messung ein Punkt
- [ ] Darunter eine **Tabelle** aller Messungen, neueste oben: Datum, Werte mit Einheit, Summe, Bemerkung; Tippen auf eine Zeile öffnet den Termin
- [ ] Diagramme mit den Farben des Themes, hell wie dunkel lesbar, wie die Diagramme im Haushaltsbuch ohne neue Bibliothek
- [ ] Kein Vergleich zwischen Patienten

### Suche

- [ ] Die Suche der Doku findet auch Bemerkungen von Messungen; der Treffer öffnet den Termin

### Tests

- [ ] Unit-Tests: Prüfung der Eingaben (Komma, Nachkommastellen, Bereich, mindestens ein Wert); Summe nur vollständig; Veränderung und besser/schlechter je Richtung; Karte mit stillgelegtem und archiviertem Test; Termin löschen mit Messungen
- [ ] UI-Tests: Messung im Termin erfassen und ändern; Karte und Verlauf; Zeile der Tabelle öffnet den Termin

## Entscheidungen

Abgestimmt im Interview vom 07.11.2026.

- Messungen immer am Termin, damit es einen Ort zum Erfassen gibt und jede Messung ihren Zusammenhang hat; ein Eingangsbefund ist der erste Termin.
- Ein Diagramm je Wert statt aller Werte in einem, da Skalen sich unterscheiden.
- Auswertung nur beim einzelnen Patienten.
- Zweimal derselbe Test an einem Termin ist erlaubt; das kam im Interview nicht vor und schränkt nichts ein.
