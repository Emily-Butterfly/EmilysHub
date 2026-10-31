package de.emilyshub.backup

import android.content.pm.ProviderInfo
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** The folder for the nightly backups as the Storage Access Framework gives it, a directory behind a documents provider. */
@RunWith(AndroidJUnit4::class)
class DocumentFolderTest {

    @get:Rule
    val directory = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val tree = DocumentsContract.buildTreeDocumentUri(FolderProvider.AUTHORITY, FolderProvider.ROOT)
    private lateinit var folder: DocumentFolder

    @Before
    fun provider() {
        FolderProvider.directory = directory.root
        val info = ProviderInfo().apply {
            authority = FolderProvider.AUTHORITY
            exported = true
            grantUriPermissions = true
            readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
            writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
        }
        Robolectric.buildContentProvider(FolderProvider::class.java).create(info)
        folder = DocumentFolder(app, tree)
    }

    @Test
    fun `files are written, listed, overwritten and deleted`() {
        directory.newFile("Urlaub.jpg")

        folder.write("a.emhb").use { it.write(byteArrayOf(1, 2, 3)) }
        folder.write("a.emhb").use { it.write(byteArrayOf(4)) }

        assertEquals(listOf("Urlaub.jpg", "a.emhb"), folder.fileNames().sorted())
        assertEquals(listOf<Byte>(4), directory.root.resolve("a.emhb").readBytes().toList())
        folder.delete("a.emhb")
        assertEquals(listOf("Urlaub.jpg"), folder.fileNames())
    }

    @Test
    fun `its name is the one Android shows, and its access is kept and given back`() {
        assertEquals("Backups", folder.name)

        folder.keepAccess()
        assertTrue(app.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission })
        folder.releaseAccess()
        assertTrue(app.contentResolver.persistedUriPermissions.none { it.uri == tree })
    }
}
