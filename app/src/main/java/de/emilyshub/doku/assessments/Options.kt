package de.emilyshub.doku.assessments

/** Options scored 0, 1, 2 … in the order given, as the originals number them, such as the KOOS: 0 for few symptoms. */
internal fun numbered(vararg labels: String): List<Option> = labels.mapIndexed { i, label -> Option("$i", label, i) }

/** Options scored from the most for the first down to 0 for the last, as the HOOS scores them: 4 for few symptoms. */
internal fun fourToZero(vararg labels: String): List<Option> = labels.mapIndexed { i, label -> Option("$i", label, labels.size - 1 - i) }

/** Options with the [points] given, in the order of the original, their ids numbered from 0. */
internal fun scored(vararg options: Pair<String, Int>): List<Option> =
    options.mapIndexed { i, (label, points) -> Option("$i", label, points) }

/** Each of [items]' ids. */
internal fun ids(items: List<Item>): List<String> = items.map(Item::id)

/** Half of [n] items, rounded up: the least to answer of a subscale of the HOOS or FAOS. */
internal fun half(n: Int): Int = (n + 1) / 2
