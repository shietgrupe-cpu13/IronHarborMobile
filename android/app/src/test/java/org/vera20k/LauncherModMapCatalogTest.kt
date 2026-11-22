package org.vera20k

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LauncherModMapCatalogTest {
    @Test
    fun discoversMapsRecursivelyWithoutFlatteningDuplicateNames() {
        val root = Files.createTempDirectory("ih-mod-maps").toFile()
        try {
            val first = root.resolve("MapsMO/Standard/arena.MAP")
            val second = root.resolve("Community/Maps/arena.map")
            first.parentFile.mkdirs()
            second.parentFile.mkdirs()
            first.writeText("map one")
            second.writeText("map two")
            root.resolve("readme.txt").writeText("not a map")

            val maps = LauncherModMapCatalog.scan(root)
            assertEquals(
                listOf("Community/Maps/arena.map", "MapsMO/Standard/arena.MAP"),
                maps.map { it.relativePath },
            )
            assertEquals(listOf("Community/Maps", "MapsMO/Standard"), maps.map { it.group })
            assertEquals(listOf("arena", "arena"), maps.map { it.title })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun doesNotFollowSymbolicLinksOutsideSelectedMod() {
        val parent = Files.createTempDirectory("ih-mod-map-link").toFile()
        try {
            val root = parent.resolve("mod").apply { mkdirs() }
            val outside = parent.resolve("outside").apply { mkdirs() }
            outside.resolve("outside.map").writeText("outside")
            runCatching {
                Files.createSymbolicLink(root.resolve("linked").toPath(), outside.toPath())
            }.onFailure { return }

            assertTrue(LauncherModMapCatalog.scan(root).isEmpty())
        } finally {
            parent.deleteRecursively()
        }
    }
}
