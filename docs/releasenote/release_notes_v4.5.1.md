# SonderKey 4.5.1

## Autopilot

- **The drawn key now matches the touch area exactly.** Growing the key had its own size setting, which meant the picture and the boundary it was meant to describe could disagree — and after 4.5 they did, by roughly double. The drawing is now taken from the same figure the keyboard uses to decide which key your finger hit, so what you see is where the boundary really is.
- **The separate Growth slider is gone.** Strength is the only number now. There was never a second effect to size, only a second way of picturing the first one.
- **"Grow the keys" is now "Show the effect"**, and "Show boundaries" is now "Outline the touch areas". The old names read as two more things the keyboard does, when both only decide whether you can see what Autopilot is already doing.
- A favoured key is drawn above its neighbours on both sides rather than above the one to its left and beneath the one to its right, and grows by the same number of pixels vertically as horizontally, matching the touch area rather than overshooting on taller keys.

Autopilot is still off by default, and what your finger hits is unchanged from 4.5.
