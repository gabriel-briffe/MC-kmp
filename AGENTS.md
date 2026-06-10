# AGENTS.md

## Project

Mountain Circles (`MC-kmp`) is a Kotlin Multiplatform **Android/iOS** gliding map app. This repo currently contains only the `composeApp` module. **Android-only builds on Linux are supported**; iOS targets are configured but not required for Android APK output.

## Build the Android debug APK

Prerequisites: **JDK 21**, **Android SDK** with API 35 + build-tools, `ANDROID_HOME` set, and `local.properties` pointing at the SDK:

```properties
sdk.dir=/path/to/Android/Sdk
```

From the repo root:

```bash
./gradlew :composeApp:assembleDebug
```

Output APK:

```text
composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Install on a device or local emulator:

```bash
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

The app needs **internet** for the base OpenStreetMap tiles and the default **Mapterhorn** hillshade layer (`https://tiles.mapterhorn.com`). Location permission is optional but enables the GPS puck.

## Download the APK from your phone (GitHub Actions)

A workflow builds the debug APK on GitHub’s servers so you do not need a local build machine.

1. Open the repo on GitHub (mobile app or browser).
2. Go to **Actions** → **Build Android APK**.
3. Tap **Run workflow** (choose branch `main`) and start the run.
4. When it finishes (green check), open the run → scroll to **Artifacts**.
5. Download **mountain-circles-debug-apk** (`mountain-circles-debug.apk`).
6. Install on your phone (tap the file, or use `adb install -r mountain-circles-debug.apk`).

Pushes to `main` also trigger a build automatically; use the latest successful run’s artifact.

Artifacts are kept for **90 days**.

## Lint / tests

There are **no Kotlin test sources** in this repo. Lint can be run with `./gradlew :composeApp:lintDebug` but is not required for producing the debug APK.

## Cursor Cloud specific instructions

- **Android-only scope:** Use `./gradlew :composeApp:assembleDebug`. Do not start iOS/Xcode/CocoaPods tooling on Linux.
- **First-time VM setup:** Install Android command-line tools, accept licenses, and install `platform-tools`, `platforms;android-35`, and `build-tools;35.0.0`. Write `local.properties` with `sdk.dir=...`.
- **Cloud VM emulator:** The Android emulator requires KVM (`/dev/kvm`). Cursor Cloud VMs typically **cannot** run the emulator; build the APK here and install it on a physical device or a local emulator instead.
- **Gradle scaffolding:** Root `settings.gradle.kts`, `gradle/libs.versions.toml`, and the Gradle wrapper were added so the imported `composeApp` module can build standalone. MapLibre Compose **0.12.1** matches this codebase’s APIs.
- **Dependencies:** Gradle resolves Maven dependencies during `assembleDebug`; no separate install step beyond the Android SDK.
