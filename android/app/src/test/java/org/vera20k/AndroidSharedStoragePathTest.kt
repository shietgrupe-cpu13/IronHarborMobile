package org.vera20k

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidSharedStoragePathTest {
    @Test
    fun resolvesOnlyExistingFoldersOnPrimarySharedStorage() {
        val tempRoot = requireNotNull(System.getProperty("java.io.tmpdir"))
        val root = Files.createTempDirectory(File(tempRoot).toPath(), "ironharbor-storage-").toFile()
        try {
            val selected = File(root, "Documents/Game").apply { mkdirs() }
            assertEquals(selected.canonicalFile, AndroidSharedStoragePath.fromDocumentId("primary:Documents/Game", root))
            assertNull(AndroidSharedStoragePath.fromDocumentId("ABCD-1234:Game", root))
            assertNull(AndroidSharedStoragePath.fromDocumentId("primary:../private", root))
            assertNull(AndroidSharedStoragePath.fromDocumentId("primary:missing", root))
        } finally {
            root.deleteRecursively()
        }
    }
}
