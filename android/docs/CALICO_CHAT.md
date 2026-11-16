# Calico 0.18 — On-device Android cat AI

Calico uses the cream/ginger/cocoa cat design and existing packaged Calico animations.

## Chat and the live model library (Calico 0.19)

**Default model:** Built-in Calico pet brain. It is a compact activity classifier
and basic deterministic chat, not an unrestricted conversational LLM.

Open **Models** to access the new searchable live **Hugging Face LiteRT-LM**
model library. The catalog refreshes when the screen opens and whenever you
submit a search. You can inspect a repository for downloadable model variants,
see size and hardware requirements, and download compatible public
`.litertlm` weights without a key. The list is no longer fixed to only Qwen.

**Download workflow:**

1. Open Models → search for Qwen, Gemma, Llama, Phi, etc.
2. Select a public LiteRT model repository, then inspect its individual files.
3. Choose a model with a published SHA-256 LFS digest and a plausible size.
4. Press Download & Verify. Downloads go to **private app storage**, can be
   paused and resumed, and are checked against upstream SHA-256 before activation.
5. Choose Use for the installed model. That same model powers both **chat** and
   periodically refines **pet animations** through a safe animation allowlist.
6. Switch back to Built-in pet brain at any time or remove non-active models.

Search and model downloads require an internet connection, but once installed,
AI inference is local in the Android app. No Termux server, Ollama gateway,
cloud AI, account sign-in, or API key is necessary. A large model may not run
reliably on every device; Calico's runtime currently initializes LiteRT-LM
on **CPU**, so specialized NPU builds are marked and cannot be downloaded from
the general model picker. Some public repositories may not publish SHA-256
file digests; such files are shown but cannot be installed. Gated/private
repositories are excluded.

**GGUF/Ollama models are not supported by this LiteRT-LM runtime.** They require
an additional embedded llama.cpp-compatible backend, which is not included in
this release. A model's presence in search does not guarantee runtime
compatibility or sufficient device memory.

Calico 0.18's already-verified Qwen3-0.6B download remains discoverable on an
upgrade; existing installed bytes are not removed. The previous model choice
can be migrated to the new model ID.

## App layout and controls

- The animated Calico avatar appears in a dedicated bottom-right area inside
  the chat screen. The floating overlay is temporarily hidden there, to avoid
  showing two cats at once.
- Outside Calico chat, the overlay first appears at the bottom-right corner.
  Dragging changes its saved position.
- Tapping the floating cat opens Calico chat.
- On each launch or return from settings, missing overlay or Accessibility
  permissions are checked again and the setup screen is shown.
- Overlay is needed for the floating cat and Accessibility is needed for
  browser-tab reactions. Notification access remains optional.
- The shortcuts for Calendar, Android search, Maps, email and files use
  Android intents. They do not grant access to the user's Gmail, Drive or
  Calendar data behind the scenes.

## Browser reactions

With Android Accessibility permission granted, Calico reads limited accessible
browser window titles and visible UI hints **locally**. It classifies coding,
studying, reading, media, shopping, maps, search, planning and errors into
suitable animation choices instead of assuming every Chrome tab is identical.
Titles and page text are not persisted or uploaded. Sensitive and incognito
windows are excluded by best-effort checks. Browser implementation and
accessibility tree details vary by device.

## Screen-off sleep

When the screen turns off, Calico enters the sleeping state, sets a 30-second
timer and hides the overlay at timeout. On screen wake it reappears and plays
the waking animation before returning to the active state. Android cannot
show an overlay on a physically powered-off display; a 30-second visible
lock-screen animation is not guaranteed, nor can Calico power on the display.

## Limits

Language models need sufficient RAM, storage, and compatible LiteRT-LM
operators. Not every model will load successfully on every device.
Model initialization may take several seconds, and small models can still
make factual errors. Catalog discovery and model downloads require internet;
inference remains local after installation. No Gemini cloud API or keys.

System-level Circle to Search, image-based screen analysis, and full Gmail,
Drive, Tasks and Google-account authorization are **not implemented**.
The current model and screen reactions should be tested on a real Android
device before relying on them.

UI layout was adapted to native Jetpack Compose from
[EvanBacon/chat-template](https://github.com/EvanBacon/chat-template).
