# 61 – Dokumentation: Tests zentral anlegen

**Etappe:** 10 – Sonstiges, Heute & Dokumentation  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Tests)  
**GitHub-Issue:** #61  
**Blockiert durch:** [58 – Dokumentation: Sperre](58-doku-sperre.md)

## Was gebaut wird

Im Menü der Doku legst du Tests an, zum Beispiel „Handkraft“ mit einem Wert in kg, „Bewegungsumfang Schulter“ mit mehreren Werten in ° oder „Barthel-Index“ mit Punkt-Items und Summe. Bei den Patienten erfasst du später ihre Werte (#62).

## Akzeptanzkriterien

### Test

- [ ] Menüpunkt **Tests**: die Tests nach Name, darunter „Archiviert“; „+ Test“
- [ ] Ein Test hat einen **Namen** (Pflicht, unter den Tests eindeutig, ohne Rücksicht auf Groß- und Kleinschreibung) und **einen oder mehrere Werte**
- [ ] Je Wert: **Name** (Pflicht, im Test eindeutig), **Einheit** (optional, z. B. „kg“, „°“, „Punkte“), **Bereich** von/bis (optional, eins oder beide; von ≤ bis), **Nachkommastellen** (aus: ganze Zahlen; an: bis zu 2), **Richtung** („höher ist besser“ oder „niedriger ist besser“, Standard höher)
- [ ] Die Reihenfolge der Werte lässt sich ändern
- [ ] **Summe** optional, ab zwei Werten: Schalter „Summe bilden“ mit eigener Richtung (Standard höher ist besser)
- [ ] Skizze:
  ```
  Barthel-Index
  Werte            Einheit   Bereich   Richtung
  Essen            Punkte    0–10      ↑ besser
  Baden            Punkte    0–5       ↑ besser
  …
  [✓] Summe bilden                     ↑ besser
  ```

### Ändern, wenn schon gemessen

- [ ] Name, Einheit, Bereich, Nachkommastellen, Richtung und Summe lassen sich jederzeit ändern; betrifft das Messungen, sagt ein Hinweis beim Speichern „Betrifft 14 Messungen bei 3 Patienten“
- [ ] Alte Messungen behalten ihre Zahlen, auch wenn sie nach einer Änderung außerhalb des Bereichs liegen
- [ ] Einen Wert **hinzufügen** geht jederzeit; alte Messungen haben dafür keinen Wert
- [ ] Einen Wert **entfernen**: nie gemessen, wird er gelöscht; sonst **stillgelegt**: bei neuen Messungen nicht mehr abgefragt, alte Werte bleiben, im Test grau mit „Wieder aktivieren“
- [ ] Ein Test braucht mindestens einen nicht stillgelegten Wert

### Test entfernen

- [ ] **Archivieren**: für neue Messungen nicht mehr wählbar, die Verläufe bei den Patienten bleiben; „Wiederherstellen“ im Abschnitt „Archiviert“
- [ ] **Endgültig löschen** mit Rückfrage „Löscht 14 Messungen bei 3 Patienten“; ohne Messungen ohne diesen Satz

### Tests

- [ ] Unit-Tests: Prüfung der Felder und Eindeutigkeit; Summe erst ab zwei Werten; Wert entfernen gelöscht oder stillgelegt; mindestens ein aktiver Wert; Archivieren und Löschen mit Messungen; Anzahl betroffener Messungen
- [ ] UI-Tests: Test mit mehreren Werten und Summe anlegen, Reihenfolge ändern, Wert stilllegen, archivieren

## Entscheidungen

Abgestimmt im Interview vom 07.11.2026.

- Nur Zahlenwerte, damit jeder Wert einen Verlauf hat; Freitext steht in der Bemerkung einer Messung (#62).
- Änderungen gelten auch für alte Messungen, mit Hinweis, statt Versionen eines Tests zu führen.
- Gemessene Werte werden stillgelegt statt gelöscht, damit kein Verlauf unbemerkt verschwindet.
- **Zurückgestellt:** interaktive Assessmentbögen. Sie werden besprochen, wenn die übrigen Slices dieser Etappe erledigt sind.
