package wt.core.io

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A Drive call that failed with HTTP [code]: 401 means sign-in lapsed, 404 that the file is gone. */
class DriveException(val code: Int) : IOException("Drive HTTP $code")

/**
 * The few Drive REST v3 calls partner sharing needs, with an access token for the `drive.file` scope. Blocking:
 * call it off the main thread. [api] and [oauth] are only changed by tests.
 */
class DriveClient(
    private val token: String,
    private val api: String = "https://www.googleapis.com",
    private val oauth: String = "https://oauth2.googleapis.com",
) {
    /** Creates a JSON file in my Drive and returns its ID. */
    fun create(name: String, json: String): String {
        val boundary = "wt-share-boundary"
        val metadata = """{"name":${Json.encodeToString(String.serializer(), name)},"mimeType":"application/json"}"""
        val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
            "--$boundary\r\nContent-Type: application/json\r\n\r\n$json\r\n--$boundary--"
        val reply = call("POST", "$api/upload/drive/v3/files?uploadType=multipart", body, "multipart/related; boundary=$boundary")
        return Json.parseToJsonElement(reply).jsonObject.getValue("id").jsonPrimitive.content
    }

    /** Anyone with the link can view (and only view) the file. */
    fun makePublic(id: String) {
        call("POST", "$api/drive/v3/files/$id/permissions", """{"role":"reader","type":"anyone"}""", "application/json")
    }

    /**
     * Replaces the file's content and returns whether the file is in the trash ("Delete" in the Drive app only trashes
     * it, and Drive still accepts updates). HttpURLConnection can't send PATCH, so it uses Google's method override.
     */
    fun update(id: String, json: String): Boolean {
        val reply = call("POST", "$api/upload/drive/v3/files/$id?uploadType=media&fields=trashed", json, "application/json", patch = true)
        return Json.parseToJsonElement(reply).jsonObject["trashed"]?.jsonPrimitive?.content == "true"
    }

    /** Creates the file and lets anyone with the link view it; if that fails, deletes the file again and rethrows. */
    fun createPublic(name: String, json: String): String {
        val id = create(name, json)
        try {
            makePublic(id)
        } catch (e: IOException) {
            runCatching { delete(id) }
            throw e
        }
        return id
    }

    /** Deletes the file; one that is already gone counts as deleted. */
    fun delete(id: String) {
        try {
            call("DELETE", "$api/drive/v3/files/$id", null, null)
        } catch (e: DriveException) {
            if (e.code != 404) throw e
        }
    }

    /** Withdraws the app's access to my Drive (Turn off). */
    fun revoke() {
        call("POST", "$oauth/revoke?token=${URLEncoder.encode(token, "UTF-8")}", "", "application/x-www-form-urlencoded", bearer = false)
    }

    private fun call(method: String, url: String, body: String?, type: String?, patch: Boolean = false, bearer: Boolean = true): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            if (bearer) c.setRequestProperty("Authorization", "Bearer $token")
            if (patch) c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            if (body != null) {
                c.doOutput = true
                type?.let { c.setRequestProperty("Content-Type", it) }
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw DriveException(code)
            return c.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            c.disconnect()
        }
    }
}
