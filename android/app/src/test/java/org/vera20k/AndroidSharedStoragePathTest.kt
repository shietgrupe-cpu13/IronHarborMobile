package org.vera20k

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidSharedStoragePathTest {
    @Test
    fun defaultDataRootIsTheSingleDocumentsFolder() {
        val primary = File("/storage/emulated/0")
        assertEquals(File("/storage/emulated/0/Documents/RA2Harbor"), AndroidSharedStoragePath.defaultDataRoot(primary))
    }

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

    @Test
    fun selectedModMustRemainUnderTheSingleManagedModsFolder() {
        val tempRoot = requireNotNull(System.getProperty("java.io.tmpdir"))
        val root = Files.createTempDirectory(File(tempRoot).toPath(), "ironharbor-mods-").toFile()
        try {
            val mods = File(root, "Documents/RA2Harbor/Mods").apply { mkdirs() }
            val selected = File(mods, "wrapper/Mental Omega").apply { mkdirs() }
            val outside = File(root, "Documents/OtherMod").apply { mkdirs() }
            assertEquals("wrapper/Mental Omega", AndroidSharedStoragePath.relativeFolderName(mods, selected))
            assertNull(AndroidSharedStoragePath.relativeFolderName(mods, mods))
            assertNull(AndroidSharedStoragePath.relativeFolderName(mods, outside))
        } finally {
            root.deleteRecursively()
        }
    }
}
