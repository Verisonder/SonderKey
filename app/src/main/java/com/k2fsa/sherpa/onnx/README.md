# Vendored sherpa-onnx Kotlin API

These files are taken unmodified from sherpa-onnx v1.13.4, except that the
`System.loadLibrary` calls have been removed: the native library is not packaged
in the app, and `helium314.keyboard.latin.voice.VoiceEngine` loads the downloaded
copy before any of this is used.

Source: https://github.com/k2-fsa/sherpa-onnx
Licence: Apache-2.0
