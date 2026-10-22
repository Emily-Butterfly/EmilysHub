package de.emilyshub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.emilyshub.ui.EmilysHubApp
import de.emilyshub.ui.theme.EmilysHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            EmilysHubTheme {
                EmilysHubApp()
            }
        }
    }
}
