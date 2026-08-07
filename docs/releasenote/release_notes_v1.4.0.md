# SonderKey 1.4.0

- **Gesture typing works straight after installing it.** The library can only be loaded when the process starts, and the app previously had no way to restart itself without disappearing — Android does not allow starting an activity from the background. A small helper now runs in its own process, restarts the app, and brings the settings back up, so the library takes effect immediately and setup ends where you expect.
- **English (United States) is the default language.** English (Australia) is declared first in the subtype list, so any match that was not exact landed there instead.
