# 96 – Heute: Kopfzeile & drei Blöcke

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1 (Heute-Übersicht, Drei Blöcke)  
**GitHub-Issue:** #96  
**Blockiert durch:** –

## Was gebaut wird

Heute zeigt zuerst, was jetzt dran ist. Überfälliges wird eine aufklappbare Zeile, und was schon vorbei ist, rückt zugeklappt nach unten. Bisher füllen mittags Überfälliges in Rot und Verpasstes den ersten Bildschirm.

## Akzeptanzkriterien

### Kopfzeile

- [x] Über der Liste stehen das Datum und der Stand des Tages, z. B. „Fr., 23. Okt. · 2 von 13 erledigt“; ohne Einträge nur das Datum
- [x] Gezählt werden die Einträge von heute (Fälligkeiten der Routinen und To-dos, ohne „Demnächst“), ohne Übersprungenes, von Hand oder durch eine gekoppelte Routine; erledigt ist, was abgehakt ist

### Blöcke

- [x] Reihenfolge der Seite: Kopfzeile, Zeile des Eingangs (#93, sobald gebaut), „‹ Gestern“, Warnbanner, angepinnte Notizen, **Überfällig**, **Jetzt und als Nächstes**, **Schon vorbei**, Demnächst, Heatmap
- [x] **Überfällig (n)**: eine einzelne Zeile mit der Zahl der überfälligen To-dos; Antippen klappt sie auf, die am längsten überfälligen zuerst. Ohne Überfälliges fehlt die Zeile
- [x] **Jetzt und als Nächstes**: alles, was heute noch offen ist: die To-dos des Tages ohne Uhrzeit, dann Routinen und To-dos mit Uhrzeit, nach ihr, wie bisher geordnet. Auch was seine Uhrzeit hinter sich hat, steht hier, solange es offen ist, ebenso Fälligkeiten von gestern, deren Zeitfenster noch läuft
- [x] Ist nichts offen, steht dort „Für heute ist alles erledigt“; gibt es gar keine Einträge, der bisherige Text
- [x] **Schon vorbei**: eine Zeile mit einer Zusammenfassung wie „3 erledigt · 1 übersprungen · 1 verpasst“, die nur nennt, was vorkommt; aufgeklappt die Einträge nach Uhrzeit mit ihrem Status wie bisher
- [x] Was abgehakt wird, wandert nach „Schon vorbei“; eine Leiste bietet „Rückgängig“. In „Schon vorbei“ lässt sich wie bisher der Haken entfernen und Übersprungenes oder Ausgelassenes zurücknehmen; der Eintrag steht dann wieder oben
- [x] Eine Routine mit Checkliste bleibt oben, bis ihr letzter Unterpunkt abgehakt ist. Eine verpasste Routine zeigt ihre Checkliste nicht mehr
- [x] „Überfällig“ und „Schon vorbei“ sind zugeklappt, wann immer Heute neu erscheint (Start, Tabwechsel, nach der App-Sperre); liegt nur ein Editor darüber, bleibt der Zustand
- [x] Demnächst, angepinnte Notizen, Warnbanner, Heatmap und der Vortag (Gestern nachtragen) bleiben, wie sie sind

### Übriges

- [x] Die Abschnittstitel „Demnächst“ in Heute und „Buchungen“ im Haushaltsbuch bekommen links denselben Rand wie die Zeilen darunter (Befund U11)
- [x] `Heute` und `todayList` liefern die Blöcke und den Stand; der Bildschirm zeichnet sie nur
- [x] Für Screenreader sind die Zeilen der Blöcke Überschriften, die sagen, ob sie auf- oder zugeklappt sind
- [x] Unit-Tests: jeder Status in seinem Block, Reihenfolge, Zählung des Standes, von gestern noch Offenes; UI-Tests: Überfällig auf- und zuklappen; Abhaken wandert nach „Schon vorbei“, „Rückgängig“ holt es zurück; verpasste Routine ohne Checkliste; Tabwechsel klappt zu

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befunde U3 und U11, Vorschlag UX 2).

- **Bestätigt am 12.11.2026:** Überfällige To-dos bleiben oben, aber als eine aufklappbare Zeile.
- Nicht in einem Interview abgestimmt: Abgehaktes wandert nach unten statt durchgestrichen stehen zu bleiben; den Fortschritt zeigen die Kopfzeile und die Zeile „Schon vorbei“.
- Zugeklappt bei jedem Öffnen, damit der erste Blick immer derselbe ist.

Bei der Umsetzung festgelegt (13.11.2026):

- **Bestätigt am 13.11.2026:** Der Stand zählt nur die Einträge von heute: To-dos, die heute fällig sind, und die Fälligkeiten der Routinen auf Heute, auch die von gestern, deren Zeitfenster noch läuft. To-dos von Vortagen zählen nicht, überfällig oder heute erledigt; sie zeigt „Überfällig (n)“. Verpasstes und Ausgelassenes zählt mit, als nicht erledigt.
- **Bestätigt am 13.11.2026:** Die Zusammenfassung von „Schon vorbei“ lässt aus, was eine gekoppelte Routine erledigt hat; seine Zeile sagt „durch … erledigt“.
- Die Zusammenfassung nennt „Heute nicht“ eigens, als „ausgelassen“, wie der Status in der Zeile.
- „Jetzt und als Nächstes“ hat einen sichtbaren Abschnittstitel. Steht nur noch Überfälliges offen, sagt der Block „Für heute ist alles erledigt“.
- Die Leiste mit „Rückgängig“ kommt beim Abhaken des Kästchens; hakt der letzte Unterpunkt einer Checkliste den Eintrag ab, kommt keine, der Unterpunkt lässt sich in „Schon vorbei“ wieder abhaken.
- Auf- und Zugeklappt hält die App wie „‹ Gestern“: Es bleibt, solange ein Bildschirm über Heute liegt und beim Wechsel zu Gestern und zurück, und ist nach Tabwechsel, Neustart und App-Sperre wieder zugeklappt.
- Die UI-Tests von Heute laufen in der Größe des Galaxy A52, da Kopfzeile und Abschnittstitel Robolectrics kleinen Standardbildschirm füllen.
