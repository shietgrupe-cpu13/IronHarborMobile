package org.vera20k

import java.io.File

internal data class CampaignProgress(val completedMissions: Int, val finished: Boolean)

/** Contract shared with the Rust victory handler; status is written only on an actual win. */
internal object LauncherCampaignProgress {
    const val INTENT_EXTRA_OPEN_SELECTION = "org.vera20k.extra.OPEN_CAMPAIGN_SELECTION"
    private const val FILE_NAME = "Campaigns/progress.ini"

    @JvmStatic
    fun read(dataRoot: File): Map<String, CampaignProgress> =
        parse(File(dataRoot, FILE_NAME).takeIf(File::isFile)?.readText().orEmpty())

    @JvmStatic
    fun parse(contents: String): Map<String, CampaignProgress> {
        val missions = mutableMapOf<String, Int>()
        val finished = mutableSetOf<String>()
        contents.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEach
            val (key, value) = line.split('=', limit = 2).takeIf { it.size == 2 } ?: return@forEach
            val separator = key.lastIndexOf('.')
            if (separator <= 0) return@forEach
            val campaign = key.substring(0, separator).lowercase()
            val field = key.substring(separator + 1)
            when (field) {
                "completed" -> missions[campaign] = maxOf(missions[campaign] ?: 0, value.toIntOrNull()?.coerceIn(0, 7) ?: 0)
                "finished" -> if (value == "1") finished += campaign
            }
        }
        return (missions.keys + finished).associateWith { campaign ->
            CampaignProgress(missions[campaign] ?: 0, campaign in finished)
        }
    }

    @JvmStatic
    fun label(progress: CampaignProgress?): String = when {
        progress == null || progress.completedMissions == 0 -> "No completed missions recorded"
        progress.finished -> "Campaign complete · 7/7 missions"
        else -> "${progress.completedMissions}/7 missions completed"
    }
}
