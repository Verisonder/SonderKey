# SonderKey 1.1.8

## Emoji

The bundled emoji font has been swapped from Noto's vector build to its bitmap build. Two problems go away with it:

- **No more placeholder boxes.** Noto's vector build ships unfinished artwork for the Emoji 17.0 additions — distorted face, hairy creature, fight cloud, trombone, treasure chest, orca and landslide all rendered as a "NO GLYPH" box. The bitmap build of the same font version has the finished artwork for all seven. These could not be detected by probing, because the font reports a glyph for them either way.
- **Every Android version now gets the newer emoji.** The vector build needed Android 13 or later to render at all, so older devices were left on the system font. Bitmap emoji have been supported since long before that, and the version gate is gone.

The cost is app size: the bitmap build is roughly 5 MB larger.
