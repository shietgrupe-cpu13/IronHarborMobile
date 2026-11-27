package org.vera20k

import java.io.File
import java.util.Locale

/** Resolves only local primary-volume SAF selections to paths usable by Rust. */
internal object AndroidSharedStoragePath {
    private val existingDataRootNames = listOf(
        "Ra2Harbor",
        "RA2Harbor",
        "IronHarborRA2YR",
        "RA2IRONHARBOR",
    )

    fun fromDocumentId(documentId: String, primaryStorage: File): File? {
        val separator = documentId.indexOf(':')
        if (separator <= 0 || !documentId.substring(0, separator).equals("primary", ignoreCase = true)) {
            return null
        }
        val relative = documentId.substring(separator + 1)
        val parts = relative.split('/')
        if (parts.isEmpty() || parts.any { it.isEmpty() || it == "." || it == ".." || '\u0000' in it }) {
            return null
        }
        return runCatching {
            val root = primaryStorage.canonicalFile
            val target = parts.fold(root) { parent, part -> File(parent, part) }.canonicalFile
            if (target.toPath().startsWith(root.toPath()) && target.isDirectory) target else null
        }.getOrNull()
    }

    /** Reuse an existing Iron Harbor data root before creating the canonical name. */
    fun sharedDataRoot(primaryStorage: File): File {
        val documents = File(primaryStorage, "Documents")
        val existing = existingDataRootNames
            .asSequence()
            .map { File(documents, it) }
            .firstOrNull(File::isDirectory)
        return existing ?: File(documents, "Ra2Harbor")
    }
}

/** Basic file checks before Rust parses the selected installation's archives. */
internal object GameFolderValidation {
    val requiredArchives = listOf("ra2.mix", "ra2md.mix")

    fun missingRequiredArchives(directory: File): List<String> {
        val present = directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.canRead() }
            ?.map { it.name.lowercase(Locale.ROOT) }
            ?.toSet()
            .orEmpty()
        return requiredArchives.filterNot(present::contains)
    }
}
