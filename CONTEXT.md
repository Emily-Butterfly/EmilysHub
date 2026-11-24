# EmilysHub

Eine private App für den eigenen Alltag: Routinen, To-dos, Notizen, Ziele und die Bereiche unter Sonstiges.

## Language

### Mein Warum

**Mein Warum**:
Ein Bereich unter Sonstiges: ein Board mit den Gründen, weiterzuleben, zum Anschauen in schweren Momenten, auch bei Suizidgedanken.
_Avoid_: Mood Board (beschreibt nur die Form), Hope Box

**Grund**:
Ein Eintrag in Mein Warum: ein Text, ein Foto oder ein Foto mit Text.
_Avoid_: Kachel, Karte, Eintrag

### Vokabeltrainer

**Tagespensum**:
Was eine Sprache heute zu üben hat: alle fälligen Richtungen ihrer Karten und dazu höchstens so viele neue wie eingestellt, bis zur Grenze des Tages. Geschafft, wenn nichts davon mehr offen ist; dann ist eine Routine „Erledigt durch Vokabeln“ dieser Sprache erledigt, je Routine einmal am Tag.
_Avoid_: Workload (nur im Code), Pensum allein, Tagesziel

**Runde**:
Eine Abfrage vom Öffnen bis zum Verlassen: die Karten des Tagespensums oder eines Themas, die Antworten darauf, die letzte Antwort, die sich noch zurücknehmen lässt.
_Avoid_: Session (heißt in Sprachen eine Unterrichtsstunde), Durchgang

### Daten

**Ablage**:
Die verschlüsselten Dateien der Daten neben der Datenbank: die Anhänge der Sessions und die Fotos der Gründe. Die Fotos im Tracking liegen nicht darin, sondern bei ihrer Insel (ADR 0004).
_Avoid_: Anhänge (heißen nur die der Sessions), Speicher, Vault (nur im Code)

## Relationships

- **Mein Warum** hält beliebig viele **Gründe**
- Eine **Sprache** hat an jedem Tag ein **Tagespensum**; eine **Runde** fragt es ab, oder die Karten eines Themas, die zu keinem Tagespensum zählen
- Die **Ablage** hält die Anhänge der **Sessions** und die Fotos der **Gründe**, je mit ihrer Höchstzahl

## Flagged ambiguities

- „Karte“ ist im Vokabeltrainer schon vergeben (Karteikarte); ein **Grund** heißt deshalb nie Karte.
- „Session“ ist in Sprachen eine Unterrichtsstunde; das Üben der Karten heißt deshalb **Runde**, nie Session.
- Das Foto eines **Grundes** ist kein Anhang; beide liegen in der **Ablage**.
