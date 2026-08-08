# SonderKey 2.5.1

- **Fixes the microphone key multiplying along the suggestion strip.** Rebuilding the toolbar cleared two of its three containers but not the one the voice key sits in when it is placed on the left, which is the default — so each rebuild added another microphone instead of replacing it. Anything that reloads the keyboard several times over, installing dictionaries in particular, left a row of them behind. They only disappeared on a force stop, since reopening the keyboard reuses the existing strip.
