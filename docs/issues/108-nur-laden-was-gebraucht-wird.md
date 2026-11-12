# 108 – Heute & To-dos: nur laden, was gebraucht wird

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Technik  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1, §8 (das Verhalten bleibt unverändert)  
**GitHub-Issue:** #108  
**Blockiert durch:** –

## Was gebaut wird

Heute und der Tab To-dos lesen nur noch, was sie zeigen. Bisher laden sie alle To-dos samt Archiv und alle Fälligkeiten aller Zeiten, filtern in Kotlin und rechnen in Heute jede Minute neu, im Hauptthread. Das wächst mit jedem Tag der Nutzung. Es eilt nicht: Das Ticket ist dran, sobald Heute merklich zäh wird.

## Akzeptanzkriterien

- [ ] Am Verhalten ändert sich nichts; alle bestehenden Tests bleiben grün
- [ ] **To-dos**: Die offenen To-dos, die von Heute und die unter „Demnächst“ kommen aus Abfragen, die nur offene To-dos und die heute erledigten lesen, nicht mehr aus `SELECT * FROM todos`; das Archiv wird nur gelesen, wo es gezeigt wird
- [ ] Die Zahl der archivierten To-dos im Tab To-dos kommt aus einer Zählung, nicht aus der Liste
- [ ] **Heatmap**: Sie liest nur die erledigten Fälligkeiten und die Erledigt-Zeitpunkte der To-dos seit ihrem ersten Tag (26 Wochen zurück)
- [ ] **Flamme**: Sie liest die Fälligkeiten erst ab dem jüngsten Tag mit Verpasstem oder Ausgelassenem, nicht die ganze Geschichte; der längste Streak wird nur berechnet, wo die Statistik ihn zeigt
- [ ] Heute rechnet nicht mehr im Hauptthread: Das Zusammenführen in `Heute.day()` läuft im Hintergrund
- [ ] Der Minutentakt in Heute liest Heatmap und Flamme nicht jede Minute neu, sondern nur, wenn sich an ihnen etwas ändert oder der Tag wechselt
- [ ] Indizes, wo die neuen Abfragen sie brauchen; dann Schema-Version eins hoch, Migration mit Test (`docs/agents/schema-change.md`)
- [ ] Ein Test mit großem Bestand (etwa 50 000 Fälligkeiten über fünf Jahre und 5 000 archivierte To-dos) zeigt, dass Heute dieselben Werte liefert wie die bisherige Berechnung
- [ ] Unit-Tests für jede neue Abfrage an ihren Grenzen: erster Tag der Heatmap, Tag des Bruchs, heute erledigtes To-do, Wechsel der Zeitzone

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befund T4); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Kein Vorratsspeicher für Tagessummen: Begrenzte Abfragen reichen und halten die Daten an einer Stelle.
- Der Vergleich mit der bisherigen Berechnung im Test sichert, dass sich Streak und Heatmap nicht still ändern.
