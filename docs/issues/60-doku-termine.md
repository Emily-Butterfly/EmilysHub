# 60 – Dokumentation: Termine mit Dokumentation & Vorlage

**Etappe:** 10 – Sonstiges, Heute & Dokumentation  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Termine)  
**GitHub-Issue:** #60  
**Blockiert durch:** [59 – Dokumentation: Patienten](59-doku-patienten.md)

## Was gebaut wird

Bei jedem Patienten legst du Termine an und schreibst zu jedem eine Dokumentation in Markdown. Eine zentrale Vorlage füllt neue Termine auf Wunsch vor.

## Akzeptanzkriterien

### Termin

- [ ] Felder: **Datum** (Pflicht, Standard heute, auch in der Zukunft), **Uhrzeit** (optional), **Dokumentation** (ein Markdown-Text, Bearbeiten und Vorschau wie bei den Notizen)
- [ ] „+ Termin“ im Reiter Termine; gibt es eine Vorlage, steht ihr Text schon in der Dokumentation
- [ ] Datum, Uhrzeit und Text lassen sich jederzeit ändern
- [ ] Gespeichert wird wie im Notiz-Editor; beim Sperren der Doku (#58) wird gespeichert, was getippt ist, auch ein neuer Termin
- [ ] „Löschen“ im Menü des Termins mit Rückfrage; mit #62 nennt sie auch die Messungen, die mitgehen
- [ ] Keine Erinnerungen, keine Benachrichtigungen

### Reiter Termine

- [ ] Die Termine des Patienten, neueste oben: nach Datum, am selben Tag nach Uhrzeit, ohne Uhrzeit zuletzt
- [ ] Je Zeile Datum (und Uhrzeit) und eine Zeile Vorschau: die erste nicht leere Zeile, die keine Überschrift ist, ohne Markdown-Zeichen; sonst die erste Überschrift; ohne Text „(leer)“
- [ ] Beim abgeschlossenen Patienten nur zum Lesen
- [ ] „Zuletzt behandelt“ auf der Startseite (#59) richtet sich nach dem letzten Termin bis heute

### Termin-Vorlage

- [ ] Menüpunkt „Termin-Vorlage“ der Doku: ein Markdown-Text; leer heißt keine Vorlage
- [ ] Eine geänderte Vorlage ändert keine vorhandenen Termine

### Suche

- [ ] Die Suche der Doku findet auch Text in Termin-Dokumentationen; der Treffer öffnet den Termin

### Tests

- [ ] Unit-Tests: Reihenfolge der Termine; Vorschau-Zeile mit und ohne Vorlage; Vorlage nur für neue Termine; Löschen; Speichern beim Sperren; Suche im Termin-Text
- [ ] UI-Tests: Termin anlegen mit Vorlage, bearbeiten, löschen; nur zum Lesen im Archiv

## Entscheidungen

Abgestimmt im Interview vom 07.11.2026.

- Eine Dokumentation je Termin, als freier Text mit optionaler Vorlage, statt fester Felder wie Befund und Maßnahmen.
- Die Vorschau überspringt Überschriften, damit sie bei Terminen aus der Vorlage nicht immer „Befund“ zeigt.
- Termine sind Daten, an denen dokumentiert wird; geplant wird im Kalender oder der Praxissoftware.
