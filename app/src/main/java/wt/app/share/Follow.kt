package wt.app.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wt.app.BuildConfig
import wt.core.io.FetchResult
import wt.core.io.fetchSnapshot
import wt.core.io.followSourceUrl

/** Where to download what my partner pasted (debug builds also take the emulator host, for the smoke test). */
fun followSource(text: String): String? = followSourceUrl(text, BuildConfig.DEBUG)

/** Downloads and reads the share file off the main thread. */
suspend fun fetchFollow(url: String): FetchResult = withContext(Dispatchers.IO) { fetchSnapshot(url) }
