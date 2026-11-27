//! The icon the VERA Icon box selects, on platforms whose icon is not a window
//! attribute.
//!
//! Desktop icon selection is handled by [`crate::app::window_icon`]. Android
//! distribution branding is fixed to Iron Harbor, so Android does not install
//! this hook and [`apply`] is a no-op there.

use std::sync::OnceLock;

static HOOK: OnceLock<fn(bool)> = OnceLock::new();

/// Install a platform hook when one is required.
pub fn install(hook: fn(bool)) {
    let _ = HOOK.set(hook);
}

/// Show the icon the VERA Icon box selected.
///
/// Safe to call on every start-up and on every toggle: without an installed
/// hook it does nothing.
pub fn apply(vera_icon: bool) {
    if let Some(hook) = HOOK.get() {
        hook(vera_icon);
    }
}
