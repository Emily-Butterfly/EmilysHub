package de.emilyshub.doku

import de.emilyshub.data.InputText
import de.emilyshub.doku.assessments.Answer
import de.emilyshub.doku.assessments.AnswerCodec
import de.emilyshub.doku.assessments.Assessment
import de.emilyshub.doku.assessments.BuiltInAssessments
import de.emilyshub.doku.assessments.Item
import de.emilyshub.doku.assessments.Score
import de.emilyshub.doku.assessments.ScoreResult
import de.emilyshub.doku.assessments.Scoring
import de.emilyshub.doku.assessments.Section
import de.emilyshub.doku.assessments.StoredAnswer
import de.emilyshub.doku.assessments.isNothing
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
class Assessments(private val doku: DokuStore, private val background: CoroutineScope) {

    /**
     * Adds form [assessmentId] at appointment [appointmentId], nothing answered yet; it counts as used once answered. Its id, none for a closed patient's, a form this version does not know, or once locked.
     */
    suspend fun create(appointmentId: Long, assessmentId: String): Long? = doku.changing { write(it, appointmentId, null, assessmentId, emptyMap()) }

    /** Filled-in form [id] to fill in or read, with whether it may be changed; none once deleted or locked. */
    suspend fun sheet(id: Long): AssessmentSheet? = doku.withDatabase { database ->
        read(database, id)?.let { AssessmentSheet(it, database.editableAppointment(it.appointmentId) != null) }
    }

    /**
     * What the page of [sheet] has not saved yet: its answers, once [AssessmentSheet.changed]; nothing of one only to
     * read. A [new] one with nothing answered goes again as the page closes, so that "+ Bogen" alone keeps nothing, but
     * not as the screen turns ([Leaving]); one whose result stands without an answer, such as an RMDQ with nothing ticked, which is
     * 0, stays if [confirmed] by "Fertig", and counts as used (08.11.2026). As the Doku locks, it is not confirmed.
     */
    fun draft(sheet: AssessmentSheet, new: Boolean, confirmed: Boolean): DokuDraft {
        if (!sheet.canChange) return DokuDraft.None
        val filled = sheet.filled
        val empty = new && sheet.answers.isEmpty()
        val write: suspend (DokuDatabase) -> Unit = { write(it, filled.appointmentId, filled.id, filled.assessmentId, sheet.answers) }
        return object : DokuDraft {
            override fun pending(): (suspend (DokuDatabase) -> Unit)? = when {
                empty -> { database -> dropIfEmpty(database, filled.id, confirmed = false) }
                sheet.changed -> write
                else -> null
            }

            override fun close(leaving: Leaving) {
                when {
                    empty -> if (leaving == Leaving.Closed) background.launch { doku.changing { dropIfEmpty(it, filled.id, confirmed) } }
                    sheet.changed -> background.launch { doku.changing(write) }
                }
            }
        }
    }

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

    /**
     * Saves [answers] to form [assessmentId] as filled-in form [id] or as a new one at appointment [appointmentId]: those
     * to items shown, and to items the form no longer has, so that they stay. Its id; none for a closed patient's, for
     * a form this version does not know, or once locked. Answered for the first time, it goes to the top of those last used.
     */
    suspend fun save(appointmentId: Long, id: Long?, assessmentId: String, answers: Map<String, Answer>): Long? =
        doku.changing { write(it, appointmentId, id, assessmentId, answers) }

    /** Deletes filled-in form [id]; one of a closed patient stays, as it is only to read. */
    suspend fun delete(id: Long) {
        doku.changing { database ->
            val entry = database.assessments().get(id) ?: return@changing
            if (database.editableAppointment(entry.appointmentId) != null) database.assessments().delete(id)
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
        if (database.editableAppointment(entry.appointmentId) == null || database.assessments().answers(id).isNotEmpty()) return
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
        database.editableAppointment(appointmentId) ?: return null
        if (id != null && database.assessments().get(id)?.let { it.appointmentId == appointmentId && it.assessmentId == assessmentId } != true) return null
        val form = BuiltInAssessments.byId(assessmentId)
        if (form == null && id == null) return null
        val stored = AssessmentSheet.kept(form, answers).mapNotNull { (itemId, answer) ->
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
    /** Its results, reckoned from the answers once. */
    val results: List<ScoreResult> by lazy { form?.let { Scoring.results(it, answers) }.orEmpty() }
}

/**
 * Filled-in form [filled] to fill in, or only to read unless [canChange], with its [answers] as given so far: what its
 * page shows and the Doku keeps of it. Items under a condition show once it holds; an answer that says nothing is none;
 * answers its form no longer has, or of another kind, are orphans, shown apart and kept; answers to items not shown stay
 * here, so that they come back with their condition, but neither count nor are kept. Pure, without the database.
 */
data class AssessmentSheet(val filled: FilledAssessment, val canChange: Boolean, val answers: Map<String, Answer> = filled.answers) {
    /** Its form as this version of the app has it; none if it no longer does. */
    val form: Assessment? get() = filled.form

    /** Its form's sections in order, each with the items shown and their answers; none without its form. */
    val sections: List<ShownSection> by lazy {
        val form = form ?: return@lazy emptyList()
        val shown = Scoring.visible(form, answers).map(Item::id).toSet()
        form.sections.map { section ->
            ShownSection(section, section.items.filter { it.id in shown }.map { item -> ShownItem(item, answers[item.id]?.takeIf { item.fits(it) }) })
        }
    }

    /** The answers its form no longer has, or that no longer fit their item; all of them without its form. */
    val orphans: Map<String, Answer> by lazy { form?.let { Scoring.orphans(it, answers) } ?: answers }

    /** Its results, reckoned from the answers to the items shown; none without its form. */
    val results: List<ScoreResult> by lazy { form?.let { Scoring.results(it, answers) }.orEmpty() }

    /** What is kept of it: the answers to the items shown, and the orphans. */
    val stored: Map<String, Answer> by lazy { kept(form, answers) }

    /** Whether its answers differ from those it was read with. */
    val changed: Boolean get() = answers != filled.answers

    /** With [answer] to item [itemId], or none; one that says nothing is none. */
    fun answering(itemId: String, answer: Answer?): AssessmentSheet =
        copy(answers = if (answer == null || answer.isNothing) answers - itemId else answers + (itemId to answer))

    companion object {
        /** What is kept of [answers] to [form]: those to the items shown and the orphans; all of them without a form. */
        internal fun kept(form: Assessment?, answers: Map<String, Answer>): Map<String, Answer> {
            val kept = form?.let { f -> Scoring.visible(f, answers).map(Item::id).toSet() + Scoring.orphans(f, answers).keys }
            return answers.filter { (id, answer) -> (kept == null || id in kept) && !answer.isNothing }
        }
    }
}

/** A [section] of a form with the [items] shown of it. */
data class ShownSection(val section: Section, val items: List<ShownItem>)

/** An [item] shown, with its [answer], none if unanswered or answered with another kind. */
data class ShownItem(val item: Item, val answer: Answer?)

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
