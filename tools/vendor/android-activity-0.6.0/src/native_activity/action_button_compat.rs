#[derive(Clone, Copy)]
pub(crate) enum ButtonAction {
    Press,
    Release,
    Other,
}

/// Identify the button bit which changed between consecutive pre-API-33
/// motion events. Android's ButtonState reports the post-event pressed mask.
pub(crate) fn changed_button(previous: u32, current: u32, action: ButtonAction) -> u32 {
    match action {
        ButtonAction::Press => current & !previous,
        ButtonAction::Release => previous & !current,
        _ => 0,
    }
}

pub(crate) fn first_button(mask: u32) -> u32 {
    mask & mask.wrapping_neg()
}

#[cfg(test)]
mod tests {
    use super::ButtonAction;
    use super::{changed_button, first_button};

    #[test]
    fn old_android_mouse_button_changes_use_state_delta() {
        const PRIMARY: u32 = 1;
        const SECONDARY: u32 = 2;

        assert_eq!(changed_button(0, PRIMARY, ButtonAction::Press), PRIMARY);
        assert_eq!(
            changed_button(PRIMARY, PRIMARY | SECONDARY, ButtonAction::Press),
            SECONDARY
        );
        assert_eq!(
            changed_button(PRIMARY | SECONDARY, PRIMARY, ButtonAction::Release),
            SECONDARY
        );
        assert_eq!(changed_button(PRIMARY, 0, ButtonAction::Release), PRIMARY);
        assert_eq!(changed_button(0, PRIMARY, ButtonAction::Other), 0);
        assert_eq!(first_button(PRIMARY | SECONDARY), PRIMARY);
    }
}
