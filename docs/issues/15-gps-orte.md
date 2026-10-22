# 15 – GPS-Orte mit Ankunftserkennung

**Etappe:** 3 – Orte  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §4  
**GitHub-Issue:** #15  
**Blockiert durch:** [14 – Ortsgebundene Routinen (WLAN)](14-ortsgebundene-routinen.md)

## Was gebaut wird

Orte ohne eigenes WLAN werden über einen GPS-Kreis erkannt.

## Akzeptanzkriterien

- [ ] GPS-Ort: „Aktuellen Standort übernehmen“ oder Koordinaten eingeben (mit Bereichsprüfung)
- [ ] Radius 50–500 m, Standard 150 m
- [ ] Ankunftserkennung ohne Google Play Services
- [ ] Überwachung nur aktiv, solange Einträge auf einen GPS-Ort warten (Akku)
- [ ] Unit-Tests für Entfernungsberechnung und Radiusprüfung
