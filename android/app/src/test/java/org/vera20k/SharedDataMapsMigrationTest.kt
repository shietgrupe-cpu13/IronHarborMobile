package org.vera20k

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedDataMapsMigrationTest {
    @Test
    fun movesLegacyConfigsMapsAndNestedRandomMapDataIntoCanonicalFolder() {
        val root = Files.createTempDirectory("ih-maps-migration").toFile()
        try {
            val legacyMap = root.resolve("Configs/Maps/arena.yrm")
            val randomMapState = root.resolve("Configs/Maps/RandomMaps/seed.ini")
            legacyMap.parentFile.mkdirs()
            randomMapState.parentFile.mkdirs()
            legacyMap.writeText("scenario")
            randomMapState.writeText("seed")

            SharedDataStorage.migrateLegacyMaps(root)

            assertEquals("scenario", root.resolve("Maps/arena.yrm").readText())
            assertEquals("seed", root.resolve("Maps/RandomMaps/seed.ini").readText())
            assertFalse(root.resolve("Configs/Maps").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun keepsExistingUnifiedMapAndMovesCollisionWithoutOverwritingOnRepeat() {
        val root = Files.createTempDirectory("ih-maps-collision").toFile()
        try {
            root.resolve("Configs/Maps/arena.yrm").apply {
                parentFile.mkdirs()
                writeText("legacy")
            }
            root.resolve("Maps/arena.yrm").apply {
                parentFile.mkdirs()
                writeText("current")
            }

            SharedDataStorage.migrateLegacyMaps(root)
            SharedDataStorage.migrateLegacyMaps(root)

            assertEquals("current", root.resolve("Maps/arena.yrm").readText())
            assertEquals("legacy", root.resolve("Maps/arena (legacy 2).yrm").readText())
            assertTrue(root.resolve("Maps").isDirectory)
            assertFalse(root.resolve("Configs/Maps").exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
