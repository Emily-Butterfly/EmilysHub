# 162 – Mein Warum: Gründe mit Foto

**Etappe:** 18 – Mein Warum  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §18 (Mein Warum: Gründe); §9 (Sicherheit), §10 (Backups)  
**GitHub-Issue:** #162  
**Blockiert durch:** #161

## Was gebaut wird

Gründe mit Foto: ein Foto allein oder mit Text als Streifen darunter.

## Akzeptanzkriterien

- [ ] Ein Grund hat optional ein Foto, aus der Galerie oder mit „Foto aufnehmen“ über die Kamera-App des Handys, ohne Kamera-Berechtigung wie bei den Sessions (§15)
- [ ] Fotos werden auf höchstens 2000 px an der langen Kante verkleinert und verschlüsselt gespeichert wie die Anhänge der Sessions; das unverschlüsselte Kamerafoto nur bis zum Einlesen
- [ ] Höchstens 100 Gründe mit Foto; der Editor sagt es, wenn die Grenze erreicht ist
- [ ] Mit Text steht der Text als Streifen unter dem Foto; ein Foto ohne Text ist erlaubt
- [ ] Groß geöffnet steht das Foto bildschirmfüllend mit Zoom
- [ ] Ein gelöschter Grund nimmt sein Foto mit; Foto entfernen oder ersetzen im Editor
- [ ] Die Fotos im Backup, auch im nächtlichen; Schema-Version eins hoch, Migration mit Test
- [ ] SPEC.md §9, §10 und §18 nennen es

## Tests

- [ ] Unit: verkleinern, verschlüsselt speichern, mit dem Grund löschen, die Grenze von 100
- [ ] UI: Grund mit Foto aus der Galerie, mit und ohne Text; groß geöffnet; Backup und Wiederherstellen mit Foto

## Entscheidungen

Am 24.11.2026 mit der Nutzerin abgestimmt (SPEC.md §18, Entscheidungen).
