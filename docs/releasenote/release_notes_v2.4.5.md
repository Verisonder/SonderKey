# SonderKey 2.4.5

- **Fixes voice typing failing with "Failed to get field ID" on release builds.** The speech library reads its configuration through JNI by field name, and release builds shrink the app by renaming and removing anything that looks unused — which those fields do, since nothing in Java or Kotlin reads them. They are now protected from that. Debug builds were unaffected, which is why this only appeared on the published APK.
