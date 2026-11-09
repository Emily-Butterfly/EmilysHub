# 73 – Bögen: Kiefer / CMD

**Etappe:** 11 – Dokumentation: Assessmentbögen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Assessmentbögen)  
**GitHub-Issue:** #73  
**Blockiert durch:** [66 – Bögen: Grundgerüst, Anamnesebogen & PHQ-9](66-boegen-grundgeruest.md)

## Was gebaut wird

Die Bögen der Unterkategorie „Kiefer / CMD“ von DIGOTOR kommen in die App, mit Auswertung wie im Original.

## Akzeptanzkriterien

### Bögen

- [x] CMD – DC/TMD-Untersuchungsbogen
- [x] CMD – DC/TMD Symptom-Fragebogen
- [x] CMD – Schmerzbild
- [x] Dizziness Handicap Inventory (DHI)
- [x] Oral Behaviors Checklist (OBC)
- [x] Generalized Anxiety Disorder Scale (GAD-7)
- [x] Schon übertragen und hier nur einsortiert (durch #66, #71, #72): PHQ-9, PHQ-15, PHQ-4, FABQ, GCPS, Neurophysiology of Pain Questionnaire

### Besonderheiten

- [x] **GAD-7**: Der Link bei DIGOTOR zeigt auf den GAQ-PP (Stand 11/2026), nicht auf den GAD-7. Erst nach dem richtigen PDF suchen; gibt es keins, nachfragen
- [x] **DC/TMD Symptom-Fragebogen**: Seine Datei heißt wie der Untersuchungsbogen mit „-1“ – prüfen, dass es wirklich der Symptom-Fragebogen ist
- [x] DC/TMD-Untersuchung und Schmerzbild brauchen Regionen im Kiefer- und Kopfbereich (z. B. Kiefergelenk, M. masseter, M. temporalis je links/rechts) – als eigene Liste für den Baustein Körperregionen

### Für jeden Bogen

- [x] Je Bogen alle Items und Antworten **im Wortlaut** des PDFs aus der Kategorie „Scores“ (https://digotor.info/downloads/), mit Punkten, Auswertung, Regeln für fehlende Antworten, Einstufung und Richtung je Ergebnis; Quelle mit Stand aus dem Dateinamen
- [x] Jeder Bogen kommt in **alle** Unterkategorien, in denen er bei DIGOTOR steht (der Slice, der ihn überträgt, sortiert ihn überall ein), mit seiner Abkürzung für die Suche
- [x] Nur die Inhalte, nicht die PDFs oder ihr Layout
- [x] Lässt sich ein Bogen mit den Bausteinen aus #66 nicht abbilden, bringt dieser Slice den Baustein mit; ist die Auswertung im PDF unklar oder unvollständig, wird nachgefragt statt geraten

### Tests

- [x] Unit-Tests je Bogen: kleinstes und größtes Ergebnis, ein gemischter Fall von Hand aus dem PDF nachgerechnet, fehlende Antworten nach der Regel des Bogens, Einstufung an den Grenzen

## Entscheidungen

Abgestimmt im Interview vom 08.11.2026.

- Alle Bögen der Kategorie „Scores“, in Slices nach Unterkategorie.
- Nachgefragt am 09.11.2026, da DIGOTOR die PDFs nicht hat:
  - **GAD-7**: Der Link zeigt auf den GAQ-PP, und DIGOTOR hat keinen GAD-7. Er kommt aus der deutschen Version von Prof. Dr. Bernd Löwe (2015, UKE), Quelle „GAD-7, deutsche Version (Löwe 2015)“: 7 Items 0–3, Summe 0–21, minimale (0–4), leichte (5–9), mäßige (10–14), schwere Ängste (15–21).
  - **DC/TMD Symptom-Fragebogen**: Die Datei „…Untersuchungsbogen_09-2026-1.pdf“ ist Byte für Byte der Untersuchungsbogen. Er kommt aus dem deutschen „Symptombezogenen Fragebogen“ (Asendorf, Eberhard & Schierz, Version 12/2018) des International RDC/TMD Consortium Network; seine Sprünge („springen Sie zu Frage …“) als Bedingungen, ohne Ergebnis.
  - **DC/TMD-Untersuchung**: je Zeile des Rasters (Abschnitte 4–9) eine Ankreuzliste der Schmerzarten bzw. Befunde; angekreuzt ist J, nicht angekreuzt N. Ohne Ergebnis.
  - **Regionen im Kiefer- und Kopfbereich**: Kiefergelenk, M. temporalis, M. masseter, Andere Kaumuskeln, Nicht-mastikatorische Muskeln, Gesicht, je rechts/links, und Mund und Zähne. 1a der Untersuchung bietet die fünf Orte des Bogens je Seite, 1b den M. temporalis je Seite („Andere“ und „Keine“ im Freitext bzw. nichts angekreuzt); das Schmerzbild fragt Gesicht und Kiefer mit dieser Liste und den Körper mit der bisherigen.
- Jedes Ergebnis erst, wenn alle seine Items beantwortet sind, wie in #72; keiner der Bögen sagt etwas zu fehlenden Antworten.
- DHI: Physisch (P, 7 Fragen), Emotional (E, 9), Funktionell (F, 9) und Total (0–100), Ja 4, Manchmal 2, Nein 0; niedriger ist besser, keine Einstufung, wie im Original.
- OBC: Summe 0–84, keine (0), gering (1–24), ausgeprägt (25–84); Fragen 1–2 mit den Häufigkeiten im Schlaf, 3–21 mit Nie bis Immer.
- Schneidekantenverhältnisse: Overjet, Overbite und Mittellinie als Zahl in mm, „falls negativ“ als eigenes Häkchen, die Seite der Mittellinie als R/L.
- Namen wie im Ticket; Abkürzungen DHI, OBC, GAD-7; die drei CMD-Bögen finden sich über „DC/TMD“ bzw. „Schmerzbild“ im Namen. Alle sechs nur unter „Kiefer / CMD“, wie bei DIGOTOR.
- Offensichtliche Tippfehler: „Generalizied“ als „Generalized“ (bei DIGOTOR und im Löwe-PDF).
