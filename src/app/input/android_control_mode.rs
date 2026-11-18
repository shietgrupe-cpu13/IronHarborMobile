use winit::event::{DeviceId, WindowEvent};

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub(crate) enum AndroidControlMode {
    Touch,
    External,
    #[default]
    Mixed,
}

impl AndroidControlMode {
    pub(crate) fn parse(value: Option<&str>) -> Self {
        match value.map(str::trim).map(str::to_ascii_lowercase).as_deref() {
            Some("touch") => Self::Touch,
            Some("external") => Self::External,
            Some("mixed") | None => Self::Mixed,
            _ => Self::Mixed,
        }
    }

    pub(crate) fn allows(self, event: &WindowEvent, dispatched_from_touch: bool) -> bool {
        match event {
            WindowEvent::Touch(_) => self != Self::External,
            WindowEvent::KeyboardInput { device_id, .. } => match self {
                Self::Touch => *device_id == DeviceId::dummy(),
                Self::External => *device_id != DeviceId::dummy(),
                Self::Mixed => true,
            },
            WindowEvent::CursorMoved { device_id, .. }
            | WindowEvent::CursorEntered { device_id }
            | WindowEvent::CursorLeft { device_id }
            | WindowEvent::MouseWheel { device_id, .. }
            | WindowEvent::MouseInput { device_id, .. } => match self {
                Self::Touch => dispatched_from_touch && *device_id == DeviceId::dummy(),
                Self::External => !dispatched_from_touch && *device_id != DeviceId::dummy(),
                Self::Mixed => true,
            },
            _ => true,
        }
    }
}

pub(crate) fn current() -> AndroidControlMode {
    use std::sync::OnceLock;
    static MODE: OnceLock<AndroidControlMode> = OnceLock::new();
    *MODE
        .get_or_init(|| AndroidControlMode::parse(std::env::var("IH_CONTROL_MODE").ok().as_deref()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use winit::dpi::PhysicalPosition;

    #[test]
    fn unknown_and_missing_modes_preserve_mixed_input() {
        assert_eq!(AndroidControlMode::parse(None), AndroidControlMode::Mixed);
        assert_eq!(
            AndroidControlMode::parse(Some(" MIXED ")),
            AndroidControlMode::Mixed
        );
        assert_eq!(
            AndroidControlMode::parse(Some("future-mode")),
            AndroidControlMode::Mixed
        );
    }

    #[test]
    fn touch_external_and_mixed_modes_keep_their_input_boundaries() {
        let touch = WindowEvent::Touch(winit::event::Touch {
            device_id: DeviceId::dummy(),
            phase: winit::event::TouchPhase::Started,
            location: PhysicalPosition::new(5.0, 8.0),
            force: None,
            id: 1,
        });
        let touch_mouse = WindowEvent::MouseInput {
            device_id: DeviceId::dummy(),
            state: winit::event::ElementState::Pressed,
            button: winit::event::MouseButton::Left,
        };
        assert!(AndroidControlMode::Touch.allows(&touch, false));
        assert!(!AndroidControlMode::External.allows(&touch, false));
        assert!(AndroidControlMode::Mixed.allows(&touch, false));
        assert!(AndroidControlMode::Touch.allows(&touch_mouse, true));
        assert!(!AndroidControlMode::Touch.allows(&touch_mouse, false));
        assert!(!AndroidControlMode::External.allows(&touch_mouse, true));
        assert!(AndroidControlMode::Mixed.allows(&touch_mouse, false));
    }
}
