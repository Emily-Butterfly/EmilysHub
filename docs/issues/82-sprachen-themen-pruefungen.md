# 82 – Sprachen: Themen & Prüfungen

**Etappe:** 12 – Budget-Rechner, Abos & Sprachen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Themen & Prüfungen)  
**GitHub-Issue:** #82  
**Blockiert durch:** #81

## Was gebaut wird

Sessions halten fest, welche Themen dran waren, und jede Sprache bekommt eine Themenliste mit Prüfungen: was bis zur Prüfung noch behandelt werden soll, zum Abhaken.

## Akzeptanzkriterien

### Themen

- [ ] Ein Thema gehört zu einer Sprache und ist optional einer Prüfung zugeordnet; Name Pflicht, je Sprache eindeutig; anlegen, umbenennen, einer Prüfung zuordnen oder wegnehmen, mit Rückfrage löschen (die Sessions verlieren es)
- [ ] Session-Editor: Feld „Themen“ mit den behandelten Themen, aus der Liste der Sprache gewählt oder neu getippt; ein neu getipptes landet offen und ohne Prüfung auf der Liste; wechselt die Session die Sprache, verliert sie ihre Themen (mit Hinweis)
- [ ] Ein Thema ist **offen** oder **abgehakt**; abgehakt wird nur von Hand in der Themenliste, wieder öffnen geht; eine Session, die es behandelt, hakt es nicht ab
- [ ] Das Thema zeigt, in wie vielen Sessions es dran war und wann zuletzt; Antippen listet diese Sessions
- [ ] Reiter **Themen** auf der Seite der Sprache: nach Prüfung gruppiert (anstehende nach Datum, dann „Ohne Prüfung“, abgelegte eingeklappt), in jeder Gruppe offene vor abgehakten

### Prüfungen

- [ ] Je Sprache beliebig viele, mit Name (Pflicht) und Datum (Pflicht); anlegen, ändern, mit Rückfrage löschen (ihre Themen bleiben, ohne Prüfung)
- [ ] Die nächste anstehende steht auf der Karte der Sprache und oben im Reiter Themen: „noch 43 Tage · 12 von 30 offen“; am Tag selbst „heute“, danach „seit 3 Tagen“, bis sie abgelegt ist
- [ ] **Abgelegt** von Hand, optional mit Ergebnis als Freitext; lässt sich zurücknehmen. Die Themen einer abgelegten Prüfung sind nur zum Nachlesen: nicht abhakbar, nicht in Sessions wählbar
- [ ] Offene Themen einer Prüfung lassen sich einzeln oder alle zusammen zu einer anderen Prüfung der Sprache oder zu „Ohne Prüfung“ verschieben
- [ ] Backups enthalten Themen, Prüfungen und die Themen der Sessions; ältere Backups laden weiter

### Tests

- [ ] Unit-Tests: neu getipptes Thema landet auf der Liste; abhaken und öffnen nur von Hand; Zähler und „zuletzt“; nächste Prüfung und ihre Zahlen, auch am Tag und danach; abgelegt und zurück; Verschieben einzeln und zusammen; Löschen von Thema und Prüfung; Sprachwechsel der Session. UI-Tests: Thema in der Session tippen und in der Liste abhaken; Prüfung anlegen und ablegen

## Entscheidungen

Abgestimmt im Interview vom 11.11.2026.

- Themen gehören zur Sprache, nicht zur Prüfung, damit sie ohne Prüfung bestehen und wandern können.
- Mehrere Prüfungen nacheinander je Sprache.
- Abgehakt wird von Hand; die Session hält nur fest, was dran war.
- Zwei Stufen (offen, abgehakt), keine dritte.
