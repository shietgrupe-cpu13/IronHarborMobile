package org.vera20k

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalFolderPickerTest {
    @Test
    fun emptyFolderHasNonNullPlaceholderLabel() {
        assertEquals(listOf("No subfolders"), localFolderRowLabels(emptyList()))
    }

    @Test
    fun parentAndChildFoldersHaveNonNullLabels() {
        assertEquals(
            listOf("Parent folder", "ExampleMod"),
            localFolderRowLabels(listOf(null, File("ExampleMod"))),
        )
    }
}
