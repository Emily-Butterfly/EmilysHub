# 104 – Vokabeltrainer: Karten, Fächer & Abfrage

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer)  
**GitHub-Issue:** #104  
**Blockiert durch:** –

## Was gebaut wird

Sprachen bekommt Karteikarten, die in wachsenden Abständen abgefragt werden. Bisher hält der Bereich nur fest, was im Unterricht war, übt aber nichts.

## Akzeptanzkriterien

### Karten

- [ ] Eine Karte gehört zu einer Sprache: Vorderseite (Pflicht, bis 200 Zeichen), Rückseite (Pflicht, bis 500), Umschrift (optional, bis 200), Notiz (optional, bis 500), Thema (optional, aus den Themen der Sprache)
- [ ] Die Seite einer Sprache bekommt den Reiter **Karten**, zwischen Themen und Auswertung: alle Karten mit Fach und nächster Fälligkeit, nach Vorderseite geordnet, filterbar nach Thema; „+“ legt eine an, Antippen öffnet sie zum Ändern, Löschen mit Rückfrage
- [ ] Eine Vorderseite, die es in der Sprache schon gibt, wird abgelehnt („Diese Karte gibt es schon“)
- [ ] Text in Schriften von rechts nach links steht so wie in den Notizen
- [ ] Wird ein Thema gelöscht, bleiben seine Karten ohne Thema

### Fächer

- [ ] Eine neue Karte liegt in Fach 1 und gilt als neu, bis sie zum ersten Mal abgefragt wurde. Die Abstände der Fächer 1 bis 5 sind 1, 3, 7, 14 und 30 Tage
- [ ] **Nochmal**: Fach 1; die Karte kommt in derselben Runde am Ende wieder und ist danach morgen fällig. **Gewusst**: ein Fach vor. **Leicht**: zwei Fächer vor. Höher als Fach 5 geht es nicht
- [ ] Die nächste Fälligkeit ist heute plus der Abstand des neuen Fachs. **Sicher** ist eine Karte in Fach 4 oder 5

### Tagespensum & Abfrage

- [ ] Das Tagespensum einer Sprache: alle Karten, die heute oder früher fällig sind, und dazu höchstens 10 neue, in der Reihenfolge ihres Anlegens; die Zahl ist je Sprache einstellbar (0–50), im Menü des Reiters Karten
- [ ] Die Startseite von Sprachen zeigt je Sprache mit Karten „Heute fällig: 23“ und „Üben“; ist das Pensum geschafft, „Für heute geschafft“
- [ ] „Üben“ öffnet die Abfrage: die Vorderseite groß, oben „7 von 23“; „Aufdecken“ zeigt Rückseite, Umschrift und Notiz; dann **Nochmal · Gewusst · Leicht**
- [ ] Jede Antwort ist sofort gespeichert; die Abfrage lässt sich jederzeit verlassen, und „Üben“ setzt sie fort
- [ ] Am Ende steht „Für heute geschafft: 23 Karten“
- [ ] Der Tag wechselt um Mitternacht in der Zeitzone des Handys (`Day`)
- [ ] Abgefragt wird nur von der Vorderseite zur Rückseite
- [ ] Karten einer archivierten Sprache bleiben, werden aber nicht abgefragt und nicht gezählt
- [ ] Die Abfrage erinnert an nichts, und nichts davon steht in Heute

### Daten & Tests

- [ ] Eine Tabelle für die Karten und die Einstellung je Sprache: Schema-Version eins hoch, Migration mit Test, im Backup (`docs/agents/schema-change.md`); die Regeln der Fächer als reines Modul
- [ ] Unit-Tests: Fach und Fälligkeit nach jeder Antwort, Fach 5 bleibt, „Nochmal“ kommt in der Runde wieder, Tagespensum mit der Grenze für neue Karten, Fortsetzen nach dem Verlassen, Tageswechsel, archivierte Sprache, doppelte Vorderseite; UI-Tests: Karte anlegen, üben bis geschafft, Zahl auf der Startseite

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Feature 3).

- Nicht einzeln bestätigt: fünf Fächer nach Leitner statt SM-2, weil sich die Regeln in einem Satz erklären und leicht prüfen lassen.
- Drei Antworten, damit eine leichte Karte schneller aus dem Weg ist.
- Eine Grenze für neue Karten, damit ein Import von 500 Karten nicht 500 Karten am ersten Tag bedeutet.
- Keine Tonaufnahmen und keine Bilder: Das bräuchte neue Berechtigungen oder größere Backups.
