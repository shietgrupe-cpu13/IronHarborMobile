package org.vera20k

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Native Android command center. The Rust engine remains in [VeraActivity],
 * started only after the player presses Play. Keeping it in the separate
 * `:game` process means a launcher return never creates a second winit loop.
 */
class LauncherActivity : Activity() {
    private enum class Page(val title: String) {
        HOME("HOME"), FILES("GAME FILES"), SETTINGS("SETTINGS"),
        TOUCH("TOUCH"), MODS("MODS"), DIAGNOSTICS("DIAGNOSTICS"),
    }

    private companion object {
        const val REQUEST_PICK_GAME = 0x564c
        const val REQUIRED_FREE_BYTES = 1700L shl 20
        const val PROGRESS_STEP_BYTES = 16L shl 20

        init {
            System.loadLibrary("vera20k_android")
        }
    }

    private var page = Page.HOME
    private var installation = LauncherInstallation(null, listOf("ra2.mix", "ra2md.mix"))
    private var importing = false
    private var copiedBytes = 0L
    private var reportedBytes = 0L
    private var exportingDiagnostics = false
    private lateinit var pageBody: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var playButton: TextView
    private lateinit var importButton: TextView
    private var importNote: String? = null

    private external fun nativeConfigForImport(directory: String, filesDirectory: String): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getString("launcher_page")?.let { value ->
            Page.entries.firstOrNull { it.name == value }
        } ?: Page.HOME
        buildShell()
        refreshInstallation()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("launcher_page", page.name)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::pageBody.isInitialized) refreshInstallation()
    }

    @Deprecated("The SAF tree picker returns through this activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_GAME) return
        val tree = if (resultCode == RESULT_OK) data?.data else null
        if (tree == null) {
            importNote = "Import cancelled. Your current game folder was not changed."
            renderPage()
            return
        }
        try {
            contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // The current picker grant still permits this foreground import.
        }
        importGame(tree)
    }

    private fun buildShell() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.launcher_background))
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(8))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_ironharbor)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Iron Harbor Mobile app icon"
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(52), dp(52)))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        brand.addView(text("IRON HARBOR", 18, R.color.launcher_text, bold = true, tracking = 0.08f))
        brand.addView(text("MOBILE COMMAND  ·  ${appVersion()}", 10, R.color.launcher_muted, tracking = 0.06f))
        header.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(text("GPLv3", 11, R.color.launcher_accent, bold = true, tracking = 0.08f))
        root.addView(header)

        val navScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
        }
        tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navScroll.addView(tabs)
        root.addView(navScroll, LinearLayout.LayoutParams(-1, dp(48)))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(12))
        }
        val centered = FrameLayout(this)
        pageBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(20))
        }
        val maxWidth = minOf(dp(960), resources.displayMetrics.widthPixels - dp(44))
        centered.addView(pageBody, FrameLayout.LayoutParams(maxWidth, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        scroll.addView(centered)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        renderPage()
    }

    private fun renderPage() {
        if (!::pageBody.isInitialized) return
        renderTabs()
        pageBody.animate().cancel()
        pageBody.alpha = 0.72f
        pageBody.removeAllViews()
        when (page) {
            Page.HOME -> renderHome()
            Page.FILES -> renderFiles()
            Page.SETTINGS -> renderSettings()
            Page.TOUCH -> renderTouch()
            Page.MODS -> renderMods()
            Page.DIAGNOSTICS -> renderDiagnostics()
        }
        pageBody.animate().alpha(1f).setDuration(140).start()
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        Page.entries.forEach { item ->
            val selected = item == page
            val tab = TextView(this).apply {
                text = item.title
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f
                gravity = Gravity.CENTER
                setTextColor(color(if (selected) R.color.launcher_accent else R.color.launcher_muted))
                setPadding(dp(15), 0, dp(15), 0)
                background = rounded(
                    if (selected) color(R.color.launcher_surface_alt) else color(R.color.launcher_background),
                    if (selected) color(R.color.launcher_accent_dark) else color(R.color.launcher_background),
                    10,
                )
                isFocusable = true
                isClickable = true
                setOnClickListener { page = item; renderPage() }
            }
            val params = LinearLayout.LayoutParams(-2, dp(42))
            params.setMargins(0, 0, dp(6), 0)
            tabs.addView(tab, params)
        }
    }

    private fun renderHome() {
        pageBody.addView(kicker("ANDROID COMMAND CENTER  /  ${appVersion()}"))
        pageBody.addView(text("Command the next\nfrontier.", 34, R.color.launcher_text, bold = true))
        pageBody.addView(text(
            "An independent mobile client based on YuriPlanet's VERA20K engine. Your game files stay private to this device and are never included in this app.",
            15,
            R.color.launcher_muted,
        ), margin(bottom = 18))

        val stateCard = card()
        val ready = installation.ready
        stateCard.addView(kicker(if (ready) "INSTALLATION READY" else "SETUP REQUIRED", if (ready) R.color.launcher_accent else R.color.launcher_warning))
        stateCard.addView(text(
            if (ready) "Yuri's Revenge files detected" else "Add your own game files",
            19,
            R.color.launcher_text,
            bold = true,
        ))
        stateCard.addView(text(
            if (ready) installation.configuredDirectory?.absolutePath.orEmpty()
            else "The launcher checks the engine's configured folder for ra2.mix and ra2md.mix.",
            12,
            R.color.launcher_muted,
        ), margin(top = 5))
        pageBody.addView(stateCard, margin(bottom = 12))

        playButton = actionButton("START GAME", primary = true) {
            if (installation.ready) launchGame() else {
                importNote = "Import your game folder before launching."
                page = Page.FILES
                renderPage()
            }
        }
        playButton.isEnabled = installation.ready && !importing
        pageBody.addView(playButton, margin(bottom = 10))
        pageBody.addView(actionButton("GAME FILES  ·  IMPORT OR CHANGE", primary = false) {
            page = Page.FILES
            renderPage()
        }, margin(bottom = 17))

        val engine = card()
        engine.addView(kicker("ENGINE STATUS"))
        infoRow(engine, "Engine", "VERA20K ${appVersion()}")
        infoRow(engine, "Renderer", "Vulkan required by this build")
        infoRow(engine, "Attribution", "YuriPlanet / VERA20K · GPLv3")
        pageBody.addView(engine)
        pageBody.addView(text(
            "Unofficial community fork. Not affiliated with or endorsed by Electronic Arts.",
            10,
            R.color.launcher_muted,
        ), margin(top = 14))
    }

    private fun renderFiles() {
        pageBody.addView(kicker("GAME LIBRARY"))
        pageBody.addView(text("Game files", 30, R.color.launcher_text, bold = true))
        pageBody.addView(text(
            "Choose the folder from your own Yuri's Revenge installation. The import is copied into app-private storage; the selected source is left untouched.",
            14,
            R.color.launcher_muted,
        ), margin(bottom = 15))

        val status = card()
        val ready = installation.ready
        status.addView(kicker(if (ready) "VALIDATION PASSED" else "VALIDATION NEEDED", if (ready) R.color.launcher_accent else R.color.launcher_warning))
        status.addView(text(
            if (ready) "Required engine archives are present" else "Required files not confirmed",
            18,
            R.color.launcher_text,
            bold = true,
        ))
        val location = installation.configuredDirectory?.absolutePath ?: "No configured game folder"
        status.addView(text(location, 12, R.color.launcher_muted), margin(top = 6))
        if (!ready) {
            status.addView(text(
                if (installation.configuredDirectory == null) "Missing configuration. Import an installation to set it up."
                else "Missing: ${installation.missingArchives.joinToString()}",
                12,
                R.color.launcher_warning,
            ), margin(top = 7))
        }
        pageBody.addView(status, margin(bottom = 12))

        if (importing) {
            val progressCard = card()
            progressCard.addView(kicker("IMPORT IN PROGRESS"))
            progressCard.addView(text(importNote ?: "Copying game files…", 15, R.color.launcher_text))
            progressCard.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = true
            }, margin(top = 10, bottom = 8))
            progressCard.addView(actionButton("CANCEL IMPORT", primary = false) { AssetImportJob.cancelActive() })
            pageBody.addView(progressCard, margin(bottom = 12))
        } else {
            importButton = actionButton(if (ready) "CHANGE GAME FOLDER" else "IMPORT GAME FILES", primary = true) {
                chooseGameFolder()
            }
            pageBody.addView(importButton, margin(bottom = 12))
            if (importNote != null) {
                pageBody.addView(text(importNote!!, 13, R.color.launcher_warning), margin(bottom = 10))
            }
        }

        val guide = card()
        guide.addView(kicker("WHAT THE CHECKER USES"))
        infoRow(guide, "Required", "ra2.mix and ra2md.mix")
        infoRow(guide, "Storage", "App-private copy; no all-files permission")
        infoRow(guide, "Game data", "Not bundled with this app")
        pageBody.addView(guide)
    }

    private fun renderSettings() {
        pageBody.addView(kicker("ENGINE-BACKED OPTIONS"))
        pageBody.addView(text("Game settings", 30, R.color.launcher_text, bold = true))
        pageBody.addView(text("This baseline exposes status only. It does not show controls the engine cannot apply.", 14, R.color.launcher_muted), margin(bottom = 14))
        val display = card()
        display.addView(kicker("DISPLAY & GRAPHICS"))
        infoRow(display, "Orientation", "Landscape-first Android window")
        infoRow(display, "Renderer", "Vulkan; required by the current engine")
        infoRow(display, "Resolution", "Uses the Android surface size")
        infoRow(display, "Quality / FPS cap", "No launcher control in this build")
        pageBody.addView(display, margin(bottom = 12))
        val audio = card()
        audio.addView(kicker("AUDIO"))
        infoRow(audio, "Output", "Engine default; device volume controls")
        infoRow(audio, "Mixer settings", "No launcher control in this build")
        pageBody.addView(audio)
    }

    private fun renderTouch() {
        pageBody.addView(kicker("INPUT PROFILE"))
        pageBody.addView(text("Touch controls", 30, R.color.launcher_text, bold = true))
        pageBody.addView(text("Gesture handling lives in the Rust app input mapper and does not change simulation rules.", 14, R.color.launcher_muted), margin(bottom = 14))
        val card = card()
        card.addView(kicker("CURRENT GESTURES"))
        infoRow(card, "Tap", "Select / activate")
        infoRow(card, "Quick one-finger drag", "Camera pan during a match")
        infoRow(card, "Hold then drag", "Box selection")
        infoRow(card, "Two-finger drag", "Camera pan")
        infoRow(card, "Pinch", "Presentation zoom")
        infoRow(card, "Two-finger tap", "Context / cancel action")
        infoRow(card, "Sensitivity / overlay", "Not configurable in this launcher baseline")
        pageBody.addView(card)
    }

    private fun renderMods() {
        pageBody.addView(kicker("CONTENT PROFILES"))
        pageBody.addView(text("Mods", 30, R.color.launcher_text, bold = true))
        val card = card()
        card.addView(kicker("NOT AVAILABLE IN THIS BUILD", R.color.launcher_warning))
        card.addView(text("This build does not currently expose a mod profile manager.", 16, R.color.launcher_text, bold = true))
        card.addView(text("No compatibility or launch profile is implied here. The engine uses the selected Yuri's Revenge installation and its supported rules.", 13, R.color.launcher_muted), margin(top = 8))
        pageBody.addView(card)
    }

    private fun renderDiagnostics() {
        pageBody.addView(kicker("DEVICE & ENGINE"))
        pageBody.addView(text("Diagnostics", 30, R.color.launcher_text, bold = true))
        val activityManager = getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memory)
        val device = card()
        device.addView(kicker("COMPATIBILITY SNAPSHOT"))
        infoRow(device, "Device", "${Build.MANUFACTURER} ${Build.MODEL}")
        infoRow(device, "Android", "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
        infoRow(device, "ABI", Build.SUPPORTED_ABIS.joinToString())
        infoRow(device, "Vulkan", "Required by package; live adapter not queried here")
        infoRow(device, "Memory", "${memory.availMem / (1024 * 1024)} MB available · native heap ${Debug.getNativeHeapAllocatedSize() / (1024 * 1024)} MB")
        infoRow(device, "FPS / frame time", "In-game telemetry is not connected to the launcher")
        infoRow(device, "Log export", "Full retained engine logs and crash report in a ZIP")
        pageBody.addView(device, margin(bottom = 12))
        pageBody.addView(actionButton(
            if (exportingDiagnostics) "PREPARING DIAGNOSTICS ZIP…" else "EXPORT FULL DIAGNOSTICS ZIP",
            primary = false,
        ) {
            shareDiagnostics(memory)
        }.apply { isEnabled = !exportingDiagnostics })
    }

    private fun shareDiagnostics(memory: ActivityManager.MemoryInfo) {
        if (exportingDiagnostics) return
        val report = buildString {
            appendLine("Iron Harbor Mobile diagnostics (based on YuriPlanet VERA20K)")
            appendLine("Version: ${appVersion()}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Vulkan: required by package; live adapter not queried by launcher")
            appendLine("Available memory: ${memory.availMem / (1024 * 1024)} MB")
            appendLine("Native heap: ${Debug.getNativeHeapAllocatedSize() / (1024 * 1024)} MB")
            appendLine("Installation configured: ${installation.configuredDirectory?.absolutePath ?: "no"}")
            appendLine("Engine logs: ${File(filesDir, "logs").absolutePath}")
            appendLine("The attached ZIP includes every retained regular file in the engine logs directory and except.txt when present.")
            appendLine("Android system logcat is separate and is not included.")
            appendLine("No game assets are included in this report.")
        }
        exportingDiagnostics = true
        renderPage()
        Toast.makeText(this, "Preparing the full diagnostics ZIP…", Toast.LENGTH_SHORT).show()
        Thread({
            try {
                val bundle = DiagnosticLogArchive.create(filesDir, cacheDir, report)
                val uri = Uri.Builder()
                    .scheme("content")
                    .authority("$packageName.diagnostics")
                    .appendPath("diagnostics")
                    .appendPath(bundle.file.name)
                    .build()
                runOnUiThread {
                    exportingDiagnostics = false
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (page == Page.DIAGNOSTICS) renderPage()
                    if (bundle.logFileCount == 0 && !bundle.crashReportIncluded) {
                        Toast.makeText(this, "No saved engine logs yet; the ZIP contains the device summary.", Toast.LENGTH_LONG).show()
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_SUBJECT, "Iron Harbor Mobile full diagnostics")
                        putExtra(Intent.EXTRA_TEXT, report)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(contentResolver, bundle.file.name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(send, "Share full diagnostics")
                    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(chooser)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    exportingDiagnostics = false
                    if (!isFinishing && !isDestroyed) {
                        if (page == Page.DIAGNOSTICS) renderPage()
                        Toast.makeText(this, "Could not export diagnostics: ${error.message ?: error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }, "diagnostic-log-export").start()
    }

    private fun refreshInstallation() {
        installation = LauncherInstallation.read(filesDir)
        renderPage()
    }

    private fun chooseGameFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_PICK_GAME)
    }

    private fun importGame(tree: Uri) {
        val job = AssetImportJob.begin()
        if (job == null) {
            importNote = "Another import is already running."
            renderPage()
            return
        }
        importing = true
        copiedBytes = 0
        reportedBytes = 0
        importNote = "Checking the selected folder…"
        page = Page.FILES
        renderPage()
        Thread({
            val result = try {
                val free = filesDir.usableSpace
                if (free < REQUIRED_FREE_BYTES) {
                    throw IOException("${free shr 20} MB free; at least ${REQUIRED_FREE_BYTES shr 20} MB is required")
                }
                val missing = missingRequiredArchives(tree)
                if (missing.isNotEmpty()) {
                    throw IOException("Selected folder is missing ${missing.joinToString()}")
                }
                val installed = job.run(
                    filesDir,
                    { staging -> copyTree(tree, staging, job) },
                    { staging -> nativeConfigForImport(staging.absolutePath, filesDir.absolutePath) },
                )
                "Game files imported to ${installed.absolutePath}."
            } catch (_: AssetImportJob.Cancelled) {
                "Import cancelled. The previous installation remains active."
            } catch (error: Throwable) {
                "Import failed: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                job.close()
                runCatching {
                    contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            runOnUiThread {
                importing = false
                importNote = result
                refreshInstallation()
            }
        }, "vera20k-game-import").start()
    }

    private fun missingRequiredArchives(tree: Uri): List<String> {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val present = mutableSetOf<String>()
        contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0)?.let { present += it.lowercase(Locale.ROOT) }
        } ?: throw IOException("Cannot read the selected folder")
        return listOf("ra2.mix", "ra2md.mix").filterNot(present::contains)
    }

    private fun copyTree(tree: Uri, directory: File, job: AssetImportJob) {
        copyChildren(tree, DocumentsContract.getTreeDocumentId(tree), directory, job)
    }

    private fun copyChildren(tree: Uri, documentId: String, directory: File, job: AssetImportJob) {
        job.checkCancelled()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        var listed = false
        contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            listed = true
            while (cursor.moveToNext()) {
                job.checkCancelled()
                val id = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2)
                val destination = AssetImportJob.child(directory, name)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!destination.mkdir()) throw IOException("Cannot create folder $name")
                    copyChildren(tree, id, destination, job)
                } else {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    contentResolver.openInputStream(uri)?.use { input ->
                        destination.outputStream().use { output ->
                            val buffer = ByteArray(1 shl 16)
                            while (true) {
                                job.checkCancelled()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                copiedBytes += count
                            }
                        }
                    } ?: throw IOException("Cannot read $name")
                    if (copiedBytes - reportedBytes >= PROGRESS_STEP_BYTES) {
                        val megabytes = copiedBytes / (1024 * 1024)
                        runOnUiThread { importNote = "Copying game files · $megabytes MB"; if (page == Page.FILES) renderPage() }
                        reportedBytes = copiedBytes
                    }
                }
            }
        }
        if (!listed) throw IOException("Cannot list the selected folder")
    }

    private fun launchGame() {
        try {
            startActivity(Intent(this, VeraActivity::class.java))
        } catch (error: Exception) {
            Toast.makeText(this, "Could not start the game: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun appVersion(): String = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }
    }.getOrNull() ?: "Android"

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(color(R.color.launcher_surface), color(R.color.launcher_border), 16)
    }

    private fun infoRow(parent: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(8), 0, dp(3))
        }
        row.addView(text(label, 12, R.color.launcher_accent, bold = true), LinearLayout.LayoutParams(dp(126), -2))
        row.addView(text(value, 13, R.color.launcher_text), LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row)
    }

    private fun kicker(value: String, tint: Int = R.color.launcher_accent): TextView = text(value, 10, tint, bold = true, tracking = 0.12f).apply {
        setPadding(0, 0, 0, dp(7))
    }

    private fun text(
        value: String,
        size: Int,
        tint: Int,
        bold: Boolean = false,
        tracking: Float = 0f,
    ): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color(tint))
        if (bold) typeface = Typeface.DEFAULT_BOLD
        letterSpacing = tracking
        includeFontPadding = true
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun actionButton(label: String, primary: Boolean, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = if (primary) 16f else 12f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
        gravity = Gravity.CENTER
        minHeight = dp(if (primary) 62 else 50)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setTextColor(color(if (primary) R.color.launcher_background else R.color.launcher_text))
        background = rounded(
            color(if (primary) R.color.launcher_accent else R.color.launcher_surface_alt),
            color(if (primary) R.color.launcher_accent_dark else R.color.launcher_border),
            14,
        )
        isFocusable = true
        isClickable = true
        setOnClickListener { action() }
        contentDescription = label
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        setCornerRadius(dp(radius).toFloat())
        setStroke(dp(1), stroke)
    }

    private fun margin(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
        setMargins(0, dp(top), 0, dp(bottom))
    }

    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
