# 79 – Haushaltsbuch: Abos mit Kündigungsfrist

**Etappe:** 12 – Budget-Rechner, Abos & Sprachen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §13 (Abos)  
**GitHub-Issue:** #79  
**Blockiert durch:** –

## Was gebaut wird

Eine Liste **Abos** im Haushaltsbuch: Verträge mit dem Tag, bis zu dem sie gekündigt sein müssen. Die Erinnerung dazu kommt mit #80.

## Akzeptanzkriterien

- [ ] Im Haushaltsbuch neben den Fixbuchungen die Liste „Abos“, sortiert nach dem nächsten letzten Kündigungstag, mit dem Tag und den Tagen bis dahin; oben die Summe der laufenden Abos, auf den Monat umgerechnet (Abos ohne Betrag zählen nicht); darunter der Abschnitt „Gekündigt“
- [ ] Felder: Name (Pflicht), Betrag und Abstand (optional; monatlich, vierteljährlich, halbjährlich, jährlich), Notiz (optional), Frist, Vorlauf in Tagen (Standard 14), optional eine verknüpfte Fixbuchung; strikte Eingabeprüfung wie überall (§9)
- [ ] Frist als **einzelnes Datum** „kündigen bis“ oder **gerechnet** aus „Laufzeit bis“ (Datum), Kündigungsfrist (Anzahl Tage, Wochen oder Monate vor dem Ende) und Verlängerung um eine Anzahl Monate; der Editor zeigt den letzten Kündigungstag, der sich ergibt
- [ ] Gerechnet: Verstreicht der letzte Kündigungstag ohne Kündigung, rückt das Abo von selbst auf die nächste Laufzeit vor, auch über mehrere, wenn die App länger nicht lief
- [ ] Einzelnes Datum: Verstreicht es, zeigt das Abo „Frist verstrichen“, bis ein neues Datum eingetragen ist
- [ ] **Verknüpfung**: Eine Fixbuchung gehört zu höchstens einem Abo; verknüpft kommen Betrag und Abstand von ihr und sind am Abo nicht änderbar. Der Editor der Fixbuchung bietet „Als Abo führen“ (legt ein verknüpftes Abo mit ihrem Namen an) bzw. öffnet ihr Abo. Wird die Fixbuchung gelöscht, bleibt das Abo und behält Betrag und Abstand als eigene Angabe
- [ ] **Gekündigt**: wandert in den Abschnitt „Gekündigt“ mit seinem Enddatum (Ende der Laufzeit; beim einzelnen Datum gewählt, Standard der Kündigungstag); ist eine Fixbuchung verknüpft, fragt die App, ob sie deren Ende auf dieses Datum setzen soll; lässt sich zurücknehmen
- [ ] **Läuft weiter**: ein gerechnetes Abo rückt auf die nächste Laufzeit vor, eins mit einzelnem Datum wartet auf ein neues
- [ ] Löschen mit Rückfrage; die Fixbuchung bleibt
- [ ] Die Suche findet Abos nach Name und Notiz; Backups enthalten sie, ältere Backups laden weiter (ohne)
- [ ] Unit-Tests: letzter Kündigungstag aus Laufzeit, Frist (Tage, Wochen, Monate, Monatsende) und Verlängerung; Vorrücken über eine und mehrere Laufzeiten; einzelnes Datum verstrichen; Gekündigt mit und ohne Fixbuchung und zurück; Fixbuchung gelöscht; Summe je Monat; Eingabeprüfung. UI-Test: Abo anlegen, „Als Abo führen“, kündigen

## Entscheidungen

Abgestimmt im Interview vom 11.11.2026.

- Eigene Liste, mit einer Fixbuchung nur optional verknüpft: So passen auch Abos ohne Buchung (Probe-Abo, anderes Konto).
- Frist als einzelnes Datum oder gerechnet, beides.
- Keine Tags, keine Kategorien am Abo.
