# 89 – Backups: ans Auslagern erinnern

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §10 (Auslagern), §1 (Heute-Übersicht)  
**GitHub-Issue:** #89  
**Blockiert durch:** –

## Was gebaut wird

Die App merkt sich, wann die Backups zuletzt vom Handy wegkopiert wurden, und erinnert in Heute daran. Bisher bleibt sie mit nächtlichem Backup still, auch wenn alle Backups nur auf dem Handy liegen.

## Akzeptanzkriterien

- [ ] Backup-Bildschirm: ein Abschnitt „Auslagern“ mit dem bisherigen Hinweis zum Wegkopieren, darunter „Zuletzt ausgelagert am …“ oder „Noch nie ausgelagert“ und der Knopf **Heute weggekopiert**
- [ ] Der Knopf merkt sich den heutigen Tag; eine Leiste bietet „Rückgängig“, dann gilt wieder der Stand davor
- [ ] Heute zeigt die Karte „Deine Backups liegen nur auf dem Handy“, wenn es ein Backup gibt und die letzte Bestätigung über 30 Tage her ist, oder wenn nie bestätigt wurde und das erste Backup auf diesem Gerät über 30 Tage her ist; „Zu den Backups“ öffnet den Bildschirm
- [ ] Für eine bestehende Installation, die schon Backups hat, zählen die 30 Tage ab dem Update
- [ ] Es steht immer nur eine Backup-Karte in Heute, mit diesem Vorrang: fehlgeschlagen, dann veraltet oder noch keins, dann nicht ausgelagert
- [ ] Die Bestätigung ist eine Einstellung auf dem Gerät: Sie steht nicht im Backup und bleibt beim Einspielen eines Backups, wie sie ist
- [ ] Keine Benachrichtigung, nur die Karte in Heute, wie bei den übrigen Hinweisen zu den Backups
- [ ] `NightlyBackups.warning()` kennt den neuen Fall (`BackupWarning`), `BackupWarningCard` zeigt ihn
- [ ] Unit-Tests: Grenze von 30 Tagen, nie bestätigt, Vorrang vor und hinter den anderen Warnungen, Rücknahme, unberührt durch das Einspielen; UI-Tests: Knopf im Backup-Bildschirm, Karte in Heute

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befund T1); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Bestätigen von Hand: Ohne Internet und ohne Zugriff auf andere Geräte kann die App nicht selbst prüfen, ob etwas wegkopiert wurde.
- 30 Tage, wie bei der Warnung vor einem veralteten Backup.
