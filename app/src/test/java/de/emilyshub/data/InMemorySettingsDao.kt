package de.emilyshub.data

/** Stands in for the encrypted database, which needs SQLCipher's native library and the Android Keystore. */
class InMemorySettingsDao : SettingsDao {
    val values = mutableMapOf<String, String>()

    override suspend fun get(name: String): String? = values[name]

    override suspend fun put(setting: Setting) {
        values[setting.name] = setting.value
    }
}
