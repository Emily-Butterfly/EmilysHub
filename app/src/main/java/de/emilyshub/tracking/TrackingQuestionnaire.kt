package de.emilyshub.tracking

import java.time.Clock
import java.time.LocalDate

/** A completed questionnaire with its [score]. */
data class FuncapResult(val form: FuncapForm, val score: FuncapScore) {
    /** The day it counts for: the day it was completed. */
    val day: LocalDate get() = checkNotNull(form.completedOn)
}

/** The draft on its page: the [form], its [answers] by item, and whether it is [stale] and to be discarded once asked. */
data class FuncapDraft(val form: FuncapForm, val answers: Map<Int, Int>, val stale: Boolean)

/** What the questionnaire's page and the start page show: whether one is [due], the [draft] if begun, the [results], the latest first. */
data class FuncapOverview(val due: Boolean, val draft: FuncapDraft?, val results: List<FuncapResult>)

/**
 * The questionnaire in the doctor's summary: the [latest] one with the one before it ([previous]), and beside them the
 * means of the evenings' fatigue and muscle and joint pain over the [FuncapSummary.MONTH_DAYS] days up to the latest
 * ([monthMeans]), as FUNCAP leaves fatigue and pain out.
 */
data class FuncapSummary(val latest: FuncapResult, val previous: FuncapResult?, val monthMeans: List<SymptomFigure>) {
    companion object {
        /** The month the means of the evenings reach over, ending on the day of the latest questionnaire. */
        const val MONTH_DAYS = 28L

        /**
         * The symptoms whose monthly means go beside the questionnaire: fatigue, muscle pain and joint pain of the start
         * list, by their names there; one renamed is not found.
         */
        val MONTH_SYMPTOMS = listOf(TrackingSymptoms.START_LIST[0], TrackingSymptoms.START_LIST[4], TrackingSymptoms.START_LIST[5])

        /** The latest of [results] completed by [to], of two on a day the one completed last; none without one. */
        fun latest(results: List<FuncapResult>, to: LocalDate): FuncapResult? = upTo(results, to).firstOrNull()

        private fun upTo(results: List<FuncapResult>, to: LocalDate) =
            results.filter { !it.day.isAfter(to) }.sortedWith(compareByDescending<FuncapResult> { it.day }.thenByDescending { it.form.id })

        /** The first day of the month of [latest]'s means. */
        fun monthFrom(latest: FuncapResult): LocalDate = latest.day.minusDays(MONTH_DAYS - 1)

        /**
         * The summary of [results], the latest first, as it stood on [to]: none without one completed by then; the
         * means of [values] of [symptoms] named as [MONTH_SYMPTOMS] over the month up to the latest, in that order.
         */
        fun of(results: List<FuncapResult>, to: LocalDate, symptoms: List<Symptom>, values: List<SymptomValue>): FuncapSummary? {
            val upTo = upTo(results, to)
            val latest = upTo.firstOrNull() ?: return null
            val month = monthFrom(latest)..latest.day
            val means = MONTH_SYMPTOMS.mapNotNull { name ->
                symptoms.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { symptom ->
                    SymptomFigure(symptom, Figure.mean(values.filter { it.symptomId == symptom.id && it.date in month }.map { it.level }))
                }
            }
            return FuncapSummary(latest, upTo.getOrNull(1), means)
        }
    }
}

/**
 * The monthly questionnaire of Tracking (SPEC.md §17, "Monatsfragebogen"): FUNCAP55, due [Funcap.DUE_DAYS] days after
 * the last; begun, it is kept as a draft as it is answered, item by item, and counts for the day it is completed on. A
 * draft older than [Funcap.DRAFT_DAYS] days is discarded once asked. Read and written only while Tracking is open.
 */
class TrackingQuestionnaire(private val tracking: TrackingStore, private val clock: Clock) {

    private fun today(): LocalDate = LocalDate.now(clock)

    /** Whether one is due, the draft and the results; none once locked. */
    suspend fun overview(): FuncapOverview? = tracking.withDatabase { database ->
        val results = resultsOf(database)
        FuncapOverview(Funcap.due(results.firstOrNull()?.day, today()), draftOf(database), results)
    }

    /** The draft, begun now if there is none; none once locked. */
    suspend fun begin(): FuncapDraft? = tracking.changing { database ->
        draftOf(database) ?: run {
            val form = FuncapForm(startedOn = today())
            FuncapDraft(form.copy(id = database.funcap().insert(form)), emptyMap(), stale = false)
        }
    }

    /** Answers [item] of draft [formId] with [value], or takes its answer back with none; false off the scale, for no item, or once completed or locked. */
    suspend fun answer(formId: Long, item: Int, value: Int?): Boolean = tracking.changing { database ->
        val form = database.funcap().get(formId)?.takeIf { it.completedOn == null } ?: return@changing false
        if (Funcap.domainOf(item) == null || (value != null && value !in Funcap.SCALE)) return@changing false
        if (value == null) database.funcap().clear(form.id, item) else database.funcap().put(FuncapAnswer(form.id, item, value))
        true
    } ?: false

    /** Completes draft [formId] today, the day it counts for; false without any answer, if stale, or once completed or locked. */
    suspend fun complete(formId: Long): Boolean = tracking.changing { database ->
        val form = database.funcap().get(formId)?.takeIf { it.completedOn == null } ?: return@changing false
        if (Funcap.stale(form.startedOn, today()) || database.funcap().answers(form.id).isEmpty()) return@changing false
        database.funcap().update(form.copy(completedOn = today()))
        true
    } ?: false

    /** Deletes questionnaire [formId], draft or completed, with its answers. */
    suspend fun delete(formId: Long) {
        tracking.changing { it.funcap().delete(formId) }
    }

    /** The completed questionnaire [formId] with its answers; none if gone, a draft, or once locked. */
    suspend fun result(formId: Long): Pair<FuncapResult, Map<Int, Int>>? = tracking.withDatabase { database ->
        val form = database.funcap().get(formId)?.takeIf { it.completedOn != null } ?: return@withDatabase null
        val answers = database.funcap().answers(form.id).associate { it.item to it.value }
        FuncapResult(form, Funcap.score(answers)) to answers
    }

    private suspend fun draftOf(database: TrackingDatabase): FuncapDraft? = database.funcap().draft()?.let { form ->
        FuncapDraft(form, database.funcap().answers(form.id).associate { it.item to it.value }, Funcap.stale(form.startedOn, today()))
    }

    companion object {
        /** The completed questionnaires of [database] with their scores, the latest first. */
        suspend fun resultsOf(database: TrackingDatabase): List<FuncapResult> {
            val answers = database.funcap().completedAnswers().groupBy { it.formId }
            return database.funcap().completed().map { form ->
                FuncapResult(form, Funcap.score(answers[form.id].orEmpty().associate { it.item to it.value }))
            }
        }
    }
}
