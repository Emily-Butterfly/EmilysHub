package de.emilyshub.routines

import de.emilyshub.data.InputText
import java.io.Serializable
import java.time.Instant
import java.time.LocalTime

enum class DraftProblem {
    NameMissing,
    NameTooLong,
    DescriptionTooLong,
    TimesMissing,
    TooManyTimes,
    WeekdaysMissing,
    IntervalOutOfRange,
    FollowUpCountOutOfRange,
    FollowUpIntervalOutOfRange,
    WindowOutOfRange,
    ChecklistTooLong,
    ChecklistItemTooLong,
    TargetOutOfRange,
}

/**
 * A routine as typed into the editor, before it is checked. The checks apply to the [cleaned]
 * draft, and saving refuses a draft with [problems] (SPEC.md §9: strict input checks).
 * Serializable, so that the editor keeps it across rotation and the app lock.
 */
data class RoutineDraft(
    val name: String,
    val description: String,
    val times: List<LocalTime>,
    val repetition: Repetition,
    val followUps: FollowUps = FollowUps(),
    val fullScreen: Boolean = false,
    val window: TimeWindow = TimeWindow(),
    val priority: Priority = Priority.Normal,
    /** Its sub-items ("Unterpunkte") in their order, ticked off one by one on each occurrence. */
    val checklist: List<ChecklistDraftItem> = emptyList(),
    /** Its tags; one deleted meanwhile is left out on saving. */
    val tagIds: Set<Long> = emptySet(),
    /** The places it pops up at, any of them; none: everywhere. One deleted meanwhile is left out on saving. */
    val placeIds: Set<Long> = emptySet(),
    /** The goal it is part of (SPEC.md §6), if any; one deleted meanwhile is left out on saving. */
    val goalId: Long? = null,
    /** What it aims at for its goal, if anything; left out without a goal. */
    val target: RoutineTarget? = null,
) : Serializable {

    val problems: Set<DraftProblem>
        get() = with(cleaned()) {
            buildSet {
                if (!InputText.isVisible(name)) add(DraftProblem.NameMissing)
                if (InputText.lengthOf(name) > NAME_MAX) add(DraftProblem.NameTooLong)
                if (InputText.lengthOf(description) > DESCRIPTION_MAX) add(DraftProblem.DescriptionTooLong)
                if (times.isEmpty()) add(DraftProblem.TimesMissing)
                if (times.size > TIMES_MAX) add(DraftProblem.TooManyTimes)
                when (val repetition = repetition) {
                    is Repetition.Weekdays -> if (repetition.days.isEmpty()) add(DraftProblem.WeekdaysMissing)
                    is Repetition.EveryNDays -> if (repetition.days !in INTERVAL_DAYS) add(DraftProblem.IntervalOutOfRange)
                    Repetition.Daily, is Repetition.Once -> Unit
                }
                if (followUps.count !in FollowUps.COUNTS) add(DraftProblem.FollowUpCountOutOfRange)
                if (followUps.everyMinutes !in FollowUps.MINUTES) add(DraftProblem.FollowUpIntervalOutOfRange)
                if (window.hours !in TimeWindow.HOURS) add(DraftProblem.WindowOutOfRange)
                if (checklist.size > CHECKLIST_MAX) add(DraftProblem.ChecklistTooLong)
                if (checklist.any { InputText.lengthOf(it.text) > CHECKLIST_ITEM_MAX }) add(DraftProblem.ChecklistItemTooLong)
                target?.let { if (it.count !in RoutineTarget.COUNTS) add(DraftProblem.TargetOutOfRange) }
            }
        }

    /**
     * Without surrounding spaces; the name and each checklist item on a single line, empty items left out; the
     * times in the order of the day, each once; without follow-ups, the default distance between them, and until
     * midnight, the default hours: neither is shown then. Without a goal, it aims at nothing.
     */
    fun cleaned() = copy(
        name = InputText.singleLine(name),
        description = InputText.multiLine(description).trimStart(),
        times = times.distinct().sorted(),
        followUps = if (followUps.count == 0) FollowUps(count = 0) else followUps,
        window = if (window.untilMidnight) TimeWindow(untilMidnight = true) else window,
        checklist = checklist.map(ChecklistDraftItem::cleaned).filter { InputText.isVisible(it.text) },
        target = target.takeIf { goalId != null },
    )

    /** The routine this draft makes, [id] if it is saved already, with its times planned [from] then on. */
    fun toRoutine(id: Long = 0, plannedFrom: Instant, status: RoutineStatus = RoutineStatus.Active) =
        Routine(id, name, description, times, repetition, plannedFrom, followUps, fullScreen, window, priority, status, goalId, target)

    companion object {
        const val NAME_MAX = 80
        const val DESCRIPTION_MAX = 1000

        /** One every hour. */
        const val TIMES_MAX = 24

        /** Every day is "daily"; a year is the longest gap. */
        val INTERVAL_DAYS = 2..365

        const val CHECKLIST_MAX = Checklists.ITEMS_MAX
        const val CHECKLIST_ITEM_MAX = Checklists.ITEM_LENGTH_MAX
    }
}

/** An item of a checklist as typed into the editor; [id] if it is saved already, so that it keeps its ticks. */
data class ChecklistDraftItem(val id: Long = 0, val text: String) : Serializable {
    /** On a single line, without surrounding spaces. */
    fun cleaned() = copy(text = InputText.singleLine(text))
}

/** The routine with its [checklist], tags and places as the editor starts out with it. */
fun Routine.toDraft(checklist: List<ChecklistItem>, tagIds: Set<Long>, placeIds: Set<Long>) = RoutineDraft(
    name, description, times, repetition, followUps, fullScreen, window, priority,
    checklist.map { ChecklistDraftItem(it.id, it.text) }, tagIds, placeIds, goalId, target,
)
