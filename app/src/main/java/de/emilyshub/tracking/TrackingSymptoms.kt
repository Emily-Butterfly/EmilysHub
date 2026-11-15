package de.emilyshub.tracking

/**
 * The symptoms asked for in the evening (SPEC.md §17, "Symptome"): the start list at first, then as one renames,
 * orders, hides and adds them. Hidden ones keep their values and show in the entries that have them. Read and written
 * only while Tracking is open.
 */
class TrackingSymptoms(private val tracking: TrackingStore) {

    /** All symptoms in their order, the hidden ones too; none once locked. */
    suspend fun all(): List<Symptom>? = tracking.withDatabase(::listed)

    /** All symptoms of [database] in their order, the start list put in first if it never was. */
    internal suspend fun listed(database: TrackingDatabase): List<Symptom> {
        if (database.settings().get(SEEDED) == null) {
            database.symptoms().insert(START_LIST.mapIndexed { position, name -> Symptom(name = name, position = position) })
            database.settings().put(TrackingSetting(SEEDED, "1"))
        }
        return database.symptoms().all()
    }

    /** Adds a symptom of one's own, [name], at the end; false if the name is empty, too long or taken. */
    suspend fun add(name: String): Boolean = tracking.changing { database ->
        val all = listed(database)
        val cleaned = cleanedName(name, all, except = null) ?: return@changing false
        database.symptoms().insert(listOf(Symptom(name = cleaned, position = (all.maxOfOrNull { it.position } ?: -1) + 1)))
        true
    } ?: false

    /** Renames symptom [id] to [name], its values staying; false if the name is empty, too long or taken. */
    suspend fun rename(id: Long, name: String): Boolean = tracking.changing { database ->
        val cleaned = cleanedName(name, listed(database), except = id) ?: return@changing false
        database.symptoms().rename(id, cleaned)
        true
    } ?: false

    /** Hides symptom [id] from the evening entries to come, or shows it again; its values stay. */
    suspend fun setHidden(id: Long, hidden: Boolean) {
        tracking.changing { it.symptoms().setHidden(id, hidden) }
    }

    /** Moves symptom [id] one up ([up]) or down in the list. */
    suspend fun move(id: Long, up: Boolean) {
        tracking.changing { database ->
            val all = listed(database)
            val index = all.indexOfFirst { it.id == id }
            val other = if (up) index - 1 else index + 1
            if (index < 0 || other !in all.indices) return@changing
            val reordered = all.toMutableList().apply { add(other, removeAt(index)) }
            reordered.forEachIndexed { position, symptom -> database.symptoms().setPosition(symptom.id, position) }
        }
    }

    private fun cleanedName(name: String, all: List<Symptom>, except: Long?): String? {
        val cleaned = name.trim()
        if (cleaned.isEmpty() || cleaned.length > NAME_MAX) return null
        if (all.any { it.id != except && it.name.equals(cleaned, ignoreCase = true) }) return null
        return cleaned
    }

    companion object {
        /** The longest name a symptom can have. */
        const val NAME_MAX = 60

        private const val SEEDED = "symptoms_seeded"

        /** The start list (SPEC.md §17), muscle and joint pain apart. */
        val START_LIST = listOf(
            "Erschöpfung",
            "Konzentration",
            "Schlafbedürfnis",
            "Kopfschmerz",
            "Muskelschmerzen",
            "Gelenkschmerzen",
            "Muskelschwäche",
            "Atemnot",
            "Herzrasen",
            "Schwindel im Stehen",
            "Übelkeit",
            "Hautveränderungen",
            "Kälteempfinden",
            "Hitzeempfinden",
            "Stimmung gedrückt",
            "Angst/innere Unruhe",
        )
    }
}
