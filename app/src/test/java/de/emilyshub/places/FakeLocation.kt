package de.emilyshub.places

/** The position the test says Android tells; none to begin with, as while Android cannot tell. */
class FakeLocation : CurrentLocation {
    var position: Position? = null

    override suspend fun position(): Position? = position
}
