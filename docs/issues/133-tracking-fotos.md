# 133 – Tracking: Fotos an Ereignissen

**Etappe:** 15 – Tracking  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Ereignisse, Arzt-Zusammenfassung)  
**GitHub-Issue:** #133  
**Blockiert durch:** [128 – Ereignisse mit Puls-Timer & Hautangaben](128-tracking-ereignisse.md)

## Was gebaut wird

Fotos zu einem Ereignis, aufgenommen mit der Kamera-App, sofort verschlüsselt in Tracking, nie in der Galerie.

## Akzeptanzkriterien

- [x] Im Ereignis „+ Foto“: startet die Kamera-App über `ACTION_IMAGE_CAPTURE` mit einer URI des eigenen `FileProvider` (nicht exportiert, Recht nur für diesen Aufruf); keine Kameraberechtigung
- [x] Die Datei wird sofort verschlüsselt (Schlüssel aus Tracking) abgelegt und die unverschlüsselte gelöscht; nie in MediaStore oder Galerie
- [x] Anzeige als Vorschaubild, Vollbild beim Antippen; Löschen mit Rückfrage
- [x] `FLAG_SECURE` bleibt; keine Vorschau außerhalb von Tracking
- [x] Fotos im Backup (#123); optional in der PDF (#132, Standard aus)
- [x] Alles löschen entfernt die Fotodateien

## Tests

- [x] Instrumentiert: Foto speichern, keine unverschlüsselte Datei übrig, nicht im MediaStore; Backup mit Foto
- [x] UI: Foto hinzufügen und löschen

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Fotos kommen, aber zuletzt.

Abgestimmt beim Bauen (15.11.2026): Für die Kamera-App bleibt Tracking offen, nur für diesen einen Ausflug; Bildschirm aus sperrt weiter, zurück nach 5 Minuten oder mehr ohne Berührung auch ([ADR 0003](../adr/0003-tracking-photos-in-its-database-trips-out-stay-open.md)).

Beim Bauen festgelegt (15.11.2026), nicht einzeln bestätigt:

- Die Fotos liegen als JPEG in der SQLCipher-Datenbank von Tracking (Tabelle `event_photo`, tracking.db v8), nicht als eigene Dateien: so sind sie mit dem Schlüssel von Tracking verschlüsselt, im Backup und mit „Alles löschen“ überschrieben, ohne eigenen Weg dafür ([ADR 0003](../adr/0003-tracking-photos-in-its-database-trips-out-stay-open.md)). *Abgelöst am 16.11.2026, siehe unten.*
- Neu kodiert, höchstens 1600 px an der langen Kante, ohne Metadaten; höchstens 6 Fotos je Ereignis (eigene Festlegungen), damit viele ins Backup passen.
- Dieselbe Datei für die Kamera-App wie bei den Lektionen (`CameraShot`): gelesen, sofort in die Datenbank, dann gelöscht.

Nachtrag nach dem Review vom 16.11.2026: Im Inhalt des Backups hätte die Datenbank mit Doku und allen übrigen Daten 20 MB geteilt; schon ein paar Dutzend Fotos hätten jedes Backup scheitern lassen. Jetzt folgen die Datenbanken der Inseln dem Inhalt als eigene Datensätze (Backup-Format 3, ältere Backups öffnen weiter), bis 64 MB je Insel; ein Foto, das Tracking über 56 MB brächte, wird mit Hinweis abgelehnt ([ADR 0003](../adr/0003-tracking-photos-in-its-database-trips-out-stay-open.md)).

Abgestimmt am 16.11.2026: Damit die Zahl der Fotos nicht durch das Backup begrenzt ist, liegt jedes Foto als eigene Datei von Tracking (`tracking.db-files`), verschlüsselt mit einem Schlüssel aus dem von Tracking; in `event_photo` steht nur ihr Name (tracking.db v10). Im Backup folgt jedes Foto der Datenbank als eigener Teil derselben Datei und wird einzeln gelesen und geschrieben. Die Fotos aus der Datenbank wandern beim ersten Öffnen nach dem Update in ihre Dateien. Die Grenze von 56 MB mit dem Hinweis „Tracking ist voll“ fällt damit wieder weg ([ADR 0004](../adr/0004-tracking-photos-in-files-of-their-own.md)).

Nachtrag vom 16.11.2026: Ist der Speicher des Handys voll, wird ein Foto mit dem Hinweis „Kein Platz auf dem Handy“ nicht gespeichert, ohne halb geschriebene Datei; Fotos, die noch in der Datenbank auf den Umzug warten, bleiben dort lesbar und ziehen beim nächsten Öffnen um, Tracking öffnet trotzdem; ein Backup, für dessen Fotos beim Öffnen kein Platz ist, sagt das, statt als unlesbar zu gelten ([ADR 0004](../adr/0004-tracking-photos-in-files-of-their-own.md)).
