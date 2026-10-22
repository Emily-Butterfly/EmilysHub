# 04 – App-Sperre & Design-Umschalter

**Etappe:** 1 – Grundgerüst & Sicherheit  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1, §9  
**GitHub-Issue:** #4  
**Blockiert durch:** [03 – Verschlüsselte Datenbank](03-verschluesselte-datenbank.md)

## Was gebaut wird

Die App ist per Fingerabdruck (Geräte-PIN als Ersatz) gesperrt und sperrt sich nach 1 Minute im Hintergrund erneut. In den Einstellungen lässt sich das Design (System / Hell / Dunkel) wählen.

## Akzeptanzkriterien

- [ ] `BiometricPrompt` mit `BIOMETRIC_STRONG` oder Gerätesperre (`DEVICE_CREDENTIAL`)
- [ ] Sperre beim Kaltstart und nach 1 Minute im Hintergrund
- [ ] Vor dem Entsperren ist kein Inhalt sichtbar – auch nicht kurz beim Zurückkehren
- [ ] Einstellung „App-Sperre“ (Standard: an); ohne eingerichtete Gerätesperre Hinweis statt Aktivierung
- [ ] Einstellung „Design“: System / Hell / Dunkel, gespeichert in der verschlüsselten Datenbank
- [ ] Unit-Test für die Timeout-Logik
