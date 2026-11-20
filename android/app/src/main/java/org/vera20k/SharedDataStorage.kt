package org.vera20k

import android.content.Context
import android.os.Environment
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/** User-owned Documents/IronHarborRA2YR tree plus the filesystem mirror used by Rust. */
internal class SharedDataStorage(private val context: Context) {
    private val resolver = context.contentResolver

    fun treeUri(): Uri? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(TREE_URI, null)?.let(Uri::parse)

    fun rootUri(): Uri {
        val tree = treeUri() ?: throw IOException("Choose the Documents folder first")
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val selected = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val selectedName = displayName(tree, rootId)
        val root = if (selectedName.equals(ROOT_NAME, ignoreCase = true)) {
            selected
        } else {
            findChild(tree, rootId, ROOT_NAME)
                ?: migrateLegacyRoot(tree, rootId, selected)
                ?: createDirectory(tree, selected, ROOT_NAME)
        }
        return root
    }

    /** Store the chosen original game tree; the engine gets a private path mirror. */
    fun setGameSource(tree: Uri, displayName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(GAME_SOURCE_URI, tree.toString())
            .putString(GAME_SOURCE_NAME, safeName(displayName))
            .remove(ACTIVE_GAME)
            .apply()
    }

    fun clearGameSource() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(GAME_SOURCE_URI)
            .remove(GAME_SOURCE_NAME)
            .remove(ACTIVE_GAME)
            .apply()
    }

    fun gameSourceTree(): Uri? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(GAME_SOURCE_URI, null)?.let(Uri::parse)

    fun gameSourceName(): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(GAME_SOURCE_NAME, null)

    /** A SAF tree can be passed to Rust only when it maps to a granted real path. */
    fun directPathForTree(tree: Uri): File? {
        if (!hasDirectSharedStorageAccess()) return null
        return primaryPathForTree(tree)
    }

    fun hasDirectSharedStorageAccess(): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    /** Resolve the selected primary-volume folder even after access is revoked, to prevent self-mirroring. */
    fun primaryGameSourceMatches(destination: File): Boolean {
        val tree = gameSourceTree() ?: return false
        val direct = primaryPathForTree(tree) ?: return false
        return runCatching { direct.canonicalFile == destination.canonicalFile }.getOrDefault(false)
    }

    private fun primaryPathForTree(tree: Uri): File? {
        if (tree.authority != "com.android.externalstorage.documents") return null
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        return AndroidSharedStoragePath.fromDocumentId(id, Environment.getExternalStorageDirectory())
    }

    /** Mirror a SAF source into the filesystem directory consumed by Rust. */
    fun mirrorGameSource(destination: File) {
        val tree = gameSourceTree() ?: throw IOException("Locate your game folder again")
        try {
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val document = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            mirrorDocumentDirectory(document, destination, tree)
        } catch (error: SecurityException) {
            throw IOException("Game-folder access was revoked. Locate the folder again.", error)
        }
    }

    fun ensureLayout() {
        val root = rootUri()
        listOf("Mods", "Maps", "Saves", "Logs", "AresSupport").forEach { ensureChild(root, it) }
    }

    fun setActiveGameFolder(name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ACTIVE_GAME, safeName(name)).apply()
    }

    fun activeGameFolder(): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(ACTIVE_GAME, null)

    fun displayLocation(): String {
        val tree = treeUri() ?: return "Not selected"
        val selectedId = DocumentsContract.getDocumentId(rootUri())
        return "Documents/${displayName(tree, selectedId)}"
    }

    fun copyLocalDirectory(area: String, source: File, childName: String? = null) {
        val parent = ensureChild(rootUri(), area)
        val target = if (childName == null) {
            if (children(treeUri()!!, DocumentsContract.getDocumentId(parent)).isNotEmpty()) {
                throw IOException("Documents/$ROOT_NAME/$area already has files; existing user data was left unchanged")
            }
            parent
        } else {
            if (findChild(treeUri()!!, DocumentsContract.getDocumentId(parent), childName) != null) {
                throw IOException("Documents/$ROOT_NAME/$area/$childName already exists; existing user data was left unchanged")
            }
            createDirectory(treeUri()!!, parent, safeName(childName))
        }
        try {
            if (childName == null) {
                copyDirectoryContentsToDocument(source, target)
            } else {
                copyDirectoryContentsToDocument(source, target)
            }
        } catch (error: Throwable) {
            if (childName != null) runCatching { DocumentsContract.deleteDocument(resolver, target) }
            else runCatching { deleteChildren(target) }
            throw error
        }
    }

    fun copyLocalFile(area: String, source: File) {
        val parent = ensureChild(rootUri(), area)
        val tree = treeUri()!!
        val safe = safeName(source.name)
        if (findChild(tree, DocumentsContract.getDocumentId(parent), safe) != null) {
            throw IOException("Documents/$ROOT_NAME/$area/$safe already exists")
        }
        val target = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", safe)
            ?: throw IOException("Cannot create Documents file $safe")
        try {
            resolver.openOutputStream(target, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, 1 shl 16) }
            } ?: throw IOException("Cannot write Documents file $safe")
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw error
        }
    }

    /** Store one user-selected support archive without touching any mod folder. */
    fun copyDocumentFile(area: String, source: Uri): String {
        val name = resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: throw IOException("Cannot read the selected file name")
        val safe = safeName(name)
        if (area.equals("AresSupport", ignoreCase = true) && !safe.equals("ares.mix", ignoreCase = true)) {
            throw IOException("Choose the file named ares.mix")
        }
        val parent = ensureChild(rootUri(), area)
        val tree = treeUri()!!
        if (findChildFile(tree, DocumentsContract.getDocumentId(parent), safe) != null) {
            throw IOException("Documents/$ROOT_NAME/$area/$safe already exists; remove it first if you want to replace it")
        }
        val target = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", safe)
            ?: throw IOException("Cannot create Documents file $safe")
        try {
            resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(target, "w")?.use { output -> input.copyTo(output, 1 shl 16) }
                    ?: throw IOException("Cannot write Documents file $safe")
            } ?: throw IOException("Cannot read the selected file")
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw error
        }
        return safe
    }

    /** Publishes new or locally newer files without deleting user-managed documents. */
    fun publishLocalFolder(area: String, source: File) {
        val destination = ensureChild(rootUri(), area)
        publishDirectory(source, destination)
    }

    /** Refreshes an engine-readable folder from its editable Documents copy. */
    fun mirrorFolder(area: String, destination: File) {
        val parent = ensureChild(rootUri(), area)
        mirrorDocumentDirectory(parent, destination, treeUri()!!)
    }

    /** Lists real mod roots in the user-managed SAF folder without copying their contents. */
    fun listModFolders(maxDepth: Int = 4): List<String> {
        if (maxDepth < 1 || treeUri() == null) return emptyList()
        val tree = treeUri()!!
        val mods = ensureChild(rootUri(), "Mods")
        val result = linkedSetOf<String>()
        fun visit(document: Uri, relative: String, depth: Int) {
            if (depth > maxDepth) return
            val entries = children(tree, DocumentsContract.getDocumentId(document))
            val files = entries.filter { it.mime != DocumentsContract.Document.MIME_TYPE_DIR }
            if (relative.isNotEmpty() && (files.isNotEmpty() || entries.isEmpty())) result += relative
            if (depth == maxDepth) return
            entries.asSequence()
                .filter { it.mime == DocumentsContract.Document.MIME_TYPE_DIR && !it.name.startsWith(".") }
                .forEach { entry ->
                    val name = safeName(entry.name)
                    val child = DocumentsContract.buildDocumentUriUsingTree(tree, entry.id)
                    val path = if (relative.isEmpty()) name else "$relative/$name"
                    visit(child, path, depth + 1)
                }
        }
        visit(mods, "", 0)
        return result.sortedBy { it.lowercase(java.util.Locale.ROOT) }
    }

    /** Mirrors one selected nested SAF folder into its engine path, never the whole Mods tree. */
    fun mirrorRelativeFolder(area: String, relativePath: String, destination: File) {
        val segments = relativePath.split('/')
        if (segments.isEmpty() || segments.size > 4 || segments.any { safeName(it) != it }) {
            throw IOException("Unsafe folder path")
        }
        val base = ensureChild(rootUri(), safeName(area))
        val tree = treeUri()!!
        var current = base
        for (segment in segments) {
            current = findChild(tree, DocumentsContract.getDocumentId(current), segment)
                ?: throw IOException("Documents/$ROOT_NAME/$area/$relativePath is missing")
        }
        val engineRoot = File(context.filesDir, safeName(area)).canonicalFile.toPath()
        val engineDestination = destination.canonicalFile
        if (!engineDestination.toPath().startsWith(engineRoot) || engineDestination.toPath() == engineRoot) {
            throw IOException("Engine mirror path is outside the managed $area folder")
        }
        var path = engineDestination.toPath()
        while (path != engineRoot && path.startsWith(engineRoot)) {
            if (java.nio.file.Files.isSymbolicLink(path)) throw IOException("Symbolic link in engine mirror path")
            path = path.parent ?: break
        }
        mirrorDocumentDirectory(current, engineDestination, tree)
    }
    fun mirrorChildFolder(area: String, childName: String, destination: File) {
        val parent = ensureChild(rootUri(), area)
        val tree = treeUri()!!
        val child = findChild(tree, DocumentsContract.getDocumentId(parent), childName)
            ?: throw IOException("Documents/$ROOT_NAME/$area/$childName is missing")
        mirrorDocumentDirectory(child, destination, tree)
    }

    fun mirrorMods(destination: File) {
        val parent = ensureChild(rootUri(), "Mods")
        mirrorDocumentDirectory(parent, destination, treeUri()!!)
    }

    private fun mirrorDocumentDirectory(document: Uri, target: File, tree: Uri) {
        if (!target.isDirectory && !target.mkdirs()) throw IOException("Cannot create engine mirror ${target.absolutePath}")
        val entries = children(tree, DocumentsContract.getDocumentId(document))
        val present = HashSet<String>()
        for (entry in entries) {
            val (id, name, mime, size, modified) = entry
            safeName(name)
            present.add(name)
            val destination = AssetImportJob.child(target, name)
            val childUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                if (destination.exists() && !destination.isDirectory) destination.delete()
                mirrorDocumentDirectory(childUri, destination, tree)
                continue
            }
            if (destination.isDirectory) AssetImportJob.deleteStagingTree(destination.toPath())
            if (destination.isFile && size >= 0 && destination.length() == size &&
                modified > 0 && destination.lastModified() >= modified
            ) continue
            val temporary = File(target, ".${name}.${UUID.randomUUID()}.tmp")
            try {
                resolver.openInputStream(childUri)?.use { input ->
                    FileOutputStream(temporary).use { output ->
                        input.copyTo(output, 1 shl 16)
                        output.fd.sync()
                    }
                } ?: throw IOException("Cannot read Documents file $name")
                if (size >= 0 && temporary.length() != size) throw IOException("Incomplete Documents copy for $name")
                if (!temporary.renameTo(destination)) throw IOException("Cannot publish engine mirror file $name")
                if (modified > 0) destination.setLastModified(modified)
            } finally {
                temporary.delete()
            }
        }
        target.listFiles()?.forEach { local ->
            if (local.name !in present && !local.name.endsWith(".tmp")) {
                if (local.isDirectory) AssetImportJob.deleteStagingTree(local.toPath()) else local.delete()
            }
        }
    }

    private fun copyDirectoryContentsToDocument(source: File, target: Uri) {
        val files = source.listFiles() ?: throw IOException("Cannot read ${source.absolutePath}")
        for (file in files) {
            safeName(file.name)
            if (file.isDirectory) {
                val child = createDirectory(treeUri()!!, target, file.name)
                copyDirectoryContentsToDocument(file, child)
            } else if (file.isFile) {
                val child = DocumentsContract.createDocument(
                    resolver,
                    target,
                    "application/octet-stream",
                    file.name,
                ) ?: throw IOException("Cannot create Documents file ${file.name}")
                resolver.openOutputStream(child, "w")?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output, 1 shl 16) }
                } ?: throw IOException("Cannot write Documents file ${file.name}")
            }
        }
    }

    private fun publishDirectory(source: File, target: Uri) {
        val tree = treeUri() ?: throw IOException("Choose the Documents folder first")
        val files = source.listFiles() ?: return
        for (file in files) {
            safeName(file.name)
            val existing = children(tree, DocumentsContract.getDocumentId(target))
                .firstOrNull { it.name.equals(file.name, ignoreCase = true) }
            if (file.isDirectory) {
                val child = if (existing == null) createDirectory(tree, target, file.name) else {
                    if (existing.mime != DocumentsContract.Document.MIME_TYPE_DIR) {
                        throw IOException("Documents/${file.name} is a file, but the engine produced a folder")
                    }
                    DocumentsContract.buildDocumentUriUsingTree(tree, existing.id)
                }
                publishDirectory(file, child)
            } else if (file.isFile) {
                if (existing != null && existing.mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    throw IOException("Documents/${file.name} is a folder, but the engine produced a file")
                }
                if (existing != null && existing.modified >= file.lastModified() && existing.size == file.length()) continue
                val child = if (existing == null) {
                    DocumentsContract.createDocument(resolver, target, "application/octet-stream", file.name)
                        ?: throw IOException("Cannot create Documents file ${file.name}")
                } else {
                    DocumentsContract.buildDocumentUriUsingTree(tree, existing.id)
                }
                resolver.openOutputStream(child, "wt")?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output, 1 shl 16) }
                } ?: throw IOException("Cannot write Documents file ${file.name}")
            }
        }
    }

    private fun ensureChild(parent: Uri, name: String): Uri {
        val tree = treeUri() ?: throw IOException("Choose the Documents folder first")
        val id = DocumentsContract.getDocumentId(parent)
        return findChild(tree, id, name) ?: createDirectory(tree, parent, name)
    }

    private fun findChild(tree: Uri, parentId: String, name: String): Uri? =
        children(tree, parentId).firstOrNull { (id, childName, mime) ->
            mime == DocumentsContract.Document.MIME_TYPE_DIR && childName.equals(name, ignoreCase = true)
        }?.let { (id, _, _) -> DocumentsContract.buildDocumentUriUsingTree(tree, id) }

    private fun findChildFile(tree: Uri, parentId: String, name: String): Uri? =
        children(tree, parentId).firstOrNull { (_, childName, mime) ->
            mime != DocumentsContract.Document.MIME_TYPE_DIR && childName.equals(name, ignoreCase = true)
        }?.let { (id, _, _) -> DocumentsContract.buildDocumentUriUsingTree(tree, id) }

    private fun createDirectory(tree: Uri, parent: Uri, name: String): Uri =
        DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, safeName(name))
            ?: throw IOException("Cannot create Documents folder $name")

    /** Rename the old app-owned root in place; retain it unchanged if unsupported. */
    private fun migrateLegacyRoot(tree: Uri, parentId: String, parent: Uri): Uri? {
        val legacy = findChild(tree, parentId, LEGACY_ROOT_NAME) ?: return null
        return runCatching { DocumentsContract.renameDocument(resolver, legacy, ROOT_NAME) }
            .getOrNull()
            ?: legacy
    }

    private fun deleteChildren(folder: Uri) {
        val tree = treeUri()!!
        children(tree, DocumentsContract.getDocumentId(folder)).forEach { entry ->
            val child = DocumentsContract.buildDocumentUriUsingTree(tree, entry.id)
            if (entry.mime == DocumentsContract.Document.MIME_TYPE_DIR) deleteChildren(child)
            DocumentsContract.deleteDocument(resolver, child)
        }
    }

    private fun displayName(tree: Uri, id: String): String {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
        }
        return ""
    }

    private data class Entry(val id: String, val name: String, val mime: String, val size: Long, val modified: Long)

    private fun children(tree: Uri, parentId: String): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val result = mutableListOf<Entry>()
        resolver.query(uri, columns, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                result.add(Entry(
                    cursor.getString(idColumn),
                    cursor.getString(nameColumn) ?: continue,
                    cursor.getString(mimeColumn) ?: "application/octet-stream",
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else -1,
                    if (modifiedColumn >= 0 && !cursor.isNull(modifiedColumn)) cursor.getLong(modifiedColumn) else 0,
                ))
            }
        } ?: throw IOException("Cannot list Documents folder")
        return result
    }

    private fun safeName(name: String): String {
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') || name.contains('\\') || name.contains('\u0000')) {
            throw IOException("Unsafe Documents name")
        }
        return name
    }

    companion object {
        private const val PREFS = "ironharbor_user_storage"
        private const val TREE_URI = "documents_tree_uri"
        private const val ACTIVE_GAME = "active_game_folder"
        private const val GAME_SOURCE_URI = "game_source_tree_uri"
        private const val GAME_SOURCE_NAME = "game_source_name"
        private const val ROOT_NAME = "IronHarborRA2YR"
        private const val LEGACY_ROOT_NAME = "RA2IRONHARBOR"

        fun setTree(context: Context, tree: Uri) {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previous = preferences.getString(TREE_URI, null)
            preferences.edit().putString(TREE_URI, tree.toString()).apply {
                if (previous != tree.toString()) remove(ACTIVE_GAME)
            }.apply()
        }
    }
}
