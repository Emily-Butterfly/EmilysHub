package de.emilyshub.ledger

/**
 * Amounts in euros and cents (SPEC.md §13), reckoned in whole cents so that nothing is lost to rounding: typed with a
 * comma, with thousands dots or without ("1.234,56", "1234,5", "12"), and shown as "1.234,56 €".
 */
object Money {
    /** The largest amount: 9.999.999,99 €. */
    const val MAX = 999_999_999L

    private val TYPED = Regex("""(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?""")

    /** The cents of [text] as typed, the euro sign and surrounding spaces allowed; null if it is no amount up to [MAX]. */
    fun parse(text: String): Long? {
        val match = TYPED.matchEntire(text.trim().removeSuffix("€").trim()) ?: return null
        val (euros, cents) = match.destructured
        val digits = euros.replace(".", "")
        if (digits.length > MAX_EURO_DIGITS) return null
        val amount = digits.toLong() * CENTS + cents.padEnd(2, '0').ifEmpty { "0" }.toLong()
        return amount.takeIf { it <= MAX }
    }

    /** "1.234,56 €", a minus before one below zero. */
    fun format(cents: Long): String {
        val whole = Math.absExact(cents)
        val euros = (whole / CENTS).toString().reversed().chunked(3).joinToString(".").reversed()
        val sign = if (cents < 0) "−" else ""
        return "$sign$euros,${(whole % CENTS).toString().padStart(2, '0')} €"
    }

    /** As it is typed again in the editor: "1234,56", or "12" without cents. */
    fun typed(cents: Long): String {
        val rest = cents % CENTS
        return if (rest == 0L) "${cents / CENTS}" else "${cents / CENTS},${rest.toString().padStart(2, '0')}"
    }

    private const val CENTS = 100L
    private const val MAX_EURO_DIGITS = 8
}
