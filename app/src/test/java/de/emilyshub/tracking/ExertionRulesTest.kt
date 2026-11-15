package de.emilyshub.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** The rules of exertions, suggestions, the pacing threshold, follow-up days and episodes (SPEC.md §17), as pure logic. */
class ExertionRulesTest {

    private val day = LocalDate.parse("2026-11-15")

    private fun evening(physical: Int? = null, cognitive: Int? = null, emotional: Int? = null, social: Int? = null) =
        EveningEntry(date = day, loadPhysical = physical, loadCognitive = cognitive, loadEmotional = emotional, loadSocial = social, recordedOn = day)

    private fun exertion(date: LocalDate, strength: Int, time: LocalTime? = null, id: Long = 0) =
        Exertion(id = id, date = date, time = time, kind = ExertionKind.Physical, strength = strength, origin = ExertionOrigin.Manual)

    @Test
    fun `a load of 2 or more in the evening is an exertion of its kind and strength`() {
        assertEquals(
            mapOf(ExertionKind.Physical to 2, ExertionKind.Emotional to 3, ExertionKind.Social to 2),
            ExertionRules.fromEvening(evening(physical = 2, cognitive = 1, emotional = 3, social = 2)),
        )
        assertEquals(emptyMap<ExertionKind, Int>(), ExertionRules.fromEvening(evening()))
    }

    @Test
    fun `a day is suggested above 150 percent of the mean of the 14 days before, those with data only`() {
        // Seven days with 1000 steps among the fourteen before, the others without data.
        val before = (1L..14L).filter { it % 2 == 0L }.associate { day.minusDays(it) to 1000L }
        assertTrue(ExertionRules.exceeds(before + (day to 1501L), day) != null)
        assertNull("150 percent exactly is not more", ExertionRules.exceeds(before + (day to 1500L), day))
        val check = ExertionRules.exceeds(before + (day to 3000L), day)!!
        assertEquals(1000.0, check.mean, 0.001)
        assertEquals(3000L, check.value)
    }

    @Test
    fun `fewer than seven days with data suggest nothing, nor a day without`() {
        val six = (1L..6L).associate { day.minusDays(it) to 1000L }
        assertNull(ExertionRules.exceeds(six + (day to 9000L), day))
        val seven = (1L..7L).associate { day.minusDays(it) to 1000L }
        assertNull(ExertionRules.exceeds(seven, day))
        assertTrue(ExertionRules.exceeds(seven + (day to 9000L), day) != null)
    }

    @Test
    fun `days older than fourteen do not count`() {
        val old = (15L..30L).associate { day.minusDays(it) to 100L }
        val recent = (1L..7L).associate { day.minusDays(it) to 1000L }
        assertNull(ExertionRules.exceeds(old + recent + (day to 1400L), day))
    }

    @Test
    fun `the pacing threshold is one's own, else the mean of the morning pulses plus 15`() {
        assertEquals(PacingThreshold(110, own = true), PacingThreshold.of(110, listOf(60, 70)))
        assertEquals(PacingThreshold(80, own = false), PacingThreshold.of(null, listOf(60, 70)))
        assertEquals("rounded", PacingThreshold(81, own = false), PacingThreshold.of(null, listOf(65, 66)))
        assertNull(PacingThreshold.of(null, emptyList()))
    }

    @Test
    fun `the hint on pulse-lowering medication shows only without one's own threshold`() {
        assertTrue(PacingThreshold.warnsOfPulseLowering(own = null, anyLowersPulse = true))
        assertFalse(PacingThreshold.warnsOfPulseLowering(own = 110, anyLowersPulse = true))
        assertFalse(PacingThreshold.warnsOfPulseLowering(own = null, anyLowersPulse = false))
    }

    @Test
    fun `the three evenings after an exertion of strength 2 to 3 ask after it, not after a lighter one`() {
        val strong = exertion(day.minusDays(1), 2, id = 1)
        val light = exertion(day.minusDays(1), 1, id = 2)
        val older = exertion(day.minusDays(3), 3, id = 3)
        val tooOld = exertion(day.minusDays(4), 3, id = 4)
        val same = exertion(day, 3, id = 5)

        assertEquals(listOf(1L, 3L), ExertionRules.followUps(day, listOf(strong, light, older, tooOld, same)).map { it.id })
    }

    @Test
    fun `no exertion after the start is a trigger to choose, an unknown time taken as noon`() {
        val morning = Episode(kind = EpisodeKind.Pem, startDate = day, startTime = LocalTime.of(9, 0))
        val list = listOf(
            exertion(day, 2, time = LocalTime.of(8, 0), id = 1),
            exertion(day, 2, time = LocalTime.of(18, 0), id = 2),
            exertion(day, 2, id = 3),
        )

        assertEquals(listOf(1L), ExertionRules.candidates(morning, list).map { it.id })
        assertEquals("start not known either", listOf(1L, 2L, 3L), ExertionRules.candidates(morning.copy(startTime = null), list).map { it.id })
    }

    @Test
    fun `the triggers to choose are the exertions of the three days before the start, and that day`() {
        val episode = Episode(kind = EpisodeKind.Pem, startDate = day, startTime = LocalTime.of(8, 0))
        val list = listOf(exertion(day, 2, time = LocalTime.of(7, 0), id = 1), exertion(day.minusDays(3), 2, id = 2), exertion(day.minusDays(4), 2, id = 3), exertion(day.plusDays(1), 2, id = 4))

        assertEquals(listOf(1L, 2L), ExertionRules.candidates(episode, list).map { it.id })
    }

    @Test
    fun `the gap from the exertion to the start counts over the day's end, an unknown time as noon`() {
        val episode = Episode(kind = EpisodeKind.Crash, startDate = day, startTime = LocalTime.of(8, 0))

        val gap = ExertionRules.hoursAfter(exertion(day.minusDays(2), 3, time = LocalTime.of(20, 0)), episode)
        assertEquals(36L, gap.hours)
        assertFalse(gap.approximate)

        val unknown = ExertionRules.hoursAfter(exertion(day.minusDays(1), 3), episode)
        assertEquals(20L, unknown.hours)
        assertTrue(unknown.approximate)
    }
}
