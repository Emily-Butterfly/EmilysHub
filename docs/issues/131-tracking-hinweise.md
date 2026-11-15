# 131 – Tracking: Hinweise mit Quellen

**Etappe:** 15 – Tracking  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Hinweise, Quellen der Schwellen)  
**GitHub-Issue:** #131  
**Blockiert durch:** [130 – Wochenauswertung & Verlauf](130-tracking-wochenauswertung.md)

## Was gebaut wird

Regeln erkennen Muster in den Einträgen und schlagen den nächsten Schritt fürs Arztgespräch vor, mit Begründung, Datenbasis und Quelle. Keine Diagnose.

## Akzeptanzkriterien

- [ ] Regelwerk als reine Logik ohne Android; Schwellen, eigene Arbeitswerte und Quellenkürzel an einer Stelle
- [ ] Alle Regeln nach §17: Basisuntersuchungen (abhakbar), Atemnot + Thromboserisiko, Ruhepuls am Morgen > 100, Medikamente (6 Wochen), Stehtest Pulsanstieg, Blutdruckabfall (früh und verzögert), Beschwerden ohne Messauffälligkeit, PEM (Folgetage oder Episoden mit Abstand 12–72 h), Haut, Schlaf, Stimmung, Trinkmenge
- [ ] **Stufen:** A zählt wie eigene Messungen; B nur als Stütze eines schon ausgelösten Hinweises („auch … war niedriger“); C nie
- [ ] Jeder Hinweis: Muster mit „passt zu“/„spricht für“, Datenbasis (Anzahl, Zeitraum, Stufe), nächster Schritt (Fachrichtung, Frage), Quellenkürzel mit vollständiger Angabe beim Antippen
- [ ] **Mindestdaten:** fehlen sie, „noch X Stehtests/Tage/Ereignisse/Anstrengungen“ statt eines Hinweises
- [ ] Wortwahl geprüft: nie „Du hast“, keine Anweisung zu Medikamenten außer „mit den Behandelnden besprechen“
- [ ] Hinweise auf der Startseite, neueste und wichtigste oben; „erledigt“ blendet einen Hinweis aus, bis sich seine Datenbasis ändert

## Tests

- [ ] Unit: jede Regel an ihren Schwellen, mit zu wenig Daten, mit Stufe A/B/C; B allein löst nichts aus; Wortwahl der Texte (keine verbotenen Formulierungen)
- [ ] UI: Hinweis öffnen, Quelle anzeigen, als erledigt markieren

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Mindestdaten und Arbeitswerte fest, als eigene Festlegung gekennzeichnet; Gewichtung über die drei Stufen. „Erledigt bis sich die Datenbasis ändert“ kam im Interview nicht vor.
