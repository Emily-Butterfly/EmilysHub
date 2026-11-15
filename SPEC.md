# EmilysHub – Spezifikation

Persönliche, vollständig offline arbeitende Android-App als zentrale Anlaufstelle für Routinen, Erinnerungen, To-dos, Ziele, Notizen, Haushaltsbuch, Sprachunterricht und eine gesperrte Dokumentation zu Patienten.

## 1. Grundlagen

| Thema | Entscheidung |
|---|---|
| Zielgerät | Samsung Galaxy A52 5G, Android 14 (One UI 6) |
| Mindestversion | Android 11 (API 30) |
| Technik | Kotlin, Jetpack Compose, Room + SQLCipher |
| Sprache | Deutsch |
| Name | EmilysHub |
| Icon | Haus mit ausgestanztem „E“ (schlichte Blockform), adaptives Icon mit einfarbiger Variante, selbst erstellt (keine externen Grafiken); das Symbol in der Statusleiste zeigt dasselbe |
| Farben | Festes Farbschema: hell = Flieder, dunkel = Lila (kein Material You) |
| Hell/Dunkel | Folgt dem System, in der App umstellbar |

### Navigation

Untere Leiste mit den Tabs **Heute · Routinen · To-dos · Notizen · Sonstiges**.
Startbildschirm ist **Heute**. **Sonstiges** ist eine Liste mit **Ziele** (§6; hieß bis v1 „Wunschliste“ und war bis Etappe 10 ein eigener Tab), **Dokumentation** (§14), **Sprachen** (§15) und **Tracking** (§17). Alle öffnen im Tab: Die untere Leiste bleibt, der Zurück-Pfeil führt zur Liste. Ziele und Sprachen bleiben beim Wechsel des Tabs offen, Dokumentation und Tracking sperren sich dabei (§14, §17). Orte, Statusseite, Backups und Sicherheit liegen in den **Einstellungen**. Das **Haushaltsbuch** (§13) öffnet im Tab Notizen ein Knopf in der Mitte der oberen Leiste, direkt unter der Kamera: 1/4 der Bildschirmbreite, beschriftet „Haushaltsbuch“, bei zu wenig Platz „€“. In Heute öffnet es ein kleines „€“ links neben der Flamme. Der **Eingang** (§16) hat seine Zeile oben in Heute.

### Heute-Übersicht

- **Kopfzeile** mit dem Datum und dem Stand des Tages („Fr., 23. Okt. · 2 von 13 erledigt“)
- Die Zeile des **Eingangs** (§16): ein Gedanke, mit Enter festgehalten; darunter „Eingang (3)“, solange etwas darin liegt
- Alle heute fälligen Routinen und To-dos, direkt abhakbar, in **drei Blöcken** (unten)
- Einträge, die auf das Erreichen eines Ortes warten
- Angepinnte Notizen
- Abschnitt **Demnächst**: To-dos, die schon vor ihrer Fälligkeit gezeigt werden (§5), und Abos, deren Kündigungsfrist näher rückt (§13)
- **Heatmap** der letzten 6 Monate unter den Einträgen: je Tag ein Kästchen, je mehr Routinen und To-dos erledigt, desto kräftiger
- **Flamme** in der oberen Leiste mit den Tagen des Gesamt-Streaks (§8); grau mit dem Streak bis gestern, solange der heutige Tag noch nicht zählt, und grau mit „0“, wenn kein Streak läuft; es gibt sie, sobald in der Geschichte etwas für oder gegen einen Tag zählt; Tippen öffnet die Statistik
- **„€“** in der oberen Leiste links neben der Flamme: öffnet den laufenden Monat im Haushaltsbuch (§13)
- **„‹ Gestern“** über der Liste: der Vortag zum Nachtragen (unten)
- Warnbanner bei fehlenden Berechtigungen, fehlgeschlagenem oder veraltetem Backup und wenn die Backups zu lange nicht vom Handy wegkopiert wurden (§10)

### Drei Blöcke

- **Überfällig (n)**: ganz oben eine einzelne Zeile mit der Zahl der überfälligen To-dos; Antippen klappt sie auf, die am längsten überfälligen zuerst. Zugeklappt bei jedem Öffnen von Heute.
- **Jetzt und als Nächstes**: alles, was heute noch offen ist: die To-dos des Tages ohne Uhrzeit, dann Routinen und To-dos mit Uhrzeit, nach ihr. Dazu gehört auch, was seine Uhrzeit schon hinter sich hat, solange es offen ist.
- **Schon vorbei**: zugeklappt, mit einer Zeile wie „3 erledigt · 1 verpasst“: Erledigtes, Übersprungenes, Ausgelassenes, durch eine gekoppelte Routine Erledigtes und Verpasstes, nach Uhrzeit. Aufgeklappt lässt sich zurücknehmen, was sich zurücknehmen lässt. Eine verpasste Routine zeigt ihre Checkliste nicht.
- Was abgehakt wird, wandert nach „Schon vorbei“; eine Leiste bietet „Rückgängig“. Eine Routine mit Checkliste bleibt oben, bis ihr letzter Unterpunkt abgehakt ist.
- Ist nichts mehr offen, sagt der mittlere Block das („Für heute ist alles erledigt“).
- Der Stand in der Kopfzeile zählt die Einträge des Tages ohne Übersprungenes; erledigt ist, was abgehakt ist.
- Der Vortag (unten) bleibt eine Liste ohne Blöcke.

### Menü je Eintrag

- Jeder Eintrag in Heute hat ein Menü, auch per langem Druck. Antippen der Zeile hakt weiter ab.
- **To-do**: „Morgen · Am Wochenende · Nächste Woche · Datum wählen · Öffnen“. Verschieben ändert nur das Datum der Fälligkeit, die Uhrzeit bleibt; danach bietet eine Leiste „Rückgängig“. „Am Wochenende“ ist der kommende Samstag, „Nächste Woche“ der kommende Montag. Dasselbe Menü haben die To-dos im Tab To-dos.
- **Routine**: „Überspringen · Heute nicht · Öffnen“; einen eigenen Knopf „Überspringen“ hat die Zeile nicht mehr. Eine offene Routine mit Checkliste zeigt dort „Starten“ (§2, Ablauf-Modus).
- „Öffnen“ führt in den Editor.

### Gestern nachtragen

- „‹ Gestern“ zeigt den Vortag, deutlich markiert („Gestern, Fr. 6. Nov. – nachtragen“), mit „Heute ›“ zurück; die Zurück-Taste führt ebenfalls zurück. Es geht nur einen Tag zurück.
- Gezeigt werden nur die Routinen und To-dos des Vortags, ohne Demnächst, angepinnte Notizen, Heatmap und Warnbanner.
- Verpasste, ausgelassene und noch offene Routinen lassen sich als erledigt abhaken, offene To-dos ebenso; der Haken lässt sich wieder entfernen. Überspringen und „Heute nicht“ gibt es dort nicht.
- Nachgetragenes zählt voll als erledigt am Vortag: für Streak, Flamme, Statistik und Heatmap, ohne Sondermarkierung. Ein To-do gilt als gestern erledigt; bei Wiederholung „ab Erledigung“ rechnet der nächste Termin ab gestern.
- Wechsel des Tabs oder ein neuer Start der App zeigt wieder den heutigen Tag.

## 2. Routinen

| Feld | Details |
|---|---|
| Name | Pflicht |
| Beschreibung | Optional |
| Uhrzeiten | Eine oder mehrere pro Tag |
| Wiederholung | Täglich, bestimmte Wochentage, alle X Tage, einmalig; außer bei „einmalig“ endet sie nie oder an einem Datum (einschließlich), danach wandert die Routine ins Archiv |
| Orte | Keiner („überall“), einer oder mehrere; gilt an einem beliebigen davon |
| Nachhol-Zeitfenster | Pro Routine einstellbar, Standard 4 Stunden; alternativ Checkbox „bis Mitternacht“ |
| Vollbild-Alarm | An/aus pro Routine (Standard: aus) |
| Ton | Ton der Erinnerung und des Vollbild-Alarms (§3); ohne Wahl der Standard aus den Einstellungen |
| Name auf dem Sperrbildschirm | An/aus pro Routine (Standard: aus), §3 |
| Kopplung | Mit anderen Routinen zu einer Gruppe, in der eine erledigte für den Tag reicht; nur Routinen mit genau einer Uhrzeit am Tag |
| Wiederholungs-Erinnerungen | Abstand (Standard 30 min) und Anzahl (Standard 3, max. 5) pro Routine |
| Priorität | Niedrig / Normal / Hoch |
| Checkliste | Unterpunkte; Routine ist erledigt, wenn alle abgehakt sind, oder direkt komplett abhaken; je Unterpunkt optional Minuten für den Ablauf-Modus (unten) |
| Tags | Gemeinsame Tags (alle Bereiche) |
| Routinen-Ziel (optional) | z. B. „30 Tage Streak“ oder „100 × erledigt“ – für den Fortschritt verknüpfter Ziele (§6) |
| Erledigt durch Vokabeln | Optional eine Sprache: Ist ihr Tagespensum im Vokabeltrainer geschafft, gilt die Routine an dem Tag als erledigt (§15) |
| Status | Aktiv, pausiert, archiviert; Statistik bleibt erhalten |

### Tracking

- Jede Fälligkeit endet als **Erledigt**, **Übersprungen**, **Ausgelassen** oder **Verpasst**.
- **Verpasst** wird automatisch gesetzt, wenn das Zeitfenster abläuft.
- **Ausgelassen**: mit „Heute nicht“ bewusst für den Tag fallen gelassen, und zwar alle restlichen Fälligkeiten der Routine von heute. Es kommen keine Erinnerungen mehr; zählt wie Verpasst, wird aber eigens angezeigt. In Heute rückgängig zu machen, solange der Tag läuft.
- Pausierte Tage zählen nicht als verpasst.
- Am Vortag (§1, Gestern nachtragen) lassen sich verpasste, ausgelassene und offene Fälligkeiten nachträglich als **Erledigt** abhaken; ohne Haken sind sie wieder verpasst.

### Gekoppelte Routinen

- Gekoppelte Routinen bilden eine Gruppe; eine Routine gehört zu höchstens einer.
- Ist eine Routine der Gruppe an einem Tag erledigt, gelten die anderen an dem Tag als **übersprungen** („durch Laufen erledigt“); ihre Erinnerungen kommen nicht mehr. Rückgängig machen bringt sie zurück, solange ihr Zeitfenster läuft.
- Das gilt für alles, was an dem Tag noch offen oder schon **verpasst** ist, und für eine Routine, deren „Überspringen“ an dem Tag rückgängig gemacht wird: Sie gilt dann gleich als durch die erledigte übersprungen.
- Überspringen und „Heute nicht“ betreffen nur die eine Routine, außer im gemeinsamen Alarm (§3).

### Ablauf-Modus

Eine Routine mit Checkliste lässt sich Schritt für Schritt abarbeiten.

- **Starten** an der offenen Routine in Heute, in ihrer Erinnerung und im Vollbild-Alarm (§3). Der Ablauf zeigt einen Unterpunkt groß, „Schritt 2 von 4“, „Fertig“ und „Schritt zurück“; schon Abgehaktes überspringt er.
- „Fertig“ hakt den Unterpunkt ab, wie in Heute. Mit dem letzten ist die Routine erledigt; ein kurzer Abschluss zeigt die Dauer und den Streak.
- Der Ablauf ist nur eine andere Sicht auf die Checkliste: Verlassen lässt die Haken, wie sie sind, und er lässt sich wieder aufnehmen, solange die Fälligkeit offen ist.
- **Zeit**: Hat der Unterpunkt Minuten (1–180), läuft ein Ring mit seiner Restzeit; ohne Minuten eine Stoppuhr. Ist die Zeit um, zählt er weiter und meldet sich einmal; weiter geht es nur mit „Fertig“. Haben alle restlichen Unterpunkte Minuten, steht dabei „noch 3 Schritte, fertig gegen 07:42“.
- **Laufende Benachrichtigung**, solange ein Ablauf läuft: mit dem Countdown und „Fertig“. Der Unterpunkt steht dort nur, wenn die Routine ihren Namen auf dem Sperrbildschirm zeigen darf, sonst „Ablauf läuft“. Kein Dauerdienst; das Ende eines Schritts meldet ein Wecker.
- Der Bildschirm bleibt an, solange der Ablauf offen ist. Ein laufender Ablauf übersteht das Beenden der App.
- Nur für Routinen; To-dos mit Checkliste haben keinen Ablauf-Modus. Der gemeinsame Alarm gekoppelter Routinen bietet „Starten“ nicht.

## 3. Erinnerungen (Pop-ups)

- Standard: Benachrichtigung mit hoher Priorität (Heads-up) und Aktionen **Erledigt · Später · Überspringen**; bei einer Routine mit Checkliste **Starten · Erledigt · Später** (§2, Ablauf-Modus), Überspringen bleibt in Heute und im Vollbild-Alarm.
- Optional pro Routine und To-do: **Vollbild-Alarm** (auch auf dem Sperrbildschirm). Bei Routinen bietet er entsperrt **Erledigt · Später · Überspringen · Heute nicht**, mit Checkliste dazu **Starten**.
- **Keine** Overlays über anderen Apps (`SYSTEM_ALERT_WINDOW` wird nicht verwendet).
- **Später**: Auswahl 10 min / 30 min / 1 h. In der Benachrichtigung einer Routine 10 min / 30 min / **Heute nicht** (§2), da Android dort höchstens drei Knöpfe zeigt; der Vollbild-Alarm bietet alle drei Zeiten. Bei To-dos kommen **Heute Abend** (20:00, angeboten bis 19:00) und **Morgen früh** (08:00 am nächsten Tag, nachts vor 04:00 am selben) dazu: Ihre Benachrichtigung bietet 30 min / Heute Abend / Morgen früh, nach 19:00 statt „Heute Abend“ 1 h; der Vollbild-Alarm bietet alle. „Später“ verschiebt nur die Erinnerung, nicht die Fälligkeit.
- **Ignoriert**: erneute Erinnerung im eingestellten Abstand, höchstens die eingestellte Anzahl (max. 5); danach bleibt die Benachrichtigung still stehen, bis das Zeitfenster abläuft.
- Sperrbildschirm: nur „Erinnerung fällig“, außer der Eintrag erlaubt seinen Namen dort (dann Name und Uhrzeit, im Vollbild-Alarm wie in der Benachrichtigung). Aktionen in jedem Fall erst nach Entsperren.
- **Ton**: je Routine und To-do ein Ton; der Vollbild-Alarm spielt ihn in Schleife, die Benachrichtigung einmal. Zur Wahl stehen die Töne des Handys und eigene Audiodateien, die die App dafür in die Töne des Handys kopiert. In den Einstellungen je ein Standard für Wecker und für Benachrichtigungen; fehlt ein Ton, gilt der Standard. Benachrichtigungen mit eigenem Ton laufen über einen eigenen Kanal je Ton.
- **Gemeinsamer Alarm**: Sind gekoppelte Routinen (§2) zur selben Uhrzeit fällig, kommt eine Erinnerung für alle. Der Vollbild-Alarm hat einen „Erledigt“-Knopf je Routine; „Später“, „Überspringen“ und „Heute nicht“ gelten für alle zusammen. Die Benachrichtigung bietet „Erledigt · Später · Überspringen“ und fragt nach „Erledigt“, welche es war.

## 4. Orte

- Benannte Orte (z. B. „Zuhause“, „Arbeit“, „Gym“), verwaltet in den Einstellungen. Ein neuer Ort lässt sich auch direkt im Feld „Orte“ des Routine- und des To-do-Editors anlegen („Neuer Ort“); er ist danach dort gewählt, und der Entwurf bleibt.
- **WLAN-Ort**: aktuelles WLAN übernehmen oder Name (SSID) eintippen; mehrere WLANs pro Ort.
- **GPS-Ort**: aktuellen Standort übernehmen oder Koordinaten eingeben; Radius einstellbar (50–500 m, Standard 150 m).
- Ortserkennung ohne Google Play Services (Android `LocationManager` / `ConnectivityManager`).

### Ortsgebundene Erinnerung

1. Zur fälligen Uhrzeit am Ort → Pop-up sofort.
2. Nicht am Ort → Eintrag wartet; Pop-up beim Ankommen, solange das Zeitfenster (bzw. „bis Mitternacht“) läuft.
3. Zeitfenster abgelaufen → **Verpasst**.

Ankommen erkennt die App beim Verbinden mit einem WLAN. Solange ein To-do auf einen Ort mit GPS-Bereich wartet, sieht sie zusätzlich alle 30 Minuten nach, ob du dort angekommen bist (in den Einstellungen abschaltbar).

## 5. To-dos

| Feld | Details |
|---|---|
| Titel | Pflicht |
| Beschreibung | Optional |
| Fälligkeit | Ohne / Datum / Datum + Uhrzeit |
| Wiederholung | Optional, nur mit Fälligkeit: täglich, bestimmte Wochentage, alle X Tage, monatlich, jährlich; nächster Termin nach Plan oder ab Erledigung; endet nie, nach N Mal (N zählt die To-dos der Reihe) oder an einem Datum (einschließlich) |
| In Heute zeigen | Am Fälligkeitstag oder 1–30 Tage vorher (Abschnitt „Demnächst“), nur mit Fälligkeit |
| Vollbild-Alarm | An/aus pro To-do (Standard: aus); klingelt, wo die Erinnerung kommt: zur Uhrzeit, nach „Später“ und beim Ankommen an einem Ort |
| Weckzeit | Optional: N Tage vor der Fälligkeit um eine Uhrzeit (wandert bei Wiederholungen mit) oder ein fester Zeitpunkt (auch ohne Fälligkeit, gilt nur für dieses To-do); zur Fälligkeit kommt die Erinnerung trotzdem |
| Ton | Wie bei Routinen (§3) |
| Name auf dem Sperrbildschirm | An/aus pro To-do (Standard: aus), §3 |
| Ort | Optional; Erinnerung beim Ankommen, auch ohne Uhrzeit |
| Priorität | Niedrig / Normal / Hoch |
| Checkliste | Unterpunkte |
| Tags | Gemeinsame Tags |

- Erinnerungen nutzen dasselbe System wie Routinen.
- Erledigte To-dos wandern ins **Archiv** (wiederherstellen oder endgültig löschen).
- Ein **wiederkehrendes** To-do wandert abgehakt ebenso ins Archiv; zugleich erscheint das nächste mit der nächsten Fälligkeit.
- Überfällige To-dos stehen oben in der Heute-Übersicht.
- Am Vortag (§1, Gestern nachtragen) abgehakt, gilt ein To-do als **gestern erledigt**.
- To-dos mit Vorlauf stehen in Heute unter **Demnächst**, nach Fälligkeit sortiert, bis sie fällig sind; abhaken geht dort sofort. „Demnächst“ erinnert nicht.
- **Verschieben** mit einem Tipp, aus Heute und aus dem Tab To-dos (§1, Menü je Eintrag).

## 6. Ziele

Bis v1 hieß der Bereich „Wunschliste“; überall ist jetzt von Zielen die Rede. Erreichbar über Sonstiges (§1).

| Feld | Details |
|---|---|
| Titel | Pflicht |
| Beschreibung | Optional |
| Tags | Gemeinsame Tags |
| Status | Offen → In Arbeit → Erledigt (mit Datum) |
| Zeitraum | Freitext (z. B. „2027“, „Sommer“), keine Erinnerung |

- **Mehrstufig**: beliebig tief verschachtelte Unterziele, eingerückt und auf-/zuklappbar.
- Verknüpfbar: **To-dos** und **Routinen** (Routinen mit optionalem Routinen-Ziel, z. B. 30 Tage Streak) und **Prüfungen** aus dem Bereich Sprachen (§15), dazu ein **Karten-Ziel** aus dem Vokabeltrainer (§15).
- **Fortschritt**: automatisch aus erledigten Unterpunkten, manuell überschreibbar.
- Ziel mit einem Tipp in verknüpfte To-dos umwandeln.
- **Rückblick**: Liste erreichter Ziele mit Datum.
- Bewusst **keine** Erinnerungen.

Beispiel:

```
🎯 Arabisch A2 erreichen                    [40 %]
 ├─ 🎯 A1 abschließen                        [✓]
 │   ├─ ☑ To-do: Lehrbuch Kapitel 1–6
 │   └─ ☑ To-do: A1-Probetest machen
 ├─ 🎯 Wortschatz 1000 Wörter               [60 %]
 │   └─ 🔁 Routine: täglich 20 min Vokabeln
 └─ ☐ To-do: A2-Prüfung anmelden
```

## 7. Notizen

### Tabs

- Notizen liegen in **Tabs** (eine Ebene); jede Notiz in genau einem Tab.
- Fester Tab **„Alle“** ganz vorne; **„Allgemein“** ist der Standard-Tab und nicht löschbar.
- Tabs anlegen, umbenennen, sortieren, löschen. Beim Löschen Rückfrage: Inhalt nach „Allgemein“ verschieben oder mitlöschen.

### Ordner

- In jedem Tab **beliebig tief verschachtelte Ordner**, auf-/zuklappbar, mit Pfad-Anzeige (z. B. „Arabisch › Grammatik › Verben“).
- „Verschieben nach…“ für Notizen und Ordner, auch zwischen Tabs.
- Beim Löschen Rückfrage: Inhalt eine Ebene höher verschieben oder mitlöschen.

### Reihenfolge innerhalb eines Ordners

1. 📌 Angepinnte Ordner
2. 📌 Angepinnte Notizen
3. 📁 Normale Ordner
4. Normale Notizen

Innerhalb jeder Gruppe gilt die pro Ordner gewählte Sortierung (Name / letzte Änderung / manuell).

### Inhalt

- **Markdown** mit Vorschau; Rendering ohne HTML und ohne ausführbare Links.
- Notizen anpinnen (auch in die Heute-Übersicht).
- Verknüpfung mit Zielen, To-dos und Routinen.
- Der Editor speichert beim Verlassen von selbst (§8, Editoren).
- **Keine Anhänge** an Notizen; Anhänge gibt es nur an den Sessions im Bereich Sprachen (§15).

### Archiv

- Notizen und ganze Ordner lassen sich **archivieren**: Sie verschwinden aus Tabs, Ordnern, „Alle“, Heute, Suche und Verknüpfungen und verlieren ihren Pin.
- Das **Archiv** (Menü der Notizen) zeigt sie nur zum Lesen; „Wiederherstellen“ bringt sie an den alten Ort, sonst in den nächsten Ordner darüber bzw. nach oben; „Endgültig löschen“ mit Rückfrage.
- Löschen eines Ordners oder Tabs löscht nichts Archiviertes mit; es rückt höher bzw. nach „Allgemein“.

## 8. Übergreifend

- **Gemeinsame Tags** für alle Bereiche.
- **Suche** über alle Bereiche (Routinen, To-dos, Ziele, Notizen, Haushaltsbuch mit Abos, Sprachen mit ihren Karten, Eingang); die Dokumentation hat ihre eigene (§14).
- **Editoren** (alle außer denen der Dokumentation, die beim Schließen schon von selbst speichert):
  - Wer einen geänderten Entwurf verlässt, per Pfeil oder Zurück, wird gefragt: „Speichern · Verwerfen · Weiter bearbeiten“. Ohne Änderung schließt der Editor sofort.
  - Notizen und Sessions speichern beim Verlassen von selbst, wenn sie sich speichern lassen; einer Notiz ohne Titel gibt die erste Zeile ihres Textes den Titel. Sonst gilt die Rückfrage.
  - Bei einem neuen Eintrag steht die Tastatur im Titelfeld. „Speichern“ ist ein gefüllter Knopf unten, über der Tastatur.
  - Die Editoren für Routinen und To-dos zeigen zuerst das Nötige (Name, Uhrzeiten bzw. Fälligkeit, Wiederholung); alles Weitere liegt zugeklappt unter „Mehr Optionen“, mit einer Zeile, die nennt, was gesetzt ist.
- **Statistik** pro Routine und gesamt:
  - Aktueller und längster Streak
  - Kalender-Ansicht (erledigt / übersprungen / ausgelassen / verpasst)
  - Erledigt-Quote für 7 / 30 / 90 Tage
  - Nur Tage, an denen die Routine fällig war, zählen.
  - **Übersprungen** unterbricht den Streak nicht, **Verpasst** und **Ausgelassen** schon; Ausgelassen zählt in der Erledigt-Quote als nicht erledigt.
  - Durch eine gekoppelte Routine erledigte Fälligkeiten zählen wie Übersprungen.
  - Am Vortag Nachgetragenes zählt wie Erledigt.
  - Läuft kein Streak, nennt die Statistik den letzten: seine Tage und bis wann er lief.
  - **Gesamt-Streak** (die Flamme in Heute, §1): Für einen Tag zählen erledigte Fälligkeiten der Routinen und abgehakte To-dos, ein To-do an dem Tag, an dem es abgehakt wurde. Gegen einen Tag zählen nur verpasste und ausgelassene Fälligkeiten; ein offenes oder überfälliges To-do zählt nie dagegen, Übersprungenes und Offenes weder dafür noch dagegen. Ab wann ein Tag zählt, bestimmt die Einstellung „Ein Tag zählt für den Gesamt-Streak“ (Einstellungen, Statistik): **ab einer Anzahl Erledigungen** (Standard, 1–30, vorbelegt mit 1) oder **ab einer Quote** (10–100 % in Zehnerschritten: der Anteil der Erledigungen an allem, was dafür oder dagegen zählt). Ein vergangener Tag, an dem etwas dafür oder dagegen zählt und der die Regel nicht erfüllt, bricht den Streak; ein Tag, an dem nichts zählt, zählt nicht und bricht nicht. Der heutige Tag zählt, sobald er die Regel erfüllt, und bricht den Streak nicht vor Mitternacht. Die Einstellung wirkt rückwirkend auf die ganze Geschichte und bleibt auf dem Gerät. Der Streak je Routine und die Routinen-Ziele (§2) folgen ihr nicht; für sie bricht weiter jede verpasste oder ausgelassene Fälligkeit.

## 9. Sicherheit

| Bedrohung | Maßnahme |
|---|---|
| Datenabfluss | **Keine `INTERNET`-Berechtigung**, keine Google-Dienste, keine Analytics, minimale Abhängigkeiten |
| Andere Apps | Keine exportierten Komponenten außer dem Launcher und einem Alias auf ihn, den nur das System starten darf (`START_VIEW_PERMISSION_USAGE`), weil Health Connect ab Android 14 darüber zeigt, wozu Tracking liest (§17; entschieden am 15.11.2026); `PendingIntent`s immutable und explizit, mit einer Ausnahme: Das Antwortfeld der Benachrichtigung „Gedanke festhalten“ (§16) braucht ein veränderliches, wie Android es verlangt; es bleibt explizit und geht nur an den eigenen, nicht exportierten Empfänger (entschieden am 12.11.2026); strikte Eingabeprüfung |
| Diebstahl / Speicher auslesen | Datenbank mit SQLCipher verschlüsselt, Schlüssel im Android Keystore; `allowBackup=false`, Datenextraktionsregeln schließen alles aus |
| Zugriff auf entsperrtes Handy | App-Sperre per Fingerabdruck mit Geräte-PIN als Ersatz; erneute Sperre nach 1 min im Hintergrund |
| Mitlesen | `FLAG_SECURE` (keine Screenshots, ausgeblendet in „Zuletzt verwendet“); Sperrbildschirm zeigt nur „Erinnerung fällig“, Namen nur von Einträgen, die es erlauben (Standard: aus) |
| Manipulierte Dateien | Backup-Import mit Authentifizierung (AES-GCM) und strenger Schemaprüfung; Markdown ohne HTML/Links |
| Unlesbare Daten | Rettungsbildschirm statt Absturz: Backup laden oder neu beginnen; die unlesbaren Dateien werden beiseitegelegt, nicht gelöscht (§10) |
| Importierte Dateien (Sprachen, §15) | Anhänge liegen nur verschlüsselt im Speicher der App, nie im Klartext; Fotos werden neu kodiert, PDFs nur angezeigt (Androids eigener Renderer), nichts daraus ausgeführt; Größengrenzen beim Import; keine Kamera-Berechtigung, „Foto aufnehmen“ läuft über die Kamera-App des Handys |
| Gesundheitsdaten (Dokumentation, §14) | Eigene SQLCipher-Datenbank, deren Schlüssel nur Doku-Passwort oder Fingerabdruck freigeben, nicht der Schlüssel der übrigen App; Sperre beim Verlassen, im Hintergrund und nach 5 min ohne Eingabe; nicht in Suche, Heute und Benachrichtigungen; Tastatur ohne Lernen; Zwischenablage vertraulich und beim Sperren geleert; gelöschte Daten überschrieben |
| Gesundheitsdaten (Tracking, §17) | Wie die Dokumentation: eigene SQLCipher-Datenbank mit eigenem Passwort oder Fingerabdruck, gleiche Sperre und Insel-Regeln; Health Connect nur lesend und nur bei entsperrtem Tracking; PDF-Export nur von Hand, mit Hinweis auf die fehlende Verschlüsselung |

Release-Builds mit R8 (Minify + Shrink), keine Debug-Features.

## 10. Backups

- **Backup-Passwort** wird einmal festgelegt; daraus abgeleiteter Schlüssel (Argon2id bzw. PBKDF2) wird im Android Keystore geschützt abgelegt. **„Passwort ändern“** im Backup-Bildschirm verlangt das alte Passwort; daraus wird ein neuer Schlüssel mit neuem Salz, mit dem alle künftigen Backups verschlüsselt werden, auch die nächtlichen. Ist das alte vergessen, lässt sich nach der App-Sperre (auch bei offener App) ein neues festlegen; ohne Displaysperre gibt es diesen Weg nicht, und der Dialog sagt, dass erst eine einzurichten ist. Schon gespeicherte Backups öffnen in beiden Fällen weiter nur mit dem Passwort, mit dem sie gespeichert wurden; der Bildschirm sagt das vorher.
- Verschlüsselung: AES-256-GCM.
- **Automatisch**: täglich nachts in einen vom Nutzer gewählten Ordner (Storage Access Framework); die letzten 7 nächtlichen bleiben erhalten, ältere löscht die App.
- **Manuell**: Export und Import jederzeit; Export in einen beliebigen Ordner, auch den der nächtlichen; von Hand gespeicherte Backups löscht die App nie. Import auf neuem Gerät mit dem Passwort.
- Hinweis in der Heute-Übersicht, wenn das letzte Backup älter als 30 Tage ist oder fehlgeschlagen ist.
- Hinweis an den Nutzer: Backups regelmäßig vom Handy wegkopieren (PC, USB-Stick).
- **Auslagern**: In den Backups bestätigt „Heute weggekopiert“, dass die Backups auch auf einem anderen Gerät liegen; der Bildschirm zeigt „Zuletzt ausgelagert am …“. Ist das über 30 Tage her, oder wurde es 30 Tage nach dem ersten Backup noch nie bestätigt, warnt Heute („Deine Backups liegen nur auf dem Handy“). Die Bestätigung bleibt auf dem Gerät und steht nicht im Backup.
- Gewählte Töne stehen im Backup nur als Wahl, die Tondateien selbst nicht.
- Die **Anhänge** der Sessions im Bereich Sprachen (§15) stehen im Backup, auch im nächtlichen; die Größengrenzen beim Import halten es klein.
- Die **Dokumentation** (§14) liegt im Backup in ihrer eigenen Verschlüsselung, auch im nächtlichen, das sie dafür nicht entsperrt. Mit dem Backup-Passwort allein ist sie nicht lesbar; einspielen braucht zusätzlich das Doku-Passwort, das beim Backup galt.
- **Tracking** (§17) ebenso, mit dem Tracking-Passwort.

### Wenn sich die Daten nicht öffnen lassen

- Geht die Datenbank beim Start nicht auf (Schlüsseldatei beschädigt, Schlüssel im Android Keystore verloren, Datei beschädigt, Daten aus einer neueren Version der App), zeigt die App statt abzustürzen den Bildschirm „Deine Daten lassen sich nicht öffnen“, mit dem Grund in einem Satz und ohne technische Einzelheiten.
- Er bietet „Erneut versuchen“, „Backup laden“ (wie in den Backups, mit dem Passwort des Backups) und „Neu beginnen“ (mit Rückfrage). Bei Daten aus einer neueren Version nennt er stattdessen den Weg: die neuere App installieren.
- „Backup laden“ und „Neu beginnen“ löschen die unlesbaren Dateien nicht, sondern legen sie beiseite; die Backups zeigen sie danach mit „Endgültig löschen“.
- Die Dokumentation (§14) hat ihre eigene Datenbank und bleibt davon unberührt.
- Solange der Bildschirm steht, kommen keine Erinnerungen; er sagt das.

## 11. Berechtigungen

Ausschließlich:

- `POST_NOTIFICATIONS`
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`
- `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`
- `USE_FULL_SCREEN_INTENT`
- `VIBRATE` (Vollbild-Alarm vibriert wie ein Wecker)
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `RECEIVE_BOOT_COMPLETED` (Alarme nach Neustart wiederherstellen)
- `USE_BIOMETRIC`
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` (Vollbild-Alarm klingelt wie ein Wecker weiter, auch wenn Android bei benutztem Gerät nur die Benachrichtigung zeigt)
- `FOREGROUND_SERVICE_LOCATION` (nur falls für die Ortserkennung nötig)
- Health Connect nur lesend für Tracking (§17): `android.permission.health.READ_HEART_RATE`, `READ_RESTING_HEART_RATE`, `READ_STEPS`, `READ_SLEEP` und `READ_HEALTH_DATA_HISTORY`

- Abfrage **erst bei Bedarf**, jeweils mit deutscher Erklärung.
- **Statusseite „Funktioniert alles?“** in den Einstellungen: zeigt fehlende Berechtigungen, Samsung-Hinweis („Nie in Standby versetzen“), Testbutton für eine Probe-Erinnerung in 10 s. Was fehlt, richtet sich danach, was Routinen, To-dos und Abos (§13) brauchen.
- Warnbanner in der Heute-Übersicht bei fehlenden wichtigen Berechtigungen.

## 12. Lieferung

- GitHub Actions baut bei jedem Push eine **signierte Release-APK** (Artefakt zum Herunterladen).
- Signaturschlüssel liegt ausschließlich als **GitHub-Secret**, nie im Repo.
- Automatische Tests für die Logik (Wiederholungen, Zeitfenster, Streaks, Verschlüsselung).
- Jeder Build der CI trägt eine eigene, steigende Versionsnummer (`versionCode` = laufende Nummer des Builds), damit sich kein älterer Build über einen neueren installieren lässt. Die Einstellungen zeigen unten Version und Commit („0.1.412 · 2dc38f5“).

### Etappen

| # | Inhalt |
|---|---|
| 1 | Grundgerüst & Sicherheit: Projekt, verschlüsselte DB, App-Sperre, keine Internet-Berechtigung, CI-Build, Tabs + Heute-Gerüst, Theme |
| 2 | Routinen & Erinnerungen: Routinen komplett, Pop-ups, Später, Wiederholungen, Erledigt/Übersprungen/Verpasst |
| 3 | Orte: WLAN- und GPS-Orte, Nachholen beim Ankommen, Zeitfenster, Statusseite |
| 4 | To-dos mit Orten, Erinnerungen und Archiv |
| 5 | Wunschliste / Ziele: mehrstufig, Fortschritt, Rückblick |
| 6 | Notizen: Tabs, Ordner, Markdown, Anpinnen |
| 7 | Statistik, Suche über alle Bereiche, Backups |
| 8 | Erweiterungen (v1.1): Icon, Ziele statt Wunschliste, „Heute nicht“, Wiederholungs-Ende, „Demnächst“, Wecker, Weckzeit und Töne für To-dos, Name auf dem Sperrbildschirm, gekoppelte Routinen, Streak-Flamme, Heatmap, Haushaltsbuch |
| 9 | Erweiterungen: Anfangssaldo und Saldo-Übertrag im Haushaltsbuch, Haushaltsbuch-Knopf in der Mitte der Notizen-Leiste, voraussichtlicher Endsaldo, Fixbuchungen mit Ende |
| 10 | Sonstiges mit Zielen und Dokumentation, „€“ und „Gestern nachtragen“ in Heute, Dokumentation: Sperre, Patienten, Termine, Tests, Backup |
| 11 | Dokumentation: Assessmentbögen (eingebaut, nach Unterkategorie) |
| 12 | Haushaltsbuch: Gesamtbudget und Budget-Rechner, Abos mit Kündigungsfrist und Erinnerung; Sprachen: Sessions, Lehrer, Themen, Prüfungen, Anhänge |
| 13 | Aus dem Gutachten vom 12.11.2026: Editoren mit Rückfrage und Selbstspeichern, Auslagern der Backups, Versionsnummer; Eingang; Heute in drei Blöcken mit Menü je Eintrag; Rettungsbildschirm; Ablauf-Modus für Routinen; Vokabeltrainer; Abfragen eingrenzen |

Nach jeder Etappe: installierbare APK.

## 13. Haushaltsbuch

Einnahmen und Ausgaben je Monat, mit Archiv. Erreichbar über den Knopf in der Mitte der oberen Leiste im Tab Notizen und über das „€“ in Heute (§1). Fixbuchungen, Abos, Kategorien und Archiv öffnet das Menü „⋮“ oben rechts im Haushaltsbuch, in dieser Reihenfolge und mit Text.

| Feld | Details |
|---|---|
| Art | Einnahme oder Ausgabe |
| Betrag | Euro mit Cent, größer als 0 |
| Datum | Pflicht, Standard heute |
| Kategorie | Pflicht; eigene Kategorien je Art |
| Beschreibung | Optional |

- **Kategorien**: zum Start Vorschläge (Ausgaben: Lebensmittel, Wohnen, Mobilität, Freizeit, Gesundheit, Kleidung, Sonstiges; Einnahmen: Gehalt, Sonstiges). Anlegen, umbenennen, löschen; beim Löschen Rückfrage, die Buchungen wandern nach „Sonstiges“ ihrer Art.
- **Fixbuchungen**: Vorlagen (z. B. Miete, Gehalt, Abos) mit Abstand monatlich, vierteljährlich, halbjährlich oder jährlich und einem Tag im Monat. Optional mit Ende: nach einer Anzahl Buchungen ab dem ersten Monat oder an einem Datum (einschließlich). An ihrem Tag automatisch eingetragen, nachgetragen, wenn die App an dem Tag nicht lief; im Monat änderbar und löschbar, ohne die Vorlage zu ändern.
- **Monatsansicht**: Einnahmen, Ausgaben, Saldo (immer Einnahmen − Ausgaben); Ausgaben je Kategorie als Balken und als Tortendiagramm; alle Buchungen nach Datum.
- **Anfangssaldo**: Jeder Monat kann einen haben, einen eigenen Wert am Monat, keine Buchung; Euro mit Cent, auch 0 oder negativ. Hat ein Monat einen, zeigt die Monatsansicht über den Summen den Anfangssaldo mit seiner Herkunft („aus Oktober übertragen“ oder „von Hand festgelegt“) und darunter den **Endsaldo** = Anfangssaldo + Einnahmen − Ausgaben. Der Anfangssaldo zählt nie als Einnahme: nicht in Summen, Diagrammen, Budgets, Archiv- und Jahressummen, nicht in Buchungsliste und Suche.
- **Voraussichtlicher Endsaldo**: nur im laufenden, offenen Monat und nur, solange darin noch Fixbuchungen kommen: Endsaldo (Anfangssaldo, keiner als 0 €, + Einnahmen − Ausgaben) samt den Fixbuchungen, Einnahmen wie Ausgaben, die bis zum Monatsende noch eingetragen werden.
- **Übertrag**: Ein offener Monat ohne Anfangssaldo bietet „Saldo aus Vormonat übertragen“ (aus einem offenen Vormonat mit Rückfrage, sein Endsaldo kann sich noch ändern) und „Anfangssaldo festlegen“. In offenen Monaten lässt er sich von Hand ändern und entfernen. Beim Abschließen fragt der Dialog „Abbrechen · Nur abschließen · Abschließen & übertragen“: Der Endsaldo geht als Anfangssaldo in den Folgemonat (nie in den Kalendermonat von heute) und ersetzt einen vorhandenen, den der Dialog nennt; in einen abgeschlossenen Folgemonat wird nicht übertragen. Ein Übertrag ist ein fester Wert und ändert sich nicht mit dem Vormonat.
- **Budget** je Ausgaben-Kategorie und Monat: Balken ab 80 % orange, darüber rot („12,50 € über Budget“); kurzer Hinweis beim Speichern einer Buchung, die es überschreitet. Keine Benachrichtigungen.
- **Monatsbudget**: In der Monatsansicht eines offenen Monats lässt sich das Budget einer Ausgaben-Kategorie nur für diesen Monat ändern: ein anderer Betrag, ein neues oder keins. Es geht dem monatlichen vor, sieht aber aus wie jedes Budget; „Wie sonst“ nimmt es zurück. Beim Abschließen hält der Monat es fest.
- **Gesamtbudget** für alle Ausgaben eines Monats, als Standard für alle offenen und kommenden Monate eins von dreien:
  - **keins** (wie bisher),
  - ein **fester Betrag**, Euro mit Cent, größer als 0,
  - **aus Einnahmen**: die im Monat eingetragenen Einnahmen samt den Fixbuchungen, die bis zum Monatsende noch als Einnahme kommen, minus einem **Sparbetrag** (Standard 0 €); nie unter 0 €. Der Anfangssaldo zählt nicht.

  Jeder offene Monat kann abweichen: ein eigener Betrag, „aus Einnahmen“ mit eigenem Sparbetrag oder keins; „Wie sonst“ nimmt es zurück. Beim Abschließen hält der Monat sein Gesamtbudget als Betrag fest; wieder geöffnet gilt die Einstellung von damals, auch der Standard von damals, nicht ein inzwischen geänderter.
  - Monatsansicht: oben ein **Gesamtbalken** „ausgegeben von Gesamtbudget“ mit den Farben der Kategorie-Balken (ab 80 % orange, darüber rot); ohne Gesamtbudget keiner.
  - Kurzer Hinweis beim Speichern einer Buchung, die das Gesamtbudget ihres Monats überschreitet. Keine Benachrichtigungen.
- **Budget-Rechner**: eine Seite mit allen Ausgaben-Kategorien, je einem Budget-Feld, der laufenden Summe, dem Gesamtbudget und der Differenz („50 € frei“ oder rot „120 € drüber“). Neben jedem Feld steht als Hilfe der Durchschnitt der Ausgaben der letzten drei abgeschlossenen Monate. Kategorien ohne Budget heißen „ohne Budget“ und zählen 0 €. Gespeichert wird alles auf einmal, auch über dem Gesamtbudget; gekürzt wird nichts von selbst. Zwei Einstiege:
  - aus den **Kategorien**: die monatlichen Budgets gegen das Standard-Gesamtbudget (ist das „aus Einnahmen“, mit dem Wert des laufenden Monats, was die Seite sagt); hier lässt sich auch das Standard-Gesamtbudget ändern,
  - aus einer **offenen Monatsansicht**: die Budgets dieses Monats gegen sein Gesamtbudget; was vom monatlichen abweicht, wird ein Monatsbudget. Hier lässt sich auch das Gesamtbudget des Monats ändern.
- **Monatsabschluss** von Hand: abgeschlossene Monate liegen im **Archiv**, nur zum Lesen, und lassen sich mit Rückfrage wieder öffnen. Mehrere Monate können gleichzeitig offen sein. Ein abgeschlossener Monat behält seine Budgets und sein Gesamtbudget von damals; sein Anfangssaldo ist nur zu lesen. Das Archiv zeigt je Monat auch den Endsaldo, wenn er einen Anfangssaldo hat, und neben den Ausgaben das Gesamtbudget mit der Differenz („1 840 € von 2 000 € · 160 € frei“ oder rot „… · 75 € drüber“), wenn er eins hat.
- **Jahresübersicht**: Summen je Monat und je Kategorie über ein Jahr; dazu oben der Anfangssaldo des Januars und unten der Endsaldo des Dezembers, wo vorhanden. Unter den Ausgaben eine Zeile **Gesamtbudget** je Monat (offene Monate mit ihrem von jetzt) und in der Jahresspalte deren Summe neben den Ausgaben derselben Monate; Monate ohne Gesamtbudget bleiben leer und zählen in diesem Vergleich auf keiner Seite mit.
- Buchungen und Abos kommen in die Backups und in die Suche.

### Abos

Verträge, die gekündigt werden müssen, bevor sie sich verlängern. Eine eigene Liste **Abos** im Haushaltsbuch neben den Fixbuchungen, sortiert nach dem nächsten letzten Kündigungstag; oben die Summe der laufenden Abos, auf den Monat umgerechnet (Abos ohne Betrag zählen nicht).

| Feld | Details |
|---|---|
| Name | Pflicht |
| Betrag und Abstand | Optional; Abstand monatlich, vierteljährlich, halbjährlich oder jährlich |
| Fixbuchung | Optional verknüpft; dann kommen Betrag und Abstand von ihr und sind am Abo nicht änderbar |
| Notiz | Optional (Kundennummer, Kündigungsweg) |
| Frist | Ein Datum „kündigen bis“, oder gerechnet (unten) |
| Vorlauf | Tage vor dem letzten Kündigungstag, an denen die Erinnerung beginnt; Standard 14 |

- **Gerechnete Frist**: „Laufzeit bis“ (Datum), Kündigungsfrist (eine Anzahl Tage, Wochen oder Monate vor dem Ende der Laufzeit) und Verlängerung um eine Anzahl Monate. Der letzte Kündigungstag ergibt sich daraus. Verstreicht er ohne Kündigung, rückt das Abo von selbst auf die nächste Laufzeit vor.
- **Einzelnes Datum**: Verstreicht es ohne Kündigung, zeigt das Abo „Frist verstrichen“ und erinnert nicht mehr, bis ein neues Datum eingetragen ist.
- **Verknüpfung**: Eine Fixbuchung gehört zu höchstens einem Abo. Der Editor der Fixbuchung bietet „Als Abo führen“, das ein verknüpftes Abo anlegt. Wird die Fixbuchung gelöscht, bleibt das Abo und behält Betrag und Abstand als eigene Angabe.
- **Erinnerung**: eine Benachrichtigung um 21:00 Uhr, mit dem Standard-Ton für Benachrichtigungen, kein Vollbild-Alarm: am ersten Tag des Vorlaufs und noch einmal am letzten Kündigungstag. Auf dem Sperrbildschirm steht nur „Ein Abo ist zu kündigen“, ohne Namen. Antippen öffnet das Abo. Knöpfe „Gekündigt“ (öffnet das Abo und fragt dort wie in der App nach dem Enddatum) und „Läuft weiter“. Lief das Handy um 21:00 nicht, kommt sie beim nächsten Start nach, solange die Frist nicht verstrichen ist. Vom ersten Tag des Vorlaufs bis zum letzten Kündigungstag steht das Abo in Heute unter **Demnächst** (§1).
- **Gekündigt**: Das Abo wandert in den Abschnitt „Gekündigt“ mit seinem Enddatum (dem Ende der Laufzeit, beim einzelnen Datum einem selbst gewählten) und erinnert nicht mehr. Ist eine Fixbuchung verknüpft, fragt die App, ob sie deren Ende auf dieses Datum setzen soll. Zurücknehmen lässt es sich.
- **Läuft weiter**: beendet die Erinnerungen dieser Laufzeit; ein gerechnetes Abo rückt auf die nächste vor, eins mit einzelnem Datum wartet auf ein neues.
- Löschen jederzeit, mit Rückfrage; die Fixbuchung bleibt.

## 14. Dokumentation

Persönliche Arbeitsnotizen zu Patienten, **keine** offizielle Behandlungsdokumentation (Patientenakte nach §630f BGB): frei änderbar und löschbar, ohne Aufbewahrungsfrist. Es sind Gesundheitsdaten (Art. 9 DSGVO), daher ist die Dokumentation eine Insel mit eigener Sperre. Erreichbar über Sonstiges (§1).

### Sperre & Verschlüsselung

- **Doku-Passwort** (mindestens 8 Zeichen): beim ersten Öffnen festgelegt, zweimal eingegeben, mit dem Hinweis, dass es sich nicht wiederherstellen lässt. **Fingerabdruck** optional als Abkürzung, das Passwort als Ersatz; die Geräte-PIN gilt nicht. Ein am Handy neu eingerichteter Fingerabdruck verlangt einmal das Passwort.
- Eigene, mit SQLCipher verschlüsselte Datenbank. Ihr Schlüssel lässt sich nur mit dem Doku-Passwort oder dem Fingerabdruck öffnen, nicht mit dem Schlüssel der übrigen App; ohne beides kommt niemand an die Daten.
- Sperrt sich **sofort** beim Verlassen (anderer Tab, zurück zu Sonstiges), wenn die App in den Hintergrund geht oder der Bildschirm ausgeht, und nach **5 Minuten** ohne Eingabe. Was gerade getippt wird, wird vorher gespeichert.
- Doku-Passwort ändern und Fingerabdruck an/aus im Menü der Dokumentation, nicht in den Einstellungen.

### Insel

- Nicht in der Suche über alle Bereiche, nicht in Heute, keine Pins, gemeinsamen Tags oder Verknüpfungen, keine Erinnerungen oder Benachrichtigungen, nicht in Statistik und Heatmap.
- Die Tastatur wird gebeten, in der Dokumentation keine Wörter zu lernen.
- Kopierter Text ist als vertraulich markiert (keine Vorschau) und wird beim Sperren aus der Zwischenablage gelöscht.
- Kein Export, auch nicht je Patient (entschieden am 08.11.2026); nur das Backup (§10) verlässt das Gerät, in der eigenen Verschlüsselung.

### Startseite

- Oben ein **Löschhinweis**, sobald Patienten seit über 12 Monaten abgeschlossen sind („3 Personen seit über 12 Monaten abgeschlossen – jetzt löschen?“); nur hier, keine Benachrichtigung
- Eigene **Suche** über Patienten, ihre Felder, Termin-Dokumentationen und Bemerkungen
- Die **aktiven Patienten**, zuletzt behandelte zuerst (umschaltbar auf Name), je mit dem letzten Termin; „+ Patient“
- Menü: Tests · Bögen · Termin-Vorlage · Archiv · Doku-Passwort ändern · Fingerabdruck

### Patienten

| Feld | Details |
|---|---|
| Name / Kürzel | Pflicht, freier Text |
| Geburtsjahr | Optional, nur das Jahr |
| Diagnose | Optional, Markdown |
| Anamnese | Optional, Markdown |
| Therapeutische Zielsetzung | Optional, Markdown |
| Sonstiges | Optional, Markdown |
| Status | Aktiv oder abgeschlossen |

- Die Seite eines Patienten hat vier Reiter: **Übersicht** (die Felder) · **Termine** · **Tests** · **Bögen**.
- **Abschließen**: ins Doku-Archiv, nur zum Lesen, wiederherstellbar.
- **Endgültig löschen** mit Rückfrage, mit allen Terminen und Messungen; die Datenbank überschreibt Gelöschtes. In älteren Backups steckt es noch: die nächtlichen fallen nach 7 Nächten weg, die von Hand gespeicherten bleiben, bis man sie löscht.

### Termine

- Datum (Pflicht, Standard heute), Uhrzeit optional und **eine** Dokumentation als Markdown
- Optionale **Termin-Vorlage**, zentral angelegt, füllt neue Termine vor; im Termin frei änderbar
- Im Reiter Termine die neuesten oben, mit Datum und der ersten Zeile als Vorschau
- Termine sind nur Daten, an denen dokumentiert wird: keine Erinnerungen.

### Tests

- **Zentral angelegt**. Ein Test hat einen oder mehrere **Werte**, optional eine **Summe** daraus.
- Je Wert: Name, Einheit (optional), erlaubter Bereich (optional), Nachkommastellen ja/nein, **Richtung** (höher oder niedriger ist besser); die Reihenfolge ist frei.
- Nur Zahlenwerte; je Messung eine optionale Bemerkung.
- Änderungen an einem Test gelten auch für seine alten Messungen (Hinweis mit ihrer Anzahl). Ein Wert, der schon gemessen wurde, wird beim Entfernen stillgelegt statt gelöscht. Ein Test lässt sich archivieren (für neue Messungen nicht mehr wählbar) oder mit allen Messungen endgültig löschen.
- **Messungen** immer an einem Termin, mit dessen Datum; mehrere Tests je Termin.
- **Entwicklung** im Reiter Tests: je gemessenem Test eine Karte mit dem letzten Wert (bzw. der Summe) und der Veränderung zur ersten Messung, besser oder schlechter nach der Richtung. Der Verlauf zeigt je Wert ein Liniendiagramm über die Zeit und darunter alle Messungen als Tabelle; eine Zeile öffnet ihren Termin. Kein Vergleich zwischen Patienten.

### Assessmentbögen

Abgestimmt im Interview vom 08.11.2026.

- **Eingebaut, nicht änderbar**: die Bögen aus der Kategorie „Scores“ der Downloads von DIGOTOR (digotor.info), übernommen wird ihr Inhalt (Items, Antworten, Punkte, Auswertung), nicht die PDFs. Keine eigenen Bögen; ein neuer Bogen kommt mit einem App-Update. Weggelassen werden Fachtexte (Artikel, Schlafhygiene, Nachbehandlung und Expertenkonsens Spinalstenose), Varianten (Word, mit DIGOTOR-Logo) und der englische Anamnesebogen.
- Der Wortlaut der Bögen steht im Quellcode; das Repo ist deshalb **privat**.
- **Bausteine**: Antwort-Items mit festen Antworten und Punkten; ohne Punkte Ja/Nein, Skala (z. B. 0–10, auch in Schritten wie 0, 10 … 100), „Nicht zutreffend“ als Antwort, die wie keine zählt, Ankreuzliste, Freitext und **Körperregionen** zum Ankreuzen mit Freitext (statt „bitte einzeichnen“).
- **Auswertung wie im Original**: Summe, Prozent, Teilskalen, umgekehrt gepolte Items, Regeln für fehlende Antworten und die Einstufung als Text; live im Bogen. Reicht das Beantwortete nach diesen Regeln nicht, „Ergebnis: –“. Die Richtung (höher oder niedriger ist besser) je Ergebnis kommt aus dem Bogen.
- **Am Termin**: „+ Bogen“ unter den Tests; ein Bogen hat das Datum seines Termins, ein Termin kann mehrere haben. Ändern, mit Rückfrage löschen; ein gelöschter Termin nimmt seine Bögen mit. Beim abgeschlossenen Patienten nur zum Lesen. Ausgefüllt wird nur in der eigenen Hand, kein Patientenmodus zum Weiterreichen des Handys.
- **Auswahl** bei „+ Bogen“: nach Unterkategorie wie bei DIGOTOR, Suche nach Name und Abkürzung, oben „Zuletzt benutzt“ (die letzten 5). Ein Bogen in mehreren Kategorien ist derselbe Bogen. Unter **Bögen** im Menü lassen sich Bögen ausblenden.
- **Reiter Bögen** beim Patienten: je Bogen mit Ergebnis eine Karte wie bei den Tests (letztes Ergebnis, Veränderung zur ersten Ausfüllung, besser oder schlechter); der Verlauf mit einem Liniendiagramm je Ergebnis (bei Teilskalen je Teilskala) und einer Tabelle, eine Zeile öffnet den Bogen am Termin. Darunter die Bögen ohne Ergebnis nach Datum.
- Die Doku-Suche findet die Freitexte der Bögen; Bögen kommen ins Backup; es gelten die Regeln der Insel.

## 15. Sprachen

Sprachunterricht festhalten: gehaltene Sessions mit Zeit, Lehrer, Themen und Notizen, dazu die Themen, die bis zu einer Prüfung noch dran sind. Gedacht für Unterricht über italki, aber für jeden Unterricht brauchbar. Erreichbar über Sonstiges (§1). Die App holt nichts ab (§9: kein Internet); alles wird von Hand eingetragen. Hinter der App-Sperre wie alles andere, ohne eigene Sperre.

### Sprachen & Lehrer

- **Sprachen**: Name (Pflicht, eindeutig). Anlegen, umbenennen, archivieren; eine archivierte wird nicht mehr angeboten, ihre Sessions bleiben.
- **Lehrer**: Name (Pflicht), optionale Notiz, mindestens eine Sprache, die sie unterrichten. Anlegen, ändern, archivieren wie Sprachen. Eine weggenommene Sprache lässt die alten Sessions, wie sie sind; der Lehrer wird für sie nur nicht mehr angeboten.
- Beides lässt sich im Session-Editor nebenbei anlegen und über das Menü der Startseite verwalten.

### Sessions

Nur gehaltene Sessions; geplant wird mit To-dos und Routinen.

| Feld | Details |
|---|---|
| Datum | Pflicht, Standard heute; keine Uhrzeit |
| Dauer | Pflicht, in Minuten; Schnellwahl 30 / 45 / 60 / 90 und freie Eingabe |
| Sprache | Pflicht |
| Lehrer | Pflicht; angeboten werden die Lehrer der gewählten Sprache, dazu „Neuer Lehrer“ (gleich mit dieser Sprache) und „Anderen Lehrer wählen“ (gibt einem vorhandenen die Sprache dazu) |
| Preis | Optional, Euro mit Cent; nur für die Auswertung, keine Buchung im Haushaltsbuch |
| Themen | Die behandelten Themen: aus der Liste der Sprache gewählt oder neu getippt |
| Notiz | Optional, Markdown wie in den Notizen (§7), bis 100 000 Zeichen |
| Anhänge | Bis zu 10 Fotos und PDFs |

- Ändern und, mit Rückfrage, löschen; eine gelöschte Session nimmt ihre Anhänge mit.
- Keine Tags, nichts in Heute, keine Erinnerungen.

### Import & Anhänge

- **Textdatei**: Ihr Text wird an die Notiz der Session angehängt und ist danach wie getippter Text. Was die Notiz über 100 000 Zeichen brächte, wird abgelehnt.
- **Foto und PDF** werden Anhänge: über den Dateiwähler des Handys gewählt, ein Foto auch mit „Foto aufnehmen“ über die Kamera-App des Handys, ohne Kamera-Berechtigung (§11). Das so aufgenommene Foto liegt als einzige Ausnahme von §9 unverschlüsselt im Speicher der App, und nur bis die App es eingelesen hat; gleich danach wird es gelöscht. Fotos werden auf höchstens 2000 px an der langen Kante verkleinert; ein PDF darf höchstens 10 MB haben.
- Anhänge liegen verschlüsselt im Speicher der App (§9) und werden in der App angezeigt: Fotos mit Zoom, PDFs seitenweise. Einzeln löschbar; „Exportieren“ speichert eine Kopie über den Dateiwähler. Kein Teilen an andere Apps, keine Texterkennung.
- Anhänge stehen im Backup (§10).

### Themen & Prüfungen

- **Themen** gehören zu einer Sprache und sind optional einer Prüfung zugeordnet. Anlegen in der Themenliste oder beim Tippen in einer Session; ein dort neu getipptes landet offen und ohne Prüfung auf der Liste.
- Ein Thema ist **offen** oder **abgehakt**. Abgehakt wird nur von Hand, und es lässt sich wieder öffnen; dass eine Session es behandelt hat, hakt es nicht ab. Das Thema zeigt, in wie vielen Sessions es dran war und wann zuletzt.
- **Prüfungen**: je Sprache beliebig viele, mit Name und Datum. Die nächste anstehende steht oben mit den Tagen bis dahin und den offenen Themen („noch 43 Tage · 12 von 30 offen“).
- Eine Prüfung wird von Hand als **abgelegt** markiert, optional mit einem Ergebnis als Freitext; zurücknehmen lässt es sich. Ihre Themen bleiben bei ihr, nur zum Nachlesen; offene lassen sich einzeln oder alle zusammen zu einer anderen Prüfung verschieben.
- **Ziel**: Eine Prüfung kann an höchstens einem Ziel hängen (§6), gewählt im Editor der Prüfung. Dort ist sie ein Teil wie ein To-do, mit eigener Zeile („Prüfung: Arabisch A2 · 18 von 30 Themen“): Ihr Fortschritt ist der Anteil abgehakter Themen (ohne Themen 0 %), erledigt ist sie, sobald sie abgelegt ist. Nimmt man „abgelegt“ zurück, öffnet das ein dadurch erledigtes Ziel wieder. Eine gelöschte Prüfung verschwindet aus dem Ziel; wird das Ziel gelöscht, bleibt die Prüfung ohne Ziel.

### Auswertung & Aufbau

- **Startseite**: oben Stunden und Anzahl Sessions für diesen Monat, dieses Jahr und insgesamt; darunter je Sprache eine Karte mit ihren Zahlen, ihrer nächsten Prüfung und, sobald sie Karteikarten hat, „Heute fällig: 23“ mit „Üben“ (unten, Vokabeltrainer); „+“ legt eine Session an.
- **Seite einer Sprache** mit vier Reitern: **Sessions** (nach Datum, filterbar nach Lehrer), **Themen** (nach Prüfung gruppiert, mit Abhaken), **Karten** (Vokabeltrainer) und **Auswertung** (Stunden, Sessions und, wo Preise stehen, Kosten je Lehrer, für Monat, Jahr und insgesamt). Unterrichtet ein Lehrer mehrere Sprachen, zählt jede Sprache für sich.
- Keine Diagramme.
- Sessions kommen in die Backups und in die Suche (§8): Notiz, Themen und Name des Lehrers.

### Vokabeltrainer

Karteikarten je Sprache, abgefragt in wachsenden Abständen, die der Algorithmus FSRS-6 plant (Formeln, Parameter und Testwerte: `docs/research/spaced-repetition.md`).

| Feld | Details |
|---|---|
| Vorderseite | Pflicht, bis 200 Zeichen: das Wort oder der Satz in der Sprache |
| Rückseite | Pflicht, bis 500 Zeichen: die Übersetzung |
| Umschrift | Optional |
| Notiz | Optional |
| Thema | Optional, aus den Themen der Sprache |

- **Planung**: FSRS-6 mit seinen 21 Standardparametern und einer gewünschten Behaltensquote von 90 %, in ganzen Kalendertagen, ohne Lernschritte in Minuten und ohne zufällige Streuung; selbst gerechnet, ohne fremde Bibliothek. Je Karte merkt sich die App Stabilität, Schwierigkeit, den Tag der letzten Abfrage und die Fälligkeit, dazu ein Protokoll jeder Abfrage. **Sicher** ist eine Karte, deren zuletzt geplanter Abstand mindestens 21 Tage beträgt.
- **Abfrage**: Die Karte zeigt die Vorderseite; „Aufdecken“ zeigt den Rest. Vier Antworten: **Nochmal · Schwer · Gut · Leicht**, jede mit dem Abstand, den sie ergäbe. „Schwer“ heißt gewusst, aber mit Mühe; nicht gewusst ist „Nochmal“. Nach „Nochmal“ kommt die Karte in derselben Runde wieder.
- **Tagespensum** einer Sprache: alle fälligen Karten und dazu höchstens 10 neue am Tag (je Sprache einstellbar, 0–50). Geschafft ist es, wenn nichts davon mehr offen ist.
- Abgefragt wird nur von der Vorderseite zur Rückseite. Keine Tonaufnahmen, keine Bilder.
- **Anlegen**: von Hand; aus einer Session-Notiz (Zeilen mit „ – “, „ = “ oder Tabulator werden als Vorder- und Rückseite vorgeschlagen, zum Ankreuzen); aus einer Textdatei über den Dateiwähler (je Zeile Vorderseite, Rückseite, Umschrift, Notiz, getrennt mit Tabulator oder Semikolon, bis 2 000 Zeilen). Eine Karte, deren Vorderseite es in der Sprache schon gibt, wird übersprungen und gezählt.
- **Reiter Karten** auf der Seite einer Sprache: alle Karten mit ihrer nächsten Fälligkeit und der Marke „sicher“, filterbar nach Thema; ändern und löschen.
- **Routine**: Eine Routine kann „erledigt durch Vokabeln“ einer Sprache sein (§2): Ist deren Tagespensum geschafft, ist die offene Fälligkeit der Routine von heute erledigt. Ist an dem Tag nichts fällig und nichts neu, bleibt die Routine offen.
- **Karten-Ziel**: An einem Ziel (§6) hängt optional „N sichere Karten“ einer Sprache, mit eigener Zeile („Karten: Arabisch · 412 von 1 000“). Der Fortschritt ist ihr Anteil; erreicht, ist der Teil erledigt, wie eine abgelegte Prüfung.
- **Themen**: Ein Thema zeigt „12 Karten · 5 sicher“, die nächste Prüfung die Summe ihrer Themen. „Thema üben“ fragt alle Karten des Themas ab; jede Antwort zählt wie sonst, der Algorithmus rechnet ein, wenn eine Karte früh kommt.
- Karten einer archivierten Sprache bleiben, werden aber nicht abgefragt. Karten kommen in die Backups und in die Suche (§8).

## 16. Eingang

Ein Gedanke soll in der App landen, bevor er weg ist; sortiert wird später.

- **Festhalten**: Die Zeile oben in Heute („Was geht dir durch den Kopf?“) nimmt einen Text, Enter legt ihn im Eingang ab. Kein Formular, kein Speichern-Knopf. Bis 1 000 Zeichen.
- **Eingang**: Liegt etwas darin, zeigt Heute unter der Zeile „Eingang (3)“; Antippen öffnet die Liste, das Neueste oben.
- **Einsortieren**: Jeder Eintrag bietet „To-do · Notiz · Routine · Löschen“. Die ersten drei öffnen den bekannten Editor, die erste Zeile des Textes als Titel bzw. Name, der Rest als Beschreibung bzw. Text. Der Eintrag verschwindet erst, wenn dort gespeichert ist. Löschen mit „Rückgängig“.
- **Kurzbefehl**: Langes Drücken auf das App-Symbol bietet „Neuer Gedanke“: Die App öffnet, nach ihrer Sperre, eine schlanke Eingabe mit offener Tastatur, über dem, was offen war.
- **Ohne Entsperren**: Optional (Einstellungen, Standard aus) steht dauerhaft eine stille Benachrichtigung „Gedanke festhalten“ mit Antwortfeld. Was dort getippt wird, landet im Eingang. Sie kann nur schreiben: Sie zeigt nie, was im Eingang liegt, auch nicht das eben Getippte. Auf dem Sperrbildschirm verlangt Android vorher die Entsperrung des Handys, nicht die der App.
- Keine Erinnerungen, keine Tags, keine Verknüpfungen. Der Eingang kommt in die Backups und in die Suche (§8).

## 17. Tracking

Ein eigenes Tagebuch zu Befinden, Puls, Blutdruck und Anstrengung, das Muster sammelt und für Arzttermine aufbereitet. Es stellt **keine Diagnose**, sagt nie „Du hast …“ und gibt keine Anweisungen zu Medikamenten. Es sind Gesundheitsdaten (Art. 9 DSGVO), daher eine Insel wie die Dokumentation (§14). Erreichbar über Sonstiges (§1). Tracking ist die erste Stufe des Tages-Check-ins; HRV, Stability und Monatsfragebogen folgen als eigene Etappe ([ohne-ticket.md](docs/issues/ohne-ticket.md) §1).

**Grundsatz für das öffentliche Repo:** Code, Spezifikation und Tickets beschreiben nur die allgemeine Funktion. Persönliche Werte, Medikamente oder Beschwerden stehen nie im Repo, auch nicht als Beispiel oder Testdaten; Tests benutzen erfundene Werte und Namen wie „Medikament A“.

### Sperre & Insel

- **Tracking-Passwort** (mindestens 8 Zeichen) und optional Fingerabdruck, unabhängig vom Doku-Passwort; eigene SQLCipher-Datenbank, deren Schlüssel nur Passwort oder Fingerabdruck freigeben.
- Sperre, Insel-Regeln und Überschreiben gelöschter Daten wie §14: sofort beim Verlassen, im Hintergrund, bei ausgehendem Bildschirm und nach 5 Minuten ohne Eingabe; nicht in Suche, Heute, Statistik oder Benachrichtigungen; keine Verknüpfung mit Routinen oder anderen Bereichen; Tastatur ohne Lernen; Zwischenablage vertraulich.
- **Ausnahme Messassistent:** Solange ein Stehtest oder eine Ereignismessung läuft, bleibt der Bildschirm an und die 5-Minuten-Sperre ruht. Hintergrund und Bildschirm aus sperren weiter; die Werte bis dahin bleiben erhalten.
- **Erinnern** tut Tracking nicht. Wer erinnert werden will, legt eine normale Routine an (z. B. „Abendeintrag“); sie weiß nichts von Tracking.
- **Backup** in eigener Verschlüsselung wie die Dokumentation (§10); einspielen braucht das Tracking-Passwort, das beim Backup galt. Übernommene Garmin-Werte sind als Kopie im Backup.
- **Export** nur der Arzt-Zusammenfassung als PDF, nur von Hand (unten).
- **Alles löschen** im Menü, mit Rückfrage und Eingabe des Passworts.

### Startseite

- Ganz oben **„Akute Beschwerden?“**: die Warnzeichen (unten); „112 anrufen“ öffnet die Telefon-App mit der Nummer, ohne Berechtigung.
- Darunter der heutige Morgen- und Abendeintrag (offen/erledigt), „+ Stehtest“, „+ Ereignis“, „+ Anstrengung“, „+ Episode“ und offene Vorschläge (Anstrengung, laufende Episode).
- **Hinweise**, **Wochenauswertung**, **Verlauf**.
- Menü: Symptome · Medikamente · Grunddaten · Pacing-Schwelle · Garmin · Arzt-Zusammenfassung · Passwort ändern · Fingerabdruck · Alles löschen.

### Tageseinträge

- **Skala** für Symptome und Belastung: 0 = nicht vorhanden, 1 = leicht, 2 = mittel, 3 = schwer. Leer heißt „nicht erfasst“ und zählt nie als 0.
- **Morgens:** Ruhepuls im Liegen, Blutdruck (optional), Schlafdauer, Schlafqualität 0–3 (0 = sehr gut), erholt aufgewacht ja/nein; Hinweis „nach 5 Minuten ruhig liegen, vor Koffein und Medikamenten – sonst vermerken“.
- **Abends:** jedes aktive Symptom 0–3; Belastung körperlich, kognitiv, emotional, sozial 0–3; Arbeitstag, Hitze oder langes Stehen ja/nein; Trinkmenge in Litern, Mahlzeiten, Koffein und Alkohol als Anzahl; je Medikament „wie geplant“ (vorausgewählt) oder abweichend (Uhrzeit oder vergessen); „Crash oder PEM?“ als schneller Einstieg in eine Episode (unten); Notiz (Markdown).
- **Symptome** im Menü: Startliste Erschöpfung, Konzentration, Schlafbedürfnis, Kopfschmerz, Muskelschmerzen, Gelenkschmerzen, Muskelschwäche, Atemnot, Herzrasen, Schwindel im Stehen, Übelkeit, Hautveränderungen, Kälteempfinden, Hitzeempfinden, Stimmung gedrückt, Angst/innere Unruhe; umbenennen, sortieren, ausblenden, eigene ergänzen. Ausgeblendete behalten ihre Werte.
- **Tag:** Bis 04:00 schlägt ein neuer Eintrag den Vortag vor; das Datum lässt sich im Eintrag ändern.
- **Nachtragen** bis 7 Tage zurück. Später als am Folgetag Erfasstes ist still als nachgetragen markiert; die Auswertung zeigt den Anteil.

### Grunddaten & Medikamente

- **Grunddaten:** feste Markdown-Felder mit Leitfragen als Platzhalter: Verlauf (Beginn, Schübe, Infekte oder Belastungen davor, was früher ging), Familie (Thrombosen, Herz, Autoimmunerkrankungen), Schlaf, Ernährung und Substanzen, Sonstiges.
- **Medikamente:** frei eingegeben, ohne Vorschlagsliste: Name, Darreichung, Einnahmezeiten, wofür (optional); eine **Zeitleiste** mit Beginn, Dosisänderungen (Datum, alt → neu) und Ende; Schalter „erhöht laut Beipackzettel das Thromboserisiko“ und „senkt den Puls“; Freitext „Nebenwirkungen, auf die ich achte“.
- Überall der Hinweis: Medikamente nur mit den Behandelnden ändern oder absetzen.

### Garmin über Health Connect

- Liest aus Health Connect, was Garmin Connect dorthin schreibt (ab Android 14): Herzfrequenz, Schlaf, Schritte und, falls vorhanden, Ruhepuls. Nur lesend und nur, solange Tracking entsperrt offen ist; keine Berechtigung für das Lesen im Hintergrund. Beim ersten Einrichten einmal die Berechtigung für ältere Daten, sonst gibt Health Connect nur 30 Tage.
- Die Werte werden als Kopie in der Tracking-Datenbank gespeichert, mit Quelle (App, Gerätetyp) und Zeitpunkt; ein erneutes Lesen ergänzt, ohne zu verdoppeln.
- **„Woher kommen meine Werte?“** im Menü Garmin: je Datentyp die Quellen mit Gerätetyp (Uhr, Brustgurt, unbekannt), Anzahl und letztem Wert.
- **Stufen** nach Aussagekraft; jeder Hinweis nennt die Stufe, die ihn trägt:

| Stufe | Wirkung | Werte |
|---|---|---|
| A – gemessen, gut überprüft | kann einen Hinweis auslösen wie die eigenen Messungen | Herzfrequenz in Ruhe; Herzfrequenz vom Brustgurt (Gerätetyp in Health Connect), auch bei Bewegung; Schritte |
| B – abgeleitet, teilweise überprüft | stützt einen Hinweis, löst allein nichts aus | Herzfrequenz vom Handgelenk oder unbekannter Quelle bei Bewegung; Ruhepuls von Garmin; Nacht-HRV; Schlafdauer |
| C – eigene Garmin-Berechnung oder schwach überprüft | nur in Verlauf und PDF, gekennzeichnet als „Garmin-Schätzung“ | HRV-Status, Schlafphasen, Sleep Score, Intensitätsminuten, Stress, Body Battery, SpO₂ |

- „In Ruhe“ heißt: keine Schritte in der Minute davor und danach, oder ein Stehtest. Die Zuordnung steht mit ihren Quellen an einer Stelle im Code. [GARMIN-HF] [GARMIN-HRV] [GARMIN-SCHLAF] [GARMIN-SCHRITTE] [GARMIN-SCORES]
- Der Ruhepuls von Garmin ist eine eigene Kurve neben dem Morgenwert. Regeln mit Schwellen aus Leitlinien nutzen nur den Morgenwert.
- HRV, Stress, Body Battery und SpO₂ liefert Garmin nicht über Health Connect; sie kommen mit dem Import der FIT-Dateien in der HRV-Etappe.
- Datenwege, Validierung je Wert und Pacing-Schwellen mit Quellen: [garmin-health-connect.md](docs/research/garmin-health-connect.md).

### Stehtest

- **Varianten:** „Stehen“ mit Messungen nach 1, 3, 5, 8 und 10 Minuten oder „Anlehnen“ (NASA-Lean-Test: Schulterblätter an der Wand, Fersen etwa 15–20 cm davor) mit einer Messung je Minute. [CMAJ-POTS-2022] [LEE-2020] [DGN-SYNK-2020]
- **Vorher** eine Prüfliste zum Abhaken: zweite Person dabei, neben Bett oder Sofa, heute keine Warnzeichen, nicht direkt nach heißem Duschen, Alkohol oder großer Mahlzeit, Medikamente wie gewohnt genommen.
- 5–10 Minuten liegen mit Timer, dann Puls und Blutdruck im Liegen; dann aufstehen. Zu jedem Zeitpunkt Ton und Vibration in der App, Eingabe von Puls, Blutdruck (optional), Schwindel, Herzrasen, Atemnot 0–3 und Verfärbung der Beine ja/nein.
- Ein großer Knopf **„Abbrechen – hinlegen“** jederzeit; gespeichert wird bis dahin, mit Minute und Grund.
- Gespeichert werden außerdem Uhrzeit, Stunden seit den heute eingetragenen Einnahmen und Koffein, Mahlzeit und Trinkmenge davor.
- Liegt eine Herzfrequenz von Garmin für den Zeitraum vor, zeigt der Test sie als **Zusatzkurve** (Stufe A); die eigenen Messpunkte bleiben die Hauptwerte. Fehlt ein Messpunkt, füllt ihn die Kurve, gekennzeichnet.
- **Auswertung** je Test: größter Pulsanstieg gegenüber dem Liegen, Messungen mit Anstieg ≥ 30/min, Mittel der letzten drei Minuten (Anlehnen), größter Abfall systolisch/diastolisch innerhalb von 3 Minuten und danach, systolisch < 90 mmHg.
- Auf dem Ergebnis steht, dass ein Heimtest keine ärztliche Untersuchung ersetzt und Medikamente das Ergebnis verändern.

### Ereignisse

Für Situationen, die Beschwerden auslösen (Duschen, Baden, Sauna, Hitze, langes Stehen, Sonstiges):

- Wassertemperatur lauwarm/warm/heiß, Dauer, stehend/sitzend.
- Puls vorher, direkt danach und nach 5 und 10 Minuten mit Timer; Sauerstoffsättigung optional; Atemnot und Schwindel 0–3; hinsetzen nötig (nach wie vielen Minuten).
- **Haut:** wo (Beine, Arme, Rumpf, Finger oder Zehen), Muster (gleichmäßig-geschlossen oder unterbrochen-netzartig), schmerzt ja/nein, verschwindet im Liegen ja/nein, Minuten bis zur Rückbildung; Minuten bis wieder belastbar.
- Herzfrequenz von Garmin von 15 Minuten davor bis 30 Minuten danach als Zusatzkurve, mit ihrer Stufe.
- **Fotos:** aufgenommen mit der Kamera-App (ohne Kameraberechtigung), sofort verschlüsselt in Tracking abgelegt, nie in der Galerie; im Backup.

### Anstrengungen, PEM & Crash

- **Anstrengung:** Datum, Uhrzeit, Art (körperlich, kognitiv, emotional, sozial, Stehen/Wärme), Dauer, Stärke 0–3. Sie entsteht auf vier Wegen:
  - von Hand mit „+ Anstrengung“;
  - **direkt aus dem Abendeintrag:** Belastung körperlich, kognitiv, emotional oder sozial ≥ 2 gilt als Anstrengung des Tages, mit dieser Art und Stärke;
  - als **Vorschlag aus den Schritten**, wenn ein Tag mehr als 150 % des Mittels der letzten 14 Tage hat;
  - als **Vorschlag aus der Herzfrequenz**, wenn die Minuten über der Pacing-Schwelle mehr als 150 % des Mittels der letzten 14 Tage betragen.
  Vorschläge erscheinen im Abendeintrag und auf der Startseite; bestätigt werden sie eine Anstrengung, verworfene kommen nicht wieder. Die 150 % und die 14 Tage sind eigene Arbeitswerte.
- **Pacing-Schwelle** im Menü: ein eigener Wert in Schlägen/min, z. B. die anaerobe Schwelle aus einer Spiroergometrie (am besten vom zweiten Testtag). Ohne eigenen Wert gilt das Mittel der Morgen-Ruhepulse der letzten 7 Tage + 15, gekennzeichnet als „Faustregel, schwach belegt“. [WORKWELL-15] [KELLER-2024] Die Minuten über der Schwelle erzeugen nur Vorschläge, nie einen Hinweis. Ohne eigenen Wert und mit einem Medikament „senkt den Puls“ steht dabei: „Medikamente, die den Puls senken, verschieben die Schwelle; eine individuelle Schwelle (Spiroergometrie) ärztlich erfragen.“ [DIAZ-BUSCHMANN-2014]
- **Folgetage:** Nach einer Anstrengung der Stärke 2–3 fragen die Abendeinträge der folgenden drei Tage „Schlechter als vor der Anstrengung?“.
- **Episode** („+ Episode“ oder „Crash oder PEM?“ im Abendeintrag): Art PEM (spürbare Verschlechterung) oder Crash (schwer, Tag weitgehend abgebrochen); Beginn mit Datum und ungefährer Uhrzeit; Ende (solange offen „läuft noch“); Schwere 1–3; **vermutete Auslöser:** Anstrengungen der letzten 3 Tage vor dem Beginn auswählen oder direkt neu anlegen; je Auslöser die Sicherheit sicher, wahrscheinlich oder unklar. Die App zeigt den Abstand „Beginn x Stunden nach der Anstrengung“.

### Wochenauswertung & Verlauf

- Je Woche (Mo–So): Ruhepuls am Morgen Mittel, Minimum, Maximum und Tage über 100/min; Stehtests mit größtem Anstieg, Anzahl mit Anstieg ≥ 30/min in mindestens zwei Messungen, Anzahl mit Abfall ≥ 20/10 mmHg innerhalb von 3 Minuten und später; Ereignisse mit Atemnot ≥ 2 und mit Hautveränderung, mittlere Rückbildungszeit; Anstrengungen und Episoden mit mittlerem Abstand zum Beginn; Mittel je Symptom; Tage mit weniger als 1,5 l Trinkmenge (eigener Arbeitswert, keine Leitlinie); Nächte mit Schlafqualität ≥ 2; Abweichungen bei Medikamenten; Schritte und Minuten über der Pacing-Schwelle; Anteil nachgetragener Einträge.
- **Verlauf:** je Symptom, für den Ruhepuls am Morgen und die Garmin-Werte ein Liniendiagramm über 4 oder 12 Wochen, wie die Diagramme der Dokumentation (§14); Medikamentenänderungen als senkrechte Markierung, Episoden als Band; Werte der Stufe C sind als „Garmin-Schätzung“ beschriftet.

### Hinweise

Regeln, die aus den Einträgen Muster erkennen und den nächsten Schritt für das Arztgespräch vorschlagen. Jeder Hinweis nennt das Muster („passt zu“, „spricht für“), worauf er sich stützt (Anzahl Einträge, Zeitraum, Stufe der Garmin-Werte), was ärztlich als Nächstes sinnvoll ist (Fachrichtung und Frage) und seine Quelle. Fehlen Daten, sagt er, wie viele noch („noch 2 Stehtests“). Werte der Stufe A zählen wie eigene Messungen, Stufe B nur als Stütze eines Hinweises, der schon ausgelöst ist, Stufe C nie. Die Regeln sind reine Logik ohne Android, mit Schwellen und Quellen an einer Stelle.

- **Mindestdaten** (eigene Festlegung): 14 Tage Einträge, 3 Stehtests an verschiedenen Tagen, 5 Ereignisse, 3 Anstrengungen der Stärke 2–3 mit den Tagen danach.
- **Immer:** Erinnerung an die Basisuntersuchungen (EKG mit QTc, Elektrolyte), abhakbar, wenn erledigt. [HRS-2015] [NIH-POTS-2021]
- **Atemnot** bei Belastung oder Wärme an mindestens 3 Tagen und ein Medikament mit Thromboserisiko → zeitnah ärztlich die Wahrscheinlichkeit einer Lungenembolie klären lassen. [ESC-LE-2019]
- **Ruhepuls am Morgen** an der Mehrzahl der Tage über 100/min → Langzeit-EKG, Abklärung einer inadäquaten Sinustachykardie. [HRS-2015]
- **Medikamente:** Verschlechterung innerhalb von 6 Wochen (eigener Arbeitswert) nach Beginn oder Erhöhung eines Medikaments → mit den Verordnenden besprechen, nichts selbst ändern.
- **Stehtest, Pulsanstieg:** in mindestens 2 von mindestens 3 Tests ≥ 30/min in mindestens zwei Messungen innerhalb von 10 Minuten (beim Anlehnen: Mittel der letzten drei Minuten > 30/min über dem Liegen oder > 120/min), ohne Abfall ≥ 20/10 mmHg, Liegendpuls ≥ 60/min, mit Beschwerden im Stehen → passt zu einem POTS-Muster; Kardiologie oder autonome Ambulanz, ärztlicher Stehtest über 10 Minuten, bei negativem Ergebnis Kipptisch; Medikamente können die Ursache sein. [NIH-POTS-2021] [CMAJ-POTS-2022] [DGN-SYNK-2020] [LEE-2020]
- **Stehtest, Blutdruckabfall** ≥ 20 mmHg systolisch, ≥ 10 mmHg diastolisch oder systolisch < 90 mmHg innerhalb von 3 Minuten → passt zu orthostatischer Hypotonie, Medikamente prüfen lassen; erst danach → verzögerte Form, Kipptisch erfragen. [ESC-SYNK-2018] [DGN-SYNK-2020]
- **Stehtest, Beschwerden ohne Messauffälligkeit** → morgens wiederholen, ärztlichen Test erfragen. [CMAJ-POTS-2022]
- **PEM:** Verschlechterung an Tag +1 oder +2 nach mindestens 2 von 3 Anstrengungen der Stärke 2–3, oder mindestens 2 Episoden mit einem Auslöser „sicher“ oder „wahrscheinlich“ und Beginn 12–72 Stunden danach → passt zu Verschlechterung nach Belastung (PEM); ME/CFS-Abklärung ansprechen, Belastung nicht austesten. [IOM-2015] [NICE-NG206]
- **Haut:** bleibt über 30 Minuten (eigener Arbeitswert), ist unterbrochen-netzartig oder schmerzt → Dermatologie oder Rheumatologie, Antiphospholipid-Antikörper erfragen; Finger oder Zehen betroffen → Raynaud ansprechen; verschwindet im Liegen rasch und betrifft die Beine → passt zu Blutansammlung in den Beinen, stützt den Stehtest. [SAJJAN-2015]
- **Schlaf:** Schlafqualität ≥ 2 an der Mehrzahl der Nächte → Schlaf ansprechen, auch Schnarchen und Atempausen. [DEGAM-MUEDIGKEIT]
- **Stimmung:** gedrückt oder Angst ≥ 2 an der Mehrzahl der Tage → ansprechen, parallel zur körperlichen Abklärung, nicht danach. [DEGAM-MUEDIGKEIT]
- **Trinkmenge** an der Mehrzahl der Tage unter 1,5 l → ansprechen (eigener Arbeitswert).

**Warnzeichen** (Liste unter „Akute Beschwerden?“): sofort 112 bei plötzlicher oder rasch zunehmender Atemnot, Brustschmerz, Ohnmacht oder Beinahe-Ohnmacht, Bluthusten, Ohnmacht bei Belastung oder im Liegen, Herzrasen vor einer Ohnmacht, plötzlichem Sehverlust, Lähmung, Sprach- oder Gefühlsstörung, Krampfanfall. [ESC-LE-2019] [ESC-SYNK-2018] Zeitnah ärztlich bei einseitiger Beinschwellung oder -schmerz, fortschreitender Atemnot, neuen anhaltenden Kopfschmerzen oder Sehstörungen, Gelbfärbung von Haut oder Augen, Farbveränderungen oder Wunden an Fingern oder Zehen, Hautzeichnung, die bleibt, Ruhepuls dauerhaft über 100/min. [HRS-2015]

### Arzt-Zusammenfassung

- Eine Seite, vorausgefüllt für einen Zeitraum in **Wochen**, frei wählbar von 1 bis zur Zahl der Wochen, die schon getrackt sind (Standard: 4 oder weniger): stärkste Symptome mit Mittelwert, Kennzahlen der Wochenauswertung, Stehtests als Tabelle, Ereignisse, Anstrengungen und Episoden, Medikamente mit Zeitleiste, Garmin-Werte mit ihrer Stufe, die aktuellen Hinweise mit ihren Fragen; eigene Fragen frei (Markdown).
- Vorschau, dann **„Als PDF speichern“** in einen gewählten Ordner (Storage Access Framework), mit Android-eigenen Mitteln ohne neue Bibliothek. Fotos optional (Standard: ohne).
- Vor dem Speichern der Hinweis: Die PDF ist **nicht verschlüsselt**; nach dem Termin löschen.

### Quellen der Schwellen

Die Kürzel verweisen auf Primärquellen; im Code stehen sie mit Fundstelle neben der Schwelle.

| Kürzel | Quelle |
|---|---|
| CMAJ-POTS-2022 | Raj SR, Fedorowski A, Sheldon RS: Diagnosis and management of postural orthostatic tachycardia syndrome. CMAJ 2022; 194: E378–E385. doi:10.1503/cmaj.211373 |
| DEGAM-MUEDIGKEIT | DEGAM: S3-Leitlinie Müdigkeit, AWMF-Register 053-002, Stand 12/2022 |
| DGN-SYNK-2020 | Diehl RR et al.: Synkopen, S1-Leitlinie der DGN, 2020, AWMF-Register 030/072 |
| ESC-LE-2019 | Konstantinides SV et al.: 2019 ESC Guidelines for the diagnosis and management of acute pulmonary embolism. Eur Heart J 2020; 41: 543–603. doi:10.1093/eurheartj/ehz405 |
| ESC-SYNK-2018 | Brignole M et al.: 2018 ESC Guidelines for the diagnosis and management of syncope. Eur Heart J 2018; 39: 1883–1948. doi:10.1093/eurheartj/ehy037 |
| HRS-2015 | Sheldon RS et al.: 2015 Heart Rhythm Society Expert Consensus Statement on the Diagnosis and Treatment of Postural Tachycardia Syndrome, Inappropriate Sinus Tachycardia, and Vasovagal Syncope. Heart Rhythm 2015; 12: e41–e63. doi:10.1016/j.hrthm.2015.03.029 |
| IOM-2015 | Institute of Medicine: Beyond Myalgic Encephalomyelitis/Chronic Fatigue Syndrome: Redefining an Illness. National Academies Press 2015. doi:10.17226/19012 |
| LEE-2020 | Lee J et al.: Hemodynamics during the 10-minute NASA Lean Test: evidence of circulatory decompensation in a subset of ME/CFS patients. J Transl Med 2020; 18: 314. doi:10.1186/s12967-020-02481-y |
| NICE-NG206 | NICE: Myalgic encephalomyelitis (or encephalopathy)/chronic fatigue syndrome: diagnosis and management, NG206, 2021 |
| NIH-POTS-2021 | Vernino S et al.: Postural orthostatic tachycardia syndrome (POTS): State of the science and clinical care from a 2019 National Institutes of Health Expert Consensus Meeting – Part 1. Auton Neurosci 2021; 235: 102828. doi:10.1016/j.autneu.2021.102828 |
| SAJJAN-2015 | Sajjan VV et al.: Livedo reticularis: A review of the literature. Indian Dermatol Online J 2015; 6: 315–321. doi:10.4103/2229-5178.164493 |
| DIAZ-BUSCHMANN-2014 | Díaz-Buschmann I et al.: Programming exercise intensity in patients on beta-blocker treatment. Eur J Prev Cardiol 2014. doi:10.1177/2047487313500214 |
| GARMIN-HF | Gillinov et al.: Variable accuracy of wearable heart rate monitors during aerobic exercise. Med Sci Sports Exerc 2017 (PubMed 28709155); Merrigan et al. 2023, doi:10.1080/1091367X.2022.2161820 (Brustgurt gegen EKG); Casanova-Lizón et al. 2026, doi:10.3390/s26206549 (Ruhe) |
| GARMIN-HRV | Dial et al. 2025, doi:10.14814/phy2.70527 (Nacht-RMSSD gegen EKG); Miller et al. 2022, doi:10.3390/s22166317 |
| GARMIN-SCHLAF | Schyvens et al. 2025, doi:10.1093/sleepadvances/zpaf021 (gegen Polysomnographie) |
| GARMIN-SCHRITTE | Evenson KR, Spade CL: Review of validity and reliability of Garmin activity trackers. J Meas Phys Behav 2020. doi:10.1123/jmpb.2019-0035 |
| GARMIN-SCORES | Siepe et al. 2025, doi:10.1037/abn0001013 (Stress, Body Battery); Rosenbach et al. 2025, doi:10.1002/smi.70125 (Stress); Schiefer et al. 2021, doi:10.3390/s21196363 (SpO₂); Garmin: Accuracy disclaimer, garmin.com/legal/atdisclaimer |
| KELLER-2024 | Keller et al.: Zwei-Tages-Spiroergometrie bei ME/CFS. J Transl Med 2024. doi:10.1186/s12967-024-05410-5 |
| WORKWELL-15 | Faustregel der Workwell Foundation (Ruhepuls + 15); nur eine Studie belegt, laut Erhebung in Work 2023, doi:10.3233/WOR-220512 – Primärdokument offen |

### Entscheidungen

Abgestimmt im Interview vom 15.11.2026.

- Tracking ist die erste Stufe des Tages-Check-ins; HRV, Stability und Monatsfragebogen kommen später in denselben Bereich.
- Eigene Sperre wie die Dokumentation; der Name „Tracking“ verrät auf dem Bildschirm nichts.
- Export nur der Arzt-Zusammenfassung als PDF, Zeitraum in Wochen bis zur getrackten Dauer.
- Nachtragen bis 7 Tage; bis 04:00 der Vortag als Vorschlag.
- Medikamente getrennt von den Routinen; erinnert wird über eine normale Routine.
- Standardwerte und Arbeitswerte fest, als eigene Festlegung gekennzeichnet; Schmerzen getrennt in Muskel- und Gelenkschmerzen.
- PEM und Crash als Episode mit vermuteten Auslösern; Abendbelastung ≥ 2 zählt direkt als Anstrengung, Schritte und Herzfrequenz schlagen vor; Folgetage nur nach Stärke 2–3.
- Garmin über Health Connect jetzt, FIT-Import mit HRV, Stress, Body Battery und SpO₂ in der HRV-Etappe; Gewichtung über drei Stufen statt Zahlen; Brustgurt nach Gerätetyp; Garmin-Ruhepuls neben, nicht statt des Morgenwerts.
- Pacing-Schwelle: eigener Wert, sonst Ruhepuls + 15 als schwach belegte Faustregel; daraus nur Vorschläge.
- Fotos kommen zuletzt.
