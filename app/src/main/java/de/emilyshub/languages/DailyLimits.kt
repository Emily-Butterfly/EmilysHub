package de.emilyshub.languages

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.emilyshub.data.InputText
import java.time.LocalDate
import java.time.ZoneId

/**
 * The ways of a language's cards answered on a day, as the limits count them (SPEC.md §15; #156): those the day's
 * workload asked ([inWorkload]), which count against a limit, each once however often "Nochmal" brings it; and those
 * due when answered ([wasDue]), in the workload or in a topic or exam practised, which still count to the backlog.
 */
data class AskedToday(val inWorkload: Set<PromptKey> = emptySet(), val wasDue: Set<PromptKey> = emptySet()) {
    companion object {
        /** Of [reviews], those given on [today] as told in [zone]. */
        fun of(reviews: List<CardReview>, today: LocalDate, zone: ZoneId): AskedToday {
            val todays = reviews.filter { it.at.atZone(zone).toLocalDate() == today }
            return AskedToday(
                inWorkload = todays.filter { it.inWorkload }.map { PromptKey(it.cardId, it.reverse) }.toSet(),
                wasDue = todays.filter { it.wasDue }.map { PromptKey(it.cardId, it.reverse) }.toSet(),
            )
        }
    }
}

/** A language's [backlog] on a day, by its [id] and [name], to share the overall limit out by (#156). */
data class Backlog(val id: Long, val name: String, val backlog: Int)

/**
 * The limits of how many ways of its cards a day's workload asks (SPEC.md §15; #156): one for each language and one over
 * all, shared out by backlog, so that what piled up in a break comes over several days.
 */
object DailyLimits {
    /** What a language's own limit may be. */
    val PER_LANGUAGE = 20..500

    /** What the overall limit may be. */
    val OVERALL = 20..1_000

    /**
     * The backlog of a language's [cards] on [today]: the ways due and those answered today while due ([asked]), so
     * that it stays the same over the day as they are practised.
     */
    fun backlog(cards: List<Card>, asked: AskedToday, today: LocalDate): Int {
        val prompts = cards.flatMap { it.prompts }
        val keys = prompts.map { it.key }.toSet()
        return prompts.count { it.dueOn(today) && it.key !in asked.wasDue } + asked.wasDue.count { it in keys }
    }

    /**
     * The overall [limit] shared out by the [backlogs] of the languages not archived: each the limit times its backlog
     * over all of them, rounded so that the shares add up to the limit, the largest remainder first, by name if alike.
     * One without a backlog gets none.
     */
    fun shares(limit: Int, backlogs: List<Backlog>): Map<Long, Int> {
        val owing = backlogs.filter { it.backlog > 0 }
        val total = owing.sumOf { it.backlog.toLong() }
        if (total == 0L) return emptyMap()
        val exact = owing.map { it to limit.toLong() * it.backlog }
        val whole = exact.associate { (language, share) -> language.id to (share / total).toInt() }.toMutableMap()
        val left = limit - whole.values.sum()
        exact.sortedWith(compareByDescending<Pair<Backlog, Long>> { (_, share) -> share % total }.then(compareBy(InputText.byName) { it.first.name }))
            .take(left)
            .forEach { (language, _) -> whole[language.id] = whole.getValue(language.id) + 1 }
        return whole
    }

    /** A language's limit on a day: the smaller of its [own] and its [share]; none without either. */
    fun limitOf(own: Int?, share: Int?): Int? = listOfNotNull(own, share).minOrNull()

    /**
     * The workload on [today] of each of the [languages] not archived, by its id: of its [cards], with what was
     * answered today of [reviews] (told in [zone]), under the smaller of its own limit and its share of the [overall].
     */
    fun workloads(
        languages: List<Language>,
        cards: List<Card>,
        reviews: List<CardReview>,
        overall: Int?,
        today: LocalDate,
        zone: ZoneId,
    ): Map<Long, Workload> {
        val open = languages.filterNot { it.archived }
        val byLanguage = cards.groupBy { it.languageId }
        val languageOf = cards.associate { it.id to it.languageId }
        val reviewsBy = reviews.groupBy { languageOf[it.cardId] }
        val asked = open.associate { it.id to AskedToday.of(reviewsBy[it.id].orEmpty(), today, zone) }
        val shares = overall?.let { limit ->
            shares(limit, open.map { Backlog(it.id, it.name, backlog(byLanguage[it.id].orEmpty(), asked.getValue(it.id), today)) })
        }.orEmpty()
        return open.associate { language ->
            language.id to Workload.of(
                byLanguage[language.id].orEmpty(),
                language.newCardsPerDay,
                today,
                limit = limitOf(language.dailyLimit, shares[language.id]),
                asked = asked.getValue(language.id),
            )
        }
    }
}

/**
 * The Vokabeltrainer's settings over all languages (SPEC.md §15), in the backups unlike the device's: how many ways of
 * the cards of all languages together the day's workloads ask at most ([dailyLimit], #156), none for no limit. A single
 * row ([id] [ONLY]), none at all meaning no limit.
 */
@Entity(tableName = "card_settings")
data class CardSettings(
    @PrimaryKey val id: Int = ONLY,
    @ColumnInfo(name = "daily_limit") val dailyLimit: Int? = null,
) {
    companion object {
        /** The id of its only row. */
        const val ONLY = 1
    }
}
