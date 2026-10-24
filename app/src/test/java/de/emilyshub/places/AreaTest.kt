package de.emilyshub.places

import org.junit.Assert.assertEquals
import org.junit.Test

/** GPS places (SPEC.md §4): how far apart two points are, and whether a position lies within a place's area. */
class AreaTest {

    private val berlin = Coordinates(52.520008, 13.404954)

    @Test
    fun `distances run along the earth's surface, a thousandth of a degree north being 111 m, and across the date line too`() {
        assertEquals(0.0, berlin.distanceTo(berlin), 0.0)
        assertEquals(111.2, berlin.distanceTo(Coordinates(52.521008, 13.404954)), 0.1)
        assertEquals(255_250.0, berlin.distanceTo(Coordinates(53.551086, 9.993682)), 1_000.0)
        assertEquals(111.2, Coordinates(0.0, 179.9995).distanceTo(Coordinates(0.0, -179.9995)), 0.1)
        assertEquals(111.2, Coordinates(89.9995, 0.0).distanceTo(Coordinates(89.9995, 180.0)), 0.1)
    }

    @Test
    fun `a position lies within an area up to its radius from the center, the border included`() {
        val gym = Area(berlin, radius = 150)

        assertEquals(true, gym.includes(at(metersNorth = 0.0)))
        assertEquals(true, gym.includes(at(metersNorth = 140.0)))
        assertEquals(true, gym.includes(at(metersNorth = 149.9)))
        assertEquals(false, gym.includes(at(metersNorth = 160.0)))
        assertEquals(false, gym.includes(at(metersNorth = -2_000.0)))
    }

    @Test
    fun `Android's give or take counts towards the area, and a position less precise than the area is large tells nothing`() {
        val gym = Area(berlin, radius = 150)

        assertEquals(false, gym.includes(at(metersNorth = 180.0, accuracy = 20.0)))
        assertEquals(true, gym.includes(at(metersNorth = 160.0, accuracy = 20.0)))
        assertEquals(false, gym.includes(at(metersNorth = 400.0, accuracy = 150.0)))
        assertEquals(null, gym.includes(at(metersNorth = 400.0, accuracy = 151.0)))
        assertEquals(null, gym.includes(at(metersNorth = 1_800.0, accuracy = 2_000.0)))
    }

    @Test
    fun `degrees are typed with a point or a comma, a minus for south and west, and are shown with a comma`() {
        assertEquals(52.520008, Degrees.parse("52,520008")!!, 0.0)
        assertEquals(52.520008, Degrees.parse(" 52.520008 ")!!, 0.0)
        assertEquals(-13.4, Degrees.parse("-13,4")!!, 0.0)
        assertEquals(-13.4, Degrees.parse("−13.4")!!, 0.0)
        assertEquals(13.0, Degrees.parse("+13")!!, 0.0)
        listOf("", " ", "abc", "1e3", "52,5,3", "52.", ",5", "NaN", "Infinity", "--1").forEach {
            assertEquals(it, null, Degrees.parse(it))
        }

        assertEquals("52,520008", Degrees.format(52.520008))
        assertEquals("13,4", Degrees.format(13.4))
        assertEquals("-0,5", Degrees.format(-0.5))
        assertEquals("90", Degrees.format(90.0))
        assertEquals("52,123457", Degrees.format(52.1234567))
        assertEquals("0", Degrees.format(-0.0000001))
    }

    /** A position [metersNorth] of Berlin's center, give or take [accuracy] meters. */
    private fun at(metersNorth: Double, accuracy: Double = 0.0) =
        Position(Coordinates(berlin.latitude + metersNorth / METERS_PER_DEGREE, berlin.longitude), accuracy)

    private companion object {
        const val METERS_PER_DEGREE = 111_195.08
    }
}
