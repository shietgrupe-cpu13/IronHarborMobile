package org.vera20k

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

internal enum class LauncherIniProfile(val configValue: String, val title: String) {
    YR("yr", "Yuri's Revenge"),
    MO("mo", "Mental Omega"),
    AUTO("auto", "Auto-detect INI roots"),
    CUSTOM("custom", "Custom");

    companion object {
        fun fromConfig(value: String?): LauncherIniProfile =
            entries.firstOrNull { it.configValue == value }
                ?: throw IOException("Unknown INI profile '$value'")
    }
}

internal data class LauncherModCatalog(
    val installedMods: List<String>,
    val selectedMod: String?,
    val profile: LauncherIniProfile,
    val configError: String?,
) {
    fun containsSelectedMod(): Boolean =
        selectedMod == null || installedMods.any { it.equals(selectedMod, ignoreCase = true) }

    companion object {
        /** Mental Omega needs its MO archive family and root names; never silently pair it with YR roots. */
        @JvmStatic
        fun requiresMoProfile(modName: String): Boolean =
            modName.contains("mentalomega", ignoreCase = true)

        @JvmStatic
        fun read(filesDirectory: File): LauncherModCatalog {
            val installed = File(filesDirectory, "Mods").listFiles()
                ?.asSequence()
                ?.filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) }
                ?.map(File::getName)
                ?.sortedBy { it.lowercase(Locale.ROOT) }
                ?.toList()
                .orEmpty()
            val config = File(filesDirectory, "config.toml")
            if (!config.isFile) {
                return LauncherModCatalog(installed, null, LauncherIniProfile.YR, null)
            }
            return try {
                var section = ""
                var selectedMod: String? = null
                var profile: LauncherIniProfile? = null
                val seen = mutableSetOf<Pair<String, String>>()
                config.useLines { lines ->
                    lines.forEachIndexed { index, raw ->
                        val line = raw.trim().removePrefix("\uFEFF")
                        if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed
                        if (line.startsWith('[')) {
                            val header = line.substringBefore('#').trim()
                            if (!header.endsWith(']')) throw IOException("Invalid config section at line ${index + 1}")
                            section = header.substring(1, header.length - 1).trim()
                            return@forEachIndexed
                        }
                        val (key, rawValue) = line.split('=', limit = 2).let {
                            if (it.size != 2) throw IOException("Invalid config value at line ${index + 1}")
                            it[0].trim() to it[1].trim()
                        }
                        if ((section == "paths" && key == "mod") || (section == "ini" && key == "profile")) {
                            if (!seen.add(section to key)) throw IOException("Duplicate [$section] $key")
                            val value = readString(rawValue)
                            if (section == "paths") selectedMod = value.takeIf(String::isNotEmpty)
                            else profile = LauncherIniProfile.fromConfig(value)
                        }
                    }
                }
                LauncherModCatalog(installed, selectedMod, profile ?: LauncherIniProfile.YR, null)
            } catch (error: Exception) {
                LauncherModCatalog(installed, null, LauncherIniProfile.YR, error.message ?: "Cannot read config.toml")
            }
        }

        private fun readString(value: String): String {
            if (value.startsWith('\'')) {
                val end = value.indexOf('\'', startIndex = 1)
                if (end < 0 || value.substring(end + 1).trim().let { it.isNotEmpty() && !it.startsWith('#') }) {
                    throw IOException("Invalid literal string in config.toml")
                }
                return value.substring(1, end)
            }
            if (!value.startsWith('"')) throw IOException("Expected a quoted string in config.toml")
            val result = StringBuilder()
            var index = 1
            while (index < value.length) {
                when (val character = value[index++]) {
                    '"' -> {
                        if (value.substring(index).trim().let { it.isNotEmpty() && !it.startsWith('#') }) {
                            throw IOException("Invalid text after config string")
                        }
                        return result.toString()
                    }
                    '\\' -> {
                        if (index >= value.length) throw IOException("Incomplete config escape")
                        when (val escaped = value[index++]) {
                            '"', '\\' -> result.append(escaped)
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000c')
                            'u', 'U' -> {
                                val digits = if (escaped == 'u') 4 else 8
                                if (index + digits > value.length) throw IOException("Incomplete Unicode escape")
                                val codePoint = value.substring(index, index + digits).toLongOrNull(16)
                                    ?: throw IOException("Invalid Unicode escape")
                                if (codePoint > 0x10ffff) throw IOException("Invalid Unicode code point")
                                result.appendCodePoint(codePoint.toInt())
                                index += digits
                            }
                            else -> throw IOException("Unsupported config escape")
                        }
                    }
                    else -> result.append(character)
                }
            }
            throw IOException("Unterminated config string")
        }
    }
}

/** Publishes exactly the config text emitted by the Rust engine's editor. */
internal object LauncherConfigPublisher {
    @JvmStatic
    @Throws(IOException::class)
    fun publish(filesDirectory: File, contents: String) {
        val parent = filesDirectory.toPath()
        val temporary = Files.createTempFile(parent, ".config-selection-", ".tmp")
        try {
            FileOutputStream(temporary.toFile()).use { output ->
                output.write(contents.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(
                temporary,
                File(filesDirectory, "config.toml").toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
