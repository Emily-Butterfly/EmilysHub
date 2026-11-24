# 163 – Mein Warum: Schriften und Farben

**Etappe:** 18 – Mein Warum  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §18 (Mein Warum: Gründe)  
**GitHub-Issue:** #163  
**Blockiert durch:** #162

## Was gebaut wird

Jeder Grund bekommt seine eigene Schrift, Größe und Farben.

## Akzeptanzkriterien

- [ ] Acht in der App mitgebrachte Schriften (SIL Open Font License, Lizenzen in der App einsehbar): Standard, Serifenschrift, runde, zwei Handschriften (locker und schönschriftlich), kräftige Plakatschrift, schmale elegante, Schreibmaschinenschrift; je Grund eine, in klein, mittel oder groß
- [ ] Zeichen, die eine Schrift nicht hat (etwa arabische), zeigt eine passende Schrift des Handys
- [ ] Hintergrund und Schrift aus derselben Palette von 35 Farben: sieben Farbtöne (Rot, Orange, Gelb, Grün, Türkis, Blau, Lila) in je vier Stufen und sieben Neutrale (Weiß, Creme, Sand, drei Grautöne, Schwarz), dazu „Standard“, das dem Design der App folgt
- [ ] Gewählte Farben bleiben in hellem und dunklem Design gleich
- [ ] Bei einem Foto gelten Schrift und Farben für den Streifen darunter
- [ ] Bei zu wenig Kontrast sagt der Editor „Schwer zu lesen“, erlaubt es aber
- [ ] Der Editor zeigt den Grund beim Wählen, wie er auf dem Board aussieht
- [ ] Schema-Version eins hoch, Migration mit Test, im Backup
- [ ] SPEC.md §18 nennt es

## Tests

- [ ] Unit: Kontrast an der Grenze; „Standard“ in hell und dunkel; eine unbekannte gespeicherte Schrift oder Farbe gilt als Standard
- [ ] UI: Schrift, Größe und beide Farben wählen; „Schwer zu lesen“

## Entscheidungen

Am 24.11.2026 mit der Nutzerin abgestimmt (SPEC.md §18, Entscheidungen). Offen beim Bau: welche frei lizenzierten Schriften genau; Kontrastgrenze wie WCAG für großen Text (3:1).
