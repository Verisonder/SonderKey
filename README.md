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

- 🎨 **Deep customization** — themes, full color editor, key styles, adjustable layouts, custom toolbar, image backgrounds, Material You dynamic colors
- 🧠 **Optional AI assistance** — proofread & translate with **Gemini** (default), Groq, or any OpenAI-compatible provider, using *your* key — never on by default
- 👆 **Gesture typing** — fast swipe input powered by a native engine
- 📋 **Clipboard history** — with search, pinning, and undo
- 📝 **Text expander** — shortcuts with dynamic date/time/clipboard variables
- 🪟 **Floating keyboard** — draggable, resizable, multitask-friendly
- ✍️ **Handwriting input** & 🔎 **emoji search** & 🧭 **dedicated text-editing mode**
- 🌍 **Extensive language support** inherited from the HeliBoard lineage

Full documentation: **[docs/FEATURES.md](docs/FEATURES.md)**

## 🗺️ Where SonderKey is going

This fork exists to push further than its upstream in three directions:

1. **🎨 Sonder design language** — a new key style with modern squircle keys, press animations, gradient & glass themes, and shareable `.sonderkey` theme files
2. **🇲🇦 Darija-first typing** — an Arabizi-aware layout (3, 7, 9…) with real Moroccan Darija suggestions and autocorrect, fully offline. No mainstream keyboard takes Darija seriously. SonderKey will.
3. **🔍 Privacy dashboard** — see exactly which permissions are active and prove to yourself that nothing leaves the device

Follow the [releases](https://github.com/Verisonder/SonderKey/releases) to watch it happen.

## 📥 Download

First public release coming soon — debug builds are available now from [GitHub Actions](https://github.com/Verisonder/SonderKey/actions) artifacts.

## 🛠️ Building

```bash
./gradlew assembleStandardDebug   # standard variant
./gradlew assembleOfflineDebug    # offline variant
```

Requires JDK 17 and the Android SDK/NDK.

## 📜 License & credits

SonderKey is licensed under **GPL-3.0-only** (with Apache-2.0 and CC-BY-SA-4.0 components — see the LICENSE files).

Built on the shoulders of:
- [LeanType](https://github.com/LeanBitLab/LeanType) by LeanBitLab
- [HeliBoard](https://github.com/Helium314/HeliBoard) by Helium314 and contributors
- [OpenBoard](https://github.com/openboard-team/openboard) and the AOSP LatinIME project

All original copyright notices are preserved.

<div align="center">
<sub>Made with care by <a href="https://github.com/Verisonder">Verisonder</a> 🇲🇦</sub>
</div>
