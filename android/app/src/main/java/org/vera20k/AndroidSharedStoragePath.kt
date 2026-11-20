package org.vera20k

import java.io.File

/** Converts only Android's primary shared-storage document IDs to real paths. */
internal object AndroidSharedStoragePath {
    fun defaultDataRoot(primaryStorage: File): File = File(File(primaryStorage, "Documents"), "RA2Harbor")

    fun fromDocumentId(documentId: String, primaryStorage: File): File? {
        val separator = documentId.indexOf(':')
        if (separator <= 0 || !documentId.substring(0, separator).equals("primary", ignoreCase = true)) {
            return null
        }
        val relative = documentId.substring(separator + 1)
        val parts = relative.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || '\u0000' in it }) return null

        return runCatching {
            val root = primaryStorage.canonicalFile
            val target = parts.fold(root) { parent, part -> File(parent, part) }.canonicalFile
            if (!target.toPath().startsWith(root.toPath()) || !target.isDirectory) null else target
        }.getOrNull()
    }

    /** Relative mod path only for an existing folder inside the managed Mods root. */
    fun relativeFolderName(modsRoot: File, selected: File): String? = runCatching {
        val root = modsRoot.canonicalFile.toPath()
        val folder = selected.canonicalFile.toPath()
        if (folder == root || !folder.startsWith(root) || !folder.toFile().isDirectory) return null
        val parts = root.relativize(folder).map { it.toString() }
        if (parts.isEmpty() || parts.any { it.isBlank() || it == "." || it == ".." || '/' in it || '\\' in it }) null
        else parts.joinToString("/")
    }.getOrNull()
}
