# 05 – Einfache Routine anlegen & abhaken

**Etappe:** 2 – Routinen & Erinnerungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §2  
**GitHub-Issue:** #5  
**Blockiert durch:** [03 – Verschlüsselte Datenbank](03-verschluesselte-datenbank.md)

## Was gebaut wird

Erste Routine von Ende zu Ende: anlegen, in der Heute-Übersicht sehen und als erledigt oder übersprungen abhaken. Das Datenmodell trennt Routine und einzelne Fälligkeit, damit spätere Slices darauf aufbauen.

## Akzeptanzkriterien

- [ ] Tab Routinen: Liste, anlegen, bearbeiten, löschen (mit Rückfrage)
- [ ] Felder: Name (Pflicht, Längenbegrenzung), Beschreibung (optional), eine Uhrzeit, täglich
- [ ] Heute zeigt die heutigen Fälligkeiten nach Uhrzeit sortiert
- [ ] Abhaken als **Erledigt** oder **Übersprungen**, rückgängig machbar
- [ ] Datenmodell: Routine + Fälligkeit mit Status offen / erledigt / übersprungen / verpasst
- [ ] Unit-Tests für Zeitzone und Sommerzeitwechsel
