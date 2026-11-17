# 158 – Heute: Vokabeln üben aus der oberen Leiste

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1 (Heute), §15 (Vokabeltrainer)  
**GitHub-Issue:** #158  
**Blockiert durch:** –

## Was gebaut wird

Aus Heute direkt üben, ohne den Umweg über Sonstiges › Sprachen: Hat heute eine Sprache noch Offenes in ihrem Tagespensum, steht in der oberen Leiste von Heute ein Icon, das die Abfrage öffnet.

## Entschieden am 17.11.2026

- **Platz:** in der oberen Leiste links vom „€“; Reihenfolge rechts oben: Üben-Icon · „€“ · Flamme.
- **Symbol:** das Sprach-Symbol von Material („Translate“, A/文), ohne Zahl daran.
- **Nur mit Offenem:** Das Icon gibt es nur, solange mindestens eine nicht archivierte Sprache heute im Tagespensum noch Offenes hat (fällig oder neu, jede Richtung einer Karte für sich, wie „Heute: 18 fällig · 5 neu“ in §15). Ist alles geschafft oder gibt es keine Karten, verschwindet es.
- **Eine Sprache:** Tippen öffnet direkt ihre Abfrage.
- **Mehrere Sprachen:** Tippen öffnet ein kleines Menü mit den Sprachen, die heute Offenes haben, das meiste Offene zuerst, bei gleich viel nach Name, je mit der Zahl: „Arabisch (18) · Italienisch (12)“. Die Wahl öffnet deren Abfrage.
- **Zurück nach Heute:** Die Abfrage öffnet im Tab Heute; der Zurück-Pfeil, die Zurück-Taste und „Zurück“ am Ende der Runde führen nach Heute. „Weiter mit Italienisch (12)“ am Ende der Runde (#147) bleibt und übt die nächste Sprache an Stelle dieser, zurück ebenfalls nach Heute. „Karte ändern“ (#146) geht wie sonst.
- **Nur heute:** In der Ansicht „‹ Gestern“ gibt es das Icon nicht.

## Akzeptanzkriterien

- [x] Icon „Translate“ in der oberen Leiste von Heute links vom „€“, nur solange heute in einer nicht archivierten Sprache noch etwas offen ist; es verschwindet, sobald das Tagespensum aller Sprachen geschafft ist, auch während Heute offen ist (etwa nach dem Üben oder um Mitternacht)
- [x] TalkBack sagt „Vokabeln üben“
- [x] Eine Sprache mit Offenem: Tippen öffnet ihre Abfrage
- [x] Mehrere: Tippen öffnet ein Menü mit diesen Sprachen, das meiste Offene zuerst, bei gleich viel nach Name, mit der Zahl in Klammern; die Wahl öffnet die Abfrage
- [x] Aus Heute geöffnet, führen Zurück-Pfeil, Zurück-Taste und „Zurück“ am Ende der Runde nach Heute; „Weiter mit …“ und „Karte ändern“ gehen wie sonst, zurück ebenfalls nach Heute
- [x] Nicht in der Ansicht „‹ Gestern“
- [x] SPEC.md §1 (Liste der Leiste in Heute) und §15 nennen das Icon

## Tests

- [x] Unit: welche Sprachen Offenes haben und in welcher Reihenfolge (Zahl, Name bei Gleichstand), archivierte und geschaffte nicht
- [x] UI: kein Icon ohne Offenes; eine Sprache öffnet direkt; mehrere zeigen das Menü in der Reihenfolge; zurück aus der Abfrage nach Heute; nicht in „Gestern“

## Entscheidungen

Im Gespräch am 17.11.2026 abgestimmt (Platz, Symbol, Sichtbarkeit, Menü bei mehreren Sprachen und seine Reihenfolge, keine Zahl am Icon, zurück nach Heute, nicht in „Gestern“).
