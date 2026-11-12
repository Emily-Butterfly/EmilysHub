# 103 – Ablauf-Modus: Start aus Erinnerung und Vollbild-Alarm

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §3, §2 (Ablauf-Modus)  
**GitHub-Issue:** #103  
**Blockiert durch:** #101

## Was gebaut wird

Die Erinnerung an eine Routine mit Checkliste führt mit einem Tipp in ihren Ablauf. So wird aus „daran denken“ gleich „anfangen“.

## Akzeptanzkriterien

- [ ] Die Benachrichtigung einer Routine mit Checkliste bietet **Starten · Erledigt · Später** statt „Erledigt · Später · Überspringen“; Routinen ohne Checkliste bleiben, wie sie sind
- [ ] „Später“ bietet dort wie bisher 10 min, 30 min und „Heute nicht“
- [ ] Der Vollbild-Alarm einer Routine mit Checkliste bietet entsperrt zusätzlich **Starten**, neben Erledigt, Später, Überspringen und Heute nicht
- [ ] „Starten“ öffnet die App hinter ihrer Sperre direkt im Ablauf dieser Fälligkeit (#101); das Klingeln endet, und die Erinnerung ist zurückgestellt wie nach „Später“, um den Abstand des Nachfassens der Routine. Ist die Routine dann noch offen, erinnert sie wieder
- [ ] Auf dem Sperrbildschirm wirkt „Starten“ wie die anderen Aktionen erst nach dem Entsperren
- [ ] Ist die Fälligkeit inzwischen beantwortet, öffnet „Starten“ Heute und nimmt die Erinnerung weg
- [ ] Der gemeinsame Alarm gekoppelter Routinen und seine Benachrichtigung bleiben ohne „Starten“; To-dos und Abos bleiben, wie sie sind
- [ ] Das Aussehen läuft über `ReminderFace`, die Antwort über denselben Weg wie die anderen (`Answer`), damit Benachrichtigung und Alarm gleich antworten
- [ ] Was den Ablauf öffnet, wird geprüft wie die anderen Intents: nur eine gültige, offene Fälligkeit, über die Launcher-Activity mit einem Zusatz, wie beim Abo; keine weitere exportierte Komponente
- [ ] Tests: Knöpfe mit und ohne Checkliste; „Starten“ öffnet den Ablauf nach dem Entsperren und stellt zurück; beantwortete Fälligkeit; gemeinsamer Alarm unverändert

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Feature 2).

- Nicht einzeln bestätigt: „Starten“ ersetzt in der Benachrichtigung „Überspringen“, weil Android dort höchstens drei Knöpfe zeigt. Überspringen bleibt in Heute und im Vollbild-Alarm.
- Zurückstellen statt beenden: Wer startet und abbricht, wird wieder erinnert.
