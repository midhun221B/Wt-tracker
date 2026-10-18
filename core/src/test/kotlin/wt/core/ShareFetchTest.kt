package wt.core

import com.sun.net.httpserver.HttpServer
import wt.core.io.FetchResult
import wt.core.io.FollowError
import wt.core.io.fetchSnapshot
import wt.core.io.followSourceUrl
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ShareFetchTest {
    private val fixture = javaClass.getResource("/share-sample.json")!!.readText()
    private var server: HttpServer? = null

    /** Serves [body] with [code] at /share.json and returns its URL. */
    private fun serve(code: Int, body: String): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/share.json") { ex ->
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong().coerceAtLeast(1))
            ex.responseBody.use { if (bytes.isNotEmpty()) it.write(bytes) else it.write(' '.code) }
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}/share.json"
    }

    @AfterTest
    fun stop() {
        server?.stop(0)
    }

    @Test
    fun okDecodes() {
        val r = assertIs<FetchResult.Ok>(fetchSnapshot(serve(200, fixture)))
        assertEquals("Midhun", r.snapshot.ownerName)
    }

    @Test
    fun missingIsLinkDead() {
        assertEquals(FetchResult.Failed(FollowError.LinkDead), fetchSnapshot(serve(404, "nope")))
    }

    @Test
    fun garbageIsUnreadable() {
        assertEquals(FetchResult.Failed(FollowError.Unreadable), fetchSnapshot(serve(200, "<html>")))
    }

    @Test
    fun newerVersion() {
        val newer = fixture.replaceFirst("\"version\": 1", "\"version\": 2")
        assertEquals(FetchResult.Failed(FollowError.NewerVersion), fetchSnapshot(serve(200, newer)))
    }

    @Test
    fun noServerIsOffline() {
        val port = ServerSocket(0).use { it.localPort } // free, then closed: nothing listens
        assertEquals(FetchResult.Failed(FollowError.Offline), fetchSnapshot("http://127.0.0.1:$port/share.json"))
    }

    @Test
    fun sourceUrl() {
        val id = "1AbCdEfGhIjKlMnOpQrStUvWxYz012345"
        assertEquals(
            "https://drive.google.com/uc?export=download&id=$id",
            followSourceUrl("https://drive.google.com/file/d/$id/view?usp=sharing", debug = false),
        )
        assertNull(followSourceUrl("http://10.0.2.2:8765/share.json", debug = false))
        assertEquals("http://10.0.2.2:8765/share.json", followSourceUrl(" http://10.0.2.2:8765/share.json ", debug = true))
        assertNull(followSourceUrl("hello", debug = true))
    }
}
