package de.emilyshub.island

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * An island's database key in SQLCipher's raw form, x'<64 hex digits>', used as is without a slow passphrase derivation.
 * The bytes stay SQLCipher's while the database is open, so that wiping them once it is closed wipes SQLCipher's copy.
 */
@JvmInline
value class RawKey(val bytes: ByteArray) {
    fun wipe() = bytes.fill(0)

    companion object {
        /** The [RawKey] of [key], written straight into bytes, so that no string of it lingers. */
        fun of(key: ByteArray): RawKey {
            require(key.size == IslandKeys.KEY_BYTES) { "An island's key must be ${IslandKeys.KEY_BYTES} bytes" }
            val raw = ByteArray(3 + key.size * 2)
            raw[0] = 'x'.code.toByte()
            raw[1] = '\''.code.toByte()
            key.forEachIndexed { i, byte ->
                raw[2 + 2 * i] = HEX[(byte.toInt() shr 4) and 0xF].code.toByte()
                raw[3 + 2 * i] = HEX[byte.toInt() and 0xF].code.toByte()
            }
            raw[raw.lastIndex] = '\''.code.toByte()
            return RawKey(raw)
        }

        private const val HEX = "0123456789abcdef"
    }
}

/**
 * What is deleted from an island is overwritten on disk, not only marked free (SPEC.md §14, §17): on each opening, as
 * the pragma lasts as long as the connection.
 */
val SECURE_DELETE = object : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
    }
}

/** Moves what the write-ahead log holds into the database file, so that the file alone holds it. */
fun RoomDatabase.checkpoint() {
    openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
}
