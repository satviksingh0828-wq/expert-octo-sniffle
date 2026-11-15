# ORCA — Offline Android Automatic AI Pet

ORCA is a standalone Android pet companion built from [Bynlk/clawd-on-mobile](https://github.com/Bynlk/clawd-on-mobile). It features an always-on-top Calico character and a built-in offline screen-description controller. The app reads permitted text descriptions through Android Accessibility, classifies the current activity locally, and selects from the packaged Calico animations. It does not need Termux, an AI API key, a downloaded model, a desktop app, a WebSocket server, or visual screen capture. It is designed for ARM64 Android 8+ (API 26+) and has target SDK 35.

## Status

The source includes a compact Start/Stop launcher with an in-app test log. The pet runs automatically after Start; Accessibility permission is still required in Android Settings. APK build and installed-device behavior must be validated through GitHub Actions and an ARM64 Android device.

## Features

- Standalone ORCA launcher with only automatic Start/Stop controls and an in-app test log.
- Calico overlay foreground service; the built-in controller can select every packaged Calico animation. Pet overlay can make a best-effort restart after reboot if enabled.
- A built-in local screen-description classifier selects all packaged Calico animation families without network access or external model files.
- Text screen awareness uses an Android Accessibility service, enabled by Start and confirmed in Android Settings. Password nodes and a best-effort list of sensitive apps are skipped; common codes/emails are redacted. This is not a guarantee that all private data is excluded.

## Build with GitHub Actions

This repository needs the `android` folder and `.github/workflows/orca-apk.yml`.

1. Push the ORCA Android source to an empty GitHub repository with `main` as its default branch. **Do not upload any GGUF model**.
2. In GitHub **Actions**, choose **ORCA Standalone APK** > **Run workflow**, or wait for the push-triggered build.
3. The workflow installs Java 17 and Android SDK 35, then runs `./gradlew assembleDebug`.
4. If successful, download the `ORCA-debug-APK` artifact, extract the `.apk`, and install it on an ARM64 device. It is a debug-signed build; don't publish it as a release.
5. Launch ORCA and press **Start**. Grant the Android **Display over other apps** permission and enable ORCA's Accessibility service when prompted.
6. Switch between apps and watch the in-app logs while ORCA automatically selects Calico animations from the screen description.
7. Press **Stop** to stop the pet and screen-description processing.
8. Validate memory use, battery behavior, UI, and automatic animation changes on an actual device before distributing.

## Privacy and Android limitations

Screen text may include private chats and one-time codes. ORCA's best-effort exclusions may miss sensitive content; only use Start if you understand the access granted. Android can hide protected content and restrict background execution. Screen descriptions are not written to the in-app logs; only package names, decisions, and service events are logged. Pet reboot restart is best-effort only.

## License and credits

This project is a modified derivative of **Bynlk/clawd-on-mobile**, licensed under **GNU AGPL-3.0**. Keep the upstream attribution, notices and `LICENSE` in the new repo and comply with AGPL obligations when distributing the app.

## Developer map

- Main launcher: `android/app/src/main/java/com/clawd/mobile/orca/OrcaHomeActivity.kt`
- Built-in offline controller: `.../orca/OrcaActivityEngine.kt`
- In-app test logs: `.../orca/OrcaLogStore.kt`
- Accessibility: `.../orca/OrcaScreenService.kt`
- Floating pet: `.../overlay/FloatingPetService.kt`
- GitHub workflow: `.github/workflows/orca-apk.yml`
