# 129 – Tracking: Anstrengungen, Vorschläge, PEM- & Crash-Episoden

**Etappe:** 15 – Tracking  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Anstrengungen, PEM & Crash)  
**GitHub-Issue:** #129  
**Blockiert durch:** [125 – Grunddaten & Medikamente mit Zeitleiste](125-tracking-medikamente.md), [126 – Garmin-Werte über Health Connect & Stufen](126-tracking-garmin-health-connect.md)

## Was gebaut wird

Anstrengungen entstehen von Hand, aus der Abendbelastung oder als Vorschlag aus Schritten und Herzfrequenz. PEM und Crash werden als Episode mit vermuteten Auslösern erfasst; nach starken Anstrengungen fragen die Folgetage nach.

## Akzeptanzkriterien

- [ ] **Anstrengung:** Datum, Uhrzeit, Art (körperlich, kognitiv, emotional, Stehen/Wärme), Dauer, Stärke 0–3; „+ Anstrengung“ auf der Startseite
- [ ] Belastung ≥ 2 im Abendeintrag (#124) gilt **direkt** als Anstrengung des Tages mit dieser Art und Stärke; ändert sich die Angabe, ändert sich die Anstrengung mit
- [ ] **Vorschlag aus Schritten:** Tag > 150 % des Mittels der letzten 14 Tage (nur Tage mit Daten; erst ab 7 Tagen)
- [ ] **Pacing-Schwelle** im Menü: eigener Wert in Schlägen/min; ohne ihn Mittel der Morgen-Ruhepulse der letzten 7 Tage + 15, gekennzeichnet „Faustregel, schwach belegt“; mit einem Medikament „senkt den Puls“ (#125) und ohne eigenen Wert der Hinweis nach §17
- [ ] **Vorschlag aus Herzfrequenz:** Minuten über der Schwelle > 150 % des 14-Tage-Mittels (Herzfrequenz der Stufen A und B)
- [ ] Vorschläge im Abendeintrag und auf der Startseite: bestätigen (wird Anstrengung, Art körperlich, Stärke wählbar) oder verwerfen (kommt nicht wieder); Vorschläge lösen nie einen Hinweis aus
- [ ] **Folgetage:** nach Anstrengung der Stärke 2–3 fragen die Abendeinträge der drei folgenden Tage „Schlechter als vor der Anstrengung?“ (ja/nein/unklar)
- [ ] **Episode** über „+ Episode“ oder „Crash oder PEM?“ im Abendeintrag: Art PEM/Crash, Beginn (Datum, ungefähre Uhrzeit), Ende oder „läuft noch“, Schwere 1–3, vermutete Auslöser aus den Anstrengungen der 3 Tage davor oder direkt neu angelegt, je Auslöser Sicherheit sicher/wahrscheinlich/unklar; Anzeige „Beginn x Stunden nach der Anstrengung“
- [ ] Laufende Episode auf der Startseite mit „beenden“

## Tests

- [ ] Unit: Abendbelastung → Anstrengung und zurück; Mittel und 150 %-Grenze mit Lücken; Schwelle mit und ohne eigenen Wert; verworfene Vorschläge; Folgetage nur bei 2–3; Abstand Beginn–Auslöser über Tagesgrenzen
- [ ] UI: Episode aus dem Abendeintrag anlegen, Auslöser wählen, beenden

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Episode statt Ja/Nein; Abendbelastung zählt direkt; Schritte und Herzfrequenz schlagen nur vor; Folgetage nur nach Stärke 2–3; Pacing-Schwelle eigener Wert, sonst Ruhepuls + 15. 150 % und 14 Tage sind eigene Arbeitswerte; „erst ab 7 Tagen“ kam im Interview nicht vor.
