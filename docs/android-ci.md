# Android ARM64 GitHub Actions builds

`.github/workflows/android-arm64.yml` builds Iron Harbor Mobile on the standard,
free `ubuntu-latest` GitHub-hosted runner. It uses the repository's existing
`android/Cargo.toml` cdylib and Gradle app; the workflow does not create a second
Android project or fetch game files.

## Build paths

- Pushes to `android-development` that touch Rust, Cargo, Android, build, or
  workflow files run an optimized Rust `fast` ARM64 build, Android JVM unit tests,
  APK packaging, and metadata/signature/alignment checks.
- Pull requests targeting `android-development` run the same debug verification.
- The `workflow_dispatch` form offers `debug` and signed `release` builds. The
  manual `version_name` is optional. A manual release build is an artifact only.
- Tags named `ironharbor-vX.Y.Z` build the Rust `shipping` profile, package a
  signed release APK, verify that its version code advances, and publish a stable
  GitHub Release.
- A daily schedule builds the `android-development` branch and creates an
  immutable, debug-signed nightly prerelease. Each nightly tag includes the
  workflow run number; existing release tags are not moved or rewritten.

GitHub only enables `workflow_dispatch` and scheduled runs after the workflow is
present on the repository's default branch. Branch push builds work as soon as
this file is pushed to `android-development`. Promote the workflow to the default
branch through a reviewed change when manual and nightly triggers should be live.

## Caches and build tools

- `Swatinem/rust-cache` keys Cargo state with the Rust toolchain, manifests/lockfile,
  Android ARM64 target, and distinct `fast`/`shipping` plus `phobos` build keys.
  The optimized `fast` profile retains Cargo incremental artifacts. Shipping uses
  a separate cache key and keeps the repository's existing shipping profile.
- `sccache` uses the GitHub Actions cache backend for Rust compiler invocations;
  build logs include `sccache --show-stats` so hit rates can be measured from real
  workflow runs. Rust's incremental profile may bypass sccache for those compiler
  invocations; the persistent fast-profile target cache is the primary warm-debug
  mechanism. Shipping is non-incremental and can benefit from sccache.
- The Gradle setup action uses the basic GitHub Actions cache provider for Gradle
  distributions, dependencies, and build cache. Configuration cache stays off
  until this Android build proves compatible.
- `ccache` is not installed: the Android Gradle module only packages the Rust
  `cdylib` and compiles Kotlin/Java. The Android Rust dependency graph has no C/C++
  compilation crate, CMake project, or `ndk-build` task, so a ccache wrapper would
  add setup overhead without caching a current build stage. Revisit if that graph
  changes.
- No paid runner, paid cache backend, private game-file repository, or signing
  credential is used by debug/PR builds.

## Stable signing secrets

Stable releases require the existing Iron Harbor release keystore. Configure these
repository Actions secrets without committing the keystore or credentials:

- `ANDROID_RELEASE_KEYSTORE_BASE64`: base64-encoded existing `.jks` file.
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`

The workflow writes the keystore only to the hosted runner's temporary directory,
sets restrictive permissions, and never uploads it. If any secret is absent, the
release build fails with a specific message before compilation. The current
repository has no Actions signing secrets configured, so stable-release signing
must be set up before using `ironharbor-v*` tags. Debug builds use the Android
Gradle debug signing config and package as `org.ironharbor.mobile.debug`.

## What CI verifies

Each successful artifact contains the ARM64 APK, SHA-256, Cargo/Rust/Android and
Gradle logs, the Gradle profile report, changelog, and build manifest. The workflow
runs JVM tests and verifies the package ID/version, APK signature, `arm64-v8a`
Rust library, 16 KB ELF `PT_LOAD` alignment, 16 KB APK ZIP alignment, and absence
of game archive/art assets such as MIX/SHP/VXL/HVA files. A successful hosted build
proves compilation and packaging only; gameplay, Vulkan, storage access and Android
device behavior still need physical-device testing.

## Local fallback

Local builds remain available for an explicitly requested fallback. On Windows, use
the E-drive toolchain and paths in `docs/android.md`; set `CARGO_HOME`, `RUSTUP_HOME`,
`GRADLE_USER_HOME`, `TMP`, `TEMP`, `ANDROID_HOME` and Java temp settings to E: before
invoking Cargo or Gradle. Do not run a heavy local Rust/Gradle Android build without
explicit user authorization; use the GitHub workflow by default.