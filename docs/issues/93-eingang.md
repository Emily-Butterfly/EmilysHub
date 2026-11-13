# 93 – Eingang: Gedanken festhalten & einsortieren

**Etappe:** 13 – Absichern, Eingang, Heute, Ablauf-Modus & Vokabeltrainer  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §16 (Eingang), §1 (Heute-Übersicht), §8  
**GitHub-Issue:** #93  
**Blockiert durch:** –

## Was gebaut wird

Ein Gedanke landet mit einer Zeile und Enter in der App, bevor er weg ist. Sortiert wird später: Aus jedem Eintrag des Eingangs wird mit einem Tipp ein To-do, eine Notiz oder eine Routine.

## Akzeptanzkriterien

### Festhalten

- [x] Oben in Heute, unter der Leiste, steht eine einzeilige Eingabe „Was geht dir durch den Kopf?“; „Fertig“ auf der Tastatur legt den Text im Eingang ab und leert die Zeile, die Tastatur bleibt für den nächsten Gedanken offen
- [x] Kein Formular und kein Speichern-Knopf; ein leerer Text legt nichts ab; bis 1 000 Zeichen, mit Zähler kurz vor der Grenze
- [x] Ein kurzer Hinweis „Im Eingang“ bestätigt das Ablegen
- [x] Die Zeile gibt es nur in Heute, nicht am Vortag (Gestern nachtragen)

### Eingang

- [x] Liegt etwas darin, steht unter der Zeile „Eingang (3)“; Antippen öffnet die Liste über den Tabs, das Neueste oben, je Eintrag der Text und wann er festgehalten wurde
- [x] Ist der Eingang leer, fehlt die Zeile „Eingang“ in Heute, und die Liste sagt „Der Eingang ist leer“
- [x] Der Text eines Eintrags lässt sich in der Liste ändern, auch mehrzeilig

### Einsortieren

- [x] Jeder Eintrag bietet **To-do · Notiz · Routine · Löschen**
- [x] To-do, Notiz und Routine öffnen den jeweiligen Editor für einen neuen Eintrag: die erste Zeile des Textes als Titel bzw. Name, auf dessen Höchstlänge gekürzt, der Rest als Beschreibung bzw. Text; was nicht in den Titel passt, steht im Rest
- [x] Der Eintrag verschwindet aus dem Eingang erst, wenn im Editor gespeichert wurde; wird der Editor ohne Speichern verlassen, bleibt er
- [x] „Löschen“ entfernt sofort; eine Leiste bietet „Rückgängig“

### Daten

- [x] Eine Tabelle für den Eingang (Text, Zeitpunkt): Schema-Version eins hoch, Migration mit Test, im Backup (`docs/agents/schema-change.md`); ein Modul `Inbox` nach dem Muster von `Tags`, im `Hub`
- [x] Die Suche über alle Bereiche findet Einträge des Eingangs in einem eigenen Bereich „Eingang“; ein Treffer öffnet den Eingang
- [x] Der Eingang erinnert an nichts und hat weder Tags noch Verknüpfungen
- [x] Unit-Tests: ablegen, Grenzen, Reihenfolge, Titel und Rest beim Einsortieren, Eintrag bleibt ohne Speichern, Löschen und Rückgängig, Suche, Backup hin und zurück; UI-Tests: tippen und „Fertig“, Zähler in Heute, Einsortieren als To-do

## Entscheidungen

Aus dem Gutachten vom 12.11.2026 (Befund U1, Feature 1); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Die Zeile steht oben in Heute, weil Heute der Startbildschirm ist: Öffnen, tippen, „Fertig“.
- Festhalten und Einsortieren sind getrennt: Beim Festhalten wird nichts entschieden.
- Der Eintrag bleibt, bis das Neue gespeichert ist, damit beim Einsortieren nichts verloren geht.

Bei der Umsetzung festgelegt (13.11.2026):

- Der Text wird beim Ändern in einem Dialog bearbeitet, der sich mit einem Tipp auf den Eintrag öffnet.
- Gekürzt wird der Titel beim Einsortieren an einem Leerzeichen, wenn eines davor liegt; der abgeschnittene Teil beginnt den Rest.
- Die Suche zeigt einen Eintrag mit seiner ersten Zeile, das Neueste oben, als letzten Bereich.
- Zum Einsortieren gibt es eigene Editor-Ziele (`FromInbox`); der Editor einer Notiz legt sie wie sonst unter „Allgemein“ an.
