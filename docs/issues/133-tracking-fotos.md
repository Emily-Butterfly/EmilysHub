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

- [ ] Im Ereignis „+ Foto“: startet die Kamera-App über `ACTION_IMAGE_CAPTURE` mit einer URI des eigenen `FileProvider` (nicht exportiert, Recht nur für diesen Aufruf); keine Kameraberechtigung
- [ ] Die Datei wird sofort verschlüsselt (Schlüssel aus Tracking) abgelegt und die unverschlüsselte gelöscht; nie in MediaStore oder Galerie
- [ ] Anzeige als Vorschaubild, Vollbild beim Antippen; Löschen mit Rückfrage
- [ ] `FLAG_SECURE` bleibt; keine Vorschau außerhalb von Tracking
- [ ] Fotos im Backup (#123); optional in der PDF (#132, Standard aus)
- [ ] Alles löschen entfernt die Fotodateien

## Tests

- [ ] Instrumentiert: Foto speichern, keine unverschlüsselte Datei übrig, nicht im MediaStore; Backup mit Foto
- [ ] UI: Foto hinzufügen und löschen

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Fotos kommen, aber zuletzt.
