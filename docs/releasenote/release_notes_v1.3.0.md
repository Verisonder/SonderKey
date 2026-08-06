# SonderKey 1.3.0

## Voice typing

**Speech to text that runs on the device.** Audio is transcribed locally, is never written to storage, and never leaves the phone.

A new **Voice typing** screen under settings downloads what it needs:

- **Speech engine**, about 25 MB — the recognition runtime
- **Parakeet 110m (English)**, about 126 MB — the language model

Neither ships inside the app, the same arrangement the gesture typing library uses, so the download stays small for anyone who does not want this.

Once both are installed, the microphone key on the toolbar records instead of handing off to the system voice input. Press once to start, press again to transcribe and insert. Recording stops on its own after a minute.

If voice typing is not set up, the microphone key behaves as before and opens whichever voice input method the system provides, so it is never dead.

Built on sherpa-onnx (Apache-2.0) with NVIDIA's Parakeet TDT-CTC 110m.
