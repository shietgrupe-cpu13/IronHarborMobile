package org.vera20k

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
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
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
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
        TOUCH("TOUCH"), MODS("MODS"), MAPS("MAPS"), DIAGNOSTICS("DIAGNOSTICS"),
    }

    private companion object {
        const val REQUEST_PICK_GAME = 0x564c
        const val REQUEST_PICK_MOD = 0x564d
        const val REQUEST_PICK_MAPS = 0x5650
        const val REQUEST_PICK_STORAGE = 0x5651
        const val REQUEST_EXPORT_HUD = 0x564e
        const val REQUEST_IMPORT_HUD = 0x564f
        const val REQUIRED_FREE_BYTES = 1700L shl 20
        const val PROGRESS_STEP_BYTES = 16L shl 20

        init {
            System.loadLibrary("vera20k_android")
        }
    }

    private var page = Page.HOME
    private var installation = LauncherInstallation(null, listOf("ra2.mix", "ra2md.mix"))
    private var modCatalog = LauncherModCatalog(emptyList(), null, LauncherIniProfile.YR, null)
    private var modSelectionNote: String? = null
    private var modImporting = false
    private var modCopiedBytes = 0L
    private var modReportedBytes = 0L
    private var modImportNote: String? = null
    private var mapImporting = false
    private var mapCopiedCount = 0
    private var mapImportNote: String? = null
    private var validatingLaunch = false
    private val launchGate = GameLaunchGate()
    private var importing = false
    private var copiedBytes = 0L
    private var reportedBytes = 0L
    private var exportingDiagnostics = false
    private lateinit var pageBody: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var playButton: TextView
    private lateinit var importButton: TextView
    private var importNote: String? = null
    private var storageNote: String? = null
    private var gameWasLaunched = false

    private external fun nativeConfigForImport(directory: String, filesDirectory: String): String
    private external fun nativeConfigForSelection(modName: String, profile: String, filesDirectory: String): String
    private external fun nativeConfigForMod(modName: String, filesDirectory: String): String
    private external fun nativeValidateCurrentSelection(filesDirectory: String): String
    private external fun nativeMapsDirectory(filesDirectory: String): String

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
        launchGate.returnedToLauncher()
        if (gameWasLaunched) {
            gameWasLaunched = false
            publishRuntimeData()
        }
        if (::pageBody.isInitialized) refreshInstallation()
    }

    @Deprecated("The SAF tree picker returns through this activity result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_STORAGE) {
            val tree = if (resultCode == RESULT_OK) data?.data else null
            if (tree == null) {
                storageNote = "Storage folder selection cancelled. Existing files were not changed."
            } else {
                val flags = (data?.flags ?: 0) and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                try {
                    contentResolver.takePersistableUriPermission(tree, flags)
                    SharedDataStorage.setTree(this, tree)
                    SharedDataStorage(this).ensureLayout()
                    storageNote = "Documents/RA2IRONHARBOR is ready. Checking for existing app data to move there…"
                    migrateExistingDataToDocuments()
                } catch (error: Exception) {
                    storageNote = "Cannot use that Documents folder: ${error.message ?: error.javaClass.simpleName}"
                }
            }
            renderPage()
            return
        }
        if (requestCode == REQUEST_PICK_MAPS) {
            val uris = if (resultCode == RESULT_OK && data != null) {
                buildList {
                    data.clipData?.let { clip ->
                        for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(::add)
                    }
                    if (isEmpty()) data.data?.let(::add)
                }.distinct()
            } else emptyList()
            if (uris.isEmpty()) {
                mapImportNote = "Map import cancelled. Your installed maps were not changed."
                page = Page.MAPS
                renderPage()
            } else importMaps(uris)
            return
        }
        if (requestCode == REQUEST_PICK_MOD) {
            val tree = if (resultCode == RESULT_OK) data?.data else null
            if (tree == null) {
                modImportNote = "Mod import cancelled. Installed mods were not changed."
                renderPage()
                return
            }
            try {
                contentResolver.takePersistableUriPermission(
                    tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // The picker grant remains valid for this foreground import.
            }
            importMod(tree)
            return
        }
        if (requestCode == REQUEST_EXPORT_HUD || requestCode == REQUEST_IMPORT_HUD) {
            val uri = if (resultCode == RESULT_OK) data?.data else null
            if (uri == null) {
                Toast.makeText(this, "HUD layout transfer cancelled.", Toast.LENGTH_SHORT).show()
                return
            }
            if (requestCode == REQUEST_EXPORT_HUD) exportHudLayout(uri) else importHudLayout(uri)
            return
        }
        if (requestCode != REQUEST_PICK_GAME) return
        val tree = if (resultCode == RESULT_OK) data?.data else null
        if (tree == null) {
            importNote = "Import cancelled. Your current game folder was not changed."
            renderPage()
            return
        }
        val retainGameSource = try {
            contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            true
        } catch (_: SecurityException) {
            // A one-time provider grant still permits this copy, but cannot be
            // reused to synchronize the selected source on later launches.
            false
        }
        importGame(tree, retainGameSource)
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
            Page.MAPS -> renderMaps()
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
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(14), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(color(R.color.launcher_surface_alt), color(R.color.launcher_surface), color(R.color.launcher_background)),
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), color(R.color.launcher_border))
            }
        }
        val heroCopy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heroCopy.addView(kicker("INDEPENDENT MOBILE CLIENT  /  ${appVersion()}"))
        heroCopy.addView(text("IRON HARBOR\nFIELD COMMAND", 27, R.color.launcher_text, bold = true, tracking = 0.025f))
        heroCopy.addView(text("Based on YuriPlanet VERA20K", 13, R.color.launcher_muted), margin(top = 6))
        hero.addView(heroCopy, LinearLayout.LayoutParams(0, -2, 1f))
        hero.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher_ironharbor)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Original Iron Harbor radar and harbor emblem"
        }, LinearLayout.LayoutParams(dp(92), dp(92)))
        pageBody.addView(hero, margin(bottom = 14))
        pageBody.addView(text(
            "Your legally obtained game files stay on this device and are never bundled with the app.",
            14,
            R.color.launcher_muted,
        ), margin(bottom = 14))

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

        playButton = actionButton(if (validatingLaunch) "CHECKING GAME FILES…" else "START GAME", primary = true) {
            if (validatingLaunch) return@actionButton
            if (installation.ready) launchGame() else {
                importNote = "Import your game folder before launching."
                page = Page.FILES
                renderPage()
            }
        }
        playButton.isEnabled = installation.ready && !importing && !validatingLaunch && !launchGate.isPending
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
            "Locate your own Yuri's Revenge folder. Iron Harbor leaves that source in place and keeps saves, maps, mods and logs under Documents/IronHarborRA2YR.",
            14,
            R.color.launcher_muted,
        ), margin(bottom = 15))

        val storageCard = card()
        storageCard.addView(kicker("USER-MANAGED STORAGE"))
        val sharedStorage = SharedDataStorage(this)
        val storageLocation = if (sharedStorage.treeUri() == null) {
            "Documents/IronHarborRA2YR"
        } else {
            sharedStorage.displayLocation()
        }
        storageCard.addView(text(storageLocation, 16, R.color.launcher_text, bold = true))
        storageCard.addView(text(
            if (sharedStorage.treeUri() == null) "Choose Documents in the folder picker; Iron Harbor creates IronHarborRA2YR and its Mods/Maps/Saves/Logs folders."
            else "Mods, Maps, Saves and Logs are managed under $storageLocation.",
            13,
            R.color.launcher_muted,
        ), margin(top = 6))
        storageCard.addView(actionButton("CHOOSE DOCUMENTS FOLDER", primary = false) { chooseStorageLocation() }, margin(top = 8))
        storageNote?.let { storageCard.addView(text(it, 12, R.color.launcher_muted), margin(top = 8)) }
        pageBody.addView(storageCard, margin(bottom = 12))

        val status = card()
        val ready = installation.ready
        status.addView(kicker(if (ready) "VALIDATION PASSED" else "VALIDATION NEEDED", if (ready) R.color.launcher_accent else R.color.launcher_warning))
        status.addView(text(
            if (ready) "Required engine archives are present" else "Required files not confirmed",
            18,
            R.color.launcher_text,
            bold = true,
        ))
        val shared = SharedDataStorage(this)
        val gameSource = shared.gameSourceName()
        val sharedGame = shared.activeGameFolder()
        val location = gameSource?.let { "Selected source: $it (kept in place; engine cache is private)" }
            ?: sharedGame?.let { "Documents/IronHarborRA2YR/Game/$it (legacy source)" }
            ?: installation.configuredDirectory?.absolutePath
            ?: "No configured game folder"
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
            progressCard.addView(kicker("PREPARING GAME FILES"))
            progressCard.addView(text(importNote ?: "Copying game files…", 15, R.color.launcher_text))
            progressCard.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = true
            }, margin(top = 10, bottom = 8))
            progressCard.addView(actionButton("CANCEL PREPARATION", primary = false) { AssetImportJob.cancelActive() })
            pageBody.addView(progressCard, margin(bottom = 12))
        } else {
            importButton = actionButton(if (ready) "LOCATE / CHANGE GAME FOLDER" else "LOCATE GAME FILES", primary = true) {
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
        infoRow(guide, "Storage", "Original game folder stays in place; one private engine-readable copy is maintained")
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
        val controlPrefs = getSharedPreferences(TouchHudSettings.PREFS, MODE_PRIVATE)
        val hudCard = card()
        hudCard.addView(kicker("FLOATING COMMAND HUD"))
        hudCard.addView(text("RA2YRPORT-inspired floating controls", 19, R.color.launcher_text, bold = true))
        hudCard.addView(text("Customize from the in-game EDIT button or arrange controls in this preview. EDIT and HIDE/SHOW can also be moved. C1-C10 select groups; hold one in a match to assign the current selection.", 14, R.color.launcher_muted), margin(top = 6, bottom = 8))
        val enabled = CheckBox(this).apply {
            text = "Show floating HUD in touch and mixed modes"
            setTextColor(color(R.color.launcher_text))
            isChecked = controlPrefs.getBoolean(TouchHudSettings.HUD_ENABLED, true)
        }
        hudCard.addView(enabled)
        hudCard.addView(actionButton("CUSTOMIZE AND ARRANGE BUTTONS", primary = true) {
            showTouchHudEditor()
        }, margin(top = 8))
        hudCard.addView(actionButton("REMAP ACTION KEYS", primary = false) {
            chooseTouchHudKey()
        }, margin(top = 7))
        enabled.setOnCheckedChangeListener { _, checked ->
            controlPrefs.edit().putBoolean(TouchHudSettings.HUD_ENABLED, checked).apply()
        }
        pageBody.addView(hudCard, margin(bottom = 12))

        pageBody.addView(kicker("INPUT PROFILE"))
        pageBody.addView(text("Touch controls", 30, R.color.launcher_text, bold = true))
        pageBody.addView(text("VERA20K keeps its native touch-to-input mapper. These gestures feed the existing mouse and camera controls without changing simulation rules.", 14, R.color.launcher_muted), margin(bottom = 14))
        val gestures = card()
        gestures.addView(kicker("SUPPORTED GESTURES"))
        infoRow(gestures, "Tap", "Select or activate")
        infoRow(gestures, "Quick drag", "Camera pan during a match")
        infoRow(gestures, "Hold, then drag", "Box selection")
        infoRow(gestures, "Two-finger drag", "Camera pan")
        infoRow(gestures, "Pinch", "Presentation zoom")
        infoRow(gestures, "Two-finger tap", "Context / cancel action")
        pageBody.addView(gestures, margin(bottom = 12))

        val inputModeCard = card()
        inputModeCard.addView(kicker("INPUT DEVICE MODE"))
        inputModeCard.addView(text("Choose how game input is accepted", 19, R.color.launcher_text, bold = true))
        inputModeCard.addView(text("Touch-only keeps screen gestures and the floating HUD. Mixed accepts both touch and external devices. External-only disables touch gestures and the HUD. This takes effect when the game is next launched.", 13, R.color.launcher_muted), margin(top = 6, bottom = 7))
        val modes = listOf("Touch only", "Touch + mouse / keyboard", "Mouse + keyboard only")
        val modeValues = listOf(TouchHudSettings.MODE_TOUCH, TouchHudSettings.MODE_MIXED, TouchHudSettings.MODE_EXTERNAL)
        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(this@LauncherActivity, android.R.layout.simple_spinner_dropdown_item, modes)
            setSelection(modeValues.indexOf(TouchHudSettings.parseControlMode(
                controlPrefs.getString(TouchHudSettings.CONTROL_MODE, null),
            )).coerceAtLeast(0))
        }
        mode.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                controlPrefs.edit().putString(TouchHudSettings.CONTROL_MODE, modeValues[position]).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        inputModeCard.addView(mode)
        pageBody.addView(inputModeCard, margin(bottom = 12))

        val layout = card()
        layout.addView(kicker("MOVE AVAILABLE GAME UI"))
        layout.addView(text("Edit Layout", 19, R.color.launcher_text, bold = true))
        layout.addView(text(
            "In a match, open Options > Interface > Edit Layout. Drag supported panels and layout-added buttons, then choose Save. Reset restores the current game's default layout.",
            14,
            R.color.launcher_muted,
        ), margin(top = 7))
        layout.addView(text("The floating command HUD has its own in-game EDIT button; drag every HUD control, including EDIT and HIDE/SHOW, there or use the preview above.", 13, R.color.launcher_muted), margin(top = 9))
        pageBody.addView(layout)
    }

    private fun showTouchHudEditor() {
        val prefs = getSharedPreferences(TouchHudSettings.PREFS, MODE_PRIVATE)
        val visible = TouchHudSettings.parseVisible(
            prefs.getString(TouchHudSettings.HUD_VISIBLE, null),
            prefs.contains(TouchHudSettings.HUD_VISIBLE),
        ).toMutableSet()
        val choices = TouchHudSettings.buttons
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(4))
        }
        root.addView(text("Choose visible controls, assign them to Always / Set 1 / Set 2, then switch sets from the in-game HUD. Drag, resize, and transfer your setup when needed.", 13, R.color.launcher_muted), margin(bottom = 8))
        val checkboxes = mutableListOf<Pair<String, CheckBox>>()
        val controlSets = TouchHudSettings.parseControlSets(prefs.getString(TouchHudSettings.HUD_CONTROL_SETS, null)).toMutableMap()
        val sizes = TouchHudSettings.parseSizes(prefs.getString(TouchHudSettings.HUD_SIZES, null)).toMutableMap()
        val positions = TouchHudSettings.parsePositions(prefs.getString(TouchHudSettings.HUD_POSITIONS, null)).toMutableMap()
        var positionedLayout = prefs.getInt(TouchHudSettings.HUD_LAYOUT, TouchHudSettings.LAYOUT_FREE)
        var positionedDock = prefs.getInt(TouchHudSettings.HUD_DOCK, 0)
        val sizeOptions = listOf(70, 85, 100, 115, 130, 150)
        choices.forEach { action ->
            val check = CheckBox(this).apply {
                val bound = prefs.contains("key_${action.id}") || action.defaultKeyCode != KeyEvent.KEYCODE_UNKNOWN
                text = if (bound) action.label else "${action.label}  ·  assign an existing game key first"
                setTextColor(color(R.color.launcher_text))
                isChecked = action.id in visible
            }
            check.setOnCheckedChangeListener { _, checked ->
                if (checked) visible.add(action.id) else visible.remove(action.id)
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(check, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (action.groupNumber == null) {
                val setSpinner = Spinner(this).apply {
                    adapter = ArrayAdapter(this@LauncherActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Always", "Set 1", "Set 2"))
                    setSelection((controlSets[action.id] ?: 0).coerceIn(0, 2))
                }
                setSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        controlSets[action.id] = position
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                }
                row.addView(setSpinner, LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val sizeSpinner = Spinner(this).apply {
                adapter = ArrayAdapter(this@LauncherActivity, android.R.layout.simple_spinner_dropdown_item, sizeOptions.map { "$it%" })
                val currentSize = sizes[action.id] ?: 100
                setSelection(sizeOptions.indexOf(currentSize).takeIf { it >= 0 } ?: 2)
            }
            sizeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    sizes[action.id] = sizeOptions[position]
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
            row.addView(sizeSpinner, LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT))
            root.addView(row)
            checkboxes += action.id to check
        }
        fun seekRow(title: String, initial: Int, min: Int, max: Int, onLabel: (Int) -> String): Pair<SeekBar, TextView> {
            val label = text(onLabel(initial), 13, R.color.launcher_text)
            root.addView(label, margin(top = 7))
            val seek = SeekBar(this).apply {
                this.max = max - min
                progress = initial.coerceIn(min, max) - min
                contentDescription = title
            }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    label.text = onLabel(min + progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
            root.addView(seek)
            return seek to label
        }
        val scale = seekRow("Global HUD size", TouchHudSettings.clampScale(prefs.getInt(TouchHudSettings.HUD_SCALE, 100)), 70, 150) { "Global button size  /  $it%" }.first
        val opacity = seekRow("HUD opacity", TouchHudSettings.clampOpacity(prefs.getInt(TouchHudSettings.HUD_OPACITY, 88)), 30, 100) { "Button opacity  /  $it%" }.first
        root.addView(text("Layout preset", 13, R.color.launcher_text), margin(top = 8))
        val layoutNames = listOf("Free / saved layout", "Two-thumb layout", "Yuri controls layout")
        val layout = Spinner(this).apply {
            adapter = ArrayAdapter(this@LauncherActivity, android.R.layout.simple_spinner_dropdown_item, layoutNames)
            setSelection(prefs.getInt(TouchHudSettings.HUD_LAYOUT, TouchHudSettings.LAYOUT_FREE).coerceIn(0, layoutNames.lastIndex))
        }
        root.addView(layout)
        root.addView(text("Button dock", 13, R.color.launcher_text), margin(top = 5))
        val dock = Spinner(this).apply {
            adapter = ArrayAdapter(this@LauncherActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Bottom left", "Left edge", "Bottom right"))
            setSelection(prefs.getInt(TouchHudSettings.HUD_DOCK, 0).coerceIn(0, 2))
        }
        root.addView(dock)
        root.addView(text("A preset resets button positions to its starting arrangement. Place them on the launcher preview before saving.", 12, R.color.launcher_muted), margin(top = 3, bottom = 5))
        root.addView(actionButton("DRAG AND SIZE BUTTONS IN PREVIEW", primary = true) {
            val previewVisible = visible.toMutableSet()
            val previewPositions = positions.toMutableMap()
            val previewSizes = sizes.toMutableMap()
            val layoutChanged = positionedLayout != layout.selectedItemPosition || positionedDock != dock.selectedItemPosition
            if (layoutChanged) previewPositions.clear()
            TouchHudLayoutEditor(this).show(
                previewVisible,
                previewPositions,
                previewSizes,
                controlSets.toMap(),
                layout.selectedItemPosition,
                dock.selectedItemPosition,
                scale.progress + 70,
                opacity.progress + 30,
            ) {
                visible.clear()
                visible.addAll(previewVisible)
                positions.clear()
                positions.putAll(previewPositions)
                sizes.clear()
                sizes.putAll(previewSizes)
                positionedLayout = layout.selectedItemPosition
                positionedDock = dock.selectedItemPosition
                checkboxes.forEach { (id, check) -> check.isChecked = id in visible }
            }
        }, margin(top = 7, bottom = 6))
        val backupButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val export = Button(this).apply { text = "EXPORT HUD" }
        val import = Button(this).apply { text = "IMPORT HUD" }
        backupButtons.addView(export, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        backupButtons.addView(import, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(backupButtons)
        export.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "IronHarbor-HUD-Layout.ihhud")
            }, REQUEST_EXPORT_HUD)
        }
        import.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, REQUEST_IMPORT_HUD)
        }
        val scroll = ScrollView(this).apply { addView(root) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Customize floating HUD")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save HUD", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = checkboxes.filter { it.second.isChecked }.mapTo(linkedSetOf()) { it.first }
                val previousLayout = prefs.getInt(TouchHudSettings.HUD_LAYOUT, TouchHudSettings.LAYOUT_FREE)
                val previousDock = prefs.getInt(TouchHudSettings.HUD_DOCK, 0)
                if ((previousLayout != layout.selectedItemPosition || previousDock != dock.selectedItemPosition) &&
                    (positionedLayout != layout.selectedItemPosition || positionedDock != dock.selectedItemPosition)
                ) {
                    positions.clear()
                }
                val edit = prefs.edit()
                    .putString(TouchHudSettings.HUD_VISIBLE, TouchHudSettings.encodeVisible(selected))
                    .putString(TouchHudSettings.HUD_CONTROL_SETS, TouchHudSettings.encodeControlSets(controlSets))
                    .putString(TouchHudSettings.HUD_SIZES, TouchHudSettings.encodeSizes(sizes))
                    .putString(TouchHudSettings.HUD_POSITIONS, TouchHudSettings.encodePositions(positions))
                    .putInt(TouchHudSettings.HUD_SCALE, TouchHudSettings.clampScale(scale.progress + 70))
                    .putInt(TouchHudSettings.HUD_OPACITY, TouchHudSettings.clampOpacity(opacity.progress + 30))
                    .putInt(TouchHudSettings.HUD_LAYOUT, layout.selectedItemPosition)
                    .putInt(TouchHudSettings.HUD_DOCK, dock.selectedItemPosition)
                    .putBoolean(TouchHudSettings.HUD_ENABLED, true)
                edit.apply()
                dialog.dismiss()
                Toast.makeText(this, "HUD settings saved. They apply the next time you start a match.", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    private fun chooseTouchHudKey() {
        val actions = TouchHudSettings.remappableActions
        AlertDialog.Builder(this)
            .setTitle("Choose command to remap")
            .setItems(actions.map { it.label }.toTypedArray()) { _, which -> captureTouchHudKey(actions[which].id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportHudLayout(uri: Uri) {
        val prefs = getSharedPreferences(TouchHudSettings.PREFS, MODE_PRIVATE)
        val payload = TouchHudSettings.encodeLayoutBackup(
            TouchHudSettings.parseVisible(prefs.getString(TouchHudSettings.HUD_VISIBLE, null), prefs.contains(TouchHudSettings.HUD_VISIBLE)),
            TouchHudSettings.parsePositions(prefs.getString(TouchHudSettings.HUD_POSITIONS, null)),
            TouchHudSettings.parseSizes(prefs.getString(TouchHudSettings.HUD_SIZES, null)),
            prefs.getInt(TouchHudSettings.HUD_SCALE, 100),
            prefs.getInt(TouchHudSettings.HUD_OPACITY, 88),
            prefs.getInt(TouchHudSettings.HUD_LAYOUT, TouchHudSettings.LAYOUT_FREE),
            TouchHudSettings.remappableActions.mapNotNull { action ->
                if (prefs.contains("key_${action.id}")) action.id to prefs.getInt("key_${action.id}", 0) else null
            }.toMap(),
            TouchHudSettings.parseControlSets(prefs.getString(TouchHudSettings.HUD_CONTROL_SETS, null)),
            prefs.getInt(TouchHudSettings.HUD_DOCK, 0),
        )
        runCatching { (contentResolver.openOutputStream(uri, "wt") ?: error("Selected document could not be opened")).bufferedWriter().use { it.write(payload) } }
            .onSuccess { Toast.makeText(this, "HUD layout exported.", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, "Could not export HUD layout: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun importHudLayout(uri: Uri) {
        val payload = runCatching {
            val input = contentResolver.openInputStream(uri) ?: error("Selected document could not be opened")
            input.bufferedReader().use { reader ->
                val content = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (content.length + count > 65536) error("HUD layout file is larger than 64 KB")
                    content.append(buffer, 0, count)
                }
                content.toString()
            }
        }.getOrElse {
            Toast.makeText(this, "Could not read HUD layout: ${it.message}", Toast.LENGTH_LONG).show()
            return
        }
        val imported = TouchHudSettings.decodeLayoutBackup(payload)
        if (imported == null) {
            Toast.makeText(this, "That file is not a supported Iron Harbor HUD layout.", Toast.LENGTH_LONG).show()
            return
        }
        val edit = getSharedPreferences(TouchHudSettings.PREFS, MODE_PRIVATE).edit()
            .putString(TouchHudSettings.HUD_VISIBLE, TouchHudSettings.encodeVisible(imported.visible))
            .putString(TouchHudSettings.HUD_CONTROL_SETS, TouchHudSettings.encodeControlSets(imported.controlSets))
            .putString(TouchHudSettings.HUD_POSITIONS, TouchHudSettings.encodePositions(imported.positions))
            .putString(TouchHudSettings.HUD_SIZES, TouchHudSettings.encodeSizes(imported.sizes))
            .putInt(TouchHudSettings.HUD_SCALE, imported.scale)
            .putInt(TouchHudSettings.HUD_OPACITY, imported.opacity)
            .putInt(TouchHudSettings.HUD_LAYOUT, imported.layout)
            .putInt(TouchHudSettings.HUD_DOCK, imported.dock)
            .putBoolean(TouchHudSettings.HUD_ENABLED, true)
        TouchHudSettings.remappableActions.forEach { edit.remove("key_${it.id}") }
        imported.keyBindings.forEach { (id, keyCode) -> edit.putInt("key_$id", keyCode) }
        edit.apply()
        Toast.makeText(this, "HUD layout imported. It applies when the next match starts.", Toast.LENGTH_LONG).show()
    }

    private fun captureTouchHudKey(id: String) {
        val prefs = getSharedPreferences(TouchHudSettings.PREFS, MODE_PRIVATE)
        val action = TouchHudSettings.buttons.first { it.id == id }
        val code = prefs.getInt("key_$id", action.defaultKeyCode)
        val prompt = TextView(this).apply {
            val currentKey = if (code == KeyEvent.KEYCODE_UNKNOWN) "Not bound" else KeyEvent.keyCodeToString(code)
            text = "Current key: $currentKey\n\nTap this panel, then press the physical keyboard key already assigned to ${action.label} in the game. The HUD sends that key through VERA20K's existing engine input path."
            textSize = 16f
            setTextColor(color(R.color.launcher_text))
            setPadding(dp(24), dp(24), dp(24), dp(24))
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_UNKNOWN) return@setOnKeyListener false
                if ((event.source and android.view.InputDevice.SOURCE_KEYBOARD) != android.view.InputDevice.SOURCE_KEYBOARD) return@setOnKeyListener true
                prefs.edit().putInt("key_$id", keyCode).apply()
                Toast.makeText(this@LauncherActivity, "${action.label} bound to ${KeyEvent.keyCodeToString(keyCode)}", Toast.LENGTH_SHORT).show()
                true
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Bind ${action.label}")
            .setView(prompt)
            .setPositiveButton("Done", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener { prompt.requestFocus() }
        dialog.show()
    }

    private fun renderMods() {
        pageBody.addView(kicker("CONTENT PROFILES"))
        pageBody.addView(text("Mods", 30, R.color.launcher_text, bold = true))
        val current = card()
        current.addView(kicker("ENGINE CONFIGURATION"))
        current.addView(text(
            "Next launch: ${modCatalog.selectedMod ?: "Vanilla Yuri's Revenge"}",
            16,
            R.color.launcher_text,
            bold = true,
        ))
        current.addView(text(
            "INI profile: ${modCatalog.profile.title}",
            14,
            R.color.launcher_muted,
        ), margin(top = 5))
        current.addView(text(
            "Selections are saved through the engine config writer and read when the game process starts. They do not replace resources in an active match.",
            13,
            R.color.launcher_muted,
        ), margin(top = 8))
        if (!installation.ready) {
            current.addView(text(
                "Import a valid Yuri's Revenge installation before changing the active mod or profile.",
                13,
                R.color.launcher_warning,
            ), margin(top = 8))
        }
        if (modCatalog.profile == LauncherIniProfile.AUTO) {
            current.addView(text(
                "Auto detects INI roots. It keeps the Yuri's Revenge archive family; choose Mental Omega for expandmo archives.",
                13,
                R.color.launcher_warning,
            ), margin(top = 8))
        }
        if (modCatalog.configError != null) {
            current.addView(text(
                "Cannot read config.toml: ${modCatalog.configError}",
                13,
                R.color.launcher_warning,
            ), margin(top = 8))
        }
        if (!modCatalog.containsSelectedMod()) {
            current.addView(text(
                "The configured mod folder is missing. Select Vanilla or an installed folder to repair the choice.",
                13,
                R.color.launcher_warning,
            ), margin(top = 8))
        }
        pageBody.addView(current, margin(bottom = 12))

        val profiles = card()
        profiles.addView(kicker("SUPPORTED INI ROOT PROFILES"))
        listOf(LauncherIniProfile.YR, LauncherIniProfile.MO, LauncherIniProfile.AUTO).forEach { profile ->
            profiles.addView(actionButton(
                if (modCatalog.profile == profile) "✓  ${profile.title.uppercase()}  ·  SELECTED" else profile.title.uppercase(),
                primary = modCatalog.profile == profile,
            ) { applyModSelection(modCatalog.selectedMod, profile) }, margin(top = 7))
        }
        if (modCatalog.profile == LauncherIniProfile.CUSTOM) {
            profiles.addView(text(
                "Custom is already selected in engine config. Editing its INI roots in the launcher is not supported yet.",
                13,
                R.color.launcher_warning,
            ), margin(top = 8))
        }
        pageBody.addView(profiles, margin(bottom = 12))

        val installed = card()
        installed.addView(kicker("INSTALLED DATA MODS"))
        installed.addView(actionButton(
            if (modImporting) "IMPORTING MOD…" else "IMPORT MOD FOLDER",
            primary = false,
        ) { if (!modImporting) chooseModFolder() }, margin(top = 7))
        if (modImporting) {
            installed.addView(text(
                "Copying ${modCopiedBytes / (1024 * 1024)} MB into private Mods storage. The source folder is unchanged.",
                13,
                R.color.launcher_muted,
            ), margin(top = 7))
            installed.addView(actionButton("CANCEL MOD IMPORT", primary = false) {
                if (ModImportJob.cancelActive()) {
                    modImportNote = "Cancelling mod import; temporary files will be cleaned up."
                    renderPage()
                }
            }, margin(top = 7))
        }
        installed.addView(text(
            "Choose one mod folder to copy into private storage. DLL or executable patches are not run by VERA20K, and compatibility depends on engine support.",
            13,
            R.color.launcher_muted,
        ), margin(top = 8))
        installed.addView(actionButton(
            if (modCatalog.selectedMod == null) "✓  VANILLA YURI'S REVENGE  ·  SELECTED" else "USE VANILLA YURI'S REVENGE",
            primary = modCatalog.selectedMod == null,
        ) { applyModSelection(null, null) }, margin(top = 10))
        if (modCatalog.installedMods.isEmpty()) {
            installed.addView(text("No mod folders found under Documents/RA2IRONHARBOR/Mods.", 13, R.color.launcher_muted), margin(top = 8))
        }
        modCatalog.installedMods.forEach { folder ->
            installed.addView(text(folder, 15, R.color.launcher_text, bold = true), margin(top = 13))
            installed.addView(actionButton(
                if (folder.equals(modCatalog.selectedMod, ignoreCase = true)) "✓  SELECTED FOR NEXT LAUNCH" else "USE THIS MOD",
                primary = folder.equals(modCatalog.selectedMod, ignoreCase = true),
            ) { applyModSelection(folder, null) }, margin(top = 5))
        }
        pageBody.addView(installed)
        modSelectionNote?.let { note ->
            pageBody.addView(text(note, 13, R.color.launcher_muted), margin(top = 10))
        }
        modImportNote?.let { note ->
            pageBody.addView(text(note, 13, R.color.launcher_muted), margin(top = 8))
        }
    }

    private fun renderMaps() {
        pageBody.addView(kicker("CUSTOM SCENARIOS"))
        pageBody.addView(text("Maps", 30, R.color.launcher_text, bold = true))
        pageBody.addView(text(
            "Add your own map files. The engine scans this folder in Choose Map after the game and active mod folders.",
            14,
            R.color.launcher_muted,
        ), margin(bottom = 14))

        val location = runCatching { File(nativeMapsDirectory(filesDir.absolutePath)).canonicalFile }
        val mapDirectory = location.getOrNull()
        val card = card()
        card.addView(kicker("ENGINE MAP LIBRARY"))
        card.addView(text(mapDirectory?.absolutePath ?: "Engine map folder unavailable", 12, R.color.launcher_muted))
        card.addView(text("Editable folder: Documents/RA2IRONHARBOR/Maps", 12, R.color.launcher_accent), margin(top = 6))
        if (mapDirectory == null) {
            card.addView(text(
                "The current config.toml could not be resolved. Fix the engine configuration before importing maps.",
                13,
                R.color.launcher_warning,
            ), margin(top = 8))
        } else {
            val maps = mapDirectory.listFiles()?.filter { file ->
                file.isFile && file.extension.lowercase(Locale.ROOT) in setOf("mmx", "yro", "map", "mpr", "yrm")
            }?.sortedBy { it.name.lowercase(Locale.ROOT) }.orEmpty()
            card.addView(text("${maps.size} map file${if (maps.size == 1) "" else "s"} available", 15, R.color.launcher_text, bold = true), margin(top = 8))
            if (maps.isEmpty()) {
                card.addView(text("No custom maps imported yet.", 13, R.color.launcher_muted), margin(top = 5))
            } else {
                maps.forEach { file ->
                    card.addView(text(file.name, 14, R.color.launcher_text), margin(top = 7))
                }
            }
        }
        pageBody.addView(card, margin(bottom = 12))

        if (mapImporting) {
            val progress = card()
            progress.addView(kicker("IMPORT IN PROGRESS"))
            progress.addView(text("Imported $mapCopiedCount map file${if (mapCopiedCount == 1) "" else "s"}…", 14, R.color.launcher_text))
            progress.addView(ProgressBar(this).apply { isIndeterminate = true }, margin(top = 9, bottom = 8))
            progress.addView(actionButton("CANCEL MAP IMPORT", primary = false) {
                MapImportJob.cancelActive()
                mapImportNote = "Cancelling after the current file; completed imports will remain available."
                renderPage()
            })
            pageBody.addView(progress, margin(bottom = 12))
        } else if (mapDirectory != null) {
            pageBody.addView(actionButton("ADD CUSTOM MAPS", primary = true) {
                chooseMaps()
            }, margin(bottom = 10))
        }
        pageBody.addView(text(
            "Supported engine formats: .mmx, .yro, .map, .mpr, and .yrm. Existing files are never replaced; name conflicts receive a numbered filename. Game map assets are not included.",
            12,
            R.color.launcher_muted,
        ))
        mapImportNote?.let { note ->
            pageBody.addView(text(note, 13, R.color.launcher_muted), margin(top = 10))
        }
    }

    private fun chooseMaps() {
        if (!ensureSharedStorage()) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_PICK_MAPS)
    }

    private fun importMaps(uris: List<Uri>) {
        val job = MapImportJob.begin()
        if (job == null) {
            mapImportNote = "Another custom map import is already running."
            renderPage()
            return
        }
        page = Page.MAPS
        mapImporting = true
        mapCopiedCount = 0
        mapImportNote = "Preparing selected map files…"
        renderPage()
        Thread({
            var result: String
            var completedCount = 0
            try {
                val sources = uris.map { uri ->
                    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }?.takeIf { it.isNotBlank() } ?: throw IOException("The selected provider did not supply a filename")
                    MapImportJob.Source(name) { destination ->
                        job.checkCancelled()
                        contentResolver.openInputStream(uri)?.use { input ->
                            destination.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    job.checkCancelled()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                            }
                        } ?: throw IOException("Cannot read $name")
                    }
                }
                val mapsDirectory = File(nativeMapsDirectory(filesDir.absolutePath))
                val imported = job.run(mapsDirectory, sources) { count ->
                    completedCount = count
                    runOnUiThread {
                        mapCopiedCount = count
                        if (page == Page.MAPS) renderPage()
                    }
                }
                val shared = SharedDataStorage(this)
                imported.forEach { shared.copyLocalFile("Maps", it) }
                result = "Imported ${imported.size} map file${if (imported.size == 1) "" else "s"} into Documents/RA2IRONHARBOR/Maps. The engine refreshes its mirror before launch."
            } catch (_: MapImportJob.Cancelled) {
                result = "Map import cancelled. $completedCount completed file${if (completedCount == 1) " remains" else "s remain"}; existing files were not replaced."
            } catch (error: Throwable) {
                result = "Map import stopped: ${error.message ?: error.javaClass.simpleName}. $completedCount completed file${if (completedCount == 1) " remains" else "s remain"}."
            } finally {
                job.close()
            }
            runOnUiThread {
                mapImporting = false
                mapImportNote = result
                renderPage()
            }
        }, "vera20k-map-import").start()
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
        infoRow(device, "Performance profile", "In-game Options > Performance Stats shows frame/tick rate, CPU phases, core load and GPU timing")
        infoRow(device, "Launcher telemetry", "Performance samples are not yet exported to the launcher or log ZIP")
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
            appendLine("The attached ZIP includes retained engine logs, except.txt when present, and a bounded best-effort Android logcat capture filtered to this app UID.")
            appendLine("The logcat capture cannot include system-wide logs or guarantee that entries remain available.")
            appendLine("No game assets are included in this report.")
        }
        exportingDiagnostics = true
        renderPage()
        Toast.makeText(this, "Preparing the full diagnostics ZIP…", Toast.LENGTH_SHORT).show()
        Thread({
            try {
                val logcat = AndroidLogcatCapture.capture(android.os.Process.myUid(), cacheDir)
                val bundle = DiagnosticLogArchive.create(filesDir, cacheDir, report, logcat)
                runCatching { SharedDataStorage(this).copyLocalFile("Logs", bundle.file) }
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
        modCatalog = LauncherModCatalog.read(filesDir)
        renderPage()
    }

    private fun applyModSelection(modName: String?, profile: LauncherIniProfile?) {
        if (!installation.ready) {
            modSelectionNote = "Import a valid game installation before changing a mod or INI profile."
            renderPage()
            return
        }
        if (modCatalog.configError != null) {
            modSelectionNote = "Fix the unreadable config before changing a mod or profile."
            renderPage()
            return
        }
        if (modName != null && modCatalog.installedMods.none { it == modName }) {
            modSelectionNote = "That mod folder is no longer available. Refresh the launcher and try again."
            refreshInstallation()
            return
        }
        try {
            val contents = if (profile == null && LauncherModCatalog.requiresMoProfile(modName.orEmpty())) {
                nativeConfigForSelection(modName.orEmpty(), LauncherIniProfile.MO.configValue, filesDir.absolutePath)
            } else if (profile == null) {
                nativeConfigForMod(modName.orEmpty(), filesDir.absolutePath)
            } else {
                nativeConfigForSelection(modName.orEmpty(), profile.configValue, filesDir.absolutePath)
            }
            LauncherConfigPublisher.publish(filesDir, contents)
            modSelectionNote = if (profile == null) {
                "Saved. The engine selected a complete INI root profile for ${modName ?: "Vanilla Yuri's Revenge"}; it takes effect next launch."
            } else {
                "Saved. ${modName ?: "Vanilla Yuri's Revenge"} with ${profile.title} will be read at the next game launch."
            }
            refreshInstallation()
        } catch (error: Exception) {
            modSelectionNote = "Selection was not applied: ${error.message ?: error.javaClass.simpleName}"
            renderPage()
        }
    }

    private fun chooseStorageLocation() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_PICK_STORAGE)
    }

    private fun ensureSharedStorage(): Boolean {
        if (SharedDataStorage(this).treeUri() != null) return true
        storageNote = "First choose Documents. The launcher will create IronHarborRA2YR and its Mods/Maps/Saves/Logs folders."
        page = Page.FILES
        renderPage()
        chooseStorageLocation()
        return false
    }

    private fun chooseGameFolder() {
        if (!ensureSharedStorage()) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_PICK_GAME)
    }

    private fun chooseModFolder() {
        if (!ensureSharedStorage()) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_PICK_MOD)
    }

    private fun importMod(tree: Uri) {
        val job = ModImportJob.begin()
        if (job == null) {
            modImportNote = "Another mod import is already running."
            renderPage()
            return
        }
        modImporting = true
        modCopiedBytes = 0
        modReportedBytes = 0
        modImportNote = "Checking the selected mod folder…"
        page = Page.MODS
        renderPage()
        Thread({
            val result = try {
                val name = selectedTreeName(tree)
                val destination = job.run(
                    filesDir,
                    name,
                    { staging ->
                        val count = copyModTree(tree, staging, job)
                        if (count == 0) throw IOException("The selected folder contains no files")
                    },
                    { staging ->
                        // Defer checks until a base game exists. Otherwise ask
                        // the engine to inspect this exact staging tree before
                        // it can appear as an installed, selectable mod.
                        if (installation.ready) {
                            if (LauncherModCatalog.requiresMoProfile(name)) {
                                nativeConfigForSelection(
                                    staging.name,
                                    LauncherIniProfile.MO.configValue,
                                    filesDir.absolutePath,
                                )
                            } else {
                                nativeConfigForMod(staging.name, filesDir.absolutePath)
                            }
                        }
                        SharedDataStorage(this).copyLocalDirectory("Mods", staging, name)
                    },
                )
                if (installation.ready) {
                    "Imported and engine-validated $name (${modCopiedBytes / (1024 * 1024)} MB) to Documents/RA2IRONHARBOR/Mods/$name. It is ready to select; edits and removals are picked up before launch."
                } else {
                    "Imported $name (${modCopiedBytes / (1024 * 1024)} MB) to Documents/RA2IRONHARBOR/Mods/$name. Profile validation is pending until a game installation is imported."
                }
            } catch (_: ModImportJob.Cancelled) {
                "Mod import cancelled. The selected mod folder was not changed."
            } catch (error: Throwable) {
                "Mod import failed: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                job.close()
                runCatching {
                    contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            runOnUiThread {
                modImporting = false
                modImportNote = result
                refreshInstallation()
            }
        }, "vera20k-mod-import").start()
    }

    private fun selectedTreeName(tree: Uri): String {
        val id = DocumentsContract.getTreeDocumentId(tree)
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        return contentResolver.query(
            document,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.takeIf { it.isNotBlank() } ?: throw IOException("Cannot read the selected folder name")
    }

    private fun copyModTree(tree: Uri, directory: File, job: ModImportJob): Int =
        copyModChildren(tree, DocumentsContract.getTreeDocumentId(tree), directory, job)

    private fun copyModChildren(tree: Uri, documentId: String, directory: File, job: ModImportJob): Int {
        job.checkCancelled()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        var copiedFiles = 0
        contentResolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                job.checkCancelled()
                val id = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2)
                val destination = AssetImportJob.child(directory, name)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!destination.mkdir()) throw IOException("Cannot create folder $name")
                    copiedFiles += copyModChildren(tree, id, destination, job)
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
                                modCopiedBytes += count
                            }
                        }
                    } ?: throw IOException("Cannot read $name")
                    copiedFiles++
                    if (modCopiedBytes - modReportedBytes >= PROGRESS_STEP_BYTES) {
                        val megabytes = modCopiedBytes / (1024 * 1024)
                        runOnUiThread {
                            modImportNote = "Copying mod files · $megabytes MB"
                            if (page == Page.MODS) renderPage()
                        }
                        modReportedBytes = modCopiedBytes
                    }
                }
            }
        } ?: throw IOException("Cannot list the selected mod folder")
        return copiedFiles
    }

    private fun importGame(tree: Uri, retainGameSource: Boolean) {
        val job = AssetImportJob.begin()
        if (job == null) {
            importNote = "Another import is already running."
            renderPage()
            return
        }
        importing = true
        copiedBytes = 0
        reportedBytes = 0
        importNote = "Preparing the private engine copy…"
        page = Page.FILES
        renderPage()
        Thread({
            var sourceSaved = false
            val result = try {
                val free = filesDir.usableSpace
                if (free < REQUIRED_FREE_BYTES) {
                    throw IOException("${free shr 20} MB free; at least ${REQUIRED_FREE_BYTES shr 20} MB is required")
                }
                val missing = missingRequiredArchives(tree)
                if (missing.isNotEmpty()) {
                    throw IOException("Selected folder is missing ${missing.joinToString()}")
                }
                val sharedGameName = selectedTreeName(tree)
                job.run(
                    filesDir,
                    { staging -> copyTree(tree, staging, job) },
                    { staging ->
                        nativeConfigForImport(staging.absolutePath, filesDir.absolutePath)
                    },
                )
                if (retainGameSource) {
                    SharedDataStorage(this).setGameSource(tree, sharedGameName)
                } else {
                    SharedDataStorage(this).clearGameSource()
                }
                sourceSaved = true
                if (retainGameSource) {
                    "Using $sharedGameName as the source. The original stays in place; one private engine copy is synchronized before play. Saves and logs use Documents/IronHarborRA2YR."
                } else {
                    "Prepared $sharedGameName as a private engine copy. This folder provider did not allow persistent access, so later source edits will not sync. Saves and logs use Documents/IronHarborRA2YR."
                }
            } catch (_: AssetImportJob.Cancelled) {
                "Import cancelled. The previous installation remains active."
            } catch (error: Throwable) {
                "Import failed: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                job.close()
                if (!sourceSaved) runCatching {
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
        if (validatingLaunch || !launchGate.begin()) return
        validatingLaunch = true
        renderPage()
        Thread({
            val validation = runCatching {
                syncUserManagedData()
                nativeValidateCurrentSelection(filesDir.absolutePath)
            }
            runOnUiThread {
                validatingLaunch = false
                if (isFinishing || isDestroyed) {
                    launchGate.completedWithoutLaunch()
                    return@runOnUiThread
                }
                validation.fold(
                    onSuccess = {
                        try {
                            gameWasLaunched = true
                            startActivity(Intent(this, VeraActivity::class.java))
                        } catch (error: Exception) {
                            gameWasLaunched = false
                            launchGate.completedWithoutLaunch()
                            Toast.makeText(this, "Could not start the game: ${error.message}", Toast.LENGTH_LONG).show()
                            renderPage()
                        }
                    },
                    onFailure = { error ->
                        launchGate.completedWithoutLaunch()
                        modSelectionNote = "Cannot launch this game/mod selection: ${error.message ?: error.javaClass.simpleName}. Choose a profile whose required roots are available."
                        modCatalog = LauncherModCatalog.read(filesDir)
                        page = Page.MODS
                        renderPage()
                    },
                )
            }
        }, "vera20k-launch-preflight").start()
    }

    /** SAF documents are mirrored to app-private paths because Rust consumes filesystem paths. */
    private fun syncUserManagedData() {
        val storage = SharedDataStorage(this)
        val sourceTree = storage.gameSourceTree()
        if (sourceTree != null) {
            val engineGameDirectory = installation.configuredDirectory
                ?: throw IOException("No engine game directory is configured")
            storage.mirrorGameSource(engineGameDirectory)
        }
        if (storage.treeUri() == null) return // Keep installations launchable if Documents access was revoked.
        storage.ensureLayout()
        if (sourceTree == null) {
            val gameFolder = storage.activeGameFolder()
            if (gameFolder != null) {
                val engineGameDirectory = installation.configuredDirectory
                    ?: throw IOException("No engine game directory is configured")
                storage.mirrorChildFolder("Game", gameFolder, engineGameDirectory)
            }
        }
        storage.mirrorFolder("Mods", File(filesDir, "Mods"))
        val mapsDirectory = File(nativeMapsDirectory(filesDir.absolutePath))
        storage.mirrorFolder("Maps", mapsDirectory)
        storage.mirrorFolder("Saves", File(filesDir, "saves"))
    }

    private fun publishRuntimeData() {
        val storage = SharedDataStorage(this)
        if (storage.treeUri() == null) return
        Thread({
            val outcome = runCatching {
                storage.ensureLayout()
                storage.publishLocalFolder("Saves", File(filesDir, "saves"))
                storage.publishLocalFolder("Logs", File(filesDir, "logs"))
            }
            outcome.exceptionOrNull()?.let { error ->
                runOnUiThread {
                    storageNote = "Could not update shared saves/logs: ${error.message ?: error.javaClass.simpleName}"
                    if (!isFinishing && !isDestroyed && page == Page.FILES) renderPage()
                }
            }
        }, "ironharbor-publish-runtime-data").start()
    }

    private fun migrateExistingDataToDocuments() {
        Thread({
            val storage = SharedDataStorage(this)
            val notes = mutableListOf<String>()
            File(filesDir, "Mods").listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { mod ->
                runCatching { storage.copyLocalDirectory("Mods", mod, mod.name) }
                    .onFailure { notes += "${mod.name} left in private storage (${it.message})" }
            }
            runCatching {
                val maps = File(nativeMapsDirectory(filesDir.absolutePath))
                maps.listFiles()?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in setOf("mmx", "yro", "map", "mpr", "yrm") }
                    ?.forEach { map -> runCatching { storage.copyLocalFile("Maps", map) } }
            }
            runCatching { storage.publishLocalFolder("Saves", File(filesDir, "saves")) }
                .onFailure { notes += "some saves could not be copied (${it.message})" }
            runCatching { storage.publishLocalFolder("Logs", File(filesDir, "logs")) }
                .onFailure { notes += "some logs could not be copied (${it.message})" }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                storageNote = if (notes.isEmpty()) {
                    "${storage.displayLocation()} is ready. Game files remain in their selected folder; runtime data is kept in Documents."
                } else {
                    "Documents storage is ready. ${notes.joinToString("; ")}. Existing files were not overwritten."
                }
                refreshInstallation()
            }
        }, "ironharbor-documents-migration").start()
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
        background = rounded(color(R.color.launcher_surface), color(R.color.launcher_border), 18)
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
        textSize = if (primary) 16f else 13f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.045f
        gravity = Gravity.CENTER
        minHeight = dp(if (primary) 62 else 54)
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
