# 156 – Vokabeltrainer: Rückstand nach einer Pause verteilen

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer: Tagespensum, Routine)  
**GitHub-Issue:** #156  
**Blockiert durch:** #143

## Was gebaut wird

Nach zwei Wochen ohne Üben sind leicht 300 Karten fällig. Das schreckt ab, und an einem Tag schafft man es nicht. Eine Grenze je Sprache und eine über alle Sprachen verteilen den Rückstand auf mehrere Tage.

## Entschieden am 16.11.2026

- **Zwei Grenzen.** Eine je Sprache und eine übergreifende über alle Sprachen zusammen; beide sind einstellbar, Standard ist jeweils „ohne Grenze“.
- **Übergreifend nach Rückstand verteilt.** Jede Sprache bekommt von der übergreifenden Grenze einen Anteil im Verhältnis zu ihrem Rückstand.
- **Geschafft an der Grenze.** Ist die Grenze einer Sprache erreicht, ist ihr Tagespensum geschafft, auch wenn noch Fälliges wartet; eine Routine „Erledigt durch Vokabeln“ (#106) wird dann erledigt.
- **Reihenfolge wie bisher.** Die am längsten fälligen Karten zuerst.
- **Keine neuen Karten bei Rückstand.** Solange Fälliges über die Grenze hinaus wartet, nimmt das Pensum keine neuen Karten.

## Begriffe

- **Abfragen eines Tages:** die Richtungen einer Karte (#141), die das Pensum an dem Tag nimmt, abgefragte und offene; eine Wiederholung nach „Nochmal“ zählt nicht noch einmal.
- **Rückstand einer Sprache an einem Tag:** ihre an dem Tag schon abgefragten fälligen Richtungen und die noch fälligen. Weil die abgefragten mitzählen, bleibt er über den Tag gleich, und damit auch der Anteil.

## Akzeptanzkriterien

### Grenzen

- [ ] Je Sprache im Menü des Reiters Karten: „Höchstens Abfragen am Tag“, „ohne Grenze“ (Standard) oder 20–500
- [ ] Übergreifend im Menü der Startseite von Sprachen: „Höchstens Abfragen am Tag, alle Sprachen“, „ohne Grenze“ (Standard) oder 20–1 000
- [ ] Der Anteil einer Sprache an der übergreifenden Grenze ist die Grenze mal ihr Rückstand geteilt durch den Rückstand aller nicht archivierten Sprachen; gerundet so, dass die Anteile zusammen die Grenze ergeben (größter Rest zuerst, bei Gleichstand nach Name). Ohne Rückstand bekommt eine Sprache keinen Anteil und hat keine Grenze aus der übergreifenden
- [ ] Die Grenze einer Sprache an einem Tag ist das Kleinere aus ihrer eigenen Grenze und ihrem Anteil

### Tagespensum

- [ ] Mit Grenze nimmt das Pensum (`Workload.of`) die fälligen Richtungen, die am längsten fälligen zuerst, bis die Abfragen des Tages die Grenze erreichen
- [ ] **Rückstand über die Grenze hinaus:** keine neuen Karten
- [ ] **Ohne Rückstand über die Grenze hinaus:** neue Karten wie bisher bis zur Zahl „Neue Karten am Tag“, aber nur, soweit die Grenze reicht
- [ ] Ist die Grenze erreicht, ist das Pensum geschafft: die Routine „Erledigt durch Vokabeln“ wird erledigt (#106), die Startseite zeigt „Für heute geschafft“
- [ ] Hin- und Rückrichtung einer Karte nie am selben Tag, wie bisher (#141)
- [ ] „Thema üben“ und „Prüfung üben“ kennen keine Grenze und zählen nicht gegen sie
- [ ] Karten einer archivierten Sprache zählen nirgends mit

### Anzeige

- [ ] Startseite und Reiter Karten nennen, was über die Grenze hinaus wartet: „Heute: 30 fällig · 140 warten“, geschafft „Für heute geschafft · 140 warten“ (mit der Zeile aus #143)
- [ ] Die Auswertung („heute fällig“) zählt weiter alles Fällige, ohne Grenze

### Daten

- [ ] Die Grenze je Sprache an der Sprache, die übergreifende in den Einstellungen der App (`AppSettings`): Schema-Version eins hoch, Migration mit Test, im Backup (`docs/agents/schema-change.md`)
- [ ] SPEC.md §15 nennt die Grenzen und die Regeln oben

## Tests

- [ ] Unit: Pensum mit eigener Grenze; Anteile nach Rückstand mit Rundung, zwei und drei Sprachen, eine ohne Rückstand; das Kleinere aus eigener Grenze und Anteil; Anteil bleibt über den Tag gleich, während geübt wird; keine neuen bei Rückstand, neue ohne Rückstand innerhalb der Grenze; geschafft an der Grenze erledigt die Routine (#106); Rückrichtung (#141); Thema üben ohne Grenze; archivierte Sprache
- [ ] UI: beide Einstellungen; Zeile mit „warten“ auf der Startseite

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029). Die fünf Punkte oben sind am 16.11.2026 entschieden. Nicht einzeln bestätigt: die Spannen (20–500 je Sprache, 20–1 000 übergreifend), wo die Einstellungen stehen, die Rundung der Anteile und dass die Auswertung ohne Grenze zählt.
