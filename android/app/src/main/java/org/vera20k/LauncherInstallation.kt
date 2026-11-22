package org.vera20k

import java.io.File
import java.util.Locale

/** Read-only view of the installation selected by the engine configuration. */
internal data class LauncherInstallation(
    val configuredDirectory: File?,
    val missingArchives: List<String>,
    val iniProfile: String = "yr",
) {
    val ready: Boolean get() = configuredDirectory != null && missingArchives.isEmpty()

    companion object {
        private val requiredArchives = listOf("ra2.mix", "ra2md.mix")
        private val profilePattern = Regex("""^\s*profile\s*=\s*"([^"]+)"\s*(?:#.*)?$""")
        private val valuePattern = Regex("""^\s*ra2_dir\s*=\s*"(.*)"\s*(?:#.*)?$""")

        fun read(filesDirectory: File): LauncherInstallation {
            val config = File(filesDirectory, "Config/config.toml")
            if (!config.isFile) return LauncherInstallation(null, requiredArchives)
            val (configured, profile) = runCatching {
                var inPaths = false
                var inIni = false
                var iniProfile = "yr"
                config.useLines { lines ->
                    var directory: String? = null
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                            inPaths = trimmed == "[paths]"
                            inIni = trimmed == "[ini]"
                        } else if (inPaths) {
                            valuePattern.matchEntire(line)?.groupValues?.get(1)?.let { directory = it }
                        } else {
                            if (inIni) profilePattern.matchEntire(line)?.groupValues?.get(1)?.let { iniProfile = it }
                        }
                    }
                    directory to iniProfile
                }
            }.getOrNull() ?: (null to "yr")
            val gameDirectory = configured?.let(::unquotePath)?.let(::File)?.absoluteFile

            return LauncherInstallation(gameDirectory, missingRequiredArchives(gameDirectory, profile), profile)
        }

        @JvmOverloads
        fun missingRequiredArchives(
            directory: File?,
            profile: String = "yr",
            allowBaseRa2Detection: Boolean = false,
        ): List<String> {
            val present = directory?.takeIf(File::isDirectory)?.listFiles()
                ?.asSequence()
                ?.filter(File::isFile)
                ?.map { it.name.lowercase(Locale.ROOT) }
                ?.toSet()
                .orEmpty()
            val isBaseRa2 = profile.equals("ra2", ignoreCase = true) ||
                (allowBaseRa2Detection && "ra2.mix" in present && "ra2md.mix" !in present)
            val required = if (isBaseRa2) listOf("ra2.mix") else requiredArchives
            return required.filterNot(present::contains)
        }

        private fun unquotePath(value: String): String = value
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
    }
}
