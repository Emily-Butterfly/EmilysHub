# 83 – Sprachen: Session-Notizen importieren – Textdatei, Foto, PDF

**Etappe:** 12 – Budget-Rechner, Abos & Sprachen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Import & Anhänge), §9, §10, §11  
**GitHub-Issue:** #83  
**Blockiert durch:** #81

## Was gebaut wird

Zu einer Session lassen sich ihre Notizen importieren: eine Textdatei wird Text der Notiz, Fotos und PDFs werden Anhänge, die die App verschlüsselt aufbewahrt und selbst anzeigt. Es sind die ersten Dateien, die die App neben ihrer Datenbank hält.

## Akzeptanzkriterien

### Import

- [ ] **Textdatei** (über den Dateiwähler, `text/*`): Ihr Text wird an die Notiz der Session angehängt, als UTF-8 gelesen und gereinigt wie getippter Text; brächte er die Notiz über 100 000 Zeichen oder ist die Datei kein lesbarer Text, wird sie mit einem Hinweis abgelehnt
- [ ] **Foto** über den Dateiwähler oder „Foto aufnehmen“ mit der Kamera-App des Handys; die App fordert **keine** Kamera-Berechtigung und keine neue Speicher-Berechtigung (§11)
- [ ] Fotos werden neu kodiert (JPEG) und auf höchstens 2000 px an der langen Kante verkleinert, richtig herum; Ortsangaben und andere Metadaten des Fotos gehen dabei nicht mit
- [ ] **PDF** über den Dateiwähler, höchstens 10 MB; größere und Dateien, die sich nicht als PDF öffnen lassen, werden mit einem Hinweis abgelehnt
- [ ] Bis zu 10 Anhänge je Session; der elfte wird abgelehnt
- [ ] Was der Editor ohne Speichern verlässt, hinterlässt keine Anhänge

### Aufbewahren & Anzeigen

- [ ] Anhänge liegen nur verschlüsselt im Speicher der App (AES-GCM, Schlüssel im Android Keystore bzw. von dem der Datenbank abgeleitet), nie im Klartext; auch beim Aufnehmen und Anzeigen bleibt keine Klartext-Datei zurück
- [ ] In der Session als Vorschaubilder; Antippen zeigt das Foto mit Zoom bzw. das PDF seitenweise (Androids `PdfRenderer`), in der App, mit `FLAG_SECURE` wie überall
- [ ] Einzeln löschbar mit Rückfrage; eine gelöschte Session nimmt ihre Anhänge mit; verwaiste Dateien räumt die App auf
- [ ] „Exportieren“ speichert eine entschlüsselte Kopie über den Dateiwähler; kein Teilen an andere Apps, keine Texterkennung

### Backup

- [ ] Backups enthalten die Anhänge, auch das nächtliche; Einspielen stellt sie wieder her; ältere Backups laden weiter (ohne); die Prüfung des Backups kennt die Anhänge (Zahl, Größe, zu welcher Session)
- [ ] Der Abgleich „Backup-Schema aus dem Datenbank-Schema“ (`BackupMigration`) bleibt gültig

### Tests

- [ ] Unit-Tests: Text anhängen und Grenze; Verkleinern und Ausrichtung; Grenzen für PDF und Anzahl; verschlüsselt auf der Platte (kein Klartext zu finden); Löschen und Aufräumen; Backup hin und zurück mit Anhängen; manipuliertes Backup wird abgelehnt. UI-Tests: Textdatei importieren; Foto anhängen und ansehen
- [ ] Geräte-Test (`androidTest`): Foto aufnehmen über die Kamera-App, PDF anzeigen

## Entscheidungen

Abgestimmt im Interview vom 11.11.2026.

- Die Session hat ihren eigenen Text und ihre Anhänge; die Notizen (§7) bleiben ohne Anhänge.
- Anhänge kommen ins Backup, mit Größengrenzen, weil sieben nächtliche Backups jeden Anhang siebenmal halten.
- Keine Texterkennung: offline nur mit einer Google-Bibliothek, die §9 ausschließt.
