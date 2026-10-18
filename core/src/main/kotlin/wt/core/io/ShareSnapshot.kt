package wt.core.io

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import wt.core.model.AppData

/**
 * What my partner's app reads: my data (the same content as a backup), my name and when it was last updated
 * ([updatedAt], ISO time in Asia/Tokyo).
 */
data class ShareSnapshot(val ownerName: String, val updatedAt: String, val data: AppData)

@Serializable
private data class ShareFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val ownerName: String,
    val updatedAt: String,
    val backup: JsonElement,
) {
    companion object {
        const val FORMAT = "wt-tracker-share"
        const val VERSION = 1
    }
}

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

fun encodeShare(s: ShareSnapshot): String = json.encodeToString(
    ShareFile.serializer(),
    ShareFile(ownerName = s.ownerName, updatedAt = s.updatedAt, backup = json.parseToJsonElement(encodeBackup(s.data, s.updatedAt))),
)

/** The message for a share file written by a newer app; [fetchSnapshot] maps it to [FollowError.NewerVersion]. */
const val NEWER_VERSION = "Update Downhill to read this"

/**
 * Reads a share file; throws IllegalArgumentException with a readable message when it isn't one, or when it was
 * written by a newer app ("Update Downhill to read this"). The backup inside is checked like any restore.
 */
fun decodeShare(text: String): ShareSnapshot {
    val file = try {
        json.decodeFromString(ShareFile.serializer(), text)
    } catch (e: Exception) {
        throw IllegalArgumentException("Not a Downhill share file", e)
    }
    require(file.format == ShareFile.FORMAT) { "Not a Downhill share file" }
    require(file.version <= ShareFile.VERSION) { NEWER_VERSION }
    return ShareSnapshot(file.ownerName, file.updatedAt, decodeBackup(file.backup.toString()))
}

private val idInLink = Regex("""(?:/file/d/|[?&]id=)([A-Za-z0-9_-]{20,})""")
private val bareId = Regex("""^[A-Za-z0-9_-]{25,}$""")

/**
 * The Drive file ID in [text]: a drive.google.com link (also inside a longer message, as pasted from WhatsApp) or
 * a bare ID. Null for anything else.
 */
fun driveFileId(text: String): String? {
    val t = text.trim()
    if ("drive.google.com" in t) return idInLink.find(t)?.groupValues?.get(1)
    return t.takeIf { bareId.matches(it) }
}

/** The link I send my partner. */
fun driveLink(id: String) = "https://drive.google.com/file/d/$id/view?usp=sharing"

/** Where my partner's app downloads the file from (works for "anyone with the link" files, no sign-in). */
fun driveDownloadUrl(id: String) = "https://drive.google.com/uc?export=download&id=$id"
