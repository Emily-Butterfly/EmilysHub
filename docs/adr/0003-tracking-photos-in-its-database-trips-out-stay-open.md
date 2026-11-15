# Tracking's photos lie in its database; a trip out to the camera or the folder picker keeps it open

The photos of Tracking's events (#133) are JPEGs in Tracking's own SQLCipher database (`event_photo`), not files of
their own. They are encrypted with Tracking's key the moment they are written, go along in its backups as the database
does, and are overwritten on disk by `secure_delete` when deleted, "Alles löschen" included. Files would have needed a
key of their own derived from Tracking's, a place in the island's snapshot, and their own way through backup, restore
and deleting. The lessons' attachments have that way (`AttachmentStore`), but under the app's key, not Tracking's.

The cost: a backup holds the whole database in its content, at most 20 MB (`BackupFile.MAX_BYTES`). So photos are
kept small, at most 1600 px at the long edge, and an event has at most 6. Revisit if Tracking's backups come near that
size: then the photos move to files, carried as records of their own in the backup, encrypted under a key from
Tracking's.

Taking a photo hands the screen to the camera app, and saving the doctor's summary to the folder picker. Both stop the
activity, which locks an island (SPEC.md §14). Locked, the photo could not be encrypted on its return, and would wait
in plain text until the next unlock. So, as decided with the user on 15.11.2026, Tracking stays open for that one trip
(`Island.goingOut`). The trip ends with the result, which Android delivers before the app is back in front. Back in
front without it, the user came back some other way, from the launcher or the recent apps, and Tracking locks then.
Meanwhile the screen going off locks it, as does leaving it, and coming back after 5 minutes or more without a touch.
FLAG_SECURE stays on throughout. What stays open: while the user, from the camera app, goes to other apps, Tracking's
database stays open in memory until one of these locks it, at the latest after those 5 minutes.
