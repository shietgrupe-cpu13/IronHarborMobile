//! Gameplay actions submitted by Android's floating HUD.

use std::sync::Mutex;

static QUEUE: Mutex<Vec<(i32, bool)>> = Mutex::new(Vec::new());

/// Queue a C1..C10 tap/hold. Returns `None` if the queue cannot accept it.
pub fn push_control_group(group: i32, held: bool) -> Option<bool> {
    let mut queue = QUEUE.lock().ok()?;
    if queue.len() >= 64 {
        return None;
    }
    let wake = queue.is_empty();
    queue.push((group, held));
    Some(wake)
}

pub(crate) fn take_control_groups() -> Vec<(i32, bool)> {
    QUEUE
        .lock()
        .map(|mut queue| std::mem::take(&mut *queue))
        .unwrap_or_default()
}
