# SonderKey 1.1.9

## Emoji

**The emoji palette now actually draws with the bundled font.** It never did. The font was used to decide *which* emoji to offer and to render the search results and suggestion strip, but the palette grid itself is drawn by the keyboard view, which uses the keyboard's typeface — so the emoji were being drawn by the system font all along. On a device whose system font predates an emoji, that produced an empty box even though the app was carrying the artwork for it.

This is why newer emoji inserted correctly but looked wrong in the grid, and why the problem tracked the phone's Android version rather than the app's font.
