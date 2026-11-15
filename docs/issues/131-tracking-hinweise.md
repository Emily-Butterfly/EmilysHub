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

- [x] Regelwerk als reine Logik ohne Android; Schwellen, eigene Arbeitswerte und Quellenkürzel an einer Stelle
- [x] Alle Regeln nach §17: Basisuntersuchungen (abhakbar), Atemnot + Thromboserisiko, Ruhepuls am Morgen > 100, Medikamente (6 Wochen), Stehtest Pulsanstieg, Blutdruckabfall (früh und verzögert), Beschwerden ohne Messauffälligkeit, PEM (Folgetage oder Episoden mit Abstand 12–72 h), Haut, Schlaf, Stimmung, Trinkmenge
- [x] **Stufen:** A zählt wie eigene Messungen; B nur als Stütze eines schon ausgelösten Hinweises („auch … war niedriger“); C nie
- [x] Jeder Hinweis: Muster mit „passt zu“/„spricht für“, Datenbasis (Anzahl, Zeitraum, Stufe), nächster Schritt (Fachrichtung, Frage), Quellenkürzel mit vollständiger Angabe beim Antippen
- [x] **Mindestdaten:** fehlen sie, „noch X Stehtests/Tage/Ereignisse/Anstrengungen“ statt eines Hinweises
- [x] Wortwahl geprüft: nie „Du hast“, keine Anweisung zu Medikamenten außer „mit den Behandelnden besprechen“
- [x] Hinweise auf der Startseite, neueste und wichtigste oben; „erledigt“ blendet einen Hinweis aus, bis sich seine Datenbasis ändert

## Tests

- [x] Unit: jede Regel an ihren Schwellen, mit zu wenig Daten, mit Stufe A/B/C; B allein löst nichts aus; Wortwahl der Texte (keine verbotenen Formulierungen)
- [x] UI: Hinweis öffnen, Quelle anzeigen, als erledigt markieren

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Mindestdaten und Arbeitswerte fest, als eigene Festlegung gekennzeichnet; Gewichtung über die drei Stufen. „Erledigt bis sich die Datenbasis ändert“ kam im Interview nicht vor.

Beim Bauen festgelegt (15.11.2026), nicht einzeln bestätigt:

- Regeln, Schwellen, eigene Festlegungen und Quellen an einer Stelle: `HintRules` und `Hints` in `Hints.kt`; die Texte stehen dort mit, damit ein Test ihre Wortwahl prüft. Keine neue Tabelle: „erledigt“ steht in den Einstellungen von Tracking.
- Zeiträume (eigene Festlegung): Regeln über Tage schauen 4 Wochen zurück, die über Stehtests, Ereignisse, Anstrengungen, Episoden und Medikamente 12 Wochen. „Mehrzahl“ heißt mehr als die Hälfte.
- Mindestdaten je Regel aus der eigenen Art von Einträgen: 14 Tage mit Morgenpuls, Schlafqualität, Stimmung/Angst oder Trinkmenge; 3 Stehtests an verschiedenen Tagen (für Pulsanstieg, Blutdruckabfall und Beschwerden ohne Auffälligkeit, je Muster in mindestens 2 Tests); 5 Ereignisse (je Hautmuster in mindestens 2); 3 Anstrengungen der Stärke 2–3 mit Antworten oder Abendeinträgen an Tag +1 und +2. Atemnot mit Thromboserisiko hat keine Mindestdaten, weil sie zeitnah zu klären ist; sie steht als dringend ganz oben.
- Atemnot bei Belastung oder Wärme: aus Ereignissen, Stehtests und Abenden mit Hitze/langem Stehen oder Belastung ≥ 2; Symptome „Atemnot“, „Stimmung gedrückt“ und „Angst/innere Unruhe“ über ihren Namen aus der Startliste, umbenannt sieht die Regel sie nicht mehr.
- Medikamente: Beginn oder höhere Dosis (die ersten Zahlen von alt und neu; ohne Zahl zählt die Änderung); Verschlechterung, wenn das Mittel der Symptome in den 6 Wochen danach um mindestens 0,5 über dem der 2 Wochen davor liegt, mit je mindestens 7 Abenden (eigene Arbeitswerte). Fehlen Abende danach, solange die 6 Wochen laufen: „noch X Tage“.
- PEM: Verschlechterung ist die Antwort „ja“ auf „Schlechter als vor der Anstrengung?“ an Tag +1 oder +2; gezählt werden die letzten 3 Anstrengungen mit den Tagen danach. Bestätigte Vorschläge sind eigene Anstrengungen; die aus Schritten bringen Stufe A in die Datenbasis.
- Stufe B stützt: Garmin-Ruhepuls beim Morgenpuls und bei PEM (höher an den Tagen danach), Garmin-Schlafdauer beim Schlaf (kürzer in den schlechten Nächten). Stufe A zählt beim Stehtest über die aus der Garmin-Kurve gefüllten Punkte. Stufe C geht in keine Regel ein.
- Haut: „verschwindet im Liegen rasch“ heißt verschwindet im Liegen und höchstens 30 Minuten bis zur Rückbildung.
- „Erledigt“ merkt sich, worauf der Hinweis sich stützt (Tage, Tests, Ereignisse …); er kommt wieder, sobald etwas Neues dazukommt. Die Basisuntersuchungen bleiben abgehakt. Reihenfolge: dringend zuerst, dann der mit dem neuesten Beleg, die Basisuntersuchungen zuletzt; darunter „Noch zu wenig Daten“ mit „noch X …“.
- Die Seite eines Hinweises zeigt Muster, Datenbasis, Stütze, Fachrichtung und Frage, die Quellenkürzel (angetippt mit vollständiger Angabe) und die eigenen Arbeitswerte; „Erledigt“ geht zurück zur Startseite.
