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

- [ ] **Grunddaten** im Menü: Markdown-Felder Verlauf, Familie, Schlaf, Ernährung und Substanzen, Sonstiges, je mit Leitfragen als Platzhalter nach §17
- [ ] **Medikamente** im Menü, frei eingegeben ohne Vorschlagsliste: Name (Pflicht), Darreichung, Einnahmezeiten (eine oder mehrere), wofür (optional), Schalter „erhöht laut Beipackzettel das Thromboserisiko“ und „senkt den Puls“, Freitext „Nebenwirkungen, auf die ich achte“
- [ ] **Zeitleiste** je Medikament: Beginn (Pflicht), Dosisänderungen (Datum, alt → neu, frei als Text), Ende; aktive oben, beendete darunter
- [ ] **Abendeintrag:** je an dem Tag aktives Medikament „wie geplant“ (vorausgewählt) oder abweichend (andere Uhrzeit oder vergessen)
- [ ] Hinweis „Medikamente nur mit den Behandelnden ändern oder absetzen“ in Medikamenten und Zeitleiste
- [ ] Keine Verknüpfung mit Routinen

## Tests

- [ ] Unit: aktives Medikament an einem Tag (Beginn, Ende, Grenzen); Abweichung speichern; Dosisänderungen sortiert
- [ ] UI: Medikament anlegen, ändern, beenden; Abweichung im Abendeintrag; Grunddaten bearbeiten
- [ ] Testdaten nur erfunden („Medikament A“)

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Medikamente getrennt von den Routinen, „wie geplant“ vorausgewählt. Die Schalter „Thromboserisiko“ und „senkt den Puls“ braucht #129 und #131.
