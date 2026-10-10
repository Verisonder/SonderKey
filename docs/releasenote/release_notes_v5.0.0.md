# SonderKey 5.0.0

## A reorganised settings app

- **Eight places instead of thirteen**, each named for what it holds and with a line saying what is inside: Languages & layouts, Appearance, Keys & feedback, Typing, Toolbar & clipboard, Words & dictionaries, AI, and Privacy & system.
- **Search is the first thing on the main page.** Type part of any setting's name to jump straight to it.
- **One home per setting.** Things that belonged together now sit together: vibration, sound and long-press timing under Keys & feedback; language switching and the number row under Languages & layouts; clipboard history beside the toolbar; key press effects inside Appearance; gesture and voice typing at the top of Typing.
- **Less on each page.** Settings most people never change sit in a collapsed **More** group at the bottom of their page.
- **More rows show their current value**, including key selections ("7 on") and the size sliders.
- Every setting is still there and keeps its value. Nothing resets, and backups restore as before.

## Words & dictionaries

- **Learned words.** A new screen lists the words the keyboard has learned from your typing. Search them, sort by most used or alphabetically, and for any word: add it to the personal dictionary, forget it, or block it.
- **Choose when new words are added.** "Add after" sets how many times an unknown word must be typed before it goes into the personal dictionary. Uses are now counted per word, so the number means what it says.
- **Ask before adding.** With this on, new words wait under Learned words until you add or dismiss them. A dismissed word is not offered again.
- **Personal dictionary:** search matches anywhere in a word, sort by weight, and long-press to select several words and delete them together.
- **Export and import.** Export the whole personal dictionary to a text file and import it back with weights intact. Importing no longer duplicates words that are already there.
- Fixed: editing or deleting a word in the personal dictionary could in some cases also remove other words with the same weight.

## Auto-correction

- **Maximum letters changed.** Limit auto-correction to fixes that change at most a set number of letters. A swapped pair counts as one change and capitalisation counts as none. Set to No limit by default.

## Autopilot

- The touch-area outline can now be drawn as a rectangle, dashed rectangle, rounded shape, oval or a faint fill, in any colour. Rectangle and dashed show the exact boundary.

## Plugins

- Fixed: downloading the translation or handwriting plugin failed with "HTTP 404". Both are now published as one file per device type, and the keyboard picks the one that matches your phone.
