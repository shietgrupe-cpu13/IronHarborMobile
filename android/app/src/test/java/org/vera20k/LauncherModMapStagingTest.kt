package org.vera20k

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LauncherModMapStagingTest {
    @Test
    fun mirrorsNestedEngineMapsToUnifiedFolderWithoutChangingSources() {
        val root = Files.createTempDirectory("ih-map-stage").toFile()
        try {
            val mod = root.resolve("mod").apply { mkdirs() }
            val maps = root.resolve("Documents/RA2Harbor/Configs/Maps")
            val nested = mod.resolve("MapsMO/Standard/arena.yrm").apply {
                parentFile.mkdirs()
                writeText("[Basic]\nName=MO Arena\n")
            }
            mod.resolve("Maps/extra.map").apply { parentFile.mkdirs(); writeText("[Basic]\nName=Extra\n") }
            mod.resolve("Archives/ignored.mix").apply { parentFile.mkdirs(); writeText("not a map") }

            val result = LauncherModMapStaging.sync(maps, mod)

            assertEquals(2, result.activeMaps)
            assertTrue(LauncherModMapStaging.isSynchronized(maps, mod))
            assertFalse(LauncherModMapStaging.isSynchronized(maps, root.resolve("other-mod")))
            assertEquals("[Basic]\nName=MO Arena\n", nested.readText())
            val staged = maps.listFiles()!!.filter { it.name.startsWith("ihmod_") }
            assertEquals(2, staged.size)
            assertTrue(staged.any { it.readText().contains("Name=MO Arena") })
            assertTrue(staged.any { it.readText().contains("Name=Extra") })

            nested.writeText("[Basic]\nName=MO Arena Updated\n")
            LauncherModMapStaging.sync(maps, mod)
            val refreshed = maps.listFiles()!!.filter { it.name.startsWith("ihmod_") }
            assertEquals(2, refreshed.size)
            assertTrue(refreshed.any { it.readText().contains("Name=MO Arena Updated") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun switchingAndUnselectingRemovesOnlyUnchangedManagedCopies() {
        val root = Files.createTempDirectory("ih-map-switch").toFile()
        try {
            val maps = root.resolve("Documents/RA2Harbor/Configs/Maps").apply { mkdirs() }
            val userMap = maps.resolve("my-map.map").apply { writeText("user-owned") }
            val firstMod = root.resolve("first").apply { mkdirs() }
            val oldSource = firstMod.resolve("Sub/old.yrm").apply { parentFile.mkdirs(); writeText("old map") }
            LauncherModMapStaging.sync(maps, firstMod)
            val oldCopy = maps.listFiles()!!.single { it.name.startsWith("ihmod_") }

            val secondMod = root.resolve("second").apply { mkdirs() }
            secondMod.resolve("maps/new.mpr").apply { parentFile.mkdirs(); writeText("new map") }
            val switched = LauncherModMapStaging.sync(maps, secondMod)
            assertEquals(1, switched.activeMaps)
            assertFalse(oldCopy.exists())
            assertTrue(oldSource.exists())
            val newCopy = maps.listFiles()!!.single { it.name.startsWith("ihmod_") }

            newCopy.writeText("user edited this copy")
            LauncherModMapStaging.sync(maps, null)
            assertTrue(userMap.exists())
            assertTrue(newCopy.exists())
            assertEquals("user edited this copy", newCopy.readText())
        } finally {
            root.deleteRecursively()
        }
    }
}
