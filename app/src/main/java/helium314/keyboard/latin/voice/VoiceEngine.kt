// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import android.content.Context
import android.os.Build
import helium314.keyboard.latin.utils.ChecksumCalculator
import helium314.keyboard.latin.utils.Log
import java.io.File

/**
 * Loads the optional speech-recognition libraries.
 *
 * They are downloaded on demand rather than shipped in the app, the same arrangement the gesture
 * typing library uses: most people never turn voice typing on, and the engine is around 25 MB
 * unpacked. Nothing here is required for the keyboard to work — if the libraries are missing or
 * fail to load, voice typing is simply unavailable.
 */
object VoiceEngine {
    private const val TAG = "VoiceEngine"

    const val VERSION = "1.13.4"
    private const val RELEASE_TAG = "voice-engine-1.13.4"
    private const val BASE_URL = "https://github.com/Verisonder/SonderKey/releases/download/$RELEASE_TAG"

    const val LIB_ONNXRUNTIME = "libonnxruntime.so"
    const val LIB_SHERPA_JNI = "libsherpa-onnx-jni.so"

    /** sha256 of the archive published for each ABI. A mismatch means the file is not loaded. */
    private val CHECKSUMS = mapOf(
        "arm64-v8a" to "f918b7ca0b958a6a53ad55a1293452483fe1e39e76520714ade69d656e68d5b8",
        "armeabi-v7a" to "6e27be6dea405dfb11f443944daf7f4434050ea5811ff22b3fa06e42d572c40e"
    )

    fun abi(): String? = Build.SUPPORTED_ABIS.firstOrNull { it in CHECKSUMS }

    fun isSupportedDevice() = abi() != null

    fun archiveUrl(): String? = abi()?.let { "$BASE_URL/sherpa-onnx-$VERSION-$it.tar.gz" }

    fun expectedChecksum(): String? = abi()?.let { CHECKSUMS[it] }

    /** Where the unpacked libraries live. Private to the app. */
    fun libDir(context: Context) = File(context.filesDir, "voice-engine")

    fun areLibrariesPresent(context: Context): Boolean {
        val dir = libDir(context)
        return File(dir, LIB_ONNXRUNTIME).isFile && File(dir, LIB_SHERPA_JNI).isFile
    }

    fun deleteLibraries(context: Context) {
        libDir(context).deleteRecursively()
        loaded = false
    }

    @Volatile private var loaded = false
    @Volatile private var failedOnce = false
    @Volatile var loadError: String? = null
        private set

    val isLoaded get() = loaded

    /**
     * Loads the libraries if they are present. Order matters: the JNI wrapper links against
     * onnxruntime, so that has to be in the process first.
     */
    @Synchronized
    fun ensureLoaded(context: Context): Boolean {
        if (loaded) return true
        if (failedOnce) return false
        if (!areLibrariesPresent(context)) return false
        return try {
            val dir = libDir(context)
            // onnxruntime first: the JNI wrapper records it as a dependency, and the linker
            // resolves it from what is already loaded rather than searching a path.
            System.load(File(dir, LIB_ONNXRUNTIME).absolutePath)
            System.load(File(dir, LIB_SHERPA_JNI).absolutePath)
            loaded = true
            Log.i(TAG, "voice engine loaded")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "could not load voice engine", t)
            loadError = t.message ?: t.javaClass.simpleName
            // Keep the files. Deleting them here meant a transient failure cost a 25 MB
            // re-download, and hid the cause behind "not installed".
            failedOnce = true
            false
        }
    }

    fun verifyArchive(file: File): Boolean {
        val expected = expectedChecksum() ?: return false
        return ChecksumCalculator.checksum(file).equals(expected, ignoreCase = true)
    }
}
