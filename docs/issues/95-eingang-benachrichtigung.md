# 95 – Eingang: Benachrichtigung mit Antwortfeld

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §16 (Eingang), §9, §11  
**GitHub-Issue:** #95  
**Blockiert durch:** #93

## Was gebaut wird

Ein Gedanke lässt sich festhalten, ohne die App zu öffnen und zu entsperren: über eine stille, dauerhafte Benachrichtigung mit Antwortfeld. Sie kann nur schreiben, nichts zeigen.

## Akzeptanzkriterien

- [x] Einstellungen, neuer Abschnitt „Eingang“: der Schalter „Gedanke festhalten in den Benachrichtigungen“, Standard aus, mit einem Satz, was er tut
- [x] An: Dauerhaft steht eine stille Benachrichtigung „Gedanke festhalten“ mit dem Knopf „Schreiben“, der ein Antwortfeld öffnet; ein eigener Kanal mit niedriger Wichtigkeit, ohne Ton und ohne Aufpoppen
- [x] Was im Antwortfeld abgeschickt wird, landet im Eingang (#93), mit denselben Grenzen; die Benachrichtigung steht danach wieder leer da und zeigt den Text nicht, auch nicht als Verlauf
- [x] Sie zeigt nie Inhalte aus der App, weder Texte noch die Zahl der Einträge
- [x] Auf dem Sperrbildschirm steht nur „Gedanke festhalten“; zum Schreiben verlangt Android die Entsperrung des Handys, die App-Sperre wird nicht gefragt
- [x] Antippen der Benachrichtigung selbst öffnet die App hinter ihrer Sperre, mit der Eingabe aus #94, sobald es sie gibt
- [x] Das PendingIntent des Antwortfelds ist veränderlich, wie Android es verlangt, explizit und an einen eigenen, nicht exportierten Empfänger gerichtet; alle anderen bleiben unveränderlich. Ein Kommentar an der Stelle nennt die Ausnahme von SPEC §9
- [x] Der Empfänger nimmt nur den Text des Antwortfelds an, prüft ihn wie die App und ignoriert alles andere
- [x] Nach einem Neustart des Handys und einem Update der App steht die Benachrichtigung wieder; wurde sie weggewischt, kommt sie beim nächsten Öffnen der App zurück
- [x] Fehlt die Erlaubnis für Benachrichtigungen, fragt der Schalter danach; ohne sie bleibt er aus
- [x] Schalter aus: Benachrichtigung und Kanal verschwinden
- [x] Die Einstellung bleibt auf dem Gerät und steht nicht im Backup
- [x] Tests: an und aus; eine Antwort landet im Eingang; die Benachrichtigung zeigt den Text nicht; fremde Intents werden ignoriert; wieder da nach dem Neustart; ein Test hält fest, dass genau dieses eine PendingIntent veränderlich ist

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Feature 1).

- **Bestätigt am 12.11.2026:** Das Antwortfeld darf ein veränderliches PendingIntent nutzen, als einzige Ausnahme von SPEC §9.
- Standard aus: Eine dauerhafte Benachrichtigung soll eine bewusste Wahl sein.
- Keine Kachel in den Schnelleinstellungen: Sie bräuchte einen exportierten Dienst (SPEC §9).
- Nur schreiben: So bleibt alles, was im Eingang liegt, hinter der App-Sperre.

Bei der Umsetzung festgelegt (13.11.2026):

- Die Einstellung steht in der Tabelle der Einstellungen, die kein Backup mitnimmt, wie die App-Sperre.
- Nach dem Abschicken wird die Benachrichtigung neu gesetzt, ohne Verlauf des Antwortfelds; so wartet sie auch nicht weiter auf die Antwort.
- Vor Android 13 gibt es keine Erlaubnis zu fragen: Sind Benachrichtigungen dort aus, öffnet der Schalter Androids Einstellungen dazu und bleibt aus.
- Sie kommt bei jedem Öffnen der App wieder (`onStart`, nicht nur beim Kaltstart) und nach Neustart und Update über den `RescheduleReceiver`; die Aktualisierungen laufen nacheinander, damit keine verspätete sie nach dem Ausschalten wieder zeigt.
- Werden Benachrichtigungen später verboten, zeigt der Schalter „aus“ und sagt, was fehlt; wurde die Erlaubnis einmal abgelehnt, führt er zu Androids Einstellungen.
- Schreiben verlangt ab Android 12 ausdrücklich das entsperrte Handy (`setAuthenticationRequired`), und die Benachrichtigung bleibt auf dem Handy (`setLocalOnly`), damit Uhren oder spiegelnde Apps nicht über sie schreiben.
- Offen, am Gerät zu prüfen: ob Android den abgeschickten Text kurz als Verlauf zeigt, bevor die neu gesetzte Benachrichtigung ihn ersetzt. Ein leerer oder zu langer Text wird ohne Hinweis verworfen.
- Dass genau ein PendingIntent veränderlich ist, prüft ein Test über die Quellen (`FLAG_MUTABLE` nur in `CaptureNotification.kt`) und einer an der Benachrichtigung selbst.
