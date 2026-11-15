# 115 – Abhängigkeiten: Dependabot schlägt neue Versionen vor

**Etappe:** 14 – Kleinigkeiten aus dem Gutachten  
**Typ:** Technik  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** –  
**GitHub-Issue:** #115  
**Blockiert durch:** –

## Was gebaut wird

Dependabot meldet neue Versionen der Bibliotheken und der GitHub Actions als Pull Requests. Bisher meldet nichts neue Versionen, und Lints Hinweise darauf sind abgeschaltet; Sicherheitsfixes von SQLCipher oder Room kommen nur von Hand (Gutachten, Befund T6).

## Akzeptanzkriterien

- [x] `.github/dependabot.yml` für Gradle (`gradle/libs.versions.toml`, auch die Build-Logik, die denselben Katalog liest) und für die GitHub Actions
- [x] Monatlich, je Ökosystem ein gebündelter Pull Request
- [ ] Sicherheitsupdates kommen sofort und einzeln: dafür meldet der Job `dependencies` im Build die Gradle-Abhängigkeiten an GitHub; 🙋 in den Einstellungen des Repos unter „Code security“ müssen „Dependency graph“, „Dependabot alerts“ und „Dependabot security updates“ an sein
- [x] Die Actions bleiben auf Commit-SHAs festgelegt; Dependabot pflegt SHA und Versionskommentar
- [ ] Der Build läuft für Dependabots Pull Requests durch, ohne die Signatur-Secrets: Das Signieren wird übersprungen, wie schon ohne Secrets
- [x] `targetSdk` und die Versionen, die die Spezifikation festlegt, schlägt Dependabot nicht vor, oder sie sind ausgenommen
- [x] `docs/agents/` beschreibt in einer kurzen Datei, wie ein Pull Request von Dependabot übernommen wird: Claude prüft CI und Changelog und behebt, was bricht; Emily merged
- [ ] Geprüft, dass Dependabot die Datei annimmt (der Tab „Dependabot“ in GitHub zeigt keine Fehler)

## Entscheidungen

- **Entschieden am 14.11.2026:** Claude prüft die Pull Requests von Dependabot und behebt, was bricht; Emily merged.
- Nicht einzeln bestätigt: monatlich und gebündelt, damit nicht jede Woche ein Pull Request kommt.
