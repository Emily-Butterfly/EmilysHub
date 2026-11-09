# Visible: Pacing, HRV und Symptomtracking

- **Untersuchte Version:** Visible `26.10.1` (`versionCode 4151`, Paket `com.makevisible.visible`)
- **Primärmaterial:** vollständiges Base-APK plus ARM64-Split, insbesondere `libapp.so`, `libpacepoints_ml.so` und das ONNX-Modell `v1_1min_opset18.onnx`
- **Analyse:** statische Reverse-Engineering-Analyse und offizielle Visible-Hilfeseiten, Stand 2026-11-09

## 1. Kernaussagen

1. **Pacing besteht bei Visible aus drei getrennten Ebenen:**
   - **PacePoints** messen die tatsächlich akkumulierte Belastung über den Tag.
   - **Stability Score** bewertet morgens die aktuelle körperliche Stabilität auf einer Skala von 1–5.
   - **Daily Budget** kann aus der persönlichen Baseline und der Stability ein tagesaktuelles PacePoints-Budget ableiten.
2. **Die aktuelle App enthält ein lokales neuronales PacePoints-Modell.** Es verarbeitet minutenweise zehn standardisierte Statistikwerte plus zwei Tageszeitmerkmale und erzeugt getrennte Werte für körperliche, nichtkörperliche und gesamte Belastung. Die frühere Base-only-Notiz konnte diesen Pfad noch nicht untersuchen; die nun vorliegenden ARM64-Bibliotheken machen ihn sichtbar. [N1] [N2] [N3]
3. **HRV ist kein direkter Bestandteil der nachweisbaren PacePoints-Ausgabeformel.** HRV fließt nachweislich in den Stability Score ein; dieser kann anschließend das Daily Budget beeinflussen. Ob HRV auch unter den zehn nicht mehr namensaufgelösten Modell-Statistiken vorkommt, ist offen und darf nicht unterstellt werden. [N1] [V-STAB]
4. **Die öffentlich beschriebene Zonenlogik und das native ONNX-Modell sind nicht dieselbe Spezifikation.** Öffentlich beschreibt Visible Rest/Activity/Exertion, ungefähr vierfache Punktrate in Exertion und eine Exponentialkurve. Die aktuelle Binärdatei enthält zusätzlich ein direktes ML-Modell. Die öffentliche Beschreibung darf deshalb nicht als vollständige aktuelle PacePoints-Formel verwendet werden. [V-PP] [V-ZONES] [N2]
5. **Der Stability Score kombiniert vier Faktoren:** Ruhepuls, normierte HRV, Schlafbewertung und die Symptome aus dem vorherigen Abend-Check-in. Fehlt dieser Check-in, wird mit den anderen drei Faktoren gerechnet. [V-STAB] [V-EVENING]
6. **Die exakten Stability-Gewichte, Schwellen und die numerische Stability→Daily-Budget-Abbildung bleiben offen.** `calculateStability` ist als Cloud-Funktion verdrahtet; die App speichert zwar Faktorgrenzen und Scores, enthält aber keine belastbare vollständige öffentliche Aggregationsformel. [D1] [V-NORMAL]
7. **Symptomtracking ist ein täglicher, konfigurierbarer Beobachtungsdatensatz, kein kontinuierliches Symptom-Monitoring.** Es umfasst Symptome, Crash, subjektive Belastungen, Periode, Infektion, Medikamente, Lifestyle und Notiz. Die Symptomwerte des Abends beeinflussen die Stability des folgenden Tages; Crash bleibt ein eigener Faktor. [V-EVENING] [V-ONCE] [V-STAB]

## 2. Beweisklassen und Dateiintegrität

- **[N]** Direkt aus nativem ARM64-Code oder ONNX-Graph rekonstruiert.
- **[D]** Direkt aus Dart-/Flutter-Metadaten, Datenbank-/API-Namen und App-Texten in `libapp.so`.
- **[V]** Offizielle Visible-Produktdokumentation; beschreibt Verhalten, nicht zwingend die aktuelle Implementierung.
- **[OFFEN]** Nicht ausreichend belegt; keine erfundene Ergänzung.

| Datei | SHA-256 |
|---|---|
| `REA/com.makevisible.visible.apk` | `47e319c34a655de463b45700c2675161f48c61e5936e590687a59fc257a3c4e1` |
| `REA/visible-26.10.1/splits/config.arm64_v8a.apk` | `7098ef288529ae795cfc5a92fa132503674303e4e3a326e263d97dbdd552ddc6` |
| `REA/visible-26.10.1/libs/libapp.so` | `0b19345ddd7915d8cbcfbf854eb576ca207c0d5de8cc3bd285c0e957104e73d7` |
| `REA/visible-26.10.1/libs/libpacepoints_ml.so` | `c6d740192f964c35c3fc54b4437b640aee94436bf514144e8a895c14297bd581` |
| ONNX `v1_1min_opset18.onnx` | `89801e2af4630a018383ed8e771f422d8d3bbc10a71e78d65009993024cee9af` |

Die ARM64-Bibliotheken im Split stammen aus demselben XAPK-Set wie die Base-APK. Im Gegensatz zur früheren Base-only-Analyse liegen damit der Flutter-AOT-Snapshot und die native PacePoints-Laufzeit tatsächlich vor.

## 3. Gesamtarchitektur des Pacings

```text
Band-/Sensordaten + Tageszeit
        │
        ▼
Minutenweise Statistik + ONNX-Modell
        │
        ├── physical exertion
        ├── non-physical exertion
        └── total exertion
        │
        ▼  Zeitintegration + persönliche Skalierung
PacePoints / Aktivitätsverlauf / Activity Insights
        │
        ├── Vergleich mit persönlichem Baseline-Budget
        ├── PaceSetter und Benachrichtigungen
        └── optional Daily Budget

Vorheriger Abend: Symptome 0–3, Crash separat
Morgen: Schlaf 1–4 + Ruhe-HR + normierte HRV
        │
        ▼
Stability Score 1–5
        │
        └── kann das Daily Budget senken/erhöhen;
            bei erhöhtem Ruhepuls teilweise Sperrung auf Baseline
```

**Wichtige Begriffsabgrenzung:**

- `PacePoints physical/nonPhysical` sind **Modellausgaben** und nicht die manuell im Evening Check-in bewerteten Belastungsarten.
- `Exertion zone` ist eine **Herzfrequenzzone** und nicht dasselbe wie der numerische Gesamt-Exertion-Score des ONNX-Modells.
- `Symptom severity` ist eine **subjektive Tagesbewertung**, während PacePoints aus Sensordaten entstehen.
- `Daily Budget` ist ein **Richtwert**, keine harte Obergrenze: PacePoints laufen auch nach Überschreiten weiter. [V-DAY]

## 4. PacePoints: aktueller nativer Modellpfad

### 4.1 Native Schnittstelle

`libpacepoints_ml.so` exportiert unter anderem:

- `pm_build_features`
- `pm_infer_rows`
- `pm_score_range`
- `pm_pace_shared_constant`
- `pm_pace_window_days`
- `pm_backend_infer`

Die Kotlin-Schicht ruft:

```text
PacePointsMlNative.scoreRange(
    session,
    stats: double[][],       // 10 Zeitreihen
    localMinutes: long[],
    rowMinutes: double,
    loc: double[10],
    scale: double[10],
    paceMedian: double
)
```

Zurückgeliefert werden neun Werte:

```text
physical, nonPhysical, total,
rowsScored, rowsSkipped,
pacePointsPhysical, pacePointsNonPhysical, pacePointsTotal,
hasPacePoints
```

Die Rückgabenamen sind im Kotlin-Modell `PacePointsTotals` eindeutig erhalten. [N1] [K1]

### 4.2 Merkmalsbildung pro Minute

Die native Funktion bildet für jede Zeile `i` zwölf Features:

```text
z_ij = (stats[j][i] - loc[j]) / scale[j]     für j = 0 … 9
x_i  = [z_i0 … z_i9, sin(θ_i), cos(θ_i)]
θ_i  = 2π × ((localMinutes[i] mod 1440) / 1440)
```

Dabei gilt:

- `scale[j]` wird bei ungültigem/zu kleinem Skalenwert auf `1.0` gesetzt; die native Prüfschwelle ist `1e-6`.
- Fehlt ein einzelner Statistikwert (`NaN`/nicht endlich), wird sein standardisiertes Feature `0.0`.
- Ist der Wert der **zehnten Statistikserie (`stats[9]`)** nicht endlich, wird die gesamte Zeile übersprungen. Diese Serie ist damit der native Pflicht-/Qualitätsanker.
- Übersprungene Zeilen werden nicht inferiert und als `rowsSkipped` gezählt.
- Der Modellname und die Datei `v1_1min_opset18.onnx` zeigen den vorgesehenen 1-Minuten-Raster; `rowMinutes` erlaubt zusätzlich eine zeitliche Gewichtung eines Blocks.

**[OFFEN]** Die semantischen Namen und die Berechnung der zehn Statistikserien, der zehn `loc`- und `scale`-Werte sowie die Vorverarbeitung/Artefaktfilterung konnten aus dem obfuskierten Dart-AOT-Code nicht zuverlässig rekonstruiert werden. Es wäre spekulativ, die Spalten vorschnell als HR, HRV, ACC oder Schritte zu benennen. [N1] [N2]

### 4.3 ONNX-Modell

Das Modell hat:

- Produzent: PyTorch `2.13.0`, ONNX Opset 18;
- Eingang `features: FLOAT[n_rows,12]`;
- Ausgang `exertion: FLOAT[n_rows,3]`;
- drei versteckte lineare Schichten mit jeweils 128 Neuronen;
- exakte GELU-Aktivierung nach jeder versteckten Schicht;
- eine lineare Schicht auf vier Komponenten;
- Sigmoid und Multiplikation mit 2;
- eine feste Composite-Matrix.

Mathematisch:

```text
h1 = GELU(W0 × x + b0)              // 12 → 128
h2 = GELU(W1 × h1 + b1)             // 128 → 128
h3 = GELU(W2 × h2 + b2)             // 128 → 128
l  = W3 × h3 + b3                   // 128 → 4
q  = 2 × sigmoid(l)                 // vier Werte, jeweils 0 … 2

physical    = q0
nonPhysical = 0,583 × q1 + 0,319 × q2 + 0,098 × q3
total       = physical + nonPhysical

GELU(v) = 0,5 × v × (1 + erf(v / √2))
```

Die Composite-Matrix ist exakt:

```text
             physical   nonPhysical   total
q0              1           0           1
q1              0           0,583       0,583
q2              0           0,319       0,319
q3              0           0,098       0,098
```

Damit liegen `physical` und `nonPhysical` jeweils im Rohbereich 0–2, `total` im Rohbereich 0–4. Nichtkörperliche Belastung ist also keine eigenständige zweite Netzarchitektur, sondern eine feste Mischung dreier weiterer gelernten Komponenten. [N2]

### 4.4 Aggregation und Umrechnung in PacePoints

Für alle nicht übersprungenen Zeilen summiert der native Code die ersten beiden Modellausgaben zeitgewichtet:

```text
Ephysical    = rowMinutes × Σ physical_i
EnonPhysical = rowMinutes × Σ nonPhysical_i
Etotal       = Ephysical + EnonPhysical
```

Die native Konstante beträgt:

```text
C = pm_pace_shared_constant()
  = 0,0013222063302792595
```

Bei gültigem persönlichem `paceMedian` entstehen die PacePoints:

```text
PPphysical    = Ephysical    × paceMedian × C
PPnonPhysical = EnonPhysical × paceMedian × C
PPtotal       = Etotal       × paceMedian × C
```

`hasPacePoints` kennzeichnet, ob diese Skalierung verfügbar ist. Ohne gültigen `paceMedian` liefert die App zwar Exertion-Rohwerte, aber keine skalierten PacePoints; im Dart-Code steht entsprechend `no PacePoints without pace median`. [N1] [D1]

Zusätzlich exportiert die Bibliothek:

```text
pm_pace_window_days() = 14
```

Das belegt ein natives 14-Tage-Fenster für die Pace-/Median-Logik. **[OFFEN]** Wie `paceMedian` aus diesem Fenster im Einzelnen gebildet wird, welche Tage einbezogen werden und wie Schlaf/Lücken darin behandelt werden, ist nicht hinreichend rekonstruiert.

### 4.5 Qualitäts- und Fehlerverhalten

- Ungültige Zeilen werden übersprungen statt mit Nullwerten inferiert.
- Nicht endliche Modellausgaben führen zu einem Fehler; es werden dann keine plausibel aussehenden Ersatz-PacePoints geschrieben.
- Die native Fehlermeldung nennt ausdrücklich `model produced a non-finite output (input overflow)`.
- Das Modell läuft über ONNX Runtime 1.30.0 lokal auf dem Gerät.
- Die fertigen Tages-/Verlaufsergebnisse werden lokal gespeichert und über `visible_exertion_scores` synchronisiert. Die Synchronisation ist nicht mit der eigentlichen lokalen Modellinferenz zu verwechseln. [N1] [D1]

### 4.6 Datenlücken und Schlaf

Nach offizieller Produktdokumentation:

- entstehen Lücken im PacePoints-Verlauf bei unzureichender kontinuierlicher HR-Abdeckung; der Hilfetext nennt dabei eine 15-Minuten-Grenze;
- bei aktiviertem Sleep-Tracking entstehen während erkannten Schlafs keine PacePoints;
- nach dem Ende der PaceSetter-Zeit laufen PacePoints trotzdem weiter. [V-DAY]

**[OFFEN]** Die exakte native Resampling-, Lückenüberbrückungs- und Schlafmaskierungslogik vor dem ONNX-Aufruf ist nicht vollständig rekonstruiert.

## 5. Herzfrequenzzonen und öffentlich beschriebene Punktelogik

### 5.1 Zonen

Visible unterscheidet öffentlich drei Zonen: [V-ZONES]

| Zone | Bedeutung | PacePoints laut Hilfe |
|---|---|---|
| Rest | Ruhe, Liegen, Schlaf, Recovery | keine |
| Activity | Alltag, aktive Ruhe, sitzende Tätigkeiten | Punkte; mehr bei höherer HR innerhalb der Zone |
| Exertion | anstrengende Tätigkeiten | ungefähr 4× Activity, laut Hilfe im Zeitverlauf exponentiell steigend |

Die öffentliche Erklärung lautet sinngemäß: Je höher die Herzfrequenz und je länger sie erhöht bleibt, desto schneller steigen die Punkte. [V-PP]

**Versionseinordnung:** Diese Zonenbeschreibung erklärt das Produktkonzept und sehr wahrscheinlich die ältere `exertion_score`-Logik. Sie beschreibt **nicht** den vollständigen oben rekonstruierten 12-Feature-ONNX-Graph. Beide Erkenntnisse dürfen nicht zu einer angeblichen einzigen „offiziellen Formel“ vermischt werden.

### 5.2 Personalisierung der Zonen

- Auto-generierte Zonen werden nach vier Tagen mit Banddaten angeboten.
- Laut Hersteller berücksichtigt der Zonenalgorithmus Alter, Functional Capacity und bisherige HR-Daten; Geschlecht, Disability-Status und Krankheitsart fließen nach dessen Angabe nicht ein.
- In der App werden Monthly Check-in und Functional Capacity mit dem Freischalten/Neuberechnen der Zonen verknüpft.
- Zonen können manuell angepasst werden; empfohlen werden Schritte von 5 bpm und anschließend 2–3 volle Beobachtungstage.
- Die App speichert Zonen historisch. Eine Änderung gilt ab dem aktuellen Tag; ältere Tage behalten ihre damaligen Zonen.
- Neuberechnung von PacePoints für die letzten drei Tage ist als eigener App-Vorgang vorhanden.
- POTS und herzwirksame Medikamente können manuelle Anpassung nötig machen. [V-ZONES] [V-MANAGE-ZONES] [D1]

**[OFFEN]** Die genaue Formel des auto-generierten Zonenmodells und die Schwellen für `activityThreshold`/`exertionThreshold` sind nicht enthalten.

## 6. PacePoints-Budget und Daily Budget

### 6.1 Initiales Baseline-Budget

- Nach vier Datentagen kann ein persönliches Budget gesetzt werden.
- Ausgangspunkt ist laut Hilfe die durchschnittliche tägliche Punktemenge.
- Die Nutzerin bewertet zusätzlich, wie sich das Aktivitätsniveau angefühlt hat; die App führt anschließend zu einem passenden Budget.
- Die App enthält Onboarding-/Review-Stufen an Tag 5, Tag 14 und Tag 28.
- Die native Pace-/Median-Logik verwendet ein 14-Tage-Fenster; daraus folgt nicht automatisch, dass jedes initiale Budget exakt ein 14-Tage-Median ist. [V-PP] [D1] [N1]

Die Herstellerempfehlung ist, das Budget nicht häufig zu ändern. Erst Zonen prüfen, dann Budget. Kurzfristige Infektionen, Crashs oder Wettereinflüsse sollen nicht sofort zu einer dauerhaften Budgetänderung führen. Relevant sind dagegen langfristige Zustandsänderungen, herzwirksame Medikamente oder veränderte Zonen. [V-BUDGET]

### 6.2 Stability-abhängiges Daily Budget

Die aktuelle App enthält einen separaten `DailyBudgetCalculator`, historische Budgets und die Auswahl `baseline` oder `suggested`. Nachweisbare Logik:

- Daily Budget basiert auf Stability und der persönlichen Baseline.
- In App-Test-/Debug-Presets entspricht Score 2 einem niedrigeren, Score 3 dem Baseline- und Score 5 einem höheren Budget.
- Nutzer können vorgeschlagenes oder Baseline-Budget wählen.
- Historische Tage behalten ihren damaligen Wert; spätere Budgetänderungen schreiben alte Tage nicht um.
- Liegt das Baseline-Budget im Minimumbereich – der App-Kommentar nennt 3 oder weniger – wird kein Senkungsvorschlag erzeugt.
- Bei **erhöhtem nächtlichem Ruhepuls** kann Visible das Budget trotz niedriger Stability auf Baseline halten. Begründung im App-Text: Ein zusätzlich gesenktes Budget könnte sonst zu noch stärkerer Aktivitätsreduktion führen, obwohl PacePoints ohnehin schneller ansteigen können.
- Ein Daily Budget ist eine Empfehlung, keine Sperre; Überschreiten verändert nur Darstellung und Auswertung. [D1] [V-DAY]

**[OFFEN]** Es ist keine belastbare vollständige Tabelle wie „Score 1 = x %, Score 2 = y % …“ rekonstruierbar. Ebenso offen sind Rundung, minimale/maximale Vorschläge außer dem genannten Sonderfall und alle Bedingungen der Elevated-HR-Sperre.

### 6.3 Budget-Reviews

Die App fragt nach ersten Wochen erneut ab, wie gut das Budget passt:

- regelmäßige Überschreitung ohne gefühlte Überlastung kann für ein höheres Budget sprechen;
- regelmäßiges Unterbleiben bei trotzdem starker Belastung kann für ein niedrigeres Budget sprechen;
- die App berücksichtigt die Antworten „comfortable“, „above“, „below“ und „not sure“;
- mögliche Gründe für Unsicherheit sind Crash/Infektion, bewusstes Pacing, Symptom-Aktivitäts-Mismatch oder noch unklare Erfahrung;
- Vorschläge können angenommen oder das aktuelle Budget beibehalten werden. [D1] [V-BUDGET]

## 7. PaceSetter und Benachrichtigungen

Der PaceSetter ist der lineare Soll-Verlauf über die aktive Tageszeit:

```text
start ≤ t ≤ end:
    Soll(t) = Budget × (t - start) / (end - start)

nach end:
    Soll bleibt am Endwert;
    Ist-PacePoints steigen weiter.
```

Diese Gleichung ist die direkte formale Darstellung der offiziell gezeigten diagonalen Guideline. [V-PP] [V-DAY]

**Tagesgrenzen:**

- Visible-Tage laufen von 04:00 bis 04:00 Uhr.
- Mit Band über Nacht startet der PaceSetter um 04:00 Uhr, bei aktivem Sleep-Tracking zur Wake-Time.
- Ohne Band über Nacht startet er beim ersten Anlegen nach 04:00 Uhr.
- Standard-Ende ist Mitternacht; einstellbar ist 18:00 bis 04:00 Uhr.
- Das Ende des PaceSetters beendet nicht die Punkteaufzeichnung. [V-PP] [V-DAY]

**Benachrichtigungen:**

- Exertion-Benachrichtigung nach einstellbarer Zeit in der Exertion-Zone;
- PaceSetter-Benachrichtigung, wenn man vor dem Soll-Verlauf liegt;
- zusätzlich Benachrichtigung beim Erreichen des Tagesbudgets;
- Ton und Aktivierung sind getrennt einstellbar. [V-NOTIF]

## 8. Activity Tags und Activity Insights

Aktivitäten können nachträglich getaggt oder mit dem Activity Monitor live erfasst werden. Der Activity Monitor in genau dieser App-Version zeigt PacePoints, Live-HR und Zone, unterstützt optionale Timer von 1–60 Minuten und speichert Aktivitäten ab einer Minute. [V-TAGS] [V-MONITOR]

### 8.1 Impact

Impact ist der PacePoints-Gesamtverbrauch einer Aktivität relativ zum aktuellen Budget: [V-INSIGHTS]

| Einstufung | Anteil am aktuellen Budget |
|---|---:|
| Higher impact | > 30 % |
| Medium impact | 15–30 % |
| Lower impact | < 15 % |

### 8.2 Intensity

Intensity ist PacePoints pro Stunde. Die Klassifikation normiert auf acht Stunden: [V-INSIGHTS]

| Einstufung | Rechnung |
|---|---|
| Higher intensity | `PP/h × 8 > 100 % des Budgets` |
| Medium intensity | `PP/h × 8 > 50 % des Budgets` |
| Lower intensity | sonst |

Die Nutzung des **aktuellen** Budgets für diese Kategorien ist zu unterscheiden von der historischen Anzeige des damals gültigen Tagesbudgets.

## 9. HRV-Messung und Normalisierung

### 9.1 RMSSD und Visible-HRV

Für validierte Normal-zu-Normal-Intervalle `NN₁ … NN_N` in Millisekunden:

```text
d_i      = NN_(i+1) - NN_i                    für i = 1 … N-1
RMSSD_ms = sqrt(Σ d_i² / (N-1))               für N ≥ 2
HRV      = ln(RMSSD_ms) / 6,5 × 100           für RMSSD_ms > 0
```

Die angezeigte Visible-HRV ist damit **nicht RMSSD in ms**, sondern eine logarithmisch normierte 0–100-Metrik. [V-HRV]

Testanker:

| RMSSD in ms | HRV |
|---:|---:|
| 1 | 0 |
| 10 | 35,424386 |
| 20 | 46,088189 |
| 50 | 60,184969 |
| 100 | 70,848772 |
| 200 | 81,512575 |
| `e^6,5 ≈ 665,141633` | 100 |

Die Stability-Einstufung nutzt ungerundete Werte; HR/HRV werden erst für Anzeige und Diagramme gerundet. Deshalb kann beispielsweise ein exakter Wert 49,6 als 50 erscheinen und trotzdem unterhalb eines Normalbereichs ab 50 liegen. [V-STAB]

**[OFFEN]** Die Quelle beschreibt keine verbindliche Clipping-Regel für rechnerische Werte außerhalb 0–100 und keine vollständige Artefaktfilterung. Diese darf nicht erfunden werden.

### 9.2 Messwege

- **Morgenmessung mit Telefon oder Band:** eine Minute, möglichst direkt nach dem Aufwachen, gleiche Position und Zeit, ruhig und bewegungsfrei.
- **Nachtmessung:** vier Stunden beginnend 30 Minuten nach dem Einschlafen; nur ruhige Abschnitte, Wach-/Bewegungsphasen werden ignoriert und die verbleibenden Werte gemittelt.
- **Getrennte Baselines:** Morgen- und Nachtmessungen werden nicht miteinander vermischt.
- Die native Happitech-Schicht liefert unter anderem AVNN, SDNN, RMSSD, pNN50, Puls und Qualitäts-/Fehlerinformationen. Die Kamera-PPG- und Peak-Erkennung selbst ist proprietärer nativer Code. [V-PHONE] [V-NIGHT-HR] [APK-HAPPITECH]

## 10. Stability Score

### 10.1 Eingaben und zeitlicher Bezug

| Faktor | Eingabe | Bewertung laut Hersteller |
|---|---|---|
| Ruhepuls | Morgen- oder Nachtmessung | innerhalb/niedriger als Normalbereich tendenziell stabil |
| HRV | normierte HRV derselben Messart | höher als Mittel häufig positiv; sehr große Aufwärtsausschläge können instabil sein |
| Schlaf | Morning Check-in, 1–4 | bessere Bewertung und besser als üblich positiv |
| Symptome | vorheriger Evening Check-in, 0–3 je Symptom | niedrigere Ausprägung als üblich positiv |

Die Abend-Symptome wirken auf den **folgenden** Tag. Fehlt der Evening Check-in, rechnet Visible mit Ruhepuls, HRV und Schlaf weiter. Crash wird separat erfasst und nicht als Symptomwert eingerechnet. [V-STAB] [V-EVENING]

Schlafbewertung:

```text
1 = Awful
2 = Bad
3 = Fair
4 = Good
```

Eine nachträgliche Schlafänderung löst laut Hersteller keine Neuberechnung des Stability Score aus. [V-MORNING]

### 10.2 Baseline und Ausgabe

- Erster Score nach vier Morning Check-ins. [V-INTRO]
- Rollierende Normalbereiche aus bis zu 90 Tagen historischer Daten.
- Personalisierung anhand der typischen biometrischen Schwankung mit einem Variationskoeffizienten.
- Jeder Faktor wird unabhängig bewertet.
- HR/HRV: innerhalb, niedriger, höher oder deutlich höher als Normalbereich; größere Abweichungen wirken stärker.
- Schlaf/Symptome: ungefähr durchschnittlich, besser oder schlechter.
- Morgen und Nacht besitzen getrennte Baselines; die letzte Nacht gehört zur aktuellen Messung und nicht zu ihrer eigenen Vergleichsbaseline. [V-NORMAL] [V-STAB] [D1]

| Score | Bedeutung |
|---:|---|
| 1 | „Out of balance“, stärker reduzierte Resilienz; meist Ruhe priorisieren |
| 2 | „Out of balance“, mindestens ein instabiles Signal; eher ruhiger Tag |
| 3 | „Around your average“, durchschnittliche oder gemischte Signale |
| 4 | „Stable“, mindestens ein positiver Faktor |
| 5 | „Stable and improving“, mehrere positive Signale |

Der Score ist keine Diagnose, kein allgemeiner Wellnesswert und keine Crash-Garantie. [V-STAB]

### 10.3 Nicht rekonstruierte Bestandteile

**[OFFEN]** Nicht belastbar verfügbar sind:

- die konkrete Normalbereichsformel aus dem Variationskoeffizienten;
- exakte Faktorgewichte und Schwellen für die Aggregation auf 1–5;
- die Aggregation mehrerer Symptome zu einem einzigen Symptomfaktor;
- die genaue Regel, wann ein HRV-Anstieg positiv oder instabil ist;
- die vollständige Behandlung einzelner fehlender Faktoren außer dem dokumentierten fehlenden Evening Check-in;
- serverseitige Versionslogik.

Die App speichert unter anderem `lower_bound_hrv`, `upper_bound_hrv`, `threshold_hrv_stable_min/max`, `threshold_hrv_unstable_min`, `threshold_hrv_improving_max` und analoge HR-Felder. Das bestätigt explizite Faktorgrenzen, liefert aber keine festen universalen Zahlen. [D1]

`calculateStability` ist als Cloud-Funktion benannt, und Visible verlangt für die Score-Berechnung eine Verbindung. Eine vollständig offline identische Stability-Nachbildung ist daher aus dem APK allein nicht belegt. [D1] [V-NIGHT]

## 11. Tägliches Symptomtracking

### 11.1 Evening Check-in

Der Evening Check-in erfolgt einmal täglich und erfasst den Tag rückblickend. Die Frage lautet sinngemäß: „How were your symptoms today, at their worst?“ [V-EVENING] [V-ONCE]

| Bereich | Funktionsweise |
|---|---|
| Crash | Ja/Nein; eigenes Ereignis |
| Symptome | vordefiniert und benutzerdefiniert; aktivierbar/ausblendbar |
| Symptomwert | stärkste Ausprägung des Tages: 0 keine, 1 leicht, 2 mäßig, 3 schwer |
| Belastung | körperlich, kognitiv/mental, emotional; in Tracker-Verwaltung zusätzlich sozial |
| Weitere Faktoren | Periode und Infektion/Krankheit |
| Medikamente | eigene Einträge, Einnahme ja/nein |
| Lifestyle | eigene Tracker für Gewohnheiten/Verhalten |
| Notiz | Freitext für Tageskontext |

- „Previous answers“ kann wiederkehrende Angaben übernehmen.
- Frühere Tage können ergänzt und Antworten jederzeit geändert werden.
- Tracker werden unter `Profile → Trackers` verwaltet.
- Custom Exertions sind nicht möglich; Custom Symptoms, Medications und Lifestyle-Tracker schon.
- Ein neuer App-Tag beginnt um 04:00 Uhr. Bei abweichendem Schlafrhythmus sollen Morning und Evening Check-in demselben persönlichen Wachtag zugeordnet werden. [V-EVENING] [V-ADD]

Die Herstellerbegründung für nur eine Symptomabfrage pro Tag ist die Reduktion der Tracking- und mentalen Belastung. Symptomtracking ist optional und nicht Voraussetzung für PacePoints. [V-ONCE] [D1]

**[OFFEN]** Eine eindeutige numerische Skala der subjektiven körperlichen/kognitiven/emotionalen/sozialen Belastung geht aus den ausgewerteten Primärquellen nicht hervor. Sie darf nicht automatisch mit der Symptomskala 0–3 gleichgesetzt werden.

### 11.2 Crash-Logik

Visible beschreibt Crash als verzögerte, deutliche Verschlechterung von Symptomen/Fatigue, häufig nach körperlicher, mentaler oder emotionaler Belastung; synonym werden Flare-up, PEM und post-exertionelle Symptome genannt.

Die App nennt als Definition einen Zeitraum von **mindestens 12 Stunden**, in dem Symptome signifikant über der persönlichen Baseline liegen. Gleichzeitig erlaubt sie eine eigene konsistente Crash-Definition. [D1]

Wichtig:

- Crash ist keine zusätzliche Symptomzeile und geht nicht als gewöhnlicher 0–3-Wert in den Symptomfaktor ein.
- Die App enthält eigene Felder wie `crash_probability`, `days_since_last_crash` und Crash-Synchronisation.
- Diese Felder belegen keine öffentlich vollständige Crash-Vorhersageformel. [V-STAB] [D1]

### 11.3 Datenmodell

Die lokale App verwendet allgemeine `health_variables` und `observations` statt einer isolierten Symptomtabelle:

- Trackerdefinition mit Kategorie und Rating Scale;
- Beobachtung mit Datum, Wert und optional Descriptor;
- eindeutiger Schlüssel aus Nutzer, `observation_date` und `health_variable_id`;
- separate Notizen;
- Rating-Scale-Werte für geordnete Antworten;
- Synchronisation über `visible_observations`.

Dadurch werden Symptome, Exertion, Medikamente, Perioden, Infektionen, Crashs, Schlaf und weitere Tracker einheitlich gespeichert und über Trends abfragbar. [D1]

Für korrekte Auswertungen muss **fehlend von 0 unterschieden** werden: Kein Check-in ist nicht gleichbedeutend mit Symptomfreiheit. Die App besitzt dafür eigene Zustände wie `symptomsNotRated`, `incompleteEveningCheckIn` und `insufficient`.

### 11.4 Morning Check-in als Gegenstück

Der Morning Check-in erfasst die Schlafbewertung 1–4 und die biometrische Messung. Die App verbindet anschließend diese Angaben mit den Symptomen des vorherigen Abends. [V-MORNING] [V-STAB]

## 12. Auswertung, Trends und Export

### 12.1 Symptom-Stabilität

Die App enthält eine eigene Darstellung der Symptom-Stabilität:

- nach vier aufeinanderfolgenden Tagen mit Symptombewertungen;
- Vergleich mit einer „recent baseline“;
- Zustände wie reducing/stable/increasing sowie significant worsening und insufficient data.

**[OFFEN]** Diese Symptom-Stabilitätsanzeige ist nicht mit dem vier-Faktoren-Stability-Score gleichzusetzen. Ihre genaue Baseline-, Mittelungs- und Schwellenformel ist nicht rekonstruiert. [D1]

### 12.2 Trends

- Ein Faktor oder Vergleich zweier Faktoren;
- Zeiträume 2 Wochen, 1, 3 oder 6 Monate;
- Trendlinie als trailing 3-Tage-Mittel einschließlich des aktuellen Tags;
- Symptome, Exertions, Stability-HR/HRV, Schlaf, Crashs und Custom Tracker;
- bei Mitgliedschaft auch PacePoints und Breathing-Minuten;
- Evening-Notizen über das Notizsymbol. [V-TRENDS]

Bei fehlenden Tagen sollten Auswertungen die Anzahl einbezogener Beobachtungen sichtbar machen. Fehlende Werte als 0 aufzufüllen würde künstliche Symptomfreiheit erzeugen; das ist eine Implementierungsfolge aus dem Datenmodell, keine behauptete Herstellerformel.

### 12.3 Health Report

- Voraussetzung: mindestens 30 Morning-Check-in-Tage, davon mindestens einer in den letzten 30 Tagen.
- HR/HRV als 14-Tage-rollierender Mittelwert.
- Fünf am häufigsten berichtete Symptome als 7-Tage-rollierende mittlere Ausprägung.
- Farben: Grün keine, Gelb leicht, Orange mäßig, Rot schwer.
- Perioden und Medikamente sind enthalten, andere Experience-Tracker nicht.
- Keine kontinuierliche HR, keine HR-Zonen und keine PacePoints im PDF. [V-REPORT]

### 12.4 CSV

Die offizielle Dokumentation nennt eine Beobachtung pro Zeile mit:

```text
observation_date, tracker_name, tracker_category, observation_value
```

Die aktuelle Binärdatei enthält zusätzlich:

```text
observation_descriptor, note_scope
```

Enthalten sind Stability, Morning-, Evening- und Monthly-Daten; bei Mitgliedschaft zusätzlich PacePoints und Budget. Nicht enthalten sind kontinuierliche HR-Rohdaten und HR-Zonen. Evening-Notizen stehen am Ende der Datei. [V-EXPORT] [D1]

## 13. Monthly Check-in und Pacing-Personalisierung

Der Monthly Check-in liefert die Functional-Capacity-Grundlage für personalisiertes Pacing und auto-generierte Zonen:

- 18 PROMIS-Kernfragen: Fatigue 6, Thinking 4, Movement 4, Social Roles 4;
- optional drei Symptom-Delay-/PEM-Fragen und eine Pain-Interference-Frage;
- getrennte Ergebnisse je Domäne, kein einziger universeller Gesamt-PROMIS-Score;
- Fatigue: höher schlechter; Thinking/Movement/Social Roles: niedriger schlechter;
- öffnet alle 30 Tage und bleibt sieben Tage geöffnet. [V-MONTH-WINDOW]

Die drei Symptom-Delay-Fragen sind Visible-eigen und keine PROMIS-T-Scores. Die vollständige Fragenliste und Scoring-Grenzen sind in `visible-apk-hrv-symptomtracking.md` dokumentiert. [V-MONTH-Q] [V-MONTH-SUM]

Das ältere FUNCAP27-Ergebnis bleibt historisch separat und ist nicht in PROMIS umrechenbar. [V-FUNCAP]

**[OFFEN]** Wie genau die PROMIS-Ergebnisse zusammen mit Alter und HR-Daten in Zonenschwellen oder `paceMedian` eingehen, ist nicht veröffentlicht.

## 14. Zusammenspiel von Symptomen, HRV und Pacing

Die belastbare Kausalkette lautet:

```text
Abend Symptome 0–3 ───────┐
Morgen Schlaf 1–4 ────────┤
Ruhepuls + normierte HRV ─┴─> Stability 1–5 ──> optionales Daily Budget
                                                       │
Sensordaten ─> minütliches ONNX-Modell ─> PacePoints ───┴─> Ist/Soll-Vergleich
```

Daraus folgt:

1. **Symptome verändern nicht rückwirkend die bereits aufgezeichneten Sensor-PacePoints.** Sie verändern den Kontext und gegebenenfalls das nächste Tagesbudget.
2. **HRV und Ruhepuls sind Recovery-/Stabilitätssignale**, während PacePoints die akkumulierte Belastung beschreiben.
3. **Ein hoher Stability Score ist keine Erlaubnis für unbegrenzte Aktivität**, und ein niedriger Score beweist keinen bevorstehenden Crash.
4. **Subjektive Belastung und Modellbelastung können auseinanderlaufen.** Genau dafür besitzen Budget-Reviews eine Symptom-Aktivitäts-Mismatch-Option.
5. **Crash, Infektion, Medikamente und empfundene Symptome sind notwendiger Kontext**, damit Zonen und Budget nicht nur mechanisch angepasst werden.

## 15. Offene Punkte vor einer 1:1-Nachbildung

1. Namen, Einheiten und Berechnung der zehn ONNX-Statistikserien sowie `loc`/`scale` rekonstruieren.
2. Bildung von `paceMedian`, Schlafmaskierung und 14-Tage-Fenster im Detail klären.
3. Dynamische Auswertung mit Testvektoren gegen die native App durchführen; statisch belegte Formeln allein ersetzen keine Golden-Tests.
4. Vollständige Stability-Spezifikation oder Server-Testvektoren beschaffen: Normalbereich, Symptomaggregation, Gewichte, Schwellen, Missing-Data-Regeln.
5. Numerische Daily-Budget-Matrix, Mindest-/Höchstwerte und Elevated-HR-Sonderregeln verifizieren.
6. Auto-generierte Zonenformel und den genauen PROMIS-Einfluss klären.
7. Öffentliche Zonen-/Exponentialbeschreibung und aktuellen ONNX-Pfad versionsbereinigt behandeln, nicht mischen.
8. Medizinische Sicherheit: keine Diagnose, keine Crash-Vorhersage und keine unvalidierten Belastungsanweisungen aus einer Nachbildung ableiten.

## 16. Quellen

### Native und APK-Primärbefunde

- **[N1]** `REA/visible-26.10.1/libs/libpacepoints_ml.so`: disassemblierte Funktionen `pm_build_features` (`0x1584`), `pm_score_range` (`0x1a64`), `pm_pace_shared_constant` (`0x1d90`), `pm_pace_window_days` (`0x1d9c`), `pm_infer_rows` (`0x1da4`); `.rodata` mit `C`, `1e-6` und `2π`.
- **[N2]** Base-APK, `assets/flutter_assets/packages/pacepoints_ml/assets/model/v1_1min_opset18.onnx`: ONNX-Graph, Gewichte, GELU-/Sigmoid-Knoten und `composite`-Matrix.
- **[N3]** `REA/visible-26.10.1/libs/libapp.so`: Referenzen auf `pacepoints_ml/src/session.dart`, `totals.dart`, `bindings.g.dart`, `PacePointsMlSession`, `paceMedian`, `rowMinutes`, `localMinutes`, `physical`, `nonPhysical` und `total`.
- **[K1]** Base-APK `classes4.dex`: `com.makevisible.pacepoints_ml.PacePointsMlNative`, Session-Wrapper und `PacePointsTotals`; native Signatur und Zuordnung aller neun Rückgabewerte.
- **[D1]** `REA/visible-26.10.1/libs/libapp.so`: Dart-Klassen-/Dateinamen, Datenbank-/GraphQL-Felder und Produkttexte zu `DailyBudgetCalculator`, Stability, Check-ins, Observations, Trends, Export und Pacing. Dart-AOT ist obfuskiert; Strings werden deshalb nur für das jeweils ausdrücklich Genannte verwendet, nicht für unbelegte numerische Schlussfolgerungen.
- **[APK-HAPPITECH]** Base-APK `classes4.dex`: Happitech `ME_TimeHRV`, Konvertierung und Qualitätslistener; außerdem `REA/visible-26.10.1/libs/libheartbeat-android.so`.

### Offizielle Visible-Quellen

- **[V-PP]** „PacePoints and the PaceSetter“: https://help.makevisible.com/en/articles/9562114-pacepoints-and-the-pacesetter
- **[V-BUDGET]** „How to manage your PacePoints budget“: https://help.makevisible.com/en/articles/11626793-how-to-manage-your-pacepoints-budget
- **[V-DAY]** „Your pacing day“: https://help.makevisible.com/en/articles/13600620-your-pacing-day
- **[V-ZONES]** „Heart rate zones“: https://help.makevisible.com/en/articles/9561897-heart-rate-zones
- **[V-MANAGE-ZONES]** „How to manage your heart rate zones“: https://help.makevisible.com/en/articles/11626771-how-to-manage-your-heart-rate-zones
- **[V-NOTIF]** „Pacing notifications“: https://help.makevisible.com/en/articles/9562198-pacing-notifications
- **[V-INSIGHTS]** „Activity insights“: https://help.makevisible.com/en/articles/8677068-activity-insights
- **[V-TAGS]** „How do tags work?“: https://help.makevisible.com/en/articles/8698068-how-do-tags-work
- **[V-MONITOR]** „Activity Monitor“: https://help.makevisible.com/en/articles/15705406-activity-monitor
- **[V-HRV]** „What is heart rate variability (HRV)?“: https://help.makevisible.com/en/articles/10125500-what-is-heart-rate-variability-hrv
- **[V-STAB]** „Understanding your Stability Score“: https://help.makevisible.com/en/articles/17115224-understanding-your-stability-score
- **[V-INTRO]** „An introduction to the Stability Score“: https://help.makevisible.com/en/articles/10125371-an-introduction-to-the-stability-score
- **[V-NORMAL]** „What is my normal range?“: https://help.makevisible.com/en/articles/10125529-what-is-my-normal-range
- **[V-PHONE]** „Getting your Stability Score with a morning reading from your phone“: https://help.makevisible.com/en/articles/17629581-getting-your-stability-score-with-a-morning-reading-from-your-phone
- **[V-NIGHT]** „Getting your Stability Score from overnight data“: https://help.makevisible.com/en/articles/14982301-getting-your-stability-score-from-overnight-data
- **[V-NIGHT-HR]** „How Stability (overnight data) measures your HR and HRV“: https://help.makevisible.com/en/articles/14979704-how-stability-overnight-data-measures-your-hr-and-hrv
- **[V-MORNING]** „Completing your Morning Check-in“: https://help.makevisible.com/en/articles/12276375-completing-your-morning-check-in
- **[V-EVENING]** „Evening Check-in“: https://help.makevisible.com/en/articles/10156905-evening-check-in
- **[V-ONCE]** „Why do we only ask you to record your symptoms once a day?“: https://help.makevisible.com/en/articles/10125165-why-do-we-only-ask-you-to-record-your-symptoms-once-a-day
- **[V-ADD]** „How do I add a symptom or medication to the Evening Check-in?“: https://help.makevisible.com/en/articles/9583049-how-do-i-add-a-symptom-or-medication-to-the-evening-check-in
- **[V-TRENDS]** „Trends“: https://help.makevisible.com/en/articles/12149164-trends
- **[V-REPORT]** „Health Report PDF“: https://help.makevisible.com/en/articles/9583075-health-report-pdf
- **[V-EXPORT]** „Data export“: https://help.makevisible.com/en/articles/9583077-data-export
- **[V-MONTH-Q]** „What the Monthly Check-in asks you“: https://help.makevisible.com/en/articles/17757584-what-the-monthly-check-in-asks-you
- **[V-MONTH-SUM]** „Understanding your Monthly Check-in summary“: https://help.makevisible.com/en/articles/17757668-understanding-your-monthly-check-in-summary
- **[V-MONTH-WINDOW]** „When your Monthly Check-in opens and closes“: https://help.makevisible.com/en/articles/17757497-when-your-monthly-check-in-opens-and-closes
- **[V-FUNCAP]** „Your Functional Capacity results“: https://help.makevisible.com/en/articles/9583619-your-functional-capacity-results
