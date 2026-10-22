# Signierte Release-APK

Android installiert ein Update nur, wenn es mit demselben Schlüssel signiert ist wie die installierte App. Diesen Schlüssel erzeugst du einmal selbst. Er liegt danach nur bei dir und als Secret auf GitHub, niemals im Repo.

Ohne die Secrets baut GitHub Actions weiter wie bisher, signiert die Release-APK aber nicht. Im Lauf steht dann ein entsprechender Hinweis.

## 1. Schlüssel erzeugen

Du brauchst `keytool`. Es gehört zu jedem Java (JDK) und ist auch in Android Studio enthalten:

| System | `keytool` aus Android Studio |
|---|---|
| Windows (PowerShell) | `& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"` |
| macOS | `"/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool"` |
| Linux, oder ein JDK ist installiert | `keytool` |

Öffne ein Terminal in einem Ordner **außerhalb** des Repos, zum Beispiel in „Dokumente“. Dort ausführen (unter Windows/macOS `keytool` durch den Aufruf aus der Tabelle ersetzen):

```
keytool -genkeypair -v -keystore emilyshub-release.jks -alias emilyshub -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=EmilysHub"
```

`keytool` fragt zweimal nach einem Passwort. Nimm ein langes, am besten aus deinem Passwortmanager. Es ist das einzige Passwort: Du trägst es gleich sowohl als `KEYSTORE_PASSWORD` als auch als `KEY_PASSWORD` ein. Der Schlüssel gilt gut 27 Jahre.

Fingerabdruck anzeigen und notieren (Zeile `SHA256:`):

```
keytool -list -v -keystore emilyshub-release.jks -alias emilyshub
```

## 2. Schlüssel sichern

**Bevor** du weitermachst: Geht der Schlüssel verloren, kann keine neue Version mehr über die installierte App installiert werden. Dann bleibt nur Deinstallieren und Neuinstallieren, und die Daten auf dem Handy sind weg.

- Sichere die Datei `emilyshub-release.jks` und das Passwort an mindestens zwei Orten, die nicht im Internet liegen, zum Beispiel auf einem USB-Stick und im Passwortmanager.
- Lege die Datei nie ins Repo und verschicke sie nicht per Mail oder Chat. `.gitignore` schließt `*.jks`, `*.keystore` und `*.p12` sicherheitshalber aus.

## 3. Secrets in GitHub eintragen

Kopiere die Schlüsseldatei als Base64-Text in die Zwischenablage. Dabei entsteht keine zusätzliche Datei:

| System | Befehl im Ordner der Schlüsseldatei |
|---|---|
| Windows (PowerShell) | `[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path emilyshub-release.jks))) \| Set-Clipboard` |
| macOS | `base64 -i emilyshub-release.jks \| pbcopy` |
| Linux | `base64 -w 0 emilyshub-release.jks`, dann die Ausgabe kopieren |

Öffne auf GitHub das Repo **EmilysHub** und dort **Settings → Secrets and variables → Actions**. Lege mit **New repository secret** diese vier Secrets an:

| Name | Wert |
|---|---|
| `KEYSTORE_BASE64` | der Text aus der Zwischenablage |
| `KEYSTORE_PASSWORD` | dein Passwort |
| `KEY_ALIAS` | `emilyshub` |
| `KEY_PASSWORD` | dasselbe Passwort |

Danach die Zwischenablage überschreiben, also einfach irgendetwas anderes kopieren.

Fehlt nur ein Teil der Secrets, schlägt der Lauf mit der Liste der fehlenden Namen fehl.

## 4. Signierte APK bauen und installieren

1. Einen neuen Lauf starten: entweder etwas pushen oder unter **Actions → Build → Run workflow**.
2. Im Lauf den Schritt **Release-APK signieren** öffnen. Dort steht eine Zeile mit `certificate SHA-256 digest: …`. Der Wert muss dein Fingerabdruck aus Schritt 1 sein, nur kleingeschrieben und ohne Doppelpunkte.
3. Unten bei **Artifacts** `emilyshub-release` herunterladen. Die ZIP-Datei enthält `app-release.apk`.
4. Die APK aufs A52 bringen, zum Beispiel per USB-Kabel, und in „Eigene Dateien“ antippen. Samsung fragt beim ersten Mal, ob diese App unbekannte Apps installieren darf: erlauben, dann **Installieren**.

Ist noch die Debug-APK (`emilyshub-debug`) installiert, deinstalliere sie vorher. Sie ist mit einem anderen Schlüssel signiert, deshalb würde Android die Release-APK sonst mit „App nicht installiert“ ablehnen.

## 5. Update einmal ausprobieren

Installiere die APK aus einem späteren Lauf über die vorhandene App. Android bietet dann ein **Update** an, und die App behält ihre Daten. Erscheint stattdessen „App nicht installiert“, vergleiche die Fingerabdrücke der beiden Läufe.
