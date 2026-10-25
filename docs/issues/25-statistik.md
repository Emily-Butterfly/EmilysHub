# 25 – Statistik: Streak, Kalender & Quoten

**Etappe:** 7 – Statistik, Suche & Backups  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §8  
**GitHub-Issue:** #25  
**Blockiert durch:** [10 – Zeitfenster & automatisch „verpasst“](10-zeitfenster-und-verpasst.md), [11 – Priorität, Checkliste, Pausieren & Archivieren](11-routinen-extras.md)

## Was gebaut wird

Auswertung pro Routine und gesamt.

## Akzeptanzkriterien

- [ ] Aktueller und längster Streak; **Übersprungen** unterbricht nicht, **Verpasst** schon, pausierte Tage sind neutral
- [ ] Kalender-Ansicht pro Routine (erledigt / übersprungen / verpasst)
- [ ] Erledigt-Quote für 7 / 30 / 90 Tage, pro Routine und über alle Routinen
- [ ] Nur Tage, an denen die Routine fällig war, zählen
- [ ] Unit-Tests für Streak- und Quotenberechnung

## Hinweise

- **Entschieden:** Bei mehreren Uhrzeiten pro Tag zählt ein Tag für den Streak, wenn keine Fälligkeit des Tages verpasst wurde.
- **Entschieden:** Ein Tag, der nur übersprungen wurde, ist für den Streak neutral: Er unterbricht nicht und zählt nicht mit.
- **Entschieden:** Die Erledigt-Quote lässt Übersprungenes weg: erledigt ÷ (erledigt + verpasst).
