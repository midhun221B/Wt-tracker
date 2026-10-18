package wt.core

import com.sun.net.httpserver.HttpServer
import wt.core.io.DriveClient
import wt.core.io.DriveException
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DriveClientTest {
    /** What the fake Drive saw. */
    private data class Seen(val method: String, val path: String, val query: String?, val auth: String?, val override: String?, val body: String)

    private val seen = mutableListOf<Seen>()
    private var server: HttpServer? = null

    /** A fake API answering every request with [code] and [reply]; returns its base URL. */
    private fun api(code: Int, reply: String = ""): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            seen += Seen(
                ex.requestMethod, ex.requestURI.path, ex.requestURI.query,
                ex.requestHeaders.getFirst("Authorization"), ex.requestHeaders.getFirst("X-HTTP-Method-Override"),
                ex.requestBody.readBytes().decodeToString(),
            )
            val bytes = reply.toByteArray()
            ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            ex.responseBody.use { if (bytes.isNotEmpty()) it.write(bytes) }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    @AfterTest
    fun stop() {
        server?.stop(0)
    }

    @Test
    fun createReturnsId() {
        val id = DriveClient("t", api(200, """{"id":"abc","name":"Wt-tracker share.json"}""")).create("Wt-tracker share.json", """{"format":"wt-tracker-share"}""")
        assertEquals("abc", id)
        val r = seen.single()
        assertEquals("POST", r.method)
        assertEquals("/upload/drive/v3/files", r.path)
        assertEquals("uploadType=multipart", r.query)
        assertEquals("Bearer t", r.auth)
        assertTrue(r.body.contains(""""name":"Wt-tracker share.json""""), r.body)
        assertTrue(r.body.contains("""{"format":"wt-tracker-share"}"""), r.body)
    }

    @Test
    fun makePublicPostsAnyoneReader() {
        DriveClient("t", api(200, """{"id":"p"}""")).makePublic("abc")
        val r = seen.single()
        assertEquals("POST", r.method)
        assertEquals("/drive/v3/files/abc/permissions", r.path)
        assertEquals("""{"role":"reader","type":"anyone"}""", r.body)
    }

    @Test
    fun updateUsesPatchOverride() {
        DriveClient("t", api(200, """{"id":"abc"}""")).update("abc", "{}")
        val r = seen.single()
        assertEquals("POST", r.method)
        assertEquals("PATCH", r.override)
        assertEquals("/upload/drive/v3/files/abc", r.path)
        assertEquals("uploadType=media", r.query)
        assertEquals("{}", r.body)
    }

    @Test
    fun deleteIgnores404() {
        DriveClient("t", api(404)).delete("abc")
        assertEquals("DELETE", seen.single().method)
        assertEquals("/drive/v3/files/abc", seen.single().path)
    }

    @Test
    fun updateMissingFileThrows404() {
        val e = assertFailsWith<DriveException> { DriveClient("t", api(404)).update("abc", "{}") }
        assertEquals(404, e.code)
    }

    @Test
    fun expiredTokenThrows401() {
        val e = assertFailsWith<DriveException> { DriveClient("t", api(401)).create("x", "{}") }
        assertEquals(401, e.code)
    }

    @Test
    fun revokePostsTheToken() {
        val base = api(200)
        DriveClient("t", base, oauth = base).revoke()
        assertEquals("/revoke", seen.single().path)
        assertEquals("token=t", seen.single().query)
    }
}
