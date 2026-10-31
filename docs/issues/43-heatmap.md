# 43 – Heatmap in Heute

**Etappe:** 8 – Erweiterungen (v1.1)  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §1  
**GitHub-Issue:** #43  
**Blockiert durch:** –

## Was gebaut wird

Unter den Einträgen von Heute zeigt eine Heatmap die letzten 6 Monate: je Tag ein Kästchen, je mehr Routinen und To-dos erledigt, desto kräftiger das Lila.

## Akzeptanzkriterien

- [ ] Unter den Einträgen von Heute (und unter „Demnächst“, #35) eine Heatmap der letzten 6 Monate: 26 Wochen als Spalten, Montag bis Sonntag als Zeilen, die laufende Woche rechts
- [ ] Je Tag ein Kästchen; die Farbe zeigt, wie viel an dem Tag erledigt wurde: erledigte Fälligkeiten von Routinen (nach ihrem Tag) und abgehakte To-dos (nach dem Tag des Abhakens)
- [ ] Leer plus vier Lila-Stufen, gemessen am vollsten Tag im Zeitraum; in hellem und dunklem Design gut zu unterscheiden; Tage in der Zukunft bleiben leer
- [ ] Monatsnamen über den Spalten, einige Wochentage am Rand
- [ ] Tippen auf ein Kästchen zeigt Datum und Anzahl, z. B. „Mi, 14.10.: 5 erledigt“
- [ ] Passt ohne seitliches Scrollen auf das Galaxy A52
- [ ] Zählt sofort mit, wenn heute etwas abgehakt oder rückgängig gemacht wird
- [ ] Übersprungene, ausgelassene und verpasste zählen nicht; archivierte Routinen und To-dos im Archiv zählen mit, endgültig gelöschte nicht mehr
- [ ] Steht auch da, wenn heute nichts ansteht
- [ ] TalkBack liest Tage mit Datum und Anzahl
- [ ] Unit-Tests: Zählung je Tag (Mitternacht, Zeitzone), Stufen

## Entscheidungen

- Anzahl erledigter Einträge, keine Quote.
- 6 Monate, unter den Einträgen.
