# SonderKey 2.4.0

The first full release since 1.0.1, and a large one. Voice typing, a colour system of its own, and a long list of fixes to things inherited from upstream.

## Voice typing

**Speech to text that runs entirely on this device.** Audio is never written to storage and never leaves the phone.

- A **Voice typing** screen downloads what it needs: a speech engine (~25 MB) and an English language model (~126 MB). Neither ships inside the app, so the download stays small for anyone who does not want this.
- The microphone key on the toolbar records instead of handing off to the system. Press once to start, press again to transcribe and insert. Recording stops on its own after a minute.
- While recording, the suggestion strip shows a **Listening** label and a level meter driven by your voice, so it is obvious the microphone is live.
- The key is pinned above the keyboard on the left by default; either end and either placement can be chosen.
- Built on sherpa-onnx (Apache-2.0) with NVIDIA's Parakeet TDT-CTC 110m. English only — speaking another language produces nonsense rather than an error, so the app now says so up front.

## Sonder theme

- **Four independent colours** — keys, function keys, accent and background — each set directly, with presets, hue/saturation/brightness sliders, hex entry and a live keyboard preview.
- The rest of the palette is derived from them, with tones placed on the CIE L\* curve so the ramp stays even whichever hue is chosen.
- The colours apply to the settings app as well as the keyboard, and update as you pick them.
- **Sonder Dark** is the default night theme.

## Emoji

- The **Emoji 17.0** set is available regardless of the device's Android version. A colour emoji font ships inside the app and is used to draw the palette.
- Seven emoji whose artwork no font can be relied on to draw are rendered from bundled images instead, so the palette never shows an empty box.
- The **Override Emoji version** ceiling was raised; it was capped below the emoji the app already contained.
- Emoji suggestions are off by default, and are never offered for gesture typing, where they displaced the word actually drawn.

## Setup and libraries

- The setup wizard now offers the **main dictionary**, without which gesture typing runs and silently returns nothing, and can install **voice typing** and grant microphone access.
- Installing the gesture typing library no longer closes the app. It restarts properly and returns to the settings, and the library works immediately.
- **English (United States)** is the default language.
- The handwriting plugin download button is visible and reachable.

## Appearance

- Settings use a grouped layout with rounded cards, labels above them, plain outline icons and roomier rows.
- The app icon is correctly centred at every size; the adaptive icon previously contained the whole plate, so launchers masked a square inside a squircle.

## Fixes carried over

- Migration steps inherited from upstream no longer re-run on every update, which had been silently resetting punctuation suggestions and clipboard retention.
- The microphone key no longer depends on a system voice input method existing.
- Default keyboard height, padding, toolbar keys and key hints tuned from device testing.
