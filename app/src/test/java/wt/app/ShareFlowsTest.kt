package wt.app

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import wt.app.data.AppDatabase
import wt.app.share.Mode
import wt.app.share.SharePrefs
import wt.app.ui.AppViewModel
import wt.app.ui.turnOnErrorText
import wt.core.io.DriveClient
import wt.core.io.DriveException
import java.io.IOException
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The follow and sharing flows in the view model, against a local server standing in for Drive. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareFlowsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val fixture = javaClass.getResource("/share-sample.json")!!.readText()
    private var server: HttpServer? = null
    private val methods = mutableListOf<String>()

    /** Answers every request with [code] and [body] after [delayMs]; returns the base URL. */
    private fun serve(code: Int, body: String = "", delayMs: Long = 0): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            synchronized(methods) { methods += ex.requestMethod }
            Thread.sleep(delayMs)
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            ex.responseBody.use { if (bytes.isNotEmpty()) it.write(bytes) }
        }
        s.executor = java.util.concurrent.Executors.newCachedThreadPool()
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}"
    }

    @After
    fun stop() {
        server?.stop(0)
    }

    /** Runs the main looper (where the view model's coroutines resume) for [ms]. */
    private fun pump(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun stopFollowingWinsOverARefreshInFlight() {
        // A slow refresh must not bring my partner's data back after Stop following.
        val base = serve(200, fixture, delayMs = 1500)
        SharePrefs(app).apply { mode = Mode.Follow; followUrl = "$base/share.json"; ownerName = "Midhun" }
        val vm = AppViewModel(app)
        pump(300)
        vm.refreshFollow(force = true)
        pump(300)
        vm.stopFollowing()
        pump(3000)
        assertEquals(Mode.Owner, SharePrefs(app).mode)
        assertNull(runBlocking { AppDatabase.get(app).profile().get() })
    }

    @Test
    fun turnOffWithoutATokenKeepsTheLink() {
        // Offline (no token): nothing was deleted, so sharing must stay on and the link stay known.
        SharePrefs(app).apply { mode = Mode.Owner; fileId = "abc"; ownerName = "Midhun" }
        val vm = AppViewModel(app)
        vm.stopSharing(null)
        pump(500)
        assertEquals("abc", SharePrefs(app).fileId)
    }

    @Test
    fun turnOffKeepsTheLinkWhenTheDeleteFails() {
        val base = serve(500)
        SharePrefs(app).apply { mode = Mode.Owner; fileId = "abc"; ownerName = "Midhun" }
        val vm = AppViewModel(app).apply { driveFor = { DriveClient(it, base, oauth = base) } }
        vm.stopSharing("t")
        pump(1500)
        assertTrue(methods.contains("DELETE"), methods.toString())
        assertEquals("abc", SharePrefs(app).fileId)
    }

    @Test
    fun turnOffClearsTheLinkOnceTheFileIsDeleted() {
        val base = serve(204)
        SharePrefs(app).apply { mode = Mode.Owner; fileId = "abc"; ownerName = "Midhun" }
        val vm = AppViewModel(app).apply { driveFor = { DriveClient(it, base, oauth = base) } }
        vm.stopSharing("t")
        pump(1500)
        assertNull(SharePrefs(app).fileId)
    }

    @Test
    fun turnOnErrorsSayWhatWentWrong() {
        // A 403 is usually the Drive API not being turned on (setup), not the connection.
        assertTrue(turnOnErrorText(DriveException(403)).contains("403"))
        assertTrue(turnOnErrorText(IOException("timeout")).contains("connection"))
    }
}
