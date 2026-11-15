package de.emilyshub.island

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle

/**
 * The clipboard as the island uses it (SPEC.md §14): what is copied from it is marked as sensitive, so that the phone
 * shows no preview of it, and taken off the clipboard again when the island locks, unless something else was copied since.
 */
class IslandClipboard(context: Context) {
    private val manager = context.getSystemService(ClipboardManager::class.java)

    /** The text last copied from the island, while it may still be on the clipboard. */
    private var copied: CharSequence? = null

    /** Marks [clip], about to be copied from the island, as sensitive, and remembers it to take it off again. */
    fun markCopied(clip: ClipData): ClipData {
        clip.description.extras = (clip.description.extras ?: PersistableBundle()).apply {
            // ClipDescription.EXTRA_IS_SENSITIVE, from Android 13; older ones ignore it.
            putBoolean(EXTRA_IS_SENSITIVE, true)
        }
        copied = clip.text()
        return clip
    }

    /** Takes the island's text off the clipboard, as the island locks, unless something else was copied since. */
    fun clearIfFromIsland() {
        val ours = copied ?: return
        copied = null
        // Android 10 and later only let an app read the clipboard while it has the focus. Without, it is taken off all
        // the same: better something copied elsewhere since is gone than the island's text left behind.
        val now = try {
            manager.primaryClip
        } catch (_: SecurityException) {
            null
        }
        if (now == null || now.text()?.toString() == ours.toString()) manager.clearPrimaryClip()
    }

    private fun ClipData.text(): CharSequence? = if (itemCount > 0) getItemAt(0).text else null

    private companion object {
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}
