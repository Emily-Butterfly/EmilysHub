package de.emilyshub.ledger

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.emilyshub.data.InputText
import java.io.Serializable
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** What a notice is counted in (SPEC.md §13): days, weeks or months, each with the most it may be. */
enum class NoticeUnit(val stored: String, val range: IntRange) {
    Days("days", 0..365),
    Weeks("weeks", 0..52),
    Months("months", 0..24),
}

/** A notice of [count] [unit]s before the end of a term (SPEC.md §13). */
data class Notice(val count: Int, val unit: NoticeUnit) : Serializable {
    /**
     * The last day to cancel a term ending on [end], whose terms end on [endDay] of the month: so long before it. In
     * months, on that day of the month, or the last of a shorter month; [LAST_DAY] for the last of every month.
     */
    fun before(end: LocalDate, endDay: Int = endDayOf(end)): LocalDate = when (unit) {
        NoticeUnit.Days -> end.minusDays(count.toLong())
        NoticeUnit.Weeks -> end.minusWeeks(count.toLong())
        NoticeUnit.Months -> onDay(YearMonth.from(end).minusMonths(count.toLong()), endDay)
    }
}

/** The day of the month that terms ending on [date] end on: its own, or [LAST_DAY] if it is the last of its month. */
fun endDayOf(date: LocalDate): Int = if (date.dayOfMonth == date.lengthOfMonth()) LAST_DAY else date.dayOfMonth

/** The day of the month standing for the last of every month. */
const val LAST_DAY = 31

/** [day] of [month], or its last if it is shorter. */
private fun onDay(month: YearMonth, day: Int): LocalDate = month.atDay(minOf(day, month.lengthOfMonth()))

/**
 * Until when an Abo is to be cancelled (SPEC.md §13): by a single date ([OnDate]), or reckoned from the end of its term
 * with its notice and the months it renews by ([Term]).
 */
sealed interface Deadline : Serializable {
    /** Cancelled by [cancelBy]; gone by, it waits for a new date. */
    data class OnDate(val cancelBy: LocalDate) : Deadline

    /**
     * A term ending on [termEnd], to be cancelled [notice] before, and renewed by [renewalMonths] if not. Its terms end on
     * [endDay] of the month, as the first one did, so that one ending on the 30th ends on the 30th again after February;
     * [LAST_DAY] for the last of every month.
     */
    data class Term(val termEnd: LocalDate, val notice: Notice, val renewalMonths: Int, val endDay: Int = endDayOf(termEnd)) : Deadline {
        /** The last day to cancel it. */
        val lastDay: LocalDate get() = notice.before(termEnd, endDay)

        /** The term after it, ending on its day of the month again. */
        fun next(): Term = copy(termEnd = onDay(YearMonth.from(termEnd).plusMonths(renewalMonths.toLong()), endDay))

        /** The term that holds on [today]: this one, or, its last day gone by, the first after it whose has not. */
        fun current(today: LocalDate): Term {
            require(renewalMonths >= 1) { "A term renews by a month at least" }
            var term = this
            while (term.lastDay.isBefore(today)) term = term.next()
            return term
        }
    }
}

/** How an Abo stands on a day (SPEC.md §13). */
sealed interface SubscriptionStatus {
    /** To be cancelled by [lastDay], [daysLeft] from the day on. */
    data class Due(val lastDay: LocalDate, val daysLeft: Long) : SubscriptionStatus

    /** Its single date [cancelBy] went by uncancelled: "Frist verstrichen", until a new one is set. */
    data class Lapsed(val cancelBy: LocalDate) : SubscriptionStatus

    /** Let run on past its single date [cancelBy] ("Läuft weiter"): it waits for a new one. */
    data class RunsOn(val cancelBy: LocalDate) : SubscriptionStatus

    /** Cancelled, ending on [endsOn]. */
    data class Cancelled(val endsOn: LocalDate) : SubscriptionStatus
}

/**
 * An Abo of the household book (SPEC.md §13): a contract by [name] to cancel before it renews, with an amount every
 * [interval] if known ([amountCents]), a [note], and its deadline: a single date [cancelBy], or the end of its term
 * [termEnd], on [termDay] of the month as its first did, with a notice of [noticeCount] [noticeUnit]s and a renewal by
 * [renewalMonths]. The reminder begins [leadDays]
 * before its last day (#80). Linked to a fixed booking ([recurringId]), at most one Abo to each, its amount and interval
 * are that booking's; the booking deleted, it keeps them as its own. [runsOn]: its single date was let run on, and it
 * waits for a new one. Cancelled, it ends on [endsOn].
 */
@Entity(
    tableName = "subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = RecurringBooking::class, parentColumns = ["id"], childColumns = ["recurring_id"], onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["recurring_id"], unique = true)],
)
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long? = null,
    val interval: BookingInterval? = null,
    @ColumnInfo(name = "recurring_id") val recurringId: Long? = null,
    val note: String = "",
    @ColumnInfo(name = "cancel_by") val cancelBy: LocalDate? = null,
    @ColumnInfo(name = "term_end") val termEnd: LocalDate? = null,
    @ColumnInfo(name = "term_day") val termDay: Int? = null,
    @ColumnInfo(name = "notice_count") val noticeCount: Int? = null,
    @ColumnInfo(name = "notice_unit") val noticeUnit: NoticeUnit? = null,
    @ColumnInfo(name = "renewal_months") val renewalMonths: Int? = null,
    @ColumnInfo(name = "lead_days") val leadDays: Int = DEFAULT_LEAD,
    @ColumnInfo(name = "runs_on", defaultValue = "0") val runsOn: Boolean = false,
    @ColumnInfo(name = "ends_on") val endsOn: LocalDate? = null,
) {
    /** Its deadline, from what is stored: reckoned if it has the end of a term, else by its single date. */
    val deadline: Deadline
        get() = if (termEnd != null) {
            Deadline.Term(
                termEnd, Notice(checkNotNull(noticeCount), checkNotNull(noticeUnit)), checkNotNull(renewalMonths), termDay ?: endDayOf(termEnd),
            )
        } else {
            Deadline.OnDate(checkNotNull(cancelBy))
        }

    /** How it stands on [today]. */
    fun statusOn(today: LocalDate): SubscriptionStatus {
        endsOn?.let { return SubscriptionStatus.Cancelled(it) }
        return when (val deadline = deadline) {
            is Deadline.OnDate -> when {
                runsOn -> SubscriptionStatus.RunsOn(deadline.cancelBy)
                deadline.cancelBy.isBefore(today) -> SubscriptionStatus.Lapsed(deadline.cancelBy)
                else -> SubscriptionStatus.Due(deadline.cancelBy, ChronoUnit.DAYS.between(today, deadline.cancelBy))
            }
            is Deadline.Term -> {
                val lastDay = deadline.current(today).lastDay
                SubscriptionStatus.Due(lastDay, ChronoUnit.DAYS.between(today, lastDay))
            }
        }
    }

    /** With [deadline] in place of the one it has. */
    fun with(deadline: Deadline): Subscription = when (deadline) {
        is Deadline.OnDate -> copy(
            cancelBy = deadline.cancelBy, termEnd = null, termDay = null, noticeCount = null, noticeUnit = null, renewalMonths = null,
        )
        is Deadline.Term -> copy(
            cancelBy = null, termEnd = deadline.termEnd, termDay = deadline.endDay, noticeCount = deadline.notice.count,
            noticeUnit = deadline.notice.unit, renewalMonths = deadline.renewalMonths,
        )
    }

    companion object {
        /** The days before the last day the reminder begins, unless set otherwise. */
        const val DEFAULT_LEAD = 14

        /** An Abo by [name] with [deadline] and nothing else. */
        fun of(id: Long, name: String, deadline: Deadline): Subscription = Subscription(id = id, name = name).with(deadline)
    }
}

/** What an Abo costs every [interval]: [cents]. */
data class SubscriptionCost(val cents: Long, val interval: BookingInterval)

/** What [costs] come to a month, each by the months of its interval, rounded once at the end, half up. */
fun perMonth(costs: List<SubscriptionCost>): Long {
    val months = BookingInterval.Yearly.months.toLong()
    // In twelfths of a cent, which every interval divides.
    val twelfths = costs.sumOf { it.cents * (months / it.interval.months) }
    return (twelfths * 2 + months) / (2 * months)
}

/** What keeps a [SubscriptionDraft] from being saved, each shown at its field in the editor. */
enum class SubscriptionProblem {
    NameMissing,
    NameTooLong,
    AmountInvalid,
    AmountNotAboveZero,
    NoteTooLong,
    CancelByMissing,
    TermEndMissing,
    NoticeOutOfRange,
    RenewalOutOfRange,
    LeadOutOfRange,

    /** Its fixed booking has an Abo already: only when saving, by [Subscriptions.save]. */
    RecurringTaken,
}

/**
 * An Abo as typed into its editor (SPEC.md §13): its [name]; an [amount] with a comma every [interval], none if left
 * empty, or those of the fixed booking [recurringId] it is linked to; a [note]; its deadline, [reckoned] from the end of
 * its term [termEnd] with a [notice] in [noticeUnit]s and a [renewal] in months, or by the single date [cancelBy]; and
 * the [lead] in days. [termDay] is the day of the month its terms end on, kept from the one saved while its end is not
 * chosen anew; none to take it from [termEnd]. Serializable, so that the editor keeps it.
 */
data class SubscriptionDraft(
    val name: String,
    val amount: String = "",
    val interval: BookingInterval = BookingInterval.Monthly,
    val recurringId: Long? = null,
    val note: String = "",
    val reckoned: Boolean = false,
    val cancelBy: LocalDate? = null,
    val termEnd: LocalDate? = null,
    val termDay: Int? = null,
    val notice: String = "1",
    val noticeUnit: NoticeUnit = NoticeUnit.Months,
    val renewal: String = "12",
    val lead: String = Subscription.DEFAULT_LEAD.toString(),
) : Serializable {

    /** Its name on a single line, as it is saved. */
    val cleanedName: String get() = InputText.singleLine(name)

    /** Its note as it is saved. */
    val cleanedNote: String get() = InputText.multiLine(note)

    /** Its own amount in cents: none if left empty, or linked. */
    val cents: Long? get() = if (recurringId == null && amount.isNotBlank()) Money.parse(amount) else null

    private val noticeCount: Int? get() = notice.toIntOrNull()?.takeIf { it in noticeUnit.range }
    private val renewalMonths: Int? get() = renewal.toIntOrNull()?.takeIf { it in RENEWAL }
    private val leadDays: Int? get() = lead.toIntOrNull()?.takeIf { it in LEAD }

    /** Its deadline as typed; none while it has a problem. */
    val deadline: Deadline?
        get() = if (reckoned) {
            val end = termEnd ?: return null
            Deadline.Term(end, Notice(noticeCount ?: return null, noticeUnit), renewalMonths ?: return null, termDay ?: endDayOf(end))
        } else {
            cancelBy?.let(Deadline::OnDate)
        }

    /** What keeps it from being saved; none if it can be. */
    val problems: Set<SubscriptionProblem>
        get() = buildSet {
            when {
                !InputText.isVisible(cleanedName) -> add(SubscriptionProblem.NameMissing)
                InputText.lengthOf(cleanedName) > NAME_MAX -> add(SubscriptionProblem.NameTooLong)
            }
            if (recurringId == null && amount.isNotBlank()) {
                val parsed = Money.parse(amount)
                when {
                    parsed == null -> add(SubscriptionProblem.AmountInvalid)
                    parsed <= 0 -> add(SubscriptionProblem.AmountNotAboveZero)
                }
            }
            if (InputText.lengthOf(cleanedNote) > NOTE_MAX) add(SubscriptionProblem.NoteTooLong)
            if (reckoned) {
                if (termEnd == null) add(SubscriptionProblem.TermEndMissing)
                if (noticeCount == null) add(SubscriptionProblem.NoticeOutOfRange)
                if (renewalMonths == null) add(SubscriptionProblem.RenewalOutOfRange)
            } else if (cancelBy == null) {
                add(SubscriptionProblem.CancelByMissing)
            }
            if (leadDays == null) add(SubscriptionProblem.LeadOutOfRange)
        }

    /** The Abo it saves as, under [id]; only without [problems]. Linked, its amount and interval come from the booking. */
    fun toSubscription(id: Long = 0): Subscription {
        require(problems.isEmpty()) { "Invalid Abo: $problems" }
        val own = cents
        return Subscription(
            id = id, name = cleanedName, amountCents = own, interval = interval.takeIf { own != null }, recurringId = recurringId,
            note = cleanedNote, leadDays = checkNotNull(leadDays),
        ).with(checkNotNull(deadline))
    }

    companion object {
        const val NAME_MAX = 80
        const val NOTE_MAX = 1000

        /** How many months a term may renew by. */
        val RENEWAL = 1..120

        /** How many days before the last day the reminder may begin. */
        val LEAD = 0..365
    }
}
