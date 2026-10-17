// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.database

import helium314.keyboard.latin.ClipboardHistoryEntry

/**
 * What the clipboard panel reads from. Two of these exist: the saved history
 * ([ClipboardDao]) and the private-mode clipboard ([PrivateClipboard]), which lives only
 * in memory. The panel never needs to know which one it is showing.
 */
interface ClipStore {
    var listener: ClipboardDao.Listener?

    fun addClip(timestamp: Long, pinned: Boolean, text: String, imageUri: String? = null)
    fun isPinned(index: Int): Boolean
    fun getAt(index: Int): ClipboardHistoryEntry?
    fun getClips(): List<ClipboardHistoryEntry>
    fun get(id: Long): ClipboardHistoryEntry?
    fun count(): Int
    fun sort()
    fun togglePinned(id: Long)
    fun deleteClipAt(index: Int): ClipboardHistoryEntry?
    fun restoreClip(entry: ClipboardHistoryEntry)
    fun clearOldClips(now: Boolean = false)
    fun clearNonPinned()
    fun clear()
}
