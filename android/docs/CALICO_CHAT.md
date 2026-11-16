# Calico — Offline Android Cat Assistant

Calico is the cat-branded launcher and assistant for the original Android virtual pet.
Package ID stays `com.orca.companion` to retain upgrades and data.

## Implemented in 0.17

- Calico launcher name, cream/ginger/cocoa palette derived from the supplied cat artwork.
- Original cat-face vector launcher icon; the animated pet still uses the existing bundled Calico APNG/SVG files.
- Native Jetpack Compose chat UI with conversation drawer, local history, streaming replies, and live animated Calico avatar.
- Tap the floating Calico pet to open chat; existing approval bubble gets priority.
- First-run permission explanation with independent Android settings for overlay, accessibility, notifications, and optional notification posting. **No automatic Accessibility enablement**.
- Calendar event composer via Android intent; Android web-search intent; no Google API keys.
- Context-aware cat reactions to user questions, error reports, programming, studying, music, and positive replies.
- A real localhost-only OpenAI-compatible LLM adapter, plus the existing independent 34-signal neural pet activity model. No cloud fallback, no API key, no fake chat completions.
- Stored conversations stay in app-private preferences and can be deleted inside the conversation drawer.
- Only `127.0.0.1`/`localhost` permit plain HTTP; other traffic retains the existing strict network security policy.

## Starting a local conversational LLM

The packaged neural **pet behavior classifier cannot generate arbitrary conversational text**.
Calico therefore talks to a local llama.cpp server on the *same Android device*.
For example, with a compatible GGUF model already downloaded in Termux:

```sh
llama-server -m /path/to/your-model.gguf --host 127.0.0.1 --port 8080 -c 4096
```

If that binary is installed elsewhere, use its actual absolute path. The endpoint is
`http://127.0.0.1:8080/v1/chat/completions`. Start the server first; then open Calico.
No remote Ollama server or cloud API is required. The current build **does not bundle model weights
or an LLM inference runtime**, so generative chat requires that local Termux process.

The pet's own classification and animations continue to work without that server.

## Permission behavior

Onboarding explains options and never grants Android permissions by itself.
Overlay is necessary only to display the floating pet. Accessibility is optional
for coarse screen activity reactions; Calico does not upload screen text.
Notification listener access is optional for event and media reactions.

The calendar shortcut delegates creation to the installed calendar app.
The web-search shortcut opens Android search. Neither is an API-key-based Google
Workspace integration, and neither grants access to arbitrary apps.

## Limits and next work

- Google's proprietary Circle to Search has no reliable public launcher API.
  This build offers an Android search shortcut, **not** screen cropping or Circle to Search.
- Gmail, Drive, and Tasks account data require separate consented API authorization;
  not implemented here. No Google account sign-in or data syncing is claimed.
- Image-understanding requires a local multimodal model plus a user-approved
  screen-capture flow; not implemented in 0.17.
- Verify on a real device with the local model running and with the optional
  accessibility/overlay permissions enabled.

## Design reference

Chat layout concepts (conversation drawer, streaming responses, composer,
chat history) were adapted for the existing Kotlin/Compose application from
[EvanBacon/chat-template](https://github.com/EvanBacon/chat-template),
a separate MIT-licensed Expo/React Native project. Its cloud Anthropic API
backend is deliberately **not** incorporated into this no-key Android application.
