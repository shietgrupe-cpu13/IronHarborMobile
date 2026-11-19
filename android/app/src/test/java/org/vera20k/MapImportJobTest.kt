package org.vera20k

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapImportJobTest {
    @Test fun acceptsOnlyMapFormatsRecognizedByEngine() {
        listOf("arena.MMX", "scenario.yro", "one.map", "two.mpr", "seed.yrm").forEach {
            assertEquals(it, MapImportJob.safeMapName(it))
        }
        listOf("notes.txt", "map.mix", "unknown").forEach { name ->
            val error = runCatching { MapImportJob.safeMapName(name) }.exceptionOrNull()
            assertTrue("$name should be rejected", error is IOException)
        }
    }

    @Test fun rejectsProviderNamesThatCouldEscapeMapDirectory() {
        listOf("../escape.map", "folder\\escape.map", ".", "").forEach { name ->
            assertTrue(name, runCatching { MapImportJob.safeMapName(name) }.exceptionOrNull() is IOException)
        }
    }

    @Test fun preservesExistingMapAndUsesNumberedNameForConflict() {
        val directory = Files.createTempDirectory("map-import-test").toFile()
        try {
            val existing = File(directory, "arena.map")
            existing.writeText("existing")
            val job = MapImportJob.begin()!!
            val imported = try {
                job.run(directory, listOf(MapImportJob.Source("arena.map") { it.writeText("imported") })) { }
            } finally {
                job.close()
            }
            assertEquals("existing", existing.readText())
            assertEquals("imported", imported.single().readText())
            assertEquals("arena (2).map", imported.single().name)
            assertFalse(directory.listFiles().orEmpty().any { it.name.startsWith(".vera-map-") })
        } finally {
            directory.deleteRecursively()
        }
    }
}
