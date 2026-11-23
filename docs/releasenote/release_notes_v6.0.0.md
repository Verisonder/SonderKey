# SonderKey 6.0.0

## Typing that learns your hands

- **Learn how you type** (Typing, on by default). The keyboard notices where you actually tap each letter, from the words you keep, and moves the edges between keys to match. A key only changes after enough taps, and never by more than a third of its width. Only tap positions are stored, never words, and only on this phone. Nothing is learned in private mode or in password fields. Reset it any time.
- **Autopilot, reworked.** Every tap is now weighed two ways: how well it fits each key, including where you usually land, and how likely each letter is to come next. It now also helps with the first letter of a word. A tap near the middle of a key always stays on that key. Strength 10 is now the useful maximum.
- **Outline the touch areas** draws the real boundaries as they stand, learned taps included.

## Corrections that listen

- **Remember undone corrections** (on by default). Undo an auto-correction and keep your word, and that correction won't be made again for it. It stays in the suggestion strip if you ever want it. Picking it from there allows it again.

## Better predictions

- **Your usual next words.** The keyboard remembers which words you type after the last one or two and suggests those first. Recent habits count for more than old ones. Forget them any time under Suggestions.
- **Smarter predictions** (optional download, English). A small language model on this phone reads the sentence so far and suggests the next word. Download it under Typing → Suggestions (about 165 MB, use Wi-Fi). It runs only on the phone and sends nothing anywhere. The model is SmolLM2-135M (Apache-2.0).
- New predictions only reorder what's shown. They never make auto-correction replace a word you typed.
