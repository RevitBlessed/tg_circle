# Building the submission

## Full source build

Use the normal upstream build, with no private local paths or prebuilt APK required:

- JDK 17 (upstream Dockerfile); the supplied APK was compiled with Android Studio's JDK 21.
- Gradle 8.13 via committed wrapper; Android Gradle Plugin 8.13.2.
- SDK platform 36, Build Tools 36.0.0, NDK 27.2.12479018, CMake 3.22.1.
- Recursive submodules, accepted SDK licenses, and free disk space for native builds/dependencies.

```sh
git clone --branch contest/round-video-upgrades --recursive --shallow-submodules https://github.com/RevitBlessed/Telegram.git
cd Telegram
git submodule update --init --recursive --depth=1
```

Configure the SDK in Android Studio or local `local.properties` / `ANDROID_HOME`. Follow the upstream [application configuration instructions](../../README.md#creating-your-telegram-application) for your own API credentials, package identity, Firebase and signing. Upstream example credentials/configuration are unchanged and are not personal distribution credentials.

Linux/macOS:

```sh
./gradlew :TMessagesProj_App:assembleAfatDebug --no-daemon --no-parallel --max-workers=2
```

Windows PowerShell:

```powershell
.\gradlew.bat :TMessagesProj_App:assembleAfatDebug --no-daemon --no-parallel --max-workers=2
```

Output: `TMessagesProj_App/build/outputs/apk/afat/debug/app.apk`.

Inherited Gradle settings allow an 8 GB heap. On low-memory machines, close emulators and select an appropriate local heap override. Do not commit machine paths or credentials. A clean native build is much heavier than an incremental Java build.

## Supplied APK provenance

[Release asset](https://github.com/RevitBlessed/Telegram/releases/tag/round-video-v1.0): `Telegram-Round-Video-Contest.apk`.

SHA-256:

```text
ac4da2890c8c2ae55eedd10de25a8b91df6ea886cdfc3af7cb73c21466dfd430
```

This is the exact APK accepted after the pause/resume fix, not a replacement rebuild: `afatDebug`, package `org.telegram.messenger.beta`, with ARM and x86 libraries. Updating an installation requires matching signing certificates; do not uninstall an existing app without preserving needed data.

The local APK build reused unchanged native libraries via the untracked `dev-tools/emulator-native.gradle` init script; Java/resources were compiled from the final sources. That shortcut is **not** the clean source-build recipe above. Byte-for-byte reproducibility from a clean native rebuild is not claimed. Native source and submodule revisions are unchanged. See [validation scope](VALIDATION.md).

## Countdown regression test (JDK only)

From the repository root:

```sh
javac -d build/countdown-test TMessagesProj/src/main/java/org/telegram/ui/Components/RoundVideoRecordingCountdown.java Tools/countdown/RoundVideoRecordingCountdownTest.java
java -cp build/countdown-test org.telegram.ui.Components.RoundVideoRecordingCountdownTest
```

Both commands must exit successfully; explicit assertions do not require `-ea`. The GitHub workflow runs this test, not a full Android build/device test.
