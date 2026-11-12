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

- [ ] Einstellungen, neuer Abschnitt „Eingang“: der Schalter „Gedanke festhalten in den Benachrichtigungen“, Standard aus, mit einem Satz, was er tut
- [ ] An: Dauerhaft steht eine stille Benachrichtigung „Gedanke festhalten“ mit dem Knopf „Schreiben“, der ein Antwortfeld öffnet; ein eigener Kanal mit niedriger Wichtigkeit, ohne Ton und ohne Aufpoppen
- [ ] Was im Antwortfeld abgeschickt wird, landet im Eingang (#93), mit denselben Grenzen; die Benachrichtigung steht danach wieder leer da und zeigt den Text nicht, auch nicht als Verlauf
- [ ] Sie zeigt nie Inhalte aus der App, weder Texte noch die Zahl der Einträge
- [ ] Auf dem Sperrbildschirm steht nur „Gedanke festhalten“; zum Schreiben verlangt Android die Entsperrung des Handys, die App-Sperre wird nicht gefragt
- [ ] Antippen der Benachrichtigung selbst öffnet die App hinter ihrer Sperre, mit der Eingabe aus #94, sobald es sie gibt
- [ ] Das PendingIntent des Antwortfelds ist veränderlich, wie Android es verlangt, explizit und an einen eigenen, nicht exportierten Empfänger gerichtet; alle anderen bleiben unveränderlich. Ein Kommentar an der Stelle nennt die Ausnahme von SPEC §9
- [ ] Der Empfänger nimmt nur den Text des Antwortfelds an, prüft ihn wie die App und ignoriert alles andere
- [ ] Nach einem Neustart des Handys und einem Update der App steht die Benachrichtigung wieder; wurde sie weggewischt, kommt sie beim nächsten Öffnen der App zurück
- [ ] Fehlt die Erlaubnis für Benachrichtigungen, fragt der Schalter danach; ohne sie bleibt er aus
- [ ] Schalter aus: Benachrichtigung und Kanal verschwinden
- [ ] Die Einstellung bleibt auf dem Gerät und steht nicht im Backup
- [ ] Tests: an und aus; eine Antwort landet im Eingang; die Benachrichtigung zeigt den Text nicht; fremde Intents werden ignoriert; wieder da nach dem Neustart; ein Test hält fest, dass genau dieses eine PendingIntent veränderlich ist

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Feature 1).

- **Bestätigt am 12.11.2026:** Das Antwortfeld darf ein veränderliches PendingIntent nutzen, als einzige Ausnahme von SPEC §9.
- Standard aus: Eine dauerhafte Benachrichtigung soll eine bewusste Wahl sein.
- Keine Kachel in den Schnelleinstellungen: Sie bräuchte einen exportierten Dienst (SPEC §9).
- Nur schreiben: So bleibt alles, was im Eingang liegt, hinter der App-Sperre.
