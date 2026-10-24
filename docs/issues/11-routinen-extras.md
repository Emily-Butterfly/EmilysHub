# 11 – Priorität, Checkliste, Pausieren & Archivieren

**Etappe:** 2 – Routinen & Erinnerungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §2  
**GitHub-Issue:** #11  
**Blockiert durch:** [05 – Einfache Routine anlegen & abhaken](05-einfache-routine.md)

## Was gebaut wird

Routinen bekommen Priorität, eine Checkliste und lassen sich pausieren oder archivieren, ohne den Verlauf zu verlieren.

## Akzeptanzkriterien

- [x] Priorität niedrig / normal / hoch; sichtbar in Liste und Heute
- [x] Checkliste: Unterpunkte pro Fälligkeit abhakbar; sind alle abgehakt, gilt die Routine als erledigt; alternativ direkt komplett abhaken
- [x] Pausieren / Fortsetzen: während der Pause entstehen keine Fälligkeiten
- [x] Archivieren / Wiederherstellen: Routine verschwindet aus der Liste, Verlauf bleibt erhalten

## Umsetzung

Umgesetzt in f2834e7, 53839c2 und 54bda56; Schema-Version 8.

### Priorität

- Im Editor unter „Priorität“: Niedrig · Normal · Hoch, Standard Normal.
- In der Routinen-Liste und in Heute ist der Name umso fetter, je höher die Priorität ist: niedrig normal, normal halbfett, hoch fett und etwas größer (18 statt 16 sp).
- Darunter steht „Hohe Priorität“ (in der Akzentfarbe) oder „Niedrige Priorität“, damit die Priorität nicht nur am Schriftbild hängt; bei normaler steht nichts.

### Checkliste

- Im Editor unter „Checkliste“: bis zu 20 Unterpunkte mit je bis zu 80 Zeichen hinzufügen, ändern und entfernen; leere fallen beim Speichern weg.
- Heute zeigt sie eingerückt unter der Routine; jede Fälligkeit hat ihre eigenen Häkchen.
- Mit dem letzten Unterpunkt ist die Routine erledigt. Wird bei einer erledigten einer abgewählt, ist sie wieder offen, und die anderen bleiben abgehakt.
- Direkt abgehakt, auch per „Erledigt“ in der Benachrichtigung oder im Vollbild-Alarm, gelten alle Unterpunkte als abgehakt. Wird die Routine direkt wieder abgewählt, sind auch die Unterpunkte abgewählt.
- Übersprungene und verpasste Fälligkeiten lassen sich nicht über die Checkliste abhaken.
- Beim Ändern der Checkliste behalten bestehende Unterpunkte ihre Häkchen, entfernte nehmen ihre mit. Bleiben danach nur abgehakte übrig, ist die offene Fälligkeit erledigt.

### Pausieren / Fortsetzen

- Im Editor über das Menü ⋮.
- Pausiert verschwinden die offenen Fälligkeiten, es entstehen keine neuen, nichts erinnert und nichts wird verpasst. Was sein Zeitfenster schon hinter sich hatte, wird vorher noch als verpasst gesetzt.
- Die Routine verlässt Heute und steht in der Liste als „Pausiert“. Was sie erledigt hat, lässt sich bis zum Fortsetzen nicht zurücknehmen, damit während der Pause nichts fällig wird.
- Fortgesetzt ist sie ab dann wieder fällig, am selben Tag zu den Uhrzeiten, die noch kommen; die Pausentage werden nicht nachgeplant.

### Archivieren / Wiederherstellen

- Im Editor über das Menü ⋮. Archiviert verlässt die Routine die Liste und Heute, wie beim Pausieren.
- Unter der Routinen-Liste führt „Archiv (n)“ zu den archivierten Routinen, dort mit „Wiederherstellen“. Wiederhergestellt ist eine Routine aktiv, auch wenn sie vor dem Archivieren pausiert war.
- Erledigte, übersprungene und verpasste Fälligkeiten bleiben beim Pausieren wie beim Archivieren erhalten.

### Datenbank

- Version 8: `routines.priority` und `routines.status` sowie die Tabellen `checklist_items` (pro Routine, mit Reihenfolge) und `checklist_ticks` (pro Fälligkeit und Unterpunkt).
- Bestehende Routinen sind aktiv, normal und ohne Checkliste; die Migration ist auf dem Emulator getestet.

### Offen für später

- Wird eine Routine am selben Tag pausiert und fortgesetzt, kommt eine vorher offene Fälligkeit, die noch nachgeholt werden könnte, nicht zurück.
- Archivierte Routinen lassen sich nur wiederherstellen; zum endgültigen Löschen erst wiederherstellen und dann im Editor löschen.
