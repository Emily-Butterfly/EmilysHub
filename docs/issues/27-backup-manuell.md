# 27 – Verschlüsseltes Backup: Export & Import

**Etappe:** 7 – Statistik, Suche & Backups  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §9, §10  
**GitHub-Issue:** #27  
**Blockiert durch:** [03 – Verschlüsselte Datenbank](03-verschluesselte-datenbank.md)

## Was gebaut wird

Manueller Export aller Daten in eine passwortgeschützte Datei und Import auf demselben oder einem neuen Gerät.

## Akzeptanzkriterien

- [ ] Backup-Passwort festlegen (Mindestlänge, Bestätigung)
- [ ] Export über den Systemdialog in eine frei gewählte Datei
- [ ] Versioniertes Format; AES-256-GCM; Schlüssel aus dem Passwort per Argon2id bzw. PBKDF2 mit Salt
- [ ] Import: Passwort, Integritäts- und Schemaprüfung, Größenlimit; bei Fehler bleiben die Daten unverändert
- [ ] Import ersetzt die Daten erst nach Rückfrage
- [ ] Tests: Roundtrip, falsches Passwort, manipulierte Datei, abgeschnittene Datei
