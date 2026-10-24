# 09 – Vollbild-Alarm pro Routine

**Etappe:** 2 – Routinen & Erinnerungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §3, §11  
**GitHub-Issue:** #9  
**Blockiert durch:** [07 – Erinnerungs-Pop-up zur Uhrzeit](07-erinnerungs-popup.md)

## Was gebaut wird

Wichtige Routinen können statt einer Benachrichtigung einen Vollbild-Alarm auslösen – wie ein Wecker, auch auf dem Sperrbildschirm.

## Akzeptanzkriterien

- [x] Schalter „Vollbild-Alarm“ pro Routine (Standard: aus)
- [x] Auf dem Sperrbildschirm zeigt das Vollbild nur „Erinnerung fällig“; Name und Aktionen erst nach Entsperren
- [x] Unter Android 14 wird `USE_FULL_SCREEN_INTENT` bei Bedarf über die Systemeinstellung angefragt
- [x] Ohne Berechtigung fällt die App auf die normale Benachrichtigung zurück

## Umsetzung

Umgesetzt in 50e0b14, bae2ead, 0f825bd und acf03d1; erweitert in 54c224a, nachdem der Alarm auf einem Tablet in Benutzung nur als normale Benachrichtigung kam. Schema-Version 6.

### Schalter und Erlaubnis

- Im Editor unter den Wiederholungs-Erinnerungen: „Vollbild-Alarm“ mit dem Zusatz „Wie ein Wecker, auch auf dem Sperrbildschirm“, Standard aus.
- Ab Android 14 braucht die App die Erlaubnis für Vollbild-Benachrichtigungen (`USE_FULL_SCREEN_INTENT`). Beim Einschalten erklärt sie auf Deutsch, wozu, und öffnet die Systemeinstellung. Solange Android sie nicht erteilt, steht das im Editor, mit „Erlauben“ zur Einstellung; geprüft wird bei jeder Rückkehr in die App.
- Jede Erinnerung prüft die Erlaubnis neu. Ohne sie kommt die normale Benachrichtigung, und der Schalter bleibt an, bis Android es erlaubt.

### Wann Android das Vollbild zeigt

- Den ganzen Bildschirm füllt Android nur, wenn das Gerät gesperrt oder der Bildschirm aus ist.
- Wird das Gerät gerade benutzt, zeigt Android stattdessen die Erinnerung als Banner oben. Das gilt für alle Apps, auch für Wecker, und lässt sich nicht umgehen. Der Alarm klingelt dann trotzdem (siehe unten), und ein Tipp auf die Erinnerung öffnet den Vollbild-Alarm statt der App.

### Sperrbildschirm

- Solange das Gerät gesperrt ist, zeigt der Vollbild-Alarm nur „Erinnerung fällig“ mit „Entsperren“ und „Schließen“.
- Erst nach dem Entsperren, über „Entsperren“ oder direkt auf dem Sperrbildschirm (z. B. per Fingerabdruck), erscheinen Name, Uhrzeit und **Erledigt · Später · Überspringen**. „Später“ bietet 10 Min. · 30 Min. · 1 Std. an, soweit sie vor dem Ende des Zeitfensters zurückkommen.

### Klingeln wie ein Wecker

- Ein Vollbild-Alarm klingelt mit dem Alarmton aus den Android-Einstellungen in Schleife, in Wecker-Lautstärke, und vibriert im Wechsel; der Vollbild-Alarm hält dabei den Bildschirm an.
- Das Klingeln kommt aus einem Vordergrunddienst. So klingelt es auch, wenn Android nur das Banner zeigt, und Android beendet die App währenddessen nicht. Solange es klingelt, steht oben eine kleine Benachrichtigung „Alarm klingelt“ mit „Stumm“; ein Tipp darauf öffnet den Alarm.
- Still wird es nach höchstens 10 Minuten, durch eine Antwort auf die Erinnerung (im Alarm, in der Benachrichtigung oder in der App), durch „Stumm“, „Schließen“ oder Zurück, oder wenn der Vollbild-Alarm länger als 3 Sekunden nicht zu sehen ist (Ein-/Aus-Taste, Home, Anruf). Ein kurzes Verschwinden, wie es manche Geräte beim Einschalten des Bildschirms haben, zählt nicht. Die Erinnerung bleibt jeweils stehen.
- Jede Wiederholungs-Erinnerung und jeder neue Vollbild-Alarm klingelt wieder. Wiederholungen werden dafür neu gepostet, weil Android das Vollbild nur bei neuen Benachrichtigungen zuverlässig öffnet.
- Kommen mehrere Vollbild-Alarme zugleich, steht der neueste vorne; nach der Antwort folgt der nächste noch fällige.
- Vollbild-Erinnerungen gelten als Wecker: „Nicht stören“ lässt sie durch, wo es Wecker durchlässt.

### Sicherheit und Berechtigungen

- Der Vollbild-Alarm und der Klingel-Dienst sind nicht exportiert. Geöffnet wird der Alarm nur über die unveränderlichen, ausdrücklichen Intents der App: das Vollbild der Erinnerung oder ein Tipp auf sie oder auf „Alarm klingelt“; `FLAG_SECURE` gilt auch hier.
- Er zeigt nicht mehr als die Benachrichtigung selbst und liegt deshalb nicht hinter der App-Sperre. Seine Zeit auf dem Bildschirm zählt für die App-Sperre als Zeit außerhalb der App, sonst könnten Alarme die App entsperrt halten.
- Berechtigungen: `USE_FULL_SCREEN_INTENT`, `VIBRATE` sowie `FOREGROUND_SERVICE` und `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` (der Diensttyp für Wecker, die exakte Alarme setzen dürfen); SPEC.md §11 führt sie auf.

### Datenbank

- Version 6: `routines.full_screen`, bestehende Routinen ohne Vollbild-Alarm; die Migration ist auf dem Emulator getestet.

### Offen für später

- Ist in den Android-Einstellungen kein Alarmton gewählt, vibriert der Alarm nur.
- Auf echten Geräten noch nicht ausprobiert: Klingeln bei ausgeschaltetem Bildschirm, das kurze Verschwinden beim Einschalten des Bildschirms, das erneute Vollbild bei Wiederholungen und das Klingeln mit Banner auf dem Tablet in Benutzung.
