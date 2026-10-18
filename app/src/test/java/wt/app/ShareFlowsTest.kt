package wt.app

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
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
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The follow and sharing flows in the view model, against a local server standing in for Drive. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareFlowsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    // Shared with core's tests; Gradle runs these from the app module's directory.
    private val fixture = java.io.File("../core/src/test/resources/share-sample.json").readText()
    private var server: ServerSocket? = null
    private val methods = mutableListOf<String>()

    /**
     * Answers every request with [code] and [body] after [delayMs]; returns the base URL. A bare socket server, since
     * the JDK's HttpServer isn't on the Android unit-test classpath.
     */
    private fun serve(code: Int, body: String = "", delayMs: Long = 0): String {
        val s = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: IOException) { break }
                thread(isDaemon = true) { c.use { answer(it, code, body, delayMs) } }
            }
        }
        server = s
        return "http://127.0.0.1:${s.localPort}"
    }

    private fun answer(c: Socket, code: Int, body: String, delayMs: Long) {
        val input = c.getInputStream().bufferedReader()
        val requestLine = input.readLine() ?: return
        synchronized(methods) { methods += requestLine.substringBefore(' ') }
        var length = 0
        while (true) {
            val header = input.readLine()
            if (header.isNullOrEmpty()) break
            if (header.startsWith("Content-Length:", ignoreCase = true)) length = header.substringAfter(':').trim().toInt()
        }
        repeat(length) { input.read() }
        Thread.sleep(delayMs)
        val bytes = body.toByteArray()
        c.getOutputStream().apply {
            write("HTTP/1.1 $code X\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes)
            flush()
        }
    }

    @After
    fun stop() {
        server?.close()
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
