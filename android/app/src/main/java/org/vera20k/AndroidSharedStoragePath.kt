package org.vera20k

import java.io.File

/** Converts only Android's primary shared-storage document IDs to real paths. */
internal object AndroidSharedStoragePath {
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
}
