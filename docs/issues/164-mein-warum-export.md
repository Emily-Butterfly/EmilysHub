# 164 – Mein Warum: als Bild exportieren

**Etappe:** 18 – Mein Warum  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §18 (Mein Warum: Als Bild exportieren); §9 (Sicherheit)  
**GitHub-Issue:** #164  
**Blockiert durch:** #163

## Was gebaut wird

Das Board lässt sich als Bilddatei speichern.

## Akzeptanzkriterien

- [x] Im Menü (⋮) der Ansicht „Als Bild exportieren“; bei leerem Board nicht angeboten
- [x] Das ganze Board als PNG, 1080 px breit, im Design, das die App gerade zeigt, mit Fotos, Schriften und Farben
- [x] Höher als etwa 16 000 px: mehrere Bilder, jedes mit den nächsten Gründen in ihrer Reihenfolge, für sich neu angeordnet, nie durch einen Grund hindurch
- [x] Vor dem Speichern der Hinweis „Das Bild ist nicht verschlüsselt.“
- [x] Ein Bild über die Dateiauswahl als „Mein Warum 2026-11-24.png“; mehrere in einen einmal gewählten Ordner als „Mein Warum 2026-11-24 (1 von 3).png“ usw.
- [x] Kein Teilen an andere Apps; nichts bleibt unverschlüsselt im Speicher der App zurück
- [x] SPEC.md §9 und §18 nennen es

## Tests

- [x] Unit: Aufteilen an der Grenze (ein Bild, genau an der Grenze, mehrere; ein einzelner hoher Grund); Dateinamen
- [x] UI: Export mit Hinweis, ein Bild und mehrere in einen Ordner

## Entscheidungen

Am 24.11.2026 mit der Nutzerin abgestimmt (SPEC.md §18, Entscheidungen).
