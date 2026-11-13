package de.emilyshub.data

import android.content.Context
import de.emilyshub.security.DatabaseKeyFile
import de.emilyshub.security.KeystoreKeys
import java.io.File

/**
 * Opens the app's database on the phone (SPEC.md §9, #100), its key read from [keyFile], sealed with the Keystore key, or
 * made anew for a database still to be made; fails with a [DataUnavailable] that says why it cannot: the key file
 * unreadable or damaged, missing beside a database, or the Keystore key lost; the database damaged or sealed with another
 * key; or of a newer version of the app. Opens it right away, so that it fails here rather than on first use.
 */
fun openOnPhone(context: Context, keyFile: File, name: String = HubDatabase.NAME): HubDatabase {
    val databaseFile = HubDatabase.file(context, name)
    val key = try {
        // A new key would lock the database for good.
        check(keyFile.exists() || !databaseFile.exists()) { "The database key file is missing" }
        DatabaseKeyFile(keyFile, KeystoreKeys.getOrCreateAesGcmKey(KEYSTORE_ALIAS)).readOrCreate()
    } catch (e: Exception) {
        throw DataUnavailable(Unopenable.KeyBroken, e)
    }
    val database = HubDatabase.open(context, key, name)
    try {
        database.openHelper.writableDatabase
    } catch (e: Exception) {
        database.close()
        val newer = generateSequence<Throwable>(e) { it.cause }.any { it is NewerSchemaException }
        throw DataUnavailable(if (newer) Unopenable.NewerVersion else Unopenable.FileBroken, e)
    }
    return database
}

/** The files the app's data lie in on the phone besides the Doku (#100): the database, its key, and the lessons' attachments with theirs. */
fun phoneDataFiles(context: Context, keyFile: File, attachmentKeyFile: File, attachments: File): List<File> {
    val database = HubDatabase.file(context)
    val companions = listOf("-wal", "-shm", "-journal").map { File(database.path + it) }
    return listOf(database) + companions + listOf(keyFile, attachmentKeyFile, attachments, File(attachments.path + "-aside"))
}

/** The alias of the Keystore key that seals the keys of the database and of the attachments. */
const val KEYSTORE_ALIAS = "database-key"
