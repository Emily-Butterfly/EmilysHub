package de.emilyshub.doku

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * A test of the Doku (SPEC.md §14), set up once for all patients, such as "Handkraft" or "Barthel-Index": one or more
 * numeric [TestValue]s, and if [hasSum] their sum, better the [sumDirection] way. Archived on [archivedOn], it can no
 * longer be chosen for new measurements; its measurements stay.
 */
@Entity(tableName = "doku_test")
data class ClinicalTest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "has_sum") val hasSum: Boolean = false,
    @ColumnInfo(name = "sum_direction") val sumDirection: Direction = Direction.HigherIsBetter,
    @ColumnInfo(name = "archived_on") val archivedOn: LocalDate? = null,
) {
    /** Whether it is archived: not to choose for new measurements. */
    val isArchived: Boolean get() = archivedOn != null
}

/**
 * A value of a test, at its [position] among them: a number in [unit], if any, within [min] and [max], if given, in
 * hundredths ([TestNumber]); with two [decimals] at most, or whole; better the [direction] way. Once [retired], new
 * measurements no longer ask for it, and its old ones stay.
 */
@Entity(
    tableName = "doku_test_value",
    foreignKeys = [ForeignKey(entity = ClinicalTest::class, parentColumns = ["id"], childColumns = ["test_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("test_id")],
)
data class TestValue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "test_id") val testId: Long,
    val position: Int,
    val name: String,
    val unit: String = "",
    val min: Long? = null,
    val max: Long? = null,
    val decimals: Boolean = false,
    val direction: Direction = Direction.HigherIsBetter,
    val retired: Boolean = false,
)

/** Which way a value, or a sum, gets better. */
enum class Direction(val stored: String) {
    HigherIsBetter("higher"),
    LowerIsBetter("lower"),
}

/** A measurement of a test at an appointment, with its date, and an optional [remark]. */
@Entity(
    tableName = "measurement",
    foreignKeys = [
        ForeignKey(entity = Appointment::class, parentColumns = ["id"], childColumns = ["appointment_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ClinicalTest::class, parentColumns = ["id"], childColumns = ["test_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("appointment_id"), Index("test_id")],
)
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "appointment_id") val appointmentId: Long,
    @ColumnInfo(name = "test_id") val testId: Long,
    val remark: String = "",
)

/** What a measurement found for one value of its test, in hundredths ([TestNumber]); a value left empty has none. */
@Entity(
    tableName = "measurement_value",
    primaryKeys = ["measurement_id", "value_id"],
    foreignKeys = [
        ForeignKey(entity = Measurement::class, parentColumns = ["id"], childColumns = ["measurement_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TestValue::class, parentColumns = ["id"], childColumns = ["value_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("value_id")],
)
data class MeasurementValue(
    @ColumnInfo(name = "measurement_id") val measurementId: Long,
    @ColumnInfo(name = "value_id") val valueId: Long,
    val amount: Long,
)

/** How the Doku stores what is its own. */
class DokuConverters {
    @TypeConverter
    fun fromDirection(direction: Direction): String = direction.stored

    /** Anything else counts as higher is better, the default. */
    @TypeConverter
    fun toDirection(text: String): Direction = Direction.entries.find { it.stored == text } ?: Direction.HigherIsBetter
}
