# 65 – Repo privat schalten

**Etappe:** 11 – Dokumentation: Assessmentbögen  
**Typ:** Einrichtung  
**Art:** 🙋 HITL – braucht dich  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Assessmentbögen)  
**GitHub-Issue:** #65  
**Blockiert durch:** –

## Worum es geht

Die Bögen kommen mit ihrem vollen Wortlaut in den Quellcode. Der Wortlaut gehört DIGOTOR („Alle Rechte vorbehalten“) und teils den Autoren der Instrumente. Damit er nicht veröffentlicht wird, wird das Repo privat, bevor der erste Bogen gepusht wird.

## Akzeptanzkriterien

### Schritte

- [x] Auf GitHub: Emily-Butterfly/EmilysHub → Settings → General → Danger Zone → **Change visibility** → **Make private**
- [x] Prüfen, dass der Build in GitHub Actions weiterläuft und die signierte APK als Artefakt entsteht (private Repos haben ein begrenztes Kontingent an Actions-Minuten)
- [x] Kurz im Issue bestätigen; danach dürfen #66 bis #76 Wortlaut pushen

## Entscheidungen

Abgestimmt im Interview vom 08.11.2026.

- Privat statt eines zweiten, privaten Repos nur für die Bögen: einfacher, Build und Tests laufen mit den echten Bögen.
- Für den rein privaten Gebrauch der App ist das Übernehmen der Inhalte unproblematisch; ein öffentliches Repo wäre eine Veröffentlichung.
