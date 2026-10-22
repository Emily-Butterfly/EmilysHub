# 13 – WLAN-Orte verwalten

**Etappe:** 3 – Orte  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §4  
**GitHub-Issue:** #13  
**Blockiert durch:** [03 – Verschlüsselte Datenbank](03-verschluesselte-datenbank.md)

## Was gebaut wird

Benannte Orte, die über ein oder mehrere WLANs erkannt werden.

## Akzeptanzkriterien

- [ ] Einstellungen → Orte: Liste, anlegen, umbenennen, löschen
- [ ] WLAN-Ort: „Aktuelles WLAN übernehmen“ oder SSID eintippen; mehrere SSIDs pro Ort
- [ ] Standort-Berechtigung wird erst hier abgefragt, mit Erklärung, warum sie für den WLAN-Namen nötig ist
- [ ] Anzeige „Aktuell erkannter Ort“ zum Ausprobieren
- [ ] Ortserkennung ohne Google Play Services
