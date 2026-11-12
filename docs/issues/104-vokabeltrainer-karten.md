# 104 – Vokabeltrainer: Karten, FSRS & Abfrage

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer); [docs/research/spaced-repetition.md](../research/spaced-repetition.md)  
**GitHub-Issue:** #104  
**Blockiert durch:** –

## Was gebaut wird

Sprachen bekommt Karteikarten, die der Algorithmus FSRS-6 in wachsenden Abständen zur Abfrage plant. Bisher hält der Bereich nur fest, was im Unterricht war, übt aber nichts.

## Akzeptanzkriterien

### Karten

- [ ] Eine Karte gehört zu einer Sprache: Vorderseite (Pflicht, bis 200 Zeichen), Rückseite (Pflicht, bis 500), Umschrift (optional, bis 200), Notiz (optional, bis 500), Thema (optional, aus den Themen der Sprache)
- [ ] Die Seite einer Sprache bekommt den Reiter **Karten**, zwischen Themen und Auswertung: alle Karten mit ihrer nächsten Fälligkeit und der Marke „sicher“, nach Vorderseite geordnet, filterbar nach Thema; „+“ legt eine an, Antippen öffnet sie zum Ändern, Löschen mit Rückfrage
- [ ] Eine Vorderseite, die es in der Sprache schon gibt, wird abgelehnt („Diese Karte gibt es schon“)
- [ ] Text in Schriften von rechts nach links steht so wie in den Notizen
- [ ] Wird ein Thema gelöscht, bleiben seine Karten ohne Thema

### Algorithmus

- [ ] Geplant wird mit **FSRS-6**: den 21 Standardparametern, einer gewünschten Behaltensquote von 90 %, einem längsten Abstand von 36 500 Tagen, ohne Lernschritte in Minuten und ohne zufällige Streuung, mit den Formeln aus `docs/research/spaced-repetition.md` §4 und der Regel aus §5
- [ ] Die Rechnung ist ein reines Modul in Kotlin, ohne fremde Bibliothek; ein Kommentar nennt FSRS-6, das Projekt Open Spaced Repetition und die Quelle der Parameter
- [ ] Vier Antworten: **Nochmal · Schwer · Gut · Leicht**. Unter jedem Knopf steht der Abstand, den die Antwort ergäbe („1 T“, „2 T“, „8 T“)
- [ ] Unter den Knöpfen steht beim ersten Üben ein Satz: „Schwer“ heißt gewusst, aber mit Mühe; nicht gewusst ist „Nochmal“
- [ ] Je Karte gespeichert: Stabilität, Schwierigkeit, Tag der letzten Abfrage, Fälligkeitstag und der zuletzt geplante Abstand; eine neue Karte hat davon nichts
- [ ] Tage zählen nach dem Kalender des Handys (`Day`), nicht nach 24 Stunden
- [ ] Nach „Nochmal“ kommt die Karte in derselben Runde am Ende wieder; diese Antwort zählt als Abfrage am selben Tag. Wird die Runde vorher verlassen, ist die Karte am nächsten Tag fällig
- [ ] **Sicher** ist eine Karte, deren zuletzt geplanter Abstand mindestens 21 Tage beträgt
- [ ] Jede Abfrage wird protokolliert (Karte, Zeitpunkt, Antwort), damit sich die Parameter später anpassen und die Karten neu durchrechnen lassen; das Protokoll wird nirgends angezeigt

### Tagespensum & Abfrage

- [ ] Das Tagespensum einer Sprache: alle Karten, die heute oder früher fällig sind, die am längsten fälligen zuerst, und dazu höchstens 10 neue, in der Reihenfolge ihres Anlegens; die Zahl ist je Sprache einstellbar (0–50), im Menü des Reiters Karten
- [ ] Die Startseite von Sprachen zeigt je Sprache mit Karten „Heute fällig: 23“ und „Üben“; ist das Pensum geschafft, „Für heute geschafft“
- [ ] „Üben“ öffnet die Abfrage: die Vorderseite groß, oben „7 von 23“; „Aufdecken“ zeigt Rückseite, Umschrift und Notiz; dann die vier Antworten
- [ ] Jede Antwort ist sofort gespeichert; die Abfrage lässt sich jederzeit verlassen, und „Üben“ setzt sie fort
- [ ] Am Ende steht „Für heute geschafft: 23 Karten“
- [ ] Abgefragt wird nur von der Vorderseite zur Rückseite
- [ ] Karten einer archivierten Sprache bleiben, werden aber nicht abgefragt und nicht gezählt
- [ ] Die Abfrage erinnert an nichts, und nichts davon steht in Heute

### Daten & Tests

- [ ] Tabellen für die Karten und das Protokoll, dazu die Einstellung je Sprache: Schema-Version eins hoch, Migration mit Test, im Backup (`docs/agents/schema-change.md`)
- [ ] Unit-Tests der Rechnung: alle Testvektoren aus `docs/research/spaced-repetition.md` §6, Stabilität und Schwierigkeit auf 0,0001 genau, die Abstände exakt
- [ ] Unit-Tests: Tagespensum mit der Grenze für neue Karten, „Nochmal“ kommt in der Runde wieder, Runde verlassen nach „Nochmal“, Fortsetzen, Tageswechsel um Mitternacht, „sicher“ an der Grenze von 21 Tagen, archivierte Sprache, doppelte Vorderseite; UI-Tests: Karte anlegen, üben bis geschafft, Zahl auf der Startseite

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Feature 3).

- **Entschieden am 12.11.2026:** ein Algorithmus für Spaced Repetition statt der fünf Fächer nach Leitner. Die Recherche dazu steht in `docs/research/spaced-repetition.md`.
- Nicht einzeln bestätigt: FSRS-6 statt SM-2, weil es verspätete und verfrühte Abfragen einrechnet und mit Standardparametern ohne Training auskommt.
- Nicht einzeln bestätigt: geplant wird in ganzen Kalendertagen und ohne Lernschritte in Minuten; die Behaltensquote steht fest bei 90 %.
- Eine Grenze für neue Karten, damit ein Import von 500 Karten nicht 500 Karten am ersten Tag bedeutet.
- Keine Tonaufnahmen und keine Bilder: Das bräuchte neue Berechtigungen oder größere Backups.
