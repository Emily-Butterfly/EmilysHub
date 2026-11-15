# Spaced Repetition für den Vokabeltrainer: FSRS-6

- **Anlass:** Ticket #104 (Vokabeltrainer). Ein Kasten mit fünf Fächern wurde am 12.11.2026 abgelehnt; gewünscht ist ein richtiger Algorithmus für verteiltes Wiederholen.
- **Stand:** 2026-11-12. Geprüft gegen die Referenzimplementierung `py-fsrs` 6.3.2 und die Formelseite des FSRS-Projekts.

## 1. Ergebnis in Kürze

1. **Empfehlung: FSRS-6** (Free Spaced Repetition Scheduler, 21 Parameter), mit den Standardparametern und einer gewünschten Behaltensquote von 90 %. Auf der Formelseite des Projekts ist FSRS-6 die neueste beschriebene Version, und `py-fsrs` 6.3.2 rechnet mit ihr. [W] [P]
2. **Selbst implementierbar:** Der Kern sind sieben kurze Formeln ohne Abhängigkeit. Er passt in ein reines Kotlin-Modul. [P]
3. **Kein Training nötig:** Die Referenz empfiehlt ausdrücklich, die Standardparameter zu lassen, wenn man sich mit dem Optimieren nicht auskennt; der Optimierer ist dort ein optionales Zusatzpaket. [R]
4. **Vier Antworten:** Nochmal (1), Schwer (2), Gut (3), Leicht (4). „Schwer“ heißt: gewusst, aber mit Mühe. Wer bei Vergessenem „Schwer“ drückt, bekommt zu lange Abstände. [W] [A-FSRS]
5. **Je Karte zwei Zahlen:** Stabilität S (in Tagen: der Abstand, nach dem die Erinnerung noch zu 90 % sitzt) und Schwierigkeit D (1 bis 10), dazu der Tag der letzten Abfrage und der nächste Fälligkeitstag. [W] [P]
6. **„Sicher“:** Anki nennt eine Karte „mature“, wenn ihr Abstand 21 Tage oder mehr beträgt. Das eignet sich als Schwelle für „sicher“. [A-REIF]
7. **Planung in ganzen Tagen:** Die Lernschritte in Minuten, die die Referenz vorsieht, lassen sich weglassen; sie unterstützt das selbst. Für die App reicht eine einfache Regel (§5). [P] [Vorschlag]
8. **SM-2** bleibt die einfachere Alternative (§7), hat aber keine Vorstellung von Vergessenswahrscheinlichkeit und behandelt verspätete oder verfrühte Abfragen nicht. [S]

## 2. Belegklassen

- **[W]** Formelseite des FSRS-Projekts (Wiki „The Algorithm“).
- **[P]** Quellcode der Referenzimplementierung `py-fsrs` 6.3.2 (`fsrs/scheduler.py`).
- **[T]** Tests der Referenzimplementierung (`tests/test_basic.py`).
- **[R]** README der Referenzimplementierung.
- **[A-…]** Anki-Handbuch.
- **[S]** Originalbeschreibung von SM-2.
- **[E]** Eigene Rechnung: eine eigene Nachbildung der Formeln aus [P], geprüft gegen die Ergebnisse aus [T] (§6). Die fremde Bibliothek selbst wurde nicht ausgeführt.
- **[Vorschlag]** Festlegung für EmilysHub, keine Aussage einer Quelle.

## 3. Vergleich

| | FSRS-6 | SM-2 |
|---|---|---|
| Modell | Stabilität, Schwierigkeit, Vergessenskurve | Leichtigkeitsfaktor und Abstand |
| Antworten | 4 (Nochmal, Schwer, Gut, Leicht) | 6 (0 bis 5) |
| Zustand je Karte | S, D, letzter Tag, fällig am | EF, Abstand, Zähler n |
| Verspätete oder verfrühte Abfrage | geht in die Rechnung ein | wird nicht berücksichtigt |
| Zielgröße einstellbar | ja, gewünschte Behaltensquote | nein |
| Parameter | 21, mit Standardwerten | keine |
| Umfang der Rechnung | sieben Formeln | zwei Formeln |
| Quelle | [W] [P] | [S] |

## 4. FSRS-6

### 4.1 Zustand und Antworten

- Antworten `G`: 1 = Nochmal, 2 = Schwer, 3 = Gut, 4 = Leicht. [W]
- Je Karte: Stabilität `S` (mindestens 0,001), Schwierigkeit `D` (1 bis 10), Zeitpunkt der letzten Abfrage, Fälligkeit. Eine neue Karte hat weder S noch D. [P]

### 4.2 Standardparameter

```
w0  = 0.212    w1  = 1.2931   w2  = 2.3065   w3  = 8.2956
w4  = 6.4133   w5  = 0.8334   w6  = 3.0194   w7  = 0.001
w8  = 1.8722   w9  = 0.1666   w10 = 0.796    w11 = 1.4835
w12 = 0.0614   w13 = 0.2629   w14 = 1.6483   w15 = 0.6014
w16 = 1.8729   w17 = 0.5425   w18 = 0.0912   w19 = 0.0658
w20 = 0.1542
```

Wortgleich in [W] und [P].

### 4.3 Formeln

Alle aus [P]; die Vergessenskurve und die Stabilität am selben Tag stehen ebenso in [W]. `t` sind ganze Tage seit der letzten Abfrage.

```
DECAY  = -w20
FACTOR = 0.9 ^ (1 / DECAY) - 1                    // 0.98034649...

// Wahrscheinlichkeit, die Karte nach t Tagen noch zu wissen
R(t, S) = (1 + FACTOR * t / S) ^ DECAY

// Abstand in Tagen für eine gewünschte Behaltensquote r (Standard 0.9)
I(S, r) = min( max( round( (S / FACTOR) * (r ^ (1 / DECAY) - 1) ), 1 ), 36500 )

// erste Abfrage einer neuen Karte
S0(G) = max( w[G-1], 0.001 )
D0(G) = clamp( w4 - e ^ (w5 * (G - 1)) + 1, 1, 10 )

// Schwierigkeit nach jeder weiteren Abfrage
D'(D, G) = clamp( w7 * D0roh(4) + (1 - w7) * ( D + (10 - D) * (-w6 * (G - 3)) / 9 ), 1, 10 )
           mit D0roh(4) = w4 - e ^ (w5 * 3) + 1   // ohne clamp

// Stabilität nach einer gewussten Abfrage (G = 2, 3, 4), t >= 1
S'r(D, S, R, G) = S * ( 1 + e ^ w8 * (11 - D) * S ^ (-w9) * ( e ^ ((1 - R) * w10) - 1 )
                          * (w15 falls G = 2, sonst 1) * (w16 falls G = 4, sonst 1) )

// Stabilität nach Vergessen (G = 1), t >= 1
S'f(D, S, R) = min( w11 * D ^ (-w12) * ( (S + 1) ^ w13 - 1 ) * e ^ ((1 - R) * w14),
                    S / e ^ (w17 * w18) )

// Stabilität bei einer weiteren Abfrage am selben Tag (t < 1)
zuwachs   = e ^ (w17 * (G - 3 + w18)) * S ^ (-w19)
            für G >= 2:  zuwachs = max(zuwachs, 1)
S's(S, G) = S * zuwachs
```

Nach jeder Formel gilt `S = max(S, 0.001)`.

### 4.4 Ablauf einer Abfrage

```
neue Karte:        S = S0(G);  D = D0(G)
selber Tag (t<1):  S = S's(S, G);                 D = D'(D, G)
sonst:             R = R(t, S)
                   S = (G = 1) ? S'f(D, S, R) : S'r(D, S, R, G)
                   D = D'(D, G)
danach:            Abstand = I(S, 0.9);  fällig = heute + Abstand
```

So rechnet die Referenz in allen Kartenzuständen; die Zustände unterscheiden sich dort nur darin, welcher Abstand folgt. [P]

### 4.5 Was die Referenz darüber hinaus tut

- **Lernschritte:** Standard sind 1 und 10 Minuten für neue Karten und 10 Minuten nach „Nochmal“. Ohne Schritte geht eine Karte sofort in die Planung nach Tagen; nach „Nochmal“ wird dann ebenfalls `I(S)` gerechnet, also mindestens ein Tag. [P]
- **Streuung („fuzz“):** Abstände ab 2,5 Tagen werden zufällig leicht verschoben, abschaltbar. [P]
- **Tage:** Die Referenz zählt abgelaufene Tage als volle 24 Stunden zwischen zwei Zeitpunkten in UTC. [P]
- **Grenzen:** Behaltensquote 0,9 und längster Abstand 36 500 Tage als Standard. [P] Das Anki-Handbuch nennt 90 % ebenfalls als Standard und warnt, dass die Arbeit über 90 % schnell und über 97 % überwältigend zunimmt. [A-FSRS]

## 5. Regel für EmilysHub

[Vorschlag] Geplant wird in ganzen Tagen, ohne Lernschritte und ohne Streuung:

1. `t` ist der Unterschied zwischen dem heutigen Tag und dem Tag der letzten Abfrage, nach dem Kalender des Handys (`Day`), nicht nach 24 Stunden. Eine Abfrage um 23:00 und eine am nächsten Morgen liegen damit einen Tag auseinander. Das weicht bewusst von der Referenz ab.
2. Nach jeder Antwort wird gerechnet wie in §4.4, die Fälligkeit ist heute plus der Abstand.
3. Nach „Nochmal“ kommt die Karte in derselben Runde noch einmal. Diese zweite Antwort zählt als Abfrage am selben Tag (`t = 0`). Wird die Runde vorher verlassen, ist die Karte am nächsten Tag fällig.
4. Eine Karte, die vor ihrer Fälligkeit abgefragt wird (etwa beim Üben eines Themas), wird normal gerechnet: `R` ist dann hoch, und die Stabilität wächst entsprechend wenig. Eine Sonderregel braucht es nicht.
5. Die Behaltensquote bleibt fest bei 90 %, der längste Abstand bei 36 500 Tagen.
6. Ohne Streuung sind die Abstände vorhersagbar und in Tests prüfbar. Bei einigen Tausend Karten ist die Häufung an einzelnen Tagen kein Problem.

## 6. Testvektoren

Alle Werte [E], mit Standardparametern, 90 %, ohne Lernschritte und ohne Streuung. Die Nachbildung wurde gegen zwei Ergebnisse aus [T] geprüft:

- `test_memo_state`: Nochmal, dann fünfmal Gut nach 0, 0, 1, 3, 8 und 21 Tagen ergibt S = 53,62691 und D = 6,3574867 (Toleranz dort 0,0001). Die Nachbildung liefert 53,6269 und 6,3574871.
- `test_review_card`: mit den Lernschritten der Referenz ergibt die Folge Gut ×6, Nochmal ×2, Gut ×5, jeweils am Fälligkeitstag, die Abstände 0, 2, 11, 46, 163, 498, 0, 0, 2, 4, 7, 12, 21. Die Nachbildung liefert dieselben.

Erste Abfrage einer neuen Karte:

| Antwort | S | D | Abstand |
|---|---|---|---|
| Nochmal | 0,2120 | 6,4133 | 1 |
| Schwer | 1,2931 | 5,1122 | 1 |
| Gut | 2,3065 | 2,1181 | 2 |
| Leicht | 8,2956 | 1,0000 | 8 |

A: sechsmal Gut, jeweils am Fälligkeitstag:

| Tage seit der letzten Abfrage | Antwort | S | D | Abstand |
|---|---|---|---|---|
| neu | Gut | 2,3065 | 2,1181 | 2 |
| 2 | Gut | 10,9643 | 2,1112 | 11 |
| 11 | Gut | 46,2802 | 2,1043 | 46 |
| 46 | Gut | 162,8622 | 2,0975 | 163 |
| 163 | Gut | 497,4472 | 2,0906 | 497 |
| 497 | Gut | 1345,5288 | 2,0837 | 1346 |

B: vergessen und in derselben Runde wieder gewusst:

| Tage seit der letzten Abfrage | Antwort | S | D | Abstand |
|---|---|---|---|---|
| neu | Gut | 2,3065 | 2,1181 | 2 |
| 2 | Gut | 10,9643 | 2,1112 | 11 |
| 11 | Nochmal | 1,5383 | 7,3922 | 2 |
| 0 | Gut | 1,5712 | 7,3801 | 2 |
| 2 | Gut | 4,9318 | 7,3679 | 5 |
| 5 | Gut | 12,4025 | 7,3558 | 12 |

C: Schwer, Gut, Leicht, Leicht, jeweils am Fälligkeitstag:

| Tage seit der letzten Abfrage | Antwort | S | D | Abstand |
|---|---|---|---|---|
| neu | Schwer | 1,2931 | 5,1122 | 1 |
| 1 | Gut | 4,5460 | 5,1023 | 5 |
| 5 | Leicht | 27,0306 | 3,4509 | 27 |
| 27 | Leicht | 145,8163 | 1,2478 | 146 |

D: verspätet (10 Tage statt 2), dann verfrüht (1 Tag statt 25):

| Tage seit der letzten Abfrage | Antwort | S | D | Abstand |
|---|---|---|---|---|
| neu | Gut | 2,3065 | 2,1181 | 2 |
| 10 | Gut | 25,1087 | 2,1112 | 25 |
| 1 | Gut | 29,0943 | 2,1043 | 29 |

E: neue Karte, zweimal Nochmal und dann Gut am selben Tag, Gut am nächsten:

| Tage seit der letzten Abfrage | Antwort | S | D | Abstand |
|---|---|---|---|---|
| neu | Nochmal | 0,2120 | 6,4133 | 1 |
| 0 | Nochmal | 0,0834 | 8,8063 | 1 |
| 0 | Gut | 0,1031 | 8,7927 | 1 |
| 1 | Gut | 0,6953 | 8,7792 | 1 |

Als Tests in Kotlin: mit einer Toleranz von 0,0001 für S und D und exakten Abständen.

## 7. SM-2 als Alternative

Vollständig nach [S]:

```
Start:  EF = 2.5
I(1) = 1;  I(2) = 6;  für n > 2:  I(n) = I(n-1) * EF      // Bruchteile aufrunden
nach jeder Abfrage mit Qualität q (0 bis 5):
  EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02));   EF' mindestens 1.3
  q < 3:  wieder bei I(1) beginnen, EF bleibt
am selben Tag alle Karten mit q < 4 wiederholen, bis sie mindestens 4 erreichen
```

Qualität: 5 perfekt, 4 richtig nach Zögern, 3 richtig mit großer Mühe, 2 falsch, aber die richtige Antwort schien leicht, 1 falsch, an die richtige erinnert, 0 völliger Aussetzer.

## 8. „Sicher“

Das Anki-Handbuch: „A mature card is one that has an interval of 21 days or greater“, eine jüngere heißt „young“. [A-REIF]

[Vorschlag] In EmilysHub ist eine Karte **sicher**, wenn ihr zuletzt geplanter Abstand mindestens 21 Tage beträgt. Bei 90 % entspricht der Abstand der gerundeten Stabilität.

## 9. Lizenz

- `py-fsrs` steht unter der MIT-Lizenz, „Copyright (c) 2022 Open Spaced Repetition“. [L]
- [Vorschlag] Die App übernimmt keinen Quelltext, sondern rechnet die veröffentlichten Formeln selbst nach und übernimmt die 21 Standardparameter als Zahlen. Ein Kommentar am Modul nennt FSRS-6, das Projekt und die Quelle der Parameter.
- Unter welcher Lizenz die Formelseite [W] selbst steht, ist dort nicht angegeben (§11).

## 10. Was je Abfrage zu speichern ist

- An der Karte: S, D, Tag der letzten Abfrage, Fälligkeitstag, zuletzt geplanter Abstand.
- Als Protokoll je Abfrage: Karte, Zeitpunkt, Antwort. Mehr verlangt der Optimierer der Referenz nicht; die Dauer der Abfrage ist dort freiwillig. [P] [R] Mit dem Protokoll bleibt ein späteres Anpassen der Parameter möglich, und die Karten lassen sich neu durchrechnen.

## 11. Offen

- Ob es außerhalb der beiden geprüften Quellen schon eine neuere Version als FSRS-6 gibt, wurde nicht geprüft.
- Die Lizenz der Formelseite [W] ist nicht geklärt. Formeln als solche nachzurechnen ist üblich; eine rechtliche Prüfung ist das nicht.
- Die Parameter sind über viele Lernende und Stoffe gemittelt. Ob sie für arabische Vokabeln gut passen, zeigt erst das eigene Protokoll.
- Die Abweichung „Kalendertage statt 24 Stunden“ (§5) ist eine eigene Festlegung und nicht gegen die Referenz getestet, außer wo beide dasselbe ergeben.

## 12. Quellen

Alle abgerufen am 2026-11-12.

- **[W]** https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm – Symbole, Antworten, Standardparameter von FSRS-6, Vergessenskurve, Stabilität am selben Tag. Die frühere Adresse im Wiki von `fsrs4anki` verweist dorthin.
- **[P]** https://github.com/open-spaced-repetition/py-fsrs/blob/main/fsrs/scheduler.py (Version 6.3.2 laut `pyproject.toml`) – Parameter, alle Formeln, Grenzen, Lernschritte, Streuung, Zählung der Tage.
- **[T]** https://github.com/open-spaced-repetition/py-fsrs/blob/main/tests/test_basic.py – `test_review_card`, `test_memo_state`.
- **[R]** https://github.com/open-spaced-repetition/py-fsrs/blob/main/README.md – Hinweis zu den Standardparametern, Optimierer als freiwilliger Zusatz.
- **[L]** https://github.com/open-spaced-repetition/py-fsrs/blob/main/LICENSE – MIT.
- **[A-REIF]** https://docs.ankiweb.net/getting-started.html (Abschnitt „Card States“) und https://docs.ankiweb.net/stats.html – „mature“ ab 21 Tagen.
- **[A-FSRS]** https://docs.ankiweb.net/deck-options.html (Abschnitt „FSRS“) – 90 % als Standard, „Hard“ nicht bei Vergessenem.
- **[S]** https://super-memory.com/english/ol/sm2.htm – Algorithmus SM-2 im Wortlaut.
