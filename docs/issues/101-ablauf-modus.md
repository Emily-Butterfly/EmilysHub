# 101 – Ablauf-Modus: eine Routine Schritt für Schritt

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §2 (Ablauf-Modus), §1 (Menü je Eintrag)  
**GitHub-Issue:** #101  
**Blockiert durch:** #97

## Was gebaut wird

Eine Routine mit Checkliste lässt sich als geführter Ablauf abarbeiten: ein Schritt auf dem Bildschirm, ein großer Knopf. Das hilft beim Anfangen und beim Dranbleiben. Zeiten je Schritt kommen mit #102.

## Akzeptanzkriterien

- [x] Eine offene Fälligkeit einer Routine mit Checkliste zeigt in Heute rechts den Knopf **Starten**; Routinen ohne Checkliste nicht
- [x] „Starten“ öffnet den Ablauf über den Tabs: der Name der Routine, „Schritt 2 von 4“, der aktuelle Unterpunkt groß, der Knopf **Fertig**, dazu **Schritt zurück** und eine Stoppuhr seit dem Start
- [x] „Fertig“ hakt den Unterpunkt ab, über dieselbe Stelle wie in Heute (`Routines.tick`), und zeigt den nächsten offenen; schon Abgehaktes überspringt der Ablauf, auch beim Start
- [x] „Schritt zurück“ nimmt den zuletzt im Ablauf gesetzten Haken zurück und zeigt den Unterpunkt wieder
- [x] Mit dem letzten Unterpunkt ist die Routine erledigt, wie sonst auch; ein Abschluss zeigt „Geschafft“, die Dauer und den Streak der Routine und führt zurück nach Heute
- [x] Der Ablauf ist nur eine andere Sicht auf die Checkliste: Verlassen lässt die Haken, wie sie sind, und „Starten“ nimmt ihn wieder auf, solange die Fälligkeit offen ist
- [x] Endet die Fälligkeit währenddessen (Zeitfenster abgelaufen, in Heute übersprungen oder ausgelassen, Routine pausiert oder gelöscht), schließt der Ablauf und sagt in einem Satz, warum
- [x] Der Bildschirm bleibt an, solange der Ablauf offen ist
- [x] Der Ablauf übersteht Drehen, die App-Sperre und das Beenden des Prozesses, wie die Editoren (`Destination`); die Stoppuhr zählt vom gespeicherten Start weiter
- [x] Lange Unterpunkte brechen um; bei Schriftgröße 150 % bleibt „Fertig“ ohne Scrollen erreichbar
- [x] Nur für Routinen, für Fälligkeiten von heute und für die von gestern, deren Zeitfenster noch läuft; am Vortag (Gestern nachtragen) gibt es kein „Starten“
- [x] Unit-Tests: nächster offener Schritt, Schritt zurück, der letzte Schritt erledigt die Routine, die Fälligkeit endet währenddessen; UI-Tests: Starten in Heute, Fertig bis zum Abschluss, verlassen und wieder aufnehmen, kein „Starten“ ohne Checkliste

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Feature 2); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Der Ablauf hat keine eigenen Daten: Er zeigt die Checkliste der Fälligkeit. So stimmen Heute und Ablauf immer überein.
- Kein Überspringen einzelner Schritte: Erledigt ist die Routine, wenn alle Unterpunkte abgehakt sind; wer abkürzen will, hakt sie in Heute als Ganzes ab.
- Nur Routinen; To-dos mit Checkliste bleiben, wie sie sind.
- Beim Umsetzen (14.11.2026) entschieden: Die Stoppuhr zählt vom ersten „Starten“ der Fälligkeit an, auch wenn der Ablauf zwischendurch verlassen wurde; die Fälligkeit hält dafür den Start (`run_started_at`, Version 50). „Schritt zurück“ nimmt nur Haken zurück, die der Ablauf selbst gesetzt hat; seit #102 merkt er sie sich, solange er läuft, auch über das Verlassen hinweg, nicht aber über „Beenden“.
- Endet die Fälligkeit, schließt der Ablauf und nennt den Grund in einer Toast-Meldung über Heute.
