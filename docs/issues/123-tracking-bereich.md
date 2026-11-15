# 123 – Tracking: eigener Bereich mit Passwort, Backup & „Akute Beschwerden?“

**Etappe:** 15 – Tracking  
**Typ:** Feature  
**Art:** 🤖 AFK – kann ohne Rückfragen umgesetzt werden  
**Spezifikation:** [SPEC.md](../../SPEC.md) §17 (Sperre & Insel, Startseite, Warnzeichen), §9, §10  
**GitHub-Issue:** #123  
**Blockiert durch:** –

## Was gebaut wird

Unter Sonstiges ein neuer Eintrag **Tracking**: eine Insel wie die Dokumentation mit eigenem Passwort, Fingerabdruck und eigener verschlüsselter Datenbank, im Backup in eigener Verschlüsselung. Die Startseite zeigt zunächst nur „Akute Beschwerden?“ und das Menü; die übrigen Teile füllen die folgenden Slices.

## Akzeptanzkriterien

- [x] Sonstiges listet **Tracking** nach Sprachen; öffnet im Tab, sperrt beim Verlassen wie die Dokumentation
- [x] **Tracking-Passwort** (mindestens 8 Zeichen) beim ersten Öffnen, zweimal eingegeben, mit Hinweis, dass es sich nicht wiederherstellen lässt; **Fingerabdruck** optional, Geräte-PIN gilt nicht; neu eingerichteter Fingerabdruck verlangt einmal das Passwort
- [x] Eigene SQLCipher-Datenbank `tracking.db`, deren Schlüssel nur Passwort oder Fingerabdruck freigeben, unabhängig vom App- und vom Doku-Schlüssel; Migrationen ohne `fallbackToDestructiveMigration`
- [x] Sperre sofort beim Verlassen, im Hintergrund, bei ausgehendem Bildschirm und nach 5 Minuten ohne Eingabe; Getipptes wird vorher gespeichert
- [x] Schnittstelle für die **Ausnahme Messassistent** (Bildschirm an, 5-Minuten-Sperre ruht, Hintergrund und Bildschirm aus sperren weiter), genutzt ab #127
- [x] Insel-Regeln: nicht in Suche, Heute, Statistik, Benachrichtigungen; keine Tags, Pins, Verknüpfungen; Tastatur ohne Lernen; Zwischenablage vertraulich und beim Sperren geleert; gelöschte Daten überschrieben
- [x] **„Akute Beschwerden?“** ganz oben: Warnzeichen „sofort 112“ und „zeitnah ärztlich“ nach §17 mit Quellenkürzeln; „112 anrufen“ öffnet die Telefon-App mit der Nummer (`ACTION_DIAL`, ohne Berechtigung); Hinweis, Medikamente nur mit den Behandelnden zu ändern
- [x] Menü: Passwort ändern · Fingerabdruck · Alles löschen (Rückfrage und Passwort); weitere Punkte kommen mit den Slices
- [x] **Backup:** Tracking liegt in eigener Verschlüsselung im manuellen und nächtlichen Backup, ohne entsperrt zu werden; einspielen verlangt das Tracking-Passwort, das beim Backup galt

## Tests

- [x] Unit: Datenbank ohne oder mit falschem Schlüssel nicht lesbar; Backup ohne Tracking-Passwort nicht einspielbar; Alles löschen leert die Datenbank
- [x] UI: Passwort festlegen, sperren in allen Fällen, Fingerabdruck an/aus, „112 anrufen“ startet `ACTION_DIAL`

## Entscheidungen

Abgestimmt im Interview vom 15.11.2026: eigene Sperre wie die Dokumentation, Name „Tracking“. Erinnerungen über normale Routinen, nicht aus Tracking.

Beim Bauen festgelegt (15.11.2026), nicht einzeln bestätigt:

- Sperre, Schlüssel und Backup-Teil der Dokumentation sind jetzt eine gemeinsame „Insel“ im Code (`island/`), die Dokumentation und Tracking je mit eigener Datenbank, eigenem Passwort und eigenem Schlüssel für den Fingerabdruck benutzen.
- **Alles löschen** leert die Datenbank und lässt Passwort und Fingerabdruck stehen; Tracking bleibt offen und leer. Schon gespeicherte Backups enthalten die Daten weiter, das sagt die Rückfrage.
- **„112 anrufen“** steht direkt unter der Überschrift „Akute Beschwerden?“, vor den Warnzeichen, damit es ohne Weiterlesen zur Hand ist.
- Beim Einspielen eines Backups mit Dokumentation und Tracking fragt der Dialog beide Passwörter ab; „Ohne Dokumentation und Tracking einspielen“ lässt beide, wie sie sind.
- Die Ausnahme Messassistent ist `WhileMeasuring` (Bildschirm an, 5-Minuten-Sperre ruht); #127 und #128 benutzen sie.
- Die UI-Tests prüfen das Sperren beim Tab-Wechsel, beim Zurück, im Hintergrund und den Neustart der 5 Minuten bei Berührung; das Sperren nach 5 Minuten selbst prüft der Unit-Test der Insel. Ob die Datenbank ohne oder mit falschem Schlüssel unlesbar ist, prüft `TrackingDatabaseTest` auf dem Gerät (SQLCipher läuft nur dort).
