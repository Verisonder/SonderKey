<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/sonderkey_banner_dark.svg">
  <source media="(prefers-color-scheme: light)" srcset="docs/images/sonderkey_banner_light.svg">
  <img alt="SonderKey" src="docs/images/sonderkey_banner_dark.svg" width="720">
</picture>

<br><br>

[![License](https://img.shields.io/badge/license-GPL--3.0-2FB8A6?style=for-the-badge)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Verisonder/SonderKey?include_prereleases&style=for-the-badge&color=7ED89B)](https://github.com/Verisonder/SonderKey/releases)
[![Stars](https://img.shields.io/github/stars/Verisonder/SonderKey?style=for-the-badge&color=2FB8A6)](https://github.com/Verisonder/SonderKey/stargazers)
[![Build](https://img.shields.io/github/actions/workflow/status/Verisonder/SonderKey/build-debug-apk.yml?style=for-the-badge)](https://github.com/Verisonder/SonderKey/actions)

**A privacy-first, deeply customizable open-source keyboard for Android.**
<br>
*What you type stays on your device. Always.*

</div>

---

## 🛡️ Privacy is the architecture, not a checkbox

Your keyboard sees everything — passwords, messages, searches. Most keyboards phone home. SonderKey can't:

| Variant | Network access | Best for |
|---|---|---|
| 🔒 **Offline** | ❌ Impossible — no `INTERNET` permission in the manifest | Maximum privacy |
| 🪶 **Offline Lite** | ❌ Impossible | Minimal footprint, no AI |
| 🌐 **Standard** | Opt-in only, with your own API keys | AI features, dynamic downloads |

No tracking. No analytics. No accounts. No data collection of any kind.

## ✨ Features

- 🎙️ **On-device voice typing** — speech to text that runs entirely on your phone. Audio is never written to storage and never sent anywhere. Optional download, English.
- 🎨 **Deep customization** — the Sonder theme sets keys, function keys, accent and background independently, with presets, sliders, hex entry and a live preview. Plus the full color editor, key styles, adjustable layouts, custom toolbar and image backgrounds.
- 🧠 **Optional AI assistance** — proofread & translate with **Gemini** (default), Groq, or any OpenAI-compatible provider, using *your* key — never on by default
- 👆 **Gesture typing** — fast swipe input powered by a native engine
- 📋 **Clipboard history** — with search, pinning, and undo
- 📝 **Text expander** — shortcuts with dynamic date/time/clipboard variables
- 🪟 **Floating keyboard** — draggable, resizable, multitask-friendly
- ✍️ **Handwriting input** & 🔎 **emoji search** & 🧭 **dedicated text-editing mode**
- 😀 **Current emoji on any Android version** — a colour emoji font ships with the app, so the newest emoji appear without waiting for a system update
- 🌍 **Extensive language support** inherited from the HeliBoard lineage

Full documentation: **[docs/FEATURES.md](docs/FEATURES.md)**

## 🗺️ Where SonderKey is going

**Shipped in 2.4.0**

- ✅ **Offline voice typing** — on-device speech recognition, built on sherpa-onnx with NVIDIA's Parakeet. Your voice never leaves your phone, which mainstream keyboards structurally cannot offer.
- ✅ **Sonder theme** — four independently set colours driving both the keyboard and the settings app, derived on a perceptual curve so any hue stays balanced.
- ✅ **Emoji independence** — the emoji set no longer depends on the age of your device's system font.

**Next**

1. **🌍 More voice languages** — the model registry takes one entry per model; a multilingual option is the obvious next step
2. **🎨 Theme sharing** — presets gallery and shareable `.sonderkey` theme files
3. **🔍 Privacy dashboard** — see exactly which permissions are active and prove to yourself that nothing leaves the device

Follow the [releases](https://github.com/Verisonder/SonderKey/releases) to watch it happen.

## 📥 Download

Grab the latest APK from **[Releases](https://github.com/Verisonder/SonderKey/releases)**. Development builds are available from [GitHub Actions](https://github.com/Verisonder/SonderKey/actions) artifacts.

## 🛠️ Building

```bash
./gradlew assembleStandardfullDebug   # everything, the usual build
./gradlew assembleStandardDebug       # standard variant
./gradlew assembleOfflineDebug        # no INTERNET permission at all
```

Requires **JDK 17**, Android SDK **platform 36**, build-tools **36.0.0** and NDK **28.0.13004108**.

The SDK path must not contain spaces — the NDK's make-based toolchain fails on them.

## 📜 License & credits

SonderKey is licensed under **GPL-3.0-only** (with Apache-2.0 and CC-BY-SA-4.0 components — see the LICENSE files).

Built on the shoulders of:
- [LeanType](https://github.com/LeanBitLab/LeanType) by LeanBitLab
- [HeliBoard](https://github.com/Helium314/HeliBoard) by Helium314 and contributors
- [OpenBoard](https://github.com/openboard-team/openboard) and the AOSP LatinIME project

All original copyright notices are preserved.

<div align="center">
<sub>Made with care by <a href="https://github.com/Verisonder">Verisonder</a></sub>
</div>
