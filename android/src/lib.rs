//! Android entry point for the VERA20k client.
//!
//! Android never runs `main`. `android.app.NativeActivity` loads the cdylib and
//! resolves `android_main` by symbol (the manifest names the library through
//! `android.app.lib_name`), so the process starts here instead. Everything
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
    let activity = unsafe { JObject::from_raw(context.context().cast()) };
    Some(f(&mut env, &activity))
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
        env.call_method(activity, "bindLanNetwork", "()V", &[])
            .is_ok()
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

fn read_launcher_config(
    files_directory: &std::path::Path,
) -> anyhow::Result<(std::path::PathBuf, String)> {
    let config_path = files_directory.join("Config").join("config.toml");
    let contents = match std::fs::read_to_string(&config_path) {
        Ok(contents) => contents,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => String::new(),
        Err(error) => return Err(error.into()),
    };
    Ok((config_path, contents))
}

fn launcher_selection_config(
    files_directory: &std::path::Path,
    mod_name: &str,
    profile: vera20k::util::ini_roots::IniProfile,
) -> anyhow::Result<(String, vera20k::util::config::GameConfig)> {
    let (config_path, previous) = read_launcher_config(files_directory)?;
    let contents = vera20k::util::config::set_ini_profile(
        &vera20k::util::config::set_paths_key(&previous, "mod", mod_name),
        profile,
    );
    let mut config = vera20k::util::config::GameConfig::parse_text_at(&contents, &config_path)
        .map_err(|error| {
            anyhow::anyhow!("Updated config is not accepted by the engine: {error:#}")
        })?;
    if let Some(mod_dir) = launcher_mod_directory(&config)? {
        let definition = vera20k::util::mod_definition::ModDefinition::read(&mod_dir)
            .map_err(anyhow::Error::msg)?;
        if definition.has_file_manifest() {
            config.ini = definition.ini_config(&config.ini);
            let contents = vera20k::util::config::set_ini_root_config(&contents, &config.ini);
            config = vera20k::util::config::GameConfig::parse_text_at(&contents, &config_path)
                .map_err(|error| {
                    anyhow::anyhow!("Resolved mod configuration is invalid: {error:#}")
                })?;
            return Ok((contents, config));
        }
    }
    Ok((contents, config))
}

fn launcher_mod_directory(
    config: &vera20k::util::config::GameConfig,
) -> anyhow::Result<Option<std::path::PathBuf>> {
    let Some(name) = config.paths.mod_name() else {
        return Ok(None);
    };
    let path = std::path::Path::new(name);
    if path.is_absolute() {
        let metadata = std::fs::symlink_metadata(path).map_err(|error| {
            anyhow::anyhow!(
                "The selected mod folder is unavailable at {}: {error}",
                path.display()
            )
        })?;
        anyhow::ensure!(
            metadata.file_type().is_dir() && !metadata.file_type().is_symlink(),
            "The selected absolute mod path must be a real folder"
        );
        return Ok(Some(std::fs::canonicalize(path)?));
    }
    let components: Vec<_> = path.components().collect();
    anyhow::ensure!(
        !name.contains('\\')
            && !components.is_empty()
            && components.len() <= 4
            && components
                .iter()
                .all(|part| matches!(part, std::path::Component::Normal(_))),
        "The selected mod must be a folder path within Mods (up to four levels)"
    );
    let root = config.paths.mods_dir();
    let mut checked = root.clone();
    for component in &components {
        checked.push(component.as_os_str());
        let metadata = std::fs::symlink_metadata(&checked).map_err(|error| {
            anyhow::anyhow!(
                "The selected mod folder is unavailable at {}: {error}",
                checked.display()
            )
        })?;
        anyhow::ensure!(
            metadata.file_type().is_dir() && !metadata.file_type().is_symlink(),
            "The selected mod path contains a non-folder or symbolic link"
        );
    }
    let root = std::fs::canonicalize(&root)?;
    let folder = std::fs::canonicalize(checked)?;
    anyhow::ensure!(
        folder != root && folder.starts_with(&root),
        "The selected mod folder resolves outside the managed Mods directory"
    );
    Ok(Some(folder))
}

fn validate_launcher_selection(
    config: &vera20k::util::config::GameConfig,
) -> anyhow::Result<vera20k::util::ini_roots::IniRoots> {
    let mod_dir = launcher_mod_directory(config)?;
    let definition = mod_dir
        .as_deref()
        .map(vera20k::util::mod_definition::ModDefinition::read)
        .transpose()
        .map_err(anyhow::Error::msg)?;
    let archive_names = definition
        .as_ref()
        .map(|definition| definition.archives.clone())
        .unwrap_or_default();
    let ares_setting = if definition
        .as_ref()
        .is_some_and(|definition| definition.requires_ares)
    {
        vera20k::util::ares_launch::AresSetting::On
    } else {
        vera20k::util::ares_launch::AresSetting::Auto
    };
    let roots = vera20k::assets::asset_manager::AssetManager::inspect_launch_ini_roots_with_resources(
        &config.paths.ra2_dir,
        mod_dir.as_deref(),
        Some(&config.paths.ares_support_dir()),
        vera20k::assets::asset_manager::MediaArchiveMode::STOCK_DIGITAL,
        ares_setting,
        &config.ini,
        archive_names,
    )
    .map_err(|error| {
        anyhow::anyhow!(
            "The selected {:?} INI profile cannot be loaded from the game and mod files: {error:#}",
            config.ini.profile
        )
    })?;
    log::info!("Launcher validated engine INI roots: {:?}", roots);
    Ok(roots)
}

fn launcher_settings_directory(
    files_directory: &std::path::Path,
) -> anyhow::Result<std::path::PathBuf> {
    vera20k::util::platform_dir::set(files_directory.to_path_buf());
    let (config_path, contents) = read_launcher_config(files_directory)?;
    let config = vera20k::util::config::GameConfig::parse_text_at(&contents, &config_path)?;
    Ok(config.paths.settings_dir().to_path_buf())
}

fn write_launcher_ares_setting(
    settings_directory: &std::path::Path,
    setting: vera20k::util::ares_launch::AresSetting,
) -> anyhow::Result<()> {
    use std::io::Write;
    std::fs::create_dir_all(settings_directory)?;
    let target = vera20k::util::case_insensitive_path::find(settings_directory, "VERA.INI")
        .unwrap_or_else(|| settings_directory.join("VERA.INI"));
    let previous = match std::fs::read(&target) {
        Ok(bytes) => bytes,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Vec::new(),
        Err(error) => return Err(error.into()),
    };
    let updated = vera20k::util::ini_writer::set_ini_values(
        &previous,
        "VERA",
        &[("Ares", setting.ini_value())],
    );
    let temporary = target.with_file_name(format!(
        ".VERA.INI.{}.{}.tmp",
        std::process::id(),
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)?
            .as_nanos()
    ));
    let result = (|| -> anyhow::Result<()> {
        let mut file = std::fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&temporary)?;
        file.write_all(&updated)?;
        file.sync_all()?;
        std::fs::rename(&temporary, &target)?;
        if let Ok(directory) = std::fs::File::open(settings_directory) {
            let _ = directory.sync_all();
        }
        Ok(())
    })();
    if result.is_err() {
        let _ = std::fs::remove_file(temporary);
    }
    result
}

fn read_launcher_ares_setting(
    settings_directory: &std::path::Path,
) -> anyhow::Result<vera20k::util::ares_launch::AresSetting> {
    let Some(path) = vera20k::util::case_insensitive_path::find(settings_directory, "VERA.INI")
    else {
        return Ok(vera20k::util::ares_launch::AresSetting::Auto);
    };
    let bytes = std::fs::read(path)?;
    let ini = vera20k::rules::ini_parser::IniFile::from_bytes(&bytes)
        .map_err(|error| anyhow::anyhow!("VERA.INI is invalid: {error}"))?;
    let section = ini
        .section_names()
        .into_iter()
        .find(|name| name.eq_ignore_ascii_case("VERA"));
    let value = section
        .and_then(|name| ini.section(name))
        .and_then(|section| {
            section
                .raw_entries()
                .find(|(key, _)| key.eq_ignore_ascii_case("Ares"))
        })
        .map(|(_, value)| value)
        .unwrap_or("Auto");
    Ok(vera20k::util::ares_launch::AresSetting::from_ini_text(
        value,
    ))
}

fn profile_from_launcher_value(
    value: &str,
) -> anyhow::Result<vera20k::util::ini_roots::IniProfile> {
    match value {
        "ra2" => Ok(vera20k::util::ini_roots::IniProfile::Ra2),
        "yr" => Ok(vera20k::util::ini_roots::IniProfile::Yr),
        "mo" => Ok(vera20k::util::ini_roots::IniProfile::Mo),
        "auto" => Ok(vera20k::util::ini_roots::IniProfile::Auto),
        "custom" => Ok(vera20k::util::ini_roots::IniProfile::Custom),
        _ => anyhow::bail!("Unsupported INI profile: {value}"),
    }
}

/// The activity's folder-picker answer: the chosen tree as a `content://` URI,
/// or an empty string when the player cancelled.
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
            // The copy itself runs on the Java side (only ContentResolver can
            // read a SAF tree); this starts it and returns immediately.
            if let Err(error) = env.call_method(
                &activity,
                "startImport",
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
        log::info!("asset import: copied into {path}; config.toml written");
        import_status::note(ImportEvent::Done(path.to_owned()));
    } else {
        let reason = text.strip_prefix("failed:").unwrap_or(&text).to_owned();
        log::error!("asset import: {reason}");
        import_status::note(ImportEvent::Failed(reason));
    }
    let _ = env.exception_clear();
}

/// Prepare the next config using the same editor as the desktop folder picker.
/// Java atomically publishes it only after the complete copy is ready.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_VeraActivity_nativeConfigForImport(
    mut env: jni::JNIEnv,
    _activity: JObject,
    directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let directory: String = env.get_string(&directory)?.into();
        let config_path = vera20k::util::platform_dir::get()
            .ok_or_else(|| anyhow::anyhow!("The app data directory is unavailable"))?
            .join("Config/config.toml");
        let previous = match std::fs::read_to_string(config_path) {
            Ok(contents) => contents,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => String::new(),
            Err(error) => return Err(error.into()),
        };
        let contents = vera20k::util::config::set_paths_key(&previous, "ra2_dir", &directory);
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

/// Launcher-side variant of the import config builder. The Kotlin launcher
/// passes its app-private files directory directly, so this callback works
/// before NativeActivity has initialized `ndk_context` and does not start a
/// second engine or event loop.
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
        let config_path = std::path::Path::new(&files_directory).join("Config/config.toml");
        let previous = match std::fs::read_to_string(&config_path) {
            Ok(contents) => contents,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => String::new(),
            Err(error) => return Err(error.into()),
        };
        let contents = vera20k::util::config::set_paths_key(
            &vera20k::util::config::set_paths_key(&previous, "ra2_dir", &directory),
            "settings_dir",
            &std::path::Path::new(&files_directory)
                .join("Config")
                .to_string_lossy(),
        );
        let contents = if std::path::Path::new(&directory).join("ra2.mix").is_file()
            && !std::path::Path::new(&directory).join("ra2md.mix").is_file()
        {
            vera20k::util::config::set_ini_profile(
                &contents,
                vera20k::util::ini_roots::IniProfile::Ra2,
            )
        } else {
            contents
        };
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

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeGetCameraZoomLimits(
    mut env: jni::JNIEnv,
    _activity: JObject,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let files_directory: String = env.get_string(&files_directory)?.into();
        let config_path = std::path::Path::new(&files_directory).join("Config/config.toml");
        let contents = match std::fs::read_to_string(config_path) {
            Ok(contents) => contents,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => String::new(),
            Err(error) => return Err(error.into()),
        };
        let (minimum, maximum) = vera20k::util::config::camera_zoom_limits_from_text(&contents)?;
        Ok(env
            .new_string(format!("{minimum:.3},{maximum:.3}"))?
            .into_raw())
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
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeConfigForCameraZoomLimits(
    mut env: jni::JNIEnv,
    _activity: JObject,
    minimum: jni::sys::jfloat,
    maximum: jni::sys::jfloat,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let files_directory: String = env.get_string(&files_directory)?.into();
        let config_path = std::path::Path::new(&files_directory).join("Config/config.toml");
        let previous = match std::fs::read_to_string(config_path) {
            Ok(contents) => contents,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => String::new(),
            Err(error) => return Err(error.into()),
        };
        let contents = vera20k::util::config::set_camera_zoom_limits(&previous, minimum, maximum)?;
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
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeGetRenderOptions(
    mut env: jni::JNIEnv,
    _activity: JObject,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let files_directory: String = env.get_string(&files_directory)?.into();
        let config_path = std::path::Path::new(&files_directory).join("Config/config.toml");
        let contents = match std::fs::read_to_string(config_path) {
            Ok(contents) => contents,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => String::new(),
            Err(error) => return Err(error.into()),
        };
        let (percent, fps) = vera20k::util::config::render_options_from_text(&contents)?;
        Ok(env.new_string(format!("{percent},{fps}"))?.into_raw())
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
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeConfigForRenderOptions(
    mut env: jni::JNIEnv,
    _activity: JObject,
    percent: jni::sys::jint,
    fps: jni::sys::jint,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let files_directory: String = env.get_string(&files_directory)?.into();
        let config_path = std::path::Path::new(&files_directory).join("Config/config.toml");
        let previous = match std::fs::read_to_string(config_path) {
            Ok(contents) => contents,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => String::new(),
            Err(error) => return Err(error.into()),
        };
        let percent = u8::try_from(percent).map_err(|_| anyhow::anyhow!("invalid render scale"))?;
        let fps = u16::try_from(fps).map_err(|_| anyhow::anyhow!("invalid FPS limit"))?;
        let contents = vera20k::util::config::set_render_options(&previous, percent, fps)?;
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

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_CameraZoomSettingsService_nativeApplyCameraZoomLimits(
    _env: jni::JNIEnv,
    _service: JObject,
    minimum: jni::sys::jfloat,
    maximum: jni::sys::jfloat,
) -> jni::sys::jboolean {
    u8::from(vera20k::app::android_input::camera_zoom_limits(
        minimum, maximum,
    ))
}

/// Build the next-launch mod/profile config with the engine's own config
/// writers. Kotlin publishes the returned text atomically after the selection
/// succeeds; the running NativeActivity keeps its current resource set.
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
        let profile = profile_from_launcher_value(&profile)?;
        let (contents, config) =
            launcher_selection_config(std::path::Path::new(&files_directory), &mod_name, profile)?;
        validate_launcher_selection(&config)?;
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

#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeGetAresSetting(
    mut env: jni::JNIEnv,
    _activity: JObject,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let files_directory: String = env.get_string(&files_directory)?.into();
        let settings = launcher_settings_directory(std::path::Path::new(&files_directory))?;
        let setting = read_launcher_ares_setting(&settings)?;
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
        let settings = launcher_settings_directory(std::path::Path::new(&files_directory))?;
        write_launcher_ares_setting(&settings, setting)?;
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

/// Resolve the selection with the same Auto and `mod.ini` resource semantics
/// used by the engine, then persist the concrete profile the asset provider
/// selected. Folder names do not choose profiles.
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
        let (contents, config) =
            launcher_selection_config(std::path::Path::new(&files_directory), &mod_name, profile)?;
        let roots = validate_launcher_selection(&config)?;
        let contents = vera20k::util::config::set_ini_profile(&contents, roots.profile());
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

/// Return the engine-configured downloaded-map folder used by Choose Map.
/// The launcher process has not entered `android_main`, so seed the same
/// Android app-private root the NativeActivity supplies before parsing config.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeMapsDirectory(
    mut env: jni::JNIEnv,
    _activity: JObject,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let files_directory: String = env.get_string(&files_directory)?.into();
        let files_directory = std::path::PathBuf::from(files_directory);
        vera20k::util::platform_dir::set(files_directory.clone());
        let config_path = files_directory.join("Config/config.toml");
        let contents = match std::fs::read_to_string(&config_path) {
            Ok(contents) => contents,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                // Before game import, the engine's default profile still lives
                // under filesDir. No user config exists to override it yet.
                return Ok(env
                    .new_string(files_directory.join("Maps").to_string_lossy().as_ref())?
                    .into_raw());
            }
            Err(error) => return Err(error.into()),
        };
        let config = vera20k::util::config::GameConfig::parse_text_at(&contents, &config_path)
            .map_err(|error| {
                anyhow::anyhow!("Engine config cannot resolve the map folder: {error:#}")
            })?;
        Ok(env
            .new_string(
                config
                    .paths
                    .downloaded_maps_dir()
                    .to_string_lossy()
                    .as_ref(),
            )?
            .into_raw())
    })();
    match result {
        Ok(directory) => directory,
        Err(error) => {
            let _ = env.throw_new("java/io/IOException", error.to_string());
            std::ptr::null_mut()
        }
    }
}

/// Revalidate the persisted selection before the launcher starts NativeActivity.
/// This prevents an older invalid config from reaching engine startup.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_LauncherActivity_nativeValidateCurrentSelection(
    mut env: jni::JNIEnv,
    _activity: JObject,
    files_directory: JString,
) -> jstring {
    let result = (|| -> anyhow::Result<jstring> {
        let files_directory: String = env.get_string(&files_directory)?.into();
        let files_directory = std::path::Path::new(&files_directory);
        let (config_path, contents) = read_launcher_config(files_directory)?;
        let config = vera20k::util::config::GameConfig::parse_text_at(&contents, &config_path)?;
        validate_launcher_selection(&config)?;
        Ok(env
            .new_string("Current game and INI profile validated")?
            .into_raw())
    })();
    match result {
        Ok(message) => message,
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

/// Queue a Kotlin HUD key into the one existing winit input loop.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_VeraActivity_nativeHudKey(
    _env: jni::JNIEnv,
    _activity: JObject,
    android_key_code: jni::sys::jint,
    pressed: jni::sys::jboolean,
) -> jni::sys::jboolean {
    u8::from(vera20k::app::android_input::key_event(
        android_key_code,
        pressed != 0,
    ))
}

/// Queue a touch-HUD C1..C10 tap/hold as a dedicated engine input action.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_VeraActivity_nativeHudControlGroup(
    _env: jni::JNIEnv,
    _activity: JObject,
    group_number: jni::sys::jint,
    held: jni::sys::jboolean,
) -> jni::sys::jboolean {
    u8::from(vera20k::app::android_input::control_group_event(
        group_number,
        held != 0,
    ))
}

/// Queue committed text for the game's current input-method target.
#[unsafe(no_mangle)]
pub extern "system" fn Java_org_vera20k_VeraActivity_nativeSubmitGameText(
    mut env: jni::JNIEnv,
    _activity: JObject,
    text: JString,
) -> jni::sys::jboolean {
    let Ok(text) = env.get_string(&text) else {
        let _ = env.exception_clear();
        return 0;
    };
    u8::from(vera20k::app::android_input::text_input(
        text.to_string_lossy().into_owned(),
    ))
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
            jni::NativeMethod {
                name: "nativeHudKey".into(),
                sig: "(IZ)Z".into(),
                fn_ptr: Java_org_vera20k_VeraActivity_nativeHudKey as *mut std::ffi::c_void,
            },
            jni::NativeMethod {
                name: "nativeHudControlGroup".into(),
                sig: "(IZ)Z".into(),
                fn_ptr: Java_org_vera20k_VeraActivity_nativeHudControlGroup
                    as *mut std::ffi::c_void,
            },
            jni::NativeMethod {
                name: "nativeSubmitGameText".into(),
                sig: "(Ljava/lang/String;)Z".into(),
                fn_ptr: Java_org_vera20k_VeraActivity_nativeSubmitGameText as *mut std::ffi::c_void,
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

/// Resolved by `android-activity`'s loader trampoline; the name is the ABI.
///
/// Android calls the symbol with the activity's handle, which
/// `android-activity` turns into an `AndroidApp`.
#[unsafe(no_mangle)]
fn android_main(app: AndroidApp) {
    let profiled_rayon_workers = configure_profiled_rayon_workers();
    keep_screen_on(&app);
    vera20k::util::folder_picker::install(request_folder_picker, cancel_import);
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
    // Android starts every process in "/" with no writable home. Prefer the
    // user's Documents/RA2Harbor directory when the launcher has established
    // direct shared-storage access. The pointer itself stays in app-private
    // preferences; engine files and generated data live in the selected folder.
    match app.internal_data_path() {
        Some(dir) => {
            let dir = android_user_data_root(&dir).unwrap_or(dir);
            if let Err(error) = vera20k::entry::adopt_android_data_dir(&dir) {
                eprintln!("cannot use the app data directory: {error:#}");
            }
        }
        None => eprintln!("no internal data path; logs and settings will not persist"),
    }

    let log_path = vera20k::entry::init_logging();
    vera20k::entry::log_banner(log_path.as_deref());
    if let Some(workers) = profiled_rayon_workers {
        log::info!("Android profiling build selected Rayon worker count: {workers}");
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
        // Returning lets android-activity finish this NativeActivity cleanly.
        // VeraActivity.onDestroy() then ends the process after Java teardown.
        return;
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

    match vera20k::entry::run_with_loop(launch_mode, false, event_loop) {
        Ok(0) => {}
        // Android has no `-SPAWN` status to propagate; a non-zero one still
        // belongs in the log before the activity finishes.
        Ok(status) => log::warn!("VERA20k exited with status {status}"),
        Err(error) => log::error!("VERA20k stopped: {error:#}"),
    }
    log::logger().flush();

    // Return through android-activity so it can finish the NativeActivity and
    // detach its JNI thread. VeraActivity.onDestroy() ends a finishing game
    // process after super.onDestroy(), keeping the next launch a cold start
    // without bypassing Android's native-window and RenderThread teardown.
}

/// Profiling-only compile-time override. Normal APKs omit the environment
/// variable and preserve Rayon’s automatic Android CPU-set selection.
fn configure_profiled_rayon_workers() -> Option<usize> {
    let requested = option_env!("VERA20K_ANDROID_RAYON_THREADS")?;
    let workers = requested
        .parse::<usize>()
        .ok()
        .and_then(std::num::NonZeroUsize::new)
        .filter(|workers| matches!(workers.get(), 2 | 4 | 6))?;
    match vera20k::sim::parallel::configure_worker_count(workers) {
        Ok(()) => Some(workers.get()),
        Err(error) => {
            eprintln!("could not set profiled Rayon worker count: {error}");
            None
        }
    }
}

fn android_user_data_root(internal_dir: &std::path::Path) -> Option<std::path::PathBuf> {
    let pointer = internal_dir.join("launcher-data-root.txt");
    let value = std::fs::read_to_string(pointer).ok()?;
    let path = std::path::PathBuf::from(value.trim());
    (path.is_absolute() && path.is_dir()).then_some(path)
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
