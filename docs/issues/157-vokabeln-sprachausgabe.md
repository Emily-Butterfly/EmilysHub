# 157 – Vokabeltrainer: Vorlesen mit der Sprachausgabe des Handys

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer); §9 (Sicherheit: Datenabfluss)  
**GitHub-Issue:** #157  
**Blockiert durch:** –

## Was gebaut wird

Ein Lautsprecher-Knopf liest die Seite in der Sprache mit der Sprachausgabe von Android vor (`TextToSpeech`). Die App braucht dafür keine Internet-Berechtigung und nimmt nichts auf.

## Entschieden am 16.11.2026

- **Nur Stimmen ohne Netz.** Die Sprachausgabe ist eine andere App (etwa von Google oder Samsung), und der Text geht an sie (§9). Erlaubt sind nur Stimmen, die ohne Netz auskommen (`Voice.isNetworkConnectionRequired == false`). Ohne eine solche Stimme gibt es keinen Knopf.
- **Nur auf Knopfdruck.** Nichts wird von selbst vorgelesen, auch nicht beim Aufdecken.
- **Stimme je Sprache.** In der Verwaltung der Sprachen bekommt jede Sprache ein Feld „Stimme“, mit den Offline-Stimmen des Handys zur Wahl; ohne gewählte Stimme gibt es keinen Knopf.
- Damit ist eine synthetische Stimme erlaubt: §15 „Keine Tonaufnahmen“ meint Aufnahmen, und hier wird nichts aufgenommen oder gespeichert.

## Akzeptanzkriterien

- [ ] Ein Lautsprecher-Knopf an der Seite in der Sprache: in der Hinrichtung an der Frage, in der Rückrichtung (#141) erst nach dem Aufdecken, damit er die Antwort nicht verrät; vorgelesen wird nur, wenn er gedrückt wird
- [ ] Angeboten werden nur Stimmen ohne Netz; eine Stimme, die Netz braucht, wird nie benutzt, auch nicht als Ersatz
- [ ] Im Karten-Editor ein Lautsprecher am Feld Vorderseite zum Probehören
- [ ] Verwaltung der Sprachen: Feld „Stimme“ je Sprache, Auswahl aus den Offline-Stimmen des Handys, zuerst die, deren Sprache auf Deutsch so heißt wie die Sprache der App (etwa „Arabisch“); „Keine“ als Standard
- [ ] Ohne gewählte oder ohne noch vorhandene Offline-Stimme kein Knopf; die Verwaltung der Sprachen sagt, wie man in den Einstellungen von Android eine Stimme lädt
- [ ] Manifest: die nötige `<queries>`-Angabe für die Sprachausgabe (Sichtbarkeit von Paketen ab Android 11), keine neue Berechtigung
- [ ] Das Feld je Sprache: Schema-Version eins hoch, Migration mit Test, im Backup (`docs/agents/schema-change.md`)
- [ ] SPEC.md §15 und §9 sagen, was gilt

## Tests

- [ ] Unit/Robolectric: Knopf nur mit Offline-Stimme; eine Stimme mit Netz wird nicht angeboten; nichts wird ohne Knopfdruck vorgelesen; Rückrichtung erst nach dem Aufdecken; Wahl der Stimme
- [ ] UI: Lautsprecher in Abfrage und Editor

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt. Offline-Stimmen, Knopfdruck und die Stimme je Sprache sind am 16.11.2026 entschieden (oben).
