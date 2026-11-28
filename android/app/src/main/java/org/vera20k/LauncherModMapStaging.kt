package org.vera20k

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.Properties
import java.util.UUID

/** Mirrors only the active mod's loose Choose Map files into the engine Maps folder. */
internal object LauncherModMapStaging {
    private const val MANIFEST = ".ironharbor-mod-maps.properties"
    private const val MAX_ENTRIES = 10_000
    private const val MAX_MANIFEST_ENTRIES = MAX_ENTRIES * 2
    private val engineMapExtensions = setOf("map", "yrm", "mpr")

    data class Result(val activeMaps: Int, val removedMaps: Int, val cleanupPending: Int)

    private data class OwnedMap(val name: String, val sha256: String)
    private data class Manifest(val selectedRoot: String, val activeCount: Int, val maps: List<OwnedMap>)
    private data class PendingCopy(val target: File, val temporary: File)

    /** Cheap restart check; unchanged selections avoid another recursive scan and hash pass. */
    fun isSynchronized(mapsDirectory: File, modDirectory: File?): Boolean = runCatching {
        val manifest = readManifest(File(mapsDirectory.canonicalFile, MANIFEST))
        val expectedRoot = modDirectory?.canonicalFile?.path.orEmpty()
        manifest.selectedRoot == expectedRoot &&
            manifest.activeCount == manifest.maps.size &&
            manifest.maps.all { File(mapsDirectory, it.name).isFile }
    }.getOrDefault(false)

    /**
     * Makes nested loose mod maps visible to the engine's flat Maps scan. Only files
     * listed in our private manifest are eligible for removal; modified or unknown
     * files are treated as user data and preserved.
     */
    fun sync(mapsDirectory: File, modDirectory: File?): Result {
        val mapsRoot = mapsDirectory.canonicalFile
        if (!mapsRoot.isDirectory && !mapsRoot.mkdirs()) {
            throw IOException("Cannot create engine Maps directory: ${mapsRoot.absolutePath}")
        }
        val manifest = File(mapsRoot, MANIFEST)
        val previousManifest = readManifest(manifest)
        val previous = previousManifest.maps
        val previousByName = previous.associateBy(OwnedMap::name)
        val (selectedRoot, desired) = discover(modDirectory)

        val pending = mutableListOf<PendingCopy>()
        val next = mutableListOf<OwnedMap>()
        try {
            for ((source, relativePath) in desired) {
                val digest = sha256(source)
                val identity = sha256("$selectedRoot\u0000$relativePath".toByteArray()).take(12)
                val stem = source.nameWithoutExtension
                    .replace(Regex("[^A-Za-z0-9_-]"), "_")
                    .take(36)
                    .ifBlank { "map" }
                val extension = source.extension.lowercase(Locale.ROOT)
                val base = "ihmod_${identity}_${stem}_${digest.take(12)}.$extension"
                var target = File(mapsRoot, base)
                var collision = 0
                while (target.exists() && previousByName[target.name]?.sha256 != digest) {
                    collision++
                    target = File(mapsRoot, "ihmod_${identity}_${stem}_${digest.take(12)}_$collision.$extension")
                    if (collision > 100) throw IOException("Cannot choose a safe temporary filename for ${source.name}")
                }
                val old = previousByName[target.name]
                if (target.isFile && old?.sha256 == digest && sha256(target) == digest) {
                    next += old
                    continue
                }
                val temporary = File(mapsRoot, ".${target.name}.${UUID.randomUUID()}.partial")
                Files.copy(source.toPath(), temporary.toPath())
                pending += PendingCopy(target, temporary)
                if (sha256(temporary) != digest) throw IOException("Copied map failed verification: ${source.name}")
                next += OwnedMap(target.name, digest)
            }

            // Register both old and new files before exposing the new maps. If the
            // process is interrupted during the following renames, the next sync
            // still knows which generated files it owns.
            val registered = (previous + next).distinctBy(OwnedMap::name)
            if (registered.size > MAX_MANIFEST_ENTRIES) throw IOException("Too many managed maps to safely update the temporary map list")
            writeManifest(manifest, registered, next.size, selectedRoot)
            for (copy in pending) {
                if (copy.target.exists()) throw IOException("Temporary map target unexpectedly exists: ${copy.target.name}")
                moveNewFile(copy.temporary, copy.target)
            }

            var removed = 0
            val nextNames = next.mapTo(mutableSetOf(), OwnedMap::name)
            val stale = previous.filter { it.name !in nextNames }
            val undeleted = mutableListOf<OwnedMap>()
            var cleanupPending = 0
            for (entry in stale) {
                val file = File(mapsRoot, entry.name)
                if (!file.exists()) continue
                if (!file.isFile) continue
                val unchanged = runCatching { sha256(file) == entry.sha256 }.getOrDefault(false)
                if (unchanged && file.delete()) {
                    removed++
                } else if (unchanged) {
                    undeleted += entry
                    cleanupPending++
                }
                // A changed file is no longer ours; preserve it and drop ownership.
            }
            writeManifest(manifest, (next + undeleted).distinctBy(OwnedMap::name), next.size, selectedRoot)
            return Result(next.size, removed, cleanupPending)
        } catch (error: Exception) {
            pending.forEach { it.temporary.delete() }
            // Newly exposed files remain registered in the union manifest so a
            // later unselect can remove them without touching unrelated maps.
            throw error
        }
    }

    private fun discover(modDirectory: File?): Pair<String, List<Pair<File, String>>> {
        if (modDirectory == null) return "" to emptyList()
        val root = modDirectory.canonicalFile
        if (!root.isDirectory) throw IOException("Selected mod folder is unavailable: ${root.absolutePath}")
        val maps = LauncherModMapCatalog.scan(root)
            .asSequence()
            .filter { File(it.relativePath).extension.lowercase(Locale.ROOT) in engineMapExtensions }
            .mapNotNull { map ->
                val source = File(root, map.relativePath).canonicalFile
                if (!source.isFile || !source.toPath().startsWith(root.toPath())) return@mapNotNull null
                source to map.relativePath
            }
            .take(MAX_ENTRIES)
            .toList()
        return root.path to maps
    }

    private fun readManifest(file: File): Manifest {
        if (!file.isFile) return Manifest("", 0, emptyList())
        val properties = Properties()
        FileInputStream(file).use(properties::load)
        if (properties.getProperty("version") != "1") throw IOException("Unrecognized mod-map manifest; files were left untouched")
        val selectedRoot = properties.getProperty("selected_root")
            ?: throw IOException("Incomplete mod-map manifest; files were left untouched")
        val activeCount = properties.getProperty("active_count")?.toIntOrNull()
            ?: throw IOException("Invalid mod-map manifest; files were left untouched")
        val count = properties.getProperty("count")?.toIntOrNull()
            ?: throw IOException("Invalid mod-map manifest; files were left untouched")
        if (count !in 0..MAX_MANIFEST_ENTRIES) throw IOException("Invalid mod-map manifest count; files were left untouched")
        if (activeCount !in 0..count) throw IOException("Invalid active mod-map count; files were left untouched")
        val maps = (0 until count).map { index ->
            val name = properties.getProperty("file.$index") ?: throw IOException("Incomplete mod-map manifest; files were left untouched")
            val digest = properties.getProperty("sha256.$index") ?: throw IOException("Incomplete mod-map manifest; files were left untouched")
            val safeName = name.matches(Regex("ihmod_[A-Za-z0-9_-]+\\.[A-Za-z0-9]+"))
            if (!safeName || digest.length != 64 || digest.any { it !in '0'..'9' && it !in 'a'..'f' }) {
                throw IOException("Unsafe mod-map manifest entry; files were left untouched")
            }
            OwnedMap(name, digest)
        }
        return Manifest(selectedRoot, activeCount, maps)
    }

    private fun writeManifest(file: File, entries: List<OwnedMap>, activeCount: Int, selectedRoot: String) {
        val properties = Properties().apply {
            setProperty("version", "1")
            setProperty("selected_root", selectedRoot)
            setProperty("active_count", activeCount.toString())
            setProperty("count", entries.size.toString())
            entries.forEachIndexed { index, entry ->
                setProperty("file.$index", entry.name)
                setProperty("sha256.$index", entry.sha256)
            }
        }
        val temporary = File(file.parentFile, ".${file.name}.${UUID.randomUUID()}.partial")
        try {
            FileOutputStream(temporary).use { output ->
                properties.store(output, "Iron Harbor generated active-mod map copies")
                output.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun moveNewFile(source: File, target: File) {
        Files.move(source.toPath(), target.toPath())
    }

    private fun sha256(file: File): String = FileInputStream(file).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
