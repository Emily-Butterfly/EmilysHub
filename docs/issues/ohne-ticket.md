# Ohne Ticket

Themen aus dem Gutachten vom 12.11.2026, die noch kein Ticket haben. Jedes steht hier mit Befund, Fundstelle und Vorschlag, damit es sich in einem neuen Chat aufgreifen lässt, ohne das Gutachten noch einmal zu lesen.

**So geht es weiter:** Ein Thema wird ein Ticket wie die anderen (`docs/agents/issue-tracker.md`: GitHub-Issue und Datei `docs/issues/NN-*.md` mit derselben Nummer, Zeile in `README.md`), oder es wird, wenn es klein ist, mit einem anderen Ticket zusammen erledigt. Danach verschwindet es aus dieser Datei. Was unter „Zu entscheiden“ steht, braucht vorher eine Antwort von Emily.

Das Gutachten: https://claude.ai/artifact/49Q6Zngame3CUwHUE5S2nF. Die Tickets der Etappe 13 hängen an #86.

## 1. Backup-Passwort ändern (Befund T5)

- **Befund:** Das Backup-Passwort wird einmal festgelegt und lässt sich danach nicht ändern, außer indem ein Backup mit einem anderen Passwort eingespielt wird. Wird es bekannt, gibt es keinen Ausweg.
- **Fundstelle:** `backup/Backups.kt` (`setPassword`, `hasPassword`), `backup/BackupKey.kt`, `ui/backups/BackupScreen.kt`, der Text `backup_password_explanation`; SPEC §10 („wird einmal festgelegt“).
- **Vorschlag:** „Passwort ändern“ im Backup-Bildschirm. Das alte Passwort wird gegen den gespeicherten Schlüssel geprüft (gleiches Salz, gleiche Runden), dann entsteht aus dem neuen ein neuer Schlüssel für alle künftigen Backups, auch die nächtlichen. Alte Backup-Dateien bleiben mit dem alten Passwort lesbar; der Bildschirm sagt das.
- **Zu entscheiden:** Muss das alte Passwort eingegeben werden, oder genügt die App-Sperre? Was gilt, wenn das alte vergessen ist?
- **Umfang:** klein bis mittel; SPEC §10 ist anzupassen.

## 2. Abhängigkeiten automatisch prüfen (Befund T6)

- **Befund:** Nichts meldet neue Versionen der Bibliotheken. Es gibt weder Dependabot noch Renovate, und Lints Hinweise darauf sind abgeschaltet. Sicherheitsfixes von SQLCipher oder Room kommen nur von Hand.
- **Fundstelle:** `.github/` (nur `workflows/build.yml`), `app/build.gradle.kts` (`lint.disable`: `GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion`), `gradle/libs.versions.toml`.
- **Vorschlag:** `.github/dependabot.yml` für Gradle und für die GitHub Actions, monatlich und gebündelt. Die Actions bleiben auf Commit-SHAs festgelegt; Dependabot pflegt SHA und Versionskommentar. Seine Läufe haben keinen Zugriff auf die Signatur-Secrets; der Build überspringt das Signieren dann schon von selbst.
- **Zu entscheiden:** Wer prüft und übernimmt die Vorschläge? Bisher gibt es in diesem Repo keine Pull Requests.
- **Umfang:** klein.

## 3. Ort direkt im Feld anlegen (Befund U8)

- **Befund:** Gibt es noch keinen Ort, zeigt das Feld „Orte“ im Routine- und im To-do-Editor nur den Hinweis „Orte legst du in den Einstellungen unter ‚Orte verwalten‘ an.“ Das ist eine Sackgasse mitten im Formular. Tags und Lehrer lassen sich dagegen an Ort und Stelle anlegen.
- **Fundstelle:** `ui/fields/PlacesField.kt`, die Texte `todo_places_none` und ihr Gegenstück für Routinen, `ui/places/PlaceEditorScreen.kt`, `Destination.PlaceEditor`.
- **Vorschlag:** „+ Neuer Ort“ im Feld öffnet den Orts-Editor über dem offenen Editor; dessen Entwurf bleibt, wie beim Öffnen einer Notiz aus einem To-do. Nach dem Speichern ist der neue Ort im Feld gewählt. Die Berechtigungen fragt der Orts-Editor wie bisher.
- **Umfang:** klein. Passt gut zu #92, das dieselben Editoren umbaut.

## 4. Symbole im Haushaltsbuch (Befund U9)

- **Befund:** In der oberen Leiste des Haushaltsbuchs stehen vier Symbole ohne Text: Schloss für das Archiv, Kalender für die Fixbuchungen, Kreispfeil für die Abos, Liste für die Kategorien. Ihre Bedeutung muss man raten, und mit jedem neuen Bereich kommt eins dazu.
- **Fundstelle:** `ui/ledger/LedgerScreen.kt:128–137`.
- **Vorschlag:** Ein Menü mit Text (Fixbuchungen, Abos, Kategorien, Archiv), in der Leiste bleibt höchstens das Häufigste.
- **Zu entscheiden:** Menü mit Text oder Symbole mit Beschriftung darunter? Was soll ohne Menü erreichbar bleiben?
- **Umfang:** klein.

## 5. Tab-Namen bei großer Schrift (Befund U10)

- **Befund:** Bei Schriftgröße 150 % brechen die Namen in der unteren Leiste mitten im Wort um („Routine-n“, „Sonstige-s“). Im Gutachten mit einer gerenderten Aufnahme belegt.
- **Fundstelle:** `ui/EmilysHubApp.kt:489–497` (die Beschriftung der `NavigationBarItem`).
- **Vorschlag:** Die Beschriftung einzeilig halten und die Schrift der Leiste nach oben begrenzen, damit sie bei großer Systemschrift nicht mitwächst. Ein Test bei Schriftgröße 150 % und 200 %.
- **Umfang:** sehr klein.

## 6. Tages-Check-in mit Symptomen und HRV

- **Stand:** Vorbereitet in `docs/research/pacing.md` und `docs/research/visible-apk-hrv-symptomtracking.md`, mit einem Vorschlag für das Datenmodell. Im Gutachten bewusst nicht als Vorschlag gezählt, weil es schon geplant war.
- **Zu entscheiden:** Umfang der ersten Stufe und der Ort in der App. Dafür braucht es ein Interview wie bei den früheren Etappen.
