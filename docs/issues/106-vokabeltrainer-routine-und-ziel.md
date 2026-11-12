# 106 – Vokabeltrainer: Routine und Ziel zählen mit

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer: Routine, Karten-Ziel), §2, §6  
**GitHub-Issue:** #106  
**Blockiert durch:** #104

## Was gebaut wird

Wer seine Karten geübt hat, muss die Routine „Vokabeln“ nicht noch einmal abhaken, und ein Ziel wie „Wortschatz 1 000 Wörter“ bekommt echten Fortschritt aus den Karten.

## Akzeptanzkriterien

### Routine

- [ ] Der Routine-Editor bekommt das Feld „Erledigt durch Vokabeln“: eine nicht archivierte Sprache oder „Nein“ (Standard)
- [ ] Ist das Tagespensum der Sprache geschafft (#104), wird die heute offene Fälligkeit der Routine erledigt, wie von Hand abgehakt: Ihre Erinnerungen enden; Streak, Statistik, Heatmap und ein verknüpftes Ziel zählen; gekoppelte Routinen gelten als durch sie erledigt
- [ ] Hat die Routine heute mehrere Fälligkeiten, wird die früheste offene erledigt; eine Checkliste gilt dabei als ganz abgehakt
- [ ] Ist an dem Tag nichts fällig und nichts neu, bleibt die Routine offen
- [ ] Verpasstes, Übersprungenes und Ausgelassenes bleibt, wie es ist; eine pausierte oder archivierte Routine wird nicht erledigt
- [ ] Das Erledigen lässt sich in Heute zurücknehmen wie jedes andere
- [ ] Heute zeigt an einer solchen Routine den Hinweis „durch Vokabeln: Arabisch“, und ihr Menü (#97) bietet „Üben“
- [ ] Wird die Sprache archiviert, ist die Routine wieder eine gewöhnliche

### Karten-Ziel

- [ ] Der Ziel-Editor bekommt „Karten-Ziel“: eine Sprache und eine Zahl sicherer Karten (1–100 000); je Ziel höchstens eins
- [ ] Im Zielbaum steht es als eigene Zeile, wie eine Prüfung (#84): „Karten: Arabisch · 412 von 1 000“; Antippen öffnet den Reiter Karten der Sprache
- [ ] Sein Fortschritt ist der Anteil sicherer Karten an der Zahl, höchstens 100 %, und zählt im Ziel wie ein To-do oder eine Prüfung
- [ ] Erreicht, ist der Teil erledigt. Fällt die Zahl wieder darunter (eine Karte ist nach „Nochmal“ nicht mehr sicher, Karten werden gelöscht), ist er wieder offen, und ein dadurch erledigtes Ziel öffnet sich wieder, wie beim Zurücknehmen von „abgelegt“
- [ ] Wird das Ziel gelöscht, verschwindet das Karten-Ziel; die Karten bleiben
- [ ] Der Abgleich läuft über `GoalWatch`, wie bei To-dos, Routinen und Prüfungen

### Daten & Tests

- [ ] Das Feld an der Routine und das Karten-Ziel: Schema-Version eins hoch, Migration mit Test, im Backup (`docs/agents/schema-change.md`)
- [ ] Unit-Tests: geschafftes Pensum erledigt die Routine; nichts fällig lässt sie offen; mehrere Fälligkeiten; zurücknehmen; gekoppelte Gruppe; Karten-Ziel mit Fortschritt, erreicht, wieder darunter, Ziel öffnet sich wieder, Ziel gelöscht; UI-Tests: Feld im Routine-Editor, Zeile im Zielbaum

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Feature 3); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Die Routine wird nur an Tagen mit Pensum erledigt: Ein Tag ohne Karten soll nicht als geübt zählen.
- Gezählt werden sichere Karten (Abstand ab 21 Tagen, #104), nicht alle angelegten: Ein Import soll das Ziel nicht sofort erfüllen.
