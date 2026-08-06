# SonderKey 1.1.2

## Sonder theme

- **New Sonder theme screen**, at the top of settings. Pick one accent colour and it drives both the settings app and the Sonder Light / Sonder Dark keyboard themes.
- Preset swatches, hue / saturation / brightness sliders, hex entry, and a live keyboard preview that animates as the colour changes.
- The rest of the palette is derived from the accent. Tones are placed on the CIE L\* curve so the ramp stays perceptually even whichever hue is chosen, and very dark or very bright picks are lifted to stay legible as an accent.

## Expressive shape and motion

- Rounder shape scale throughout the settings app, so containers read as distinct objects rather than stacked panels.
- Springy press feedback on colour swatches, animated selection ring, and animated colour transitions in the preview.

## Emoji

- The **Override Emoji version** slider was capped at Android 15 by an upstream limit, so the Emoji 17.0 set shipped in the app could never be reached even with the bundled font installed. The ceiling now goes to Android 16.
- Emoji support is re-probed whenever the font changes. It previously ran once and cached the result forever, so a device that had already decided "Android 15" kept that answer after the bundled font arrived.
- A tier now counts as supported on a majority of its emoji rather than on one probe character.

## Icon

- The app icon is now correctly centred. The adaptive foreground layer previously contained the whole rounded plate, so launchers were masking a square inside a squircle; the mark also sat noticeably off-centre at every density.

## Fixes carried over from 1.1.0

- Migration blocks inherited from upstream no longer re-run on every update, which had been silently resetting punctuation suggestions and clipboard retention.
- The bundled emoji font is limited to Android 13 and above, where its glyph format actually renders.
