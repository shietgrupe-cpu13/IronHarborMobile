//! Launcher settings that are consumed by the Android engine at next startup.

use std::path::Path;

/// The renderer preference in the same VERA.INI profile the game loads.
pub fn renderer_preference(settings_dir: &Path) -> String {
    crate::app::persistence::options_profile::launcher_graphics_api(settings_dir)
        .ini_value()
        .to_owned()
}

/// Persist a launcher-supported renderer through the engine's VERA.INI owner.
/// The value becomes active on the next game startup.
pub fn set_renderer_preference(settings_dir: &Path, value: &str) -> Result<String, String> {
    crate::app::persistence::options_profile::set_launcher_graphics_api(settings_dir, value)
        .map(|api| api.ini_value().to_owned())
}
