# SonderKey 1.3.7

- **Finishing setup now returns to the settings.** Installing the gesture typing library requires the process to restart, and the restart simply killed the app, which looked like being dumped on the home screen. The settings are now scheduled to reopen before the process ends.
- **The microphone permission button updates once permission is granted.** It read the permission only when the screen was first drawn, so it kept offering to ask after the answer had already been given. This applies to both the setup wizard and the Voice typing screen.
