# 28 – Automatisches tägliches Backup

**Etappe:** 7 – Statistik, Suche & Backups  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §10  
**GitHub-Issue:** #28  
**Blockiert durch:** [27 – Verschlüsseltes Backup: Export & Import](27-backup-manuell.md)

## Was gebaut wird

Die App legt jede Nacht ein verschlüsseltes Backup in einem gewählten Ordner ab.

## Akzeptanzkriterien

- [ ] Zielordner einmal wählen; Zugriff bleibt dauerhaft erhalten
- [ ] Tägliches Backup nachts
- [ ] Der aus dem Backup-Passwort abgeleitete Schlüssel ist im Android Keystore geschützt abgelegt
- [ ] Rotation: die letzten 7 Backups bleiben, ältere werden gelöscht
- [ ] Hinweis in Heute, wenn das letzte Backup älter als 30 Tage ist oder fehlgeschlagen ist
- [ ] Hinweis, die Backups regelmäßig vom Handy wegzukopieren
