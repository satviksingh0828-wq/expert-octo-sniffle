# ORCA smart pet: local neural activity brain

## Architecture
- User-enabled Android Accessibility provides foreground package, focus, text-change, scroll, tap and optional **non-editable** UI labels.
- Permission-free device context adds battery level, charging, power-save state, orientation and time-of-day as coarse neural inputs.
- Optional NotificationListener grants notification-arrival events and active MediaSession playback **state**, not message or music contents.
- The context brain stores **ephemeral metadata only**, timestamps, context tags, and state. App-specific heuristics are limited to known package name patterns.
- An on-device 26→24→12 MLP neural classifier is trained at bootstrap on 7,200 synthetic metadata scenarios and optionally fine-tuned on explicit user feedback. It requires no servers or model downloads.
- An event-driven state arbiter prioritizes active typing, 10-second typing pause, screen-off, short notification, error/success, music/video playback, idle and neural classifications.
- Only user-corrected numeric neural weights are persisted. No screenshot, messages, URLs, contacts, media titles or raw screen text are saved.

## Important limitations
This is a genuine neural classifier, **not** a large language model, pretrained visual model, universal browser-tab reader or human-emotion detector. Synthetic bootstrap data are not a substitute for real-world evaluation. Android cannot freely access protected app contents. The optional screen-text classifier uses imperfect best-effort redaction, and some apps provide little Accessibility information.

## Manual acceptance tests
1. On phone/tablet, Start ORCA and explicitly enable overlay and Accessibility.
2. Open a chat, type: animation should type immediately.
3. Stop for 10 seconds without leaving the editor: animation switches to thinking.
4. Resume typing: immediately switches back to typing.
5. Switch to Chrome: editor state should reset; scrolling changes activity.
6. Enable Notification Listener; play Spotify: music foreground session should produce conducting animation; pause and verify it no longer persists.
7. Lock/unlock screen: pet should sleep/wake appropriately after signal integration.
8. Enable notification permission, receive a notification: react briefly and return.
9. Open protected/sensitive app: scrub prior context and switch to idle.
10. Verify no raw text is persisted in logs or SharedPreferences.

## Privacy and model quality
The model uses only coarse features. Heuristic guards are deliberate because incorrect neural predictions must not override clear typing and playback signals. The app does not request screenshot access or MediaProjection. Real device validation and battery benchmarking remain necessary.


## Device context inputs
Battery percentage is reduced to coarse normalized state, charging/power-save are booleans, orientation is portrait/landscape, and time-of-day is a coarse local bucket. These signals are event-driven where Android exposes broadcasts; ORCA does not poll them continuously.


## Multisignal temporal brain (0.14)

ORCA now fuses 34 coarse inputs into a 34→32→12 local neural classifier. New signals include network availability/transport, wired or Bluetooth audio route, coarse accelerometer motion, and rapid app-switching context. The monitor does not keep SSIDs, Bluetooth names/addresses, location, microphone audio, or raw sensor history.

The output layer now uses exponential probability smoothing plus hysteresis. Hard signals such as typing, typing-pause, notification, error/success, foreground media and screen-off react immediately. Softer neural guesses must remain stable before switching animations, reducing flicker when several weak signals change together.

The signal monitor is lifecycle-bound to the floating pet service and unregisters network, audio and sensor callbacks when ORCA stops.


## Personalized system brain (0.16)

ORCA now keeps a bounded, privacy-preserving long-term habit memory. It learns only coarse app categories (browser, coding, music, video, messaging, other), broad time-of-day buckets, and weekday/weekend context. It stores intent counters rather than raw package contents, screen text, URLs, message bodies, or sensor history.

Personalization is fused into soft neural decisions as a capped prior, never more than 24% of the soft evidence. Clear real-time states such as typing, thinking-after-pause, media playback, notifications, screen-off and other hard signals remain higher priority. High-confidence observations gradually update the memory; explicit Teach ORCA feedback has stronger weight.

A new system-state monitor adds Android device-idle/Doze, interactive/user-present state and thermal pressure. Device idle or non-interactive state causes sleep immediately. Severe thermal pressure biases idle behavior toward sleep. Long sessions (90+ minutes) that become idle also rest instead of remaining indefinitely active.

The ORCA screen now includes **Reset learned habits**, which clears the persistent personalization counters. The signal monitors remain event-driven and unregister when the floating pet service stops.


## Adaptive motion sampling

The accelerometer is sampled only while the pet service is running, the display is interactive and Android battery saver is off. Screen and power-save broadcasts immediately update the sampling policy; suspending sampling clears stale motion features. Network and audio-route callbacks remain event-driven. This saves sensor work without requiring any new permission. Verify screen-off/on, battery-saver toggles, and resumed motion on a real device.
