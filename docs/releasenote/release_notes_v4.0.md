# SonderKey 4.0

## Key press effects

A new screen in settings, off until you turn it on: each key you press throws a small burst of particles that scatter across the keyboard and fade.

- **Shape** — circle, ring, square, star, or an image of your own.
- **Colour** — taken from your theme's key text, accent or gesture trail, chosen at random per particle, or set to whatever you like.
- **A custom image** can be used in place of a shape, drawn in its own colours. Choosing one drops the count to a single particle per press, since a dozen overlapping copies of a picture rarely reads well, but the count is yours to raise again.
- **Size, speed, spread, gravity and lifetime** are all adjustable. Spread runs from a narrow upward jet to a full circle. Gravity goes below zero, where particles rise instead of falling.

Particles draw above the whole keyboard rather than being clipped to the key they came from, so a burst carries on over its neighbours.

## Backup and restore

- **A failed restore no longer takes your settings with it.** The old order cleared the settings it was about to replace and only then began reading the backup, so a file it could not parse left them deleted with nothing written back - and reported success. The file is now read in full before anything is touched, and a restore that fails says so and changes nothing.
- **Key press effect settings are backed up alongside the particle image**, so ticking Theme and appearance brings all of the effect or none of it.

## Fixes

- The **microphone key no longer carries a long press hint dot**. It also turns red while listening, and a permanent coloured dot was competing with the one signal that says the microphone is open.
