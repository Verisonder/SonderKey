package helium314.keyboard.keyboard.emoji

import android.content.Context
import android.graphics.Paint
import android.os.Build
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

object SupportedEmojis {
    private val unsupportedEmojis = hashSetOf<String>()

    fun load(context: Context) {
        determineMaxSdk(context)
        val maxSdk = context.prefs().getInt(Settings.PREF_EMOJI_MAX_SDK, 0)
        unsupportedEmojis.clear()
        context.assets.open("emoji/minApi.txt").reader().readLines().forEach {
            val s = it.split(" ")
            val minApi = s.first().toInt()
            if (minApi > maxSdk)
                unsupportedEmojis.addAll(s.drop(1))
        }
    }

    /**
     * Identifies the font the emoji set was last probed against. Detection used to run exactly
     * once and never again, so swapping the font — importing one, or SonderKey starting to ship
     * its own — left the old, lower result cached forever and the newer emoji stayed hidden.
     */
    private fun fontSignature(context: Context): String {
        val s = Settings.getInstance()
        return when {
            s.useSystemEmoji() -> "system:${Build.VERSION.SDK_INT}"
            Settings.getCustomEmojiFontFile(context).exists() ->
                "custom:${Settings.getCustomEmojiFontFile(context).lastModified()}"
            s.useBundledEmojiFont() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                "bundled:${Settings.BUNDLED_EMOJI_FONT_VERSION}"
            else -> "system:${Build.VERSION.SDK_INT}"
        }
    }

    private fun determineMaxSdk(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val prefs = context.prefs()
        val signature = fontSignature(context)
        if (prefs.contains(Settings.PREF_EMOJI_MAX_SDK)
            && prefs.getString(Settings.PREF_EMOJI_MAX_SDK_FONT, null) == signature) return

        val paint = Paint()
        (Settings.getInstance().customEmojiTypeface ?: Settings.getInstance().customTypeface)
            ?.let { paint.setTypeface(it) }
        val maxApi = context.assets.open("emoji/minApi.txt").reader().readLines().maxOf {
            val s = it.split(" ")
            // probe every entry in the tier, not just the first: a single missing glyph in an
            // otherwise supported set used to disqualify the whole tier
            val supported = s.drop(1).count { e -> paint.hasGlyph(e) } * 2 >= s.size - 1
            if (supported) s.first().toInt() else 0
        }
        val newMax = maxApi.coerceAtLeast(Build.VERSION.SDK_INT)
        prefs.edit {
            putInt(Settings.PREF_EMOJI_MAX_SDK, newMax)
            putString(Settings.PREF_EMOJI_MAX_SDK_FONT, signature)
        }
    }

    fun isUnsupported(emoji: String) = emoji in unsupportedEmojis
}
