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

- [x] **Textdatei** (über den Dateiwähler, `text/*`): Ihr Text wird an die Notiz der Session angehängt, als UTF-8 gelesen und gereinigt wie getippter Text; brächte er die Notiz über 100 000 Zeichen oder ist die Datei kein lesbarer Text, wird sie mit einem Hinweis abgelehnt
- [x] **Foto** über den Dateiwähler oder „Foto aufnehmen“ mit der Kamera-App des Handys; die App fordert **keine** Kamera-Berechtigung und keine neue Speicher-Berechtigung (§11)
- [x] Fotos werden neu kodiert (JPEG) und auf höchstens 2000 px an der langen Kante verkleinert, richtig herum; Ortsangaben und andere Metadaten des Fotos gehen dabei nicht mit
- [x] **PDF** über den Dateiwähler, höchstens 10 MB; größere und Dateien, die sich nicht als PDF öffnen lassen, werden mit einem Hinweis abgelehnt
- [x] Bis zu 10 Anhänge je Session; der elfte wird abgelehnt
- [x] Was der Editor ohne Speichern verlässt, hinterlässt keine Anhänge

### Aufbewahren & Anzeigen

- [x] Anhänge liegen nur verschlüsselt im Speicher der App (AES-GCM, Schlüssel im Android Keystore bzw. von dem der Datenbank abgeleitet), nie im Klartext; auch beim Aufnehmen und Anzeigen bleibt keine Klartext-Datei zurück
- [x] In der Session als Vorschaubilder; Antippen zeigt das Foto mit Zoom bzw. das PDF seitenweise (Androids `PdfRenderer`), in der App, mit `FLAG_SECURE` wie überall
- [x] Einzeln löschbar mit Rückfrage; eine gelöschte Session nimmt ihre Anhänge mit; verwaiste Dateien räumt die App auf
- [x] „Exportieren“ speichert eine entschlüsselte Kopie über den Dateiwähler; kein Teilen an andere Apps, keine Texterkennung

### Backup

- [x] Backups enthalten die Anhänge, auch das nächtliche; Einspielen stellt sie wieder her; ältere Backups laden weiter (ohne); die Prüfung des Backups kennt die Anhänge (Zahl, Größe, zu welcher Session)
- [x] Der Abgleich „Backup-Schema aus dem Datenbank-Schema“ (`BackupMigration`) bleibt gültig

### Tests

- [x] Unit-Tests: Text anhängen und Grenze; Verkleinern und Ausrichtung; Grenzen für PDF und Anzahl; verschlüsselt auf der Platte (kein Klartext zu finden); Löschen und Aufräumen; Backup hin und zurück mit Anhängen; manipuliertes Backup wird abgelehnt. UI-Tests: Textdatei importieren; Foto anhängen und ansehen
- [x] Geräte-Test (`androidTest`): Foto aufnehmen über die Kamera-App, PDF anzeigen

## Entscheidungen

Abgestimmt im Interview vom 11.11.2026.

- Die Session hat ihren eigenen Text und ihre Anhänge; die Notizen (§7) bleiben ohne Anhänge.
- Anhänge kommen ins Backup, mit Größengrenzen, weil sieben nächtliche Backups jeden Anhang siebenmal halten.
- Keine Texterkennung: offline nur mit einer Google-Bibliothek, die §9 ausschließt.

Bei der Umsetzung festgelegt (12.11.2026):

- Backups haben ein neues Format 2, das als Strom geschrieben und gelesen wird: erst der Inhalt (wie bisher höchstens 20 MB), dann je Anhang ein eigener verschlüsselter Abschnitt (bis 16 MB), dann ein Abschluss; jeder Abschnitt ist an seine Stelle gebunden, keiner lässt sich weglassen, tauschen oder anfügen. Eine Gesamtgrenze gibt es nicht. Backups im Format 1 laden weiter.
- Der Schlüssel der Anhänge ist zufällig und liegt, mit dem Keystore-Schlüssel der Datenbank versiegelt, neben deren Schlüssel.
- Ein Foto wird bis 40 MB eingelesen und als JPEG mit Qualität 85 gespeichert.
- Hinzufügen und Entfernen von Anhängen wirkt erst beim Speichern der Session; vorher Importiertes bleibt bis dahin verschlüsselt liegen und wird beim Verlassen ohne Speichern oder spätestens beim nächsten Start gelöscht.
- Das Foto der Kamera-App liegt nur bis zum Einlesen im Cache der App und wird gleich danach gelöscht, Reste beim nächsten Start.
- Der Geräte-Test bedient die Kamera-App nicht selbst; er schreibt wie sie in die geliehene Datei.
