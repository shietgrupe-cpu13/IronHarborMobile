# Iron Harbor Mobile v0.3.6 — B44

Unofficial Android ARM64 fork of VERA20K. Yuri's Revenge game files are not included.

## What's new?

- Fixed a game Activity startup crash on Android 16 reported after B43. The NativeActivity applies immersive mode before `super.onCreate()`, when the window decor may not yet exist. Immersive mode now materializes the decor before requesting its insets controller and uses the existing fullscreen flags if the controller is not attached.

## Not fixed

- B44 still needs on-device validation through game launch, Vulkan initialization, and gameplay. A successful Activity start alone does not establish engine stability.
- The previous destroyed-mutex shutdown crash and broad Mental Omega compatibility remain unresolved.

## What's verified?

- Build and device verification results will be recorded after B44 validation.
