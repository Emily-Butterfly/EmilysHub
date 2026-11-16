package de.emilyshub.tracking

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import java.time.LocalDate

/** An item of FUNCAP55: its [number], 1–55, and its [text]. */
data class FuncapItem(val number: Int, val text: String)

/** A domain of FUNCAP55: its letter [code], A–H, its [name] and its [items]. */
data class FuncapDomain(val code: Char, val name: String, val items: List<FuncapItem>)

/**
 * The scores of a questionnaire: each domain's, by its code, the mean of its answered items or none without any; the
 * [total], the mean of the eight, only when all eight have one; and how many items were [answered].
 */
data class FuncapScore(val domains: Map<Char, Double?>, val total: Double?, val answered: Int)

/**
 * The monthly questionnaire (SPEC.md §17, "Monatsfragebogen"): FUNCAP55, the questionnaire of functional capacity in
 * ME/CFS, in its official German version, its items and answers as published, only laid out for the app. Pure logic:
 * the items, the scores and when it is due.
 *
 * FUNCAP is published under Creative Commons Attribution 4.0 (CC BY 4.0) by its authors at funcap.no; the German
 * version is the one approved there ([SOURCE]). The scores follow its development paper: a domain's score is the mean
 * of its items, the total the mean of the domains; higher is better. FUNCAP sets no cut-offs and says nothing of items
 * left out; that a domain counts with any answered item and the total only with all eight is our own setting.
 */
object Funcap {
    /** Where the questionnaire and its licence come from, as the questionnaire, its result and the PDF name it ([Source.Funcap]). */
    val SOURCE = "FUNCAP55 – Fragebogen zur funktionellen Kapazität, deutsche Fassung, funcap.no. " +
        "Lizenz CC BY 4.0 (creativecommons.org/licenses/by/4.0). Für die App-Darstellung umformatiert. " +
        "[${Source.Funcap.code}] ${Source.Funcap.citation}"

    /** What the questionnaire asks about, before its items. */
    const val INTRODUCTION =
        "Dieser Fragebogen bewertet Ihre funktionelle Kapazität für eine Reihe von Aktivitäten. Jedoch ist kein Tag wie " +
            "der andere. Daher beziehen Sie Ihre Antwort bitte auf einen durchschnittlichen Tag im letzten Monat – nicht " +
            "auf den schlechtesten oder besten. Wenn sich eine Frage auf eine Aktivität bezieht, die Sie nicht ausführen, " +
            "wie z.B. sitzend duschen, weil Sie immer im Stehen duschen, dann bewerten Sie so, wie Sie glauben, dass diese " +
            "Aktivität Sie beeinflusst hätte. Zu den beschriebenen Aktivitäten gehören alle zu ihrer Ausführung " +
            "erforderlichen Tätigkeiten. Zum Beispiel: „Einkaufen gehen“ schließt das Anziehen und notwendige " +
            "Fahrten/Wege mit ein.\n\nEs empfiehlt sich, den Fragebogen zusammen mit jemanden zu beantworten, der Sie im " +
            "Alltag kennt."

    /** The question every item answers. */
    const val QUESTION = "Welche Folgen hat es für Sie, wenn Sie die unten beschriebenen Aktivitäten ausführen? " +
        "Inwieweit beeinflusst dies, wie viel Sie sonst noch tun können?"

    /** The answers, by their value 0–6. */
    val ANSWERS = listOf(
        "Ich kann das nicht tun",
        "Meine Kapazität wird für mindestens 3 Tage stark reduziert sein",
        "Ich kann am selben Tag und für 1 bis 2 Tage danach wenig anderes tun",
        "Ich kann am selben Tag wenig anderes tun",
        "Ich muss am selben Tag andere Aktivitäten einschränken",
        "Dies beeinflusst andere Aktivitäten kaum",
        "Unproblematisch – beeinflusst andere Aktivitäten nicht",
    )

    /** The values an answer can have. */
    val SCALE = ANSWERS.indices

    /** How many days after the last one the next is due (SPEC.md §17, our own setting). */
    const val DUE_DAYS = 28L

    /** How many days a draft keeps; older, it is discarded once asked (SPEC.md §17, our own setting). */
    const val DRAFT_DAYS = 7L

    val DOMAINS: List<FuncapDomain> = listOf(
        domain(
            'A', "Körperpflege / Grundfunktionen", 1,
            "Benutzung der Toilette (nicht Bettpfanne oder Toilettenstuhl)",
            "Zähneputzen ohne Hilfe",
            "Sitzend duschen, mit Hilfe",
            "Sitzend duschen, ohne Hilfe",
            "Stehend duschen",
            "Aufstehen und für ca. 1 Stunde außerhalb des Bettes bleiben",
            "Anziehen von Alltagsbekleidung",
        ),
        domain(
            'B', "Gehen / sich bewegen", 8,
            "Eine kurze Strecke drinnen gehen, von einem Raum zum anderen",
            "Eine kurze zusammenhängende Strecke von ca. 100 m (Länge eines Fußballfeldes) gehen, drinnen oder draußen",
            "Zwischen ca. 100 m und 1 km auf ebenem Gelände gehen (Länge von 1 bis 10 Fußballfeldern)",
            "Einen längeren Spaziergang machen. Ca. 1 km, meist ebenes Gelände",
            "Einen längeren Spaziergang machen. Ca. 1 km, hügeliges oder abwechslungsreiches Gelände",
            "Körperliche Aktivität mit erhöhter Herzfrequenz, für ca. 15 Minuten",
            "Körperliche Aktivität mit erhöhter Herzfrequenz, für ca. ½ Stunde",
        ),
        domain(
            'C', "Aufgerichtet sein", 15,
            "Sitzen im Bett für ca. ½ Stunde",
            "Sitzen in einem aufrechten Stuhl (Esszimmerstuhl) mit den Füßen auf dem Boden für ca. 10 Minuten",
            "Sitzen in einem aufrechten Stuhl (Esszimmerstuhl) mit den Füßen auf dem Boden für ca. 2 Stunden",
            "Aufrecht stehen für ca. 5 Minuten, z.B. beim Anstehen oder während des Kochens",
            "Langes aufrechtes Stehen – ca. ½ Stunde",
        ),
        domain(
            'D', "Aktivitäten zu Hause", 20,
            "Leichte Hausarbeit (Abstauben, Aufräumen usw.) für ca. ½ Stunde durchgehend",
            "Schwere Hausarbeit (Böden wischen, Staubsaugen usw.) für ca. ½ Stunde durchgehend",
            "Wäsche (sortieren, zum Trocknen aufhängen und falten)",
            "Zubereitung einer einfachen kalten Mahlzeit, z.B. eines Sandwichs oder Müsli",
            "Kochen einer einfachen warmen Mahlzeit",
            "Kochen einer komplizierten Mahlzeit von Grund auf, ca. 1 Stunde Zubereitung",
        ),
        domain(
            'E', "Kommunikation", 26,
            "Ein paar Worte sprechen",
            "Ein Gespräch führen für ca. 5 Minuten",
            "Ein Gespräch führen für ca. ½ Stunde",
            "Eine kurze Nachricht mit der Hand schreiben",
            "An einem Gespräch mit drei Personen für ca. ½ Stunde teilnehmen",
            "Sich mit Freund:innen treffen für ca. 1 Stunde",
            "An einer Abendgesellschaft, einer Party oder einer Familienveranstaltung teilnehmen",
        ),
        domain(
            'F', "Aktivitäten außerhalb Ihres Zuhauses", 33,
            "Vor die Haustüre treten",
            "Eine notwendige Erledigung durchführen, z.B. einen Arzttermin",
            "In ein Geschäft gehen, um Lebensmittel einzukaufen",
            "Freudevollen Freizeitaktivitäten nachgehen, z.B. ein Cafébesuch, nicht-essenzielle Einkäufe usw.",
            "Als Beifahrer:in in einem Auto mitfahren, für ca. 15 Minuten",
            "Öffentliche Verkehrsmittel benutzen (Bus oder Bahn)",
            "An organisierten Freizeitaktivitäten wie Kursen, Sport usw. teilnehmen",
        ),
        domain(
            'G', "Reaktionen auf Licht und Geräusche", 40,
            "Sich in einem Raum mit gedimmtem Licht für ca. ½ Stunde aufhalten",
            "Sich in einem Raum mit normaler Beleuchtung, ohne Sonnenbrille, für ca. 1 Stunde aufhalten",
            "Sich im Freien bei Tageslicht, ohne Sonnenbrille für ca. 2 Stunden aufhalten",
            "Sich in einer Umgebung mit dem Geräusch einiger Menschen, die sich leise unterhalten für ca. 1 Stunde aufhalten",
            "Sich in einer lauten Umgebung (Einkaufszentrum, Café oder Großraumbüro) für ca. 1 Stunde aufhalten",
            "Kinobesuch, Konzertbesuch usw. mit hohem Lärmpegel",
        ),
        domain(
            'H', "Konzentration", 46,
            "Lesen eines kurzen Textes, z.B. einer Textnachricht auf dem Handy",
            "Lesen von Belletristik/ leichter Lektüre",
            "Lesen und Verstehen eines Sachtextes, z.B. eines offiziellen Dokuments, eine A4-Seite",
            "Einfache Kopfrechnungen durchführen",
            "Schreiben von Kurznachrichten auf einem Smartphone oder Tablet",
            "Soziale Medien nutzen, um mit anderen in Kontakt zu bleiben",
            "Fernsehen (Serien, Nachrichten)",
            "Sich auf eine Aufgabe für ca. 10 Minuten durchgehend fokussieren",
            "Sich auf eine Aufgabe für ca. 2 Stunden durchgehend fokussieren",
            "Bewältigung eines vollen Arbeitstages (nicht-körperliche Arbeit wie Büroarbeit, Unterricht oder Vorlesungen)",
        ),
    )

    /** How many items there are. */
    val ITEMS: Int = DOMAINS.sumOf { it.items.size }

    private fun domain(code: Char, name: String, first: Int, vararg texts: String) =
        FuncapDomain(code, name, texts.mapIndexed { index, text -> FuncapItem(first + index, text) })

    /** The domain item [number] belongs to; none for no item. */
    fun domainOf(number: Int): FuncapDomain? = DOMAINS.firstOrNull { domain -> domain.items.any { it.number == number } }

    /** The scores of [answers], by item number; answers off [SCALE] or of no item do not count. */
    fun score(answers: Map<Int, Int>): FuncapScore {
        val valid = answers.filter { (item, value) -> value in SCALE && domainOf(item) != null }
        val domains = DOMAINS.associate { domain ->
            val values = domain.items.mapNotNull { valid[it.number] }
            domain.code to values.takeIf { it.isNotEmpty() }?.average()
        }
        val total = domains.values.takeIf { scores -> scores.all { it != null } }?.map { it!! }?.average()
        return FuncapScore(domains, total, valid.size)
    }

    /** Whether one is due on [today]: none done yet, or the last [DUE_DAYS] days or more ago. */
    fun due(lastCompleted: LocalDate?, today: LocalDate): Boolean = lastCompleted == null || !lastCompleted.plusDays(DUE_DAYS).isAfter(today)

    /** Whether a draft begun on [startedOn] is older than [DRAFT_DAYS] days on [today]. */
    fun stale(startedOn: LocalDate, today: LocalDate): Boolean = startedOn.plusDays(DRAFT_DAYS).isBefore(today)
}

/** A questionnaire, begun on [startedOn]; a draft until it is completed on [completedOn], the day it counts for. */
@Entity(tableName = "funcap_form")
data class FuncapForm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_on") val startedOn: LocalDate,
    @ColumnInfo(name = "completed_on") val completedOn: LocalDate? = null,
)

/** The answer [value], 0–6, to [item] of questionnaire [formId]; one not answered has no row. */
@Entity(
    tableName = "funcap_answer",
    primaryKeys = ["form_id", "item"],
    foreignKeys = [ForeignKey(entity = FuncapForm::class, parentColumns = ["id"], childColumns = ["form_id"], onDelete = ForeignKey.CASCADE)],
)
data class FuncapAnswer(@ColumnInfo(name = "form_id") val formId: Long, val item: Int, val value: Int)

/** The questionnaires and their answers. */
@Dao
interface FuncapDao {
    @Insert
    suspend fun insert(form: FuncapForm): Long

    @Update
    suspend fun update(form: FuncapForm)

    @Query("SELECT * FROM funcap_form WHERE id = :id")
    suspend fun get(id: Long): FuncapForm?

    /** The draft, if one is begun. */
    @Query("SELECT * FROM funcap_form WHERE completed_on IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun draft(): FuncapForm?

    /** The completed ones, the latest first. */
    @Query("SELECT * FROM funcap_form WHERE completed_on IS NOT NULL ORDER BY completed_on DESC, id DESC")
    suspend fun completed(): List<FuncapForm>

    @Query("SELECT * FROM funcap_answer WHERE form_id = :formId")
    suspend fun answers(formId: Long): List<FuncapAnswer>

    /** The answers of all completed ones. */
    @Query("SELECT a.* FROM funcap_answer a JOIN funcap_form f ON f.id = a.form_id WHERE f.completed_on IS NOT NULL")
    suspend fun completedAnswers(): List<FuncapAnswer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(answer: FuncapAnswer)

    @Query("DELETE FROM funcap_answer WHERE form_id = :formId AND item = :item")
    suspend fun clear(formId: Long, item: Int)

    /** Deletes questionnaire [id] with its answers. */
    @Query("DELETE FROM funcap_form WHERE id = :id")
    suspend fun delete(id: Long)
}
