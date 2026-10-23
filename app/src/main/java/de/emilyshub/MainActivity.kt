package de.emilyshub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.emilyshub.ui.EmilysHubApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as EmilysHubApplication
        setContent {
            EmilysHubApp(app.settings, app.appLock, app.authenticator, app.routines)
        }
    }
}
