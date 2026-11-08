package de.emilyshub.doku

/**
 * The numbers of the Doku's tests (SPEC.md §14), reckoned in hundredths so that nothing is lost to rounding: typed with
 * a comma ("12,5", "-10", a point taken as well), with no more decimals than the value allows, and shown the same way,
 * without trailing zeros ("12,5", "120").
 */
object TestNumber {
    /** The largest number, either way: 9.999.999,99. */
    const val MAX = 999_999_999L

    private val TYPED = Regex("""([-−])?(\d{1,7})(?:[,.](\d{1,2}))?""")

    /** The hundredths of [text] as typed, with decimals only if [decimals]; null if it is no number up to [MAX]. */
    fun parse(text: String, decimals: Boolean): Long? {
        val match = TYPED.matchEntire(text.trim()) ?: return null
        val (sign, whole, fraction) = match.destructured
        if (fraction.isNotEmpty() && !decimals) return null
        val amount = whole.toLong() * HUNDRED + fraction.padEnd(2, '0').toLong()
        return if (sign.isEmpty()) amount else -amount
    }

    /** "12,5", "120", "−10": as it reads, the minus as a minus sign. */
    fun format(hundredths: Long): String = typed(hundredths).replace('-', '−')

    /** As it is typed again in a field: "12,5", "120", "-10". */
    fun typed(hundredths: Long): String {
        val whole = Math.absExact(hundredths)
        val sign = if (hundredths < 0) "-" else ""
        val fraction = (whole % HUNDRED).toString().padStart(2, '0').trimEnd('0')
        return sign + (whole / HUNDRED) + if (fraction.isEmpty()) "" else ",$fraction"
    }

    /** As a fraction, for charts. */
    fun toDouble(hundredths: Long): Double = hundredths / HUNDRED.toDouble()

    private const val HUNDRED = 100L
}
