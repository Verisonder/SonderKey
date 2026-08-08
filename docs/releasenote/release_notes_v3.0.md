# SonderKey 3.0

- **The keyboard's top edge can now be shaped.** Under Appearance → Sonder theme → Shape, choose flat, rounded, or inverted. Rounded curves the top two corners off. Inverted keeps the keyboard's own edge straight and instead fills up and around the corners of the app above it, so the app appears to have rounded bottom corners with the keyboard wrapping them. Flat remains the default, so nothing changes unless you pick otherwise.

- **The background colour picker now does what it says.** Every other colour in the Sonder theme was applied exactly as chosen, but the background was pushed to one end of the lightness scale and stripped of most of its colour, so every choice came out near-black or near-white with a tint too faint to see. It now uses the colour as picked, like the rest. Light surface options have been added to the presets, since the light and dark Sonder themes no longer force this colour either way.

- **It is now obvious which colour you are editing.** The selected channel carries an outline, a filled container and bolder text, rather than a tint that was easy to miss and left the swatches and sliders below looking unattached to anything.

- **Dragging a colour slider no longer flickers the keyboard.** Every change rebuilt the live keyboard immediately, which made sliders stutter and could bring the app down if it happened while the keyboard was busy handling a paste. Changes apply straight away; the rebuild now waits until they stop coming.

- **Voice typing defaults to correcting as you speak.** The transcription mode introduced in 2.7 now starts on "Keep correcting", which re-transcribes and refines the text every second. "At every pause" and "When you finish" remain available under Voice typing.
