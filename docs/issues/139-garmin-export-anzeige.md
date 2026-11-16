# 139 – Garmin-Export: Wochenauswertung, Verlauf, Tagesansicht & PDF

**Etappe:** 16 – Tracking, zweite Stufe  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Garmin über Health Connect, Garmin-Export, Wochenauswertung & Verlauf, Arzt-Zusammenfassung)  
**GitHub-Issue:** #139  
**Blockiert durch:** [137 – Import der Tageswerte](137-garmin-export-import.md), [138 – FIT-Decoder & feine Werte](138-garmin-export-fit.md)

## Was gebaut wird

Die Werte aus dem Export und der höchste Puls des Tages erscheinen in Wochenauswertung, Verlauf, einer neuen Tagesansicht und der Arzt-PDF.

## Akzeptanzkriterien

- [ ] **Höchster Puls des Tages** aus der Herzfrequenz von Health Connect, Stufe des Messwerts (A vom Brustgurt, sonst B); keine zweite Quelle
- [ ] **Wochenauswertung:** Mittel der Nacht-HRV und höchster Puls der Woche; keine Werte der Stufe C
- [ ] **Verlauf:** je eine Tageslinie für Nacht-HRV, Stress, Body Battery (höchster und niedrigster), SpO₂, Intensitätsminuten, höchsten Puls; Stufe C beschriftet als „Garmin-Schätzung“; immer Garmins Tageswert
- [ ] **Tagesansicht:** Tippen auf einen Tag im Verlauf zeigt die HRV alle 5 Minuten in der Nacht und Stress alle 3 Minuten, mit Stufe; ohne feine Werte ein klarer Leerzustand
- [ ] **Arzt-PDF:** je Woche das Mittel der Export-Werte mit Stufe, bei Intensitätsminuten die Summe
- [ ] HRV als RMSSD in ms, gerundet erst in der Anzeige
- [ ] Kein neuer Hinweis; die Hinweis-Regeln lesen die Export-Werte nicht

## Tests

- [ ] Unit: Wochenmittel und -summen, höchster Puls mit Stufe, Woche ohne Werte
- [ ] UI: Verlauf mit Export-Linien, Tagesansicht öffnen
- [ ] Unit: PDF-Inhalt enthält die Export-Werte mit Stufe

## Entscheidungen

Abgestimmt im Interview vom 16.11.2026: nur anzeigen, keine Hinweisregel; Garmins Tageswert gilt, feine Werte nur in der Tagesansicht; höchster Puls aus Health Connect; Stufe C nicht in der Wochenauswertung.
