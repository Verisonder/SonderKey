# SonderKey 2.5

- **Restoring a LeanType backup now brings back settings you never changed.** A backup only stores settings you actually touched, so anything left at its default was missing from the file entirely and picked up SonderKey's default instead — which differs from LeanType's in sixteen places, among them key hints and narrow key gaps. Those now restore to the values the backup's own app used. Settings you did change were always restored and still take priority. Thanks to @cinnabar777 for the report.

- **Toolbar keys you never had no longer appear after a restore.** Handwriting is only offered on the standardfull build, so a backup from any other build has no entry for it and was being handed the key switched on — for a plugin that was never installed. Keys absent from a backup now stay off.

- **Swipe down on the toolbar to hide the keyboard is now on by default.** It was off, which meant most people never found it. Turn it off again under Toolbar if you'd rather not have it.
