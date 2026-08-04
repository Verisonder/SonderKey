### 💖 Support Our Work
*   We are committed to making our apps as powerful and polished as possible. As an entirely community-funded project, we rely on your support to keep going, please consider becoming a [sponsor](https://github.com/sponsors/LeanBitLab) or supporting us on Ko-fi. A huge thank you to all our current supporters!

## 🚀 What's New

### 👆 Gesture & Swipe Engine
*   **Pure-Java Fallback Engine (Experimental Alpha)**: Added an experimental, alpha-stage pure-Java fallback gesture typing engine (`SwipeGestureEngine`) so gesture typing works out of the box without requiring native libraries (ported from [HeliBoard #2351](https://github.com/HeliBorg/HeliBoard/pull/2351)).
*   **Engine Selection Toggle**: Added settings to toggle between native and fallback gesture typing, dynamically hiding unnecessary settings like native library loader.
*   **Self-Learning**: Enabled swipe gesture self-learning by indexing user history and personal dictionaries.
*   **Ranking & Matching accuracy**: Added endpoint-weighted L2 scoring, shape length mismatch penalty, sequence matching penalty, next-word bigram prediction boost, and forgiving start/end matching.
*   **Index Building Optimizations**: Sped up index building, pruned low-frequency entries, cached charToPos, and optimized memory allocations to avoid ANRs.

### 🛠️ Visual & Keyboard Settings
*   **Space-Strip Punctuation Fix**: Restored manual space handling! Spaces are no longer stripped before punctuation marks if they were entered manually (only auto-inserted spaces are stripped).
*   **Toolbar Longpress Toggle**: Added settings toggle to disable long-press hint dots on toolbar keys.
*   **Blocked Words Screen**: Display total count of blocked words in the dictionary settings title.
*   **Text Edit persistence**: Added a toggle to persist text edit mode.
*   **Setup Wizard Enhancements**: Added language layout selection and gesture typing engine selection options to the welcome startup setup wizard.

## 📦 Downloads (Choose Your Flavor)

| File | Description | Permissions |
| :--- | :--- | :--- |
| **`1-LeanType_3.9.1-standardfull-release.apk`** | **Recommended**. Cloud AI | Internet | 
| **`1-LeanType_3.9.1-standard-release.apk`** | **Fdroid Build**. Standard + No Handwrite | Internet |
| **`2-LeanType_3.9.1-offline-release.apk`** | **Privacy Focused**. No Internet. Offline AI Only. | No Internet |
| **`3-LeanType_3.9.1-offlinelite-release.apk`** | **Minimalist**. Pure FOSS. No AI code. | No Internet |
