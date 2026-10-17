package wt.app

import android.content.Intent
import android.net.Uri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import wt.app.ui.shareIntent
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareIntentTest {
    @Test
    fun shareIntentCarriesImageAndCaption() {
        val uri = Uri.parse("content://io.github.midhun.wttracker.share/share/wt-week-5.png")
        val caption = "Week 5 weigh-in: 87.5 kg (−0.4). Last week 5 runs, 15.0 km."
        val intent = shareIntent(uri, caption)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        @Suppress("DEPRECATION")
        assertEquals(uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(caption, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
