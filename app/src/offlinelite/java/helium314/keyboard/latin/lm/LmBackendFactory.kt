// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.lm

import java.io.File

/** This edition has no way to download the language model, so there is nothing to run. */
object LmBackendFactory {
    val isAvailable = false

    fun open(model: File): LmBackend? = null
}
