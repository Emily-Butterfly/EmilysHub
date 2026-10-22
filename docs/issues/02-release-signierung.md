# 02 – Signierte Release-APK

**Etappe:** 1 – Grundgerüst & Sicherheit  
**Typ:** Task  
**Art:** 🙋 HITL – braucht einen Schritt von Emily  
**Spezifikation:** [SPEC.md](../../SPEC.md) §12  
**GitHub-Issue:** #2  
**Blockiert durch:** [01 – Projektgerüst](01-projektgeruest.md)

## Was gebaut wird

GitHub Actions baut eine signierte Release-APK, die sich auf dem A52 installieren und später per Update ersetzen lässt. Der Signaturschlüssel liegt ausschließlich als GitHub-Secret vor.

## Akzeptanzkriterien

- [ ] Schritt-für-Schritt-Anleitung, wie Emily den Schlüssel lokal mit `keytool` erzeugt
- [ ] Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
- [ ] Workflow baut die signierte Release-APK und stellt sie als Artefakt bereit
- [ ] Fehlen die Secrets, überspringt der Workflow die Signierung sauber, statt fehlzuschlagen
- [ ] `.gitignore` schließt `*.jks` / `*.keystore` aus; kein Schlüsselmaterial im Repo
- [ ] APK installiert sich auf dem A52; ein späterer Build mit gleichem Schlüssel installiert sich als Update

## Hinweise

- **Mensch nötig:** Emily erzeugt den Schlüssel und trägt die Secrets im Repo ein.
- Den Schlüssel zusätzlich offline sichern – geht er verloren, sind keine Updates mehr möglich (nur Neuinstallation mit Datenverlust).
