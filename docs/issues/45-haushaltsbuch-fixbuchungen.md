# 45 – Haushaltsbuch: Fixbuchungen

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §13  
**GitHub-Issue:** #45  
**Blockiert durch:** [44 – Haushaltsbuch: Buchungen, Kategorien, Monatsansicht](44-haushaltsbuch.md)

## Was gebaut wird

Wiederkehrende Buchungen wie Miete, Gehalt oder Abos trägt das Haushaltsbuch an ihrem Tag selbst ein.

## Akzeptanzkriterien

- [ ] Fixbuchungen anlegen, ändern und löschen: Art, Betrag, Kategorie, Beschreibung, Abstand (monatlich, vierteljährlich, halbjährlich, jährlich), Tag im Monat und erster Monat
- [ ] An ihrem Tag wird die Buchung automatisch eingetragen; lief die App an dem Tag nicht, wird sie beim nächsten Start nachgetragen, jede genau einmal
- [ ] Am 29.–31.: in kürzeren Monaten am Monatsletzten
- [ ] Eingetragene Fixbuchungen lassen sich im Monat ändern und löschen, ohne die Vorlage zu ändern; gelöscht kommen sie in dem Monat nicht wieder
- [ ] Eine geänderte Vorlage gilt ab dem nächsten Eintrag; ist die Vorlage gelöscht, kommen keine weiteren, die eingetragenen bleiben
- [ ] Die Monatsansicht kennzeichnet eingetragene Fixbuchungen; eine Liste zeigt alle Vorlagen mit ihrem nächsten Termin
- [ ] Backups enthalten die Vorlagen; ältere Backups laden weiter
- [ ] Unit-Tests: Termine je Abstand, Monatsende, Schaltjahr, Nachtragen nach mehreren Monaten, kein doppelter Eintrag

## Entscheidungen

- Vorlagen mit automatischem Eintrag statt Kopieren auf Knopfdruck.
- Abstände monatlich, vierteljährlich, halbjährlich und jährlich.
