package de.emilyshub

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.BundleCompat
import de.emilyshub.ui.EmilysHubApp
import de.emilyshub.ui.ledger.SubscriptionEditorTarget
import de.emilyshub.ui.navigation.Destination
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** What a reminder tapped asks to open, until the app has opened it, behind its lock if that is on. */
    private val opening = mutableStateOf<Destination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as EmilysHubApplication
        // Made anew, as on a rotation or after the process was gone, it keeps what was still to open, as while locked;
        // what was opened already is not opened again.
        opening.value = if (savedInstanceState == null) {
            intent.opening()
        } else {
            BundleCompat.getSerializable(savedInstanceState, OPENING, Destination::class.java)
        }
        publishShortcuts(this)
        // "Gedanke festhalten" back on each opening, should it have been swiped away (SPEC.md §16).
        app.background.launch { app.updateCaptureNotification() }
        setContent {
            EmilysHubApp(app, opening = opening.value, onOpened = { opening.value = null })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        opening.value?.let { outState.putSerializable(OPENING, it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.opening()?.let { opening.value = it }
    }

    companion object {
        private const val EXTRA_SUBSCRIPTION = "de.emilyshub.extra.OPEN_SUBSCRIPTION"
        private const val EXTRA_CANCEL = "de.emilyshub.extra.CANCEL"
        private const val EXTRA_CAPTURE = "de.emilyshub.extra.CAPTURE"

        /** The id of the shortcut "Neuer Gedanke" (SPEC.md §16). */
        const val SHORTCUT_CAPTURE = "capture"

        /** Under what what is still to open is kept while the activity is made anew. */
        private const val OPENING = "opening"

        /** Opens the line for a new thought over what is open (SPEC.md §16), as the shortcut on the app's icon does. */
        fun capture(context: Context): Intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_CAPTURE, true)
            // Into the app's task as it is: what is open there, a draft in an editor say, stays below.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /**
         * "Neuer Gedanke" on a long press of the app's icon (SPEC.md §16). Published from here, not declared in the
         * resources: Android starts a shortcut declared there in a cleared task, which would throw away what was open.
         * It opens this activity, the only one exported (SPEC.md §9), with [capture].
         */
        private fun publishShortcuts(context: Context) {
            val shortcut = ShortcutInfo.Builder(context, SHORTCUT_CAPTURE)
                .setShortLabel(context.getString(R.string.inbox_capture_short))
                .setLongLabel(context.getString(R.string.inbox_capture_long))
                .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_thought))
                .setIntent(capture(context))
                .build()
            context.getSystemService(ShortcutManager::class.java)?.dynamicShortcuts = listOf(shortcut)
        }

        /** Opens the Abo [id] (#80), asking until when it was cancelled if [cancel], as its reminder does. */
        fun openSubscription(context: Context, id: Long, cancel: Boolean): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SUBSCRIPTION, id)
            .putExtra(EXTRA_CANCEL, cancel)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /**
         * What [this] intent asks to open: the line for a thought, an Abo by a valid id, or nothing. The activity is the launcher's, so anything
         * may send here: it opens no more than tapping through the app would, and only behind the app's lock (SPEC.md §9).
         */
        private fun Intent.opening(): Destination? {
            // The empty line for a thought, nothing else.
            if (getBooleanExtra(EXTRA_CAPTURE, false)) return Destination.Capture
            val id = getLongExtra(EXTRA_SUBSCRIPTION, 0).takeIf { it > 0 } ?: return null
            return Destination.SubscriptionEditor(SubscriptionEditorTarget.Edit(id, cancelling = getBooleanExtra(EXTRA_CANCEL, false)))
        }
    }
}
