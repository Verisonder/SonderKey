# SonderKey 1.1.6

## Defaults

Tuned from device testing:

- **English (US)** is now enabled out of the box regardless of the device locale. Upstream derived the starting layout from the system locales, so a phone set to another language got a keyboard the user never asked for. Other languages remain one tap away under Languages.
- **Keyboard font scale** back to 100%.
- **Long-press key hints** off.
- **Style** back to Rounded, **Colors** back to dynamic, **Colors (night)** back to Darker. The Sonder key style and the Sonder Light / Sonder Dark themes are still there — they're just no longer forced on a fresh install.

Icon style stays on Sonder.

## Emoji

- Emoji the active font cannot actually draw no longer appear in the palette. Filtering by Android version alone was not enough — an emoji can sit inside a supported tier and still have no glyph in whichever font is in use, which drew an empty box. The palette now also checks each emoji against the font itself, worked out once per font and cached.

## Settings appearance

- The accent colour now applies to the settings app straight away. It was read once when the screen was first composed, so the app kept its old colour until a restart and the picker looked like it only affected the keyboard.
- Grouped settings cards are rounder, sit closer to the screen edge, and separate their rows with hairlines.

## Settings layout

- Group labels now sit above their card rather than inside it, in a muted grey.
- Row icons are plain outlines instead of filled circular badges.
- Rows are taller with more space around them.
