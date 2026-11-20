//! Input submitted by Android UI that lives beside the native game surface.
//!
//! The Kotlin HUD cannot inject events into NativeActivity's `AInputQueue` by
//! calling `Activity.dispatchKeyEvent`. It queues the same logical key and IME
//! events here and wakes the existing winit loop; `App` then routes them through
//! its normal `WindowEvent` handling.

use std::collections::VecDeque;
use std::sync::{Mutex, OnceLock};

use winit::event::ElementState;
use winit::event_loop::EventLoopProxy;
use winit::keyboard::{Key, KeyCode, NamedKey, PhysicalKey};

#[derive(Debug)]
pub(crate) enum Input {
    Key {
        physical: PhysicalKey,
        logical: Key,
        state: ElementState,
    },
    Text(String),
    ControlGroup {
        slot: usize,
        held: bool,
    },
    CameraZoomLimits {
        minimum: f32,
        maximum: f32,
    },
}

static QUEUE: OnceLock<Mutex<VecDeque<Input>>> = OnceLock::new();
static PROXY: OnceLock<Mutex<Option<EventLoopProxy<()>>>> = OnceLock::new();

fn queue() -> &'static Mutex<VecDeque<Input>> {
    QUEUE.get_or_init(|| Mutex::new(VecDeque::new()))
}

fn proxy_slot() -> &'static Mutex<Option<EventLoopProxy<()>>> {
    PROXY.get_or_init(|| Mutex::new(None))
}

pub(crate) fn install_proxy(proxy: EventLoopProxy<()>) {
    if let Ok(mut slot) = proxy_slot().lock() {
        *slot = Some(proxy);
    }
}

/// Queue one Android `KeyEvent` code. Returns false for keys that have no
/// equivalent in the engine's physical-key vocabulary or when the loop ended.
pub fn key_event(android_key_code: i32, pressed: bool) -> bool {
    let Some((physical, logical)) = key(android_key_code) else {
        return false;
    };
    submit(Input::Key {
        physical,
        logical,
        state: if pressed {
            ElementState::Pressed
        } else {
            ElementState::Released
        },
    })
}

/// Queue text for the game screen's currently focused text field.
pub fn text_input(text: String) -> bool {
    if text.is_empty() || text.len() > 4096 {
        return false;
    }
    submit(Input::Text(text))
}

/// Queue a touch-HUD control-group action. `group_number` is the visible
/// one-based C1..C10 label; the engine slot is zero-based.
pub fn control_group_event(group_number: i32, held: bool) -> bool {
    let Some(slot) = control_group_slot(group_number) else {
        return false;
    };
    submit(Input::ControlGroup { slot, held })
}

/// Queue a presentation-only camera range update from Android's launcher.
pub fn camera_zoom_limits(minimum: f32, maximum: f32) -> bool {
    if !minimum.is_finite()
        || !maximum.is_finite()
        || minimum < crate::util::config::CAMERA_ZOOM_SUPPORTED_MIN
        || maximum > crate::util::config::CAMERA_ZOOM_SUPPORTED_MAX
        || minimum > maximum
    {
        return false;
    }
    submit(Input::CameraZoomLimits { minimum, maximum })
}
fn control_group_slot(group_number: i32) -> Option<usize> {
    match group_number {
        // Match the engine's registered hotkeys: TeamCreate_1 through
        // TeamCreate_9 use slots 1 through 9, while TeamCreate_10 is key 0
        // and uses slot 0. The floating HUD uses human-facing C1..C10 labels.
        1..=9 => Some(group_number as usize),
        10 => Some(0),
        _ => None,
    }
}

fn submit(input: Input) -> bool {
    if let Ok(mut queue) = queue().lock() {
        queue.push_back(input);
    } else {
        return false;
    }
    let sent = proxy_slot()
        .lock()
        .ok()
        .and_then(|slot| slot.as_ref().map(|proxy| proxy.send_event(()).is_ok()))
        .unwrap_or(false);
    if !sent && let Ok(mut queue) = queue().lock() {
        queue.clear();
    }
    sent
}

pub(crate) fn drain() -> Vec<Input> {
    queue()
        .lock()
        .map(|mut queue| queue.drain(..).collect())
        .unwrap_or_default()
}

/// Android `KeyEvent` numeric values mapped to winit's documented physical
/// keys. These are the command keys the customizable HUD can bind.
fn key(android: i32) -> Option<(PhysicalKey, Key)> {
    let code = match android {
        7..=16 => match android {
            7 => KeyCode::Digit0,
            8 => KeyCode::Digit1,
            9 => KeyCode::Digit2,
            10 => KeyCode::Digit3,
            11 => KeyCode::Digit4,
            12 => KeyCode::Digit5,
            13 => KeyCode::Digit6,
            14 => KeyCode::Digit7,
            15 => KeyCode::Digit8,
            16 => KeyCode::Digit9,
            _ => return None,
        },
        29..=54 => match android {
            29 => KeyCode::KeyA,
            30 => KeyCode::KeyB,
            31 => KeyCode::KeyC,
            32 => KeyCode::KeyD,
            33 => KeyCode::KeyE,
            34 => KeyCode::KeyF,
            35 => KeyCode::KeyG,
            36 => KeyCode::KeyH,
            37 => KeyCode::KeyI,
            38 => KeyCode::KeyJ,
            39 => KeyCode::KeyK,
            40 => KeyCode::KeyL,
            41 => KeyCode::KeyM,
            42 => KeyCode::KeyN,
            43 => KeyCode::KeyO,
            44 => KeyCode::KeyP,
            45 => KeyCode::KeyQ,
            46 => KeyCode::KeyR,
            47 => KeyCode::KeyS,
            48 => KeyCode::KeyT,
            49 => KeyCode::KeyU,
            50 => KeyCode::KeyV,
            51 => KeyCode::KeyW,
            52 => KeyCode::KeyX,
            53 => KeyCode::KeyY,
            54 => KeyCode::KeyZ,
            _ => return None,
        },
        19 => KeyCode::ArrowUp,
        20 => KeyCode::ArrowDown,
        21 => KeyCode::ArrowLeft,
        22 => KeyCode::ArrowRight,
        59 => KeyCode::ShiftLeft,
        60 => KeyCode::ShiftRight,
        61 => KeyCode::Tab,
        62 => KeyCode::Space,
        66 => KeyCode::Enter,
        67 => KeyCode::Backspace,
        57 => KeyCode::AltLeft,
        58 => KeyCode::AltRight,
        111 => KeyCode::Escape,
        113 => KeyCode::ControlLeft,
        114 => KeyCode::ControlRight,
        131 => KeyCode::F1,
        132 => KeyCode::F2,
        133 => KeyCode::F3,
        134 => KeyCode::F4,
        135 => KeyCode::F5,
        136 => KeyCode::F6,
        137 => KeyCode::F7,
        138 => KeyCode::F8,
        139 => KeyCode::F9,
        140 => KeyCode::F10,
        141 => KeyCode::F11,
        142 => KeyCode::F12,
        _ => return None,
    };
    let logical = match android {
        7..=16 => Key::Character(char::from(b'0' + (android - 7) as u8).to_string().into()),
        29..=54 => Key::Character(char::from(b'a' + (android - 29) as u8).to_string().into()),
        19 => Key::Named(NamedKey::ArrowUp),
        20 => Key::Named(NamedKey::ArrowDown),
        21 => Key::Named(NamedKey::ArrowLeft),
        22 => Key::Named(NamedKey::ArrowRight),
        59 | 60 => Key::Named(NamedKey::Shift),
        57 | 58 => Key::Named(NamedKey::Alt),
        61 => Key::Named(NamedKey::Tab),
        62 => Key::Named(NamedKey::Space),
        66 => Key::Named(NamedKey::Enter),
        67 => Key::Named(NamedKey::Backspace),
        111 => Key::Named(NamedKey::Escape),
        113 | 114 => Key::Named(NamedKey::Control),
        131..=142 => Key::Named(match android {
            131 => NamedKey::F1,
            132 => NamedKey::F2,
            133 => NamedKey::F3,
            134 => NamedKey::F4,
            135 => NamedKey::F5,
            136 => NamedKey::F6,
            137 => NamedKey::F7,
            138 => NamedKey::F8,
            139 => NamedKey::F9,
            140 => NamedKey::F10,
            141 => NamedKey::F11,
            142 => NamedKey::F12,
            _ => return None,
        }),
        _ => return None,
    };
    Some((PhysicalKey::Code(code), logical))
}

#[cfg(all(test, any(tests_app, tests_app_input_d_z)))]
mod tests {
    use super::*;

    #[test]
    fn maps_hud_stock_actions_and_control_groups_to_physical_keys() {
        assert_eq!(
            key(47).map(|entry| entry.0),
            Some(PhysicalKey::Code(KeyCode::KeyS))
        );
        assert_eq!(
            key(29).map(|entry| entry.0),
            Some(PhysicalKey::Code(KeyCode::KeyA))
        );
        assert_eq!(
            key(32).map(|entry| entry.0),
            Some(PhysicalKey::Code(KeyCode::KeyD))
        );
        assert_eq!(
            key(51).map(|entry| entry.0),
            Some(PhysicalKey::Code(KeyCode::KeyW))
        );
        assert_eq!(
            key(8).map(|entry| entry.0),
            Some(PhysicalKey::Code(KeyCode::Digit1))
        );
        assert_eq!(
            key(7).map(|entry| entry.0),
            Some(PhysicalKey::Code(KeyCode::Digit0))
        );
        assert_eq!(
            key(113).map(|entry| entry.1),
            Some(Key::Named(NamedKey::Control))
        );
    }

    #[test]
    fn refuses_android_key_codes_without_an_engine_key_mapping() {
        assert!(key(0).is_none());
        assert!(key(2000).is_none());
    }

    #[test]
    fn control_group_touch_events_validate_visible_group_numbers() {
        assert_eq!(control_group_slot(1), Some(1));
        assert_eq!(control_group_slot(2), Some(2));
        assert_eq!(control_group_slot(9), Some(9));
        assert_eq!(control_group_slot(10), Some(0));
        assert_eq!(control_group_slot(0), None);
        assert_eq!(control_group_slot(11), None);
    }
}
