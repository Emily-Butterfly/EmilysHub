# 54 – Notizen: Archiv für Notizen und Ordner

**Etappe:** 9 – Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §7  
**GitHub-Issue:** #54  
**Blockiert durch:** –

## Was gebaut wird

Notizen und ganze Ordner lassen sich archivieren statt löschen: Sie verschwinden aus Tabs, Ordnern und „Alle“, liegen nur zum Lesen im Archiv und lassen sich an ihren alten Ort zurückholen.

## Akzeptanzkriterien

- [ ] „Archivieren“ im Menü einer Notiz, im Menü eines Ordners und im Editor einer Notiz; ohne Rückfrage
- [ ] Ein Ordner kommt mit allem, was darin liegt, ins Archiv, in jeder Tiefe
- [ ] Archiviert verliert eine Notiz bzw. ein Ordner den Pin; Notizen darin verschwinden von Heute. Wiederhergestellt sind sie nicht mehr angepinnt
- [ ] Archivierte Notizen erscheinen nirgends sonst: nicht in Tabs, Ordnern, „Alle“, auf Heute, in der Suche, nicht als verknüpfte Notizen bei Zielen, To-dos und Routinen. Ihre Verknüpfungen und Tags bleiben gespeichert und gelten wiederhergestellt wieder
- [ ] „Archiv“ im Menü des Tabs Notizen: die archivierten Notizen und Ordner, die zuletzt archivierte zuerst, mit Tab und Pfad, aus dem sie kommen
- [ ] Im Archiv: eine Notiz öffnet sich nur zum Lesen (Vorschau), ein Ordner zeigt seinen Inhalt nur zum Lesen
- [ ] „Wiederherstellen“ bringt Notiz bzw. Ordner mit Inhalt an den alten Ort, ans Ende dort; gibt es den Ordner nicht mehr oder liegt er selbst im Archiv, in den nächsten Ordner darüber, der noch da ist, sonst nach oben in den Tab, ohne Tab nach „Allgemein“
- [ ] „Endgültig löschen“ mit Rückfrage, ein Ordner mit allem darin
- [ ] Eine einzeln archivierte Notiz in einem später archivierten Ordner bleibt im Archiv, wenn der Ordner wiederhergestellt wird
- [ ] Wird ein Ordner oder Tab gelöscht, auch mit Inhalt, bleibt Archiviertes darin erhalten: es rückt eine Ebene höher bzw. nach „Allgemein“ und bleibt im Archiv
- [ ] Backups enthalten den Archivstatus; ältere Backups laden weiter (nichts archiviert)
- [ ] Unit-Tests: archivieren und wiederherstellen von Notiz und Ordner, verschwunden aus Ansichten, Suche, Heute und Verknüpfungen, Wiederherstellen ohne alten Ort, Löschen mit Archiviertem darin, endgültig löschen; UI-Test für Archiv und Lesemodus

## Entscheidungen

- Auf Wunsch vom 02.11.2026: Notizen und Ordner; nur zum Lesen; Pins entfernen; an den alten Ort; über das Menü der Notizen.
- Nicht gewählt: in der Suche bleiben, Verknüpfungen weiter zeigen.
- Löschen von Ordner oder Tab löscht nichts Archiviertes mit, damit das Archiv nichts unbemerkt verliert.
