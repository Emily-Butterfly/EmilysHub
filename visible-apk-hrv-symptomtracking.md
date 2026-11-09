# Visible-APK: Symptomtracking und HRV-Auswertung für EmilysHub

**Untersuchte Datei:** `REA/com.makevisible.visible.apk`  
**App:** Visible `26.10.1` (`versionCode 4151`, Paket `com.makevisible.visible`)  
**SHA-256:** `47e319c34a655de463b45700c2675161f48c61e5936e590687a59fc257a3c4e1`  
**Analyse:** statische REA mit APKTool 2.7.0, JADX 1.5.6 und ONNX-Inspektion; ergänzt durch offizielle Visible-/HealthMeasures-Primärquellen, Stand 2026-11-09.

## 1. Ergebnis in Kürze

1. **Die angezeigte Visible-HRV ist nicht RMSSD in ms.** Sie wird aus den Schlag-zu-Schlag-Intervallen zunächst als RMSSD berechnet und anschließend so normiert:  
   **`HRV = ln(RMSSD_ms) / 6,5 × 100`**. Diese Formel ist offiziell eindeutig belegt und kann unabhängig in Kotlin implementiert werden. [V-HRV]
2. **Der tägliche Stability Score (1–5) kombiniert vier Faktoren:** Ruhepuls, normierte HRV, subjektive Schlafbewertung und die Symptome aus dem vorherigen Abend-Check-in. Fehlt der Abend-Check-in, wird ohne den Symptomfaktor gerechnet. [V-STAB] [V-INTRO]
3. **Die Normalbereiche sind individuell und rollierend:** bis zu 90 Tage historischer Daten, personalisiert anhand des Variationskoeffizienten. Der erste Score erscheint nach vier Morning Check-ins. Morgen- und Nachtmessungen haben getrennte Baselines. [V-NORMAL] [V-INTRO] [V-PREFS]
4. **Die exakten Schwellen, Faktorgewichte und die Aggregation zum Stability Score sind weder in der vorliegenden APK noch in den offiziellen Seiten enthalten.** Deshalb ist eine behauptete 1:1-Nachbildung des 1–5-Scores nicht seriös möglich. Für EmilysHub sind zunächst die HRV-Metrik, Eingaben, Baselines und Verläufe implementierbar; eine eigene 1–5-Aggregation muss separat festgelegt und validiert werden.
5. **Symptome werden einmal täglich am Abend erfasst:** stärkste Ausprägung des Tages von 0 (keine) bis 3 (schwer), mit eigenen Symptomen, Crash, Belastungen, Medikamenten, weiteren Faktoren und Notiz. Der Monats-Check-in enthält 18 PROMIS-Kernfragen sowie vier optionale Fragen. [V-EVENING] [V-ONCE] [V-MONTH-Q]
6. **Wichtige Dateibeschränkung:** Diese Base-Split-APK verlangt ABI- und Density-Splits und enthält **keine** `lib/`-Dateien, weder den Flutter-AOT-Snapshot `libapp.so` noch `heartbeat-android.so`. Der Dart-Code der Check-ins und die native Happitech-Mathematik können daher aus dieser Datei nicht vollständig rekonstruiert werden. [APK-MANIFEST] [APK-ZIP]

## 2. Beweisklassen

- **[APK] Direkt aus der untersuchten APK abgeleitet.** Klassen-/Methodennamen sind obfuskationssicher als Fundstelle angegeben, nicht als ursprüngliche Bezeichner interpretiert.
- **[V] Offizielle Visible-Dokumentation.** Verhaltensbeschreibung des Herstellers, aber kein Sourcecode.
- **[HM] Offizielle HealthMeasures-/PROMIS-Dokumentation.** Maßgeblich für PROMIS-Auswertung.
- **[Vorschlag] Integrationsentscheidung für EmilysHub.** Keine behauptete Visible-Interna.

## 3. Was die APK tatsächlich zeigt

### 3.1 Mess- und Auswertungsarchitektur

Visible ist eine Flutter-App. Die Kamera-Messung wird über das Happitech-SDK geführt. `AbstractC1109c` erzeugt eine native Engine über `MobileEngineFactory`, setzt Messmodus und Messdauer und erhält die fertigen Ergebnisse per Callback. Der native Code wird mit `System.loadLibrary("heartbeat-android")` geladen. Die sichtbare Kotlin-Schicht berechnet RMSSD **nicht selbst**. [APK-HAPPITECH]

Beim Abschluss liefert `on_measurement_completed` vier Ergebnisobjekte:

- `ME_HeartRate`
- `ME_TimeHRV`
- `ME_FrequencyHRV`
- `ME_ClassificationResults`

Die Konvertierung `Y.a(ME_TimeHRV)` übernimmt lediglich die von der Engine gelieferten Werte:

| Ergebnis | Native Einheit/Feld | Kotlin-Modell |
|---|---|---|
| AVNN | `avnn_ms` | `AVNN: Int` |
| SDNN | `sdnn_ms` | `SDNN: Int` |
| RMSSD | `rmssd_ms` | `RMSSD: Int` |
| pNN50 | `pnn50_ms` | `PNN50: Int` |
| Qualität | `confidence_percent` | `confidenceLevel: Int` |
| Stressindex | `stress_index` | separat an den Listener weitergereicht |

Zusätzlich existieren Herzfrequenz (`pulse_bpm`), Zahl guter IBI, LF-Prozentanteil, Qualitätssegmente und Warnungen/Fehler für Bewegung, schlechte Qualität, fehlenden Finger, zu kurze Dauer und frühzeitigen Abbruch. `isHighQuality()` akzeptiert die native Zuverlässigkeit `"High"` oder `"Medium"`. [APK-HAPPITECH] [APK-CONVERT]

**Integrationsfolge:** Die APK liefert eine Schnittstellenbeschreibung, nicht den PPG-/Peak-Detection-Algorithmus. Ein eigener Kamera-HRV-Algorithmus oder eine Wiederverwendung des proprietären Happitech-Codes darf daraus nicht abgeleitet werden.

### 3.2 Armband und Nachtdaten

Die Bluetooth-/Polar-Schicht speichert unter anderem:

- HR-Samples mit Zeitstempel, Gerät und optional korrigierter Herzfrequenz;
- PPI-Intervalle mit `error`, `blocker_bit`, HR und Hautkontakt;
- Beschleunigung, Hauttemperatur und Akku;
- Polar-Nightly-Recharge-Felder wie `mean_nightly_recovery_rmssd`, `mean_baseline_rmssd` und `sd_baseline_rmssd`.

Eine separate SQL-Abfrage bildet Minutenmittel der Herzfrequenz mit `avg(heart_rate)` und ignoriert HR `<= 0`. [APK-DB]

**Wichtig:** Die Polar-Felder sind Daten des **Polar-Nightly-Recharge-Modells**. Sie beweisen nicht, dass Visible seinen Stability Score mit denselben Mitteln/Standardabweichungen berechnet. Diese beiden Algorithmen dürfen nicht gleichgesetzt werden.

### 3.3 Symptome in der nativen Schicht

`SymptomsModel` enthält nur `List<String> symptoms`. Der Happitech-Report liest diese Liste aus SharedPreferences und gibt sie kommagetrennt aus. Das ist **nicht** das vollständige tägliche Symptomtracking von Visible; dessen Flutter-Implementierung fehlt mit `libapp.so`. [APK-SYMPTOMS]

### 3.4 Kein HRV-Modell im ONNX-Asset

Das enthaltene Modell `pacepoints_ml/.../v1_1min_opset18.onnx` erwartet `FLOAT[n_rows,12]` und liefert `FLOAT[n_rows,3]` namens `exertion`. Der Native-Wrapper nennt die Ergebnisse körperliche/nichtkörperliche Belastung und PacePoints. Es ist daher **kein HRV- oder Stability-Modell** und sollte für dieses Vorhaben nicht wiederverwendet werden. [APK-ONNX]

### 3.5 Fehlende Splits – Grenze der REA

Das Manifest enthält `requiredSplitTypes="base__abi,base__density"` und `com.android.vending.splits.required=true`. Das ZIP enthält fünf DEX-Dateien, aber null `lib/`-Einträge. Damit fehlen mindestens:

- `libapp.so`: Dart-AOT-Code mit Check-in- und Stability-Implementierung;
- `heartbeat-android.so`: native Happitech-Signalverarbeitung und HRV-Berechnung;
- weitere ABI-spezifische Bibliotheken.

Für eine tiefere 1:1-Rekonstruktion werden das vollständige Split-Set derselben signierten App-Version benötigt, insbesondere `config.arm64_v8a.apk` und das Density-Split. Selbst dann könnte ein serverseitiger Anteil verbleiben: Visible verlangt für die Score-Berechnung eine Internetverbindung. [APK-MANIFEST] [APK-ZIP] [V-NIGHT]

## 4. Implementierbare HRV-Logik

### 4.1 RMSSD aus Interbeat-Intervallen

Für `N` aufeinanderfolgende, validierte Normal-zu-Normal-Intervalle `NN₁ … NN_N` in Millisekunden:

```text
d_i       = NN_(i+1) - NN_i                  für i = 1 … N-1
RMSSD_ms  = sqrt( sum(d_i²) / (N-1) )        für N >= 2
HRV       = ln(RMSSD_ms) / 6,5 × 100         für RMSSD_ms > 0
```

Die zweite Gleichung ist die offiziell beschriebene Visible-Normierung auf einen linearen 0–100-Bereich; `ln` ist der natürliche Logarithmus. [V-HRV]

**Rundung:** Visible berechnet die Stability-Einstufung mit den ungerundeten Werten und rundet HR/HRV erst für Anzeige und Diagramme. EmilysHub sollte deshalb RMSSD und HRV als Gleitkommazahl speichern und erst im UI runden. [V-STAB]

**Keine erfundene Clipping-Regel:** Die Quelle beschreibt den Zielbereich 0–100 und den Logarithmusbereich 0–6,5, veröffentlicht aber keine Regel für Werte außerhalb. Eine Ein-/Ausgabevalidierung sollte ungültige Intervalle ablehnen, statt stillschweigend plausible Werte zu erzeugen. Ob extreme gültige HRV-Werte für die UI begrenzt werden, ist eine eigene Produktentscheidung und nicht Teil der belegten Formel.

### 4.2 Kotlin-Referenzlogik

```kotlin
import kotlin.math.ln
import kotlin.math.sqrt

object HrvEvaluation {
    /** RMSSD in ms; null bei zu wenigen oder ungültigen Intervallen. */
    fun rmssdMs(nnIntervalsMs: List<Double>): Double? {
        if (nnIntervalsMs.size < 2) return null
        if (nnIntervalsMs.any { !it.isFinite() || it <= 0.0 }) return null
        val squared = nnIntervalsMs.zipWithNext { current, next ->
            val difference = next - current
            difference * difference
        }
        return sqrt(squared.average())
    }

    /** Visible-kompatible 0–100-Transformation, noch nicht auf 0..100 begrenzt. */
    fun visibleScore(rmssdMs: Double): Double? =
        if (rmssdMs.isFinite() && rmssdMs > 0.0) ln(rmssdMs) / 6.5 * 100.0 else null

    fun fromIntervals(nnIntervalsMs: List<Double>): Double? =
        rmssdMs(nnIntervalsMs)?.let(::visibleScore)
}
```

**Testanker** (mit Toleranz wegen Gleitkommaarithmetik):

| RMSSD in ms | erwartete HRV |
|---:|---:|
| 1 | 0 |
| 10 | 35,424386 |
| 20 | 46,088189 |
| 50 | 60,184969 |
| 100 | 70,848772 |
| 200 | 81,512575 |
| `e^6,5 ≈ 665,141633` | 100 |

Zusätzliche Tests: weniger als zwei Intervalle, `0`, negative Werte, `NaN`, `Infinity`, perfekt gleiche Intervalle (RMSSD 0 → keine HRV), ein Ausreißer und Rundung erst im UI.

### 4.3 Rohdatenqualität

Die Formel setzt verwertbare Schlagintervalle voraus. Roh-RR/PPI-Daten sollten nicht ungefiltert als NN-Daten verwendet werden. Pro Messung sind mindestens zu speichern:

- Quelle und Messart (`morningPhone`, `morningBand`, `overnightBand`, Import);
- Start/Ende, lokaler Tag, Dauer und Sensor;
- rohe RMSSD in ms und normierte HRV;
- Ruhepuls, falls separat berechnet;
- Zahl empfangener und verwendeter Intervalle;
- Qualitätsstatus und Ausschlussgrund;
- Algorithmusversion.

Eine konkrete Artefaktfilterung ist weder durch die fehlende native Engine noch durch die öffentliche Formel belegt. Für EmilysHub muss sie als eigener, versionierter Verarbeitungsschritt mit Tests definiert werden; sie darf nicht nachträglich als „Visible-Filter“ bezeichnet werden.

### 4.4 Morgen- und Nachtmessungen

**Morgenmessung:** eine Minute, möglichst direkt nach dem Aufwachen, gleiche Position und gleiche Zeit, ruhig und bewegungsfrei. Kamera und Band verwenden PPG. [V-PHONE] [V-BAND]

**Nachtmessung:** vier Stunden, beginnend 30 Minuten nach dem Einschlafen; nur ruhige Abschnitte, Wach- und Bewegungsphasen werden ignoriert. Visible mittelt die verbleibenden Messwerte. [V-NIGHT-HR]

**Getrennte Baselines:** Morgen- und Nachtwerte unterscheiden sich systematisch und werden jeweils nur mit derselben Messart verglichen. Nach einem Wechsel kann erneut eine Aufbauphase von vier Tagen nötig sein. [V-PREFS]

## 5. Stability-Logik: belegt und offen

### 5.1 Eingaben und zeitliche Bezüge

| Faktor | Eingabe | Richtung laut Visible |
|---|---|---|
| Ruhepuls | Morgen- oder Nachtmessung | innerhalb/niedriger als Normalbereich tendenziell stabil |
| HRV | normierte HRV aus derselben Messart | höher als Mittel oft positiv; sehr große Ausschläge können instabil sein |
| Schlaf | Morning Check-in, 1–4 | bessere Bewertung und besser als üblich positiv |
| Symptome | vorheriger Evening Check-in, 0–3 je Symptom | geringere Ausprägung als üblich positiv |

Die Symptome des Abends beeinflussen den Score des **folgenden** Tages. Fehlt dieser Check-in, wird mit Ruhepuls, HRV und Schlaf weitergerechnet. Crash wird separat erfasst und zählt nicht als Symptomwert. [V-STAB] [V-EVENING]

Schlafbewertung: `1 = Awful`, `2 = Bad`, `3 = Fair`, `4 = Good`. Eine nachträglich geänderte Schlafbewertung löst bei Visible **keine** Neuberechnung des Stability Score aus. Gelöschte Morgenmessungen bleiben dagegen in der 90-Tage-Baseline. [V-MORNING]

### 5.2 Baseline und Einstufung

- Erster Score nach vier Morning Check-ins.
- Danach rollierender Normalbereich aus bis zu 90 Tagen historischer Daten.
- Personalisierung anhand der typischen biometrischen Schwankung mit einem **Variationskoeffizienten**.
- Jeder Faktor wird unabhängig eingestuft.
- HR/HRV: innerhalb, niedriger, höher oder deutlich höher als Normalbereich; größere Abweichungen wirken stärker.
- Schlaf/Symptome: ungefähr durchschnittlich, besser oder schlechter.
- Visible nutzt die exakten, nicht gerundeten Werte. [V-INTRO] [V-NORMAL] [V-STAB]

Ausgabe:

| Score | Bedeutung laut Visible |
|---:|---|
| 1 | „Out of balance“, stärkere reduzierte Resilienz; meist Ruhe priorisieren |
| 2 | „Out of balance“, mindestens ein instabiles Signal; eher ruhiger Tag |
| 3 | „Around your average“, durchschnittlich oder gemischte Signale |
| 4 | „Stable“, mindestens ein positiver Faktor |
| 5 | „Stable and improving“, mehrere positive Signale |

Der Score ist ausdrücklich kein Wellnesswert und keine Crash-Garantie oder Diagnose. [V-STAB]

### 5.3 Was nicht rekonstruiert werden darf

Nicht öffentlich bzw. nicht in dieser APK vorhanden sind:

- Formel und Grenzen des Normalbereichs aus dem Variationskoeffizienten;
- asymmetrische Toleranzen ober-/unterhalb der Baseline im aktuellen Algorithmus;
- Aggregation mehrerer Symptome zu einem Symptomfaktor;
- Gewichtung und Schwellen der vier Faktoren;
- Umgang mit einzelnen fehlenden/ungültigen Faktoren außer dem dokumentierten fehlenden Abend-Check-in;
- genaue Regel, wann ein hoher HRV-Ausschlag positiv oder instabil ist;
- serverseitige Modell- oder Versionslogik.

**Daher keine erfundene Z-Score-/Mittelwertformel als „Visible-Algorithmus“ speichern.** Ein robuster Ersatz (etwa Median/MAD oder Mittelwert/SD mit konfigurierbaren Bändern) wäre ein **neuer EmilysHub-Algorithmus** und braucht eigenen Namen, Versionsnummer, Tests und medizinische Produktentscheidung.

### 5.4 Sichere Implementierungsstufe für EmilysHub

[Vorschlag] Zunächst ohne unbelegten 1–5-Score:

1. Vier Faktorkarten mit exakten Messwerten und getrennten 90-Tage-Baselines anzeigen.
2. Zustände `buildingBaseline`, `ready`, `insufficientData`, `invalidMeasurement` modellieren.
3. Faktor- und Verlaufsdaten speichern; keine automatische medizinische Tagesempfehlung.
4. Erst nach separater Festlegung eine versionierte `EmilyStabilityV1`-Aggregation ergänzen.
5. Bei späterer Visible-Parität vollständige ABI-Splits und/oder Hersteller-Spezifikation sowie Testvektoren beschaffen.

## 6. Tägliches Symptomtracking

### 6.1 Evening Check-in

Einmal pro Tag am Abend; bei Visible beginnt ein neuer App-Tag um **04:00 Uhr**. Frühere Tage können ergänzt und Antworten später geändert werden. „Previous answers“ kann wiederkehrende Angaben übernehmen. [V-EVENING]

| Bereich | Inhalt und Skala |
|---|---|
| Crash | Ja/Nein; eigenes Ereignis, nicht in den Symptomfaktor einrechnen |
| Symptome | vordefiniert und benutzerdefiniert; aktiviert/ausgeblendet; jeweils schlimmste Ausprägung des Tages |
| Symptomwert | `0 = keine`, `1 = leicht`, `2 = mäßig`, `3 = schwer`; fehlend ≠ 0 |
| Belastung | körperlich, kognitiv/mental, emotional; in der Tracker-Verwaltung zusätzlich sozial |
| Weitere Faktoren | Periode, Infektion/Krankheit |
| Medikamente | eigene Einträge, Einnahme ja/nein |
| Lifestyle | eigene Tracker für Gewohnheiten/Verhalten |
| Notiz | Freitext für Tageskontext |

Quellen nennen an einer Stelle „physical, mental, emotional“, an der Tracker-Stelle „Cognitive, Emotional, Physical and Social“. [V-EVENING] [V-ADD]  
[Vorschlag] EmilysHub verwendet stabil die IDs `physical`, `cognitive`, `emotional`, `social`; „mental“ ist ein UI-Alias von `cognitive`. Für die numerische Belastungsskala liegt in den ausgewerteten Quellen keine eindeutige Angabe vor und muss gesondert festgelegt werden.

Die einmal tägliche Erfassung ist auch eine bewusste Reduktion der Tracking-Last; mehrmaliges symptomzentriertes Abfragen ist nicht erforderlich. [V-ONCE]

### 6.2 Morning Check-in

- Schlafbewertung 1–4 wie oben.
- Bei freier App einminütige Kameramessung; bei Band einminütige Messung oder Nachtdaten.
- Danach Stability mit Faktoraufschlüsselung.
- Frühere Schlafwerte editierbar, aber keine rückwirkende Score-Neuberechnung. [V-MORNING]

### 6.3 Trends, Bericht und Export

- **Trends:** ein Faktor oder Vergleich zweier Faktoren; Zeiträume 2 Wochen, 1, 3 oder 6 Monate.
- **Trendlinie:** trailing 3-Tage-Mittel, Tag einbezogen.
- **Health Report:** HR/HRV als 2-Wochen-rollierender Mittelwert; fünf am häufigsten berichtete Symptome als 7-Tage-rollierende mittlere Ausprägung.
- **CSV:** eine Beobachtung pro Zeile mit `observation_date`, `tracker_name`, `tracker_category`, `observation_value`. [V-TRENDS] [V-REPORT] [V-EXPORT]

[Vorschlag] Fehlende Tage nicht als 0 auffüllen. Rollierende Mittel nur über vorhandene Werte bilden und zusätzlich die Anzahl einbezogener Tage ausgeben; sonst entsteht künstliche „Genesung“ durch fehlende Einträge.

## 7. Monthly Check-in (aktuelle PROMIS-Version)

### 7.1 Ablauf

- Öffnet alle 30 Tage und bleibt 7 Tage geöffnet.
- Fortschritt wird nach jedem Abschnitt gespeichert.
- Verpasste Check-ins können nach Ablauf nicht nachgeholt werden; der nächste öffnet planmäßig.
- 18 Kernfragen: Fatigue 6, Thinking 4, Movement 4, Social Roles 4.
- Optional: Symptom Delay 3 und Pain Interference 1.
- Nach 3, 6 und 12 Monaten kommt eine zusätzliche Verlaufsfrage hinzu; ihr genauer Wortlaut ist in der Quelle nicht angegeben.
- Zusammenfassung je Abschnitt, Vergleich mit dem vorherigen Check-in nur für damals ebenfalls ausgefüllte Abschnitte, optionale Reflexionsnotiz je Kernabschnitt. [V-MONTH] [V-MONTH-WINDOW] [V-MONTH-SUM]

### 7.2 Fragen und Antworten

Die folgenden Inhalte sind aus der offiziellen aktuellen Visible-Hilfe übernommen. [V-MONTH-Q]

#### Fatigue – 6 Fragen, Zeitraum 7 Tage

Antworten jeweils: **Not at all / A little bit / Somewhat / Quite a bit / Very much**.

1. During the past 7 days I feel fatigued.
2. During the past 7 days I have trouble starting things because I am tired.
3. In the past 7 days how run down do you feel on average?
4. In the past 7 days how fatigued were you on average?
5. In the past 7 days how much were you bothered by your fatigue on average?
6. In the past 7 days, to what degree did your fatigue interfere with your physical functioning?

#### Thinking / Cognitive Function – 4 Fragen, Zeitraum 7 Tage

Antworten jeweils: **Never / Rarely (once) / Sometimes (two or three times) / Often (about once a day) / Very often (several times a day)**.

1. In the past 7 days my thinking has been slow.
2. In the past 7 days it has seemed like my brain was not working as well as usual.
3. In the past 7 days I have had to work harder than usual to keep track of what I was doing.
4. In the past 7 days I have had trouble shifting back and forth between different activities that require thinking.

#### Movement / Physical Function – 4 Fragen, kein 7-Tage-Zeitraum im Wortlaut

Antworten jeweils: **Without any difficulty / With a little difficulty / With some difficulty / With much difficulty / Unable to do**.

1. Are you able to do chores such as vacuuming or yard work?
2. Are you able to go up and down stairs at a normal pace?
3. Are you able to go for a walk for at least 15 minutes?
4. Are you able to run errands and shop?

#### Social Roles and Activities – 4 Fragen

Antworten jeweils: **Never / Rarely / Sometimes / Usually / Always**.

1. I have trouble doing all of my regular leisure activities with others.
2. I have trouble doing all of the family activities that I want to do.
3. I have trouble doing all of my usual work (include work at home).
4. I have trouble doing all of the activities with friends that I want to do.

#### Symptom Delay / PEM – 3 optionale Fragen, Zeitraum 1 Monat

Antworten jeweils: **Not at all / An hour or less / A few hours / Half a day / Most of a day / A full day / No worsening**.

1. Over the last month, how long can you engage in physical activity, before you feel significantly worse the next day?
2. Over the last month, how long can you engage in focused mental activity, before you feel significantly worse the next day?
3. Over the last month how long can you engage in emotionally demanding situations, before you feel significantly worse the next day?

Diese drei Fragen sind Visible-eigen und **nicht PROMIS**; sie erhalten keine PROMIS-T-Scores. [V-MONTH-Q]

#### Pain Interference – 1 optionale Frage

**How much did pain interfere with your day to day activities?**  
Antworten: **Not at all / A little bit / Somewhat / Quite a bit / Very much**. Die Visible-Zusammenfassung zeigt hier die gewählte Antwort, nicht zwingend einen eigenen T-Score. [V-MONTH-Q] [V-MONTH-SUM]

### 7.3 PROMIS-Auswertung

- Pro Kernabschnitt gibt es einen eigenen T-Score, keinen Gesamtscore.
- T-Skala: Mittelwert 50, Standardabweichung 10 der Referenzpopulation.
- **Fatigue:** höher = stärker betroffen/schlechter.
- **Thinking, Movement, Social Roles:** niedriger = stärker betroffen/schlechter.
- Visible zeigt zusätzlich `Normal limits`, `Mild`, `Moderate`, `Severe`; die T-Zahlen sind standardmäßig ausgeblendet. [V-MONTH-SUM]
- HealthMeasures empfiehlt Response-Pattern-Scoring. Für Raw-Sum-Tabellen müssen alle Fragen eines Formulars beantwortet sein; fehlende Antworten erfordern das Response-Pattern-Verfahren. [HM-SOCIAL] [HM-COGNITIVE]

[Vorschlag für die Offline-App]

1. Offizielle Item-IDs, Antwort-Rohwerte und Raw-Sum→T-Tabellen der exakt verwendeten Formversionen als versionierte lokale Daten hinterlegen.
2. Nicht pauschal die Listenposition 1–5 als Rohwert verwenden: Negative und positive Domänen haben unterschiedliche Kodierrichtungen. Die offiziellen Itemformulare sind vor Implementierung abzugleichen.
3. Raw-Sum-Tabellen nur bei vollständig beantwortetem Abschnitt verwenden. Fehlende Antworten nicht imputieren und keinen T-Score erfinden.
4. Cut-Points aus der offiziellen HealthMeasures-Darstellung übernehmen und mit Grenzfalltests belegen, statt aus dem T-Mittel selbst Schwellen zu schätzen. [HM-CUT]
5. Keine Online-Scoring-API als Abhängigkeit: EmilysHub ist vollständig offline und die Doku enthält Gesundheitsdaten. [EMILY-SPEC]

Offizielle Auswertungsquellen für die Umsetzung:

- Fatigue User Manual and Scoring Instructions [HM-FATIGUE]
- Cognitive Function Scoring Manual [HM-COGNITIVE]
- Physical Function User Manual and Scoring Instructions [HM-PHYSICAL]
- Ability to Participate in Social Roles and Activities Scoring Manual [HM-SOCIAL]
- Score Cut Points [HM-CUT]

### 7.4 Legacy: FUNCAP27 nicht mit PROMIS vermischen

Visible 26.10.1 ersetzt FUNCAP27 im Monthly Check-in durch PROMIS. Alte FUNCAP27-Ergebnisse bleiben separat sichtbar und sind **nicht** in PROMIS-Ergebnisse umrechenbar. FUNCAP27 verwendet 27 Aktivitäten in acht Bereichen, Antworten 0–6 und den Durchschnitt als Gesamtergebnis 0–6; höher ist besser. [V-FUNCAP]

## 8. EmilysHub-Datenmodell und Modulgrenzen

[Vorschlag] Die vorhandene Doku-Insel mit eigener SQLCipher-Datenbank, `AssessmentEntry`/`AssessmentAnswer` und numerischen Messungen ist der passende Ort. Die allgemeine App-Suche, Heute-Ansicht und Benachrichtigungen dürfen keine Inhalte der Gesundheitsdaten erhalten. [EMILY-SPEC]

### 8.1 Fachliche Entitäten

```text
TrackerDefinition
  id, patientId?, kind, stableKey, displayName, custom
  enabled, position, createdAt, archivedAt
  kind = symptom | exertion | medication | lifestyle | period | infection

EveningCheckIn
  id, patientId, localDay, updatedAt, note
  crash: Boolean?

SymptomRating
  checkInId, trackerId, severity0To3?       // null = nicht erfasst

FactorValue
  checkInId, trackerId, valueKind, numericValue?, boolValue?

MorningCheckIn
  id, patientId, localDay, sleep1To4?, updatedAt

HrvMeasurement
  id, patientId, morningCheckInId?
  sourceKind, measuredAtUtc, localDay, durationSeconds
  rmssdMs: Double?, hrvScore: Double?
  restingHrBpm: Double?
  intervalsReceived, intervalsUsed
  quality, exclusionReason, algorithmVersion

MonthlyCheckIn
  id, patientId, openedDay, closesDay, completedAt?
  formVersion, comparisonBaseId?

MonthlyAnswer
  checkInId, sectionId, itemId, responseId, numericRawValue?

MonthlyResult
  checkInId, sectionId, rawSum?, tScore?, standardError?
  severityBand, scoringTableVersion, reflectionNote
```

**Wichtig:** `null` immer als „nicht erhoben/nicht auswertbar“ behandeln, niemals wie Symptomfreiheit `0` oder eine neutrale Bewertung.

### 8.2 Reine, testbare Module

- `HrvEvaluation`: RMSSD und Visible-kompatible HRV-Transformation.
- `MeasurementQuality`: quellenspezifische Validierung und Ausschlussgründe.
- `PersonalBaseline`: rollierende 90-Tage-Historie, getrennt nach Patient **und Messart**, keine zukünftigen Werte.
- `DailyCheckIns`: 04:00-Tagesgrenze, Vortagssymptome, Editierregeln.
- `PromisScoring`: offizielle versionierte Tabellen und Cut-Points.
- `TrendSeries`: 3-/7-/14-Tage-rollierende Mittel mit Anzahl und Lücken.
- `EmilyStability`: separater, erst nach Spezifikation aktivierter Aggregator.

### 8.3 Mindesttests

- HRV-Testvektoren aus §4.2 und alle ungültigen Eingaben.
- Kein Datenleck: aktueller Tag und Zukunft nicht in der eigenen Baseline.
- Morgen und Nacht mischen sich nie in einer Baseline.
- Vier gültige Check-ins als Aufbau-Schwelle; ungültige Messungen zählen nicht.
- Fehlender Evening Check-in lässt die drei übrigen Faktoren unverändert.
- Symptom `null` ist nicht `0`; Crash ist kein Symptomrating.
- Vortageslogik und 04:00-Grenze einschließlich DST und Zeitzone.
- Schlaf-Edit erzeugt keine Neuberechnung, falls Visible-Verhalten übernommen wird.
- Monatsfenster 30/7 Tage, Abschnitte einzeln speichern, verpasstes Fenster geschlossen.
- PROMIS vollständige/unvollständige Abschnitte, alle Tabellen-Grenzwerte und Cut-Points.
- Trends mit fehlenden Tagen und weniger als 3/7/14 vorhandenen Werten.
- Patientenwechsel, gelöschte Patienten und Kaskaden innerhalb der Doku-Datenbank.
- Keine Gesundheitsdaten in Logs, allgemeiner Suche, unverschlüsseltem Export oder Benachrichtigungen.

## 9. Offene Punkte vor einer 1:1-Integration

1. Vollständiges Split-Set derselben APK-Version besorgen; insbesondere `libapp.so` und `heartbeat-android.so`.
2. Prüfen, ob Stability zusätzlich serverseitig berechnet wird und ob Exportdaten/Testvektoren verfügbar sind.
3. Exakte Stability-Schwellen, Gewichte, Symptomaggregation und Normalbereichsformel nur aus belegten Quellen ergänzen.
4. Offizielle PROMIS-Itemformulare und Scoring-Tabellen exakt den sechs Fragengruppen zuordnen; deutsche lizenzierte Übersetzungen und deren Scoring prüfen.
5. Entscheiden, ob EmilysHub nur HR/RMSSD importiert oder eine eigene Kamera-/Bandaufnahme entwickelt. Letzteres ist ein deutlich größeres Signalverarbeitungsprojekt.
6. Medizinisches Sicherheitskonzept: kein Diagnoseanspruch, keine Crash-Vorhersage, keine unvalidierten Belastungsanweisungen; subjektives Befinden hat neben jeder Kennzahl sichtbar zu bleiben.

## 10. Quellen

### APK – Primärbefunde

- **[APK-MANIFEST]** `REA/com.makevisible.visible.apk`, `AndroidManifest.xml`, Attribute `requiredSplitTypes`, Metadaten `com.android.vending.splits.required`; APK-Badging mit Paket/versionName/versionCode.
- **[APK-ZIP]** ZIP-Inventar derselben Datei: 1798 Einträge, `classes.dex` bis `classes5.dex`, keine `lib/`-Einträge, keine `libapp.so`/`heartbeat-android.so`.
- **[APK-HAPPITECH]** `classes4.dex`: `com.happitech.heartbeatandroid.D.a()` (`loadLibrary`); `AbstractC1109c` Konstruktor, `setMode`, `setMeasurementTimeSeconds`, `isHighQuality`, `getQualityLevel`, innerer Listener `g.on_measurement_completed`; `HeartMonitor`.
- **[APK-CONVERT]** `classes4.dex`: `com.happitech.heartbeatandroid.wrapper.ME_TimeHRV`, Getter `getAvnn_ms`, `getSdnn_ms`, `getRmssd_ms`, `getPnn50_ms`, `getConfidence_percent`, `getStress_index`; `TimeHRV`; `Y.a(ME_TimeHRV)`; `FrequencyHRV`.
- **[APK-DB]** `classes3.dex`: `com.example.bluetooth_foreground_service.AppDatabase_Impl`, Tabellen `heart_rate`, `ppi`, `acc`, `skin_temperature`; `xa.d(Cursor)` und `xa.r(...)` für Nightly Recharge; `p4.x(long,long)` für Minuten-HR.
- **[APK-SYMPTOMS]** `classes4.dex`: `com.happitech.heartbeatandroid.model.SymptomsModel`; `util.PreferencesUtil.getSymptoms`; `util.report.MedicalReportGenerator`, Abschnitt zum Laden/Ausgeben der Symptomliste.
- **[APK-ONNX]** `assets/flutter_assets/packages/pacepoints_ml/assets/model/v1_1min_opset18.onnx`, Input `features: FLOAT[n_rows,12]`, Output `exertion: FLOAT[n_rows,3]`; `classes4.dex`: `com.makevisible.pacepoints_ml.a`, `b`, `c`, `PacePointsMlNative`.

Reproduktion der statischen Extraktion:

```bash
apktool d -f -s -o /tmp/rea-visible/apktool REA/com.makevisible.visible.apk
jadx -d /tmp/rea-visible/jadx --show-bad-code --no-imports REA/com.makevisible.visible.apk
aapt dump badging REA/com.makevisible.visible.apk
sha256sum REA/com.makevisible.visible.apk
```

JADX meldete bei den insgesamt 12.864 Klassen 159 Dekompilierungsfehler. Die oben zitierten Fundstellen waren lesbar; daraus wurde keine Vollständigkeit aller Pfade behauptet.

### Offizielle Visible-Quellen

- **[V-HRV]** Visible Help Center, „What is heart rate variability (HRV)?“, Abschnitt „How do we calculate your HRV?“: https://help.makevisible.com/en/articles/10125500-what-is-heart-rate-variability-hrv
- **[V-STAB]** „Understanding your Stability Score“: https://help.makevisible.com/en/articles/17115224-understanding-your-stability-score
- **[V-INTRO]** „An introduction to the Stability Score“: https://help.makevisible.com/en/articles/10125371-an-introduction-to-the-stability-score
- **[V-NORMAL]** „What is my normal range?“: https://help.makevisible.com/en/articles/10125529-what-is-my-normal-range
- **[V-PREFS]** „Checking or changing your Stability preferences“: https://help.makevisible.com/en/articles/17621323-checking-or-changing-your-stability-preferences
- **[V-MORNING]** „Completing your Morning Check-in“: https://help.makevisible.com/en/articles/12276375-completing-your-morning-check-in
- **[V-PHONE]** „Getting your Stability Score with a morning reading from your phone“: https://help.makevisible.com/en/articles/17629581-getting-your-stability-score-with-a-morning-reading-from-your-phone
- **[V-BAND]** „Getting your Stability Score with a morning reading from your band“: https://help.makevisible.com/en/articles/17628362-getting-your-stability-score-with-a-morning-reading-from-your-band
- **[V-NIGHT]** „Getting your Stability Score from overnight data“: https://help.makevisible.com/en/articles/14982301-getting-your-stability-score-from-overnight-data
- **[V-NIGHT-HR]** „How Stability (overnight data) measures your HR and HRV“: https://help.makevisible.com/en/articles/14979704-how-stability-overnight-data-measures-your-hr-and-hrv
- **[V-EVENING]** „Evening Check-in“: https://help.makevisible.com/en/articles/10156905-evening-check-in
- **[V-ONCE]** „Why do we only ask you to record your symptoms once a day?“: https://help.makevisible.com/en/articles/10125165-why-do-we-only-ask-you-to-record-your-symptoms-once-a-day
- **[V-ADD]** „How do I add a symptom or medication to the Evening Check-in?“: https://help.makevisible.com/en/articles/9583049-how-do-i-add-a-symptom-or-medication-to-the-evening-check-in
- **[V-TRENDS]** „Trends“: https://help.makevisible.com/en/articles/12149164-trends
- **[V-REPORT]** „Health Report PDF“: https://help.makevisible.com/en/articles/9583075-health-report-pdf
- **[V-EXPORT]** „Data export“: https://help.makevisible.com/en/articles/9583077-data-export
- **[V-MONTH]** „An introduction to the Monthly Check-in“: https://help.makevisible.com/en/articles/17757397-an-introduction-to-the-monthly-check-in
- **[V-MONTH-Q]** „What the Monthly Check-in asks you“: https://help.makevisible.com/en/articles/17757584-what-the-monthly-check-in-asks-you
- **[V-MONTH-SUM]** „Understanding your Monthly Check-in summary“: https://help.makevisible.com/en/articles/17757668-understanding-your-monthly-check-in-summary
- **[V-MONTH-WINDOW]** „When your Monthly Check-in opens and closes“: https://help.makevisible.com/en/articles/17757497-when-your-monthly-check-in-opens-and-closes
- **[V-FUNCAP]** „Your Functional Capacity results“: https://help.makevisible.com/en/articles/9583619-your-functional-capacity-results

Historische Herstellerhinweise, nur zur Versionsentwicklung (nicht als aktuelle Schwellen verwenden):

- Morning Stability, 26.01.2023: https://www.makevisible.com/blog/introducing-the-morning-stability-score
- v1.1, 90-Tage-Baseline und personalisierte Normalbereiche: https://www.makevisible.com/blog/introducing-the-morning-stability-score-1-1
- v1.2, asymmetrisch breiterer oberer HRV-Bereich: https://www.makevisible.com/blog/introducing-the-morning-stability-score-v1-2
- aktuellere Anpassung kleiner Fluktuationen, 23.09.2026: https://www.makevisible.com/blog/a-more-accurate-stability-score

### Offizielle HealthMeasures-/PROMIS-Quellen

- **[HM-SCORE]** „Calculate Scores“: https://healthmeasures.net/implement/scoring/calculate-scores/
- **[HM-CUT]** „Score Cut Points“ und Adult-Ansicht: https://healthmeasures.net/interpret-scores/score-cut-points/ und https://healthmeasures.net/interpret-scores/score-cut-points/adult/
- **[HM-FATIGUE]** PROMIS Fatigue User Manual and Scoring Instructions: https://healthmeasures.net/wp-content/uploads/2026/05/PROMIS-Fatigue-User-Manual-and-Scoring-Instructions_21Nov2025.pdf
- **[HM-COGNITIVE]** PROMIS Cognitive Function Scoring Manual: https://healthmeasures.net/wp-content/uploads/2026/05/PROMIS-Cognitive-Function-Scoring-Manual_03June2022.pdf
- **[HM-PHYSICAL]** PROMIS Physical Function User Manual and Scoring Instructions: https://healthmeasures.net/wp-content/uploads/2026/05/PROMIS-Physical-Function-User-Manual-and-Scoring-Instructions_24Nov2025.pdf
- **[HM-SOCIAL]** PROMIS Ability to Participate in Social Roles and Activities Scoring Manual: https://healthmeasures.net/wp-content/uploads/2026/05/PROMIS-Ability-to-Participate-in-Social-Roles-and-Activities-Scoring-Manual_05Dec2023.pdf

### EmilysHub

- **[EMILY-SPEC]** `SPEC.md`, besonders §1, §9 (Sicherheit/Datenschutz) und §14 (Dokumentation); `app/src/main/java/de/emilyshub/doku/AssessmentEntry.kt`; `app/src/main/java/de/emilyshub/doku/Measurements.kt`; `app/src/main/java/de/emilyshub/doku/assessments/Scoring.kt`.
