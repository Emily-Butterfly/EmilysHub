# Garmin-Werte in Tracking: Datenwege, Aussagekraft, Pacing-Schwellen

- **Zweck:** Grundlage für SPEC.md §17 (Garmin über Health Connect, Stufen, Pacing-Schwelle) und für die zweite Stufe von Tracking (FIT-Import, `docs/issues/ohne-ticket.md` §1)
- **Stand:** Recherche vom 15.11.2026 gegen Garmin-Handbücher und -Entwicklerseiten, Android-Doku und begutachtete Studien

**Lücken in den Quellen:** Die Garmin-Support-FAQ zu Health Connect (https://support.garmin.com/en-US/?faq=JToBEy0jfe6pIygark2Ui5) und Garmins „Health Science“-Seiten ließen sich nicht automatisch abrufen. Alles, was nur aus Forum oder Fachpresse stammt, ist so gekennzeichnet; Unbelegtes steht als **[OFFEN]**.

## 1. Kernaussagen

1. Garmin Connect schreibt seit 2025 nach Health Connect, ab Android 14. Sicher geteilt werden Herzfrequenz, Schlaf, Schritte und Aktivitäten; HRV-Status und Body Battery werden **nicht** geteilt. [HC-NEWS] [AP] [AC]
2. Vollständig mit HRV, Stress, Body Battery und SpO₂ sind nur die **FIT-Dateien** der Uhr. Sie kommen per USB-OTG oder aus dem Datenexport von Garmin Connect. [FIT-PROFILE]
3. Am besten überprüft sind **Schritte** und die **Herzfrequenz in Ruhe**. Nacht-HRV und Schlafdauer sind mittelmäßig überprüft. Schlafphasen, Intensitätsminuten, Stress und SpO₂ sind schwach überprüft. Ruhepuls, HRV-Status, Sleep Score und Body Battery sind gar nicht unabhängig überprüft. Daraus folgen die Stufen A/B/C in §17.
4. Ein Brustgurt (z. B. HRM-Fit) misst elektrisch und auch bei Bewegung genau. Die Uhr nutzt seine Werte laut Handbuch aber nur **während aufgezeichneter Aktivitäten**. Ob Health Connect den Gerätetyp „Brustgurt“ mitliefert, zeigt erst ein echtes Gerät.
5. Für Herzfrequenz-Pacing gibt es **keine gut belegte Schwelle**. Am individuellsten ist die anaerobe Schwelle aus einer Spiroergometrie. „Ruhepuls + 15“ ist eine Faustregel, „Minuten über der Schwelle“ hat keine belegte Definition.

## 2. Datenwege

### 2.1 Health Connect (in Etappe 15)

- **Seit wann:** laut Google ab Android 14 [HC-NEWS]; laut Forum seit Garmin Connect 5.14.1 vom 7.7.2025 [GF-HC].
- **Richtung:** nur Garmin → Health Connect.
- **Geteilt:** Herzfrequenz, Schlaf, Schritte und Distanz, Kalorien, Stockwerke, Gewicht, Körperfett, Aktivitäten. [AP]
- **Nicht geteilt:** Body Battery, Training Load, HRV-Status. [AC]
- Health Connect hätte eigene Typen für HRV (RMSSD), Ruhepuls, SpO₂, Atmung und Blutdruck, aber keine für Stress und Body Battery. [HC-TYPES]
- **[OFFEN]:** ob Ruhepuls, SpO₂ und Atmung geteilt werden (die Sekundärquellen widersprechen sich); Auflösung der Herzfrequenz, Sync-Takt, Regionen und Gerätemodelle.
- **Einschalten:** in Garmin Connect (Einstellungen → Health Connect) und je Datentyp in Health Connect. Das stammt nur aus Sekundärquellen.
- **Berechtigungen:** Ohne Zusatzrecht liest eine App nur die letzten 30 Tage. Älteres braucht `READ_HEALTH_DATA_HISTORY`, Lesen im Hintergrund `READ_HEALTH_DATA_IN_BACKGROUND`. Die Daten liegen lokal auf dem Gerät. [HC-TYPES] [HC-LOCAL]
- **Gerätetyp:** Health Connect kennt `Device.TYPE_CHEST_STRAP` (7) und `TYPE_WATCH` (1). Das Feld setzt aber die schreibende App, und es fehlt, wenn sie es nicht befüllt. [HC-DEVICE] **[OFFEN]:** ob Garmin Connect es je Herzfrequenz-Eintrag setzt.
- Garmin Connect selbst synchronisiert weiter mit Garmins Cloud; „ohne Cloud“ gilt nur für Tracking.

### 2.2 FIT-Dateien (zweite Stufe)

- **Inhalt laut FIT-Profil:**
  - Herzfrequenz (`monitoring.heartRate`) und Ruhepuls (`monitoring_hr_data.restingHeartRate`, `currentDayRestingHeartRate`)
  - HRV als `hrv_value` (5-Minuten-RMSSD in ms) und als HRV-Status-Zusammenfassung `hrv_status_summary` (7-Tage-Mittel, letzte Nacht, Baseline)
  - Schlagintervalle (`hrv.time`), Schlafstufen (`sleep_level`, `sleep_assessment`), SpO₂ (`spo2_data`), Stress (`stress_level`), Atmung (`respiration_rate`)

  [FIT-PROFILE]
- **Lizenz** des FIT SDK: gebührenfrei, „for Licensee's internal business purposes“. [FIT-LICENSE]
- **Per USB:** Android liest MTP-Geräte über `android.mtp.MtpDevice` (USB-OTG). [MTP]
- **Ordner auf der Uhr** (Monitor, Sleep, HRVStatus) sind nur aus Forum und Drittanbietern belegt. Laut einem Nutzer löscht der Sync mit Garmin Connect diese Dateien von der Uhr. [GF-USB] **[OFFEN]**
- **Datenexport:** Antrag im Web (garmin.com/account/datamanagement), ZIP per E-Mail-Link. Laut Forum enthält er `_HRV_STATUS.fit`-Dateien. [GF-EXPORT] **[OFFEN]**

### 2.3 Verworfen

- **Connect IQ Mobile SDK:** Es kommuniziert immer über die Garmin-Connect-App per Bluetooth LE. [CIQ-MOBILE]
  - Das Paket `ciq-companion-app-sdk` 2.4.0 verlangt keine INTERNET-Berechtigung.
  - Eine eigene Uhr-App sähe Verläufe von Herzfrequenz, Stress, Body Battery und SpO₂ und den Durchschnitts-Ruhepuls, aber keine Nacht-HRV. Die Herzfrequenz-Historie reicht nur bis zum letzten Neustart der Uhr. [CIQ-HISTORY]
  - Die Lizenz gilt „solely for developing and testing“.
  - Verworfen, weil es eine eigene Uhr-App bräuchte und keine Nacht-HRV liefert.
- **Garmin Health SDK:** nur für Firmenpartner, kommerziell lizenziert. [HEALTH-SDK]

## 3. Rechenweg und Validierung je Wert

| Wert | Gemessen/abgeleitet | Rechenweg | Validierung | Stufe (§17) |
|---|---|---|---|---|
| Herzfrequenz Handgelenk, Ruhe | gemessen (PPG) | proprietär | gut: Forerunner 955 gegen EKG ±0,5/min [CASANOVA-2026] | A |
| Herzfrequenz Handgelenk, Bewegung | gemessen (PPG) | proprietär | mittel bis schwach: CCC 0,81, Schlusslicht im Belastungstest [GILLINOV-2017]; Fehler über den Grenzwerten [EVENSON-2020] | B |
| Herzfrequenz Brustgurt | elektrisch gemessen | – | gut: HRM-Pro gegen EKG MAPE 1,96–3,73 %, CCC 0,95–0,99 (Marsch, Rad), schlechter im Tabata-Zirkel [MERRIGAN-2023]; HRM-Fit selbst nicht eigens geprüft | A |
| Schritte | abgeleitet (Beschleunigung) | proprietär | gut: 16 Studien, akzeptable Fehler [EVENSON-2020] | A |
| Ruhepuls (Garmin) | abgeleitet | laut Forum niedrigster 30-Minuten-Schnitt in 24 h [GF-RHR] [OFFEN] | keine Studie; Garmin aus einer Validierung ausgeschlossen, weil das Zeitfenster nicht offen ist [DIAL-2025] | B |
| Nacht-HRV (RMSSD) | abgeleitet aus optischen Schlagintervallen | proprietär | mittel: Fenix 6 gegen EKG, CCC 0,87, MAPE 10,5 %, von den Autoren „poor“ genannt [DIAL-2025]; Kurzmessungen schwach, ICC 0,24 [MILLER-2022] | B |
| HRV-Status, Baseline | 7-Tage-Mittel gegen persönliche Baseline, ab 3 Wochen Daten [FR265] | proprietär | keine gefunden | C |
| Schlafdauer | abgeleitet | proprietär | mittel: im Mittel 38 min zu lang, Wach nur zu 29 % erkannt [SCHYVENS-2025] | B |
| Schlafphasen, Sleep Score | abgeleitet | proprietär | schwach: κ 0,21–0,25 [SCHYVENS-2025] [MILLER-2022]; Sleep Score keine | C |
| Intensitätsminuten | Herzfrequenz gegen Ruhepuls, ohne Herzfrequenz Schritte pro Minute [GARMIN-IM] | Schwellen nicht offen | schwach: überschätzt, nur Ältere geprüft [BRIGGS-2022] | C |
| Stress (0–100) | abgeleitet aus HRV in Ruhe (Firstbeat) | Prinzip im Whitepaper, Formel nicht offen [FIRSTBEAT] | schwach bis mittel; kaum Zusammenhang mit selbstberichtetem Stress [ROSENBACH-2025] [SIEPE-2025] | C |
| Body Battery (5–100) | abgeleitet aus HRV, Stress, Schlaf, Aktivität | proprietär | keine gegen Referenz; schwacher Bezug zu selbstberichteter Müdigkeit [SIEPE-2025] | C |
| SpO₂ (Pulse Ox) | gemessen, optisch | proprietär; laut Garmin nicht für medizinische Zwecke [GARMIN-DISCLAIMER] | schwach: in 4559 m im Mittel +7 Prozentpunkte, Grenzen −6,5 bis +20,5 [SCHIEFER-2021] | C |

## 4. Brustgurt HRM-Fit

- **Messung:** elektrisch über Elektroden auf der Rückseite, die man vor dem Tragen anfeuchtet. Er wird an das Unterbrustband eines Sport-BHs geklemmt. [HRMFIT]
- **Eigenes Tracking:** Der Gurt hat ein eigenes Activity Tracking mit „all-day heart rate“. Dafür wird er direkt mit Garmin Connect gekoppelt und sendet selbst, solange das Handy in Reichweite ist. Er speichert bis zu 18 h Aktivitätsverlauf. [HRMFIT]
- **Mit der Uhr:** Die Uhr nutzt die Gurt-Herzfrequenz nur während einer Aktivität (vívoactive 5: „Chest heart rate data is only available during an activity.“). Für die 24/7-Herzfrequenz und den Ruhepuls der Uhr bleibt der Handgelenkssensor maßgeblich. [VA5]
- **[OFFEN]:** wie Garmin Connect die Ganztags-Herzfrequenz von Gurt und Uhr zusammenführt; ob der Gurt Schlagintervalle speichert.

## 5. Herzfrequenz-Pacing-Schwellen

- **Ruhepuls + 15:** Faustregel der Workwell Foundation, 7-Tage-Mittel des Ruhepulses plus 15. Laut einer Erhebung gibt es dazu nur eine Studie, obwohl mehrere Leitlinien sie empfehlen. [WORK-2023] **[OFFEN]:** Primärdokument von Workwell und Validierung.
- **Prozent der maximalen Herzfrequenz:**
  - Wegen chronotroper Inkompetenz bei ME/CFS nicht verlässlich submaximal. [DAVENPORT-2019]
  - 50–60 % werden genannt, aber ohne kontrollierte Studien. [EMERGE]
- **Anaerobe (ventilatorische) Schwelle aus der Spiroergometrie:** am individuellsten. Bei ME/CFS fallen Leistung und VO₂ an der Schwelle am zweiten Testtag ab, daher ist der Wert vom zweiten Tag vorzuziehen. [KELLER-2024]
- **Wirksamkeit:** Die einzige randomisierte Machbarkeitsstudie (n = 47) ist nicht auf Wirksamkeit ausgelegt. [CLAGUE-BAKER-2025]
- **„Minuten über der Schwelle“:** keine belegte Definition. Clague-Baker 2025 spricht nur von „minimize time spent above the anaerobic threshold“. **[OFFEN]**
- **Betablocker:**
  - In der kardiologischen Reha liegen Ruhe-, Spitzen- und Schwellen-Herzfrequenz darunter niedriger; empfohlen werden dort angepasste Prozentwerte. [DIAZ-BUSCHMANN-2014]
  - Bei POTS senkte niedrig dosiertes Propranolol die Spitzen-Herzfrequenz und verbesserte die VO₂max, eine hohe Dosis nicht (n = 11). [ARNOLD-2013]
  - Für ME/CFS oder POTS ist nicht in Zahlen belegt, wie Betablocker die Schwellen-Herzfrequenz verschieben. **[OFFEN]**
- **Levine-Protokoll** bei POTS (Base pace ~75 % der vorhergesagten maximalen Herzfrequenz): ein Trainings-, kein Pacing-Konzept. [FU-LEVINE-2015]

## 6. Begründungen aus dem Interview vom 15.11.2026

Die Entscheidungen stehen in SPEC.md §17 unter „Entscheidungen“; hier die Gründe.

- **HRV erst in der zweiten Stufe:** Health Connect liefert keine HRV. Eine eigene Messung per Kamera oder Gurt wäre ein großes Projekt der Signalverarbeitung, und die Gewichte des Stability-Werts von Visible sind nicht belegt (`visible-apk-hrv-symptomtracking.md`).
- **Drei Stufen statt Zahlengewichten:** Ein Faktor wie 0,5 ist in einem Hinweis nicht nachvollziehbar. Die Stufen sagen klar, ob ein Wert einen Hinweis auslösen, nur stützen oder nur angezeigt werden kann. Eine Garmin-Schätzung kann so nie allein einen Arztbesuch nahelegen.
- **Garmin-Ruhepuls neben, nicht statt des Morgenwerts:** Garmin nimmt den niedrigsten Wert des Tages, meist aus der Nacht. Leitlinien-Schwellen wie > 100/min beziehen sich auf eine Messung wach im Liegen. Gegen sie geprüft würde der Nachtwert Auffälligkeiten verdecken.
- **Brustgurt nach Gerätetyp statt per Schalter:** Ein Schalter würde auch Zeiten ohne Gurt als Stufe A werten. Fehlt der Gerätetyp, bleibt es vorsichtig bei der Handgelenk-Regel.
- **Abendbelastung zählt direkt als Anstrengung:** Schritte übersehen Anstrengung ohne Gehen, kognitive und emotionale Anstrengung sieht kein Sensor.
- **Schritte und Herzfrequenz nur als Vorschlag:** Beide sind indirekt. Erst die Bestätigung macht daraus eine Anstrengung, und Vorschläge lösen nie einen Hinweis aus.
- **Pacing-Schwelle mit Ruhepuls + 15 als Ausgangswert:** Ohne eigenen Wert gäbe es sonst keine Herzfrequenz-Vorschläge. Die Kennzeichnung „schwach belegt“ und der Hinweis auf pulssenkende Medikamente halten die Grenze sichtbar.
- **Folgetage nur nach Stärke 2–3:** Sonst käme die Nachfrage fast jeden Abend und würde ignoriert.
- **Nachtragen bis 7 Tage:** An schlechten Tagen entstehen Lücken. Die stille Markierung erlaubt der Auswertung, Erinnertes von zeitnah Erfasstem zu unterscheiden.
- **Name „Tracking“:** Er verrät auf dem Bildschirm nichts über den Inhalt.

## 7. Quellen

Abgerufen am 15.11.2026.

| Kürzel | Quelle |
|---|---|
| AC | Android Central: Here's everything Garmin will and won't share with Google Health Connect. https://www.androidcentral.com/wearables/garmin/heres-everything-garmin-will-and-wont-share-with-google-health-connect |
| AP | Android Police: Garmin data sharing with Google Health Connect. https://www.androidpolice.com/garmin-data-sharing-google-health-connect-details/ |
| ARNOLD-2013 | Arnold AC et al., Neurology 2013. doi:10.1212/WNL.0b013e318293e310 |
| BRIGGS-2022 | Briggs et al., Front Sports Act Living 2022. doi:10.3389/fspor.2021.766317 |
| CASANOVA-2026 | Casanova-Lizón et al., Sensors 2026. doi:10.3390/s26206549 |
| CIQ-HISTORY | Garmin Connect IQ API: Toybox.SensorHistory. https://developer.garmin.com/connect-iq/api-docs/Toybox/SensorHistory.html |
| CIQ-MOBILE | Garmin Connect IQ: Mobile SDK for Android. https://developer.garmin.com/connect-iq/core-topics/mobile-sdk-for-android/ |
| CLAGUE-BAKER-2025 | Clague-Baker et al., Fatigue: Biomed Health Behav 2025. doi:10.1080/21641846.2025.2565103 |
| DAVENPORT-2019 | Davenport et al., Front Pediatr 2019. doi:10.3389/fped.2019.00082 |
| DIAL-2025 | Dial et al., Physiol Rep 2025. doi:10.14814/phy2.70527 |
| DIAZ-BUSCHMANN-2014 | Díaz-Buschmann et al., Eur J Prev Cardiol 2014. doi:10.1177/2047487313500214 |
| EMERGE | Emerge Australia: Pacing mit Herzfrequenz. https://emerge.org.au/?p=79499 |
| EVENSON-2020 | Evenson & Spade, J Meas Phys Behav 2020. doi:10.1123/jmpb.2019-0035 |
| FIRSTBEAT | Firstbeat: Stress and recovery white paper. https://www.firstbeat.com/wp-content/uploads/2015/10/Stress-and-recovery_white-paper_20145.pdf |
| FIT-LICENSE | Garmin FIT SDK, Lizenz. https://github.com/garmin/fit-javascript-sdk/blob/main/LICENSE.txt |
| FIT-PROFILE | Garmin FIT SDK, Profil 21.221. https://github.com/garmin/fit-javascript-sdk/blob/main/src/profile.js |
| FR265 | Garmin Forerunner 265, Benutzerhandbuch (HRV-Status, Stress, Body Battery). https://www8.garmin.com/manuals/webhelp/GUID-F41EAFB3-6CC9-42DE-9C6C-9E358DBB0671/EN-US/Forerunner_265_OM_EN-US.pdf |
| FU-LEVINE-2015 | Fu & Levine, Review 2015. https://pmc.ncbi.nlm.nih.gov/articles/PMC4336603 |
| GARMIN-DISCLAIMER | Garmin: Accuracy disclaimer. https://www.garmin.com/en-US/legal/atdisclaimer/ |
| GARMIN-IM | Garmin-Handbuch: Intensitätsminuten. https://www8.garmin.com/manuals/webhelp/GUID-9D99A9D4-467A-4F1A-A0EA-023184FEA3DD/EN-US/GUID-63522E07-AD5E-4D2D-B680-3129A2300238.html |
| GF-EXPORT | Garmin-Forum: HRV im Datenexport (sekundär). https://forums.garmin.com/apps-software/mobile-apps-web/f/garmin-connect-web/423807/hrv-in-garmin-data-export/1981421 |
| GF-HC | Garmin-Forum: Health Connect in der GC-App (sekundär). https://forums.garmin.com/apps-software/mobile-apps-web/f/garmin-connect-mobile-andriod/414652/health-connect-in-gc-android-app |
| GF-RHR | Garmin-Forum: Ruhepuls-Berechnung (sekundär). https://forums.garmin.com/apps-software/mobile-apps-web/f/garmin-connect-web/408358/heart-rate-inaccuracies |
| GF-USB | Garmin-Forum: Herunterladen per USB (sekundär). https://forums.garmin.com/sports-fitness/healthandwellness/f/index-sleep-monitor/423093/manual-downloading-via-usb-is-pratically-impossible |
| GILLINOV-2017 | Gillinov et al., Med Sci Sports Exerc 2017. https://pubmed.ncbi.nlm.nih.gov/28709155/ |
| HC-DEVICE | Android: Health Connect `Device`. https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/metadata/Device |
| HC-LOCAL | Google: Health Connect speichert Daten auf dem Gerät. https://support.google.com/android/answer/12201227 |
| HC-NEWS | Android Developers: Health & Fitness Newsletter 08/2025. https://developer.android.com/health-and-fitness/community/newsletters/2025/08 |
| HC-TYPES | Android: Health Connect Datentypen. https://developer.android.com/health-and-fitness/health-connect/data-types |
| HEALTH-SDK | Garmin Health SDK, Überblick. https://developer.garmin.com/health-sdk/overview/ |
| HRMFIT | Garmin HRM-Fit, Benutzerhandbuch. https://static.garmin.com/pumac/hrmfit_OM_EN-SG.pdf |
| KELLER-2024 | Keller et al., J Transl Med 2024. doi:10.1186/s12967-024-05410-5 |
| MERRIGAN-2023 | Merrigan et al., Meas Phys Educ Exerc Sci 2023. doi:10.1080/1091367X.2022.2161820 (nur Abstract) |
| MILLER-2022 | Miller et al., Sensors 2022. doi:10.3390/s22166317 |
| MTP | Android: `android.mtp.MtpDevice`. https://developer.android.com/reference/android/mtp/MtpDevice |
| ROSENBACH-2025 | Rosenbach et al., Stress Health 2025. doi:10.1002/smi.70125 |
| SCHIEFER-2021 | Schiefer et al., Sensors 2021. doi:10.3390/s21196363 |
| SCHYVENS-2025 | Schyvens et al., Sleep Adv 2025. doi:10.1093/sleepadvances/zpaf021 |
| SIEPE-2025 | Siepe et al., J Psychopathol Clin Sci 2025. doi:10.1037/abn0001013 |
| VA5 | Garmin vívoactive 5, Handbuch: Herzfrequenz vom Brustgurt. https://www8.garmin.com/manuals/webhelp/GUID-5D183A14-BB43-4A9B-B441-5F824214CE40/EN-US/GUID-CEE01C08-4EAD-4EF2-AF52-8033A9695572.html |
| WORK-2023 | Erhebung zu Pacing-Empfehlungen, Work 2023. doi:10.3233/WOR-220512 (nur sekundär zitiert) |
