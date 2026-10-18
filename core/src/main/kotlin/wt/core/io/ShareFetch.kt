package wt.core.io

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Why following failed, each with its own message in the app. */
enum class FollowError { NotALink, LinkDead, Unreadable, NewerVersion, Offline }

sealed interface FetchResult {
    data class Ok(val snapshot: ShareSnapshot) : FetchResult
    data class Failed(val error: FollowError) : FetchResult
}

/**
 * Where to download what my partner pasted: the Drive download URL for a share link (or the whole WhatsApp
 * message), or null. In [debug] builds an emulator-host URL (http://10.0.2.2:…) is taken as is, for the smoke test.
 */
fun followSourceUrl(text: String, debug: Boolean): String? {
    driveFileId(text)?.let { return driveDownloadUrl(it) }
    val t = text.trim()
    return t.takeIf { debug && it.startsWith("http://10.0.2.2:") }
}

/**
 * Downloads and reads a share file (blocking; call it off the main thread). 403/404/410, or an HTML page (Google's
 * sign-in or error page for a trashed or unshared file), mean the link was turned off or replaced; no connection is
 * Offline; anything else that isn't a share file is Unreadable.
 */
fun fetchSnapshot(url: String, timeoutMs: Int = 15_000): FetchResult {
    val text = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        try {
            when (c.responseCode) {
                // A trashed or no-longer-shared Drive file answers with Google's HTML sign-in or error page.
                200 -> if (c.contentType?.startsWith("text/html") == true || c.url.host == "accounts.google.com") {
                    return FetchResult.Failed(FollowError.LinkDead)
                } else {
                    c.inputStream.use { it.readBytes().decodeToString() }
                }
                403, 404, 410 -> return FetchResult.Failed(FollowError.LinkDead)
                else -> return FetchResult.Failed(FollowError.Offline)
            }
        } finally {
            c.disconnect()
        }
    } catch (e: IOException) {
        return FetchResult.Failed(FollowError.Offline)
    }
    return try {
        FetchResult.Ok(decodeShare(text))
    } catch (e: IllegalArgumentException) {
        FetchResult.Failed(if (e.message?.contains("Update Wt-tracker") == true) FollowError.NewerVersion else FollowError.Unreadable)
    }
}
