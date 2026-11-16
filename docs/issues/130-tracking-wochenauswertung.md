# 130 – Tracking: Wochenauswertung & Verlauf

**Etappe:** 15 – Tracking  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Wochenauswertung & Verlauf)  
**GitHub-Issue:** #130  
**Blockiert durch:** [127 – Stehtest-Assistent mit Auswertung](127-tracking-stehtest.md), [128 – Ereignisse mit Puls-Timer & Hautangaben](128-tracking-ereignisse.md), [129 – Anstrengungen, Vorschläge, PEM- & Crash-Episoden](129-tracking-anstrengungen-episoden.md)

## Was gebaut wird

Kennzahlen je Woche und Liniendiagramme über 4 oder 12 Wochen, aus allen Einträgen und Garmin-Werten.

## Akzeptanzkriterien

- [x] **Wochenauswertung** (Mo–So), Woche wählbar, alle Kennzahlen nach §17, je mit Anzahl der zugrunde liegenden Einträge; fehlende Daten als „–“, nie als 0
- [x] Anteil nachgetragener Einträge sichtbar
- [x] **Verlauf:** je Symptom, Ruhepuls am Morgen und Garmin-Werte ein Liniendiagramm über 4 oder 12 Wochen, Lücken bei fehlenden Tagen; Medikamentenänderungen als senkrechte Markierung, Episoden als Band; Stufe-C-Werte beschriftet „Garmin-Schätzung“
- [x] Diagramme wie in der Dokumentation, mit den Farben des Themes, ohne neue Bibliothek
- [x] Rechenlogik ohne Android

## Tests

- [x] Unit: jede Kennzahl mit vollständigen und lückenhaften Wochen, Wochengrenzen, Zeitumstellung; Markierungen und Bänder
- [x] UI: Woche wechseln, Verlauf 4/12 Wochen

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Wochen Mo–So, Verlauf 4 oder 12 Wochen wie im Entwurf.

Beim Bauen festgelegt (15.11.2026), nicht einzeln bestätigt:

- Rechenlogik rein in `WeekEvaluation`, `TrackingHistory` und `GarminDays`; `TrackingEvaluation` liest alles Nötige auf einmal, solange Tracking offen ist. Keine neue Tabelle, `tracking.db` bleibt Version 6.
- Tage zählen nach der Uhr an der Wand: Stehtests und Ereignisse nach ihrem Beginn, Schritte nach dem Beginn ihres Zeitraums, Schlaf nach dem Tag, an dem er endet; so hat der Tag der Zeitumstellung 23 oder 25 Stunden.
- Jede Kennzahl nennt die Zahl der Einträge, auf denen sie beruht; ohne sie „–“. Anstrengungen und Episoden zählen 0 nur in einer Woche mit Abendeinträgen, sonst „–“. Abweichungen bei Medikamenten als Abende mit mindestens einer Abweichung, von den Abenden mit einem genommenen Medikament. Schritte und Minuten über der Pacing-Schwelle als Mittel je Tag mit Daten; die Schwelle ist die an jenem Tag geltende (Nachtrag nach dem Review vom 16.11.2026; vorher die heute geltende).
- Stehtest-Abfall: „innerhalb von 3 Minuten“ auch bei systolisch < 90 mmHg; „später“ zählt nur ein Test ohne frühen Abfall.
- Verlauf: von Montag vor 4 oder 12 Wochen (die laufende mitgezählt) bis heute; Linien für jedes nicht ausgeblendete Symptom (ausgeblendete mit Werten auch), den Morgen-Ruhepuls und die Garmin-Werte mit Daten: Schritte (A), Ruhepuls (B), Schlafdauer in Stunden (B), Minuten über der Pacing-Schwelle (B, wie die vorsichtige Stufe einer Herzfrequenz), Tiefschlaf als „Garmin-Schätzung“ (C). Medikamente: Beginn, Dosisänderung und Ende als senkrechte Linie und als Liste darunter; Episoden als Band, eine laufende bis heute, ebenfalls als Liste.
- Wochenauswertung und Verlauf als Knöpfe unter den Hinweisen auf der Startseite; die Woche wechselt mit Pfeilen, nicht über die laufende hinaus.
