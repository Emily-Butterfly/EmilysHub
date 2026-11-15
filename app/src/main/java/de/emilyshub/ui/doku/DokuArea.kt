package de.emilyshub.ui.doku

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import de.emilyshub.Hub
import de.emilyshub.R
import de.emilyshub.doku.Appointments
import de.emilyshub.doku.Assessments
import de.emilyshub.doku.Doku
import de.emilyshub.doku.Measurements
import de.emilyshub.doku.Patients
import de.emilyshub.doku.TestCatalog
import de.emilyshub.ui.island.IslandGate
import de.emilyshub.ui.island.IslandTexts

/**
 * The Doku in the tab of Sonstiges (SPEC.md §14): behind its [IslandGate], and only once unlocked its [pages]
 * ([onPagesChange]). Each unlock starts on its start page.
 */
@Composable
internal fun DokuArea(hub: Hub, pages: DokuPages, onPagesChange: (DokuPages) -> Unit, contentPadding: PaddingValues) {
    val doku = hub.doku
    val modules = remember(hub) { DokuModules(hub.patients, hub.appointments, hub.tests, hub.measurements, hub.assessments) }
    IslandGate(
        island = doku,
        texts = DOKU_TEXTS,
        contentPadding = contentPadding,
        // What was open goes with the lock: the next unlock starts on the start page.
        onLocked = { if (pages != DokuPages()) onPagesChange(DokuPages()) },
    ) {
        CompositionLocalProvider(LocalDokuPages provides rememberUpdatedState(pages)) {
            OpenPage(doku, modules, pages, onPagesChange, contentPadding)
        }
    }
}

/** What the Doku's screens call it and its password. */
internal val DOKU_TEXTS = IslandTexts(
    setUpTitle = R.string.doku_set_up_title,
    passwordNew = R.string.doku_password_new,
    passwordRepeat = R.string.doku_password_repeat,
    lockedTitle = R.string.doku_locked_title,
    fingerprintLost = R.string.doku_fingerprint_lost,
    fingerprintAgainText = R.string.doku_fingerprint_again_text,
    menu = R.string.doku_menu,
    changePassword = R.string.doku_menu_change_password,
)

/** What the open Doku's pages read and write, each only while it is open. */
internal data class DokuModules(
    val patients: Patients,
    val appointments: Appointments,
    val tests: TestCatalog,
    val measurements: Measurements,
    val assessments: Assessments,
)

/** The page of the open Doku on top of [pages]. */
@Composable
private fun OpenPage(doku: Doku, modules: DokuModules, pages: DokuPages, onPagesChange: (DokuPages) -> Unit, contentPadding: PaddingValues) {
    val (patients, appointments, tests, measurements, assessments) = modules
    val open = { page: DokuPage -> onPagesChange(pages.open(page)) }
    val back = { onPagesChange(pages.back()) }
    when (val page = pages.top) {
        DokuPage.Home -> DokuHome(
            doku = doku,
            patients = patients,
            contentPadding = contentPadding,
            onOpen = { open(DokuPage.Patient(it)) },
            // Over the patient's appointments, for back to lead there.
            onOpenAppointment = { patientId, id -> onPagesChange(pages.openFoundAppointment(patientId, id)) },
            onOpenAssessment = { patientId, hit ->
                onPagesChange(pages.openFoundAssessment(patientId, hit.appointmentId, hit.assessmentId, hit.id))
            },
            onOpenArchive = { open(DokuPage.Archive) },
        )
        DokuPage.Archive -> DokuArchivePage(doku, patients, contentPadding, onOpen = { open(DokuPage.Patient(it)) })
        is DokuPage.Patient -> PatientPage(
            doku = doku,
            modules = modules,
            page = page,
            contentPadding = contentPadding,
            onTab = { onPagesChange(pages.replaceTop(page.copy(tab = it))) },
            onEditText = { open(DokuPage.Text(page.id, it)) },
            onOpenAppointment = { open(DokuPage.Appointment(page.id, it)) },
            onAddedAppointment = { open(DokuPage.Appointment(page.id, it)) },
            onOpenHistory = { open(DokuPage.History(page.id, it)) },
            onOpenAssessmentHistory = { open(DokuPage.AssessmentHistory(page.id, it)) },
            onOpenAssessment = { appointmentId, assessmentId, id -> open(DokuPage.Assessment(page.id, appointmentId, assessmentId, id)) },
            onGone = { deleted -> onPagesChange(pages.gone(page.id, deleted)) },
        )
        is DokuPage.Text -> PatientTextPage(doku, patients, page, contentPadding, onDone = back)
        is DokuPage.Appointment -> AppointmentPage(
            doku = doku,
            modules = modules,
            page = page,
            contentPadding = contentPadding,
            onMeasure = { testId, id -> onPagesChange(pages.measureAt(page, testId, id)) },
            onFill = { assessmentId, id, new -> onPagesChange(pages.fillAt(page, assessmentId, id, new)) },
            onDone = back,
        )
        DokuPage.Template -> TemplatePage(doku, appointments, contentPadding, onDone = back)
        is DokuPage.Assessment -> AssessmentPage(doku, assessments, page, contentPadding, onDone = back)
        is DokuPage.AssessmentHistory -> AssessmentHistoryPage(
            doku = doku,
            assessments = assessments,
            page = page,
            contentPadding = contentPadding,
            onOpen = { appointmentId, id -> open(DokuPage.Assessment(page.patientId, appointmentId, page.assessmentId, id)) },
        )
        DokuPage.Assessments -> AssessmentsMenuPage(doku, assessments, contentPadding)
        DokuPage.Tests -> TestsPage(doku, tests, contentPadding, onOpen = { open(DokuPage.TestEditor(it)) })
        is DokuPage.TestEditor -> TestEditorPage(doku, tests, page, contentPadding, onDone = back)
        is DokuPage.Measurement -> MeasurementPage(doku, measurements, page, contentPadding, onDone = back)
        is DokuPage.History -> HistoryPage(
            doku = doku,
            measurements = measurements,
            page = page,
            contentPadding = contentPadding,
            onOpenAppointment = { open(DokuPage.Appointment(page.patientId, it)) },
        )
    }
}
