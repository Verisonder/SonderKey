# SonderKey 2.4.4

**Releases now ship a single APK.** `standardfull` is a superset of the other variants — everything they do, plus handwriting — so publishing four builds only made it harder to know which to download. The file is simply `SonderKey_<version>.apk`.

The other variants still build from source for anyone who wants them, including the offline ones with no internet permission at all.

## Since 2.4.0

- Voice model downloads no longer fail with "No such file or directory". Both downloads were staged in the cache directory, which Android trims under pressure — taking the second file's destination with it mid-download.
- Voice typing reports **why** it produced no text, instead of every failure reading as "Nothing was heard".
- A speech engine that fails to load is no longer deleted, so a transient failure does not cost a full re-download.
- **Check for updates** in About: finds a newer release, downloads it, and hands it to the system installer.
