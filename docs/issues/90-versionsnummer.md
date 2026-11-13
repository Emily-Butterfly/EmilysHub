# 90 – Versionsnummer aus dem Build, sichtbar in den Einstellungen

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §12  
**GitHub-Issue:** #90  
**Blockiert durch:** –

## Was gebaut wird

Jeder Build bekommt eine eigene, steigende Versionsnummer, und die App zeigt, welcher Build installiert ist. Bisher steht jeder Build auf `versionCode 1`: Builds sind nicht zu unterscheiden, und ein älterer lässt sich über einen neueren installieren, dessen Datenbank er dann nicht öffnen kann.

## Akzeptanzkriterien

- [x] In der CI kommt `versionCode` aus der laufenden Nummer des Workflows (`GITHUB_RUN_NUMBER`), `versionName` ist `0.1.<Nummer>`; ohne diese Umgebung, also beim lokalen Build, bleibt es bei `1` und `0.1.0-lokal`
- [x] Der kurze Commit (7 Zeichen) steht im Build und ist in der App lesbar; lokal steht dort „lokal“
- [x] Die Einstellungen zeigen ganz unten die Zeile „Version 0.1.412 · 2dc38f5“, nur zum Lesen
- [x] Debug- und Release-APK aus demselben Lauf tragen dieselbe Nummer, auch die im Schritt „Release-APK signieren“ neu gebaute
- [x] Die erste so gebaute APK lässt sich über die installierte App (`versionCode 1`) installieren; danach lehnt Android einen älteren Build über einem neueren ab
- [x] Gradles Configuration Cache bleibt nutzbar (die Umgebung über `providers.environmentVariable`, wie bei der Signatur)
- [x] `docs/release-signierung.md` erklärt in wenigen Sätzen, woher die Nummer kommt und was beim Umbenennen des Workflows zu tun ist
- [x] UI-Test: Die Zeile steht in den Einstellungen; ein Test für die Ableitung von `versionCode` und `versionName` aus der Umgebung

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befund T3); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Die laufende Nummer des Workflows statt eines Zeitstempels: kurz, lesbar und in den Actions wiederzufinden.
- Die Nummer zählt je Workflow. Wird `build.yml` umbenannt, beginnt sie neu; dann braucht es einen festen Versatz, damit sie weiter steigt.

Bei der Umsetzung festgelegt (13.11.2026):

- Die Ableitung steht als `AppVersion` in `build-logic` und ist dort getestet; der Commit kommt aus `GITHUB_SHA` und steht als `BuildConfig.COMMIT` in der App.
- Eine Nummer, die keine positive Zahl ist, gilt wie ein lokaler Build.
- Der UI-Test liest die erwartete Zeile aus `BuildConfig`, weil dieselben Tests in der CI mit der echten Nummer laufen.
