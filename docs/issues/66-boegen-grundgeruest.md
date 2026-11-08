# 66 – Bögen: Grundgerüst, Anamnesebogen & PHQ-9

**Etappe:** 11 – Dokumentation: Assessmentbögen  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §14 (Assessmentbögen)  
**GitHub-Issue:** #66  
**Blockiert durch:** [65 – Repo privat schalten](65-repo-privat.md)

## Was gebaut wird

Am Termin füllst du eingebaute Assessmentbögen aus, und die App wertet sie aus wie das Original. Dieser Slice baut alles, was die Bögen brauchen: das Format, die Bausteine, die Auswertung, „+ Bogen“ am Termin, den Reiter Bögen beim Patienten mit Karten und Verlauf und das Ausblenden im Menü. Dazu kommen die ersten zwei Bögen: der Anamnesebogen V8 (ohne Punkte) und der PHQ-9 (mit Punkten und Einstufung). Die übrigen Bögen folgen nach Unterkategorie in #67 bis #76.

## Akzeptanzkriterien

### Format

- [x] Ein Bogen ist eine feste Definition im Code (oder als Datei in den Assets): **stabile ID**, Name, Abkürzung, Unterkategorie(n) wie bei DIGOTOR, Quelle (Dateiname des PDFs mit Stand, z. B. „PHQ-9, Stand 10/2026“), Hinweistext des Bogens, Abschnitte mit Items und die Auswertung
- [x] Jedes Item hat eine stabile ID; gespeichert werden nur die Antworten je Item-ID. Das Ergebnis wird immer aus den Antworten berechnet, sodass eine korrigierte Definition in einem App-Update auch für alte Bögen gilt (wie bei den Tests, #61)
- [x] Ein Item, das in einer neuen Definition fehlt, bleibt mit seiner Antwort im ausgefüllten Bogen sichtbar (grau, „nicht mehr im Bogen“)

### Bausteine

- [x] **Antwort-Item**: eine der festen Antworten wählen, jede mit Punkten; optional umgekehrt gepolt
- [x] **Ja/Nein**, **Skala** (z. B. 0–10 mit Beschriftung der Enden, Punkte optional), **Ankreuzliste** (mehrere ankreuzbar, optional „Sonstiges“ mit Freitext), **Zahl** (z. B. Minuten, Meter), **Freitext**
- [x] **Körperregionen**: eine Liste von Regionen zum Ankreuzen (Kopf, HWS, BWS, LWS, Becken/ISG, Schulter, Ellenbogen, Hand, Hüfte, Knie, Fuß – Extremitäten je links/rechts) und ein Freitext; ersetzt „bitte einzeichnen“
- [x] Ein Item kann nur unter einer Bedingung erscheinen (z. B. „falls ja: …“)
- [x] Was sich mit diesen Bausteinen nicht abbilden lässt, bringt der Slice mit, der den Bogen braucht

### Auswertung

- [x] Ergebnisse je Bogen: keins, eins oder mehrere (Teilskalen); je Ergebnis Name, Rechenregel, Einheit (Punkte, %), Bereich und **Richtung** (höher oder niedriger ist besser) nach dem Original
- [x] Rechenregeln: Summe, Mittelwert, Umrechnung in Prozent oder auf 0–100, umgekehrt gepolte Items, und die **Regeln für fehlende Antworten** des Originals (z. B. Höchstpunktzahl verringern, Mindestzahl beantworteter Items)
- [x] **Einstufung** als Text nach den Grenzen des Originals (z. B. PHQ-9: „minimal“, „leicht“ …)
- [x] Das Ergebnis rechnet live mit; reicht das Beantwortete nach den Regeln nicht: „Ergebnis: –“
- [x] Die Auswertung ist reine Logik ohne Android und vollständig mit Unit-Tests abgedeckt

### Am Termin

- [x] Im Termin unter den Tests der Abschnitt **Bögen** mit den Bögen dieses Termins und „+ Bogen“
- [x] **Auswahl**: oben „Zuletzt benutzt“ (die letzten 5, gespeichert in der Doku-Datenbank), darunter die Unterkategorien wie bei DIGOTOR mit ihren Bögen, ganz oben ein Suchfeld (Name und Abkürzung, ohne Rücksicht auf Groß- und Kleinschreibung, z. B. „phq“, „oswestry“). Ausgeblendete Bögen erscheinen nicht. Ein Bogen in mehreren Kategorien steht in jeder und ist derselbe Bogen
- [x] Der Bogen öffnet als eigene Seite: Name, Datum des Termins, Hinweistext, die Items in der Reihenfolge des Originals, unten das Ergebnis mit Einstufung
- [x] Speichern geht jederzeit, auch unvollständig; was getippt wurde, wird vor dem Sperren gespeichert (§14)
- [x] Ein Termin kann mehrere Bögen haben, auch zweimal denselben
- [x] Bögen lassen sich ändern und mit Rückfrage löschen; einen Termin löschen löscht seine Bögen mit, die Rückfrage nennt Messungen und Bögen mit Anzahl
- [x] Beim abgeschlossenen Patienten nur zum Lesen

### Reiter Bögen

- [x] Die Seite eines Patienten bekommt einen vierten Reiter **Bögen** (Übersicht · Termine · Tests · Bögen)
- [x] Oben je Bogen mit Ergebnis, der beim Patienten mindestens einmal ausgefüllt wurde, eine **Karte** wie bei den Tests (#62): Name, Datum des letzten, je Ergebnis der letzte Wert mit Einheit und Einstufung und die Veränderung zum ersten, besser oder schlechter nach der Richtung, mit Pfeil und Wort; zuletzt ausgefüllte zuerst
- [x] Tippen öffnet den **Verlauf**: je Ergebnis (bei Teilskalen je Teilskala) ein Liniendiagramm wie in #62, darunter eine Tabelle aller ausgefüllten Bögen, neueste oben; eine Zeile öffnet den Bogen am Termin
- [x] Darunter **ohne Ergebnis** (z. B. Anamnesebogen): die ausgefüllten Bögen nach Datum, neueste oben; Tippen öffnet sie

### Menü Bögen

- [x] Im Menü der Doku **Bögen**: alle eingebauten Bögen nach Unterkategorie mit Quelle und Stand, je ein Schalter **ausblenden**
- [x] Ausgeblendete fehlen bei „+ Bogen“; schon ausgefüllte bleiben überall sichtbar

### Speicher, Suche, Backup

- [x] Neue Tabellen in `doku.db` mit Migration (kein `fallbackToDestructiveMigration`); damit kommen die Bögen ohne Weiteres in die Backups (#63)
- [x] Die Doku-Suche findet die Freitexte der Bögen (Freitext, „Sonstiges“, Freitext der Körperregionen); der Treffer öffnet den Bogen
- [x] Insel-Regeln wie in der übrigen Doku: keine Wörter lernen, vertrauliche Zwischenablage

### Erste Bögen

- [x] **Anamnesebogen Version 8** (Anamnese, Stand 07/2026): alle Fragen beider Seiten; Frage 1 als Körperregionen; ohne Ergebnis
- [x] **PHQ-9** (Stand 10/2026): 9 Antwort-Items 0–3, Summe 0–27, niedriger ist besser, Einstufung nach dem Bogen; Kategorien Schmerz und Psychosoziales sowie Kiefer / CMD

### Tests

- [x] Unit-Tests: jede Rechenregel (Summe, Mittelwert, Prozent, 0–100, umgekehrt gepolt, fehlende Antworten, Einstufung an den Grenzen); bedingte Items; Item, das aus einer Definition verschwindet; PHQ-9 mit 0, 27 und einem gemischten Fall; Karte und Veränderung je Richtung; Termin löschen mit Bögen; Ausblenden; Zuletzt benutzt; Migration
- [x] UI-Tests: Bogen am Termin auswählen (Suche, Zuletzt benutzt), ausfüllen und ändern; Karte und Verlauf; Anamnesebogen mit Körperregionen; Bogen ausblenden

## Entscheidungen

Abgestimmt im Interview vom 08.11.2026.

- Bögen hängen am Termin wie die Messungen, damit jede Erfassung ihren Zusammenhang hat.
- Eingebaut und nicht änderbar; kein Editor für eigene Bögen.
- Auswertung wie im Original mit Einstufung, damit Ergebnisse mit der Literatur vergleichbar sind.
- Eigener Reiter Bögen statt der Karten im Reiter Tests.
- Körperregionen statt einer Zeichenfläche: schnell, durchsuchbar, keine Bilder in der Doku.
- Kein Patientenmodus und kein Export.
- Unvollständig speichern, Ändern, Löschen und Lesen beim abgeschlossenen Patienten wie bei den Messungen (#62); das kam im Interview nicht einzeln vor.
