# SonderKey 2.4.2

- **Voice typing now reports why it failed.** A missing library, an unreadable model and genuine silence all produced the same "Nothing was heard" message, which made problems impossible to diagnose. Each now names its cause.
- A speech engine that fails to load is **no longer deleted**. A transient failure previously cost a full re-download and left the setup screen claiming nothing was installed.
