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
        let (config_path, _) = read_launcher_config(&settings_path)?;
        let contents = launcher_config_for_game_directory(
            &previous,
            &directory,
            &settings_directory,
            &config_path,
        )?;
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
        let (config_path, _) = read_launcher_config(&settings_path)?;
        let contents = launcher_config_for_game_directory(
            &previous,
            &directory,
            &settings_directory,
            &config_path,
        )?;
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
        let has_base = vera20k::util::case_insensitive_path::find(path, "ra2.mix")
            .is_some_and(|archive| archive.is_file());
        let has_expansion = vera20k::util::case_insensitive_path::find(path, "ra2md.mix")
            .is_some_and(|archive| archive.is_file());
        if has_base && !has_expansion {
            return Ok(format!(
                "ok:{} (base RA2 archive detected; full engine validation occurs during launch)",
                path.display()
            ));
        }
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

fn launcher_profile(value: &str) -> anyhow::Result<vera20k::util::ini_roots::IniProfile> {
    value.parse().map_err(anyhow::Error::msg)
}

fn launcher_config_for_selection(
    data_dir: &std::path::Path,
    mod_name: &str,
    profile: vera20k::util::ini_roots::IniProfile,
) -> anyhow::Result<String> {
    vera20k::util::platform_dir::set(data_dir.to_path_buf());
    let (config_path, previous) = read_launcher_config(data_dir)?;
    let contents = vera20k::util::config::set_paths_key(&previous, "mod", mod_name);
    let contents = vera20k::util::config::set_ini_profile(&contents, profile);
    let config = vera20k::util::config::GameConfig::parse_text_at(&contents, &config_path)
        .map_err(|error| anyhow::anyhow!("The selected configuration is not valid for the engine: {error:#}"))?;
    validate_launcher_paths(&config)?;
    // Read the exact mod manifest through the engine's resource resolver at
    // selection time. Archive-backed root validation occurs once at Play and
    // again in normal engine startup; this avoids parsing mod.ini in Kotlin.
    let _ = selected_launcher_resources_without_archives(&config)?;
    Ok(contents)
}

fn selected_launcher_resources_without_archives(
    config: &vera20k::util::config::GameConfig,
) -> anyhow::Result<vera20k::util::mod_definition::ResourceConfig> {
    let mod_dir = launcher_mod_directory(config)?;
    let Some(mod_dir) = mod_dir else {
        return Ok(vera20k::util::mod_definition::ResourceConfig::from_ini(config.ini.clone()));
    };
    let folder = vera20k::util::mod_package::ModFolder::loose(mod_dir.clone());
    let definition = vera20k::util::mod_definition::ModDefinition::read(&mod_dir)
        .map_err(anyhow::Error::msg)?;
    vera20k::assets::asset_manager::mod_resources(&folder, &definition, &config.ini)
        .map_err(anyhow::Error::msg)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeConfigForImport(
    mut env: jni::JNIEnv,
    _activity: JObject,
    directory: JString,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let directory: String = env.get_string(&directory)?.into();
        let files_directory: String = env.get_string(&files_directory)?.into();
        let data_dir = std::path::Path::new(&files_directory);
        let (config_path, previous) = read_launcher_config(data_dir)?;
        let mut contents = vera20k::util::config::set_paths_key(&previous, "ra2_dir", &directory);
        contents = vera20k::util::config::set_paths_key(&contents, "settings_dir", &files_directory);
        let game_dir = std::path::Path::new(&directory);
        let is_base_ra2 = vera20k::util::case_insensitive_path::find(game_dir, "ra2.mix").is_some()
            && vera20k::util::case_insensitive_path::find(game_dir, "ra2md.mix").is_none();
        if is_base_ra2 {
            contents = vera20k::util::config::set_ini_profile(
                &contents,
                vera20k::util::ini_roots::IniProfile::Ra2,
            );
        } else if let Ok(config) = vera20k::util::config::GameConfig::parse_text_at(&contents, &config_path)
            && config.ini.profile == vera20k::util::ini_roots::IniProfile::Ra2
        {
            contents = vera20k::util::config::set_ini_profile(
                &contents,
                vera20k::util::ini_roots::IniProfile::Yr,
            );
        }
        let _ = vera20k::util::config::GameConfig::parse_text_at(&contents, &config_path)
            .map_err(|error| anyhow::anyhow!("The engine rejected the selected game configuration: {error:#}"))?;
        Ok(env.new_string(contents)?.into_raw())
    })();
    match result {
        Ok(value) => value,
        Err(error) => {
            let _ = env.throw_new("java/io/IOException", error.to_string());
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeConfigForSelection(
    mut env: jni::JNIEnv,
    _activity: JObject,
    mod_name: JString,
    profile: JString,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let mod_name: String = env.get_string(&mod_name)?.into();
        let profile: String = env.get_string(&profile)?.into();
        let files_directory: String = env.get_string(&files_directory)?.into();
        let contents = launcher_config_for_selection(
            std::path::Path::new(&files_directory),
            &mod_name,
            launcher_profile(&profile)?,
        )?;
        Ok(env.new_string(contents)?.into_raw())
    })();
    match result {
        Ok(value) => value,
        Err(error) => {
            let _ = env.throw_new("java/io/IOException", error.to_string());
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeConfigForMod(
    mut env: jni::JNIEnv,
    _activity: JObject,
    mod_name: JString,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let mod_name: String = env.get_string(&mod_name)?.into();
        let files_directory: String = env.get_string(&files_directory)?.into();
        let profile = if mod_name.is_empty() {
            vera20k::util::ini_roots::IniProfile::Yr
        } else {
            vera20k::util::ini_roots::IniProfile::Auto
        };
        let contents = launcher_config_for_selection(
            std::path::Path::new(&files_directory),
            &mod_name,
            profile,
        )?;
        Ok(env.new_string(contents)?.into_raw())
    })();
    match result {
        Ok(value) => value,
        Err(error) => {
            let _ = env.throw_new("java/io/IOException", error.to_string());
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeValidateCurrentSelection(
    mut env: jni::JNIEnv,
    _activity: JObject,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let files_directory: String = env.get_string(&files_directory)?.into();
        let data_dir = std::path::Path::new(&files_directory);
        vera20k::util::platform_dir::set(data_dir.to_path_buf());
        let (config_path, contents) = read_launcher_config(data_dir)?;
        let config = vera20k::util::config::GameConfig::parse_text_at(&contents, &config_path)?;
        let roots = selected_launcher_resources(&config)?;
        let message = format!(
            "Validated {:?} profile; Rules={}, Art={}, AI={}",
            config.ini.profile,
            roots.rules(),
            roots.art(),
            roots.ai(),
        );
        Ok(env.new_string(message)?.into_raw())
    })();
    match result {
        Ok(value) => value,
        Err(error) => {
            let _ = env.throw_new("java/io/IOException", error.to_string());
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeMapsDirectory(
    mut env: jni::JNIEnv,
    _activity: JObject,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let files_directory: String = env.get_string(&files_directory)?.into();
        let data_dir = std::path::PathBuf::from(files_directory);
        vera20k::util::platform_dir::set(data_dir.clone());
        let (path, contents) = read_launcher_config(&data_dir)?;
        let config = vera20k::util::config::GameConfig::parse_text_at(&contents, &path)?;
        Ok(env.new_string(config.paths.downloaded_maps_dir().to_string_lossy())?.into_raw())
    })();
    match result {
        Ok(value) => value,
        Err(error) => {
            let _ = env.throw_new("java/io/IOException", error.to_string());
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeGetAresSetting(
    mut env: jni::JNIEnv,
    _activity: JObject,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let files_directory: String = env.get_string(&files_directory)?.into();
        let value = read_launcher_ares_setting(std::path::Path::new(&files_directory))?;
        Ok(env.new_string(value.ini_value())?.into_raw())
    })();
    match result {
        Ok(value) => value,
        Err(error) => {
            let _ = env.throw_new("java/io/IOException", error.to_string());
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeSetAresSetting(
    mut env: jni::JNIEnv,
    _activity: JObject,
    value: JString,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let value: String = env.get_string(&value)?.into();
        let files_directory: String = env.get_string(&files_directory)?.into();
        let setting = vera20k::util::ares_launch::AresSetting::parse(&value)
            .ok_or_else(|| anyhow::anyhow!("Unsupported Ares setting: {value}"))?;
        write_launcher_ares_setting(std::path::Path::new(&files_directory), setting)?;
        Ok(env.new_string(setting.ini_value())?.into_raw())
    })();
    match result {
        Ok(value) => value,
        Err(error) => {
            let _ = env.throw_new("java/io/IOException", error.to_string());
            std::ptr::null_mut()
        }
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
                sig: "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;".into(),
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
fn android_user_data_root(
    internal_dir: &std::path::Path,
) -> anyhow::Result<Option<std::path::PathBuf>> {
    let pointer = internal_dir.join("launcher-data-root.txt");
    let value = match std::fs::read_to_string(&pointer) {
        Ok(value) => value,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(error.into()),
    };
    let path = std::path::PathBuf::from(value.trim());
    anyhow::ensure!(path.is_absolute(), "the saved shared data path is not absolute");
    anyhow::ensure!(
        path.is_dir(),
        "the saved shared data directory is unavailable at {}; restore folder access instead of choosing another data root",
        path.display()
    );
    Ok(Some(path))
}

/// Read the configuration associated with the selected Android data root,
/// then fall back to the app's previous private configuration during migration.
fn android_config_contents(data_dir: &std::path::Path) -> anyhow::Result<String> {
    let active_dir = vera20k::util::platform_dir::get();
    let mut candidates = vec![
        data_dir.join("Config/config.toml"),
        data_dir.join("config.toml"),
    ];
    if let Some(active_dir) = active_dir {
        for candidate in [
            active_dir.join("Config/config.toml"),
            active_dir.join("config.toml"),
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

fn read_launcher_config(
    data_dir: &std::path::Path,
) -> anyhow::Result<(std::path::PathBuf, String)> {
    let primary = data_dir.join("Config/config.toml");
    let legacy = data_dir.join("config.toml");
    let path = if primary.exists() || !legacy.exists() { primary } else { legacy };
    Ok((path, android_config_contents(data_dir)?))
}

/// Update a game source without losing the selected RA2/YR engine profile.
/// A base RA2 installation has the base archive and no YR expansion archive;
/// switching back to a YR install restores the YR profile when the previous
/// selection was RA2. Every result is parsed and validated by the engine.
fn launcher_config_for_game_directory(
    previous: &str,
    game_directory: &str,
    settings_directory: &str,
    config_path: &std::path::Path,
) -> anyhow::Result<String> {
    use vera20k::util::ini_roots::IniProfile;

    let contents = vera20k::util::config::set_paths_key(previous, "ra2_dir", game_directory);
    let contents = vera20k::util::config::set_paths_key(
        &contents,
        "settings_dir",
        settings_directory,
    );
    let game_dir = std::path::Path::new(game_directory);
    let is_base_ra2 = vera20k::util::case_insensitive_path::find(game_dir, "ra2.mix")
        .is_some_and(|path| path.is_file())
        && vera20k::util::case_insensitive_path::find(game_dir, "ra2md.mix")
            .is_none_or(|path| !path.is_file());
    let profile = vera20k::util::config::GameConfig::parse_text_at(&contents, config_path)
        .map_err(|error| anyhow::anyhow!("The engine rejected the selected game configuration: {error:#}"))?
        .ini
        .profile;
    let contents = if is_base_ra2 {
        vera20k::util::config::set_ini_profile(&contents, IniProfile::Ra2)
    } else if profile == IniProfile::Ra2 {
        vera20k::util::config::set_ini_profile(&contents, IniProfile::Yr)
    } else {
        contents
    };
    let config = vera20k::util::config::GameConfig::parse_text_at(&contents, config_path)
        .map_err(|error| anyhow::anyhow!("The engine rejected the selected game configuration: {error:#}"))?;
    validate_launcher_paths(&config)?;
    Ok(contents)
}

fn launcher_mod_directory(
    config: &vera20k::util::config::GameConfig,
) -> anyhow::Result<Option<std::path::PathBuf>> {
    let Some(name) = config.paths.mod_name() else {
        return Ok(None);
    };
    let requested = std::path::Path::new(name);
    if requested.is_absolute() {
        let metadata = std::fs::symlink_metadata(requested).map_err(|error| {
            anyhow::anyhow!("The selected mod folder is unavailable at {}: {error}", requested.display())
        })?;
        anyhow::ensure!(
            metadata.file_type().is_dir() && !metadata.file_type().is_symlink(),
            "The selected absolute mod path must be a real folder"
        );
        return Ok(Some(std::fs::canonicalize(requested)?));
    }

    let components: Vec<_> = requested.components().collect();
    anyhow::ensure!(
        !name.contains('\\')
            && !components.is_empty()
            && components.len() <= 4
            && components
                .iter()
                .all(|part| matches!(part, std::path::Component::Normal(_))),
        "The selected mod must be a folder path within Mods"
    );
    let root = config.paths.mods_dir();
    let mut path = root.clone();
    for component in components {
        path.push(component.as_os_str());
        let metadata = std::fs::symlink_metadata(&path).map_err(|error| {
            anyhow::anyhow!("The selected mod folder is unavailable at {}: {error}", path.display())
        })?;
        anyhow::ensure!(
            metadata.file_type().is_dir() && !metadata.file_type().is_symlink(),
            "The selected mod path contains a non-folder or symbolic link"
        );
    }
    let root = std::fs::canonicalize(root)?;
    let path = std::fs::canonicalize(path)?;
    anyhow::ensure!(path != root && path.starts_with(&root), "The selected mod folder escapes Mods");
    Ok(Some(path))
}

fn validate_launcher_paths(config: &vera20k::util::config::GameConfig) -> anyhow::Result<()> {
    use vera20k::util::ini_roots::IniProfile;
    let game_dir = &config.paths.ra2_dir;
    anyhow::ensure!(game_dir.is_dir(), "The configured game folder is unavailable: {}", game_dir.display());
    if config.ini.profile == IniProfile::Ra2 {
        anyhow::ensure!(
            vera20k::util::case_insensitive_path::find(game_dir, "ra2.mix").is_some(),
            "The configured Red Alert 2 folder is missing ra2.mix: {}",
            game_dir.display()
        );
    } else {
        anyhow::ensure!(
            vera20k::assets::asset_manager::holds_master_archives(game_dir),
            "The configured Yuri's Revenge folder is missing ra2.mix or ra2md.mix: {}",
            game_dir.display()
        );
    }
    let _ = launcher_mod_directory(config)?;
    Ok(())
}

fn selected_launcher_resources(
    config: &vera20k::util::config::GameConfig,
) -> anyhow::Result<vera20k::util::ini_roots::IniRoots> {
    validate_launcher_paths(config)?;
    let mod_dir = launcher_mod_directory(config)?;
    let folder = mod_dir
        .as_ref()
        .map(|path| vera20k::util::mod_package::ModFolder::loose(path.clone()));
    let definition = mod_dir
        .as_deref()
        .map(vera20k::util::mod_definition::ModDefinition::read)
        .transpose()
        .map_err(anyhow::Error::msg)?;
    let resources = match (&folder, &definition) {
        (Some(folder), Some(definition)) => {
            vera20k::assets::asset_manager::mod_resources(folder, definition, &config.ini)
                .map_err(anyhow::Error::msg)?
        }
        _ => vera20k::util::mod_definition::ResourceConfig::from_ini(config.ini.clone()),
    };
    let ares = read_launcher_ares_setting(config.paths.settings_dir())?;
    let ares = resources.effective_ares(ares).map_err(anyhow::Error::msg)?;
    let manager = vera20k::assets::asset_manager::AssetManager::with_mod_and_ares_support(
        &config.paths.ra2_dir,
        mod_dir.as_deref(),
        Some(&config.paths.ares_support_dir()),
        vera20k::assets::asset_manager::MediaArchiveMode::STOCK_DIGITAL,
        ares,
        &resources.archives,
    )?
    .with_resource_config(&resources)?;
    Ok(manager.ini_roots().clone())
}

fn launcher_ares_path(settings_dir: &std::path::Path) -> std::path::PathBuf {
    let config = settings_dir.join("Config");
    vera20k::util::case_insensitive_path::find(&config, "VERA.INI")
        .or_else(|| vera20k::util::case_insensitive_path::find(settings_dir, "VERA.INI"))
        .unwrap_or_else(|| config.join("VERA.INI"))
}

fn read_launcher_ares_setting(
    settings_dir: &std::path::Path,
) -> anyhow::Result<vera20k::util::ares_launch::AresSetting> {
    let path = launcher_ares_path(settings_dir);
    let bytes = match std::fs::read(path) {
        Ok(bytes) => bytes,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(Default::default()),
        Err(error) => return Err(error.into()),
    };
    let ini = vera20k::rules::ini_parser::IniFile::from_bytes(&bytes)
        .map_err(|error| anyhow::anyhow!("VERA.INI is invalid: {error}"))?;
    let value = ini
        .section_names()
        .into_iter()
        .find(|name| name.eq_ignore_ascii_case("VERA"))
        .and_then(|name| ini.section(name))
        .and_then(|section| section.raw_entries().find(|(key, _)| key.eq_ignore_ascii_case("Ares")))
        .map(|(_, value)| value)
        .unwrap_or("Auto");
    Ok(vera20k::util::ares_launch::AresSetting::from_ini_text(value))
}

fn write_launcher_ares_setting(
    settings_dir: &std::path::Path,
    setting: vera20k::util::ares_launch::AresSetting,
) -> anyhow::Result<()> {
    use std::io::Write;
    let target = launcher_ares_path(settings_dir);
    let previous = match std::fs::read(&target) {
        Ok(bytes) => bytes,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Vec::new(),
        Err(error) => return Err(error.into()),
    };
    std::fs::create_dir_all(target.parent().unwrap_or(settings_dir))?;
    let updated = vera20k::util::ini_writer::set_ini_values(
        &previous,
        "VERA",
        &[("Ares", setting.ini_value())],
    );
    let temporary = target.with_file_name(format!(".VERA.INI.{}.tmp", std::process::id()));
    let result = (|| -> anyhow::Result<()> {
        let mut file = std::fs::OpenOptions::new().write(true).create_new(true).open(&temporary)?;
        file.write_all(&updated)?;
        file.sync_all()?;
        std::fs::rename(&temporary, &target)?;
        Ok(())
    })();
    if result.is_err() {
        let _ = std::fs::remove_file(temporary);
    }
    result
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
    // selected shared data root when its app-private pointer is valid. If a
    // previously selected root is inaccessible, fail closed instead of writing
    // into a new private folder and splitting the player's data.
    let data_dir = app.internal_data_path();
    match app.internal_data_path() {
        Some(internal_dir) => {
            match android_user_data_root(&internal_dir) {
                Ok(Some(dir)) => {
                    if let Err(error) = vera20k::entry::adopt_android_data_dir(&dir) {
                        eprintln!("cannot use the selected shared data directory: {error:#}");
                    }
                }
                Ok(None) => {
                    if let Err(error) = vera20k::entry::adopt_android_data_dir(&internal_dir) {
                        eprintln!("cannot use the app data directory: {error:#}");
                    }
                }
                Err(error) => eprintln!("cannot access the selected shared data directory: {error:#}"),
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
        // Keep larger requests and leave caller-provided stacks alone.
        // Bionic can change the recorded size of a caller's stack even
        // though the caller's allocation stays the same size.
        let mut caller_stack: *mut c_void = std::ptr::null_mut();
        let mut caller_len: usize = 0;
        let caller_owns = libc::pthread_attr_getstack(
            attr_copy.as_ptr(),
            &mut caller_stack,
            &mut caller_len,
        ) == 0
            && !caller_stack.is_null();
        let mut size: usize = 0;
        let read = libc::pthread_attr_getstacksize(attr_copy.as_ptr(), &mut size) == 0;
        let requested = if read { size } else { 0 };
        if let Some(wanted) = vera20k::util::thread_stack::android_stack(requested, caller_owns) {
            libc::pthread_attr_setstacksize(attr_copy.as_mut_ptr(), wanted);
            log::info!("android: thread stack {requested} -> {wanted} bytes");
        }
        __real_pthread_create(thread, attr_ptr, start_routine, arg)
    }
}
