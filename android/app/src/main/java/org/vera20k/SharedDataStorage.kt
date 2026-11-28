package org.vera20k

import android.content.Context
import android.os.Environment
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.UUID

/** User-owned Documents/RA2Harbor tree used directly by the Rust engine. */
internal class SharedDataStorage(private val context: Context) {
    private val resolver = context.contentResolver

    init {
        migrateCompatiblePreferences()
    }

    fun treeUri(): Uri? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(TREE_URI, null)?.let(Uri::parse)

    fun rootUri(): Uri {
        val tree = treeUri() ?: throw IOException("Choose the Documents folder first")
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val selected = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val selectedName = displayName(tree, rootId)
        val root = if (selectedName.equals(ROOT_NAME, ignoreCase = true)) {
            selected
        } else if (LEGACY_ROOT_NAMES.any { selectedName.equals(it, ignoreCase = true) }) {
            // The user selected the legacy folder itself rather than Documents.
            // Use that tree in place; never create a nested replacement inside it.
            selected
        } else {
            findChild(tree, rootId, ROOT_NAME)
                ?: migrateLegacyRoot(tree, rootId)
                ?: createDirectory(tree, selected, ROOT_NAME)
        }
        return root
    }

    /** The filesystem path used by the engine; content:// URIs are never passed to Rust. */
    fun directRootPath(): File? {
        if (!hasDirectSharedStorageAccess()) return null
        val primary = Environment.getExternalStorageDirectory()
        val root = treeUri()?.let { tree ->
            val rootId = runCatching { DocumentsContract.getDocumentId(rootUri()) }.getOrNull()
            rootId?.let { AndroidSharedStoragePath.fromDocumentId(it, primary) }
        } ?: if (treeUri() == null) {
            AndroidSharedStoragePath.defaultDataRoot(primary)
        } else {
            // A revoked or unresolvable previously selected tree must not silently
            // redirect saves and configuration into a different directory.
            return null
        }
        if (!root.exists() && !root.mkdirs()) return null
        if (!root.isDirectory) return null
        listOf("Saves", "Maps", "Mods", "Logs", "Config", "Mods/AresSupport").forEach { name ->
            val child = File(root, name)
            if (!child.isDirectory && !child.mkdirs() && !child.isDirectory) return null
        }
        return root
    }

    /** Store the chosen original game tree; the engine reads its shared path directly. */
    fun setGameSource(tree: Uri, displayName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(GAME_SOURCE_URI, tree.toString())
            .putString(GAME_SOURCE_NAME, safeName(displayName))
            .remove(GAME_SOURCE_PATH)
            .remove(ACTIVE_GAME)
            .apply()
    }

    fun setGameSourcePath(directory: File) {
        val canonical = directory.canonicalFile
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(GAME_SOURCE_PATH, canonical.path)
            .putString(GAME_SOURCE_NAME, safeName(canonical.name))
            .remove(GAME_SOURCE_URI)
            .remove(ACTIVE_GAME)
            .apply()
    }

    fun clearGameSource() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(GAME_SOURCE_URI)
            .remove(GAME_SOURCE_PATH)
            .remove(GAME_SOURCE_NAME)
            .remove(ACTIVE_GAME)
            .apply()
    }

    fun gameSourceTree(): Uri? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(GAME_SOURCE_URI, null)?.let(Uri::parse)

    fun gameSourcePath(): File? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(GAME_SOURCE_PATH, null)?.let(::File)

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
        val directPath = gameSourcePath()
        if (directPath != null) {
            return runCatching { directPath.canonicalFile == destination.canonicalFile }.getOrDefault(false)
        }
        val tree = gameSourceTree() ?: return false
        val direct = primaryPathForTree(tree) ?: return false
        return runCatching { direct.canonicalFile == destination.canonicalFile }.getOrDefault(false)
    }

    private fun primaryPathForTree(tree: Uri): File? {
        if (tree.authority != "com.android.externalstorage.documents") return null
        val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        return AndroidSharedStoragePath.fromDocumentId(id, Environment.getExternalStorageDirectory())
    }

    /** Legacy mirror helper retained for migrating older installs. Game launch no longer calls it. */
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
        if (treeUri() == null) {
            val root = directRootPath() ?: throw IOException("Grant All files access to prepare Documents/$ROOT_NAME")
            migrateLegacyMaps(root)
            return
        }
        val root = rootUri()
        listOf("Mods", "Maps", "Saves", "Logs", "Config", "Mods/AresSupport").forEach { ensureArea(it) }
        directRootPath()?.let(::migrateLegacyMaps)
    }

    fun setActiveGameFolder(name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ACTIVE_GAME, safeName(name)).apply()
    }

    fun activeGameFolder(): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(ACTIVE_GAME, null)

    fun setExternalModPath(path: String?) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (path == null) edit.remove(ACTIVE_EXTERNAL_MOD_PATH)
        else edit.putString(ACTIVE_EXTERNAL_MOD_PATH, File(path).canonicalPath)
        edit.apply()
    }

    fun externalModPath(): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(ACTIVE_EXTERNAL_MOD_PATH, null)

    fun displayLocation(): String {
        val tree = treeUri() ?: return "Documents/$ROOT_NAME"
        val selectedId = DocumentsContract.getDocumentId(rootUri())
        return "Documents/${displayName(tree, selectedId)}"
    }

    /** Old roots that still contain data are left untouched if they could not be renamed. */
    fun retainedLegacyRoots(): List<String> {
        val tree = treeUri() ?: return emptyList()
        val parentId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return emptyList()
        return LEGACY_ROOT_NAMES.filter { findChild(tree, parentId, it) != null }
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
        if (treeUri() == null) {
            val root = directRootPath() ?: throw IOException("Grant All files access to write in Documents/$ROOT_NAME")
            val safe = safeName(source.name)
            val directory = File(root, area).canonicalFile
            if (!directory.toPath().startsWith(root.canonicalFile.toPath())) throw IOException("Unsafe destination folder")
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create Documents/$ROOT_NAME/$area")
            val destination = File(directory, safe)
            if (destination.exists()) throw IOException("Documents/$ROOT_NAME/$area/$safe already exists")
            val temporary = File(directory, ".$safe.${UUID.randomUUID()}.tmp")
            try {
                source.inputStream().use { input ->
                    FileOutputStream(temporary).use { output ->
                        input.copyTo(output, 1 shl 16)
                        output.fd.sync()
                    }
                }
                if (!temporary.renameTo(destination)) throw IOException("Cannot publish $safe")
            } finally {
                temporary.delete()
            }
            return
        }
        val parent = ensureArea(area)
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
        if (area.endsWith("AresSupport", ignoreCase = true) && !safe.equals("ares.mix", ignoreCase = true)) {
            throw IOException("Choose the file named ares.mix")
        }
        if (treeUri() == null) {
            val root = directRootPath() ?: throw IOException("Grant All files access to write in Documents/$ROOT_NAME")
            val directory = File(root, area).canonicalFile
            if (!directory.toPath().startsWith(root.canonicalFile.toPath())) throw IOException("Unsafe destination folder")
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create Documents/$ROOT_NAME/$area")
            val destination = File(directory, safe)
            if (destination.exists()) throw IOException("Documents/$ROOT_NAME/$area/$safe already exists; existing data was left unchanged")
            val temporary = File(directory, ".$safe.${UUID.randomUUID()}.tmp")
            try {
                resolver.openInputStream(source)?.use { input ->
                    FileOutputStream(temporary).use { output ->
                        input.copyTo(output, 1 shl 16)
                        output.fd.sync()
                    }
                } ?: throw IOException("Cannot read the selected file")
                if (!temporary.renameTo(destination)) throw IOException("Cannot publish $safe")
            } finally {
                temporary.delete()
            }
            return safe
        }
        val parent = ensureArea(area)
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

    /** Lists only immediate mod roots in the user-managed SAF folder, without copying their contents. */
    fun listModFolders(): List<String> {
        if (treeUri() == null) return emptyList()
        val tree = treeUri()!!
        val mods = ensureChild(rootUri(), "Mods")
        return children(tree, DocumentsContract.getDocumentId(mods))
            .asSequence()
            .filter { it.mime == DocumentsContract.Document.MIME_TYPE_DIR && !it.name.startsWith(".") }
            .map { safeName(it.name) }
            .distinctBy { it.lowercase(java.util.Locale.ROOT) }
            .sortedBy { it.lowercase(java.util.Locale.ROOT) }
            .toList()
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

    private fun ensureArea(area: String): Uri {
        var current = rootUri()
        for (segment in area.split('/')) current = ensureChild(current, safeName(segment))
        return current
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

    /** Reuse an existing user folder by renaming it; never delete its contents. */
    private fun migrateLegacyRoot(tree: Uri, parentId: String): Uri? {
        for (legacyName in LEGACY_ROOT_NAMES) {
            val legacy = findChild(tree, parentId, legacyName) ?: continue
            val renamed = runCatching { DocumentsContract.renameDocument(resolver, legacy, ROOT_NAME) }.getOrNull()
            if (renamed != null) return renamed
            // Providers that cannot rename remain accessible. Do not create a
            // second root that would split the user's existing data.
            return legacy
        }
        return null
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

    /** Keep game-source selection from the upstream Android picker on upgrade. */
    private fun migrateCompatiblePreferences() {
        val current = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (current.contains(GAME_SOURCE_URI) || current.contains(GAME_SOURCE_PATH)) return

        val previous = context.getSharedPreferences(UPSTREAM_STORAGE_PREFS, Context.MODE_PRIVATE)
        val value = previous.getString(UPSTREAM_LAST_GAME_TREE, null) ?: return
        val source = runCatching { Uri.parse(value) }.getOrNull() ?: return
        if (source.scheme != "content" || source.authority != EXTERNAL_STORAGE_DOCUMENTS) return

        val name = runCatching {
            val id = DocumentsContract.getTreeDocumentId(source)
            displayName(source, id)
        }.getOrNull().orEmpty()
        current.edit()
            .putString(GAME_SOURCE_URI, source.toString())
            .putString(GAME_SOURCE_NAME, safeName(name.ifBlank { "Selected game folder" }))
            .apply()
    }

    companion object {
        private const val PREFS = "ironharbor_user_storage"
        private const val UPSTREAM_STORAGE_PREFS = "android-storage"
        private const val UPSTREAM_LAST_GAME_TREE = "last-game-tree"
        private const val EXTERNAL_STORAGE_DOCUMENTS = "com.android.externalstorage.documents"
        private const val TREE_URI = "documents_tree_uri"
        private const val ACTIVE_GAME = "active_game_folder"
        private const val ACTIVE_EXTERNAL_MOD_PATH = "active_external_mod_path"
        private const val GAME_SOURCE_URI = "game_source_tree_uri"
        private const val GAME_SOURCE_PATH = "game_source_path"
        private const val GAME_SOURCE_NAME = "game_source_name"
        private const val ROOT_NAME = "RA2Harbor"
        private val LEGACY_ROOT_NAMES = listOf("IronHarborRA2YR", "RA2IRONHARBOR")

        /** Move legacy Configs/Maps content to the canonical Maps tree without replacing user files. */
        @JvmStatic
        @Throws(IOException::class)
        internal fun migrateLegacyMaps(root: File) {
            val canonicalRoot = root.canonicalFile
            val configs = File(canonicalRoot, "Configs")
            val legacy = File(configs, "Maps")
            val target = File(canonicalRoot, "Maps")
            if (Files.isSymbolicLink(configs.toPath()) || Files.isSymbolicLink(legacy.toPath()) ||
                Files.isSymbolicLink(target.toPath())
            ) {
                throw IOException("Cannot migrate maps through a symbolic link")
            }
            if (!legacy.isDirectory) return
            if (!target.isDirectory && !target.mkdirs()) throw IOException("Cannot create ${target.absolutePath}")
            mergeLegacyDirectory(legacy, target)
        }

        @Throws(IOException::class)
        private fun mergeLegacyDirectory(source: File, target: File) {
            val children = source.listFiles() ?: throw IOException("Cannot list ${source.absolutePath}")
            for (entry in children) {
                if (Files.isSymbolicLink(entry.toPath())) continue
                val existing = target.listFiles()?.firstOrNull { it.name.equals(entry.name, ignoreCase = true) }
                if (entry.isDirectory && existing?.isDirectory == true) {
                    mergeLegacyDirectory(entry, existing)
                    continue
                }
                val destination = existing?.let { uniqueLegacyName(target, entry.name) } ?: File(target, entry.name)
                Files.move(entry.toPath(), destination.toPath())
            }
            if (source.listFiles()?.isEmpty() == true) source.delete()
        }

        private fun uniqueLegacyName(directory: File, original: String): File {
            val dot = original.lastIndexOf('.')
            val stem = if (dot > 0) original.substring(0, dot) else original
            val extension = if (dot > 0) original.substring(dot) else ""
            var suffix = 2
            while (true) {
                val candidate = File(directory, "$stem (legacy $suffix)$extension")
                val collision = directory.listFiles()?.any { it.name.equals(candidate.name, ignoreCase = true) } == true
                if (!collision) return candidate
                suffix++
            }
        }

        fun setTree(context: Context, tree: Uri) {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previous = preferences.getString(TREE_URI, null)
            preferences.edit().putString(TREE_URI, tree.toString()).apply {
                if (previous != tree.toString()) remove(ACTIVE_GAME)
            }.apply()
        }
    }
}
