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
        val prefs = context.prefs()
        val maxSdk = prefs.getInt(Settings.PREF_EMOJI_MAX_SDK, 0)
        unsupportedEmojis.clear()
        context.assets.open("emoji/minApi.txt").reader().readLines().forEach {
            val s = it.split(" ")
            val minApi = s.first().toInt()
            if (minApi > maxSdk)
                unsupportedEmojis.addAll(s.drop(1))
        }
        // Tier filtering alone is not enough: an emoji can sit in a supported tier and still have
        // no glyph in whichever font is actually active, which draws a tofu box in the palette.
        // The offenders are worked out once per font and cached.
        prefs.getString(Settings.PREF_EMOJI_UNRENDERABLE, "")
            ?.split(" ")?.filter { it.isNotEmpty() }
            ?.let { unsupportedEmojis.addAll(it) }
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
            // A tier counts as supported if the font can draw any of its emoji. A majority rule
            // would be wrong here: most entries in the newer tiers are ZWJ or skin-tone
            // sequences, and hasGlyph only returns true for those when the font carries a GSUB
            // ligature, so a font with full coverage could still fail the vote. Tier 36, for
            // instance, is 43 entries of which only 7 are plain codepoints.
            val supported = s.drop(1).any { e -> paint.hasGlyph(e) }
            if (supported) s.first().toInt() else 0
        }
        val newMax = maxApi.coerceAtLeast(Build.VERSION.SDK_INT)

        // Walk every emoji this build knows about and note the ones the active font cannot draw,
        // so the palette shows nothing the device would render as a box.
        val unrenderable = StringBuilder()
        context.assets.open("emoji/minApi.txt").reader().readLines().forEach { line ->
            val parts = line.split(" ")
            if (parts.first().toInt() > newMax) return@forEach
            parts.drop(1).forEach { emoji ->
                if (!paint.hasGlyph(emoji)) {
                    if (unrenderable.isNotEmpty()) unrenderable.append(" ")
                    unrenderable.append(emoji)
                }
            }
        }

        prefs.edit {
            putInt(Settings.PREF_EMOJI_MAX_SDK, newMax)
            putString(Settings.PREF_EMOJI_MAX_SDK_FONT, signature)
            putString(Settings.PREF_EMOJI_UNRENDERABLE, unrenderable.toString())
        }
    }

    fun isUnsupported(emoji: String) = emoji in unsupportedEmojis
}
