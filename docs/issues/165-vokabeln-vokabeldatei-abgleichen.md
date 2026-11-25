# 165 – Vokabeltrainer: Vokabeldatei abgleichen

**Etappe:** – (Einzelticket)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer, Anlegen)  
**GitHub-Issue:** #165  
**Blockiert durch:** –

## Was gebaut wird

Eine **Vokabeldatei** (`vokabeln.json`, gepflegt außerhalb der App, etwa von einem Tutor) lässt sich im Reiter Karten **abgleichen**: Vokabeln ohne Karte werden neue Karten, die anderen ändern ihre Karte. Keine Karte wird gelöscht, und was geplant ist, bleibt. So lässt sich jede neue Fassung der Datei immer wieder übernehmen.

## Akzeptanzkriterien

### Datei

- [x] Im Menü des Reiters Karten: „Vokabeldatei abgleichen“ → Dateiwähler des Handys; die Karten landen in der Sprache, deren Seite offen ist. Die Datei wird am Inhalt erkannt, nicht an der Endung (auch `vokabeln.json.txt`)
- [x] Form: ein JSON-Objekt mit der Liste `vokabeln`; je Eintrag Pflicht `ar_unvokalisiert` und `bedeutung`, optional `id`, `ar_vokalisiert`, `wurzel`, `wortart`, `kategorie`; alles andere (`meta`, `status`, `fehler_zaehler`, unbekannte Felder) wird übergangen
- [x] Grenzen: bis 3 MB und 5 000 Vokabeln; darüber, oder wenn es kein solches JSON ist, wird die Datei mit dem Grund abgelehnt
- [x] Ein Eintrag ohne die Pflichtfelder oder mit einem zu langen Feld ist nicht lesbar und wird mit seiner Nummer genannt; nichts wird gekürzt

### Schlüssel & Felder

- [x] Schlüssel einer Vokabel: ihre `id`, sonst ihr `ar_unvokalisiert`. Die Karte merkt ihn sich (unsichtbar). Kommt ein Schlüssel in der Datei mehrfach vor, werden diese Vokabeln übersprungen und genannt
- [x] Hat eine Vokabel eine `id`, die noch keine Karte kennt, findet sie ihre Karte auch über ihr `ar_unvokalisiert`; die Karte merkt sich dann die `id`
- [x] Eine Karte ohne Schlüssel (von Hand angelegt) gilt als die der Vokabel, wenn ihre Vorderseite ohne Vokalzeichen wie `ar_unvokalisiert` oder wie `ar_vokalisiert` liest; sie bekommt den Schlüssel und zählt als geändert, „von Hand angelegt“
- [x] Vorderseite ← `ar_vokalisiert`, sonst `ar_unvokalisiert`; Rückseite ← `bedeutung`; Notiz ← „Wortart · Wurzel …“ (eine Wurzel „—“ gilt als leer); Thema ← `kategorie`, ein fehlendes Thema wird angelegt
- [x] Geändert werden nur diese Felder; Planung, Protokoll, Umschrift, Beispielsatz und „In beide Richtungen abfragen“ bleiben
- [x] Würde eine Vokabel eine Vorderseite bekommen, die eine andere Karte schon hat, wird sie nicht übernommen und mit Grund genannt
- [x] Eine Karte, deren Vokabel nicht mehr in der Datei steht, bleibt wie sie ist; eine gelöschte Karte wird beim nächsten Abgleich wieder angeboten

### Vorschau

- [x] Oben „8 neu · 2 geändert · 50 unverändert“, dazu, was nicht lesbar ist und was nicht übernommen wird; ein Knopf „Übernehmen“
- [x] Die neuen Karten eingeklappt, jede abwählbar; die geänderten eingeklappt, je Feld alt → neu
- [x] „In beide Richtungen abfragen“ für die neuen Karten, Standard **an**
- [x] Ist nichts neu und nichts geändert: „Alles schon aktuell“, ohne Knopf
- [x] Übernommen wird alles in einem Zug oder nichts; danach sagt die App, wie viele angelegt und geändert wurden

### Daten & Tests

- [x] Spalte für den Schlüssel an der Karte: Schema-Version eins hoch, Migration mit Test, im Backup (`docs/agents/schema-change.md`)
- [x] Unit: Datei lesen (Pflichtfelder, Grenzen, kein JSON, unbekannte Felder, „—“), Schlüssel mit und ohne `id`, doppelte Schlüssel, Karte von Hand wiedererkennen, Felder ändern ohne Planung, Kollision der Vorderseite, neues Thema, nie löschen, beide Richtungen nur für neue Karten
- [x] SPEC.md §15 nennt den Abgleich

## Entscheidungen

Abgestimmt im Interview vom 24.11.2026: Vokabeldatei und Abgleich (CONTEXT.md); Schlüssel `id` vor `ar_unvokalisiert`; die Datei gewinnt bei ihren Feldern; `status` und `fehler_zaehler` bleiben beim Tutor, die App plant mit FSRS; nie löschen, Gelöschtes wird wieder angeboten und ist abwählbar; beide Richtungen für neue Karten an; nur über den Dateiwähler, kein Teilen an die App (SPEC §9); 3 MB und 5 000 Vokabeln.

Beim Bauen festgelegt (24.11.2026), nicht einzeln bestätigt:

- Der Schlüssel steht in der Spalte `vocab_key` der Karte. Datenbank Version 65.
- Fehlen einer Vokabel `wortart` und `wurzel` oder ihre `kategorie`, bleiben Notiz bzw. Thema ihrer Karte, wie sie sind, statt geleert zu werden.
- Ein Thema wird erst angelegt, wenn eine übernommene Karte es bekommt; eine abgewählte legt keins an.
- Bekommt eine Vokabel nur eine `id` (sonst unverändert), zählt sie als geändert („nur neu verknüpft“), damit die `id` gespeichert wird.
- Der Dateiwähler zeigt alle Dateien (`*/*`); ob es eine Vokabeldatei ist, entscheidet ihr Inhalt.
- Eine Vorderseite gilt als vergeben, solange irgendeine Karte sie vor dem Abgleich hatte, auch wenn deren Vokabel sie im selben Abgleich ändert.
- Nachgebessert am 25.11.2026: Der Dateiwähler bietet JSON ausdrücklich an (`application/json`, Text, unbekannte Typen und alle anderen). Auch „Karten importieren“ bietet JSON an; ist die gewählte Datei eine Vokabeldatei, öffnet sich der Abgleich statt des Imports.
