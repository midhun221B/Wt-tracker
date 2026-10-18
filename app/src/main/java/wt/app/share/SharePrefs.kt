package wt.app.share

import android.content.Context
import androidx.core.content.edit
import wt.core.io.FollowError

/** This phone is either mine ([Owner], today's app) or follows my partner's data read-only ([Follow]). */
enum class Mode { Owner, Follow }

/** Why my uploads stopped, shown on the Partner sharing card. */
enum class ShareError { NeedsSignIn, FileRemoved, Offline }

/**
 * Follow mode and partner sharing state. Kept out of Room on purpose: it describes this phone, not my data, and a
 * restore or a follow update (which replace the database) must not touch it.
 */
class SharePrefs(context: Context) {
    private val p = context.getSharedPreferences("share", Context.MODE_PRIVATE)

    var mode: Mode
        get() = Mode.valueOf(p.getString("mode", Mode.Owner.name)!!)
        set(v) = p.edit { putString("mode", v.name) }

    /** Follow mode: where the share file is downloaded from. */
    var followUrl: String?
        get() = p.getString("followUrl", null)
        set(v) = p.edit { putString("followUrl", v) }

    /** The name shown on the partner's phone; on mine, the name I share under. */
    var ownerName: String?
        get() = p.getString("ownerName", null)
        set(v) = p.edit { putString("ownerName", v) }

    /** Follow mode: the snapshot's own "updated at" (ISO, Tokyo). */
    var lastUpdatedAt: String?
        get() = p.getString("lastUpdatedAt", null)
        set(v) = p.edit { putString("lastUpdatedAt", v) }

    var followError: FollowError?
        get() = p.getString("followError", null)?.let(FollowError::valueOf)
        set(v) = p.edit { putString("followError", v?.name) }

    /** Owner: the shared Drive file, null while sharing is off. */
    var fileId: String?
        get() = p.getString("fileId", null)
        set(v) = p.edit { putString("fileId", v) }

    var lastUploadMillis: Long
        get() = p.getLong("lastUploadMillis", 0)
        set(v) = p.edit { putLong("lastUploadMillis", v) }

    var error: ShareError?
        get() = p.getString("error", null)?.let(ShareError::valueOf)
        set(v) = p.edit { putString("error", v?.name) }

    /** Forgets everything except the name I last shared under, which pre-fills the next "Turn on". */
    fun clear() {
        val name = ownerName.takeIf { mode == Mode.Owner }
        p.edit { clear() }
        ownerName = name
    }
}
