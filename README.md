# MC-kmp

Mountain Circles — Kotlin Multiplatform gliding map app (Android / iOS).

## Get the Android debug APK (phone-friendly)

No local build required:

1. **Actions** → **Build Android APK** → **Run workflow** (branch `main`)
2. Open the completed run → **Artifacts** → download `mountain-circles-debug.apk`

Pushes to `main` also produce a fresh APK artifact.

## Build locally

Requires JDK 21 and the Android SDK. See [AGENTS.md](AGENTS.md).

```bash
./gradlew :composeApp:assembleDebug
```

Output: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`
