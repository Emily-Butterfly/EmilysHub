package de.emilyshub.ui.tracking

import android.content.res.Resources
import de.emilyshub.R
import de.emilyshub.notes.Block
import de.emilyshub.notes.Markdown
import de.emilyshub.tracking.Certainty
import de.emilyshub.tracking.DoctorSummary
import de.emilyshub.tracking.Exertion
import de.emilyshub.tracking.Figure
import de.emilyshub.tracking.Funcap
import de.emilyshub.tracking.FuncapSummary
import de.emilyshub.tracking.GarminValue
import de.emilyshub.tracking.StandRecord
import de.emilyshub.tracking.StandVariant
import de.emilyshub.tracking.Tier
import de.emilyshub.tracking.WeekRules
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.dateAndTime
import de.emilyshub.ui.numericDate
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** A part of the doctor's summary as the preview and the PDF show it, one below the other. */
sealed interface SummaryBlock {
    /** The summary's title. */
    data class Title(val text: String) : SummaryBlock

    /** The heading of a section. */
    data class Heading(val text: String) : SummaryBlock

    /** A paragraph; [strong] in bold, [small] in a smaller size. */
    data class Text(val text: String, val strong: Boolean = false, val small: Boolean = false) : SummaryBlock

    /** A table under its [headers], each column as wide as its [weights] say. */
    data class Table(val headers: List<String>, val rows: List<List<String>>, val weights: List<Float>) : SummaryBlock

    /** A photo with its [caption]. Not a data class: its bytes compare by identity. */
    class Photo(val jpeg: ByteArray, val caption: String) : SummaryBlock
}

/**
 * The doctor's summary (SPEC.md §17, "Arzt-Zusammenfassung") as blocks of text in [resources]' words, the moments in
 * [zone]: its weeks, the strongest symptoms, the figures of the week evaluation, the Stehtests as a table, the events,
 * exertions and episodes, the medications with their timeline, Garmin's values with their tier, the latest monthly
 * questionnaire with the one before and the evenings' monthly means beside it, the current hints with
 * their questions, one's own [questions] in Markdown and the photos, if they go along. Made on [today]. Black on white
 * only, so that it reads as well printed in black and white.
 */
fun summaryBlocks(resources: Resources, summary: DoctorSummary, questions: String, zone: ZoneId, today: LocalDate): List<SummaryBlock> {
    val blocks = mutableListOf<SummaryBlock>()
    fun string(id: Int, vararg args: Any) = resources.getString(id, *args)
    val none = string(R.string.tracking_stand_missing)
    fun number(value: Number?) = value?.let { formatNumber(it.toDouble()) } ?: none
    fun heading(id: Int) = blocks.add(SummaryBlock.Heading(string(id)))
    fun table(headers: List<String>, rows: List<List<String>>, weights: List<Float>) =
        blocks.add(if (rows.isEmpty()) SummaryBlock.Text(string(R.string.tracking_summary_none)) else SummaryBlock.Table(headers, rows, weights))
    val weeks = ((summary.to.toEpochDay() - summary.from.toEpochDay() + 1) / 7).toInt()

    blocks += SummaryBlock.Title(string(R.string.tracking_summary_title))
    blocks += SummaryBlock.Text(
        string(R.string.tracking_summary_range, summary.from.numericDate(), summary.to.numericDate()) + " · " +
            resources.getQuantityString(R.plurals.tracking_summary_weeks, weeks, weeks),
        strong = true,
    )
    blocks += SummaryBlock.Text(string(R.string.tracking_summary_note, today.numericDate()), small = true)

    heading(R.string.tracking_summary_strongest)
    table(
        listOf(string(R.string.tracking_summary_symptom), string(R.string.tracking_summary_mean), string(R.string.tracking_summary_evenings)),
        summary.strongest.map { listOf(it.symptom.name, number(it.mean.value), it.mean.basis.toString()) },
        listOf(3f, 1f, 1f),
    )

    heading(R.string.tracking_summary_figures)
    val figures = summary.figures
    fun figure(section: Int, label: Int, figure: Figure, percent: Boolean = false): List<String> {
        val value = figure.value?.let { if (percent) string(R.string.tracking_week_percent, (it * 100).roundToInt()) else formatNumber(it) } ?: none
        return listOf(string(R.string.tracking_summary_figure_label, string(section), string(label)), value, if (figure.basis > 0) figure.basis.toString() else none)
    }
    table(
        listOf(string(R.string.tracking_summary_figure), string(R.string.tracking_summary_value), string(R.string.tracking_summary_basis)),
        listOf(
            figure(R.string.tracking_week_pulse, R.string.tracking_week_pulse_mean, figures.pulseMean),
            figure(R.string.tracking_week_pulse, R.string.tracking_week_pulse_min, figures.pulseMin),
            figure(R.string.tracking_week_pulse, R.string.tracking_week_pulse_max, figures.pulseMax),
            figure(R.string.tracking_week_pulse, R.string.tracking_week_pulse_high, figures.pulseHighDays),
            figure(R.string.tracking_week_stand, R.string.tracking_week_stand_count, figures.standTests),
            figure(R.string.tracking_week_stand, R.string.tracking_week_stand_max_rise, figures.standMaxRise),
            figure(R.string.tracking_week_stand, R.string.tracking_week_stand_rise, figures.standRiseTwice),
            figure(R.string.tracking_week_stand, R.string.tracking_week_stand_drop_early, figures.standDropEarly),
            figure(R.string.tracking_week_stand, R.string.tracking_week_stand_drop_late, figures.standDropLate),
            figure(R.string.tracking_week_events, R.string.tracking_week_events_count, figures.events),
            figure(R.string.tracking_week_events, R.string.tracking_week_events_breathless, figures.eventsBreathless),
            figure(R.string.tracking_week_events, R.string.tracking_week_events_skin, figures.eventsSkin),
            figure(R.string.tracking_week_events, R.string.tracking_week_events_fade, figures.skinFadeMinutes),
            figure(R.string.tracking_week_exertions, R.string.tracking_week_exertions_count, figures.exertions),
            figure(R.string.tracking_week_exertions, R.string.tracking_week_episodes_count, figures.episodes),
            figure(R.string.tracking_week_exertions, R.string.tracking_week_episodes_gap, figures.episodeGapHours),
            figure(R.string.tracking_week_day, R.string.tracking_week_fluid, figures.fluidLowDays),
            figure(R.string.tracking_week_day, R.string.tracking_week_sleep, figures.poorNights),
            figure(R.string.tracking_week_day, R.string.tracking_week_deviations, figures.deviations),
            figure(R.string.tracking_week_day, R.string.tracking_week_backfilled, figures.backfilledShare, percent = true),
            figure(R.string.tracking_week_garmin, R.string.tracking_week_minutes_above, figures.minutesAbovePerDay),
        ),
        listOf(4f, 1f, 1f),
    )

    heading(R.string.tracking_summary_stand)
    table(
        listOf(
            string(R.string.tracking_summary_date),
            string(R.string.tracking_summary_variant),
            string(R.string.tracking_summary_lying),
            string(R.string.tracking_summary_rise),
            string(R.string.tracking_summary_rises),
            string(R.string.tracking_summary_drop),
            string(R.string.tracking_summary_complaints),
            string(R.string.tracking_summary_aborted),
        ),
        summary.standTests.map { record -> standRow(resources, record, zone, none) },
        listOf(1.6f, 1.1f, 0.9f, 0.9f, 1f, 1.3f, 1f, 0.9f),
    )

    heading(R.string.tracking_summary_events)
    table(
        listOf(
            string(R.string.tracking_summary_date),
            string(R.string.tracking_summary_what),
            string(R.string.tracking_summary_pulses),
            string(R.string.tracking_summary_breathless),
            string(R.string.tracking_summary_skin),
        ),
        summary.events.map { event ->
            val skin = event.skinAreas.sorted().joinToString(", ") { resources.getString(areaName(it)) }.ifEmpty { none }
            listOf(
                event.at.atZone(zone).dateAndTime(),
                string(kindName(event.kind)),
                "${number(event.pulseBefore)} → ${number(event.pulseAfter)} / ${number(event.pulseFive)} / ${number(event.pulseTen)}",
                number(event.breathlessness),
                event.skinFadeMinutes?.let { string(R.string.tracking_summary_skin_fade, skin, it) } ?: skin,
            )
        },
        listOf(1.6f, 1.2f, 2f, 0.8f, 2f),
    )

    heading(R.string.tracking_summary_exertions)
    table(
        listOf(string(R.string.tracking_summary_date), string(R.string.tracking_summary_variant), string(R.string.tracking_summary_strength)),
        summary.exertions.map { exertion ->
            val day = exertion.date.numericDate()
            listOf(exertion.time?.let { "$day, ${it.clockTime()}" } ?: day, string(kindName(exertion.kind)), exertion.strength.toString())
        },
        listOf(2f, 2f, 1f),
    )

    heading(R.string.tracking_summary_episodes)
    if (summary.episodes.isEmpty()) blocks += SummaryBlock.Text(string(R.string.tracking_summary_none))
    summary.episodes.forEach { shown ->
        val episode = shown.episode
        val start = episode.startDate.numericDate() + (episode.startTime?.let { ", ${it.clockTime()}" } ?: "")
        val end = episode.endDate?.numericDate() ?: string(R.string.tracking_episode_running)
        blocks += SummaryBlock.Text(string(R.string.tracking_summary_episode, string(kindName(episode.kind)), start, end, number(episode.severity)))
        shown.triggers.forEach { trigger ->
            val gap = if (trigger.exertion.time == null || episode.startTime == null) R.string.tracking_episode_gap_approximate else R.string.tracking_episode_gap
            blocks += SummaryBlock.Text(
                string(R.string.tracking_summary_trigger, exertionText(resources, trigger.exertion), string(certaintyName(trigger.certainty)), string(gap, trigger.hoursAfter.toInt())),
                small = true,
            )
        }
    }

    heading(R.string.tracking_summary_medications)
    if (summary.medications.isEmpty()) blocks += SummaryBlock.Text(string(R.string.tracking_summary_none))
    summary.medications.forEach { (medication, changes) ->
        val about = listOf(medication.form, medication.purpose).filter { it.isNotBlank() }.joinToString(", ")
        blocks += SummaryBlock.Text(if (about.isEmpty()) medication.name else "${medication.name} ($about)", strong = true)
        val timeline = listOf(string(R.string.tracking_history_start, medication.start.numericDate(), medication.name)) +
            changes.map { string(R.string.tracking_history_dose, it.date.numericDate(), medication.name, it.from, it.to) } +
            listOfNotNull(medication.end?.let { string(R.string.tracking_history_end, it.numericDate(), medication.name) })
        timeline.forEach { blocks += SummaryBlock.Text(it, small = true) }
    }
    if (summary.medications.isNotEmpty()) blocks += SummaryBlock.Text(string(R.string.tracking_summary_medication_note), small = true)

    heading(R.string.tracking_summary_garmin)
    table(
        listOf(
            string(R.string.tracking_summary_garmin_value),
            string(R.string.tracking_summary_garmin_mean),
            string(R.string.tracking_summary_garmin_days),
            string(R.string.tracking_summary_tier),
        ),
        summary.garmin.map { figure ->
            val mean = figure.mean.value?.let { if (figure.value == GarminValue.Sleep) it / 60 else it }
            listOf(string(garminName(figure.value)), number(mean), figure.mean.basis.toString(), string(tierName(figure.tier)))
        },
        listOf(2.2f, 1.2f, 0.8f, 2f),
    )

    heading(R.string.tracking_summary_funcap)
    val funcap = summary.funcap
    if (funcap == null) {
        blocks += SummaryBlock.Text(string(R.string.tracking_summary_funcap_none))
    } else {
        val latest = funcap.latest
        val previous = funcap.previous
        blocks += SummaryBlock.Text(
            if (previous == null) {
                string(R.string.tracking_summary_funcap_of, latest.day.numericDate())
            } else {
                string(R.string.tracking_summary_funcap_of_before, latest.day.numericDate(), previous.day.numericDate())
            },
        )
        val totalRow = listOf(string(R.string.tracking_summary_funcap_total), number(latest.score.total), number(previous?.score?.total))
        val domainRows = Funcap.DOMAINS.map { domain ->
            listOf("${domain.code} ${domain.name}", number(latest.score.domains[domain.code]), number(previous?.score?.domains?.get(domain.code)))
        }
        table(
            listOf(string(R.string.tracking_summary_funcap_domain), latest.day.numericDate(), previous?.day?.numericDate() ?: none),
            listOf(totalRow) + domainRows,
            listOf(3f, 1f, 1f),
        )
        blocks += SummaryBlock.Text(string(R.string.tracking_summary_funcap_scale), small = true)
        if (funcap.monthMeans.isNotEmpty()) {
            blocks += SummaryBlock.Text(
                string(R.string.tracking_summary_funcap_month, FuncapSummary.monthFrom(latest).numericDate(), latest.day.numericDate()),
                strong = true,
            )
            table(
                listOf(string(R.string.tracking_summary_symptom), string(R.string.tracking_summary_mean), string(R.string.tracking_summary_evenings)),
                funcap.monthMeans.map { listOf(it.symptom.name, number(it.mean.value), it.mean.basis.toString()) },
                listOf(3f, 1f, 1f),
            )
        }
        blocks += SummaryBlock.Text(Funcap.SOURCE, small = true)
    }

    heading(R.string.tracking_summary_hints)
    if (summary.hints.isEmpty()) blocks += SummaryBlock.Text(string(R.string.tracking_summary_hints_none))
    summary.hints.forEach { hint ->
        blocks += SummaryBlock.Text(if (hint.urgent) "${hint.title} – ${string(R.string.tracking_hint_urgent)}" else hint.title, strong = true)
        blocks += SummaryBlock.Text(hint.pattern)
        hint.basis?.let { blocks += SummaryBlock.Text(it.describe(), small = true) }
        hint.support.forEach { blocks += SummaryBlock.Text(it, small = true) }
        blocks += SummaryBlock.Text(string(R.string.tracking_hint_specialty, hint.specialty))
        blocks += SummaryBlock.Text(string(R.string.tracking_hint_question, hint.question))
        hint.ownValues?.let { blocks += SummaryBlock.Text(it, small = true) }
        if (hint.sources.isNotEmpty()) blocks += SummaryBlock.Text(string(R.string.tracking_summary_sources, hint.sources.joinToString(", ") { it.code }), small = true)
    }
    blocks += SummaryBlock.Text(string(R.string.tracking_hint_no_diagnosis), small = true)

    val asked = Markdown.parse(questions).filter { Markdown.plainText(it).isNotBlank() }
    if (asked.isNotEmpty()) {
        heading(R.string.tracking_summary_questions)
        asked.forEach { block ->
            when (block) {
                is Block.Heading -> blocks += SummaryBlock.Text(Markdown.plainText(block), strong = true)
                is Block.Bullets -> block.items.forEach { blocks += SummaryBlock.Text("• " + Markdown.plainText(it.text)) }
                is Block.Numbered -> block.items.forEachIndexed { index, item -> blocks += SummaryBlock.Text("${block.start + index}. " + Markdown.plainText(item.text)) }
                else -> blocks += SummaryBlock.Text(Markdown.plainText(block))
            }
        }
    }

    if (summary.photos.isNotEmpty()) {
        heading(R.string.tracking_summary_photos)
        val events = summary.events.associateBy { it.id }
        summary.photos.forEach { photo ->
            val kind = events[photo.eventId]?.let { string(kindName(it.kind)) }.orEmpty()
            blocks += SummaryBlock.Photo(photo.jpeg, string(R.string.tracking_summary_photo, photo.takenAt.atZone(zone).dateAndTime(), kind))
        }
    }
    return blocks
}

/** A Stehtest as a row of its table. */
private fun standRow(resources: Resources, record: StandRecord, zone: ZoneId, none: String): List<String> {
    val result = record.result
    val variant = resources.getString(if (record.test.variant == StandVariant.Standing) R.string.tracking_stand_standing else R.string.tracking_stand_leaning)
    fun pulse(value: Int?) = value?.let { resources.getString(R.string.tracking_summary_per_minute, it.toString()) } ?: none
    val drop = when {
        !WeekRules.hasPressure(result) -> none
        WeekRules.droppedEarly(result) -> resources.getString(R.string.tracking_summary_drop_early)
        WeekRules.droppedLate(result) -> resources.getString(R.string.tracking_summary_drop_late)
        else -> resources.getString(R.string.tracking_no)
    }
    return listOf(
        record.test.startedAt.atZone(zone).dateAndTime(),
        variant,
        pulse(result.lyingPulse),
        pulse(result.maxRise),
        result.risesAtLeast30.size.toString(),
        drop,
        resources.getString(if (record.complaints) R.string.tracking_yes else R.string.tracking_no),
        record.test.abortedMinute?.let { resources.getString(R.string.tracking_summary_aborted_at, it) } ?: none,
    )
}

/** An exertion in a line, as the pages show it. */
private fun exertionText(resources: Resources, exertion: Exertion): String =
    resources.getString(R.string.tracking_exertion_line, exertion.date.numericDate(), resources.getString(kindName(exertion.kind)), exertion.strength)

private fun certaintyName(certainty: Certainty): Int = when (certainty) {
    Certainty.Sure -> R.string.tracking_episode_sure
    Certainty.Likely -> R.string.tracking_episode_likely
    Certainty.Unclear -> R.string.tracking_episode_unclear
}

private fun garminName(value: GarminValue): Int = when (value) {
    GarminValue.Steps -> R.string.tracking_summary_steps
    GarminValue.RestingHeartRate -> R.string.tracking_summary_garmin_pulse
    GarminValue.Sleep -> R.string.tracking_summary_sleep
    GarminValue.DeepSleep -> R.string.tracking_summary_deep_sleep
}

private fun tierName(tier: Tier): Int = when (tier) {
    Tier.A -> R.string.tracking_summary_tier_a
    Tier.B -> R.string.tracking_summary_tier_b
    Tier.C -> R.string.tracking_summary_tier_c
}
