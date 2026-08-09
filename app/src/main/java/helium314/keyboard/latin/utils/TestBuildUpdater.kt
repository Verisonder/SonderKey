// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import helium314.keyboard.latin.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Finds the most recent build published to the artifacts repository.
 *
 * Separate from [AppUpdater] because the two answer different questions. [AppUpdater] asks
 * whether a *newer version* exists, which works because published releases carry increasing
 * version numbers. Test builds do not: they are cut from a branch and all carry whatever version
 * the branch happens to sit on, so several in a row read as the same version, and one can be
 * older than what is installed and still be the one wanted. Comparing versions here would
 * mean the answer is almost always "you are up to date" when a build is sitting right there.
 *
 * So no comparison is made. The newest build is reported with its tag and date, and the decision
 * is left to whoever is looking, which is the person who asked for it to be built.
 */
object TestBuildUpdater {
    private const val TAG = "TestBuildUpdater"

    /**
     * A repository holding builds and nothing else, so it can be public without a token in the
     * app. Draft releases on the main repository are readable only with credentials, and any
     * credential shipped inside an APK is a credential given to everyone who has the APK.
     */
    private const val RELEASES =
        "https://api.github.com/repos/Verisonder/vs-artifacts/releases?per_page=10"

    data class Build(
        val tag: String,
        /** Short commit the build was cut from, so one build can be told from the next. */
        val commit: String,
        /** Publication time in the phone's own zone, since that is the one being read. */
        val published: String,
        val apkUrl: String,
        val sizeBytes: Long
    ) {
        /** What the settings row shows: which branch, which commit, and when. */
        val label get() = listOf(tag, commit, published).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun isSupported() = AppUpdater.isSupported()

    /** @return the newest published build, or null when the repository has none yet. */
    suspend fun check(): Result<Build?> = withContext(Dispatchers.IO) {
        try {
            val releases = JSONArray(fetchText(RELEASES))
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft", false)) continue
                val assets = release.getJSONArray("assets")
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.getString("name")
                    if (!name.endsWith(".apk")) continue
                    return@withContext Result.success(
                        Build(
                            tag = release.getString("tag_name"),
                            commit = commitOf(release.optString("name", ""), name),
                            published = localTimeOf(release.optString("published_at", "")),
                            apkUrl = asset.getString("browser_download_url"),
                            sizeBytes = asset.optLong("size", 0)
                        )
                    )
                }
            }
            Result.success(null)
        } catch (t: Throwable) {
            Log.w(TAG, "test build check failed", t)
            Result.failure(t)
        }
    }

    /** Reuses the ordinary download and install path; only the source of the URL differs. */
    fun asUpdate(build: Build) =
        AppUpdater.Update(build.tag, build.apkUrl, build.sizeBytes, "")

    /**
     * Digs the commit out of what the release is called, falling back to the file name.
     *
     * Both are built from it - the release is titled "<tag> <sha>" and the file is named
     * "SonderKey_<tag>-<sha>.apk" - so either will do, and having two means a release renamed by
     * hand still gives an answer rather than showing nothing.
     */
    private fun commitOf(releaseName: String, assetName: String): String {
        val fromTitle = releaseName.substringAfterLast(' ', "").trim()
        if (fromTitle.isNotEmpty() && fromTitle != releaseName) return fromTitle
        return assetName.removeSuffix(".apk").substringAfterLast('-', "")
    }

    /**
     * Turns GitHub's UTC timestamp into a local clock time.
     *
     * The date was what this showed before and it was the less useful half: several builds land
     * on one day, so the date cannot tell them apart, while the time always can. Shown in the
     * phone's own zone rather than UTC, because that is the clock being compared against.
     */
    private fun localTimeOf(iso: String): String {
        if (iso.isEmpty()) return ""
        return try {
            val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val parsed = parser.parse(iso) ?: return ""
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(parsed)
        } catch (t: Throwable) {
            Log.w(TAG, "could not read the published time", t)
            ""
        }
    }

    private fun fetchText(url: String): String {
        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "SonderKey/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json")
            instanceFollowRedirects = true
        }
        if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK)
            throw Exception("HTTP ${connection.responseCode}")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}
