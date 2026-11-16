# Tracking's photos lie in its database; a trip out to the camera or the folder picker keeps it open

The photos of Tracking's events (#133) are JPEGs in Tracking's own SQLCipher database (`event_photo`), not files of
their own. They are encrypted with Tracking's key the moment they are written, go along in its backups as the database
does, and are overwritten on disk by `secure_delete` when deleted, "Alles löschen" included. Files would have needed a
key of their own derived from Tracking's, a place in the island's snapshot, and their own way through backup, restore
and deleting. The lessons' attachments have that way (`AttachmentStore`), but under the app's key, not Tracking's.

The cost: a backup takes the whole database along. At first it lay in the backup's content, which also holds the
Doku and all other data, at most 20 MB (`BackupFile.MAX_BYTES`): a few dozen photos would have kept every backup from
being made. Since 16.11.2026 (format 3 of `BackupFile`) the islands' databases follow the content as records of their
own, in pieces of 4 MB, and an island may be up to 64 MB (`IslandSnapshot.MAX_BYTES`), several hundred photos; a
backup holds it in memory while it is made and opened. A photo that would take Tracking past 56 MB is not kept
(`PhotoAdd.Full`), so that the backup never fails for it; deleting photos makes room again. Photos are still kept
small, at most 1600 px at the long edge, and an event has at most 6. Revisit if Tracking comes near that size: then
the database is written into the backup as a stream, without holding it in memory, or the photos move to files.

Taking a photo hands the screen to the camera app, and saving the doctor's summary to the folder picker. Both stop the
activity, which locks an island (SPEC.md §14). Locked, the photo could not be encrypted on its return, and would wait
in plain text until the next unlock. So, as decided with the user on 15.11.2026, Tracking stays open for that one trip
(`Island.goingOut`). The trip ends with the result, which Android delivers before the app is back in front. Back in
front without it, the user came back some other way, from the launcher or the recent apps, and Tracking locks then.
Meanwhile the screen going off locks it, as does leaving it, and coming back after 5 minutes or more without a touch.
FLAG_SECURE stays on throughout. What stays open: while the user, from the camera app, goes to other apps, Tracking's
database stays open in memory until one of these locks it, at the latest after those 5 minutes.
