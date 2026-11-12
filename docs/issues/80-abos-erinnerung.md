# 80 – Abos: Erinnerung zum Kündigen & „Demnächst“ in Heute

**Etappe:** 12 – Budget-Rechner, Abos & Sprachen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §13 (Abos), §1 (Heute-Übersicht), §11  
**GitHub-Issue:** #80  
**Blockiert durch:** #79

## Was gebaut wird

Die App erinnert rechtzeitig daran, ein Abo zu kündigen: mit einer Benachrichtigung und in Heute.

## Akzeptanzkriterien

- [x] Eine **Benachrichtigung** um 21:00 Uhr am ersten Tag des Vorlaufs und noch einmal am letzten Kündigungstag; Standard-Ton für Benachrichtigungen, kein Vollbild-Alarm
- [x] Sie nennt Name und letzten Kündigungstag; auf dem Sperrbildschirm steht nur „Ein Abo ist zu kündigen“; Antippen öffnet das Abo (nach der App-Sperre)
- [x] Knöpfe in der Benachrichtigung und am Abo: „Gekündigt“ und „Läuft weiter“ (Verhalten wie #79); beide beenden die Erinnerungen dieser Laufzeit
- [x] Lief das Handy um 21:00 nicht, kommt die Benachrichtigung beim nächsten Start nach, solange die Frist nicht verstrichen ist; nach einem Neustart des Handys stehen die Erinnerungen wieder
- [x] Geänderte Frist, geänderter Vorlauf, Vorrücken auf die nächste Laufzeit, gekündigt, gelöscht: Die Erinnerung folgt jeweils
- [x] **Heute**, Abschnitt „Demnächst“: das Abo vom ersten Tag des Vorlaufs bis zum letzten Kündigungstag, mit „kündigen bis …“; Antippen öffnet es
- [x] Die Erinnerung läuft über das Erinnerungs-Modul als eigene Quelle neben Routinen und To-dos (`reminders/`), ihr Aussehen über `ReminderFace`
- [x] Statusseite und Warnbanner zählen Abos mit Frist zu dem, wofür Benachrichtigungen und genaue Alarme gebraucht werden
- [x] Unit-Tests: beide Zeitpunkte, Nachholen, nichts nach verstrichener Frist, nichts nach „Gekündigt“ und „Läuft weiter“, Folgen bei Änderungen, Sperrbildschirm-Text, „Demnächst“ von–bis; UI-Test: Abo in Heute

## Entscheidungen

Abgestimmt im Interview vom 11.11.2026.

- 21:00 Uhr, fest; Benachrichtigung statt Vollbild-Alarm; zweimal je Laufzeit.
- Kein Name auf dem Sperrbildschirm.
- „Gekündigt“ in der Benachrichtigung öffnet das Abo mit der Frage nach dem Enddatum (und ggf. nach dem Ende der Fixbuchung), damit es sich wie in der App verhält (#79); „Läuft weiter“ wirkt direkt.
