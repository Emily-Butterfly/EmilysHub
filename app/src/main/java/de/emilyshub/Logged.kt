package de.emilyshub

/**
 * What a failure was and where, for the log: its kind and the line it came from, never its message, which may quote the
 * data (SPEC.md §9); the log goes into bug reports, which leave the phone.
 */
internal val Throwable.logged: String get() = "${javaClass.name} at ${stackTrace.firstOrNull() ?: "?"}"
