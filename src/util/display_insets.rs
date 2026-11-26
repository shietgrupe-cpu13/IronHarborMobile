//! Android display areas hidden by a rounded corner or camera cutout.
//!
//! The Android activity measures the top-left safe area in window pixels. The
//! tactical message lane and diagnostics panel convert it to their render
//! frame before placing UI. Other platforms leave the hook uninstalled.

use std::sync::OnceLock;

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Insets {
    pub left: i32,
    pub top: i32,
}

pub struct Hooks {
    pub insets: fn() -> Insets,
}

static HOOKS: OnceLock<Hooks> = OnceLock::new();

pub fn install(hooks: Hooks) {
    let _ = HOOKS.set(hooks);
}

/// Convert window-pixel insets to render-frame pixels. A zero-sized window
/// falls back to a 1:1 scale until the platform reports usable dimensions.
pub fn frame_offset(render: (u32, u32), window: (u32, u32)) -> (i32, i32) {
    frame_offset_for(get(), render, window)
}

fn frame_offset_for(insets: Insets, render: (u32, u32), window: (u32, u32)) -> (i32, i32) {
    let sx = if window.0 > 0 {
        f64::from(render.0) / f64::from(window.0)
    } else {
        1.0
    };
    let sy = if window.1 > 0 {
        f64::from(render.1) / f64::from(window.1)
    } else {
        1.0
    };
    (
        (f64::from(insets.left) * sx).round() as i32,
        (f64::from(insets.top) * sy).round() as i32,
    )
}

pub fn get() -> Insets {
    HOOKS
        .get()
        .map(|hooks| (hooks.insets)())
        .unwrap_or_default()
}

#[cfg(all(test, tests_util))]
mod tests {
    use super::*;

    #[test]
    fn frame_offset_scales_each_axis_and_handles_zero_window_size() {
        let insets = Insets { left: 41, top: 19 };
        assert_eq!(frame_offset_for(insets, (600, 300), (1200, 600)), (21, 10));
        assert_eq!(frame_offset_for(insets, (600, 300), (0, 0)), (41, 19));
    }
}
