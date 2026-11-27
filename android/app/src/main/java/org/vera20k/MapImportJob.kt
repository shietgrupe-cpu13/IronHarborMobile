package org.vera20k

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/** Stages map imports before publishing them in the engine's scanned Maps folder. */
internal class MapImportJob private constructor() : AutoCloseable {
    @Volatile private var cancelled = false

    class Cancelled : IOException("Map import cancelled")

    data class Source(val name: String, val copyTo: (File) -> Unit)

    @Synchronized
    fun cancel(): Boolean {
        cancelled = true
        return true
    }

    fun checkCancelled() {
        if (cancelled) throw Cancelled()
    }

    @Throws(IOException::class)
    fun run(directory: File, sources: List<Source>, onImported: (Int) -> Unit): List<File> {
        if (sources.isEmpty()) return emptyList()
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create the engine's Maps folder")
        }
        val root = directory.canonicalFile
        val imported = mutableListOf<File>()
        sources.forEach { source ->
            checkCancelled()
            val name = safeMapName(source.name)
            val staging = File.createTempFile(".vera-map-", ".part", root)
            try {
                source.copyTo(staging)
                checkCancelled()
                if (staging.length() == 0L) throw IOException("${source.name} is empty")
                val destination = uniqueDestination(root, name)
                // The default move contract refuses an existing target; this
                // keeps a late collision from replacing a user's map.
                Files.move(staging.toPath(), destination.toPath())
                imported += destination
                onImported(imported.size)
            } finally {
                Files.deleteIfExists(staging.toPath())
            }
        }
        return imported
    }

    override fun close() {
        ACTIVE.compareAndSet(this, null)
    }

    companion object {
        private val ACTIVE = AtomicReference<MapImportJob?>(null)
        private val EXTENSIONS = setOf("mmx", "yro", "map", "mpr", "yrm")

        @JvmStatic fun begin(): MapImportJob? {
            val job = MapImportJob()
            return if (ACTIVE.compareAndSet(null, job)) job else null
        }

        @JvmStatic fun cancelActive(): Boolean = ACTIVE.get()?.cancel() ?: false

        @JvmStatic
        @Throws(IOException::class)
        fun safeMapName(providerName: String): String {
            val name = providerName.trim()
            if (name.isEmpty() || name == "." || name == ".." || name.contains('/') ||
                name.contains('\\') || name.contains('\u0000') || File(name).isAbsolute
            ) throw IOException("The selected document has an unsafe filename")
            val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (extension !in EXTENSIONS) {
                throw IOException("Unsupported map file '$name'. Supported: ${EXTENSIONS.sorted().joinToString()}")
            }
            return name
        }

        @JvmStatic
        @Throws(IOException::class)
        fun uniqueDestination(directory: File, name: String): File {
            val safeName = safeMapName(name)
            var candidate = File(directory, safeName)
            if (!candidate.exists()) return candidate
            val extension = safeName.substringAfterLast('.')
            val stem = safeName.dropLast(extension.length + 1)
            var suffix = 2
            do {
                candidate = File(directory, "$stem ($suffix).$extension")
                suffix++
            } while (candidate.exists())
            return candidate
        }
    }
}
