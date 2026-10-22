# 01 – Projektgerüst

**Etappe:** 1 – Grundgerüst & Sicherheit  
**Typ:** Task  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1, §9, §12  
**GitHub-Issue:** #1  
**Blockiert durch:** –

## Was gebaut wird

Leeres, aber vollständig abgesichertes Android-Projekt, das GitHub bei jedem Push baut und testet. Alle späteren Slices bauen darauf auf.

## Akzeptanzkriterien

- [ ] Kotlin + Jetpack Compose, Gradle Kotlin DSL mit Version Catalog; minSdk 30, targetSdk 35
- [ ] App heißt „EmilysHub“ und hat ein eigenes adaptives Icon (Haus mit Häkchen)
- [ ] Farbschema: hell = Flieder, dunkel = Lila; folgt der Systemeinstellung
- [ ] Untere Navigation mit **Heute · Routinen · To-dos · Wunschliste · Notizen** (Platzhalter-Screens) und Zugang zu den Einstellungen
- [ ] Alle Oberflächentexte auf Deutsch in `strings.xml`
- [ ] Das gemergte Manifest enthält **keine** `INTERNET`-Berechtigung – ein CI-Check schlägt fehl, falls eine Abhängigkeit sie einschleppt
- [ ] `allowBackup=false`, `dataExtractionRules` schließen Cloud-Backup und Gerätetransfer aus
- [ ] `FLAG_SECURE` auf allen Fenstern (keine Screenshots, ausgeblendet in „Zuletzt verwendet“)
- [ ] Nur die Launcher-Activity ist exportiert
- [ ] Release-Build mit R8 (Minify + Shrink)
- [ ] GitHub Actions: bei jedem Push Lint, Unit-Tests und Debug-APK als Download-Artefakt
