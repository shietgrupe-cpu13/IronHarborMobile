package org.vera20k

import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.Locale

/** Read-only index of supported map files anywhere below the selected mod. */
internal object LauncherModMapCatalog {
    private val supportedExtensions = setOf("map", "mpr", "mmx", "yrm")

    data class MapFile(val relativePath: String, val group: String) {
        val title: String get() = relativePath.substringAfterLast('/').substringBeforeLast('.')
    }

    fun scan(modDirectory: File): List<MapFile> {
        if (!modDirectory.isDirectory || Files.isSymbolicLink(modDirectory.toPath())) return emptyList()
        val root = runCatching { modDirectory.canonicalFile }.getOrNull() ?: return emptyList()
        val pending = ArrayDeque<Pair<File, Int>>()
        val maps = mutableListOf<MapFile>()
        pending.add(root to 0)
        while (pending.isNotEmpty()) {
            val (directory, depth) = pending.removeFirst()
            if (depth >= 16) continue
            val children = directory.listFiles()?.sortedBy { it.name.lowercase(Locale.ROOT) } ?: continue
            for (child in children) {
                if (Files.isSymbolicLink(child.toPath())) continue
                if (child.isDirectory) {
                    if (!child.name.startsWith('.')) {
                        val canonical = runCatching { child.canonicalFile }.getOrNull()
                        if (canonical != null && canonical.toPath().startsWith(root.toPath())) {
                            pending.add(canonical to depth + 1)
                        }
                    }
                    continue
                }
                if (!child.isFile || child.extension.lowercase(Locale.ROOT) !in supportedExtensions) continue
                val canonical = runCatching { child.canonicalFile }.getOrNull() ?: continue
                if (!canonical.toPath().startsWith(root.toPath())) continue
                val relative = runCatching { root.toPath().relativize(canonical.toPath()).toString() }
                    .getOrNull()?.replace(File.separatorChar, '/') ?: continue
                maps += MapFile(relative, relative.substringBeforeLast('/', "Mod root"))
                if (maps.size >= 10_000) return maps.sortedBy { it.relativePath.lowercase(Locale.ROOT) }
            }
        }
        return maps.sortedBy { it.relativePath.lowercase(Locale.ROOT) }
    }
}
