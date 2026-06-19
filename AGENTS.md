# AGENTS.md

## Project Orientation

RFAnalyzer is an Android SDR spectrum analyzer. The app combines a Kotlin/Java
Android application, Jetpack Compose UI, JNI-backed SDR/native libraries, DSP
code, and an MkDocs user manual that is bundled into the app.

Before changing behavior, read the nearby source and prefer the existing module
boundaries. This project has hardware-facing code, so a change that compiles can
still be incomplete until the affected SDR path has been manually checked.

## Source Map

- `app/` is the Android app. Main Kotlin/Java code lives under
  `app/src/main/java/com/mantz_it/rfanalyzer/`.
- `app/src/foss/` and `app/src/play/` contain distribution-specific billing
  implementations. Prefer the FOSS flavor for local work unless Play Billing is
  the feature under test.
- `nativedsp/` contains JNI/native DSP support used by the app.
- `libhackrf/`, `libairspy/`, `libairspyhf/`, and `libhydrasdr/` wrap SDR
  hardware libraries. Keep device-specific changes inside the relevant module.
- `libusb/` provides the shared native USB layer used by SDR modules.
- `docs/` is the MkDocs manual source. `resources/` contains design, store, and
  source graphics. `tools/` contains helper scripts.
- `app/schemas/` contains Room schema snapshots. Update them intentionally when
  changing database schema versions.

## Build And Test Commands

Run Gradle from the repository root with the checked-in wrapper:

```sh
./gradlew :app:assembleFossDebug
```

Use the Play flavor only when working on Play Billing or Play-specific behavior:

```sh
./gradlew :app:assemblePlayDebug
```

For focused checks, prefer the narrowest Gradle task that covers the change,
such as:

```sh
./gradlew :app:testFossDebugUnitTest
./gradlew :app:connectedFossDebugAndroidTest
```

Native modules are built through Gradle/CMake. If touching C, CMake, JNI, USB,
or SDR driver code, run the relevant app build and, when possible, verify the
affected source on Android hardware with the actual SDR device attached.

## Generated Docs And Assets

The app `preBuild` task depends on MkDocs: it builds the manual from `docs/` and
copies it into `app/src/main/assets/docs`. Treat `build_site/` and
`app/src/main/assets/docs/` as generated outputs, not hand-edited source. Edit
the Markdown in `docs/` and let the build regenerate bundled manual assets.

If MkDocs is unavailable locally, note that limitation clearly instead of
pretending the bundled manual was verified.

## Native And Hardware Boundaries

Native library modules already carry CMake settings for Android 16KB page-size
compatibility. Preserve those flags unless the change is specifically about NDK
or packaging behavior.

Avoid broad native refactors across multiple SDR modules unless the change is a
shared USB/DSP concern. Device wrappers should continue to isolate HackRF,
Airspy, Airspy HF+, and HydraSDR behavior.

For USB/source changes, record what was actually verified: emulator-only,
physical Android device, attached SDR model, file replay, or compile-only.

## Documentation And Product Behavior

Update `Readme.md`, `docs/`, or `changelog.txt` when a user-visible feature,
setup requirement, hardware support detail, or troubleshooting behavior changes.
Keep README material public-facing; keep this file focused on agent handoff and
repo operation.

Respect the licensing and branding notes in `Readme.md` and `COPYING`. The code
is GPL-covered, while the app name, logo, icon, promotional images, screenshots,
and other branding elements are not broadly reusable in modified distributions.

## Agent Safety Rules

- Do not revert unrelated user changes or generated work you did not create.
- Keep edits scoped to the module that owns the behavior.
- Prefer existing Kotlin, Compose, Room, Hilt, Gradle, and CMake patterns.
- Do not commit secrets, local SDK paths, signing material, or hardware logs
  containing private device/user data.
- Call out verification gaps plainly, especially missing Java/Android SDK,
  missing MkDocs, unavailable Android devices, or unavailable SDR hardware.
