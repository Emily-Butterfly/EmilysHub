package de.emilyshub.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The evaluation of a Stehtest (SPEC.md §17, "Stehtest"), as pure logic, with made-up values only. */
class StandTestEvaluationTest {

    private val started = Instant.parse("2026-11-15T09:00:00Z")
    private val standing = Instant.parse("2026-11-15T09:07:00Z")

    private fun test(
        variant: StandVariant,
        lyingPulse: Int? = 60,
        lyingSystolic: Int? = 120,
        lyingDiastolic: Int? = 80,
        aborted: Boolean = false,
        abortedMinute: Int? = null,
        standingAt: Instant? = standing,
    ) = StandTest(
        id = 1,
        variant = variant,
        startedAt = started,
        standingAt = standingAt,
        lyingPulse = lyingPulse,
        lyingSystolic = lyingSystolic,
        lyingDiastolic = lyingDiastolic,
        abortCause = if (aborted) AbortCause.Pressed else null,
        abortedMinute = abortedMinute,
        finished = !aborted,
    )

    private fun point(minute: Int, pulse: Int?, systolic: Int? = null, diastolic: Int? = null) =
        StandPoint(testId = 1, minute = minute, pulse = pulse, systolic = systolic, diastolic = diastolic)

    private fun beat(secondsAfterStanding: Long, bpm: Int) = Beat(standing.plusSeconds(secondsAfterStanding), bpm)

    @Test
    fun `standing, the largest rise over lying and the measurements rising by 30 or more`() {
        val result = StandTestEvaluation.evaluate(
            test(StandVariant.Standing),
            listOf(point(1, 85), point(3, 90), point(5, 89), point(8, 95), point(10, 80)),
            curve = emptyList(),
        )

        assertEquals(35, result.maxRise)
        assertEquals("29 is below, 30 counts", listOf(3, 8), result.risesAtLeast30)
        assertEquals(listOf(1, 3, 5, 8, 10), result.points.map { it.minute })
        assertNull("only leaning has the mean of the last three minutes", result.lastThreeMean)
        assertTrue(result.complete)
    }

    @Test
    fun `leaning, one measurement a minute, and the mean of the last three over lying`() {
        val pulses = listOf(70, 72, 75, 78, 80, 82, 85, 88, 90, 95)
        val result = StandTestEvaluation.evaluate(
            test(StandVariant.Leaning),
            pulses.mapIndexed { index, pulse -> point(index + 1, pulse) },
            curve = emptyList(),
        )

        assertEquals((1..10).toList(), result.points.map { it.minute })
        assertEquals(91.0, result.lastThreeMean!!, 0.001)
        assertEquals(31.0, result.lastThreeRise!!, 0.001)
        assertEquals(35, result.maxRise)
        assertEquals(listOf(9, 10), result.risesAtLeast30)
    }

    @Test
    fun `leaning, without all of the last three minutes there is no mean`() {
        val result = StandTestEvaluation.evaluate(
            test(StandVariant.Leaning),
            (1..9).map { point(it, 80) } + point(10, null),
            curve = emptyList(),
        )

        assertNull(result.lastThreeMean)
        assertNull(result.lastThreeRise)
    }

    @Test
    fun `the largest drop of blood pressure within three minutes and after, apart`() {
        val result = StandTestEvaluation.evaluate(
            test(StandVariant.Standing),
            listOf(
                point(1, 70, systolic = 105, diastolic = 75),
                point(3, 72, systolic = 100, diastolic = 70),
                point(5, 74, systolic = 95, diastolic = 68),
                point(8, 75, systolic = 89, diastolic = 72),
                point(10, 75, systolic = 110, diastolic = 79),
            ),
            curve = emptyList(),
        )

        assertEquals(20, result.systolicDropEarly)
        assertEquals(10, result.diastolicDropEarly)
        assertEquals(31, result.systolicDropLate)
        assertEquals(12, result.diastolicDropLate)
        assertEquals(listOf(8), result.systolicBelow90)
    }

    @Test
    fun `a rising blood pressure is no drop`() {
        val result = StandTestEvaluation.evaluate(
            test(StandVariant.Standing),
            listOf(point(1, 70, systolic = 130, diastolic = 85)),
            curve = emptyList(),
        )

        assertEquals(0, result.systolicDropEarly)
        assertEquals(0, result.diastolicDropEarly)
        assertNull("no pressure measured after three minutes", result.systolicDropLate)
    }

    @Test
    fun `without blood pressure there are no drops, and without it lying none either`() {
        val noPressure = StandTestEvaluation.evaluate(test(StandVariant.Standing), listOf(point(1, 80), point(3, 82)), curve = emptyList())
        assertNull(noPressure.systolicDropEarly)
        assertNull(noPressure.diastolicDropEarly)
        assertEquals(emptyList<Int>(), noPressure.systolicBelow90)

        val notLying = StandTestEvaluation.evaluate(
            test(StandVariant.Standing, lyingSystolic = null, lyingDiastolic = null),
            listOf(point(1, 80, systolic = 85, diastolic = 60)),
            curve = emptyList(),
        )
        assertNull(notLying.systolicDropEarly)
        assertEquals("below 90 needs no lying value", listOf(1), notLying.systolicBelow90)
    }

    @Test
    fun `an aborted test is evaluated up to the minute it was aborted`() {
        val result = StandTestEvaluation.evaluate(
            test(StandVariant.Standing, aborted = true, abortedMinute = 4),
            listOf(point(1, 95), point(3, 100)),
            curve = listOf(beat(300, 120)),
        )

        assertEquals("5 lies after the abort, not filled", listOf(1, 3), result.points.map { it.minute })
        assertEquals(40, result.maxRise)
        assertFalse(result.complete)
    }

    @Test
    fun `aborted lying down, nothing standing is evaluated`() {
        val result = StandTestEvaluation.evaluate(
            test(StandVariant.Leaning, aborted = true, standingAt = null, lyingPulse = null),
            emptyList(),
            curve = emptyList(),
        )

        assertEquals(emptyList<StandPointResult>(), result.points)
        assertNull(result.maxRise)
        assertFalse(result.complete)
    }

    @Test
    fun `a missing pulse is filled from the nearest heart rate of the curve, and marked`() {
        val result = StandTestEvaluation.evaluate(
            test(StandVariant.Standing),
            listOf(point(1, 80), point(3, null, systolic = 110), point(8, 85), point(10, 86)),
            curve = listOf(beat(170, 88), beat(185, 91), beat(200, 95), beat(300, 93)),
        )

        val third = result.points.single { it.minute == 3 }
        assertEquals("the nearest to 3:00", 91, third.pulse)
        assertTrue(third.filled)
        assertEquals("kept", 110, third.systolic)
        val fifth = result.points.single { it.minute == 5 }
        assertEquals("a minute without a row is filled as well", 93, fifth.pulse)
        assertTrue(fifth.filled)
        assertFalse(result.points.single { it.minute == 1 }.filled)
        assertEquals(listOf(3, 5), result.risesAtLeast30)
    }

    @Test
    fun `a heart rate too far from the minute fills nothing`() {
        val result = StandTestEvaluation.evaluate(
            test(StandVariant.Standing),
            listOf(point(1, 80)),
            curve = listOf(beat(180 + 31, 99), beat(180 - 31, 99)),
        )

        val third = result.points.single { it.minute == 3 }
        assertNull(third.pulse)
        assertFalse(third.filled)
    }

    @Test
    fun `a missing lying pulse is filled from the minute before standing up`() {
        val result = StandTestEvaluation.evaluate(
            test(StandVariant.Standing, lyingPulse = null),
            listOf(point(1, 90)),
            curve = listOf(beat(-90, 50), beat(-20, 58), beat(30, 70)),
        )

        assertEquals(58, result.lyingPulse)
        assertTrue(result.lyingFilled)
        assertEquals(32, result.maxRise)
    }

    @Test
    fun `without a lying pulse there is no rise`() {
        val result = StandTestEvaluation.evaluate(test(StandVariant.Standing, lyingPulse = null), listOf(point(1, 120)), curve = emptyList())

        assertNull(result.maxRise)
        assertEquals(emptyList<Int>(), result.risesAtLeast30)
        assertNull(result.points.single { it.minute == 1 }.rise)
    }

    @Test
    fun `the leaning mean counts a minute filled from the curve`() {
        val result = StandTestEvaluation.evaluate(
            test(StandVariant.Leaning),
            (1..7).map { point(it, 80) } + point(8, 90) + point(10, 96),
            curve = listOf(beat(540, 93)),
        )

        assertEquals(93.0, result.lastThreeMean!!, 0.001)
        assertTrue(result.points.single { it.minute == 9 }.filled)
    }

    @Test
    fun `the checklist must be ticked in full`() {
        assertFalse(StandChecklist.complete(ChecklistItem.entries.toSet() - ChecklistItem.SecondPerson))
        assertTrue(StandChecklist.complete(ChecklistItem.entries.toSet()))
    }

    @Test
    fun `the next point due is the first minute not yet measured or skipped`() {
        assertEquals(1, StandVariant.Standing.nextMinute(emptySet()))
        assertEquals(5, StandVariant.Standing.nextMinute(setOf(1, 3)))
        assertNull(StandVariant.Standing.nextMinute(setOf(1, 3, 5, 8, 10)))
        assertEquals(10, StandVariant.Leaning.nextMinute((1..9).toSet()))
    }

    @Test
    fun `a point measured before the minute it was aborted at is kept in the evaluation`() {
        val result = StandTestEvaluation.evaluate(
            test(StandVariant.Standing, aborted = true, abortedMinute = 4),
            listOf(point(1, 80), point(3, 85), point(5, 99)),
            curve = emptyList(),
        )

        assertEquals(listOf(1, 3, 5), result.points.map { it.minute })
        assertEquals(39, result.maxRise)
    }

    @Test
    fun `systolic 90 is not below 90`() {
        val result = StandTestEvaluation.evaluate(
            test(StandVariant.Standing),
            listOf(point(1, 80, systolic = 90), point(3, 80, systolic = 89)),
            curve = emptyList(),
        )

        assertEquals(listOf(3), result.systolicBelow90)
    }
}
