# 58 – Dokumentation: eigener verschlüsselter Bereich mit Passwort & Fingerabdruck

**Etappe:** 10 – Sonstiges, Heute & Dokumentation  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §9, §14 (Sperre & Verschlüsselung, Insel)  
**GitHub-Issue:** #58  
**Blockiert durch:** [55 – Sonstiges](55-sonstiges-mit-zielen.md)

## Was gebaut wird

Der Bereich **Dokumentation** in Sonstiges: Er hat eine eigene, verschlüsselte Datenbank, die sich nur mit einem eigenen Doku-Passwort oder dem Fingerabdruck öffnen lässt, und sperrt sich sofort, sobald man ihn verlässt. Dieser Slice baut den Tresor mit einer leeren Startseite; Patienten, Termine und Tests folgen in #59–#62.

## Akzeptanzkriterien

### Einstieg

- [ ] In Sonstiges der Eintrag **🔒 Dokumentation** unter „Ziele“
- [ ] Beim allerersten Öffnen: „Doku-Passwort festlegen“ mit zwei Feldern (mindestens 8 Zeichen, beide gleich), dem Hinweis „Das Passwort lässt sich nicht wiederherstellen. Ohne Passwort und Fingerabdruck sind die Daten verloren.“, den du bestätigen musst, und einem Schalter „Mit Fingerabdruck entsperren“, nur wenn am Handy ein Fingerabdruck eingerichtet ist
- [ ] Danach bei jedem Öffnen: mit Fingerabdruck sofort der Fingerabdruck-Dialog, dessen zweiter Knopf „Passwort“ zum Passwortfeld führt; ohne Fingerabdruck gleich das Passwortfeld
- [ ] Die Geräte-PIN, das Muster oder das Gerätepasswort entsperren die Doku nie
- [ ] Ein falsches Passwort zeigt „Falsches Passwort“ und lässt nichts sehen
- [ ] Vor dem Entsperren ist kein Inhalt der Doku sichtbar, auch nicht kurz
- [ ] Startseite, solange #59 fehlt: „Noch keine Patienten“ und das Menü (unten)

### Schlüssel

- [ ] Eigene Room-Datenbank `doku.db` mit SQLCipher, getrennt von `HubDatabase`, mit eigenem Schema-Export und eigenen Migrationen (kein `fallbackToDestructiveMigration`)
- [ ] Ihr Schlüssel: zufällige 256 Bit, nie im Klartext gespeichert, sondern zweimal mit AES-GCM verpackt:
  - mit einem Schlüssel aus dem Doku-Passwort (PBKDF2-HMAC-SHA256 mit Salt, Iterationen wie `BackupKey`)
  - nur mit Fingerabdruck: mit einem Keystore-Schlüssel, der für jede Verwendung einen starken Fingerabdruck verlangt (`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)`, `setInvalidatedByBiometricEnrollment(true)`), freigegeben über `BiometricPrompt` mit `CryptoObject`
- [ ] Der Schlüssel der übrigen App (`DatabaseKeyFile`) kann die Doku nicht öffnen
- [ ] Wird am Handy ein Fingerabdruck neu eingerichtet, ist der Fingerabdruck-Schlüssel ungültig: Die App verlangt das Passwort, schaltet den Fingerabdruck aus und bietet danach an, ihn wieder einzuschalten
- [ ] Gesperrt ist die Datenbank geschlossen und der Schlüssel aus dem Speicher entfernt (Byte-Array überschrieben)

### Sperren

- [ ] Die Doku sperrt sich **sofort**, wenn du sie verlässt (anderer Tab, zurück zu Sonstiges), wenn die App in den Hintergrund geht oder der Bildschirm ausgeht, unabhängig von der App-Sperre und ihrer Minute
- [ ] Nach **5 Minuten** ohne Berührung oder Eingabe in der Doku sperrt sie sich ebenfalls und zeigt ihren Sperrbildschirm
- [ ] Vor dem Sperren speichern offene Editoren der Doku, was getippt wurde
- [ ] Nach dem Entsperren beginnt die Doku auf ihrer Startseite
- [ ] Nach einem Neustart des Prozesses ist sie immer gesperrt; kein Inhalt der Doku landet im gespeicherten Zustand der Bildschirme (Bundle), höchstens Kennungen

### Insel

- [ ] Inhalte der Doku erscheinen nicht in der Suche über alle Bereiche, nicht in Heute, nicht in Benachrichtigungen, Statistik oder Heatmap; sie haben keine Tags und keine Verknüpfungen
- [ ] Alle Textfelder der Doku bitten die Tastatur, nichts zu lernen (`IME_FLAG_NO_PERSONALIZED_LEARNING`, z. B. über `InterceptPlatformTextInput`), und schalten die Autokorrektur-Vorschläge aus
- [ ] Aus der Doku Kopiertes ist als vertraulich markiert (`ClipDescription.EXTRA_IS_SENSITIVE`); beim Sperren wird die Zwischenablage geleert, wenn sie noch Text aus der Doku hält

### Menü der Doku

- [ ] „Doku-Passwort ändern“: altes Passwort, neues zweimal; der Datenbankschlüssel bleibt, nur seine Verpackung ändert sich. Der Dialog sagt: „Ältere Backups brauchen weiter das alte Passwort.“
- [ ] „Fingerabdruck“ an/aus; Einschalten verlangt einmal den Fingerabdruck
- [ ] Die übrigen Menüpunkte (Tests, Termin-Vorlage, Archiv) kommen mit #59–#61

### Tests

- [ ] Unit-Tests: Schlüssel mit richtigem und falschem Passwort; Passwort ändern hält die Daten; Sperrlogik (Verlassen, Hintergrund, 5 Minuten, Eingabe setzt die Zeit zurück); ungültiger Fingerabdruck-Schlüssel führt zum Passwort
- [ ] Instrumentierte Tests: `doku.db` lässt sich ohne Schlüssel, mit falschem und mit dem Schlüssel der übrigen App nicht öffnen
- [ ] UI-Tests: Passwort festlegen, entsperren, Sperre beim Tabwechsel, kein Inhalt vor dem Entsperren

## Entscheidungen

Abgestimmt im Interview vom 07.11.2026.

- Persönliche Arbeitsnotizen, keine Patientenakte (§630f BGB): keine Aufbewahrungspflicht, keine Pflicht zum Änderungsverlauf.
- Eigenes Passwort mit Fingerabdruck als Abkürzung; die Geräte-PIN gilt nicht, sonst wäre jeder mit der PIN auch in den Patientendaten.
- Keine Wiederherstellung des Passworts: Das macht die Verschlüsselung sicher.
- Sofort sperren statt einer kurzen Frist; dazu 5 Minuten ohne Eingabe.
- Die Doku ist eine Insel: keine Suche, kein Heute, keine Erinnerungen; Termine plant man woanders.
- Einstellungen der Doku liegen in ihrem Menü, nicht in den allgemeinen Einstellungen.
