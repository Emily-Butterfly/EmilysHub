package de.emilyshub.ui.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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

/** What reminders still need from Android, in the order it is asked for. */
private enum class Missing { Notifications, ExactAlarms }

/**
 * Asks for what reminders need (SPEC.md §3, §11), explaining each in German before Android asks:
 * showing notifications, then exact alarms if Android does not allow those yet. Either can wait.
 * [onFinished] comes once everything was asked for, whatever the answers.
 */
@Composable
fun ReminderPermissions(onFinished: () -> Unit) {
    val context = LocalContext.current
    var missing by rememberSaveable { mutableStateOf(firstMissing(context)) }
    val afterNotifications = { missing = if (exactAlarmsAllowed(context)) null else Missing.ExactAlarms }
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
                    val settings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    context.openSettings(settings.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.openExactAlarmSettings()
                missing = null
            },
            onLater = { missing = null },
        )
        null -> LaunchedEffect(Unit) { onFinished() }
    }
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
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.reminders_later)) } },
    )
}

private fun firstMissing(context: Context): Missing? = when {
    !notificationsAllowed(context) -> Missing.Notifications
    !exactAlarmsAllowed(context) -> Missing.ExactAlarms
    else -> null
}

private fun notificationsAllowed(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

// Granted with the app from Android 13 on (USE_EXACT_ALARM); before that, Android 12 lets the user take it back.
private fun exactAlarmsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

@RequiresApi(Build.VERSION_CODES.S)
private fun Context.openExactAlarmSettings() =
    openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", packageName, null)))

/** A phone without that settings page gets the app's page in Android's settings instead. */
private fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
}
