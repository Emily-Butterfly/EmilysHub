# 88 – Notizen & Sessions: beim Verlassen von selbst speichern

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §8 (Editoren), §7, §15  
**GitHub-Issue:** #88  
**Blockiert durch:** #87

## Was gebaut wird

Eine Notiz und eine Session gehen beim Verlassen des Editors nicht mehr verloren: Der Editor speichert von selbst, wenn sich der Entwurf speichern lässt.

## Akzeptanzkriterien

- [x] Notiz-Editor: Verlassen per Pfeil, Zurück-Taste oder Zurück-Geste speichert einen geänderten Entwurf von selbst und schließt; ein kurzer Hinweis „Gespeichert“ bestätigt es
- [x] Notiz ohne Titel: Die erste nicht leere Zeile des Textes wird der Titel, ohne Markdown-Zeichen am Anfang (`#`, `-`, `>`, `[ ]`), auf 80 Zeichen gekürzt; der Text bleibt, wie er ist
- [x] Eine neue Notiz ohne Titel und ohne Text schließt, ohne etwas anzulegen
- [x] Lässt sich der Entwurf nicht speichern (Titel oder Text zu lang), gilt die Rückfrage aus #87
- [x] Session-Editor: Sind alle Pflichtfelder gültig, speichert er beim Verlassen von selbst, samt neu importierten Anhängen; fehlt etwas (z. B. der Lehrer), gilt die Rückfrage aus #87
- [x] Der Knopf „Speichern“ bleibt in beiden Editoren; „Archivieren“ und „Löschen“ bleiben, wie sie sind
- [x] Eine archivierte Notiz ist weiter nur zu lesen
- [x] Wird ein Editor nur verdeckt (z. B. beim Ansehen eines Anhangs), speichert er nicht; sein Entwurf bleibt
- [x] Geht die App in den Hintergrund oder sperrt sie sich, bleibt der Entwurf wie bisher im Editor erhalten; gespeichert wird erst beim Verlassen
- [x] Unit-Tests: Titel aus der ersten Zeile (Überschrift, Liste, nur Leerzeilen, Arabisch, über 80 Zeichen); UI-Tests: Notiz tippen und zurück speichert; leere neue Notiz legt nichts an; Session vollständig und zurück speichert, unvollständig fragt

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Vorschlag UX 1, Punkt 2); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Selbstspeichern nur dort, wo ein unfertiger Stand nichts auslöst: Notizen und Sessions erinnern an nichts.
- Kein Speichern bei jedem Tastendruck: Der Entwurf übersteht Drehen, Sperre und Prozessende schon im Editor.

Bei der Umsetzung festgelegt (13.11.2026):

- Der Titel aus der ersten Zeile gilt nur beim Verlassen; der Knopf „Speichern“ zeigt bei fehlendem Titel weiter den Hinweis am Feld.
- Eine Zeile, die nach den Zeichen am Anfang leer ist (etwa nur „#“), zählt nicht als erste Zeile.
- „Gespeichert“ ist ein kurzer Hinweis unten (Toast), wie bei Buchungen über Budget.
- Eine neue Notiz ohne Titel und Text legt auch dann nichts an, wenn Tags oder Verknüpfungen gewählt waren.
