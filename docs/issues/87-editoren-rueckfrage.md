# 87 – Editoren: Rückfrage statt stillem Verwerfen

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §8 (Editoren)  
**GitHub-Issue:** #87  
**Blockiert durch:** –

## Was gebaut wird

Wer einen Editor mit geänderten Eingaben verlässt, wird gefragt. Bisher verwirft „Zurück“ alles ohne ein Wort, per Pfeil wie per Wischgeste.

## Akzeptanzkriterien

- [ ] Gilt für die Editoren von To-do, Routine, Ziel, Notiz, Ort, Buchung, Fixbuchung, Abo, Session und Lehrer und für den Budget-Rechner; die Seiten der Dokumentation bleiben, wie sie sind (sie speichern beim Schließen schon selbst)
- [ ] Verlassen per Pfeil oben links, Zurück-Taste oder Zurück-Geste: Ist der Entwurf gegenüber dem gespeicherten Stand geändert (bei einem neuen Eintrag: gegenüber dem leeren Formular), erscheint der Dialog „Änderungen speichern?“ mit **Speichern · Verwerfen · Weiter bearbeiten**
- [ ] Ohne Änderung schließt der Editor sofort, wie bisher
- [ ] „Speichern“ verhält sich wie der Speichern-Knopf: Lässt sich der Entwurf nicht speichern (z. B. fehlt der Titel), schließt nur der Dialog, und der Editor zeigt das Problem an seinem Feld
- [ ] „Verwerfen“ schließt ohne zu speichern; der Session-Editor gibt dabei wie bisher die importierten Anhänge frei
- [ ] „Weiter bearbeiten“ und Tippen neben den Dialog lassen alles, wie es ist
- [ ] Geändert heißt: Der bereinigte Entwurf (`cleaned()`) unterscheidet sich vom bereinigten Stand; Leerzeichen am Rand zählen nicht
- [ ] Der Dialog übersteht Drehen und die App-Sperre
- [ ] Löschen aus dem Editor fragt wie bisher nur nach dem Löschen
- [ ] Ein Editor, den ein anderer verdeckt (z. B. eine Notiz, aus einem To-do geöffnet), fragt nicht; sein Entwurf bleibt wie bisher erhalten
- [ ] Ein gemeinsamer Helfer neben `Closing` (`ui/Closing.kt`) trägt die Rückfrage, damit jeder Editor sie auf dieselbe Art bekommt
- [ ] Unit-Test für „geändert“; UI-Tests für jeden Editor: geändert und zurück fragt, unverändert und zurück fragt nicht; dazu einmal Speichern, Verwerfen und Weiter bearbeiten

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befund U2, Vorschlag UX 1); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Rückfrage statt stillem Selbstspeichern: Ein halb ausgefülltes To-do oder eine Routine mit Erinnerung soll nicht unbemerkt scharf werden. Notizen und Sessions speichern selbst (#88).
- Drei Antworten statt zwei, damit ein versehentliches Zurück nichts kostet.
