package de.emilyshub.warum

import de.emilyshub.backup.BackupFolder
import de.emilyshub.languages.LessonDocuments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext

/**
 * "Als Bild exportieren" (SPEC.md §18, §9): the whole board drawn by [picture] in the design the app shows; one image
 * written to the file chosen in Android's file dialog through [documents], several into a folder chosen once
 * ([folderAt]), named by the day. The only copy of Mein Warum outside its encryption, which the screen says first; drawn
 * in memory and written straight there, nothing of it in the app's own storage, and never shared with other apps.
 */
class BoardExport(
    private val reasons: Reasons,
    private val documents: LessonDocuments,
    private val folderAt: (String) -> BackupFolder,
    private val picture: BoardPicture,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    /** How many images the board makes in [theme]; none for an empty board. */
    suspend fun count(theme: BoardTheme): Int = withContext(io) { pagesOf(theme).size }

    /** The name the image [page] of [of] is saved under today. */
    fun fileName(page: Int, of: Int): String = BoardPages.fileName(LocalDate.now(clock), page, of)

    /** Writes the board, which must make one image, to the file at [address]; whether it worked. */
    suspend fun saveOne(address: String, theme: BoardTheme): Boolean = withContext(io + NonCancellable) {
        val pages = pagesOf(theme)
        val page = pages.singleOrNull() ?: return@withContext false
        documents.write(address, draw(page, theme))
    }

    /** Writes every image of the board into the folder at [address]; whether all of them could be. */
    suspend fun saveInto(address: String, theme: BoardTheme): Boolean = withContext(io + NonCancellable) {
        val pages = pagesOf(theme)
        if (pages.isEmpty()) return@withContext false
        val folder = folderAt(address)
        try {
            pages.forEachIndexed { index, page ->
                val bytes = draw(page, theme)
                folder.write(fileName(index + 1, pages.size)).use { it.write(bytes) }
            }
            true
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            // The folder's access gone meanwhile.
            false
        }
    }

    /** One image: the Gründe of [page], drawn with their photos read anew. */
    private suspend fun draw(page: Page, theme: BoardTheme): ByteArray {
        val drawn = page.reasons.map { drawn(it) }
        return picture.draw(drawn, page.heights, theme)
    }

    /** The board's Gründe split into images, each with how high they stand; their photos read one by one to measure. */
    private suspend fun pagesOf(theme: BoardTheme): List<Page> {
        val board = reasons.board().first()
        val heights = board.map { picture.heightOf(drawn(it), theme) }
        return picture.split(heights).map { range -> Page(board.slice(range), heights.slice(range)) }
    }

    private suspend fun drawn(reason: Reason) = DrawnReason(reason.text, reason.look, reason.photo?.let { reasons.photo(it) })

    private class Page(val reasons: List<Reason>, val heights: List<Int>)
}
