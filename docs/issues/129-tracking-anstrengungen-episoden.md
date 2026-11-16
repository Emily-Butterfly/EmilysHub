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

- [x] **Anstrengung:** Datum, Uhrzeit, Art (körperlich, kognitiv, emotional, Stehen/Wärme), Dauer, Stärke 0–3; „+ Anstrengung“ auf der Startseite
- [x] Belastung ≥ 2 im Abendeintrag (#124) gilt **direkt** als Anstrengung des Tages mit dieser Art und Stärke; ändert sich die Angabe, ändert sich die Anstrengung mit
- [x] **Vorschlag aus Schritten:** Tag > 150 % des Mittels der letzten 14 Tage (nur Tage mit Daten; erst ab 7 Tagen)
- [x] **Pacing-Schwelle** im Menü: eigener Wert in Schlägen/min; ohne ihn Mittel der Morgen-Ruhepulse der letzten 7 Tage + 15, gekennzeichnet „Faustregel, schwach belegt“; mit einem Medikament „senkt den Puls“ (#125) und ohne eigenen Wert der Hinweis nach §17
- [x] **Vorschlag aus Herzfrequenz:** Minuten über der Schwelle > 150 % des 14-Tage-Mittels (Herzfrequenz der Stufen A und B)
- [x] Vorschläge im Abendeintrag und auf der Startseite: bestätigen (wird Anstrengung, Art körperlich, Stärke wählbar) oder verwerfen (kommt nicht wieder); Vorschläge lösen nie einen Hinweis aus
- [x] **Folgetage:** nach Anstrengung der Stärke 2–3 fragen die Abendeinträge der drei folgenden Tage „Schlechter als vor der Anstrengung?“ (ja/nein/unklar)
- [x] **Episode** über „+ Episode“ oder „Crash oder PEM?“ im Abendeintrag: Art PEM/Crash, Beginn (Datum, ungefähre Uhrzeit), Ende oder „läuft noch“, Schwere 1–3, vermutete Auslöser aus den Anstrengungen der 3 Tage davor oder direkt neu angelegt, je Auslöser Sicherheit sicher/wahrscheinlich/unklar; Anzeige „Beginn x Stunden nach der Anstrengung“
- [x] Laufende Episode auf der Startseite mit „beenden“

## Tests

- [x] Unit: Abendbelastung → Anstrengung und zurück; Mittel und 150 %-Grenze mit Lücken; Schwelle mit und ohne eigenen Wert; verworfene Vorschläge; Folgetage nur bei 2–3; Abstand Beginn–Auslöser über Tagesgrenzen
- [x] UI: Episode aus dem Abendeintrag anlegen, Auslöser wählen, beenden

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Episode statt Ja/Nein; Abendbelastung zählt direkt; Schritte und Herzfrequenz schlagen nur vor; Folgetage nur nach Stärke 2–3; Pacing-Schwelle eigener Wert, sonst Ruhepuls + 15. 150 % und 14 Tage sind eigene Arbeitswerte; „erst ab 7 Tagen“ kam im Interview nicht vor.

Beim Bauen festgelegt (15.11.2026), nicht einzeln bestätigt:

- `tracking.db` auf Version 6 mit Migration und Test: `exertion` (Herkunft von Hand, Abend, Schritte, Herzfrequenz), `suggestion_decision` je Tag und Quelle, `follow_up` am Abendeintrag, `episode` und `episode_trigger`.
- Abendbelastung: Speichern des Abendeintrags gleicht seine Anstrengungen ab; eine geänderte Stärke ändert dieselbe Anstrengung, damit sie Auslöser einer Episode bleibt; sinkt die Belastung unter 2, fällt sie weg; zieht der Eintrag auf einen anderen Tag, zieht sie mit. Von Hand lässt sie sich weder ändern noch löschen.
- Vorschläge für die Tage, für die sich ein Eintrag machen lässt (heute und 7 zurück); Schritte je Tag nach dem Beginn jedes Zeitraums; Minuten über der Schwelle als Minuten mit mindestens einer Herzfrequenz darüber, Tage mit Herzfrequenz zählen auch mit 0 Minuten. Die Schwelle ist die an jenem Tag geltende (Nachtrag nach dem Review vom 16.11.2026; vorher die heute geltende, was frühere Tage bei jeder Änderung neu rechnete). Ein bestätigter Vorschlag wird eine körperliche Anstrengung ohne Uhrzeit.
- Pacing-Schwelle: eigener Wert im Bereich des Pulses (30–220); ohne ihn das gerundete Mittel der Morgen-Ruhepulse von heute und den 6 Tagen davor + 15. Je Tag gilt die Schwelle dieses Tages: der eigene Wert ab dem Tag, an dem er gesetzt wurde (Tabelle `pacing_threshold`, tracking.db v9; ein vorher gesetzter gilt von Anfang an), sonst die Faustregel aus den Morgen bis zu diesem Tag. Der Hinweis zu pulssenkenden Medikamenten gilt für die heute genommenen.
- Folgetage: die Abendeinträge der drei Tage nach einer Anstrengung der Stärke 2–3 fragen ja/nein/unklar; ohne Antwort bleibt die Frage offen.
- Episode: „+ Episode“ oder „Crash oder PEM?“ fragt PEM oder Crash und legt sie am Tag des Eintrags an; Uhrzeit, Ende und Schwere auf ihrer Seite. Auslöser aus den Anstrengungen der 3 Tage vor dem Beginn und des Beginntags bis zur Uhrzeit des Beginns (ohne Uhrzeit gilt 12:00; ohne Uhrzeit des Beginns der ganze Tag); „Neue Anstrengung als Auslöser“ legt eine an, die gleich Auslöser („unklar“) ist. Fehlt eine Uhrzeit, gilt 12:00 und der Abstand heißt „etwa“. „beenden“ setzt das Ende auf heute.
- Listen „Anstrengungen“ und „Episoden“ zum Öffnen und Löschen (mit Rückfrage).

