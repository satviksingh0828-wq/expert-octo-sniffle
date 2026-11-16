# Calico 0.18 — On-device Android cat AI

Calico uses the cream/ginger/cocoa cat design and existing packaged Calico animations.

## Chat and model choices

The app opens with **Built-in Calico pet brain** selected. This is the existing lightweight
34-signal neural activity classifier plus deterministic basic chat. It is ready without
downloading anything or using an account. The built-in model is **not a generative LLM**:
it can explain its capabilities, respond to basic greetings and react to activity
topics, but cannot reliably answer arbitrary questions.

Open **Models** in Calico's chat header to optionally download **Qwen3-0.6B** (about
614 MB) from Hugging Face. The download runs only after the user taps the button,
is saved in Calico's private app storage, is checked with SHA-256, and is not
sent to any AI service for inference. The app uses Google LiteRT-LM on-device
inference on the CPU and does not need Termux, Ollama, an external server,
Google account permissions, or any API key. Allow roughly 1 GB of free storage
for downloading and installation. After installation, you can switch between
**Built-in pet brain** and **Qwen3-0.6B** in the Models dialog.

The same model selection applies to both **chat and pet**. When Qwen is selected
and available, it can refine the pet's animation from coarse, non-private
activity categories every 45 seconds at most. The existing neural pet brain
continues to choose immediate reactions and is always the fallback. Qwen
suggestions are constrained to packaged Calico animations. Screen text and
browser titles are **never sent** to the model's pet animation prompt.

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

Qwen needs an explicit one-time download and sufficient device RAM/storage.
LiteRT-LM model initialization may take several seconds. Small language models
can still make errors. The app's conversational inference is local; a fresh
model download requires internet access. No Gemini cloud API or secret keys.

System-level Circle to Search, image-based screen analysis, and full Gmail,
Drive, Tasks and Google-account authorization are **not implemented**.
The current model and screen reactions should be tested on a real Android
device before relying on them.

UI layout was adapted to native Jetpack Compose from
[EvanBacon/chat-template](https://github.com/EvanBacon/chat-template).
