//! Android entry point for the VERA20k client.
//!
//! Android never runs `main`. `GameActivity` calls into the cdylib through
//! `initializeNativeCode` (the activity loads `libvera20k_android.so` before
//! `onCreate`), so the process starts here instead. Everything
//! platform-specific that has to happen before the first window — the working
//! directory, logging, panic reporting — runs in this order and then hands over
//! to `vera20k::entry`, the same start-up the desktop binary uses.
//!
//! One process gets one `android_main` call per activity creation; the activity
//! is destroyed and recreated on rotation, a locale change or a configuration
//! change the manifest does not absorb. `EventLoop` can be built only once per
//! process (winit keeps it in a static), so a second call must not reach
//! `EventLoop::builder()` — see the guard below. Killing the process on that
//! path is deliberate for the first milestone: a stale surface is a black
//! screen, while a restart is recoverable. P5 replaces it with real
//! suspend/resume handling.
#![cfg(target_os = "android")]

use android_activity::AndroidApp;
use jni::JavaVM;
use jni::objects::{JObject, JString};
use jni::sys::jstring;
use winit::platform::android::EventLoopBuilderExtAndroid;

/// Run `f` with the activity this process belongs to.
///
/// The picks go through `ndk_context`'s raw pointers rather than
/// `AndroidApp`'s own helpers: the engine calls this from inside winit's event
/// callback, where android-activity's activity lock is already held, and taking
/// it again would deadlock the loop.
fn with_activity<R>(f: impl FnOnce(&mut jni::JNIEnv, &JObject) -> R) -> Option<R> {
    let context = ndk_context::android_context();
    // SAFETY: android-activity published both handles at start-up and keeps the
    // Java VM alive for the process's lifetime; the activity reference is a JNI
    // global reference the loader owns.
    let vm = unsafe { JavaVM::from_raw(context.vm().cast()) }.ok()?;
    let mut env = vm.attach_current_thread().ok()?;
    // SAFETY: the activity handle is the loader's JNI global reference above.
    let activity = unsafe { JObject::from_raw(context.context().cast()) };
    Some(f(&mut env, &activity))
}

/// Display-inset hook for `vera20k::util::display_insets`.
///
/// The activity measures the cutout and the top-left rounded corner from the
/// window insets; the engine moves the message lane and the performance panel in
/// by them.
fn display_insets() -> vera20k::util::display_insets::Insets {
    with_activity(|env, activity| {
        let inset = |env: &mut jni::JNIEnv, name: &str| -> i32 {
            env.call_method(activity, name, "()I", &[])
                .and_then(|value| value.i())
                .unwrap_or(0)
        };
        vera20k::util::display_insets::Insets {
            left: inset(env, "displayInsetLeft"),
            top: inset(env, "displayInsetTop"),
        }
    })
    .unwrap_or_default()
}

/// Calls one of the activity's Mods page actions, `method(String...)Z` with
/// `args`; false when it cannot.
fn activity_flag(method: &str, args: &[&str]) -> bool {
    with_activity(|env, activity| {
        let mut strings = Vec::with_capacity(args.len());
        for arg in args {
            strings.push(env.new_string(arg).ok()?);
        }
        let values: Vec<jni::objects::JValue> = strings.iter().map(|text| text.into()).collect();
        let signature = format!("({})Z", "Ljava/lang/String;".repeat(args.len()));
        env.call_method(activity, method, signature, &values)
            .ok()?
            .z()
            .ok()
    })
    .flatten()
    .unwrap_or(false)
}

/// Mods page: export `folder` through the system file manager; the toasts
/// say `done` or `failed`.
fn mod_export(folder: &str, done: &str, failed: &str) -> bool {
    activity_flag("exportMod", &[folder, done, failed])
}

/// Mods page: pick a mod ZIP in the system file manager; the toast says
/// `failed` when the copy does not work.
fn mod_pick_zip(failed: &str) -> bool {
    activity_flag("pickModZip", &[failed])
}

/// Mods page: pick a mod folder in the system file manager.
fn mod_pick_folder(failed: &str) -> bool {
    activity_flag("pickModFolder", &[failed])
}

/// The mod part a picker copied in, once.
fn mod_take_picked() -> Option<String> {
    with_activity(|env, activity| {
        let value = env
            .call_method(activity, "takePickedModPart", "()Ljava/lang/String;", &[])
            .ok()?
            .l()
            .ok()?;
        env.get_string(&JString::from(value))
            .ok()
            .map(|text| text.to_string_lossy().into_owned())
    })
    .flatten()
    .filter(|part| !part.is_empty())
}

/// Mods page: delete the copies the pickers made.
fn mod_clear_picked() {
    let _ = with_activity(|env, activity| {
        env.call_method(activity, "clearPickedModParts", "()V", &[])
            .is_ok()
    });
}

/// Mods page: forget `folder`'s home-screen shortcuts (`id` and its old name).
fn mod_forget(folder: &str, id: &str) -> bool {
    activity_flag("forgetModShortcut", &[folder, id])
}

/// Mods page: add a home-screen shortcut for `folder` under `id`.
fn mod_pin(folder: &str, id: &str) -> bool {
    activity_flag("pinMod", &[folder, id])
}

/// Resolves a `modid:<id>` entry to its folder by the id file the Mods folder
/// keeps, so a shortcut survives a rename.
fn folder_for_mod_id(data_dir: &std::path::Path, entry: &str) -> Option<String> {
    let id = entry.strip_prefix("modid:")?.trim();
    if id.is_empty() {
        return None;
    }
    let mods = data_dir.join("Mods");
    let entries = std::fs::read_dir(mods).ok()?;
    for entry in entries.flatten() {
        let dir = entry.path();
        if !dir.is_dir() {
            continue;
        }
        let Ok(text) = std::fs::read_to_string(dir.join(".vera20k-mod-id")) else {
            continue;
        };
        if text.trim() == id {
            return entry.file_name().to_str().map(|name| format!("mod:{name}"));
        }
    }
    None
}

/// The entry a launcher shortcut handed over (`VeraActivity.takeLaunchEntry`),
/// read once: `mod:<folder>` picks that mod for this launch.
fn launch_entry() -> Option<String> {
    with_activity(|env, activity| {
        let value = env
            .call_method(activity, "takeLaunchEntry", "()Ljava/lang/String;", &[])
            .ok()?
            .l()
            .ok()?;
        env.get_string(&JString::from(value))
            .ok()
            .map(|text| text.to_string_lossy().into_owned())
    })
    .flatten()
    .filter(|entry| !entry.is_empty())
}

/// Folder-picker hook for `vera20k::util::folder_picker`.
///
/// The picker is the one thing the NDK cannot do itself: a Storage Access
/// Framework tree is readable only through Java's `ContentResolver`, so the
/// activity starts `ACTION_OPEN_DOCUMENT_TREE` and reports back through
/// [`Java_org_vera20k_VeraActivity_nativeOnFolderPicked`].
fn request_folder_picker() -> bool {
    with_activity(|env, activity| {
        env.call_method(activity, "startFolderPicker", "()V", &[])
            .is_ok()
    })
    .unwrap_or(false)
}

fn cancel_import() -> bool {
    with_activity(|env, activity| {
        env.call_method(activity, "cancelImport", "()Z", &[])
            .and_then(|value| value.z())
            .unwrap_or(false)
    })
    .unwrap_or(false)
}

/// Launcher and app-switcher icon hook for `vera20k::util::app_icon`.
///
/// The choice lives in the two activity aliases, which only `PackageManager`
/// can flip; the activity keeps that knowledge in [`VeraActivity.applyAppIcon`].
fn apply_app_icon(vera_icon: bool) {
    let applied = with_activity(|env, activity| {
        env.call_method(
            activity,
            "applyAppIcon",
            "(Z)V",
            &[jni::objects::JValue::Bool(u8::from(vera_icon))],
        )
        .is_ok()
    });
    if applied != Some(true) {
        log::warn!("Could not apply the app icon");
    }
}

/// LAN network hooks for `vera20k::util::lan_network`.
///
/// Android routes a datagram by the process's default network, so the LAN visit
/// runs bound to Wi-Fi while the lobby is open; the activity reports changes
/// back through [`Java_org_vera20k_VeraActivity_nativeOnLanNetworkChanged`].
fn bind_lan_network() {
    let bound = with_activity(|env, activity| {
        env.call_method(activity, "bindLanNetwork", "()V", &[]).is_ok()
    });
    if bound != Some(true) {
        log::warn!("Could not bind the LAN visit to the Wi-Fi network");
    }
}

fn release_lan_network() {
    let _ = with_activity(|env, activity| {
        env.call_method(activity, "releaseLanNetwork", "()V", &[])
            .is_ok()
    });
}

/// Default-handle suffix for `vera20k::util::device_identity`.
///
/// A LAN visit drops advertisements whose name matches the local one, and the
/// stock default is `[New Player]` on every install. Android has no text input
/// yet, so the engine appends the activity's Android ID suffix to the default
/// and two fresh installs no longer hide each other's games.
fn handle_suffix() -> Option<String> {
    let suffix = with_activity(|env, activity| {
        let value = env
            .call_method(activity, "defaultHandleSuffix", "()Ljava/lang/String;", &[])
            .ok()?
            .l()
            .ok()?;
        if value.is_null() {
            return None;
        }
        env.get_string(&JString::from(value))
            .ok()
            .map(|text| text.to_string_lossy().into_owned())
    })?;
    suffix.filter(|text| !text.is_empty())
}

/// The activity, kept for the soft-keyboard hooks: [`AndroidApp`] is cheap to
/// clone, and its text-input calls work from any thread.
static ACTIVITY: std::sync::OnceLock<AndroidApp> = std::sync::OnceLock::new();

/// Show the soft keyboard (`util::android_ime`).
fn ime_show() {
    if let Some(app) = ACTIVITY.get() {
        app.show_soft_input(false);
    }
}

/// Hide the soft keyboard.
fn ime_hide() {
    if let Some(app) = ACTIVITY.get() {
        app.hide_soft_input(false);
    }
}

/// What the input method currently holds.
fn ime_state() -> vera20k::util::android_ime::TextState {
    ACTIVITY
        .get()
        .map(|app| {
            let state = app.text_input_state();
            vera20k::util::android_ime::TextState {
                text: state.text,
                cursor: state.selection.start,
                compose: state.compose_region.map(|span| (span.start, span.end)),
            }
        })
        .unwrap_or_default()
}

/// Replace the input method's buffer, with the caret at `cursor` characters.
fn ime_set_state(text: &str, cursor: usize) {
    use android_activity::input::{TextInputState, TextSpan};
    if let Some(app) = ACTIVITY.get() {
        app.set_text_input_state(TextInputState {
            text: text.to_owned(),
            selection: TextSpan {
                start: cursor,
                end: cursor,
            },
            compose_region: None,
        });
    }
}

/// Tell the input method whether the focused edit hides its text
/// (`app::shell_wol::login::ime_secret`), so its dictionary and suggestions
/// never see a password.
fn ime_set_secret(secret: bool) {
    let _ = with_activity(|env, activity| {
        env.call_method(
            activity,
            "setImeKind",
            "(Z)V",
            &[jni::objects::JValue::Bool(secret as u8)],
        )
        .map(|_| ())
    });
}

/// The activity's folder-picker answer: the chosen tree as a `content://` URI,
/// or an empty string when the player cancelled. Java chooses direct access
/// when the user opted in and the tree resolves to local primary storage;
/// otherwise it uses the existing SAF copy importer.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_VeraActivity_nativeOnFolderPicked(
    mut env: jni::JNIEnv,
    activity: JObject,
    tree_uri: JString,
) {
    let picked = env
        .get_string(&tree_uri)
        .ok()
        .map(|text| text.to_string_lossy().into_owned())
        .filter(|text| !text.is_empty());
    match &picked {
        Some(uri) => {
            log::info!("asset import: folder picked: {uri}");
            // The dialog switches to its copying step before the copy's first
            // progress callback can arrive.
            vera20k::util::import_status::note(vera20k::util::import_status::ImportEvent::Started);
            // Java resolves the SAF tree to an opted-in direct path when
            // possible, or copies it through ContentResolver as a fallback.
            if let Err(error) = env.call_method(
                &activity,
                "onGameFolderPicked",
                "(Ljava/lang/String;)V",
                &[jni::objects::JValue::Object(&tree_uri)],
            ) {
                let _ = env.exception_clear();
                vera20k::util::import_status::note(
                    vera20k::util::import_status::ImportEvent::Failed(format!(
                        "Could not start the copy: {error}"
                    )),
                );
            }
        }
        None => log::info!("asset import: folder picker cancelled"),
    }
    vera20k::util::folder_picker::deliver(picked);
}

/// The import finished: `ok:<path>` after the copy and config.toml are written,
/// `failed:<reason>` otherwise. Runs on the copy's own thread, so it only
/// records the outcome.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_VeraActivity_nativeImportFinished(
    mut env: jni::JNIEnv,
    _activity: JObject,
    result: JString,
) {
    let text = env
        .get_string(&result)
        .ok()
        .map(|value| value.to_string_lossy().into_owned())
        .unwrap_or_default();
    use vera20k::util::import_status::{self, ImportEvent};
    if text == "cancelled" {
        import_status::note(ImportEvent::Cancelled);
    } else if let Some(path) = text.strip_prefix("ok:") {
        log::info!("asset source configured at {path}");
        import_status::note(ImportEvent::Done(path.to_owned()));
    } else {
        let reason = text.strip_prefix("failed:").unwrap_or(&text).to_owned();
        log::error!("asset import: {reason}");
        import_status::note(ImportEvent::Failed(reason));
    }
    let _ = env.exception_clear();
}

/// Prepare the next config using the engine's existing config editor. Java
/// atomically publishes it after the SAF copy is complete (or immediately when
/// selecting a shared-storage profile directory).
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_VeraActivity_nativeConfigForImport(
    mut env: jni::JNIEnv,
    _activity: JObject,
    directory: JString,
    settings_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let directory: String = env.get_string(&directory)?.into();
        let settings_directory: String = env.get_string(&settings_directory)?.into();
        let settings_path = std::path::PathBuf::from(&settings_directory);
        let previous = android_config_contents(&settings_path)?;
        let contents = vera20k::util::config::set_paths_key(&previous, "ra2_dir", &directory);
        let contents = vera20k::util::config::set_paths_key(
            &contents,
            "settings_dir",
            &settings_directory,
        );
        Ok(env.new_string(contents)?.into_raw())
    })();
    match result {
        Ok(contents) => contents,
        Err(error) => {
            let _ = env.throw_new("java/io/IOException", error.to_string());
            std::ptr::null_mut()
        }
    }
}

/// Prepare a config for a game selected in place. Retail files remain at
/// `directory`; user profiles, mods, maps, saves and logs live under
/// `settings_directory` (Documents/RA2Harbor).
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_VeraActivity_nativeConfigForDirectSelection(
    mut env: jni::JNIEnv,
    _activity: JObject,
    directory: JString,
    settings_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let directory: String = env.get_string(&directory)?.into();
        let settings_directory: String = env.get_string(&settings_directory)?.into();
        let settings_path = std::path::PathBuf::from(&settings_directory);
        let previous = android_config_contents(&settings_path)?;
        let contents = vera20k::util::config::set_paths_key(&previous, "ra2_dir", &directory);
        let contents = vera20k::util::config::set_paths_key(
            &contents,
            "settings_dir",
            &settings_directory,
        );
        Ok(env.new_string(contents)?.into_raw())
    })();
    match result {
        Ok(contents) => contents,
        Err(error) => {
            let _ = env.throw_new("java/io/IOException", error.to_string());
            std::ptr::null_mut()
        }
    }
}

/// Validate a selected directory using the engine's real archive resolver,
/// before Java persists it as the active installation.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_VeraActivity_nativeValidateGameDirectory(
    mut env: jni::JNIEnv,
    _activity: JObject,
    directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<String> {
        let directory: String = env.get_string(&directory)?.into();
        let path = std::path::Path::new(&directory);
        vera20k::assets::asset_manager::AssetManager::inspect_install(
            path,
            vera20k::assets::asset_manager::MediaArchiveMode::STOCK_DIGITAL,
        )
        .map(|_| format!("ok:{}", path.display()))
        .map_err(|error| anyhow::anyhow!("{}: {error}", path.display()))
    })();
    let message = match result {
        Ok(message) => message,
        Err(error) => format!("error:{error:#}"),
    };
    match env.new_string(message) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// The import's copy progress, in bytes. Runs on the copy's own thread.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_VeraActivity_nativeImportProgress(
    _env: jni::JNIEnv,
    _activity: JObject,
    copied_bytes: jni::sys::jlong,
) {
    use vera20k::util::import_status::{self, ImportEvent};
    import_status::note(ImportEvent::Progress(copied_bytes.max(0) as u64));
}

/// The activity reports that its Wi-Fi network appeared, left or changed.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_VeraActivity_nativeOnLanNetworkChanged(
    _env: jni::JNIEnv,
    _activity: JObject,
) {
    vera20k::util::lan_network::note_changed();
}

/// Bind `nativeOnFolderPicked` to this activity's class.
///
/// Registered from `android_main`, not `JNI_OnLoad`: `FindClass` on the loading
/// thread uses the system class loader and cannot see `org.vera20k.VeraActivity`,
/// so the earlier attempt silently fell through to the symbol lookup that fails
/// on this device (`UnsatisfiedLinkError` for a name `readelf --dyn-syms` shows
/// in the packaged library). Here the activity object is at hand, and the class
/// it reports carries the app's own loader.
///
/// Returns whether the binding took; a caller reports it rather than letting a
/// later crash say so.
fn register_folder_picker_native() -> bool {
    with_activity(|env, activity| {
        let Ok(class) = env.get_object_class(activity) else {
            let _ = env.exception_clear();
            return false;
        };
        let methods = [
            jni::NativeMethod {
                name: "nativeOnFolderPicked".into(),
                sig: "(Ljava/lang/String;)V".into(),
                fn_ptr: Java_org_vera20k_VeraActivity_nativeOnFolderPicked as *mut std::ffi::c_void,
            },
            jni::NativeMethod {
                name: "nativeImportFinished".into(),
                sig: "(Ljava/lang/String;)V".into(),
                fn_ptr: Java_org_vera20k_VeraActivity_nativeImportFinished as *mut std::ffi::c_void,
            },
            jni::NativeMethod {
                name: "nativeImportProgress".into(),
                sig: "(J)V".into(),
                fn_ptr: Java_org_vera20k_VeraActivity_nativeImportProgress as *mut std::ffi::c_void,
            },
            jni::NativeMethod {
                name: "nativeConfigForImport".into(),
                sig: "(Ljava/lang/String;)Ljava/lang/String;".into(),
                fn_ptr: Java_org_vera20k_VeraActivity_nativeConfigForImport
                    as *mut std::ffi::c_void,
            },
            jni::NativeMethod {
                name: "nativeOnLanNetworkChanged".into(),
                sig: "()V".into(),
                fn_ptr: Java_org_vera20k_VeraActivity_nativeOnLanNetworkChanged
                    as *mut std::ffi::c_void,
            },
        ];
        let registered = env.register_native_methods(class, &methods).is_ok();
        if !registered {
            let _ = env.exception_clear();
        }
        registered
    })
    .unwrap_or(false)
}

/// Ask the system to keep the display on while this window is visible.
///
/// The intro movie runs for minutes with nothing to touch, and Android's own
/// display timeout would otherwise cut it off; no synthetic input event needs
/// to stand in for a tap.
///
/// Only called before the event loop starts, on purpose:
/// `AndroidApp::set_window_flags` takes the activity's lock, and winit holds it
/// for the whole of an event callback, so setting the flag from inside one (say,
/// when a movie begins) deadlocks the loop. The flag lives with the window, so
/// one call at start-up covers every screen until the activity is destroyed.
fn keep_screen_on(app: &AndroidApp) {
    use android_activity::WindowManagerFlags;
    app.set_window_flags(
        WindowManagerFlags::KEEP_SCREEN_ON,
        WindowManagerFlags::empty(),
    );
}

/// Read one entry from the APK's assets through Android's AssetManager.
fn packaged_bytes(app: &AndroidApp, file: &str) -> std::io::Result<Vec<u8>> {
    let name = std::ffi::CString::new(file)?;
    let mut asset = app
        .asset_manager()
        .open(&name)
        .ok_or_else(|| std::io::Error::new(std::io::ErrorKind::NotFound, file.to_owned()))?;
    Ok(asset.buffer()?.to_vec())
}

fn install_packaged_map(app: &AndroidApp) -> anyhow::Result<()> {
    use vera20k::map::installed::{ARCTIC_MAP, install_arctic_map};

    let maps = vera20k::util::config::GameConfig::load()?
        .paths
        .downloaded_maps_dir();
    install_arctic_map(&maps, || packaged_bytes(app, ARCTIC_MAP))?;
    Ok(())
}

/// Use the player-selected shared data directory when its pointer is valid.
/// The pointer itself remains in app-private storage so it can be read before
/// the engine changes its working directory.
fn android_user_data_root(internal_dir: &std::path::Path) -> Option<std::path::PathBuf> {
    let pointer = internal_dir.join("launcher-data-root.txt");
    let value = std::fs::read_to_string(pointer).ok()?;
    let path = std::path::PathBuf::from(value.trim());
    (path.is_absolute() && path.is_dir()).then_some(path)
}

/// Read the configuration associated with the selected Android data root,
/// then fall back to the app's previous private configuration during migration.
fn android_config_contents(data_dir: &std::path::Path) -> anyhow::Result<String> {
    let active_dir = vera20k::util::platform_dir::get();
    let mut candidates = vec![
        data_dir.join("config.toml"),
        data_dir.join("Config/config.toml"),
    ];
    if let Some(active_dir) = active_dir {
        for candidate in [
            active_dir.join("config.toml"),
            active_dir.join("Config/config.toml"),
        ] {
            if !candidates.contains(&candidate) {
                candidates.push(candidate);
            }
        }
    }
    for path in candidates {
        match std::fs::read_to_string(&path) {
            Ok(contents) => return Ok(contents),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => return Err(error.into()),
        }
    }
    Ok(String::new())
}

/// Resolved by `android-activity`'s loader trampoline; the name is the ABI.
///
/// Android calls the symbol with the activity's handle, which
/// `android-activity` turns into an `AndroidApp`.
#[unsafe(no_mangle)]
fn android_main(app: AndroidApp) {
    keep_screen_on(&app);
    vera20k::util::folder_picker::install(request_folder_picker, cancel_import);
    // The screen's rounded corner and cutout hide the top-left of what the game
    // draws there; the activity measures them and the engine moves in by them.
    vera20k::util::display_insets::install(vera20k::util::display_insets::Hooks {
        insets: display_insets,
    });
    // The Mods page cannot hand a path to a file manager on Android: export,
    // Add Mod's pickers and the shortcut pin run in the activity.
    vera20k::util::mod_actions::install(vera20k::util::mod_actions::Hooks {
        export: mod_export,
        pin: mod_pin,
        forget: mod_forget,
        pick_zip: mod_pick_zip,
        pick_folder: mod_pick_folder,
        take_picked: mod_take_picked,
        clear_picked: mod_clear_picked,
    });
    // The launcher icon is a compiled resource, so the VERA Icon box's choice
    // reaches the two activity aliases only through PackageManager.
    vera20k::util::app_icon::install(apply_app_icon);
    // LAN discovery rides the Wi-Fi network: the lobby binds this process to it
    // and the activity reports network changes back through the JNI callback.
    vera20k::util::lan_network::install(vera20k::util::lan_network::Hooks {
        acquire: bind_lan_network,
        release: release_lan_network,
    });
    // The stock LAN default name is the same on every install and two peers left
    // at it never see each other, so the activity supplies an Android ID suffix
    // for the handle until text input lands on this platform.
    vera20k::util::device_identity::install(vera20k::util::device_identity::Hooks {
        handle_suffix,
    });
    // winit's Android backend has no IME text events (winit#4736 adds them), so
    // the engine reads the activity's GameTextInput buffer through these hooks.
    let _ = ACTIVITY.set(app.clone());
    vera20k::util::android_ime::install(vera20k::util::android_ime::Hooks {
        show: ime_show,
        hide: ime_hide,
        state: ime_state,
        set_state: ime_set_state,
        set_secret: ime_set_secret,
    });
    // Android starts every process in "/" with no writable home. Adopt the
    // selected shared data root when its app-private pointer is valid; otherwise
    // use the app's private folder. The chosen root becomes the working and
    // settings directory before logs, config.toml, saves or replays are opened.
    let data_dir = app.internal_data_path();
    match app.internal_data_path() {
        Some(internal_dir) => {
            let dir = android_user_data_root(&internal_dir).unwrap_or(internal_dir);
            if let Err(error) = vera20k::entry::adopt_android_data_dir(&dir) {
                eprintln!("cannot use the app data directory: {error:#}");
            }
        }
        None => eprintln!("no internal data path; logs and settings will not persist"),
    }

    let log_path = vera20k::entry::init_logging();
    vera20k::entry::log_banner(log_path.as_deref());
    if let Err(error) = install_packaged_map(&app) {
        log::warn!("Could not install the packaged skirmish map: {error:#}");
    }
    if let Some(root) = vera20k::util::platform_dir::get() {
        use vera20k::assets::ice_pack;
        if let Err(error) = ice_pack::install(&root.join(ice_pack::DIRECTORY), |name, _| {
            packaged_bytes(&app, name)
        }) {
            log::warn!("Could not install the packaged ice terrain: {error}");
        }
    }
    // Bind the picker's callback before the player can reach the button, and say
    // whether it took: a silent failure here is what turned a missing folder
    // picker into an `UnsatisfiedLinkError` at the worst moment.
    log::info!(
        "asset import: folder-picker callback registered: {}",
        register_folder_picker_native()
    );

    // Android can start a second activity in this same, still-cached process
    // after the player exits to the launcher and taps the icon again. A second
    // winit event loop is impossible (winit keeps process-wide state) and the
    // guard says so, but returning would leave the new activity on its splash
    // for ever. End the process instead, so that tap is a cold start.
    if !claim_event_loop() {
        log::error!("android_main called again; ending this process for a cold relaunch");
        log::logger().flush();
        std::process::exit(0);
    }

    let event_loop: winit::event_loop::EventLoop<()> = match winit::event_loop::EventLoop::builder()
        .with_android_app(app)
        .build()
    {
        Ok(event_loop) => event_loop,
        Err(error) => {
            log::error!("cannot create the Android event loop: {error}");
            return;
        }
    };

    // Android has no retail command line: the launcher starts an ordinary
    // interactive game. `-SPAWN`, replay and capture launches are desktop
    // automation and stay out of this path.
    let launch_mode = vera20k::app::frontend::launch::AppLaunchMode::Interactive(
        vera20k::app::frontend::startup_options::RetailStartupOptions::default(),
    );

    // A pinned shortcut can name the mod this launch plays; `config.toml` keeps
    // the player's own choice untouched.
    let mut entry = launch_entry();
    if let Some(entry) = entry.as_deref() {
        log::info!("Launch entry: {entry}");
    }
    if let Some(id_entry) = entry.clone().filter(|entry| entry.starts_with("modid:")) {
        match folder_for_mod_id(data_dir.as_deref().unwrap_or(std::path::Path::new(".")), &id_entry) {
            Some(folder_entry) => {
                log::info!("Launch entry {id_entry} is {folder_entry}");
                entry = Some(folder_entry);
            }
            None => log::warn!("Launch entry {id_entry} names no mod here"),
        }
    }
    match vera20k::entry::run_with_loop_and_entry(launch_mode, false, event_loop, entry.as_deref())
    {
        Ok(0) => {}
        // Android has no `-SPAWN` status to propagate; a non-zero one still
        // belongs in the log before the activity finishes.
        Ok(status) => log::warn!("VERA20k exited with status {status}"),
        Err(error) => log::error!("VERA20k stopped: {error:#}"),
    }
    log::logger().flush();

    // One process runs one game. Exiting here (rather than returning and letting
    // Android cache the process) is what makes the launcher's next tap start
    // from a clean slate: the event loop above cannot be rebuilt, so a cached
    // process would serve the new activity nothing but its splash.
    std::process::exit(0);
}

/// Claims this process's single event loop.
///
/// winit keeps its event loop in a static and panics on a second build, and a
/// panic inside the JNI callback aborts the process with no log line. The first
/// caller wins; every later one is told so the activity can finish quietly.
fn claim_event_loop() -> bool {
    use std::sync::OnceLock;
    static CLAIMED: OnceLock<()> = OnceLock::new();
    CLAIMED.set(()).is_ok()
}

use std::ffi::{c_int, c_void};

unsafe extern "C" {
    fn __real_pthread_create(
        thread: *mut *mut c_void,
        attr: *const c_void,
        start_routine: extern "C" fn(*mut c_void) -> *mut c_void,
        arg: *mut c_void,
    ) -> c_int;
}

#[unsafe(no_mangle)]
pub unsafe extern "C" fn __wrap_pthread_create(
    thread: *mut *mut c_void,
    attr: *const c_void,
    start_routine: extern "C" fn(*mut c_void) -> *mut c_void,
    arg: *mut c_void,
) -> c_int {
    use std::mem::MaybeUninit;
    let mut attr_copy: MaybeUninit<libc::pthread_attr_t> = MaybeUninit::uninit();
    let mut attr_ptr = attr;

    // SAFETY: `attr` is either null or an initialised `pthread_attr_t` the
    // caller owns, and `attr_copy` is a local of that same type.
    unsafe {
        if !attr.is_null() {
            std::ptr::copy_nonoverlapping(
                attr as *const libc::pthread_attr_t,
                attr_copy.as_mut_ptr(),
                1,
            );
        } else {
            libc::pthread_attr_init(attr_copy.as_mut_ptr());
        }
        attr_ptr = attr_copy.as_ptr() as *const c_void;
        libc::pthread_attr_setstacksize(attr_copy.as_mut_ptr(), 8 * 1024 * 1024);
        __real_pthread_create(thread, attr_ptr, start_routine, arg)
    }
}
