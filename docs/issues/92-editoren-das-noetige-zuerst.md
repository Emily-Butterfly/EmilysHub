# 92 – Routine & To-do: das Nötige zuerst, der Rest unter „Mehr Optionen“

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §8 (Editoren), §2, §5  
**GitHub-Issue:** #92  
**Blockiert durch:** #91

## Was gebaut wird

Die beiden langen Editoren beginnen mit dem, was jeder Eintrag braucht. Alles Weitere liegt zugeklappt darunter. Bisher hat eine neue Routine 16 Abschnitte über mehr als vier Bildschirmlängen, und die Uhrzeit steht an achter Stelle.

## Akzeptanzkriterien

- [ ] **Routine-Editor**, oben und immer sichtbar, in dieser Reihenfolge: Name, Uhrzeiten, Wiederholung (mit „Endet“, außer bei „Einmalig“), Checkliste
- [ ] Darunter **„Mehr Optionen“**, zugeklappt: Beschreibung, Priorität, Tags, Orte, Ziel mit Routinen-Ziel, Gekoppelt mit, Erneut erinnern, Vollbild-Alarm, Name auf dem Sperrbildschirm, Ton, Zeitfenster
- [ ] **To-do-Editor**, oben: Titel, Fälligkeit (mit Datum und Uhrzeit), Beschreibung
- [ ] Darunter **„Mehr Optionen“**, zugeklappt: In Heute zeigen, Wiederholung mit Ende, Weckzeit, Orte, Vollbild-Alarm, Name auf dem Sperrbildschirm, Ton, Priorität, Checkliste, Tags, Ziel, verknüpfte Notizen
- [ ] Die Zeile „Mehr Optionen“ nennt zugeklappt, was vom Standard abweicht, z. B. „Hohe Priorität · 2 Tags · Vollbild-Alarm · Ort: Gym“; ist nichts gesetzt, steht dort eine Aufzählung dessen, was sich einstellen lässt
- [ ] Auch beim Bearbeiten öffnet der Editor zugeklappt; die Zusammenfassung zeigt, was gesetzt ist. Auf- und Zuklappen bleibt beim Drehen und über die App-Sperre erhalten
- [ ] Liegt beim Speichern ein Problem in einem zugeklappten Feld (z. B. Beschreibung zu lang, Ende vor heute), klappt der Abschnitt von selbst auf und zeigt das Feld
- [ ] An den Feldern selbst, ihren Regeln und den gespeicherten Daten ändert sich nichts
- [ ] Auf dem A52 bei normaler Schriftgröße sind bei einer neuen Routine Name und Uhrzeiten ohne Scrollen sichtbar, bei einem neuen To-do Titel und Fälligkeit, jeweils mit offener Tastatur
- [ ] UI-Tests: Reihenfolge der Felder in beiden Editoren; die Zusammenfassung nennt Gesetztes; ein Problem in einem zugeklappten Feld klappt auf; der Zustand übersteht Drehen

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befund U5, Vorschlag UX 1, Punkt 3); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Nur Routine und To-do: Die übrigen Editoren sind kurz genug.
- Die Checkliste steht bei der Routine oben, weil sie mit dem Ablauf-Modus (#101) ihr Kern wird; beim To-do liegt sie unter „Mehr Optionen“.
- Zugeklappt auch beim Bearbeiten, damit der Editor immer gleich aussieht; die Zusammenfassung verhindert, dass Gesetztes übersehen wird.
