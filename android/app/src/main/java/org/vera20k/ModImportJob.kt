package org.vera20k

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference

/** Stages one SAF tree outside Mods, then atomically publishes the whole folder. */
internal class ModImportJob private constructor() : AutoCloseable {
    @Volatile
    private var cancelled = false

    @Volatile
    private var published = false

    class Cancelled : IOException("Mod import cancelled")

    fun interface Copy {
        @Throws(IOException::class)
        fun into(directory: File)
    }

    @Synchronized
    fun cancel(): Boolean {
        if (published) return false
        cancelled = true
        return true
    }

    @Throws(Cancelled::class)
    fun checkCancelled() {
        if (cancelled) throw Cancelled()
    }

    @Throws(IOException::class)
    fun run(filesDirectory: File, folderName: String, copy: Copy): File {
        checkCancelled()
        val modsDirectory = File(filesDirectory, "Mods")
        if (!modsDirectory.isDirectory && !modsDirectory.mkdirs()) {
            throw IOException("Cannot create the Mods folder")
        }
        val destination = AssetImportJob.child(modsDirectory, folderName)
        if (Files.exists(destination.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("A mod named '$folderName' is already installed; existing files were left untouched")
        }
        val staging = Files.createTempDirectory(filesDirectory.toPath(), ".mod-import-").toFile()
        try {
            copy.into(staging)
            synchronized(this) {
                checkCancelled()
                if (Files.exists(destination.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    throw IOException("A mod named '$folderName' appeared during import; existing files were left untouched")
                }
                Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                published = true
            }
            return destination
        } finally {
            if (!published) AssetImportJob.deleteStagingTree(staging.toPath())
        }
    }

    override fun close() {
        ACTIVE.compareAndSet(this, null)
    }

    companion object {
        private val ACTIVE = AtomicReference<ModImportJob?>(null)

        @JvmStatic
        fun begin(): ModImportJob? {
            val job = ModImportJob()
            return if (ACTIVE.compareAndSet(null, job)) job else null
        }

        @JvmStatic
        fun cancelActive(): Boolean = ACTIVE.get()?.cancel() ?: false

        @JvmStatic
        fun active(): Boolean = ACTIVE.get() != null
    }
}
