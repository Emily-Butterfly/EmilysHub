# 03 – Verschlüsselte Datenbank

**Etappe:** 1 – Grundgerüst & Sicherheit  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §9  
**GitHub-Issue:** #3  
**Blockiert durch:** [01 – Projektgerüst](01-projektgeruest.md)

## Was gebaut wird

Room-Datenbank, die mit SQLCipher verschlüsselt ist. Der Datenbankschlüssel wird zufällig erzeugt und mit einem Schlüssel aus dem Android Keystore geschützt abgelegt.

## Akzeptanzkriterien

- [ ] Room + SQLCipher eingebunden
- [ ] Zufälliger 256-Bit-Datenbankschlüssel, verschlüsselt mit einem nicht exportierbaren AES-Schlüssel im Android Keystore
- [ ] Erste Tabelle für App-Einstellungen als Nachweis
- [ ] Test: Datenbankdatei lässt sich ohne bzw. mit falschem Schlüssel nicht öffnen
- [ ] Schema-Export im Repo; explizite Migrationen, **kein** `fallbackToDestructiveMigration`
- [ ] Instrumentierte Tests laufen in CI auf einem Emulator
