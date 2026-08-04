# SonderKey

**SonderKey** is a privacy-first, highly customizable open-source keyboard for Android, developed by [Verisonder](https://github.com/Verisonder). It is a fork of [LeanType](https://github.com/LeanBitLab/LeanType), which is itself based on [HeliBoard](https://github.com/Helium314/HeliBoard) (AOSP / OpenBoard lineage).

Your keyboard sees everything you type. SonderKey is built on one principle: **what you type stays on your device.** No tracking, no analytics, no data sharing — and the Offline builds remove the `INTERNET` permission entirely at the manifest level, so network access is impossible by design, not by promise.

## Why SonderKey

- **🔒 Privacy by architecture** — Offline builds cannot touch the network. Standard builds only use the network for features you explicitly enable (AI proofreading via your own API key, dictionary downloads).
- **🎨 Deep customization** — themes, colors, key styles, layouts, toolbar, and more. Designed to go far beyond what stock keyboards allow.
- **🧠 Optional AI assistance** — proofread and translate using Gemini (default), Groq, or any OpenAI-compatible provider, with your own key. Never on by default.
- **⌨️ Full-featured** — gesture typing, clipboard history, text expander, floating keyboard, emoji search, handwriting input, custom layouts, and everything inherited from the HeliBoard/LeanType feature set.

## Feature overview

See [docs/FEATURES.md](docs/FEATURES.md) for the complete feature documentation.

## Build variants

| Variant | Network | Best for |
|---|---|---|
| **Offline** | ❌ Impossible (no INTERNET permission) | Maximum privacy |
| **Offline Lite** | ❌ Impossible | Minimal footprint, no AI |
| **Standard** | Opt-in only | AI features, dynamic downloads |

## Building

```
./gradlew assembleStandardDebug
./gradlew assembleOfflineDebug
```

Requires JDK 17 and the Android SDK/NDK.

## License & credits

SonderKey is licensed under **GPL-3.0-only** (with Apache-2.0 and CC-BY-SA-4.0 components — see the LICENSE files).

This project stands on the work of:
- [LeanType](https://github.com/LeanBitLab/LeanType) by LeanBitLab
- [HeliBoard](https://github.com/Helium314/HeliBoard) by Helium314 and contributors
- [OpenBoard](https://github.com/openboard-team/openboard) and the AOSP LatinIME project

All original copyright notices are preserved.
