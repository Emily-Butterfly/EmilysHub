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

- [x] Die 55 Fragen und Antwortstufen (0–6) der deutschen Fassung von funcap.no im Code, in 8 Bereichen; Namensnennung, Lizenz CC BY 4.0 mit Link und „für die App-Darstellung umformatiert“ im Bogen und unter den Quellen
- [x] Startseite: „Monatsfragebogen fällig“, wenn der letzte mindestens 28 Tage zurückliegt; im Menü jederzeit
- [x] Angefangener Bogen bleibt als Entwurf; älter als 7 Tage → beim Öffnen mit Rückfrage verwerfen; der Bogen zählt für den Tag des Abschlusses, kein Zurückdatieren
- [x] Auswertung als reine Logik: Bereichswert = Mittel der beantworteten Fragen; Bereich ohne Antwort leer; Gesamtwert = Mittel der 8 Bereiche nur, wenn alle 8 einen Wert haben (eigene Festlegung, gekennzeichnet); keine Schweregrade
- [x] Verlauf: Gesamtwert und je Bereich als Monatspunkte
- [x] Arzt-PDF: letzter Bogen mit Vorwert je Bereich; daneben die Monatsmittel der täglichen Werte für Erschöpfung, Muskel- und Gelenkschmerzen
- [x] Kein Hinweis aus FUNCAP; erinnert wird über eine normale Routine
- [x] In `tracking.db` (Migration mit Test), im Backup, Alles löschen entfernt die Bögen

## Tests

- [x] Unit: Bereichs- und Gesamtwert, fehlende Antworten, leerer Bereich, „fällig“ an den Grenzen (27/28 Tage), Entwurf 7/8 Tage
- [x] UI: Bogen ausfüllen, unterbrechen, fortsetzen, abschließen

## Entscheidungen

Abgestimmt im Interview vom 16.11.2026: FUNCAP55 statt PROMIS (deutsche Fassung und App-Einbindung lizenzpflichtig, `docs/research/fragebogen-lizenzen.md`); kein DSQ-PEM, weil PEM täglich erfasst wird; Fatigue und Schmerz über die Monatsmittel der täglichen Werte.

Beim Bauen festgelegt (16.11.2026), nicht einzeln bestätigt:

- Die 55 Fragen, die Antworten und die Einleitung stehen wortgleich mit der deutschen PDF von funcap.no in `Funcap.kt` (maschinell abgeglichen), mit Quelle und Lizenz in `Funcap.SOURCE`, die der Bogen, die Ergebnisseite und die PDF zeigen.
- Eine Antwort wird beim Antippen sofort gespeichert; nochmals angetippt, wird sie zurückgenommen. „Abschließen“ geht ab einer Antwort, mit Rückfrage, die die offenen Fragen nennt. Ein Entwurf über 7 Tage lässt sich nicht mehr abschließen; die Seite fragt zuerst, ihn zu verwerfen („Verwerfen“ oder „Zurück“, dann bleibt er bis zum nächsten Öffnen). Läuft er ab, während die Seite offen ist, zeigt „Abschließen“ diese Rückfrage.
- Abgeschlossene Bögen lassen sich ansehen (Werte und Antworten) und löschen, nicht ändern.
- Startseite: „Monatsfragebogen fortsetzen“, solange ein Entwurf weitergeht, sonst „Monatsfragebogen fällig“, wenn einer fällig ist.
- FUNCAP steht als Quelle `FUNCAP` in der zentralen Quellenliste (`Sources.kt`), mit Zeitschrift, DOI und Lizenz.
- `tracking.db` auf Version 11 mit den Tabellen `funcap_form` und `funcap_answer`.
- Monatsmittel in der PDF: Abendwerte der 28 Tage bis zum Tag des letzten Bogens, auch wenn sie vor dem Zeitraum der Zusammenfassung liegen. Die drei Symptome werden über ihre Namen in der Startliste gefunden; ein umbenanntes fehlt dort.
- Der UI-Test läuft mit Robolectric in einer Activity, die Robolectric ohne Eintrag im Manifest baut, statt mit einer zusätzlichen Test-Bibliothek.

