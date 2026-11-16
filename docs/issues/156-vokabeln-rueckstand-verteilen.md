# 156 – Vokabeltrainer: Rückstand nach einer Pause verteilen

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Feature  
**Art:** 🙋 HITL – braucht Emily (Entscheidungen unten), danach AFK  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer: Tagespensum, Routine)  
**GitHub-Issue:** #156  
**Blockiert durch:** #143

## Was gebaut wird

Nach zwei Wochen ohne Üben sind leicht 300 Karten fällig. Das schreckt ab, und an einem Tag schafft man es nicht. Eine Obergrenze je Tag würde den Rückstand auf mehrere Tage verteilen.

## Zu entscheiden

1. **Obergrenze:** Gibt es eine Grenze für Abfragen am Tag? Vorschlag: je Sprache im Menü des Reiters Karten, Standard „ohne Grenze“, sonst 20–500.
2. **Geschafft:** Gilt das Tagespensum als geschafft, wenn die Grenze erreicht ist? Davon hängt die Routine „Erledigt durch Vokabeln“ ab (#106). Vorschlag: ja, das Pensum ist dann das begrenzte.
3. **Reihenfolge:** Welche Fälligen zuerst? Vorschlag: wie bisher die am längsten fälligen. Alternative: die mit der niedrigsten Behaltenswahrscheinlichkeit nach FSRS.
4. **Neue Karten:** Kommen bei Rückstand neue dazu? Vorschlag: nein, solange Fällige über die Grenze hinaus warten.

## Akzeptanzkriterien

Vorbehaltlich der Entscheidungen:

- [ ] Einstellung „Höchstens N Abfragen am Tag“ je Sprache
- [ ] Das Tagespensum (`Workload.of`) nimmt Fällige bis zur Grenze, neue nur, solange nichts Fälliges wartet und Platz bleibt
- [ ] Startseite und Reiter Karten: „Heute: 100 fällig · 140 warten“
- [ ] „Thema üben“ und „Prüfung üben“ kennen keine Grenze
- [ ] Die Einstellung: Schema-Version eins hoch, Migration mit Test, im Backup (`docs/agents/schema-change.md`)
- [ ] SPEC.md §15 nennt die Grenze

## Tests

- [ ] Unit: Pensum mit Grenze, neue bei Rückstand, geschafft an der Grenze, Routine (#106)
- [ ] UI: Einstellung, Zeile auf der Startseite

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt. Die Fragen oben braucht es vor dem Bau.
