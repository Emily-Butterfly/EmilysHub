package de.emilyshub

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import de.emilyshub.ui.EmilysHubApp
import de.emilyshub.ui.ledger.SubscriptionEditorTarget
import de.emilyshub.ui.navigation.Destination

class MainActivity : ComponentActivity() {

    /** What a reminder tapped asks to open, until the app has opened it, behind its lock if that is on. */
    private val opening = mutableStateOf<Destination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as EmilysHubApplication
        // Not again when Android makes the activity anew, as on a rotation: it was opened then already.
        if (savedInstanceState == null) opening.value = intent.opening()
        setContent {
            EmilysHubApp(app, opening = opening.value, onOpened = { opening.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.opening()?.let { opening.value = it }
    }

    companion object {
        private const val EXTRA_SUBSCRIPTION = "de.emilyshub.extra.OPEN_SUBSCRIPTION"
        private const val EXTRA_CANCEL = "de.emilyshub.extra.CANCEL"

        /** Opens the Abo [id] (#80), asking until when it was cancelled if [cancel], as its reminder does. */
        fun openSubscription(context: Context, id: Long, cancel: Boolean): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SUBSCRIPTION, id)
            .putExtra(EXTRA_CANCEL, cancel)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /**
         * What [this] intent asks to open: an Abo by a valid id, or nothing. The activity is the launcher's, so anything
         * may send here: it opens no more than tapping through the app would, and only behind the app's lock (SPEC.md §9).
         */
        private fun Intent.opening(): Destination? {
            val id = getLongExtra(EXTRA_SUBSCRIPTION, 0).takeIf { it > 0 } ?: return null
            return Destination.SubscriptionEditor(SubscriptionEditorTarget.Edit(id, cancelling = getBooleanExtra(EXTRA_CANCEL, false)))
        }
    }
}
