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

- Die Fotos liegen als JPEG in der SQLCipher-Datenbank von Tracking (Tabelle `event_photo`, tracking.db v8), nicht als eigene Dateien: so sind sie mit dem Schlüssel von Tracking verschlüsselt, im Backup und mit „Alles löschen“ überschrieben, ohne eigenen Weg dafür ([ADR 0003](../adr/0003-tracking-photos-in-its-database-trips-out-stay-open.md)).
- Neu kodiert, höchstens 1600 px an der langen Kante, ohne Metadaten; höchstens 6 Fotos je Ereignis (eigene Festlegungen), damit viele ins Backup passen.
- Dieselbe Datei für die Kamera-App wie bei den Lektionen (`CameraShot`): gelesen, sofort in die Datenbank, dann gelöscht.
