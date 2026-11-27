package org.vera20k

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidSharedStoragePathTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun resolvesSelectedPrimaryFolderWithoutCopyingIt() {
        val primary = temporary.newFolder("primary")
        val selected = File(primary, "Games/Red Alert 2")
        assertTrue(selected.mkdirs())

        assertEquals(selected.canonicalFile,
            AndroidSharedStoragePath.fromDocumentId("primary:Games/Red Alert 2", primary))
        assertEquals("Documents/Ra2Harbor",
            AndroidSharedStoragePath.sharedDataRoot(primary).relativeTo(primary).path
                .replace(File.separatorChar, '/'))
    }

    @Test fun reusesExistingDataRootSpellingAndLegacyFolders() {
        listOf("Ra2Harbor", "RA2Harbor", "IronHarborRA2YR", "RA2IRONHARBOR").forEachIndexed { index, name ->
            val primary = temporary.newFolder("primary-$index")
            val documents = File(primary, "Documents").apply { assertTrue(mkdirs()) }
            val existing = File(documents, name).apply { assertTrue(mkdirs()) }

            assertEquals(existing.canonicalFile,
                AndroidSharedStoragePath.sharedDataRoot(primary).canonicalFile)
        }
    }

    @Test fun refusesOtherVolumesTraversalAndMissingFolders() {
        val primary = temporary.newFolder("primary")
        assertNull(AndroidSharedStoragePath.fromDocumentId("ABCD-1234:Games/RA2", primary))
        assertNull(AndroidSharedStoragePath.fromDocumentId("primary:../private", primary))
        assertNull(AndroidSharedStoragePath.fromDocumentId("primary:Games/Missing", primary))
    }

    @Test fun reportsMissingArchivesCaseInsensitively() {
        val install = temporary.newFolder("install")
        File(install, "RA2.MIX").writeBytes(byteArrayOf(1))
        assertEquals(listOf("ra2md.mix"), GameFolderValidation.missingRequiredArchives(install))
        File(install, "Ra2Md.MiX").writeBytes(byteArrayOf(1))
        assertTrue(GameFolderValidation.missingRequiredArchives(install).isEmpty())
    }
}
