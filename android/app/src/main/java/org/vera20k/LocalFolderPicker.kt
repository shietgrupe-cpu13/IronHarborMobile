package org.vera20k

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.widget.ArrayAdapter
import android.widget.TextView
import java.io.File

internal fun localFolderRowLabels(entries: List<File?>): List<String> =
    if (entries.isEmpty()) listOf("No subfolders")
    else entries.map { entry -> entry?.name ?: "Parent folder" }

/** Small in-launcher folder browser for local paths the native engine can open directly. */
internal object LocalFolderPicker {
    fun show(
        activity: Activity,
        root: File,
        start: File,
        title: String,
        canUse: (File) -> Boolean,
        onSelected: (File) -> Unit,
    ) {
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return
        val current = runCatching { start.canonicalFile }.getOrNull()
            ?.takeIf { it.isDirectory && it.toPath().startsWith(canonicalRoot.toPath()) }
            ?: canonicalRoot
        val directories = current.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            ?.toList()
            .orEmpty()
        val parent = current.parentFile?.takeIf { current != canonicalRoot && it.toPath().startsWith(canonicalRoot.toPath()) }
        val entries: List<File?> = buildList {
            if (parent != null) add(null)
            addAll(directories)
        }
        // ArrayAdapter binds its items before this custom getView body runs, so
        // its data must not contain the null used internally for the parent row.
        val rowLabels = localFolderRowLabels(entries)
        val adapter = object : ArrayAdapter<String>(activity, android.R.layout.select_dialog_item, rowLabels) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val row = super.getView(position, convertView, parent) as TextView
                val entry = entries.getOrNull(position)
                row.text = rowLabels.getOrNull(position).orEmpty()
                row.textSize = 15f
                row.typeface = Typeface.DEFAULT
                row.minHeight = (48 * activity.resources.displayMetrics.density + 0.5f).toInt()
                val iconId = if (entries.isEmpty() || entry == null) R.drawable.ic_ih_back else R.drawable.ic_ih_files
                val icon = activity.getDrawable(iconId)?.mutate()?.apply {
                    setTint(activity.getColor(R.color.launcher_accent_dark))
                }
                row.compoundDrawablePadding = (14 * activity.resources.displayMetrics.density + 0.5f).toInt()
                row.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                row.contentDescription = row.text
                return row
            }
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("$title\n${current.absolutePath}")
            .setAdapter(adapter) { _, index ->
                if (entries.isEmpty() || index !in entries.indices) return@setAdapter
                val selected = entries[index] ?: parent ?: canonicalRoot
                show(activity, canonicalRoot, selected, title, canUse, onSelected)
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Use this folder") { _, _ -> onSelected(current) }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = runCatching { canUse(current) }.getOrDefault(false)
        }
        dialog.show()
    }
}
