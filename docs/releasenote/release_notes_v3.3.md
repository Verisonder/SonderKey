# SonderKey 3.3

- **The comma key popup can now be customised.** Under Toolbar → Select comma popup keys, choose what appears when you long press the comma key and what order it comes in. The five entries that were fixed before — clipboard, emoji, language switch, one-handed mode and settings — are still there and still on by default where they were, but every toolbar key is now available too, so undo, select all, text editing or voice input can live there instead. This also covers the key when it becomes a slash in web and email fields.

- **Voice typing no longer keeps the microphone open after you leave.** Closing the keyboard or switching to another app ended the session but never told the recorder, so it carried on listening, and in "Keep correcting" mode carried on transcribing, with no indicator anywhere on screen. The turn now ends with the keyboard.

- **Typing during a dictation ends the dictation.** Before, it only made the next pass append rather than replace. Since "Keep correcting" re-transcribes everything you have said on each pass, that meant every pass re-emitted the whole dictation and the text doubled, and kept doubling. Taking over with the keyboard now simply ends the turn.

- **Dictated text fits what is already around it.** Speaking with the cursor against the end of a word ran the words together, and a space was added between phrases whether or not one was already there. What is actually before the cursor is now taken into account, including whether the phrase is starting a sentence — so a phrase landing mid-sentence no longer arrives with a capital letter.

- **A pulsing microphone replaces the listening banner.** The old indicator claimed the whole suggestion strip for the length of a turn, so it competed with suggestions and disappeared the moment you typed, which is exactly when knowing the microphone is still open matters. The microphone key now pulses instead and the strip is left alone. This is the new default; the old indicator is still used when no microphone key is visible to pulse, and can be chosen under Voice typing.

- **Dictation stops on its own after silence.** On by default, after three seconds, adjustable from one to fifteen under Voice typing. Whatever you said is still transcribed and inserted.

- **A dictation that ran to the sixty second limit is no longer thrown away.** Recording stopped at the cap as intended, but nothing finished the turn, so the audio went untranscribed and the indicator stayed on screen. It now ends the same way pressing the microphone key does.

- **Check for updates is easier to find.** It now sits in the main settings under Advanced, as well as in About, and has its own icon rather than borrowing the information one.
