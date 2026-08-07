# SonderKey 2.4.1

- **Fixes the voice model download failing** with "No such file or directory". Both downloads were staged in the cache directory, and the model is large enough that writing it there can prompt Android to trim that directory mid-download — taking the second file's destination with it. Downloads now go straight to their final location, and are only put in place once complete and verified.
