package de.emilyshub.ui.doku

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.text.InputType
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import de.emilyshub.doku.Doku
import de.emilyshub.doku.DokuClipboard

/**
 * Keeps what is typed and copied in [content] on the island the Doku is (SPEC.md §14, "Insel"): every text field asks
 * the keyboard to learn nothing and to suggest no corrections, what is copied is marked sensitive for [doku]'s
 * clipboard to take off again, and each touch, key or keystroke tells [doku] it is still in use ([Doku.touched]).
 * Dialogs and menus have windows of their own: their touches reach [doku] by [touchesDoku] on them.
 */
@Composable
fun DokuInsel(doku: Doku, content: @Composable () -> Unit) {
    val keyboard = remember(doku) { PrivateKeyboard(doku::touched) }
    val platform = LocalClipboard.current
    val clipboard = remember(platform, doku) { SensitiveClipboard(platform, doku.clipboard) }
    InterceptPlatformTextInput(keyboard) {
        CompositionLocalProvider(LocalClipboard provides clipboard) {
            Box(Modifier.touchesDoku(doku)) {
                content()
            }
        }
    }
}

/** Tells [doku] of each touch and key in what it modifies, before anything there handles them. */
fun Modifier.touchesDoku(doku: Doku): Modifier = this
    .pointerInput(doku) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                doku.touched()
            }
        }
    }
    .onPreviewKeyEvent {
        doku.touched()
        false
    }

/** Asks the keyboard of every text field to learn nothing typed there and to suggest nothing, and tells [onInput] of each keystroke. */
private class PrivateKeyboard(private val onInput: () -> Unit) : PlatformTextInputInterceptor {
    override suspend fun interceptStartInputMethod(request: PlatformTextInputMethodRequest, nextHandler: PlatformTextInputSession): Nothing {
        val private = object : PlatformTextInputMethodRequest {
            override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                val connection = request.createInputConnection(outAttributes)
                outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                outAttributes.inputType = withoutSuggestions(outAttributes.inputType)
                return TouchingConnection(connection, onInput)
            }
        }
        nextHandler.startInputMethod(private)
    }
}

/** A text field's [inputType] without autocorrection and suggestions; others, such as numbers, stay as they are. */
internal fun withoutSuggestions(inputType: Int): Int =
    if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
        (inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT.inv()) or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    } else {
        inputType
    }

/** Tells [onInput] of what the keyboard types. */
private class TouchingConnection(target: InputConnection, private val onInput: () -> Unit) : InputConnectionWrapper(target, false) {
    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        onInput()
        return super.commitText(text, newCursorPosition)
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        onInput()
        return super.setComposingText(text, newCursorPosition)
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        onInput()
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        onInput()
        return super.sendKeyEvent(event)
    }
}

/** The [platform]'s clipboard, with what is copied marked by the Doku's [clipboard]. */
private class SensitiveClipboard(private val platform: Clipboard, private val clipboard: DokuClipboard) : Clipboard by platform {
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        platform.setClipEntry(clipEntry?.let { ClipEntry(clipboard.markCopied(it.clipData)) })
    }
}
