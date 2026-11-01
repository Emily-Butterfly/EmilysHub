# A change to the database's schema

What a change to a table or column of `HubDatabase` takes, and what it no longer takes. The backups follow the schema
by themselves: they read the tables from the database (`DataTables`), and the tests build a backup of an older version
from the schema Room exported for it (`BackupMigration.dataColumnsOf`).

1. **The entity**: the column or table in its `@Entity`, and a new table also in `HubDatabase`'s `entities`.
2. **The version**: `HubDatabase.VERSION` one up.
3. **The migration**: a `Migration` from the version before in `data/Migrations.kt`, added to `MIGRATIONS` in
   `HubDatabase`'s companion.
4. **The schema**: Room exports `app/schemas/de.emilyshub.data.HubDatabase/<version>.json` on every build; checked in
   with the change, as the backups of that version are brought up to date with it. `BackupLayoutTest` fails should
   the schema in the app's assets not be the database the app makes.
5. **The migration's test**: in `androidTest/.../HubDatabaseMigrationTest.kt`, from the version before.
6. **A new table only**: one read in `BackupDao` and one line in `Backups.readBack`, so that a backup's rows of it are
   read as the app reads them. Every backup test fails until it is there.
7. **A test of a backup from before**, if what the column holds matters once restored: in `BackupsTest`, with
   `asOf(<version before>, …)`, which leaves out what that version did not have.

Nothing else: no list of tables or columns to keep by hand in the backups or their tests.

**Renaming** a table or column is the exception, in the tests only: the migration renames it in a backup of the
version before as in the app's own database, as backups are brought up to date by the app's migrations; but `asOf`
builds a backup of that version from today's, under the new name, which that version did not have. A test of a backup
from before the rename builds it with the old name by hand.
