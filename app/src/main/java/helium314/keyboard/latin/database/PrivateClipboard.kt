// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.database

import android.os.Handler
import android.os.Looper
import helium314.keyboard.latin.ClipboardHistoryEntry

/**
 * The private-mode clipboard.
 *
 * Held in memory only: nothing here is ever written to the database, a file, a backup or
 * the system clipboard, so no other app or website can read it and nothing survives the
 * keyboard being closed. Every entry is removed five minutes after it was copied, and the
 * whole store is wiped the moment private mode is turned off.
 */
class PrivateClipboard : ClipStore {

    override var listener: ClipboardDao.Listener? = null

    private val clips = mutableListOf<ClipboardHistoryEntry>()
    // Negative ids, so a private entry can never be mistaken for a saved one.
    private var nextId = -1L
    // Its own handler: the clipboard manager clears its main-thread callbacks whenever the
    // keyboard closes, and the expiry has to keep running regardless.
    private val handler = Handler(Looper.getMainLooper())
    private val sweep = Runnable { expire() }

    @Synchronized
    override fun addClip(timestamp: Long, pinned: Boolean, text: String, imageUri: String?) {
        if (text.isEmpty() && imageUri == null) return
        // Timed from when it arrived here, not from the clip's own timestamp, so a clip that
        // was made earlier still gets its full five minutes.
        val now = System.currentTimeMillis()
        val existing = clips.indexOfFirst { it.text == text && it.imageUri == imageUri }
        if (existing >= 0) {
            val entry = clips[existing]
            entry.timeStamp = now
            clips.sort()
            listener?.onClipMoved(existing, clips.indexOf(entry))
        } else {
            val entry = ClipboardHistoryEntry(nextId--, now, false, text, imageUri)
            clips.add(entry)
            clips.sort()
            listener?.onClipInserted(clips.indexOf(entry))
        }
        scheduleSweep()
    }

    @Synchronized
    override fun isPinned(index: Int) = clips.getOrNull(index)?.isPinned == true

    @Synchronized
    override fun getAt(index: Int) = clips.getOrNull(index)

    @Synchronized
    override fun getClips(): List<ClipboardHistoryEntry> = clips.toList()

    @Synchronized
    override fun get(id: Long) = clips.firstOrNull { it.id == id }

    @Synchronized
    override fun count() = clips.size

    @Synchronized
    override fun sort() = clips.sort()

    /** Pinning only reorders here: a pinned private clip still goes after five minutes. */
    @Synchronized
    override fun togglePinned(id: Long) {
        val entry = clips.firstOrNull { it.id == id } ?: return
        val oldPos = clips.indexOf(entry)
        entry.isPinned = !entry.isPinned
        clips.sort()
        listener?.onClipMoved(oldPos, clips.indexOf(entry))
    }

    @Synchronized
    override fun deleteClipAt(index: Int): ClipboardHistoryEntry? {
        if (index < 0 || index >= clips.size) return null
        return clips.removeAt(index)
    }

    @Synchronized
    override fun restoreClip(entry: ClipboardHistoryEntry) {
        if (System.currentTimeMillis() - entry.timeStamp >= LIFETIME_MS) return
        clips.add(entry)
        clips.sort()
        listener?.onClipInserted(clips.indexOf(entry))
        scheduleSweep()
    }

    override fun clearOldClips(now: Boolean) = expire()

    @Synchronized
    override fun clearNonPinned() = clear()

    /** Clear history in private mode clears everything, pinned or not. */
    @Synchronized
    override fun clear() {
        val count = clips.size
        clips.clear()
        handler.removeCallbacks(sweep)
        if (count > 0) listener?.onClipsRemoved(0, count)
    }

    /** The newest clip, for the paste key and the clipboard suggestion. */
    @Synchronized
    fun latest(): ClipboardHistoryEntry? = clips.maxByOrNull { it.timeStamp }

    @Synchronized
    private fun expire() {
        val cutoff = System.currentTimeMillis() - LIFETIME_MS
        val before = clips.size
        if (clips.removeAll { it.timeStamp <= cutoff } && clips.size < before) {
            listener?.onClipsRemoved(0, before - clips.size)
        }
        scheduleSweep()
    }

    @Synchronized
    private fun scheduleSweep() {
        handler.removeCallbacks(sweep)
        val oldest = clips.minOfOrNull { it.timeStamp } ?: return
        val due = (oldest + LIFETIME_MS - System.currentTimeMillis()).coerceAtLeast(0L) + 250L
        handler.postDelayed(sweep, due)
    }

    companion object {
        const val LIFETIME_MS = 5 * 60 * 1000L
    }
}
