# 140 – Tracking: Monatsfragebogen FUNCAP55

**Etappe:** 16 – Tracking, zweite Stufe  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Monatsfragebogen, Verlauf, Arzt-Zusammenfassung)  
**GitHub-Issue:** #140  
**Blockiert durch:** –

## Was gebaut wird

Einmal im Monat der Fragebogen FUNCAP55 zur funktionellen Kapazität in der offiziellen deutschen Fassung, mit Bereichswerten in Verlauf und Arzt-PDF.

## Akzeptanzkriterien

- [ ] Die 55 Fragen und Antwortstufen (0–6) der deutschen Fassung von funcap.no im Code, in 8 Bereichen; Namensnennung, Lizenz CC BY 4.0 mit Link und „für die App-Darstellung umformatiert“ im Bogen und unter den Quellen
- [ ] Startseite: „Monatsfragebogen fällig“, wenn der letzte mindestens 28 Tage zurückliegt; im Menü jederzeit
- [ ] Angefangener Bogen bleibt als Entwurf; älter als 7 Tage → beim Öffnen mit Rückfrage verwerfen; der Bogen zählt für den Tag des Abschlusses, kein Zurückdatieren
- [ ] Auswertung als reine Logik: Bereichswert = Mittel der beantworteten Fragen; Bereich ohne Antwort leer; Gesamtwert = Mittel der 8 Bereiche nur, wenn alle 8 einen Wert haben (eigene Festlegung, gekennzeichnet); keine Schweregrade
- [ ] Verlauf: Gesamtwert und je Bereich als Monatspunkte
- [ ] Arzt-PDF: letzter Bogen mit Vorwert je Bereich; daneben die Monatsmittel der täglichen Werte für Erschöpfung, Muskel- und Gelenkschmerzen
- [ ] Kein Hinweis aus FUNCAP; erinnert wird über eine normale Routine
- [ ] In `tracking.db` (Migration mit Test), im Backup, Alles löschen entfernt die Bögen

## Tests

- [ ] Unit: Bereichs- und Gesamtwert, fehlende Antworten, leerer Bereich, „fällig“ an den Grenzen (27/28 Tage), Entwurf 7/8 Tage
- [ ] UI: Bogen ausfüllen, unterbrechen, fortsetzen, abschließen

## Entscheidungen

Abgestimmt im Interview vom 16.11.2026: FUNCAP55 statt PROMIS (deutsche Fassung und App-Einbindung lizenzpflichtig, `docs/research/fragebogen-lizenzen.md`); kein DSQ-PEM, weil PEM täglich erfasst wird; Fatigue und Schmerz über die Monatsmittel der täglichen Werte.
