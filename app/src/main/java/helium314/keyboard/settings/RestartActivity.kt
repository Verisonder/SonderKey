// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process

/**
 * Restarts the app without it appearing to vanish.
 *
 * Some things — the gesture typing library above all — are loaded once when the process starts and
 * cannot be swapped in afterwards. Killing the process from inside itself leaves nothing able to
 * bring it back: Android forbids starting an activity from the background, so a scheduled relaunch
 * is silently dropped and the user is left on the home screen.
 *
 * This activity runs in its own process, so when it kills the main one it is still alive and in the
 * foreground, and is therefore allowed to start the settings again.
 */
class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pid = intent.getIntExtra(EXTRA_PID, -1)
        if (pid > 0) Process.killProcess(pid)

        startActivity(
            Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("from_ime", true) // do not reopen the setup wizard
        )
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        private const val EXTRA_PID = "pid"

        fun restart(context: Context) {
            context.startActivity(
                Intent(context, RestartActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra(EXTRA_PID, Process.myPid())
            )
        }
    }
}
