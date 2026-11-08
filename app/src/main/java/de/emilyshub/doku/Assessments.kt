package de.emilyshub.doku

import de.emilyshub.data.InputText
import de.emilyshub.doku.assessments.Answer
import de.emilyshub.doku.assessments.AnswerCodec
import de.emilyshub.doku.assessments.Assessment
import de.emilyshub.doku.assessments.BuiltInAssessments
import de.emilyshub.doku.assessments.Score
import de.emilyshub.doku.assessments.ScoreResult
import de.emilyshub.doku.assessments.Scoring
import de.emilyshub.doku.assessments.StoredAnswer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The built-in forms filled in at appointments (SPEC.md §14), several at one, the same twice too. Only their answers are
 * stored, by item id; their results are reckoned anew from them each time, so that a form corrected in an update holds
 * for those filled in before. Saved as far as answered; an active patient's are written here, a closed one's only read.
 * The forms last used and those hidden are kept in the Doku's database. All of it is read and written only while
 * [doku] is open, and its changes count in [Doku.changes]; what a page saves as it closes is written on [background].
 */
class Assessments(private val doku: Doku, private val background: CoroutineScope) {

    /**
     * Adds form [assessmentId] at appointment [appointmentId], nothing answered yet; it counts as used once answered. Its id, none for a closed patient's, a form this version does not know, or once locked.
     */
    suspend fun create(appointmentId: Long, assessmentId: String): Long? = doku.changing { write(it, appointmentId, null, assessmentId, emptyMap()) }

    /** Filled-in form [id] to fill in or read, with whether it may be changed; none once deleted or locked. */
    suspend fun sheet(id: Long): AssessmentSheet? = doku.withDatabase { database ->
        read(database, id)?.let { AssessmentSheet(it, isChangeable(database, it.appointmentId)) }
    }

    /**
     * Deletes new form [id] again if nothing of it is answered, so that "+ Bogen" alone keeps nothing; on [background],
     * for the page as it closes, not once the Doku is locked. One whose result stands without an answer, such as an RMDQ
     * with nothing ticked, which is 0, stays if [confirmed] by "Fertig", and counts as used (08.11.2026).
     */
    fun dropIfEmptyLater(id: Long, confirmed: Boolean = false) {
        background.launch { doku.changing { dropIfEmpty(it, id, confirmed) } }
    }

    /** As [dropIfEmptyLater], to be done as the Doku locks ([DokuSaver]): not confirmed. */
    fun pendingDrop(id: Long): suspend (DokuDatabase) -> Unit = { dropIfEmpty(it, id, confirmed = false) }

    /** Filled-in form [id]; none once deleted or locked. */
    suspend fun get(id: Long): FilledAssessment? = doku.withDatabase { read(it, id) }

    /** Filled-in form [id] with its appointment's day, if both are there. */
    private suspend fun read(database: DokuDatabase, id: Long): FilledAssessment? {
        val entry = database.assessments().get(id) ?: return null
        val appointment = database.appointments().get(entry.appointmentId) ?: return null
        return filled(entry, appointment.date, database.assessments().answers(id))
    }

    /** The forms filled in at appointment [appointmentId], in the order they were added; none once locked. */
    suspend fun at(appointmentId: Long): List<FilledAssessment>? = doku.withDatabase { database ->
        val appointment = database.appointments().get(appointmentId) ?: return@withDatabase emptyList()
        database.assessments().at(appointmentId).map { filled(it, appointment.date, database.assessments().answers(it.id)) }
    }

    /** Whether forms may be filled in or changed at appointment [appointmentId]: its patient is active. */
    suspend fun canChange(appointmentId: Long): Boolean = doku.withDatabase { isChangeable(it, appointmentId) } == true

    /**
     * Saves [answers] to form [assessmentId] as filled-in form [id] or as a new one at appointment [appointmentId]: those
     * to items shown, and to items the form no longer has, so that they stay. Its id; none for a closed patient's, for
     * a form this version does not know, or once locked. Answered for the first time, it goes to the top of those last used.
     */
    suspend fun save(appointmentId: Long, id: Long?, assessmentId: String, answers: Map<String, Answer>): Long? =
        doku.changing { write(it, appointmentId, id, assessmentId, answers) }

    /** As [save], on [background], for a page that saves as it closes; not once the Doku is locked. */
    fun saveLater(appointmentId: Long, id: Long?, assessmentId: String, answers: Map<String, Answer>) {
        background.launch { save(appointmentId, id, assessmentId, answers) }
    }

    /** What a page has answered, to be written as the Doku locks ([DokuSaver]). */
    fun pending(appointmentId: Long, id: Long?, assessmentId: String, answers: Map<String, Answer>): suspend (DokuDatabase) -> Unit =
        { write(it, appointmentId, id, assessmentId, answers) }

    /** Deletes filled-in form [id]; one of a closed patient stays, as it is only to read. */
    suspend fun delete(id: Long) {
        doku.changing { database ->
            val entry = database.assessments().get(id) ?: return@changing
            if (isChangeable(database, entry.appointmentId)) database.assessments().delete(id)
        }
    }

    /** The forms used last, at most [RECENT], the latest first; not those hidden. None once locked. */
    suspend fun recent(): List<Assessment>? = doku.withDatabase { database ->
        val hidden = hidden(database)
        idsOf(database.settings().get(RECENT_KEY)).filter { it !in hidden }.mapNotNull(BuiltInAssessments::byId)
    }

    /** The ids of the forms hidden from "+ Bogen"; none once locked. */
    suspend fun hidden(): Set<String> = doku.withDatabase(::hidden).orEmpty()

    /** Hides form [assessmentId] from "+ Bogen", or offers it again; those filled in stay wherever they are. */
    suspend fun setHidden(assessmentId: String, hidden: Boolean) {
        doku.changing { database ->
            val now = hidden(database).let { if (hidden) it + assessmentId else it - assessmentId }
            database.settings().put(DokuSetting(HIDDEN_KEY, now.sorted().joinToString(",")))
        }
    }

    /**
     * The patient's forms: for each form with a result filled in at least once, a card, the one filled in last first;
     * and those without a result, the latest first. None once locked.
     */
    suspend fun cards(patientId: Long): AssessmentCards? = doku.withDatabase { database ->
        val filled = ofPatient(database, patientId)
        val (scored, unscored) = filled.partition { it.form?.scores?.isNotEmpty() == true }
        val cards = scored.groupBy { it.assessmentId }.map { (_, all) ->
            val form = checkNotNull(all.first().form)
            // Dated by the last that reached a result, as its values are; else by the last filled in.
            val lastWithResult = all.lastOrNull { f -> f.results.any { it.value != null } } ?: all.last()
            AssessmentCard(form, lastWithResult.date, form.scores.mapNotNull { score -> line(score, all) })
        }.sortedWith(compareByDescending<AssessmentCard> { it.lastFilled }.thenBy(InputText.byName) { it.form.name })
        AssessmentCards(cards, unscored.reversed())
    }

    /** The history of form [assessmentId] for patient [patientId]: a column for each result, a row for each filling in, the latest first. */
    suspend fun history(patientId: Long, assessmentId: String): AssessmentHistory? = doku.withDatabase { database ->
        val patient = database.patients().get(patientId) ?: return@withDatabase null
        val form = BuiltInAssessments.byId(assessmentId) ?: return@withDatabase null
        val filled = ofPatient(database, patientId).filter { it.assessmentId == assessmentId }
        if (filled.isEmpty()) return@withDatabase null
        val columns = form.scores.map { score ->
            val range = Scoring.range(form, score)
            HistoryColumn(score.name, score.unit, score.direction, range.first, range.last)
        }
        val rows = filled.reversed().map { f -> AssessmentHistoryRow(f.id, f.appointmentId, f.date, f.results.map(ScoreResult::value)) }
        AssessmentHistory(form, patient.name, columns, rows)
    }

    /**
     * Deletes form [id] of an active patient if nothing of it is answered; unless [confirmed] and its result stands without
     * an answer ([Scoring.reachesResultUnanswered]), when it stays and goes to the top of those last used.
     */
    private suspend fun dropIfEmpty(database: DokuDatabase, id: Long, confirmed: Boolean) {
        val entry = database.assessments().get(id) ?: return
        if (!isChangeable(database, entry.appointmentId) || database.assessments().answers(id).isNotEmpty()) return
        if (confirmed && BuiltInAssessments.byId(entry.assessmentId)?.let(Scoring::reachesResultUnanswered) == true) {
            markUsed(database, entry.assessmentId)
        } else {
            database.assessments().delete(id)
        }
    }

    /** Puts form [assessmentId] on top of those last used. */
    private suspend fun markUsed(database: DokuDatabase, assessmentId: String) {
        database.settings().put(DokuSetting(RECENT_KEY, withRecent(idsOf(database.settings().get(RECENT_KEY)), assessmentId).joinToString(",")))
    }

    /**
     * Writes [answers] to form [assessmentId] at appointment [appointmentId], as filled-in form [id] or a new one, as
     * [save] describes; its id, or none.
     */
    private suspend fun write(database: DokuDatabase, appointmentId: Long, id: Long?, assessmentId: String, answers: Map<String, Answer>): Long? {
        if (!isChangeable(database, appointmentId)) return null
        if (id != null && database.assessments().get(id)?.let { it.appointmentId == appointmentId && it.assessmentId == assessmentId } != true) return null
        val form = BuiltInAssessments.byId(assessmentId)
        if (form == null && id == null) return null
        val kept = form?.let { f -> Scoring.visible(f, answers).map { it.id }.toSet() + Scoring.orphans(f, answers).keys }
        val stored = answers.filterKeys { kept == null || it in kept }.mapNotNull { (itemId, answer) ->
            AnswerCodec.encode(answer)?.let { AssessmentAnswer(0, itemId, it.value, InputText.multiLine(it.text)) }
        }
        val answeredBefore = id != null && database.assessments().answers(id).isNotEmpty()
        val saved = database.assessments().save(AssessmentEntry(id ?: 0, appointmentId, assessmentId), stored)
        // Used once answered: one added by "+ Bogen" and left empty goes again, and was not used.
        if (!answeredBefore && stored.isNotEmpty()) markUsed(database, assessmentId)
        return saved
    }

    /** The line of a card for [score], from the forms [filled] in, oldest first, that reach a result; none without any. */
    private fun line(score: Score, filled: List<FilledAssessment>): AssessmentCardLine? {
        val values = filled.mapNotNull { f -> f.results.find { it.score.id == score.id }?.takeIf { it.value != null }?.let { f.date to it } }
        val (since, first) = values.firstOrNull() ?: return null
        val last = values.last().second
        return AssessmentCardLine(
            score = score,
            last = checkNotNull(last.value),
            band = last.band,
            change = changeOf(checkNotNull(first.value), checkNotNull(last.value), since, score.direction, measuredOnce = values.size == 1),
        )
    }

    /** The forms of patient [patientId], oldest first. */
    private suspend fun ofPatient(database: DokuDatabase, patientId: Long): List<FilledAssessment> {
        val dao = database.assessments()
        val answers = dao.answersOfPatient(patientId).groupBy(AssessmentAnswer::entryId)
        return dao.ofPatient(patientId).sortedWith(OLDEST_FIRST).map { filled(it.entry, it.date, answers[it.entry.id].orEmpty()) }
    }

    /** [entry] on [date] with its stored [answers] read back; those of a kind this version does not know left out. */
    private fun filled(entry: AssessmentEntry, date: LocalDate, answers: List<AssessmentAnswer>) = FilledAssessment(
        id = entry.id,
        appointmentId = entry.appointmentId,
        date = date,
        assessmentId = entry.assessmentId,
        form = BuiltInAssessments.byId(entry.assessmentId),
        answers = answers.mapNotNull { a -> AnswerCodec.decode(StoredAnswer(a.value, a.text))?.let { a.itemId to it } }.toMap(),
    )

    /** The ids of the forms hidden from "+ Bogen". */
    private suspend fun hidden(database: DokuDatabase): Set<String> = idsOf(database.settings().get(HIDDEN_KEY)).toSet()

    /** Whether the appointment's patient is active: a closed patient's forms are only to read. */
    private suspend fun isChangeable(database: DokuDatabase, appointmentId: Long): Boolean {
        val appointment = database.appointments().get(appointmentId) ?: return false
        return database.patients().get(appointment.patientId)?.isClosed == false
    }

    companion object {
        /** How many forms "Zuletzt benutzt" shows. */
        const val RECENT = 5

        private const val RECENT_KEY = "assessments-recent"
        private const val HIDDEN_KEY = "assessments-hidden"

        /** By day, those without a time first, then by time, then as added. */
        private val OLDEST_FIRST: Comparator<DatedAssessmentEntry> = compareBy<DatedAssessmentEntry> { it.date }
            .thenComparator { a, b -> compareValues(a.time, b.time) }
            .thenBy { it.entry.id }

        /** [recent] with [used] on top, once, and [RECENT] at most. */
        fun withRecent(recent: List<String>, used: String): List<String> = (listOf(used) + (recent - used)).take(RECENT)

        private fun idsOf(stored: String?): List<String> = stored.orEmpty().split(',').filter(String::isNotEmpty)
    }
}

/**
 * A form filled in at appointment [appointmentId], on [date]: its [answers] by item id, and its [form] as this version
 * of the app has it, none if it no longer does.
 */
data class FilledAssessment(
    val id: Long,
    val appointmentId: Long,
    val date: LocalDate,
    val assessmentId: String,
    val form: Assessment?,
    val answers: Map<String, Answer>,
) {
    /** Its results, reckoned from the answers. */
    val results: List<ScoreResult> get() = form?.let { Scoring.results(it, answers) }.orEmpty()
}

/** A filled-in form to fill in, or only to read unless [canChange]. */
data class AssessmentSheet(val filled: FilledAssessment, val canChange: Boolean)

/** A patient's forms: a card for each with a result, and those without one, the latest first. */
data class AssessmentCards(val scored: List<AssessmentCard>, val unscored: List<FilledAssessment>)

/** A form filled in of a patient, last on [lastFilled], with how each of its results developed. */
data class AssessmentCard(val form: Assessment, val lastFilled: LocalDate, val lines: List<AssessmentCardLine>)

/** The [last] value of [score], its [band], and how it changed since the first. */
data class AssessmentCardLine(val score: Score, val last: Long, val band: String?, val change: Change)

/** A form's history for a patient: a column for each result and a row for each filling in, the latest first. */
data class AssessmentHistory(val form: Assessment, val patientName: String, val columns: List<HistoryColumn>, val rows: List<AssessmentHistoryRow>)

/** Form [id] filled in at [appointmentId] on [date], with a value, or none, for each result. */
data class AssessmentHistoryRow(val id: Long, val appointmentId: Long, val date: LocalDate, val numbers: List<Long?>)
