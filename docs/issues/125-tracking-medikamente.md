# 125 – Tracking: Grunddaten & Medikamente mit Zeitleiste

**Etappe:** 15 – Tracking  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Grunddaten & Medikamente, Tageseinträge)  
**GitHub-Issue:** #125  
**Blockiert durch:** [124 – Morgen- und Abendeintrag, eigene Symptome & Nachtragen](124-tracking-tageseintraege.md)

## Was gebaut wird

Die einmalige Basiserhebung als Grunddaten und eine Medikamentenliste mit Zeitleiste, deren Einnahmen im Abendeintrag abgefragt werden.

## Akzeptanzkriterien

- [x] **Grunddaten** im Menü: Markdown-Felder Verlauf, Familie, Schlaf, Ernährung und Substanzen, Sonstiges, je mit Leitfragen als Platzhalter nach §17
- [x] **Medikamente** im Menü, frei eingegeben ohne Vorschlagsliste: Name (Pflicht), Darreichung, Einnahmezeiten (eine oder mehrere), wofür (optional), Schalter „erhöht laut Beipackzettel das Thromboserisiko“ und „senkt den Puls“, Freitext „Nebenwirkungen, auf die ich achte“
- [x] **Zeitleiste** je Medikament: Beginn (Pflicht), Dosisänderungen (Datum, alt → neu, frei als Text), Ende; aktive oben, beendete darunter
- [x] **Abendeintrag:** je an dem Tag aktives Medikament „wie geplant“ (vorausgewählt) oder abweichend (andere Uhrzeit oder vergessen)
- [x] Hinweis „Medikamente nur mit den Behandelnden ändern oder absetzen“ in Medikamenten und Zeitleiste
- [x] Keine Verknüpfung mit Routinen

## Tests

- [x] Unit: aktives Medikament an einem Tag (Beginn, Ende, Grenzen); Abweichung speichern; Dosisänderungen sortiert
- [x] UI: Medikament anlegen, ändern, beenden; Abweichung im Abendeintrag; Grunddaten bearbeiten
- [x] Testdaten nur erfunden („Medikament A“)

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Medikamente getrennt von den Routinen, „wie geplant“ vorausgewählt. Die Schalter „Thromboserisiko“ und „senkt den Puls“ braucht #129 und #131.

Beim Bauen festgelegt (15.11.2026), nicht einzeln bestätigt:

- Ein Medikament braucht einen Namen und mindestens eine Einnahmezeit; „Fertig“ sagt, was fehlt. Beim Verlassen oder Sperren wird es gespeichert, wenn es sich speichern lässt, sonst bleibt der vorige Stand.
- Dosisänderungen lassen sich erst eintragen, wenn das Medikament gespeichert ist, und gelten sofort.
- Ein Medikament gilt als beendet, sobald sein Ende vor heute liegt; bis einschließlich zum Endtag steht es oben und im Abendeintrag.
- Im Abendeintrag gespeichert wird nur, was anders lief als geplant; ohne Zeile heißt es „wie geplant“.
- Die Leitfragen der Grunddaten stehen unter dem Feld statt als Platzhalter, weil ein Platzhalter nur bei Fokus zu sehen ist.
- Löschen eines Medikaments gibt es nicht; Beenden reicht für die Zeitleiste.
