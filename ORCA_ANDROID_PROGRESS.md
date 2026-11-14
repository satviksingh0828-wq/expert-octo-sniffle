# ORCA source completion report (before GitHub APK build)

**Prepared source:** Standalone ORCA launcher, Calico overlay and animations, native llama.cpp JNI inference, offline chat, model import / Wi-Fi model download, opt-in Accessibility text reading, optional user-approved MediaProjection screen capture with bundled OCR and image labeling, privacy controls and redaction, and best-effort pet boot startup.

**Tested here:** XML manifests parse; C++ JNI builds for ARM64 with Termux Clang and statically linked llama.cpp (single native shared library; no versioned ggml shared library dependencies); diff format checks. Kotlin/Android Gradle build, native model inference, Android runtime permissions, image capture, UI and animation reactions have NOT been tested, because local Android SDK/Gradle compilation is not functioning in Termux.

**Important limitation:** Qwen3 4B GGUF is text-only; screen image understanding uses local OCR and image labels, not full multimodal Qwen vision. Models are downloaded/imported after installation; they do not ship inside APK. Android Accessibility can expose sensitive private content and redaction is best-effort only.

**Next:** Push a clean ORCA-only repository, run `.github/workflows/orca-apk.yml`, resolve any CI failures, install the APK and test on-device. Consult `ORCA_README.md` for build and privacy details. Original upstream AGPL-3.0 license must remain intact.
