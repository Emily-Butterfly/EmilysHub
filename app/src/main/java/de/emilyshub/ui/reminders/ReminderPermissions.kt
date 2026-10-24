package de.emilyshub.ui.reminders

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.reminders.exactAlarmsAllowed
import de.emilyshub.reminders.fullScreenAlarmsAllowed
import de.emilyshub.reminders.notificationsAllowed
import de.emilyshub.ui.SystemSettings
import de.emilyshub.ui.openSettings

/** What reminders still need from Android, in the order it is asked for. */
private enum class Missing { Notifications, ExactAlarms }

/**
 * Asks for what reminders need (SPEC.md §3, §11), explaining each in German before Android asks:
 * showing notifications, then exact alarms if Android does not allow those yet. Either can be declined;
 * Android's settings can allow them later. [onFinished] comes once everything was asked, whatever the answers.
 */
@Composable
fun ReminderPermissions(onFinished: () -> Unit) {
    val context = LocalContext.current
    var missing by rememberSaveable { mutableStateOf(firstMissing(context)) }
    val afterNotifications = { missing = if (context.exactAlarmsAllowed()) null else Missing.ExactAlarms }
    val requestNotifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { afterNotifications() }

    when (missing) {
        Missing.Notifications -> Explanation(
            title = R.string.reminders_notifications_title,
            text = R.string.reminders_notifications_text,
            confirm = R.string.reminders_continue,
            onConfirm = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Before Android 13 notifications are on unless turned off in the app's settings.
                    context.openSettings(SystemSettings.notifications(context))
                    afterNotifications()
                }
            },
            onLater = afterNotifications,
        )
        Missing.ExactAlarms -> Explanation(
            title = R.string.reminders_exact_title,
            text = R.string.reminders_exact_text,
            confirm = R.string.reminders_open_settings,
            onConfirm = {
                context.openSettings(SystemSettings.exactAlarms(context))
                missing = null
            },
            onLater = { missing = null },
        )
        null -> LaunchedEffect(Unit) { onFinished() }
    }
}

/**
 * Asks Android for full-screen alarms (SPEC.md §3, §11) once a routine is to have one: from Android 14 on
 * the user allows them in Android's settings, explained in German first. Without them the routine's
 * reminders come as normal notifications. [onFinished] comes once that was asked, or right away if they
 * are allowed already.
 */
@Composable
fun FullScreenAlarmPermission(onFinished: () -> Unit) {
    val context = LocalContext.current
    if (context.fullScreenAlarmsAllowed()) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }
    Explanation(
        title = R.string.reminders_full_screen_title,
        text = R.string.reminders_full_screen_text,
        confirm = R.string.reminders_open_settings,
        onConfirm = {
            context.openSettings(SystemSettings.fullScreenAlarms(context))
            onFinished()
        },
        onLater = onFinished,
    )
}

@Composable
private fun Explanation(
    @StringRes title: Int,
    @StringRes text: Int,
    @StringRes confirm: Int,
    onConfirm: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.reminders_decline)) } },
    )
}

private fun firstMissing(context: Context): Missing? = when {
    !context.notificationsAllowed() -> Missing.Notifications
    !context.exactAlarmsAllowed() -> Missing.ExactAlarms
    else -> null
}
