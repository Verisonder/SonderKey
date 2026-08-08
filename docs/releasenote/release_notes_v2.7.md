# SonderKey 2.7

- **Voice typing can now write as you speak, and the keyboard stays usable while it listens.** Previously nothing appeared until you tapped the microphone a second time. There are three choices under Voice typing, because the two live approaches trade off against each other rather than one simply being better:
  - **When you finish** — the previous behaviour, kept for anyone who prefers it.
  - **At every pause** — the recording is split where you pause for breath and each phrase is transcribed on its own while the microphone stays open. Text lands a phrase behind you, and a long dictation costs no more per phrase than a short one. This is the default.
  - **Keep correcting** — everything said so far is transcribed again every second and the text replaced, so later words can fix earlier ones. Each pass is longer than the last, so this slows down on a long dictation.

- **The listening indicator no longer disappears part way through a dictation.** Word suggestions appear as soon as text is inserted, and they were taking over the suggestion strip, leaving no sign that the microphone was still open. The strip now stays with voice typing until the turn ends.
