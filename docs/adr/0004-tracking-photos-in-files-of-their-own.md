# Tracking's photos lie in files of their own, each a record of its own in the backup

Supersedes the first part of [ADR 0003](0003-tracking-photos-in-its-database-trips-out-stay-open.md), where the photos lay in Tracking's database; its part on the trips out to the camera and the folder picker still holds.

The photos of Tracking's events (#133) are JPEGs in files of Tracking's own beside its database (`tracking.db-files`,
`IslandFileStore`), not in the database. Each is encrypted with AES-GCM under a key derived from Tracking's database
key the moment it is written, bound to its name, which is chosen by chance; the row in `event_photo` names the file.
So they are readable only while Tracking is open, as the database is, and they go along in its backups as they lie,
encrypted, without unlocking it. A backup carries each in a record of its own, after the database, read one by one as
it is written (format 3 of `BackupFile`): there is no limit to how many photos Tracking keeps but the phone's storage,
and a backup holds only one of them in memory at a time. While a backup reads them, a photo deleted keeps its file
until the backup is done, so that the backup holds together. Restoring puts the files back with the database, or
neither. "Alles löschen" deletes them all.

Until 16.11.2026 the photos lay in the database, which a backup held in its content, at most 20 MB shared with all
other data, then in records of its own up to 64 MB: a few dozen photos, then a few hundred, would have kept every
backup from being made. The photos of a database from before move into their files the first time Tracking is opened
after the update (`EventPhotos.moveOutOfDatabase`), each row of the database they lay in overwritten as it is moved.

What a file costs against the database: a deleted file is deleted, not overwritten as the database's rows are
(`secure_delete`); on flash storage an overwrite would not reach the old blocks anyway, and what is left there is
encrypted under Tracking's key. Photos are still kept small, at most 1600 px at the long edge, and an event has at most 6.
