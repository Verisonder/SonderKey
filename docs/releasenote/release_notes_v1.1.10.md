# SonderKey 1.1.10

## Emoji

**Emoji that no device font can be relied on to draw are now drawn from bundled images.**

Newly encoded emoji are a chicken-and-egg problem. The character is valid and types correctly everywhere, but the phone's fonts predate it, so the palette shows an empty box. Shipping a font that contains the artwork did not solve it, because the platform — not the app — decides which font paints a given character.

So these are no longer drawn as text at all. Seven characters ship as images and are painted directly onto the key: distorted face, hairy creature, fight cloud, orca, landslide, trombone and treasure chest. Everything else is drawn as before.

This applies to the emoji palette and the emoji suggestion strip. The characters inserted are unchanged.
