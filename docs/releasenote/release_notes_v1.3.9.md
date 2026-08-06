# SonderKey 1.3.9

- **Finishing setup no longer closes the app.** Installing the gesture typing library used to kill the process so the library could load, which is why setup ended on the home screen. Relaunching automatically is not possible — Android does not allow an app to start an activity from the background — so the process is simply left alone. Setup ends in the settings, and a message notes that gesture typing becomes active the next time the app starts.
