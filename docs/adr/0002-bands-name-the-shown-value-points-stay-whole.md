# Bands name the value as shown; an option's points stay whole

A form's bands (`Band`) compare a score's value as it is shown, rounded to hundredths, up to and including their bound;
a grade's bounds (`Bound`) compare the exact value. The third architecture review (docs/architecture/deepening-plan-3.md,
§5) proposed comparing bands on the exact value too, with one "below/from" rule for both, and decimal option points in
place of the Wells-Scores' `divisor = 10`. We decided against both.

Bands name what the reader sees next to them: compared exactly, a value of 50,996 % would show as "51,00 %" and read
"unter 51 %" on the same line. A grade decides a value that others build on, such as the GCPS's grade from its
impairment points, so it takes the value as it is. The friction that was real, form authors writing rounded hundredths
by hand (`Band(5099)` with a comment), is gone with `Band.below` and `Band.rest`, which say "unter 51" and "> 6" as the
originals do.

An option's points stay whole numbers: every form's helpers (`scored`, `numbered`, `fourToZero`) and every item's range
are whole, and only the Wells-Scores count tenths. Decimal points would turn all of them over for one family of forms;
`ItemScore.divisor` says what the tenths are.

Revisit if a form's original names a band by a value that the rounding to hundredths blurs, or if more forms than the
Wells-Scores count points in fractions.
