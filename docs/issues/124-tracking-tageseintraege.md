# 124 – Tracking: Morgen- und Abendeintrag, eigene Symptome & Nachtragen

**Etappe:** 15 – Tracking  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Tageseinträge)  
**GitHub-Issue:** #124  
**Blockiert durch:** [123 – eigener Bereich mit Passwort, Backup & „Akute Beschwerden?“](123-tracking-bereich.md)

## Was gebaut wird

Der tägliche Kern: ein Morgen- und ein Abendeintrag mit Symptomen auf der Skala 0–3, Belastung, Trinkmenge und Notiz, dazu die eigene Symptomliste und Nachtragen bis 7 Tage.

## Akzeptanzkriterien

- [x] Startseite: heutiger Morgen- und Abendeintrag mit Status offen/erledigt; Tippen öffnet ihn
- [x] **Morgens:** Ruhepuls im Liegen (30–220/min), Blutdruck optional (systolisch/diastolisch), Schlafdauer, Schlafqualität 0–3 (0 = sehr gut), erholt aufgewacht ja/nein, „abweichend gemessen“ (z. B. nach Koffein) ja/nein; Hinweis zur Messung
- [x] **Abends:** jedes aktive Symptom 0–3; Belastung körperlich, kognitiv, emotional 0–3; Arbeitstag und Hitze/langes Stehen ja/nein; Trinkmenge (l, eine Nachkommastelle), Mahlzeiten, Koffein, Alkohol (Anzahl); Notiz (Markdown)
- [x] Skala mit Worten (0 nicht vorhanden … 3 schwer), nicht nur Zahlen; **leer heißt „nicht erfasst“** und zählt nie als 0
- [x] **Symptome** im Menü: Startliste nach §17 (mit Muskel- und Gelenkschmerzen getrennt); umbenennen, sortieren, ausblenden, eigene ergänzen; ausgeblendete behalten ihre Werte und erscheinen in alten Einträgen
- [x] **Tag:** Bis 04:00 schlägt ein neuer Eintrag den Vortag vor; Datum im Eintrag änderbar; je Tag ein Morgen- und ein Abendeintrag
- [x] **Nachtragen** bis 7 Tage zurück über eine Datumsauswahl; ein später als am Folgetag erfasster Eintrag ist still als nachgetragen markiert (nur in Daten und Auswertung, nicht hervorgehoben)
- [x] Speichern jederzeit, auch unvollständig; vor dem Sperren gespeichert

## Tests

- [x] Unit: 04:00-Grenze inkl. Sommer-/Winterzeit; `null` ≠ 0; Nachtragen-Grenze 7 Tage; Markierung „nachgetragen“; Symptom ausblenden und umbenennen mit alten Werten
- [x] UI: Morgen- und Abendeintrag erfassen und ändern; Nachtragen; Symptomliste bearbeiten

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: Nachtragen bis 7 Tage, still markiert; bis 04:00 der Vortag als Vorschlag; Startliste fest, in der App anpassbar.

Beim Bauen festgelegt (15.11.2026), nicht einzeln bestätigt:

- `tracking.db` ist dafür auf Version 2 gegangen, mit Migration von 1 und Test (`TrackingMigrationTest`).
- Werte außerhalb ihres Bereichs (Ruhepuls 30–220/min, systolisch 50–300, diastolisch 30–200 mmHg, Schlaf 0–24 h, Trinkmenge 0–15 l, Anzahlen 0–50) zeigt das Feld an; „Fertig“ geht erst, wenn sie berichtigt sind. Wer die Seite anders verlässt oder die Sperre greift, speichert alles außer ihnen.
- Ein Eintrag wird beim Verlassen der Seite gespeichert, bei „Fertig“, Zurück, Drehen und vor dem Sperren; unverändert geöffnet und verlassen entsteht keiner.
- „Abweichend gemessen“ ist ein Haken, kein Ja/Nein, weil es die Ausnahme ist. Erholt aufgewacht, Arbeitstag und Hitze/langes Stehen sind ja/nein/nicht erfasst.
- Das Datum lässt sich nur auf einen Tag ändern, der noch keinen Eintrag derselben Art hat.
- Die Startliste der Symptome kommt beim ersten Öffnen in die Datenbank; Symptome werden ausgeblendet, nicht gelöscht.
- „Nachgetragen“ und die 7 Tage zählen nach demselben Tag wie der Vorschlag (bis 04:00 der Vortag): Ein Abendeintrag kurz nach Mitternacht gilt nicht als nachgetragen.
