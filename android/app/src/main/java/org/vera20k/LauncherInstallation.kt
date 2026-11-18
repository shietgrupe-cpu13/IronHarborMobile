package org.vera20k

import java.io.File
import java.util.Locale

/** Read-only view of the installation selected by the engine configuration. */
internal data class LauncherInstallation(
    val configuredDirectory: File?,
    val missingArchives: List<String>,
) {
    val ready: Boolean get() = configuredDirectory != null && missingArchives.isEmpty()

    companion object {
        private val requiredArchives = listOf("ra2.mix", "ra2md.mix")
        private val valuePattern = Regex("""^\s*ra2_dir\s*=\s*"(.*)"\s*(?:#.*)?$""")

        fun read(filesDirectory: File): LauncherInstallation {
            val config = File(filesDirectory, "config.toml")
            if (!config.isFile) return LauncherInstallation(null, requiredArchives)
            val configured = runCatching {
                var inPaths = false
                config.useLines { lines ->
                    lines.firstNotNullOfOrNull { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                            inPaths = trimmed == "[paths]"
                            null
                        } else if (inPaths) {
                            valuePattern.matchEntire(line)?.groupValues?.get(1)
                        } else null
                    }
                }
            }.getOrNull()?.let(::unquotePath)?.let(::File)?.absoluteFile

            val present = configured?.listFiles()
                ?.asSequence()
                ?.filter(File::isFile)
                ?.map { it.name.lowercase(Locale.ROOT) }
                ?.toSet()
                .orEmpty()
            return LauncherInstallation(configured, requiredArchives.filterNot(present::contains))
        }

        private fun unquotePath(value: String): String = value
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
    }
}
