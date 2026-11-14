# ORCA — Offline Android AI Pet

ORCA is a standalone Android pet companion built from [Bynlk/clawd-on-mobile](https://github.com/Bynlk/clawd-on-mobile). It features an always-on-top Calico character, local Qwen3 4B text inference using llama.cpp, opt-in accessibility screen-text events, optional screen capture with **on-device** ML Kit OCR/image labeling, a local chat UI, and animations that respond to activity. The app does not need Termux, an AI API key, a desktop app, or a WebSocket server. It is designed for ARM64 Android 8+ (API 26+) and has target SDK 35.

## Status

**Source implementation is prepared. It has NOT yet been compiled into an Android APK or tested on the actual installed app.** The llama.cpp JNI source **did compile successfully** with Termux CMake/Clang for ARM64. That is a native-source test, not an Android Gradle/NDK APK build or inference validation.

## Features

- Standalone ORCA launcher with model import/download, pet controls, chat, privacy switches, and screen-capture controls.
- Calico overlay foreground service; 12 permitted automatic animations plus the project's existing animation assets. Pet overlay can make a best-effort restart after reboot if enabled.
- A single `liborca_llama.so` built with statically linked llama.cpp/ggml, running Qwen3 GGUF model on-device (CPU; one inference at a time). Native inference has not yet been tested with the model.
- Install model from the Android system file picker, or explicitly download Qwen's official `Qwen3-4B-Q4_K_M.gguf` (~2.5 GB) via Wi-Fi using Android Download Manager. Model stays in app-controlled storage and is not checked into Git.
- Text screen awareness uses an opt-in Android Accessibility service, which must be enabled **both in ORCA and Android Settings**. Password nodes and a best-effort list of sensitive apps are skipped; common codes/emails are redacted. This is not a guarantee that all private data is excluded.
- Optional `MediaProjection` screen capture requires a new system permission prompt **for each session**. A foreground notification includes **Stop**; screenshots are processed in RAM using bundled ML Kit text recognition and image labeling. Raw pixels are not saved or uploaded.
- The Qwen3 4B text-only GGUF model does **not** understand images natively: it receives OCR results and image-label descriptions. Full multimodal image reasoning would require a separate vision model and integration.

## Build with GitHub Actions

This repository needs the `android` folder, `.gitmodules` for its pinned llama.cpp submodule, and `.github/workflows/orca-apk.yml`.

1. Push the ORCA Android source to an empty GitHub repository with `main` as its default branch. **Do not upload any GGUF model**.
2. In GitHub **Actions**, choose **ORCA Standalone APK** > **Run workflow**, or wait for the push-triggered build.
3. The workflow checks out the submodule, installs Java 17, Android SDK 35, NDK 27.2, CMake 3.22.1, and runs `./gradlew assembleDebug`.
4. If successful, download the `ORCA-debug-APK` artifact, extract the `.apk`, and install it on an ARM64 device. It is a debug-signed build; don't publish it as a release.
5. Launch ORCA, grant the Android **Display over other apps** permission, start the Calico pet, and test a happy animation.
6. Import a Qwen3 4B GGUF from local storage or choose the **Download Qwen 4B** button with Wi-Fi and sufficient storage. Use **Talk to ORCA** to test local model replies.
7. If desired, turn on screen text monitoring and manually enable ORCA's Accessibility service in Android settings. Optional screen capture is started separately and requires a fresh Android permission prompt each time.
8. Validate memory use, battery behavior, UI, screen capture, and local inference on an actual device before distributing.

## Privacy and Android limitations

Screen text may include private chats and one-time codes. ORCA's best-effort exclusions may miss sensitive content; only enable screen monitoring if you understand the access granted. Android can hide protected content, restrict background execution, and revoke screen-capture sessions. Screen-capture sessions intentionally do **not** resume automatically on reboot. Pet reboot restart is best-effort only. Screenshot data is not logged or uploaded by ORCA; model downloads do connect to Hugging Face over HTTPS.

## License and credits

This project is a modified derivative of **Bynlk/clawd-on-mobile**, licensed under **GNU AGPL-3.0**. Keep the upstream attribution, notices and `LICENSE` in the new repo and comply with AGPL obligations when distributing the app. `llama.cpp` / `ggml` are included as a pinned Git submodule and have their own licenses. Qwen3 model weights are obtained separately, not redistributed in the APK.

## Developer map

- Main launcher: `android/app/src/main/java/com/clawd/mobile/orca/OrcaHomeActivity.kt`
- Local model manager: `.../orca/OrcaModel.kt`
- JNI native engine: `android/app/src/main/cpp/orca_jni.cpp`
- Accessibility: `.../orca/OrcaScreenService.kt`
- MediaProjection: `.../orca/OrcaScreenCaptureService.kt`
- On-device OCR / labeling: `.../orca/OrcaVisualAnalyzer.kt`
- Floating pet: `.../overlay/FloatingPetService.kt`
- GitHub workflow: `.github/workflows/orca-apk.yml`
