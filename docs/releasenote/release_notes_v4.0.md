# SonderKey 4.0

## Voice typing

- **Typing during dictation no longer ends the turn.** In *At every pause*, the keyboard stays usable while the microphone is open, so you can correct a word by hand without losing the dictation. The turn still ends when you type in *Keep correcting*, where it has to: that mode rewrites what it already wrote, and a keystroke in the middle of it used to make the output double.
- **A new switch, "Fit dictated text to its surroundings".** On by default, which is the behaviour you already had: a space where one is needed, and sentence case matched to what is around the cursor. Turn it off and the text arrives exactly as it was spoken, which is what dictating code, shell commands or markup wants. Phrases are still kept apart from one another.
- **Hold the microphone key to pick a mode.** The three dictation modes were only in settings, several taps away, but the choice changes more often than that. Holding the key offers them and starts dictating in whichever you choose.
- **A dictation that ends by itself no longer runs words together.** The silence timeout closes a turn after a few seconds of quiet, so pausing to think started a new one without meaning to, and the first word of it arrived stuck to the last word of the one before.
- **The start of a word is no longer clipped after a pause.** Audio captured between phrases was discarded in whole chunks, so a word beginning part way through one lost its first sound and "test" could arrive as "est".
- **A dictation interrupted while it was finishing no longer mangles the text.** Typing while the closing transcription was still being decoded could delete what you had just typed and write the whole dictation over the top of it.

## Backup

- **Backups now include what was downloaded.** Only your own learned words were being saved from the dictionary folder, so restoring left you fetching every language again. Downloaded dictionaries, the voice engine and its models, and the handwriting and gesture plugins are each their own category now.
- **Eight categories and a select-all.** Everything is included by default; untick whatever you would rather not carry. Note that a backup with voice models in it is a large file.
- **Restoring your typing history no longer deletes your dictionaries.** They share a folder, and clearing it took both.

## Text

- **A space before punctuation is kept by default.** *Preserve space before punctuation* was already in Text correction; it is now on unless you turn it off.

## Under the hood

- A speech model is now described as a set of files rather than exactly two, so architectures built differently — an encoder and a decoder, or a three-part transducer — can be added as a single entry.
