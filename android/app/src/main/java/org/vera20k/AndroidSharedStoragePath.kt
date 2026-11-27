package org.vera20k

import java.io.File
import java.util.Locale

/** Resolves only local primary-volume SAF selections to paths usable by Rust. */
internal object AndroidSharedStoragePath {
    private const val DATA_ROOT_NAME = "RA2Harbor"
    private val existingDataRootNames = listOf(
        DATA_ROOT_NAME,
        "Ra2Harbor",
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
        val existing = existingDataRootNames.firstNotNullOfOrNull { expectedName ->
            documents.listFiles()
                ?.firstOrNull { it.isDirectory && it.name.equals(expectedName, ignoreCase = true) }
        }
        return existing ?: File(documents, DATA_ROOT_NAME)
    }

    /** Default engine data root, reusing the user's established Iron Harbor directory. */
    fun defaultDataRoot(primaryStorage: File): File = sharedDataRoot(primaryStorage)

    /** Return a managed mod path only when the selected folder stays below Mods. */
    fun relativeFolderName(modsRoot: File, selected: File): String? = runCatching {
        val root = modsRoot.canonicalFile.toPath()
        val folder = selected.canonicalFile.toPath()
        if (folder == root || !folder.startsWith(root) || !folder.toFile().isDirectory) return null
        val parts = root.relativize(folder).map { it.toString() }
        if (parts.isEmpty() || parts.any { it.isBlank() || it == "." || it == ".." || '/' in it || '\\' in it }) null
        else parts.joinToString("/")
    }.getOrNull()
}

/** Basic file checks before Rust parses the selected installation's archives. */
internal object GameFolderValidation {
    val requiredArchives = listOf("ra2.mix", "ra2md.mix")

    @JvmOverloads
    fun missingRequiredArchives(
        directory: File,
        profile: String? = null,
        allowBaseRa2Detection: Boolean = false,
    ): List<String> {
        val present = directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.canRead() }
            ?.map { it.name.lowercase(Locale.ROOT) }
            ?.toSet()
            .orEmpty()
        val baseRa2 = profile?.equals("ra2", ignoreCase = true) == true ||
            (allowBaseRa2Detection && "ra2.mix" in present && "ra2md.mix" !in present)
        val required = if (baseRa2) listOf("ra2.mix") else requiredArchives
        return required.filterNot(present::contains)
    }
}
