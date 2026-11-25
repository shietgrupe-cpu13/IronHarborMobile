# Android release compliance procedure

This is a release checklist, not a declaration of legal compliance. The governing terms remain [GPLv3](../LICENSE-GPL), [upstream additional terms](../LEGAL.md) and each applicable third-party license.

Before distributing any APK (including beta or direct-message builds):

- Identify the clean source commit used for the build; record build commands, toolchain, features, dependency lockfile, local patches, required scripts and configuration. Preserve complete corresponding source as defined in GPLv3 section 1.
- Provide recipients equivalent access to corresponding source at no further charge when using the network distribution method in section 6(d). Include a direct immutable source link alongside each APK, including mirrors and private distribution. If a source archive omits submodules or other necessary inputs, supplement it. Do not move historical release tags to conceal mismatches.
- Mark modified versions and relevant modification dates, retain copyright and applicable license notices, and include the GPL text, upstream additional terms and third-party notices with the distribution. Preserve required interactive legal notices where applicable.
- Build an Android-target dependency inventory using the actual locked dependency graph and shipping features; inspect applicable license texts and required notices for Rust, native libraries, Android components, fonts, icons and vendored code. Do not assume a lockfile alone satisfies notice obligations.
- Inspect the final APK ZIP inventory, including assets, resources, native libraries and embedded assets. Verify provenance and permission for each non-original asset. Do not include retail EA game files or unlicensed third-party assets. Check any separately downloaded Ares archive against its own terms.
- Verify visible branding in launcher, game menus, splash screens, app listing, social posts and repository documentation. Identify upstream only as the source or for required credit; never imply official endorsement.
- Respect upstream's separate server access rules and noncommercial project request; commercial restrictions on separately licensed artwork remain applicable.
- Verify users can build and install their own modified version; never publish private signing secrets. Assess GPLv3 section 6 installation-information obligations if distributing in or with a User Product.
- Record the APK SHA-256, source commit, source archive URL, dependency-notice inventory, asset review and reviewer sign-off. Resolve any blockers before distribution.

## Historical releases

See [release source index](ANDROID_RELEASE_SOURCES.md). The tag-to-APK correspondence, full notices and binary contents must be verified against the original build records; this document does not retroactively certify them.

## Known notices

The repository retains upstream GPLv3 and Ares notices; Phobos attribution is in the upstream legal notice and documentation. Android development additionally contains Material Design icons and modified vendored winit/android-activity code; consult their original license files. This list is not a complete inventory of linked dependencies.
