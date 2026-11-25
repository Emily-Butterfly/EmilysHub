# 166 – Vokabeltrainer: nicht gewusste Karten nach Verlassen weiter üben

**Etappe:** – (Einzelticket)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer: Abfrage)  
**GitHub-Issue:** #166  
**Blockiert durch:** –

## Was gebaut wird

Wer die Abfrage verlässt, solange nach „Nochmal“ noch Karten offen sind, hat ein geschafftes Tagespensum, kann die nicht gewussten aber nicht weiter üben: Sie sind erst morgen fällig. Ein eigener Knopf übt sie, bis jede gewusst ist; das Pensum bleibt geschafft.

## Akzeptanzkriterien

- [x] **Nicht gewusst** heute ist eine Richtung einer Karte, deren letzte Antwort heute „Nochmal“ war, aus dem Protokoll
- [x] Ist das Tagespensum geschafft und gibt es nicht gewusste, steht neben „Für heute geschafft“ „Nicht gewusste üben (4)“: auf der Startseite von Sprachen und oben im Reiter Karten
- [x] Am Ende der täglichen Abfrage steht derselbe Knopf über „Weiter mit …“ und „Zurück“; er öffnet die Abfrage an Stelle dieser
- [x] Die Abfrage fragt die nicht gewussten je einmal, nach „Nochmal“ in derselben Runde wieder; verlassen und wieder geöffnet, geht es mit den noch nicht gewussten weiter
- [x] Die Antworten zählen wie beim Thema üben: FSRS plant am selben Tag neu, nicht gegen die Grenzen des Tages; das Tagespensum und eine durch Vokabeln erledigte Routine bleiben, wie sie sind
- [x] Am Ende „Nicht gewusste geübt: 4 Karten“, ohne Bilanz und ohne nächste Sprache
- [x] Archivierte Sprachen bieten nichts davon an

## Tests

- [x] Unit: nicht gewusst aus dem Protokoll (letzte Antwort, Rückrichtung, Tag, je Sprache), Runde mit „Nochmal“
- [x] UI: Nochmal, Abfrage verlassen, im Reiter Karten weiter üben bis gewusst; Pensum bleibt geschafft

## Entscheidungen

Abgestimmt am 25.11.2026: ein eigener Knopf statt die Karten im Tagespensum offen zu halten, damit „geschafft“ und Routinen „Erledigt durch Vokabeln“ bleiben, wie sie sind.

Beim Bauen festgelegt, nicht einzeln bestätigt:

- Die Abfrage zählt „3 von 5“ über alle heute nicht gewussten, auch die seither gewussten, damit sich eine fortgesetzte Runde wie die tägliche liest.
- Eine Auswahl `CardSelection.Missed` wie Thema und Prüfung üben; sie liest das Protokoll des Tages, ohne neue Spalte.
