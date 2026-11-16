# 146 – Vokabeltrainer: Karte aus der Abfrage heraus ändern

**Etappe:** 17 – Vokabeltrainer: Bedienung & Erweiterungen  
**Typ:** Verbesserung  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §15 (Vokabeltrainer: Abfrage)  
**GitHub-Issue:** #146  
**Blockiert durch:** –

## Was gebaut wird

Einen Tippfehler auf einer Karte bemerkt man meist beim Üben. Bisher muss man sich die Karte merken, die Abfrage verlassen und sie im Reiter Karten suchen. Ein Stift in der Abfrage öffnet sie direkt.

## Akzeptanzkriterien

- [x] Oben in der Leiste der Abfrage ein Stift „Karte ändern“, der den Karten-Editor der gezeigten Karte öffnet
- [x] Zurück steht die Abfrage an derselben Stelle der Runde, die Karte wie vorher (aufgedeckt oder nicht), mit den geänderten Texten; ihre Planung bleibt
- [x] Im Editor gelöscht: die Runde geht mit der nächsten Karte weiter
- [x] Wird „In beide Richtungen abfragen“ ausgeschaltet, während die Rückrichtung gezeigt wird, geht die Runde mit der nächsten Karte weiter
- [x] Auch beim Thema üben

## Tests

- [x] UI: aus der Abfrage ändern, zurück an derselben Stelle mit neuem Text; löschen geht zur nächsten Karte

## Entscheidungen

Aus der UX-Durchsicht des Vokabeltrainers vom 16.11.2026 (Commit 32ce029); die Einzelheiten sind nicht in einem Interview abgestimmt.

- Nicht einzeln bestätigt: der bekannte Editor statt einer Bearbeitung in der Karte selbst.
