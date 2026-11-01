package de.emilyshub.routines

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.wishes.GoalRoutine
import de.emilyshub.wishes.WishDraft
import de.emilyshub.wishes.WishStatus
import de.emilyshub.wishes.Wishes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * How an occurrence's status may change (SPEC.md §2, §6), held up against seeded random walks of what can be done with
 * coupled routines: ticking off, skipping, undoing, the checklist, "Heute nicht", joint answers, pausing, deleting, and
 * time passing so that windows end. After every step, the rules that every status change must keep:
 *
 * - one skipped by another of its group was skipped by one that is done, of the same group, on the same day;
 * - a routine with a target makes its goal done exactly while it has reached that target.
 *
 * A failure names the seed and the steps that led to it, so that the walk can be replayed.
 */
@RunWith(AndroidJUnit4::class)
class OccurrenceLifecycleTest {

    @Test
    fun `every status change keeps coverings and goals as the rules have them`() {
        val failures = (0 until WALKS).mapNotNull { seed -> Walk(seed).run() }
        if (failures.isNotEmpty()) {
            val kinds = failures.groupBy { it.problem }.map { (problem, all) -> "${all.size}× $problem (seeds ${all.map { it.seed }.take(5)})" }
            fail("${failures.size} of $WALKS walks broke a rule:\n${kinds.joinToString("\n")}\n\nFirst:\n${failures.first().report}")
        }
    }

    /** The first rule a walk broke, and how it got there. */
    private class Failure(val seed: Int, val problem: String, val report: String)

    private class Broken(val problem: String, val report: String) : Exception(report)

    private class Walk(private val seed: Int) {
        private val random = Random(seed)
        private val database: HubDatabase = inMemoryDatabase()

        // Friday, 23 October 2026, 07:00 in Berlin.
        private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
        private val routines = Routines({ database }, clock, Dispatchers.Unconfined)
        private val wishes = Wishes({ database }, clock, Dispatchers.Unconfined)
        private val steps = mutableListOf<String>()

        private var goal = 0L
        private var goalRoutine = 0L
        private val group = mutableListOf<Long>()
        private var checklistRoutine = 0L

        /** Walks [STEPS] steps; the first rule broken on the way, if any. */
        fun run(): Failure? = runBlocking {
            try {
                setUp()
                check("set up")
                repeat(STEPS) {
                    val step = step()
                    steps += step
                    check(step)
                }
                null
            } catch (broken: Broken) {
                Failure(seed, broken.problem, broken.report)
            } finally {
                database.close()
            }
        }

        private suspend fun setUp() {
            goal = wishes.create(WishDraft("Tabletten nehmen"))
            goalRoutine = routines.create(draft("Morgens", "08:00", goal = goal, target = RoutineTarget.Streak(2)))
            group += goalRoutine
            group += routines.create(draft("Mittags", "12:00", coupledWith = setOf(goalRoutine)))
            checklistRoutine = routines.create(draft("Abends", "19:00", checklist = listOf("Wasser", "Tablette"), coupledWith = setOf(goalRoutine)))
            group += checklistRoutine
            routines.dueOn(routines.today()).first()
        }

        /** One thing done, at random; returns what it was. */
        private suspend fun step(): String {
            val occurrence = occurrences().filter { it.date >= routines.today().minusDays(1) }.randomOrNull(random)
                ?: return passTime()
            val id = occurrence.id
            return when (random.nextInt(12)) {
                0, 1 -> "markDone($id)".also { routines.markDone(id) }
                2 -> "skip($id)".also { routines.skip(id) }
                3, 4 -> "reopen($id)".also { routines.reopen(id) }
                5 -> {
                    val items = routines.checklist(checklistRoutine).map { it.id }
                    val item = items.randomOrNull(random) ?: return "no checklist"
                    val ticked = random.nextBoolean()
                    "tick($id, $item, $ticked)".also { routines.tick(id, item, ticked) }
                }
                6 -> "dropForToday($id)".also { routines.dropForToday(id) }
                7 -> "skipTogether($id)".also { routines.skipTogether(id) }
                8 -> "dropTogether($id)".also { routines.dropTogether(id) }
                9 -> pauseOrResume()
                10 -> deleteAndReplace()
                else -> passTime()
            }
        }

        /** Time passes, and what ran out is missed, as the reminders have it. */
        private suspend fun passTime(): String {
            val since = clock.now
            clock.now = since + Duration.ofMinutes(random.nextLong(30, 6 * 60))
            routines.markMissed(since)
            routines.dueOn(routines.today()).first()
            return "passTime(to ${clock.now})"
        }

        private suspend fun pauseOrResume(): String {
            // The goal's too, so that what a pause marks missed is held up against the goal.
            val routine = group.random(random)
            return when (routines.get(routine)?.status) {
                RoutineStatus.Active -> "pause($routine)".also { routines.pause(routine) }
                else -> "resume($routine)".also { routines.resume(routine) }
            }
        }

        /** Deletes one of the group other than the goal's, and couples a new one in its place. */
        private suspend fun deleteAndReplace(): String {
            val routine = group.filter { it != goalRoutine && it != checklistRoutine }.randomOrNull(random) ?: return "nothing to delete"
            routines.delete(routine)
            group -= routine
            val time = LocalTime.of(random.nextInt(9, 18), 0).toString()
            val replacement = routines.create(draft("Ersatz $routine", time, coupledWith = group.toSet()))
            group += replacement
            return "delete($routine), create($replacement at $time)"
        }

        private suspend fun check(step: String) {
            val occurrences = occurrences().associateBy { it.id }
            val couplings = database.backup().routines().associate { it.id to it.coupling }
            occurrences.values.filter { it.coveredBy != null }.forEach { covered ->
                val by = occurrences[covered.coveredBy]
                val problem = when {
                    covered.status != OccurrenceStatus.Skipped -> "covered but ${covered.status}"
                    by == null -> "covered by an occurrence that is gone"
                    by.status != OccurrenceStatus.Done -> "covered by one that is ${by.status}"
                    by.date != covered.date -> "covered by one of another day"
                    couplings[by.routineId] == null || couplings[by.routineId] != couplings[covered.routineId] ->
                        "covered by one of another group"
                    else -> null
                }
                problem?.let { fail(it, step, "occurrence $covered, covered by $by") }
            }
            val routine = database.routines().get(goalRoutine) ?: return
            val reached = GoalRoutine.of(RoutineWithOutcomes(routine, database.routines().outcomesOf(goalRoutine)))?.reached
            val done = wishes.get(goal)?.status == WishStatus.Done
            if (reached != done) fail("goal done: $done, target reached: $reached", step, "")
        }

        private fun occurrences(): List<Occurrence> = database.backup().occurrences()

        private fun fail(problem: String, step: String, detail: String): Nothing = throw Broken(
            problem = "${step.substringBefore('(')}: $problem",
            report = "Seed $seed, after $step: $problem. $detail\nSteps:\n${steps.joinToString("\n")}",
        )

        private fun draft(
            name: String,
            time: String,
            checklist: List<String> = emptyList(),
            coupledWith: Set<Long> = emptySet(),
            goal: Long? = null,
            target: RoutineTarget? = null,
        ) = RoutineDraft(
            name, description = "", listOf(LocalTime.parse(time)), Repetition.Daily,
            checklist = checklist.map { ChecklistDraftItem(text = it) }, coupledWith = coupledWith, goalId = goal, target = target,
        )
    }

    private companion object {
        const val WALKS = 200
        const val STEPS = 30
    }
}

