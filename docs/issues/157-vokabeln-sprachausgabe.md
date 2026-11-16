# 157 – Vokabeltrainer: Vorlesen mit der Sprachausgabe des Handys

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Feature  
**Art:** 🙋 HITL – braucht Emily (Entscheidungen unten), danach AFK  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer); §9 (Sicherheit: Datenabfluss)  
**GitHub-Issue:** #157  
**Blockiert durch:** –

## Was gebaut wird

Ein Lautsprecher-Knopf liest die Seite in der Sprache mit der Sprachausgabe von Android vor (`TextToSpeech`). Die App braucht dafür keine Internet-Berechtigung und nimmt nichts auf.

## Zu entscheiden

1. **Spezifikation:** §15 sagt „Keine Tonaufnahmen“. Gilt das auch für eine synthetische Stimme? Vorschlag: nein, sie ist erlaubt, denn nichts wird aufgenommen oder gespeichert.
2. **Datenschutz (§9):** Die Sprachausgabe ist eine andere App (etwa von Google oder Samsung), und der Text geht an sie. Manche Stimmen arbeiten über das Netz. Vorschlag: nur Stimmen, die ohne Netz auskommen (`Voice.isNetworkConnectionRequired == false`); ohne eine solche gibt es keinen Knopf.
3. **Sprache:** Welcher Sprachcode gehört zu einer Sprache der App? Vorschlag: ein Feld „Stimme“ in der Verwaltung der Sprachen, mit den Offline-Stimmen des Handys zur Wahl.
4. **Wann:** Nur auf Knopfdruck oder auch von selbst beim Aufdecken? Vorschlag: nur auf Knopfdruck.

## Akzeptanzkriterien

Vorbehaltlich der Entscheidungen:

- [ ] Ein Lautsprecher an der Seite in der Sprache: in der Hinrichtung an der Frage, in der Rückrichtung (#141) erst nach dem Aufdecken, damit er die Antwort nicht verrät
- [ ] Im Karten-Editor ein Lautsprecher am Feld Vorderseite zum Probehören
- [ ] Ohne passende Offline-Stimme kein Knopf; die Verwaltung der Sprachen sagt, wie man in den Einstellungen von Android eine Stimme lädt
- [ ] Manifest: die nötige `<queries>`-Angabe für die Sprachausgabe (Sichtbarkeit von Paketen ab Android 11), keine neue Berechtigung
- [ ] Das Feld je Sprache: Schema-Version eins hoch, Migration mit Test, im Backup (`docs/agents/schema-change.md`)
- [ ] SPEC.md §15 und §9 sagen, was gilt

## Tests

- [ ] Unit/Robolectric: Knopf nur mit Offline-Stimme; Rückrichtung erst nach dem Aufdecken; Wahl der Stimme
- [ ] UI: Lautsprecher in Abfrage und Editor

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt. Die Fragen oben braucht es vor dem Bau.
