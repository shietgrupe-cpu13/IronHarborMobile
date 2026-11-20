use std::{
    collections::HashMap,
    marker::PhantomData,
    sync::{Mutex, OnceLock},
};

use crate::input::{
    Axis, Button, ButtonState, EdgeFlags, KeyAction, Keycode, MetaState, MotionAction,
    MotionEventFlags, Pointer, PointersIter, Source, ToolType,
};

#[path = "action_button_compat.rs"]
mod action_button_compat;

type GetActionButton = unsafe extern "C" fn(*const libc::c_void) -> i32;

/// Resolve the API 33 NDK function at runtime so Android 12 and earlier can
/// load the NativeActivity library. A direct ndk-sys call creates a hard
/// libandroid.so dependency that fails before app startup on API < 33.
fn get_action_button_fn() -> Option<GetActionButton> {
    static FUNCTION: OnceLock<Option<GetActionButton>> = OnceLock::new();
    *FUNCTION.get_or_init(|| unsafe {
        let library = libc::dlopen(c"libandroid.so".as_ptr(), libc::RTLD_NOW | libc::RTLD_LOCAL);
        if library.is_null() {
            return None;
        }
        let symbol = libc::dlsym(library, c"AMotionEvent_getActionButton".as_ptr());
        if symbol.is_null() {
            None
        } else {
            // SAFETY: the symbol name and signature are from android/input.h.
            // The library handle is intentionally kept open for process life.
            Some(std::mem::transmute::<*mut libc::c_void, GetActionButton>(
                symbol,
            ))
        }
    })
}

static PREVIOUS_BUTTON_STATES: OnceLock<Mutex<HashMap<i32, u32>>> = OnceLock::new();

/// A motion event
///
/// For general discussion of motion events in Android, see [the relevant
/// javadoc](https://developer.android.com/reference/android/view/MotionEvent).
#[derive(Debug)]
#[repr(transparent)]
pub struct MotionEvent<'a> {
    ndk_event: ndk::event::MotionEvent,
    _lifetime: PhantomData<&'a ndk::event::MotionEvent>,
}
impl<'a> MotionEvent<'a> {
    pub(crate) fn new(ndk_event: ndk::event::MotionEvent) -> Self {
        Self {
            ndk_event,
            _lifetime: PhantomData,
        }
    }
    pub(crate) fn into_ndk_event(self) -> ndk::event::MotionEvent {
        self.ndk_event
    }

    /// Get the source of the event.
    ///
    #[inline]
    pub fn source(&self) -> Source {
        // XXX: we use `AInputEvent_getSource` directly (instead of calling
        // ndk_event.source()) since we have our own `Source` enum that we
        // share between backends, which may also capture unknown variants
        // added in new versions of Android.
        let source =
            unsafe { ndk_sys::AInputEvent_getSource(self.ndk_event.ptr().as_ptr()) as u32 };
        source.into()
    }

    /// Get the device id associated with the event.
    ///
    #[inline]
    pub fn device_id(&self) -> i32 {
        self.ndk_event.device_id()
    }

    /// Returns the motion action associated with the event.
    ///
    /// See [the MotionEvent docs](https://developer.android.com/reference/android/view/MotionEvent#getActionMasked())
    #[inline]
    pub fn action(&self) -> MotionAction {
        // XXX: we use `AMotionEvent_getAction` directly since we have our own
        // `MotionAction` enum that we share between backends, which may also
        // capture unknown variants added in new versions of Android.
        let action =
            unsafe { ndk_sys::AMotionEvent_getAction(self.ndk_event.ptr().as_ptr()) as u32 }
                & ndk_sys::AMOTION_EVENT_ACTION_MASK;
        action.into()
    }

    /// Returns which button has been modified during a press or release action.
    ///
    /// For actions other than [`MotionAction::ButtonPress`] and
    /// [`MotionAction::ButtonRelease`] the returned value is undefined.
    ///
    /// See [the MotionEvent docs](https://developer.android.com/reference/android/view/MotionEvent#getActionButton())
    #[inline]
    pub fn action_button(&self) -> Button {
        if let Some(get_action_button) = get_action_button_fn() {
            // SAFETY: the event pointer is a valid NDK motion event and the
            // optional function was resolved from libandroid at runtime.
            return (unsafe {
                get_action_button(self.ndk_event.ptr().as_ptr().cast::<libc::c_void>()) as u32
            })
            .into();
        }

        // Android < 13 has no NDK action-button getter. Reconstruct the
        // changed bit from consecutive mouse button states; ButtonPress and
        // ButtonRelease events carry the post-event pressed-button mask.
        let device_id = self.device_id();
        let current = self.button_state().0;
        let action = self.action();
        let previous = PREVIOUS_BUTTON_STATES
            .get_or_init(|| Mutex::new(HashMap::new()))
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .insert(device_id, current)
            .unwrap_or(0);
        let fallback_action = match action {
            MotionAction::ButtonPress => action_button_compat::ButtonAction::Press,
            MotionAction::ButtonRelease => action_button_compat::ButtonAction::Release,
            _ => action_button_compat::ButtonAction::Other,
        };
        let changed = action_button_compat::changed_button(previous, current, fallback_action);
        let fallback = match action {
            MotionAction::ButtonPress => current,
            MotionAction::ButtonRelease => previous,
            _ => 0,
        };
        action_button_compat::first_button(if changed == 0 { fallback } else { changed }).into()
    }

    /// Returns the pointer index of an `Up` or `Down` event.
    ///
    /// Pointer indices can change per motion event.  For an identifier that stays the same, see
    /// [`Pointer::pointer_id()`].
    ///
    /// This only has a meaning when the [action](Self::action) is one of [`Up`](MotionAction::Up),
    /// [`Down`](MotionAction::Down), [`PointerUp`](MotionAction::PointerUp),
    /// or [`PointerDown`](MotionAction::PointerDown).
    #[inline]
    pub fn pointer_index(&self) -> usize {
        self.ndk_event.pointer_index()
    }

    /*
    /// Returns the pointer id associated with the given pointer index.
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#amotionevent_getpointerid)
    // TODO: look at output with out-of-range pointer index
    // Probably -1 though
    pub fn pointer_id_for(&self, pointer_index: usize) -> i32 {
        unsafe { ndk_sys::AMotionEvent_getPointerId(self.ndk_event.ptr.as_ptr(), pointer_index) }
    }
    */

    /// Returns the number of pointers in this event
    ///
    /// See [the MotionEvent docs](https://developer.android.com/reference/android/view/MotionEvent#getPointerCount())
    #[inline]
    pub fn pointer_count(&self) -> usize {
        self.ndk_event.pointer_count()
    }

    /// An iterator over the pointers in this motion event
    #[inline]
    pub fn pointers(&self) -> PointersIter<'_> {
        PointersIter {
            inner: PointersIterImpl {
                ndk_pointers_iter: self.ndk_event.pointers(),
            },
        }
    }

    /// The pointer at a given pointer index. Panics if the pointer index is out of bounds.
    ///
    /// If you need to loop over all the pointers, prefer the [`pointers()`](Self::pointers) method.
    #[inline]
    pub fn pointer_at_index(&self, index: usize) -> Pointer<'_> {
        Pointer {
            inner: PointerImpl {
                ndk_pointer: self.ndk_event.pointer_at_index(index),
            },
        }
    }

    /*
    XXX: Not currently supported with GameActivity so we don't currently expose for NativeActivity
    either, for consistency.

    /// Returns the size of the history contained in this event.
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#amotionevent_gethistorysize)
    #[inline]
    pub fn history_size(&self) -> usize {
        self.ndk_event.history_size()
    }

    /// An iterator over the historical events contained in this event.
    #[inline]
    pub fn history(&self) -> HistoricalMotionEventsIter<'_> {
        self.ndk_event.history()
    }
    */

    /// Returns the state of any modifier keys that were pressed during the event.
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#amotionevent_getmetastate)
    #[inline]
    pub fn meta_state(&self) -> MetaState {
        self.ndk_event.meta_state().into()
    }

    /// Returns the button state during this event, as a bitfield.
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#amotionevent_getbuttonstate)
    #[inline]
    pub fn button_state(&self) -> ButtonState {
        self.ndk_event.button_state().into()
    }

    /// Returns the time of the start of this gesture, in the `java.lang.System.nanoTime()` time
    /// base
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#amotionevent_getdowntime)
    #[inline]
    pub fn down_time(&self) -> i64 {
        self.ndk_event.down_time()
    }

    /// Returns a bitfield indicating which edges were touched by this event.
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#amotionevent_getedgeflags)
    #[inline]
    pub fn edge_flags(&self) -> EdgeFlags {
        self.ndk_event.edge_flags().into()
    }

    /// Returns the time of this event, in the `java.lang.System.nanoTime()` time base
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#amotionevent_geteventtime)
    #[inline]
    pub fn event_time(&self) -> i64 {
        self.ndk_event.event_time()
    }

    /// The flags associated with a motion event.
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#amotionevent_getflags)
    #[inline]
    pub fn flags(&self) -> MotionEventFlags {
        self.ndk_event.flags().into()
    }

    /* Missing from GameActivity currently...
    /// Returns the offset in the x direction between the coordinates and the raw coordinates
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#amotionevent_getxoffset)
    #[inline]
    pub fn x_offset(&self) -> f32 {
        self.ndk_event.x_offset()
    }

    /// Returns the offset in the y direction between the coordinates and the raw coordinates
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#amotionevent_getyoffset)
    #[inline]
    pub fn y_offset(&self) -> f32 {
        self.ndk_event.y_offset()
    }
    */

    /// Returns the precision of the x value of the coordinates
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#amotionevent_getxprecision)
    #[inline]
    pub fn x_precision(&self) -> f32 {
        self.ndk_event.x_precision()
    }

    /// Returns the precision of the y value of the coordinates
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#amotionevent_getyprecision)
    #[inline]
    pub fn y_precision(&self) -> f32 {
        self.ndk_event.y_precision()
    }
}

/// A view into the data of a specific pointer in a motion event.
#[derive(Debug)]
pub(crate) struct PointerImpl<'a> {
    ndk_pointer: ndk::event::Pointer<'a>,
}

impl<'a> PointerImpl<'a> {
    #[inline]
    pub fn pointer_index(&self) -> usize {
        self.ndk_pointer.pointer_index()
    }

    #[inline]
    pub fn pointer_id(&self) -> i32 {
        self.ndk_pointer.pointer_id()
    }

    #[inline]
    pub fn axis_value(&self, axis: Axis) -> f32 {
        let value: u32 = axis.into();
        let value = value as i32;
        self.ndk_pointer.axis_value(value.into())
    }

    #[inline]
    pub fn raw_x(&self) -> f32 {
        self.ndk_pointer.raw_x()
    }

    #[inline]
    pub fn raw_y(&self) -> f32 {
        self.ndk_pointer.raw_y()
    }

    #[inline]
    pub fn tool_type(&self) -> ToolType {
        let value: i32 = self.ndk_pointer.tool_type().into();
        let value = value as u32;
        value.into()
    }
}

/// An iterator over the pointers in a [`MotionEvent`].
#[derive(Debug)]
pub(crate) struct PointersIterImpl<'a> {
    ndk_pointers_iter: ndk::event::PointersIter<'a>,
}

impl<'a> Iterator for PointersIterImpl<'a> {
    type Item = Pointer<'a>;
    fn next(&mut self) -> Option<Pointer<'a>> {
        self.ndk_pointers_iter.next().map(|ndk_pointer| Pointer {
            inner: PointerImpl { ndk_pointer },
        })
    }

    fn size_hint(&self) -> (usize, Option<usize>) {
        self.ndk_pointers_iter.size_hint()
    }
}

impl<'a> ExactSizeIterator for PointersIterImpl<'a> {
    fn len(&self) -> usize {
        self.ndk_pointers_iter.len()
    }
}

/// A key event
///
/// For general discussion of key events in Android, see [the relevant
/// javadoc](https://developer.android.com/reference/android/view/KeyEvent).
#[derive(Debug)]
#[repr(transparent)]
pub struct KeyEvent<'a> {
    ndk_event: ndk::event::KeyEvent,
    _lifetime: PhantomData<&'a ndk::event::KeyEvent>,
}
impl<'a> KeyEvent<'a> {
    pub(crate) fn new(ndk_event: ndk::event::KeyEvent) -> Self {
        Self {
            ndk_event,
            _lifetime: PhantomData,
        }
    }
    pub(crate) fn into_ndk_event(self) -> ndk::event::KeyEvent {
        self.ndk_event
    }

    /// Get the source of the event.
    ///
    #[inline]
    pub fn source(&self) -> Source {
        // XXX: we use `AInputEvent_getSource` directly (instead of calling
        // ndk_event.source()) since we have our own `Source` enum that we
        // share between backends, which may also capture unknown variants
        // added in new versions of Android.
        let source =
            unsafe { ndk_sys::AInputEvent_getSource(self.ndk_event.ptr().as_ptr()) as u32 };
        source.into()
    }

    /// Get the device id associated with the event.
    ///
    #[inline]
    pub fn device_id(&self) -> i32 {
        self.ndk_event.device_id()
    }

    /// Returns the key action associated with the event.
    ///
    /// See [the KeyEvent docs](https://developer.android.com/reference/android/view/KeyEvent#getAction())
    #[inline]
    pub fn action(&self) -> KeyAction {
        // XXX: we use `AInputEvent_getAction` directly since we have our own
        // `KeyAction` enum that we share between backends, which may also
        // capture unknown variants added in new versions of Android.
        let action = unsafe { ndk_sys::AKeyEvent_getAction(self.ndk_event.ptr().as_ptr()) as u32 };
        action.into()
    }

    /// Returns the last time the key was pressed.  This is on the scale of
    /// `java.lang.System.nanoTime()`, which has nanosecond precision, but no defined start time.
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#akeyevent_getdowntime)
    #[inline]
    pub fn down_time(&self) -> i64 {
        self.ndk_event.down_time()
    }

    /// Returns the time this event occured.  This is on the scale of
    /// `java.lang.System.nanoTime()`, which has nanosecond precision, but no defined start time.
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#akeyevent_geteventtime)
    #[inline]
    pub fn event_time(&self) -> i64 {
        self.ndk_event.event_time()
    }

    /// Returns the keycode associated with this key event
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#akeyevent_getkeycode)
    #[inline]
    pub fn key_code(&self) -> Keycode {
        // XXX: we use `AInputEvent_getKeyCode` directly since we have our own
        // `Keycode` enum that we share between backends, which may also
        // capture unknown variants added in new versions of Android.
        let keycode =
            unsafe { ndk_sys::AKeyEvent_getKeyCode(self.ndk_event.ptr().as_ptr()) as u32 };
        keycode.into()
    }

    /// Returns the number of repeats of a key.
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#akeyevent_getrepeatcount)
    #[inline]
    pub fn repeat_count(&self) -> i32 {
        self.ndk_event.repeat_count()
    }

    /// Returns the hardware keycode of a key.  This varies from device to device.
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#akeyevent_getscancode)
    #[inline]
    pub fn scan_code(&self) -> i32 {
        self.ndk_event.scan_code()
    }

    /// Returns the state of the modifiers during this key event, represented by a bitmask.
    ///
    /// See [the NDK
    /// docs](https://developer.android.com/ndk/reference/group/input#akeyevent_getmetastate)
    #[inline]
    pub fn meta_state(&self) -> MetaState {
        self.ndk_event.meta_state().into()
    }
}

// We use our own wrapper type for input events to have better consistency
// with GameActivity and ensure the enum can be extended without needing a
// semver bump
/// Enum of possible input events
#[derive(Debug)]
#[non_exhaustive]
pub enum InputEvent<'a> {
    MotionEvent(self::MotionEvent<'a>),
    KeyEvent(self::KeyEvent<'a>),
    TextEvent(crate::input::TextInputState),
}
