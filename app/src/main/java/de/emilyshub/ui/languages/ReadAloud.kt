package de.emilyshub.ui.languages

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.emilyshub.R
import de.emilyshub.speech.OfflineVoice
import de.emilyshub.speech.Speech
import kotlinx.coroutines.launch

/**
 * How to read a text of a language aloud with its [voice] (SPEC.md §15; #157), only when asked; none without a voice
 * chosen, or with one no longer among the phone's voices without network. What is read stops as the screen is left.
 */
@Composable
internal fun rememberReadAloud(speech: Speech, voice: String?): ((String) -> Unit)? {
    val available = voice != null && rememberOfflineVoices(speech).orEmpty().any { it.name == voice }
    // Left, the screen reads no more.
    DisposableEffect(speech) { onDispose { speech.stop() } }
    val scope = rememberCoroutineScope()
    val read = remember(speech, voice, scope) { { text: String -> voice?.let { scope.launch { speech.speak(it, text) } }; Unit } }
    return read.takeIf { available }
}

/**
 * The phone's voices without network (#157), asked again on every return to the app, so that one loaded meanwhile in
 * Android's settings is there; none until they are known.
 */
@Composable
internal fun rememberOfflineVoices(speech: Speech): List<OfflineVoice>? {
    var voices by remember(speech) { mutableStateOf<List<OfflineVoice>?>(null) }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(speech) {
        val asking = scope.launch { voices = speech.voices() }
        onPauseOrDispose { asking.cancel() }
    }
    return voices
}

/** The loudspeaker that reads [text] aloud by [read] when pressed (#157). */
@Composable
internal fun ReadAloudButton(read: (String) -> Unit, text: String, modifier: Modifier = Modifier) {
    IconButton(onClick = { read(text) }, modifier = modifier) {
        Icon(SpeakerIcon, contentDescription = stringResource(R.string.read_aloud))
    }
}

/** Material's "Volume up", mirrored right to left. */
private val SpeakerIcon: ImageVector
    get() = materialIcon(name = "AutoMirrored.Filled.VolumeUp", autoMirror = true) {
        materialPath {
            moveTo(3f, 9f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(4f)
            lineToRelative(5f, 5f)
            verticalLineTo(4f)
            lineTo(7f, 9f)
            horizontalLineTo(3f)
            close()
            moveTo(16.5f, 12f)
            curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
            verticalLineToRelative(8.05f)
            curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
            close()
            moveTo(14f, 3.23f)
            verticalLineToRelative(2.06f)
            curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
            reflectiveCurveToRelative(-2.11f, 5.85f, -5f, 6.71f)
            verticalLineToRelative(2.06f)
            curveToRelative(4.01f, -0.91f, 7f, -4.49f, 7f, -8.77f)
            reflectiveCurveToRelative(-2.99f, -7.86f, -7f, -8.77f)
            close()
        }
    }
