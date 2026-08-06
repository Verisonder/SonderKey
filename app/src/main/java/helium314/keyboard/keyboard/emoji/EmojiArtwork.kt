// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import helium314.keyboard.latin.R

/**
 * Artwork for emoji that no font on the device can be relied on to draw.
 *
 * Newly encoded emoji are a chicken-and-egg problem: the character exists and types correctly, but
 * the phone's font predates it, so the palette shows an empty box. Shipping a font did not solve
 * it, so these few are drawn from bundled images instead — the picture is guaranteed to be right
 * regardless of what the device or its fonts know about.
 *
 * Only characters that actually need it are listed; everything else is drawn as text as before.
 */
object EmojiArtwork {

    private val drawableFor = mapOf(
        "\uD83E\uDEEA" to R.drawable.emoji_1faea, // distorted face
        "\uD83E\uDEEF" to R.drawable.emoji_1faef, // hairy creature
        "\uD83E\uDEC8" to R.drawable.emoji_1fac8, // fight cloud
        "\uD83E\uDECD" to R.drawable.emoji_1facd, // orca
        "\uD83D\uDED8" to R.drawable.emoji_1f6d8, // landslide
        "\uD83E\uDE8A" to R.drawable.emoji_1fa8a, // trombone
        "\uD83E\uDE8E" to R.drawable.emoji_1fa8e  // treasure chest
    )

    private val cache = HashMap<String, Bitmap?>()

    fun has(label: String?) = label != null && label in drawableFor

    /** Decoded once and kept; the set is tiny and the palette redraws constantly. */
    @Synchronized
    fun bitmap(context: Context, label: String): Bitmap? = cache.getOrPut(label) {
        val res = drawableFor[label] ?: return@getOrPut null
        runCatching { BitmapFactory.decodeResource(context.resources, res) }.getOrNull()
    }
}
