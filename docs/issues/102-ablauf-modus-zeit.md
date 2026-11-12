# 102 – Ablauf-Modus: Minuten je Schritt, Restzeit & laufende Benachrichtigung

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §2 (Ablauf-Modus), §3, §11  
**GitHub-Issue:** #102  
**Blockiert durch:** #101

## Was gebaut wird

Der Ablauf zeigt, wie viel Zeit ein Schritt noch hat und wann die Routine fertig ist, auch bei gesperrtem Handy. Das macht Zeit sichtbar, die sonst unbemerkt vergeht.

## Akzeptanzkriterien

### Minuten

- [ ] Im Routine-Editor hat jeder Unterpunkt der Checkliste ein optionales Feld „Minuten“ (1–180, ganze Zahl); leer heißt ohne Zeit. To-dos bekommen das Feld nicht
- [ ] Eine Spalte für die Minuten am Unterpunkt: Schema-Version eins hoch, Migration mit Test, im Backup (`docs/agents/schema-change.md`); bestehende Unterpunkte bleiben ohne Zeit

### Im Ablauf

- [ ] Hat der Unterpunkt Minuten, zeigt der Ablauf einen Ring mit der Restzeit; ohne Minuten bleibt für diesen Schritt die Stoppuhr
- [ ] Ist die Zeit um, zählt die Anzeige als Überzeit weiter („+0:45“), in einer ruhigen Farbe statt in Rot; weiter geht es nur mit „Fertig“
- [ ] Haben alle restlichen Unterpunkte Minuten, steht dabei „noch 3 Schritte, fertig gegen 07:42“, sonst nur „noch 3 Schritte“
- [ ] Die Zeit eines Schritts beginnt, wenn er erscheint; „Schritt zurück“ beginnt sie neu

### Laufende Benachrichtigung

- [ ] Solange ein Ablauf läuft, steht eine stille Benachrichtigung mit dem Countdown des Schritts und den Knöpfen „Fertig“ und „Beenden“; Antippen öffnet den Ablauf hinter der App-Sperre
- [ ] Sie nennt Routine und Unterpunkt nur, wenn die Routine ihren Namen auf dem Sperrbildschirm zeigen darf; sonst steht dort „Ablauf läuft“ und „Schritt 2 von 4“
- [ ] „Fertig“ dort hakt den Schritt ab und zeigt den nächsten, ohne die App zu öffnen; auf dem Sperrbildschirm erst nach dem Entsperren des Handys, wie bei den Erinnerungen
- [ ] „Beenden“ nimmt die Benachrichtigung weg und lässt die Haken, wie sie sind; mit dem letzten „Fertig“ verschwindet sie von selbst
- [ ] Am Ende der Zeit eines Schritts meldet sie sich einmal, mit dem Standard-Ton für Benachrichtigungen; kein Nachfassen
- [ ] Kein Dauerdienst: Das Ende eines Schritts kommt über einen exakten Wecker (`ReminderAlarm` mit eigener Aktion), auch bei geschlossener App
- [ ] Der laufende Ablauf (Fälligkeit, Beginn des aktuellen Schritts) ist gespeichert: Nach dem Beenden der App oder einem Neustart des Handys stimmen Benachrichtigung und Restzeit wieder; ist die Fälligkeit inzwischen zu Ende, verschwindet beides
- [ ] Es läuft höchstens ein Ablauf zugleich; „Starten“ an einer anderen Routine fragt, ob der laufende beendet werden soll
- [ ] Ohne die Erlaubnis für Benachrichtigungen läuft der Ablauf in der App wie in #101

### Tests

- [ ] Unit-Tests: Restzeit, Überzeit, „fertig gegen“ nur mit allen Zeiten, ein Ablauf zugleich, Wiederherstellen nach dem Neustart, Wecker zum Ende des Schritts, Text auf dem Sperrbildschirm; UI-Tests: Minuten im Editor, Ring im Ablauf; Migrationstest

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Feature 2); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Die Überzeit zählt weiter, statt von selbst weiterzuschalten: Die App weiß nicht, ob der Schritt getan ist.
- Ruhige Farbe statt Rot: Die Uhr soll zeigen, nicht drängen.
- Ein Wecker statt eines Dauerdienstes, wie bei den Erinnerungen; so braucht es keine neue Berechtigung.
