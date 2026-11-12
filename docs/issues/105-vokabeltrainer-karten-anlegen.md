# 105 – Vokabeltrainer: Karten aus Session-Notiz und Textdatei

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer, Anlegen)  
**GitHub-Issue:** #105  
**Blockiert durch:** #104

## Was gebaut wird

Karten entstehen dort, wo die Wörter schon stehen: aus den Zeilen einer Session-Notiz und aus einer Textdatei. So muss niemand eine Vokabelliste von Hand abtippen.

## Akzeptanzkriterien

### Aus der Session-Notiz

- [ ] Im Session-Editor steht bei der Notiz „Karten aus der Notiz“; ohne gewählte Sprache ist der Knopf aus
- [ ] Er zeigt die Zeilen der Notiz, die sich als Karte lesen lassen: mit „ – “, „ - “, „ = “ oder einem Tabulator zwischen zwei Teilen. Markdown-Zeichen am Zeilenanfang (`-`, `*`, `>`, `#`, `[ ]`) und Fettung zählen nicht mit
- [ ] Jede Zeile zeigt Vorderseite und Rückseite, zum Ankreuzen; angekreuzt sind alle, die es in der Sprache noch nicht gibt. „Seiten tauschen“ dreht alle um
- [ ] Hat die Session genau ein Thema, bekommen die Karten es; bei mehreren lässt sich eins oder keins wählen
- [ ] „N Karten anlegen“ legt sie an und sagt, wie viele angelegt und wie viele als schon vorhanden übersprungen wurden
- [ ] Die Notiz bleibt unverändert; Karten und Notiz hängen danach nicht zusammen

### Aus einer Textdatei

- [ ] Im Reiter Karten öffnet „Karten importieren“ den Dateiwähler des Handys, wie beim Import einer Session-Notiz (`LessonDocuments`)
- [ ] Format: je Zeile eine Karte, die Felder Vorderseite, Rückseite, Umschrift, Notiz, getrennt mit Tabulator oder Semikolon, die letzten beiden optional; UTF-8; Leerzeilen und Zeilen, die mit `#` beginnen, werden übergangen; ein Feld in Anführungszeichen darf das Trennzeichen enthalten
- [ ] Grenzen: bis 1 MB und 2 000 Zeilen; darüber wird die Datei mit dem Grund abgelehnt
- [ ] Vor dem Anlegen eine Vorschau: „312 Karten, 5 schon vorhanden, 2 Zeilen nicht lesbar (Zeile 17, 40)“, die ersten Karten zur Kontrolle und die Wahl eines Themas für alle
- [ ] Ein zu langes Feld macht seine Zeile nicht lesbar; nichts wird stillschweigend gekürzt
- [ ] Angelegt wird alles in einem Zug oder nichts; die neuen Karten liegen in Fach 1 und kommen mit dem Tagespensum (#104)
- [ ] Die Datei wird nur gelesen und nicht behalten

### Tests

- [ ] Unit-Tests: Zeilen erkennen mit jedem Trennzeichen und mit Markdown am Anfang, Seiten tauschen, schon Vorhandenes, Dateiformat mit Tabulator, Semikolon und Anführungszeichen, Grenzen, nicht lesbare Zeilen, Arabisch; UI-Tests: Karten aus einer Notiz anlegen, Datei importieren mit Vorschau

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Feature 3); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Vorschläge zum Ankreuzen statt Markieren im Text: Das geht auf dem Handy mit einem Daumen.
- Ein schlichtes Textformat statt eines fremden Kartenformats: Es lässt sich aus jeder Tabelle speichern.
- Kein Export der Karten; sie stehen im Backup.
