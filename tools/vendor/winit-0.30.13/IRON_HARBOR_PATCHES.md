# Iron Harbor Mobile winit patches

This directory is winit `0.30.13`, vendored from the Cargo registry so the Android NativeActivity backend can be patched without changing unrelated upstream source. The original crate's `LICENSE` and `LICENSE-MIT` files are retained; upstream project: https://github.com/rust-windowing/winit.

Local patch: `src/platform_impl/android/mod.rs` routes Android `Source::Mouse` motion events to winit cursor, mouse-button, and wheel events instead of treating mouse `Down/Move` as touchscreen contacts. Android virtual keyboard device ID `-1` is represented as winit's dummy device so the launcher HUD's dispatched keys remain distinct from physical keyboard events. All other non-mouse motion continues through the existing touch path.

Keep this patch narrow when upgrading winit and compare it with the then-current upstream Android backend. Do not remove upstream license/attribution files.
